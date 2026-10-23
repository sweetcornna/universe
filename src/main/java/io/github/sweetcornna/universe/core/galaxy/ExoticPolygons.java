package io.github.sweetcornna.universe.core.galaxy;

import java.util.List;

/**
 * 多边形地面（ADR-0030 §二）：平地上一张多边形的网。冻土一冬一冬地裂，裂缝里灌进冰就成冰楔多边形（火星和西伯利亚）；
 * 盐湖的卤水蒸干，盐壳沿裂缝拱起来（察尔汗、死谷）；冻融反复把碎石推到缝边，就成石环（挪威和加拿大的冻土）。
 *
 * <p>网格用随机点的维诺图：每个格子里一个点，一列归最近的那个点；离最近和次近两点的距离差不多时，这一列就在边上，
 * 边上垒一道矮脊。按星球温度选边的材质：很冷用冰楔，热而干用盐壳，其余用碎石。整片地只长在干的平地上，坡上不长。
 */
final class ExoticPolygons implements ExoticTerrain.Formation {
    /** 维诺点按这么大的格子分布：一块多边形大致这么宽。 */
    private static final int CELL = 11;
    /** 距离差小于这个数就算在边上（格）。 */
    private static final double EDGE = 0.85;
    /** 矮脊高几格。 */
    private static final int RIDGE = 1;
    /** 坡度超过这个数（格/格）就不长：多边形只在平地上。 */
    private static final double MAX_SLOPE = 0.55;
    /** 结冰的温度上限（K）。 */
    private static final double FREEZING = 268;
    /** 盐壳的温度下限（K）。 */
    private static final double ARID = 295;

    private final ExoticTerrain terrain;
    private final ExoticTerrain.Material edge;

    ExoticPolygons(ExoticTerrain terrain) {
        this.terrain = terrain;
        this.edge = terrain.temperature <= FREEZING ? ExoticTerrain.Material.ICE
                : terrain.temperature >= ARID ? ExoticTerrain.Material.SALT
                        : ExoticTerrain.Material.STONES;
    }

    @Override
    public ExoticTerrain.Prepared prepare(int x0, int z0, ExoticTerrain.Columns columns) {
        return (x, z, dx, dz, ground, state, out) -> {
            if (state != ExoticTerrain.DRY || this.terrain.keepout.blocked(x, z, 1)) {
                return;
            }
            if (this.slope(x, z) > MAX_SLOPE) {
                return;
            }
            if (!this.onEdge(x, z)) {
                return;
            }
            int top = this.terrain.clampTop(ground + RIDGE);
            if (top <= ground) {
                return;
            }
            out.add(new ExoticTerrain.Span(ground + 1, top, this.edge, false));
        };
    }

    /** 这一列是不是在两块多边形的交界上。 */
    private boolean onEdge(int x, int z) {
        double px = x + 0.5;
        double pz = z + 0.5;
        int cx = Math.floorDiv(x, CELL);
        int cz = Math.floorDiv(z, CELL);
        double nearest = Double.MAX_VALUE;
        double second = Double.MAX_VALUE;
        for (int i = -1; i <= 1; i++) {
            for (int j = -1; j <= 1; j++) {
                long seed = this.terrain.cellSeed("polygon", cx + i, cz + j);
                double sx = (cx + i) * CELL + ExoticTerrain.hash01(seed, 1, 0) * CELL;
                double sz = (cz + j) * CELL + ExoticTerrain.hash01(seed, 0, 1) * CELL;
                double d = Math.sqrt((px - sx) * (px - sx) + (pz - sz) * (pz - sz));
                if (d < nearest) {
                    second = nearest;
                    nearest = d;
                } else if (d < second) {
                    second = d;
                }
            }
        }
        return second - nearest < EDGE;
    }

    /** 这一列附近的坡度：四个方向上的高差取最大。 */
    private double slope(int x, int z) {
        double h = this.terrain.ground.height(x, z);
        double max = 0;
        max = Math.max(max, Math.abs(this.terrain.ground.height(x + 2, z) - h) / 2);
        max = Math.max(max, Math.abs(this.terrain.ground.height(x - 2, z) - h) / 2);
        max = Math.max(max, Math.abs(this.terrain.ground.height(x, z + 2) - h) / 2);
        max = Math.max(max, Math.abs(this.terrain.ground.height(x, z - 2) - h) / 2);
        return max;
    }
}
