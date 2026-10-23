package io.github.sweetcornna.universe.core.galaxy;

import java.util.List;

/**
 * 柱状玄武岩（ADR-0030 §二）：熔岩慢慢冷下来裂成的六棱柱，一片片挤在一起，柱顶高低错落成石阶，一直伸进浅海
 * （巨人堤道）；偶尔一整块台地剩下一座孤立的柱状岩塔（魔鬼塔）。
 *
 * <p>柱子的平面用一张六边形的网：先把 (x, z) 折算到六边形网格的格心，同一个格心的列属于同一根柱子，柱高由格心派生。
 * 相邻两根柱子之间的缝是节理（{@link ExoticTerrain.Material#JOINT}），比两边低一格。六边形网格和区块无关，
 * 跨区块的同一根柱子两边算出来一样高。
 */
final class ExoticBasalt implements ExoticTerrain.Formation {
    /** 六边形柱子的外接圆半径（格）：一根柱子大致 3 格粗。 */
    private static final double RADIUS = 1.8;
    /** 成片的柱群按这么大的格子分布。 */
    private static final int FIELD = 48;
    /** 柱群的半径（格）。 */
    private static final int FIELD_RADIUS = 34;
    /** 柱子露出地面的基准高度。 */
    private static final int BASE_HEIGHT = 5;
    /** 柱高的起伏幅度。 */
    private static final int RELIEF = 9;
    /** 柱状玄武岩能伸进几格深的海。 */
    private static final int WADE = 5;
    /** 孤立岩塔按这么大的格子分布。 */
    private static final int TOWER_FIELD = 320;
    /** 岩塔的半径（格）。 */
    private static final int TOWER_RADIUS = 11;
    /** 岩塔的高度。 */
    private static final int TOWER_HEIGHT = 48;

    private final ExoticTerrain terrain;

    ExoticBasalt(ExoticTerrain terrain) {
        this.terrain = terrain;
    }

    @Override
    public ExoticTerrain.Prepared prepare(int x0, int z0, ExoticTerrain.Columns columns) {
        // 柱子本身逐列算（六边形网格和区块对齐无关），只有岩塔要先找出碰到这个区块的那几座
        List<int[]> towers = this.towers(x0, z0);
        return (x, z, dx, dz, ground, state, out) -> {
            if (state == ExoticTerrain.BLOCKED) {
                return;
            }
            for (int[] tower : towers) {
                if (this.tower(x, z, tower, ground, out)) {
                    return;
                }
            }
            this.column(x, z, ground, state, out);
        };
    }

    /** 中心落在这个区块 ±TOWER_RADIUS 以内的岩塔。 */
    private List<int[]> towers(int x0, int z0) {
        List<int[]> found = new java.util.ArrayList<>(1);
        int reach = TOWER_RADIUS + 1;
        int minCx = Math.floorDiv(x0 - reach, TOWER_FIELD);
        int maxCx = Math.floorDiv(x0 + 15 + reach, TOWER_FIELD);
        int minCz = Math.floorDiv(z0 - reach, TOWER_FIELD);
        int maxCz = Math.floorDiv(z0 + 15 + reach, TOWER_FIELD);
        for (int cx = minCx; cx <= maxCx; cx++) {
            for (int cz = minCz; cz <= maxCz; cz++) {
                long seed = this.terrain.cellSeed("basalt_tower", cx, cz);
                if (ExoticTerrain.hash01(seed, 0, 0) > 0.45) {
                    continue;
                }
                int tx = cx * TOWER_FIELD + (int) (ExoticTerrain.hash01(seed, 1, 0) * TOWER_FIELD);
                int tz = cz * TOWER_FIELD + (int) (ExoticTerrain.hash01(seed, 0, 1) * TOWER_FIELD);
                if (!this.terrain.dry(tx, tz) || this.terrain.keepout.blocked(tx, tz, TOWER_RADIUS + 4)) {
                    continue;
                }
                found.add(new int[] {tx, tz, (int) Math.floor(this.terrain.ground.height(tx, tz))});
            }
        }
        return found;
    }

    /** 岩塔：一座上下一般粗的柱状台地，侧面也是六棱柱，顶上平。 */
    private boolean tower(int x, int z, int[] tower, int ground, List<ExoticTerrain.Span> out) {
        double dx = x - tower[0];
        double dz = z - tower[1];
        double d = Math.sqrt(dx * dx + dz * dz);
        if (d > TOWER_RADIUS) {
            return false;
        }
        // 塔基比塔身宽，裙边向外铺开
        int height = (int) (TOWER_HEIGHT * this.terrain.lift * ExoticTerrain.smooth(TOWER_RADIUS, TOWER_RADIUS - 7, d));
        if (height < 2) {
            return false;
        }
        int base = tower[2];
        int top = this.terrain.clampTop(base + height);
        if (top <= ground) {
            return false;
        }
        // 塔顶和塔身：顶上一格是柱顶
        int before = out.size();
        ExoticTerrain.rooted(out, Math.min(ground + 1, base + 1), top - 1, ExoticTerrain.Material.COLUMN, ground,
                ExoticTerrain.MAX_ROOT);
        if (out.size() > before) {
            out.add(new ExoticTerrain.Span(top, top, ExoticTerrain.Material.COLUMN_TOP, false));
        }
        return true;
    }

    /** 成片的柱群里的一根柱子。 */
    private void column(int x, int z, int ground, byte state, List<ExoticTerrain.Span> out) {
        if (!this.terrain.shallow(x, z, WADE)) {
            return;
        }
        double strength = this.field(x, z);
        if (strength <= 0) {
            return;
        }
        long[] cell = hex(x, z);
        int cx = (int) cell[0];
        int cz = (int) cell[1];
        // 格心到这一列的距离：靠近柱边的那一圈是节理
        double[] center = hexCenter(cx, cz);
        double dx = x + 0.5 - center[0];
        double dz = z + 0.5 - center[1];
        boolean joint = Math.sqrt(dx * dx + dz * dz) > RADIUS * 0.72;

        long seed = this.terrain.cellSeed("basalt", cx, cz);
        if (ExoticTerrain.hash01(seed, 0, 0) > 0.93) {
            // 这根塌掉了，留个豁口
            return;
        }
        int height = (int) ((BASE_HEIGHT + ExoticTerrain.hash01(seed, 1, 1) * RELIEF) * strength * this.terrain.lift);
        if (height < 1) {
            return;
        }
        int top = this.terrain.clampTop(ground + height);
        if (joint) {
            // 节理比柱身低一格，整片柱群看出来一根根的分界
            ExoticTerrain.rooted(out, ground + 1, Math.max(ground + 1, top - 1), ExoticTerrain.Material.JOINT, ground,
                    ExoticTerrain.MAX_ROOT);
            return;
        }
        int before = out.size();
        ExoticTerrain.rooted(out, ground + 1, top - 1, ExoticTerrain.Material.COLUMN, ground, ExoticTerrain.MAX_ROOT);
        if (out.size() > before) {
            out.add(new ExoticTerrain.Span(top, top, ExoticTerrain.Material.COLUMN_TOP, state == ExoticTerrain.DRY));
        }
    }

    /** 这一列在柱群里的强度：0 是群外，1 是群心。 */
    private double field(int x, int z) {
        int cx = Math.floorDiv(x, FIELD);
        int cz = Math.floorDiv(z, FIELD);
        double best = 0;
        for (int i = -1; i <= 1; i++) {
            for (int j = -1; j <= 1; j++) {
                long seed = this.terrain.cellSeed("basalt_field", cx + i, cz + j);
                if (ExoticTerrain.hash01(seed, 7, 7) > 0.62) {
                    continue;
                }
                double fx = (cx + i) * FIELD + ExoticTerrain.hash01(seed, 1, 0) * FIELD;
                double fz = (cz + j) * FIELD + ExoticTerrain.hash01(seed, 0, 1) * FIELD;
                double radius = FIELD_RADIUS * (0.6 + ExoticTerrain.hash01(seed, 2, 2) * 0.4);
                double d = Math.sqrt((x - fx) * (x - fx) + (z - fz) * (z - fz));
                if (this.terrain.keepout.blocked((int) fx, (int) fz, (int) radius)) {
                    continue;
                }
                best = Math.max(best, ExoticTerrain.smooth(radius, radius * 0.35, d));
            }
        }
        return best;
    }

    /**
     * (x, z) 落在哪个六边形里：用轴向坐标（pointy-top）取整，再在三个候选格心里挑最近的。
     * 只看坐标，不看区块，跨区块的同一根柱子两边算出同一个格心。
     */
    static long[] hex(int x, int z) {
        double px = x + 0.5;
        double pz = z + 0.5;
        double q = (Math.sqrt(3) / 3 * px - 1.0 / 3 * pz) / RADIUS;
        double r = 2.0 / 3 * pz / RADIUS;
        double s = -q - r;
        long rq = Math.round(q);
        long rr = Math.round(r);
        long rs = Math.round(s);
        // 三个取整值之和必须是 0，差得最多的那个让步
        double dq = Math.abs(q - rq);
        double dr = Math.abs(r - rr);
        double ds = Math.abs(s - rs);
        if (dq > dr && dq > ds) {
            rq = -rr - rs;
        } else if (dr > ds) {
            rr = -rq - rs;
        }
        return new long[] {rq, rr};
    }

    /** 六边形格心的世界坐标。 */
    static double[] hexCenter(long q, long r) {
        double x = RADIUS * (Math.sqrt(3) * q + Math.sqrt(3) / 2 * r);
        double z = RADIUS * (3.0 / 2 * r);
        return new double[] {x, z};
    }
}
