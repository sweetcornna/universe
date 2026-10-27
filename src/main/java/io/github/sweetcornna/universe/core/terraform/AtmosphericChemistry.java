package io.github.sweetcornna.universe.core.terraform;

import io.github.sweetcornna.universe.core.chem.AtmosphereMix;
import io.github.sweetcornna.universe.core.chem.Gas;
import io.github.sweetcornna.universe.core.galaxy.DayCycle;
import io.github.sweetcornna.universe.core.galaxy.Planet;

/**
 * 大气自己把毒气去掉（ADR-0036 §六）：照现实里这些气体在大气里能待多久（寿命）。
 *
 * <ul>
 *   <li>一氧化碳被羟基自由基氧化成二氧化碳，地球对流层里寿命约两个月；甲烷同样被氧化，寿命约 9 年（IPCC AR5）。
 *       羟基要有氧、水汽和阳光，这里要氧分压 1 千帕以上、星球上有水、不是永夜。</li>
 *   <li>二氧化硫溶进雨里落下来（酸雨），寿命一两天；氨也被雨洗掉，约一天，没有雨时被紫外线慢慢分解，几十年量级；
 *       氰化氢主要溶进海里，两三个月。要有液态的水：星球上有水，温度在冰点和沸点之间。</li>
 *   <li>氯气见光几分钟就分解。</li>
 * </ul>
 *
 * 星球本来的大气当作已经平衡好的：本来就有的去处被看不见的来源（火山、生物）抵掉，只有改造让条件变了、多出来的那部分去处才算，
 * 所以没人动过的星球不会自己变。比如本来就下雨的火山星球，二氧化硫不会自己洗掉，要用大气提取器抽；
 * 冰冻星球暖过冰点以后，氰化氢才开始被雨洗掉。
 *
 * <p>时间按 {@link #TICKS_PER_REAL_DAY} 压缩：现实一天是游戏三分钟，一氧化碳两个月折成三个小时。几种气体之间快慢的比例和现实一样。
 */
public final class AtmosphericChemistry {
    /** 现实一天折成多少刻（游戏三分钟）。 */
    public static final double TICKS_PER_REAL_DAY = 3600;
    /** 羟基自由基要的氧分压（千帕）。 */
    public static final double OH_OXYGEN_KPA = 1;
    /** 液态水的范围（K）。 */
    static final double FREEZING_POINT = 273.15;
    static final double BOILING_POINT = 373.15;
    /** 补算很长一段时间时，每一步最多几刻（十分钟），中间按新的环境重新判断条件。 */
    static final long STEP_TICKS = 12_000;

    /** 一种去处：哪种气体、要什么条件、现实里的寿命（天）、去了变成什么。 */
    enum Sink {
        CO_OXIDATION(Gas.CARBON_MONOXIDE, Needs.HYDROXYL, 60, Gas.CARBON_DIOXIDE, 0.5),
        METHANE_OXIDATION(Gas.METHANE, Needs.HYDROXYL, 3320, Gas.CARBON_DIOXIDE, 2),
        SO2_RAINOUT(Gas.SULFUR_DIOXIDE, Needs.LIQUID_WATER, 2, null, 0),
        AMMONIA_RAINOUT(Gas.AMMONIA, Needs.LIQUID_WATER, 1, null, 0),
        AMMONIA_PHOTOLYSIS(Gas.AMMONIA, Needs.SUNLIGHT, 3650, null, 0),
        HCN_OCEAN_UPTAKE(Gas.HYDROGEN_CYANIDE, Needs.LIQUID_WATER, 120, null, 0),
        CHLORINE_PHOTOLYSIS(Gas.CHLORINE, Needs.SUNLIGHT, 0.007, null, 0);

        final Gas gas;
        final Needs needs;
        final double lifetimeDays;
        /** 氧化以后变成的气体，没有为 {@code null}。 */
        final Gas product;
        /** 每去掉一个要用掉几个氧。 */
        final double oxygen;

        Sink(Gas gas, Needs needs, double lifetimeDays, Gas product, double oxygen) {
            this.gas = gas;
            this.needs = needs;
            this.lifetimeDays = lifetimeDays;
            this.product = product;
            this.oxygen = oxygen;
        }

        /** 每刻的速率（1/刻）。 */
        double rate() {
            return 1 / (this.lifetimeDays * TICKS_PER_REAL_DAY);
        }
    }

    enum Needs {
        HYDROXYL,
        LIQUID_WATER,
        SUNLIGHT
    }

    private AtmosphericChemistry() {
    }

    /** 推进一段时间，按改造多出来的去处去掉毒气。 */
    public static TerraformState advance(TerraformState state, Planet base, AtmosphereMix baseMix, boolean compactStar, long ticks) {
        if (ticks <= 0 || state.isEmpty() || !Terraform.terraformable(base)) {
            return state;
        }
        EffectiveEnvironment original = Terraform.effective(base, baseMix, compactStar, TerraformState.EMPTY);
        TerraformState current = state;
        long left = ticks;
        while (left > 0) {
            long step = Math.min(left, STEP_TICKS);
            current = step(current, base, baseMix, compactStar, original, step);
            left -= step;
        }
        return current;
    }

    private static TerraformState step(TerraformState state, Planet base, AtmosphereMix baseMix, boolean compactStar,
            EffectiveEnvironment original, long ticks) {
        EffectiveEnvironment now = Terraform.effective(base, baseMix, compactStar, state);
        TerraformState result = state;
        for (Gas gas : Gas.values()) {
            double extra = 0;
            for (Sink sink : Sink.values()) {
                if (sink.gas == gas && active(sink.needs, now, base) && !active(sink.needs, original, base)) {
                    extra += sink.rate();
                }
            }
            double amount = now.available(gas);
            if (extra <= 0 || amount <= 0) {
                continue;
            }
            double removed = amount * -Math.expm1(-extra * ticks);
            for (Sink sink : Sink.values()) {
                if (sink.gas != gas || !active(sink.needs, now, base) || active(sink.needs, original, base)) {
                    continue;
                }
                double share = removed * sink.rate() / extra;
                if (sink.oxygen > 0) {
                    // 氧不够时只去掉氧够用的那部分
                    double oxygen = Terraform.available(base, baseMix, result, Gas.OXYGEN);
                    share = Math.min(share, oxygen / sink.oxygen);
                    result = Terraform.addGas(base, baseMix, result, Gas.OXYGEN, -share * sink.oxygen);
                }
                result = Terraform.addGas(base, baseMix, result, gas, -share);
                if (sink.product != null) {
                    result = Terraform.addGas(base, baseMix, result, sink.product, share);
                }
            }
        }
        return result;
    }

    /** 这个环境下这种去处在不在。 */
    static boolean active(Needs needs, EffectiveEnvironment environment, Planet base) {
        boolean light = base.dayCycle() != DayCycle.LOCKED_NIGHT;
        return switch (needs) {
            case HYDROXYL -> light && environment.naturalWater() && environment.oxygenKpa() >= OH_OXYGEN_KPA;
            case LIQUID_WATER -> liquidWater(environment);
            case SUNLIGHT -> light && environment.pressure() >= Terraform.VACUUM;
        };
    }

    /** 地面上有液态的水：本来有水，温度在冰点和沸点之间，气压在水的三相点以上。 */
    static boolean liquidWater(EffectiveEnvironment environment) {
        return environment.naturalWater() && environment.temperature() >= FREEZING_POINT && environment.temperature() <= BOILING_POINT
                && environment.pressure() >= Terraform.TRIPLE_POINT;
    }
}
