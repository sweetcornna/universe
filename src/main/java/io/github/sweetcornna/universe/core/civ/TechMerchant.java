package io.github.sweetcornna.universe.core.civ;

import java.util.ArrayList;
import java.util.List;

import io.github.sweetcornna.universe.core.procgen.Rng;
import io.github.sweetcornna.universe.core.tech.Grade;
import io.github.sweetcornna.universe.core.tech.ModuleLoot;
import io.github.sweetcornna.universe.core.tech.ModuleType;
import io.github.sweetcornna.universe.core.tech.TechModule;

/**
 * 技术商（ADR-0027 §六）：每天 3 样货，多半从这个种族擅长的模块里抽：商旅族是飞船，学者族是扫描和外骨骼，戍卫族是武器和护盾。
 * 声望越高货越好；敌视的不卖。价钱由适配层按市场和声望折扣算。
 */
public final class TechMerchant {
    public static final int OFFERS = 3;
    /** 抽本族擅长的那一池的机会，其余从全部模块里抽。 */
    static final double FAVOURED = 0.7;

    private static final List<ModuleType> TRADER = List.of(ModuleType.THRUST_EFFICIENCY, ModuleType.PULSE_TUNING,
            ModuleType.HYPERDRIVE_BOOSTER, ModuleType.HULL_REINFORCEMENT);
    private static final List<ModuleType> SCHOLAR = List.of(ModuleType.SCANNER_ARRAY, ModuleType.ANALYZER, ModuleType.BEAM_FOCUS,
            ModuleType.LIFE_SUPPORT, ModuleType.COLD_LINING, ModuleType.HEAT_LINING, ModuleType.TOXIN_FILTER, ModuleType.RADIATION_SHIELD,
            ModuleType.JET_BOOSTER, ModuleType.SERVO);
    private static final List<ModuleType> WARDEN = List.of(ModuleType.WEAPON_CALIBRATOR, ModuleType.HEAT_SINK, ModuleType.SHIELD,
            ModuleType.FIRE_CONTROL, ModuleType.DEFLECTOR);

    private TechMerchant() {
    }

    /** 这个种族擅长的模块。 */
    public static List<ModuleType> pool(Race race) {
        return switch (race) {
            case TRADER -> TRADER;
            case SCHOLAR -> SCHOLAR;
            case WARDEN -> WARDEN;
        };
    }

    public static boolean sells(Standing.Tier tier) {
        return tier != Standing.Tier.HOSTILE;
    }

    /** 品级加成：熟客、朋友好一档，至交好两档。 */
    public static int bonus(Standing.Tier tier) {
        return switch (tier) {
            case REGULAR, FRIEND -> 1;
            case KINDRED -> 2;
            default -> 0;
        };
    }

    /**
     * 今天的货。种类和数值只看种子（NPC 加日期），品级再看声望档，所以同一天升了档，货还是那几样，只是更好。
     *
     * @param rarity 恒星的稀有程度（{@link ModuleLoot#rarity}）
     */
    public static List<TechModule> stock(Race race, Standing.Tier tier, int rarity, long seed) {
        List<ModuleType> all = List.of(ModuleType.values());
        List<TechModule> out = new ArrayList<>(OFFERS);
        for (int i = 0; i < OFFERS; i++) {
            Rng rng = Rng.of(seed + i);
            ModuleType type = rng.chance(FAVOURED) ? rng.pick(pool(race)) : rng.pick(all);
            Grade grade = ModuleLoot.grade(rng, rarity, bonus(tier));
            // 至交的最后一样至少是 A
            if (tier == Standing.Tier.KINDRED && i == OFFERS - 1 && grade.compareTo(Grade.A) < 0) {
                grade = Grade.A;
            }
            out.add(TechModule.roll(type, grade, rng));
        }
        return List.copyOf(out);
    }
}
