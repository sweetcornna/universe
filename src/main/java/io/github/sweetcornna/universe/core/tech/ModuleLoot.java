package io.github.sweetcornna.universe.core.tech;

import java.util.List;

import io.github.sweetcornna.universe.core.galaxy.StarClass;
import io.github.sweetcornna.universe.core.procgen.Rng;

/**
 * 兴趣点里掉什么模块（ADR-0014）：种类均匀随机，品级按恒星的稀有程度加权，稀有恒星周围的星球出好模块的概率高。
 * 有的来源（坠毁飞船）品级再往上提一档权重。
 */
public final class ModuleLoot {
    /** 普通恒星：C 50%、B 32%、A 15%、S 3%。 */
    static final double[] COMMON = {50, 32, 15, 3};
    /** 蓝巨星、红巨星、白矮星。 */
    static final double[] RARE = {35, 35, 23, 7};
    /** 中子星、黑洞。 */
    static final double[] EXOTIC = {20, 35, 32, 13};

    private ModuleLoot() {
    }

    /** 恒星的稀有程度：0 普通、1 稀有、2 奇特。 */
    public static int rarity(StarClass star) {
        return switch (star) {
            case NEUTRON_STAR, BLACK_HOLE -> 2;
            case O, B, RED_GIANT, WHITE_DWARF -> 1;
            default -> 0;
        };
    }

    /**
     * 抽一个品级。
     *
     * @param rarity 恒星的稀有程度（0–2）
     * @param bonus  来源的加成（0 普通，1 好一档：权重表往后挪一格的一半）
     */
    public static Grade grade(Rng rng, int rarity, int bonus) {
        double[] weights = (rarity >= 2 ? EXOTIC : rarity == 1 ? RARE : COMMON).clone();
        if (bonus > 0) {
            // 把每一档一半的权重挪到下一档
            for (int i = weights.length - 2; i >= 0; i--) {
                double moved = weights[i] / 2;
                weights[i] -= moved;
                weights[i + 1] += moved;
            }
        }
        return rng.weighted(List.of(Grade.values()), grade -> weights[grade.ordinal()]);
    }

    /** 随机一个模块：种类从给定的范围里均匀抽。 */
    public static TechModule roll(Rng rng, List<ModuleType> types, int rarity, int bonus) {
        ModuleType type = rng.pick(types);
        return TechModule.roll(type, grade(rng, rarity, bonus), rng);
    }
}
