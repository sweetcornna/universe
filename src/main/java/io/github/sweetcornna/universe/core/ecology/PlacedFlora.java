package io.github.sweetcornna.universe.core.ecology;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToDoubleFunction;

import org.jspecify.annotations.Nullable;

/**
 * 玩家放下的星球植物显示成哪个物种（ADR-0030 §六）。植物方块按形态注册，物种靠变体号区分（{@code FloraBlocks}），
 * 物品不记物种；放下时按所在星球、所在群系挑这个形态的一个物种，和世界生成时一样上色、发光。
 *
 * <p>挑法：先挑长在这个生境的，没有就挑这个形态的任意一种；几种都行时按多度、用位置算出的数挑，
 * 客户端预测和服务端放下的结果一样。这颗星球上没有这个形态的物种时返回 null（方块用默认的变体号）。
 */
public final class PlacedFlora {
    private PlacedFlora() {
    }

    /**
     * 地面植物。
     *
     * @param biotope 放的地方的生境；不长陆生植物的群系是 null
     * @param pos     放的位置算出来的数（同一位置每次一样）
     */
    public static @Nullable PlantSpecies plant(Flora flora, PlantForm form, @Nullable Biotope biotope, long pos) {
        List<PlantSpecies> any = new ArrayList<>();
        List<PlantSpecies> local = new ArrayList<>();
        for (PlantSpecies plant : flora.plants()) {
            if (plant.form() != form) {
                continue;
            }
            any.add(plant);
            if (biotope != null && plant.biotopes().contains(biotope)) {
                local.add(plant);
            }
        }
        return pick(local.isEmpty() ? any : local, PlantSpecies::abundance, pos);
    }

    /**
     * 巨菌的菌盖、柱状肉茎这类实心的肉质方块（树木的 {@link TreeBuilder.Part#CAP}）：挑长这种方块的树种。
     */
    public static @Nullable TreeSpecies cap(Flora flora, @Nullable Biotope biotope, long pos) {
        List<TreeSpecies> any = new ArrayList<>();
        List<TreeSpecies> local = new ArrayList<>();
        for (TreeSpecies tree : flora.trees()) {
            if (tree.shape() != TreeShape.MUSHROOM && tree.shape() != TreeShape.COLUMN) {
                continue;
            }
            any.add(tree);
            if (biotope != null && tree.biotopes().contains(biotope)) {
                local.add(tree);
            }
        }
        return pick(local.isEmpty() ? any : local, TreeSpecies::abundance, pos);
    }

    /** 方块坐标打散成一个数（和世界种子无关，同一格每次一样）。 */
    public static long hash(int x, int y, int z) {
        long h = x * 0x9E3779B97F4A7C15L ^ y * 0xC2B2AE3D27D4EB4FL ^ z * 0x165667B19E3779F9L;
        h ^= h >>> 33;
        h *= 0xFF51AFD7ED558CCDL;
        h ^= h >>> 33;
        return h;
    }

    private static <T> @Nullable T pick(List<T> candidates, ToDoubleFunction<T> weight, long pos) {
        if (candidates.isEmpty()) {
            return null;
        }
        double total = 0;
        for (T candidate : candidates) {
            total += Math.max(0, weight.applyAsDouble(candidate));
        }
        if (total <= 0) {
            return candidates.getFirst();
        }
        // 取高 53 位当 0–1 的均匀数
        double target = (pos >>> 11) * 0x1.0p-53 * total;
        for (T candidate : candidates) {
            target -= Math.max(0, weight.applyAsDouble(candidate));
            if (target < 0) {
                return candidate;
            }
        }
        return candidates.getLast();
    }
}
