package io.github.sweetcornna.universe.core.galaxy;

import java.util.ArrayList;
import java.util.List;

/**
 * 石林（ADR-0030 §二）：软岩层被雨水冲走，剩下一根根细高的岩柱，顶上压着一块冲不掉的硬岩帽（布莱斯峡谷）。
 *
 * <p>石柱成片长在一起（一个"剧场"里几十根），柱身是按高度分色的岩层（{@link ExoticTerrain.Material#SHAFT}），
 * 顶上一两格是硬岩帽（{@link ExoticTerrain.Material#CAPROCK}）。柱子比柱帽细，中段还能再细一道腰。
 * 只长在干地上。
 */
final class ExoticHoodoos implements ExoticTerrain.Formation {
    /** 石柱按这么大的格子分布，一格一根。 */
    private static final int CELL = 7;
    /** 柱群（剧场）按这么大的格子分布。 */
    private static final int FIELD = 64;
    /** 剧场的半径（格）。 */
    private static final int FIELD_RADIUS = 44;
    /** 柱身的基准高度。 */
    private static final int BASE_HEIGHT = 9;
    /** 柱高的起伏幅度。 */
    private static final int RELIEF = 16;
    /** 柱帽厚几格。 */
    private static final int CAP = 2;
    /** 一根柱子最远伸出自己的格子多少格（搜邻格用）。 */
    private static final int REACH = 3;

    /**
     * 一根石柱。
     *
     * @param x      柱心
     * @param z      柱心
     * @param base   柱脚所在的地面高度
     * @param height 露出地面多高
     * @param radius 柱帽的半径
     */
    private record Hoodoo(int x, int z, int base, int height, double radius) {
    }

    private final ExoticTerrain terrain;

    ExoticHoodoos(ExoticTerrain terrain) {
        this.terrain = terrain;
    }

    @Override
    public ExoticTerrain.Prepared prepare(int x0, int z0, ExoticTerrain.Columns columns) {
        List<Hoodoo> hoodoos = this.collect(x0, z0);
        return (x, z, dx, dz, ground, state, out) -> {
            if (state != ExoticTerrain.DRY) {
                return;
            }
            for (Hoodoo hoodoo : hoodoos) {
                if (this.shaft(x, z, hoodoo, ground, out)) {
                    return;
                }
            }
        };
    }

    /** 碰到这个区块的石柱：本区块的格子再往外一圈，柱帽伸过边界的也要算进来。 */
    private List<Hoodoo> collect(int x0, int z0) {
        List<Hoodoo> found = new ArrayList<>(16);
        int minCx = Math.floorDiv(x0 - REACH, CELL);
        int maxCx = Math.floorDiv(x0 + 15 + REACH, CELL);
        int minCz = Math.floorDiv(z0 - REACH, CELL);
        int maxCz = Math.floorDiv(z0 + 15 + REACH, CELL);
        for (int cx = minCx; cx <= maxCx; cx++) {
            for (int cz = minCz; cz <= maxCz; cz++) {
                Hoodoo hoodoo = this.at(cx, cz);
                if (hoodoo != null) {
                    found.add(hoodoo);
                }
            }
        }
        return found;
    }

    /** 格子 (cx, cz) 里的石柱；这一格没有时为 null。 */
    private Hoodoo at(int cx, int cz) {
        long seed = this.terrain.cellSeed("hoodoo", cx, cz);
        double strength = this.field(cx * CELL + CELL / 2, cz * CELL + CELL / 2);
        if (strength <= 0) {
            return null;
        }
        // 剧场边缘的柱子稀一些
        if (ExoticTerrain.hash01(seed, 0, 0) > strength * 0.95) {
            return null;
        }
        int x = cx * CELL + 1 + (int) (ExoticTerrain.hash01(seed, 1, 0) * (CELL - 2));
        int z = cz * CELL + 1 + (int) (ExoticTerrain.hash01(seed, 0, 1) * (CELL - 2));
        if (!this.terrain.dry(x, z)) {
            return null;
        }
        double radius = 1.1 + ExoticTerrain.hash01(seed, 2, 2) * 1.3;
        if (this.terrain.keepout.blocked(x, z, (int) Math.ceil(radius) + 2)) {
            return null;
        }
        int height = (int) ((BASE_HEIGHT + ExoticTerrain.hash01(seed, 3, 3) * RELIEF) * strength * this.terrain.lift);
        if (height < 4) {
            return null;
        }
        return new Hoodoo(x, z, (int) Math.floor(this.terrain.ground.height(x, z)), height, radius);
    }

    /** 这一列是不是在这根柱子里；是就写出方块段。 */
    private boolean shaft(int x, int z, Hoodoo hoodoo, int ground, List<ExoticTerrain.Span> out) {
        double dx = x + 0.5 - (hoodoo.x() + 0.5);
        double dz = z + 0.5 - (hoodoo.z() + 0.5);
        double d = Math.sqrt(dx * dx + dz * dz);
        if (d > hoodoo.radius()) {
            return false;
        }
        int top = this.terrain.clampTop(hoodoo.base() + hoodoo.height());
        // 柱身比柱帽细：离柱心越远的那一圈只有柱帽那几格
        boolean core = d <= Math.max(0.8, hoodoo.radius() - 0.9);
        int bottom = core ? ground + 1 : top - CAP + 1;
        if (top - CAP < bottom) {
            return false;
        }
        int before = out.size();
        ExoticTerrain.rooted(out, bottom, top - CAP, ExoticTerrain.Material.SHAFT, core ? ground : bottom - 1,
                core ? ExoticTerrain.MAX_ROOT : 0);
        if (out.size() == before && core) {
            return false;
        }
        out.add(new ExoticTerrain.Span(top - CAP + 1, top, ExoticTerrain.Material.CAPROCK, false));
        return true;
    }

    /** 这一列在剧场里的强度：0 是场外，1 是场心。 */
    private double field(int x, int z) {
        int fx = Math.floorDiv(x, FIELD);
        int fz = Math.floorDiv(z, FIELD);
        double best = 0;
        for (int i = -1; i <= 1; i++) {
            for (int j = -1; j <= 1; j++) {
                long seed = this.terrain.cellSeed("hoodoo_field", fx + i, fz + j);
                if (ExoticTerrain.hash01(seed, 5, 5) > 0.58) {
                    continue;
                }
                double cx = (fx + i) * FIELD + ExoticTerrain.hash01(seed, 1, 0) * FIELD;
                double cz = (fz + j) * FIELD + ExoticTerrain.hash01(seed, 0, 1) * FIELD;
                double radius = FIELD_RADIUS * (0.55 + ExoticTerrain.hash01(seed, 2, 2) * 0.45);
                double d = Math.sqrt((x - cx) * (x - cx) + (z - cz) * (z - cz));
                best = Math.max(best, ExoticTerrain.smooth(radius, radius * 0.3, d));
            }
        }
        return best;
    }
}
