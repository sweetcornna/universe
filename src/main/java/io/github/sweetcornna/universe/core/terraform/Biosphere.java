package io.github.sweetcornna.universe.core.terraform;

import java.util.Set;

import io.github.sweetcornna.universe.core.chem.AtmosphereMix;
import io.github.sweetcornna.universe.core.chem.Gas;
import io.github.sweetcornna.universe.core.galaxy.DayCycle;
import io.github.sweetcornna.universe.core.galaxy.Planet;
import io.github.sweetcornna.universe.core.microbe.Gene;

/**
 * 改造中星球上的生物量（ADR-0036 §六）。播下能活的先锋生物以后按 logistic 曲线长，上限由水、温度、光照、气压定；
 * 产氧光合把二氧化碳一比一换成氧（CO₂ + H₂O → CH₂O + O₂，按单位算）。环境变得活不了时慢慢死掉。
 *
 * <p>生物量 1 是整颗星球能长满的程度，播种站周围的地衣、苔藓按它铺开。
 */
public final class Biosphere {
    /** 一次播种加多少生物量。 */
    public static final double SEED_BIOMASS = 0.001;
    /** 条件最好时生物量翻一倍要几刻（半小时）。从一次播种长到一半约四个半小时。 */
    public static final long DOUBLING_TICKS = 36_000;
    /** 活不了时生物量减半要几刻（一小时）。 */
    public static final long DIE_HALF_LIFE_TICKS = 72_000;
    /** 生物量为 1 时每刻把多少个标准大气压的二氧化碳换成氧：一小时 0.03 个大气压。 */
    public static final double PHOTOSYNTHESIS_ATM_PER_TICK = 0.03 / 72_000;
    /** 二氧化碳分压到这么多（标准大气压）时光合作用是满速的一半。 */
    public static final double CO2_HALF_SATURATION = 0.0002;
    /** 补算很长一段时间时，每一步最多几刻（十分钟），中间按新的环境重新算。 */
    public static final long STEP_TICKS = 12_000;
    /** 生物量少过这个就算死光了。 */
    public static final double EXTINCT = 1e-7;

    private Biosphere() {
    }

    /** 推进一段时间。 */
    public static TerraformState advance(TerraformState state, Planet base, AtmosphereMix baseMix, boolean compactStar, long ticks) {
        if (ticks <= 0 || !Terraform.terraformable(base) || state.pioneers().isEmpty() && state.biomass() <= 0) {
            return state;
        }
        TerraformState current = state;
        long left = ticks;
        while (left > 0) {
            long step = Math.min(left, STEP_TICKS);
            current = step(current, base, baseMix, compactStar, step);
            left -= step;
        }
        return current;
    }

    private static TerraformState step(TerraformState state, Planet base, AtmosphereMix baseMix, boolean compactStar, long ticks) {
        EffectiveEnvironment environment = Terraform.effective(base, baseMix, compactStar, state);
        boolean viable = false;
        for (Set<Gene> genes : state.pioneers()) {
            if (Pioneer.viable(genes, environment, state.irrigation())) {
                viable = true;
                break;
            }
        }
        double cap = carryingCapacity(environment, base, state.irrigation());
        double biomass = state.biomass();
        double co2 = co2Factor(environment.partial(Gas.CARBON_DIOXIDE));
        double next;
        if (viable && cap > 0 && biomass > 0) {
            if (biomass < cap) {
                double rate = Math.log(2) / DOUBLING_TICKS * co2;
                next = cap * biomass / (biomass + (cap - biomass) * Math.exp(-rate * ticks));
            } else {
                next = cap + (biomass - cap) * Math.exp(-ticks * Math.log(2) / DIE_HALF_LIFE_TICKS);
            }
        } else {
            next = biomass * Math.exp(-ticks * Math.log(2) / DIE_HALF_LIFE_TICKS);
        }
        if (next < EXTINCT) {
            next = 0;
        }
        TerraformState result = state.withBiomass(next);
        if (viable) {
            double average = (biomass + next) / 2;
            double wanted = PHOTOSYNTHESIS_ATM_PER_TICK * environment.capacity() * average * co2 * ticks;
            double converted = Math.min(wanted, environment.available(Gas.CARBON_DIOXIDE));
            if (converted > 0) {
                result = Terraform.addGas(base, baseMix, result, Gas.CARBON_DIOXIDE, -converted);
                result = Terraform.addGas(base, baseMix, result, Gas.OXYGEN, converted);
            }
        }
        return result;
    }

    /** 二氧化碳少的时候光合作用慢下来（照米氏方程的样子）。 */
    static double co2Factor(double partialPressure) {
        return partialPressure <= 0 ? 0 : partialPressure / (partialPressure + CO2_HALF_SATURATION);
    }

    /**
     * 生物量的上限，0–1：水 × 温度 × 光照 × 气压。
     *
     * <ul>
     *   <li>水：本来有水为 1；只靠播种站浇水为 0.25 × 浇到的比例。</li>
     *   <li>温度：200 K 时 0.02，253 K 时 0.3，273 K 时 0.7，303 K 时 1，330 K 时 0.6，395 K 时 0.1，中间线性，再往外为 0。</li>
     *   <li>光照：潮汐锁定的永夜 0.1、晨昏 0.5，其余 1。</li>
     *   <li>气压：低于水的三相点 0.1（只剩岩石里的），低于 0.06 个大气压 0.4，再高为 1。</li>
     * </ul>
     */
    public static double carryingCapacity(EffectiveEnvironment environment, Planet base, double irrigation) {
        double water = environment.naturalWater() ? 1 : 0.25 * Math.clamp(irrigation, 0, 1);
        double temperature = temperatureFactor(environment.temperature());
        DayCycle day = base.dayCycle();
        double light = day == DayCycle.LOCKED_NIGHT ? 0.1 : day == DayCycle.LOCKED_TWILIGHT ? 0.5 : 1;
        double pressure = environment.pressure();
        double air = pressure < Terraform.TRIPLE_POINT ? 0.1 : pressure < Pioneer.UV_PRESSURE ? 0.4 : 1;
        return water * temperature * light * air;
    }

    private static final double[] TEMPERATURES = {200, 253, 273, 303, 330, 395};
    private static final double[] FACTORS = {0.02, 0.3, 0.7, 1.0, 0.6, 0.1};

    static double temperatureFactor(double temperature) {
        if (temperature < TEMPERATURES[0] || temperature > TEMPERATURES[TEMPERATURES.length - 1]) {
            return 0;
        }
        for (int i = 1; i < TEMPERATURES.length; i++) {
            if (temperature <= TEMPERATURES[i]) {
                double t = (temperature - TEMPERATURES[i - 1]) / (TEMPERATURES[i] - TEMPERATURES[i - 1]);
                return FACTORS[i - 1] + t * (FACTORS[i] - FACTORS[i - 1]);
            }
        }
        return 0;
    }
}
