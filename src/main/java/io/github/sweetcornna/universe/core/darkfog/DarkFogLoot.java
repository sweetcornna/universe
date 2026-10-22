package io.github.sweetcornna.universe.core.darkfog;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.github.sweetcornna.universe.core.procgen.Rng;

/**
 * 黑雾的掉落（ADR-0028 §六）：沙土（这里用地基的原料石头）和随机的零件，档次看等级。
 *
 * <ul>
 *   <li>能量碎片 0 级起，硅基神经元 3 级起，物质重组器 6 级起，负熵奇点 12 级起，核心素 18 级起。</li>
 *   <li>黑雾矩阵 3 级起，越高掉得越多。</li>
 *   <li>大的掉得多：单位按 1 份算，建筑按种类 3–10 份。</li>
 * </ul>
 */
public final class DarkFogLoot {
    public static final String STONE = "minecraft:stone";
    public static final String MATRIX = "universe:dark_fog_matrix";
    /** 掉在地上多少刻不捡就消失。 */
    public static final int LIFESPAN_TICKS = 2400;

    /** 零件、从几级起掉、权重。 */
    private record Part(String item, int minLevel, double weight) {
    }

    private static final List<Part> PARTS = List.of(
            new Part("universe:energy_shard", 0, 50),
            new Part("universe:silicon_neuron", 3, 20),
            new Part("universe:matter_recombinator", 6, 12),
            new Part("universe:negentropy_singularity", 12, 6),
            new Part("universe:core_element", 18, 3));
    /** 每份掉一个零件的概率。 */
    private static final double PART_CHANCE = 0.6;

    public record Drop(String item, int count) {
    }

    private DarkFogLoot() {
    }

    /** 一个单位按几份算。 */
    public static double weight(UnitKind kind) {
        return kind == UnitKind.GUARDIAN ? 1.5 : 1;
    }

    /** 一座建筑按几份算。 */
    public static double weight(BuildingKind kind) {
        return switch (kind) {
            case CORE -> 10;
            case RELAY -> 5;
            case RAIDER_CAMP, RANGER_CAMP, GUARDIAN_CAMP -> 4;
            case LASER_TOWER, PLASMA_SENTRY -> 3;
        };
    }

    /** 黑雾矩阵每份掉的概率：3 级起 15%，每级多 2%，最多 60%。 */
    public static double matrixChance(int level) {
        if (level < 3) {
            return 0;
        }
        return Math.min(0.6, 0.15 + 0.02 * (level - 3));
    }

    /** 掉什么：同一种合成一堆。 */
    public static List<Drop> roll(int level, double weight, Rng rng) {
        Map<String, Integer> drops = new LinkedHashMap<>();
        int shares = Math.max(1, (int) Math.round(weight));
        drops.merge(STONE, rng.range(1, 3) * shares, Integer::sum);
        List<Part> eligible = PARTS.stream().filter(part -> part.minLevel() <= level).toList();
        for (int i = 0; i < shares; i++) {
            if (rng.chance(PART_CHANCE)) {
                drops.merge(rng.weighted(eligible, Part::weight).item(), 1, Integer::sum);
            }
            if (rng.chance(matrixChance(level))) {
                drops.merge(MATRIX, 1 + level / 10, Integer::sum);
            }
        }
        List<Drop> result = new ArrayList<>();
        drops.forEach((item, count) -> result.add(new Drop(item, count)));
        return result;
    }
}
