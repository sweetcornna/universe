package io.github.sweetcornna.universe.core.chem;

import java.util.EnumMap;
import java.util.Map;

import io.github.sweetcornna.universe.core.galaxy.Atmosphere.Kind;
import io.github.sweetcornna.universe.core.galaxy.GalaxyGenerator;
import io.github.sweetcornna.universe.core.galaxy.Planet;
import io.github.sweetcornna.universe.core.galaxy.PlanetType;
import io.github.sweetcornna.universe.core.procgen.Rng;
import io.github.sweetcornna.universe.core.procgen.Seeds;

/**
 * 星球本来的大气成分（ADR-0036 §二）：按类型、大气种类、温度和种子算，不存盘。照真实的天体定：
 *
 * <ul>
 *   <li>母星：地球的干空气。</li>
 *   <li>可呼吸：照地球，氧 17–26%。</li>
 *   <li>稀薄：照火星，二氧化碳为主；冰冻星球冷到二氧化碳冻在地上时照冥王星、海卫一，氮为主。</li>
 *   <li>有毒：早期地球加火山气体，氮、二氧化碳、二氧化硫，有的带氨；冰冻星球照土卫六，带几十 ppm 的氰化氢。</li>
 *   <li>浓密：照金星。</li>
 *   <li>奇异：先照它的大气种类，再常带氦、氩。</li>
 *   <li>气态巨行星：照木星。</li>
 * </ul>
 */
public final class AtmosphereMixes {
    /** 派生种子用的键，已有的派生结果不受影响。 */
    public static final String SEED_KEY = "atmosphere_mix";
    /** 冷到这个温度（K）以下，二氧化碳在低压下冻在地上（火星极冠约 150 K），大气只剩氮。 */
    static final double CO2_FROST = 150;
    /** 暖过这个温度（K），冰冻星球的稀薄大气照火星。中间按种子二选一。 */
    static final double CO2_GAS = 180;
    /** 热过这个温度（K），有毒大气里火山气体多一些。 */
    static final double HOT_TOXIC = 330;

    private AtmosphereMixes() {
    }

    /** 这颗星球本来的大气成分；没有大气为空。 */
    public static AtmosphereMix base(Planet planet) {
        if (!planet.atmosphere().present()) {
            return AtmosphereMix.EMPTY;
        }
        if (planet.id().equals(GalaxyGenerator.HOME_PLANET_ID)) {
            return earth();
        }
        Rng rng = Rng.of(Seeds.derive(planet.seed(), SEED_KEY));
        if (planet.type() == PlanetType.GAS_GIANT) {
            return jovian(rng);
        }
        AtmosphereMix mix = byKind(rng, planet.type(), planet.atmosphere().kind(), planet.temperature());
        return planet.type() == PlanetType.EXOTIC ? exotic(rng, mix, planet.atmosphere().kind()) : mix;
    }

    /** 地球的干空气（体积分数）：氮 78.084%、氧 20.946%、氩 0.934%、二氧化碳 0.042%，加上氦和甲烷；氖没有对应的气体，不算。 */
    public static AtmosphereMix earth() {
        Map<Gas, Double> air = new EnumMap<>(Gas.class);
        air.put(Gas.NITROGEN, 0.78084);
        air.put(Gas.OXYGEN, 0.20946);
        air.put(Gas.ARGON, 0.00934);
        air.put(Gas.CARBON_DIOXIDE, 0.00042);
        air.put(Gas.HELIUM, 0.00000524);
        air.put(Gas.METHANE, 0.0000019);
        return AtmosphereMix.of(air);
    }

    private static AtmosphereMix byKind(Rng rng, PlanetType type, Kind kind, double temperature) {
        return switch (kind) {
            case NONE -> AtmosphereMix.EMPTY;
            case BREATHABLE -> breathable(rng);
            case THIN -> type == PlanetType.FROZEN && frozenOut(rng, temperature) ? plutonian(rng) : martian(rng);
            case TOXIC -> type == PlanetType.FROZEN ? titanian(rng) : volcanic(rng, temperature);
            case DENSE -> venusian(rng);
        };
    }

    /** 冰冻星球上二氧化碳是不是冻在了地上。 */
    private static boolean frozenOut(Rng rng, double temperature) {
        boolean coin = rng.chance(0.5);
        return temperature < CO2_FROST || temperature <= CO2_GAS && coin;
    }

    /** 可呼吸：照地球，氮补足。 */
    private static AtmosphereMix breathable(Rng rng) {
        Map<Gas, Double> minors = new EnumMap<>(Gas.class);
        minors.put(Gas.OXYGEN, rng.range(0.17, 0.26));
        minors.put(Gas.ARGON, rng.range(0.005, 0.015));
        // 二氧化碳最多 0.2%：到 2.5 个大气压也不超过 NIOSH 的 5000 ppm（显生宙的地球最高也就几千 ppm）
        minors.put(Gas.CARBON_DIOXIDE, rng.logRange(0.0003, 0.002));
        return balanced(Gas.NITROGEN, minors);
    }

    /** 火星：二氧化碳 95% 上下，氮、氩各百分之二三，氧和一氧化碳各千分之一上下。 */
    private static AtmosphereMix martian(Rng rng) {
        Map<Gas, Double> minors = new EnumMap<>(Gas.class);
        minors.put(Gas.NITROGEN, rng.range(0.02, 0.04));
        minors.put(Gas.ARGON, rng.range(0.015, 0.025));
        minors.put(Gas.OXYGEN, rng.range(0.001, 0.002));
        // 火星 0.06% 上下（好奇号 SAM）。火星只有 0.006 个大气压，分压很低；这里的稀薄大气厚一些，分压就过了 NIOSH 的限值，
        // 改到能呼吸前要等补进去的氧把它氧化掉（AtmosphericChemistry）
        minors.put(Gas.CARBON_MONOXIDE, rng.range(0.0005, 0.0009));
        return balanced(Gas.CARBON_DIOXIDE, minors);
    }

    /** 冥王星、海卫一：氮约 98%，甲烷千分之几，一氧化碳万分之几。 */
    private static AtmosphereMix plutonian(Rng rng) {
        Map<Gas, Double> minors = new EnumMap<>(Gas.class);
        minors.put(Gas.METHANE, rng.range(0.002, 0.006));
        minors.put(Gas.CARBON_MONOXIDE, rng.range(0.0003, 0.0008));
        minors.put(Gas.ARGON, rng.range(0.005, 0.015));
        return balanced(Gas.NITROGEN, minors);
    }

    /**
     * 土卫六：氮约 95%、甲烷约 5%、氢千分之一，紫外线把甲烷和氮打碎后生成氰化氢（卡西尼号测到平流层里 0.1–1 ppm，
     * 高层更多）。这里的氰化氢取 20–60 ppm，比土卫六的低层多，是一颗光化学更活跃的土卫六；冰冻星球上的毒性算在它头上。
     */
    private static AtmosphereMix titanian(Rng rng) {
        Map<Gas, Double> minors = new EnumMap<>(Gas.class);
        minors.put(Gas.METHANE, rng.range(0.04, 0.06));
        minors.put(Gas.HYDROGEN, rng.range(0.0008, 0.0015));
        minors.put(Gas.HYDROGEN_CYANIDE, rng.range(0.00002, 0.00006));
        return balanced(Gas.NITROGEN, minors);
    }

    /** 早期地球加火山气体：氮 50–70%、二氧化碳 20–40%、二氧化硫 1–5%，四成带氨。热的二氧化硫多一些。 */
    private static AtmosphereMix volcanic(Rng rng, double temperature) {
        Map<Gas, Double> minors = new EnumMap<>(Gas.class);
        minors.put(Gas.CARBON_DIOXIDE, rng.range(0.20, 0.40));
        minors.put(Gas.SULFUR_DIOXIDE, temperature > HOT_TOXIC ? rng.range(0.025, 0.05) : rng.range(0.01, 0.03));
        minors.put(Gas.ARGON, rng.range(0.005, 0.01));
        if (rng.chance(0.4)) {
            minors.put(Gas.AMMONIA, rng.range(0.002, 0.01));
        }
        return balanced(Gas.NITROGEN, minors);
    }

    /** 金星：二氧化碳约 96.5%、氮约 3.5%、二氧化硫万分之几到千分之几，氩和一氧化碳各几十个 ppm。 */
    private static AtmosphereMix venusian(Rng rng) {
        Map<Gas, Double> minors = new EnumMap<>(Gas.class);
        minors.put(Gas.NITROGEN, rng.range(0.03, 0.04));
        minors.put(Gas.SULFUR_DIOXIDE, rng.logRange(0.0002, 0.003));
        minors.put(Gas.ARGON, rng.range(0.00005, 0.0001));
        minors.put(Gas.CARBON_MONOXIDE, rng.range(0.00001, 0.00003));
        return balanced(Gas.CARBON_DIOXIDE, minors);
    }

    /** 木星：氢 86–90%，氦补足，甲烷千分之几，氨几百个 ppm。 */
    private static AtmosphereMix jovian(Rng rng) {
        Map<Gas, Double> minors = new EnumMap<>(Gas.class);
        minors.put(Gas.HYDROGEN, rng.range(0.86, 0.90));
        minors.put(Gas.METHANE, rng.range(0.002, 0.004));
        minors.put(Gas.AMMONIA, rng.range(0.0001, 0.0003));
        return balanced(Gas.HELIUM, minors);
    }

    /**
     * 奇异星球：在它那一种大气的基础上，六成带 1–20% 的氦，六成带 2–15% 的氩，挤掉的是分数最大的那种气体。
     * 可呼吸的氧分数不变，还在 17–26%。
     */
    private static AtmosphereMix exotic(Rng rng, AtmosphereMix mix, Kind kind) {
        if (mix.isEmpty()) {
            return mix;
        }
        Map<Gas, Double> amounts = new EnumMap<>(mix.fractions());
        Gas balance = mix.sorted().get(0).getKey();
        double helium = rng.chance(0.6) ? rng.range(0.01, 0.20) : 0;
        double argon = rng.chance(0.6) ? rng.range(0.02, 0.15) : 0;
        if (kind == Kind.BREATHABLE) {
            // 只挤氮，保住氧
            double room = amounts.get(balance) - 0.3;
            double scale = helium + argon > room ? room / (helium + argon) : 1;
            helium *= scale;
            argon *= scale;
        }
        amounts.merge(Gas.HELIUM, helium, Double::sum);
        amounts.merge(Gas.ARGON, argon, Double::sum);
        amounts.put(balance, amounts.get(balance) - helium - argon);
        return AtmosphereMix.of(amounts);
    }

    /** 给定的几种按原样，剩下的都是 {@code balance}。 */
    private static AtmosphereMix balanced(Gas balance, Map<Gas, Double> minors) {
        double sum = 0;
        for (double fraction : minors.values()) {
            sum += fraction;
        }
        Map<Gas, Double> amounts = new EnumMap<>(minors);
        amounts.put(balance, Math.max(0, 1 - sum));
        return AtmosphereMix.of(amounts);
    }
}
