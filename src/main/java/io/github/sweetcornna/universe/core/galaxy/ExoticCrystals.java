package io.github.sweetcornna.universe.core.galaxy;

import java.util.ArrayList;
import java.util.List;

import io.github.sweetcornna.universe.core.procgen.Seeds;

/**
 * 巨晶（ADR-0030 §二）：几十格长的晶柱斜插出地面，一簇几根从同一处岔开，像奈卡水晶洞里那样横七竖八。
 *
 * <p>一簇的根在同一点，每根沿自己的方向斜着长出去。按列生成：把这一列的中心投到晶柱的轴上，算出离轴的距离，
 * 轴附近的那一段就是晶体占的高度区间。晶柱能长在水里（{@link ExoticTerrain#shallow}），熔岩里不长。
 */
final class ExoticCrystals implements ExoticTerrain.Formation {
    /** 晶簇按这么大的格子分布。 */
    private static final int CELL = 40;
    /** 一簇最多几根。 */
    private static final int MAX_SHARDS = 5;
    /** 晶柱的基准长度。 */
    private static final int BASE_LENGTH = 16;
    /** 长度的起伏幅度。 */
    private static final int RELIEF = 26;
    /** 晶柱最粗的半径。 */
    private static final double MAX_THICKNESS = 2.2;
    /** 晶柱能长进几格深的水里。 */
    private static final int WADE = 24;

    /**
     * 一根晶柱。
     *
     * @param x  根的位置
     * @param y  根的高度
     * @param z  根的位置
     * @param dx 轴的方向（单位向量）
     * @param dy 轴的方向
     * @param dz 轴的方向
     */
    private record Shard(double x, double y, double z, double dx, double dy, double dz, double length, double thickness) {
        /** 这一列的中心 (px, pz) 离轴最近的那一点在轴上的参数 t（限制在 [0, length]）。 */
        double project(double px, double pz) {
            // 水平面上投影：轴的水平分量
            double hx = this.dx;
            double hz = this.dz;
            double hh = hx * hx + hz * hz;
            if (hh < 1.0E-6) {
                return 0;
            }
            double t = ((px - this.x) * hx + (pz - this.z) * hz) / hh;
            return Math.clamp(t, 0, this.length);
        }

        double axisX(double t) {
            return this.x + this.dx * t;
        }

        double axisY(double t) {
            return this.y + this.dy * t;
        }

        double axisZ(double t) {
            return this.z + this.dz * t;
        }

        /** 越往尖端越细。 */
        double radiusAt(double t) {
            return this.thickness * (1 - 0.75 * t / this.length);
        }
    }

    private final ExoticTerrain terrain;

    ExoticCrystals(ExoticTerrain terrain) {
        this.terrain = terrain;
    }

    @Override
    public ExoticTerrain.Prepared prepare(int x0, int z0, ExoticTerrain.Columns columns) {
        List<Shard> shards = this.collect(x0, z0);
        return (x, z, dx, dz, ground, state, out) -> {
            if (state == ExoticTerrain.BLOCKED || shards.isEmpty()) {
                return;
            }
            double px = x + 0.5;
            double pz = z + 0.5;
            int lo = Integer.MAX_VALUE;
            int hi = Integer.MIN_VALUE;
            for (Shard shard : shards) {
                double t = shard.project(px, pz);
                double ax = shard.axisX(t);
                double az = shard.axisZ(t);
                double r = shard.radiusAt(t);
                double d = Math.sqrt((px - ax) * (px - ax) + (pz - az) * (pz - az));
                if (d > r) {
                    continue;
                }
                // 这一列在晶柱的横截面里：竖直方向上占 2 * sqrt(r² - d²) * (轴的竖直伸展) 这么高
                double half = Math.sqrt(r * r - d * d) + 0.5;
                double ay = shard.axisY(t);
                lo = Math.min(lo, (int) Math.floor(ay - half));
                hi = Math.max(hi, (int) Math.ceil(ay + half));
            }
            if (hi < lo) {
                return;
            }
            // 埋在地下的那一截不画，顶多露出地面；整根都在地下的不要
            int bottom = Math.max(lo, ground - 2);
            int top = this.terrain.clampTop(hi);
            if (top <= ground) {
                return;
            }
            out.add(new ExoticTerrain.Span(Math.max(bottom, this.terrain.seaLevel - WADE), top,
                    ExoticTerrain.Material.CRYSTAL, true));
        };
    }

    /** 碰到这个区块的晶柱：搜周围的格子，一根能横着伸出很远。 */
    private List<Shard> collect(int x0, int z0) {
        List<Shard> found = new ArrayList<>(MAX_SHARDS);
        int reach = BASE_LENGTH + RELIEF;
        int minCx = Math.floorDiv(x0 - reach, CELL);
        int maxCx = Math.floorDiv(x0 + 15 + reach, CELL);
        int minCz = Math.floorDiv(z0 - reach, CELL);
        int maxCz = Math.floorDiv(z0 + 15 + reach, CELL);
        for (int cx = minCx; cx <= maxCx; cx++) {
            for (int cz = minCz; cz <= maxCz; cz++) {
                this.cluster(cx, cz, found);
            }
        }
        return found;
    }

    /** 格子 (cx, cz) 里的一簇晶柱。 */
    private void cluster(int cx, int cz, List<Shard> out) {
        long seed = this.terrain.cellSeed("crystal", cx, cz);
        if (ExoticTerrain.hash01(seed, 0, 0) > 0.72) {
            return;
        }
        int x = cx * CELL + (int) (ExoticTerrain.hash01(seed, 1, 0) * CELL);
        int z = cz * CELL + (int) (ExoticTerrain.hash01(seed, 0, 1) * CELL);
        if (!this.terrain.shallow(x, z, WADE)) {
            return;
        }
        int shards = 1 + (int) (ExoticTerrain.hash01(seed, 2, 2) * MAX_SHARDS);
        if (this.terrain.keepout.blocked(x, z, BASE_LENGTH)) {
            return;
        }
        double baseY = Math.floor(this.terrain.ground.height(x, z));
        for (int i = 0; i < shards; i++) {
            long s = Seeds.derive(seed, i);
            // 方向：水平随便转，仰角 25°–80°（奈卡洞里的晶柱大多斜着）
            double yaw = ExoticTerrain.hash01(s, 1, 1) * Math.PI * 2;
            double pitch = Math.toRadians(25 + ExoticTerrain.hash01(s, 2, 2) * 55);
            double dy = Math.sin(pitch);
            double horizontal = Math.cos(pitch);
            double length = (BASE_LENGTH + ExoticTerrain.hash01(s, 3, 3) * RELIEF) * this.terrain.lift;
            double thickness = 0.9 + ExoticTerrain.hash01(s, 4, 4) * (MAX_THICKNESS - 0.9);
            // 根稍微错开一点，不是从一个点上长出来的
            double ox = (ExoticTerrain.hash01(s, 5, 5) - 0.5) * 4;
            double oz = (ExoticTerrain.hash01(s, 6, 6) - 0.5) * 4;
            out.add(new Shard(x + 0.5 + ox, baseY - 2, z + 0.5 + oz,
                    Math.cos(yaw) * horizontal, dy, Math.sin(yaw) * horizontal, length, thickness));
        }
    }
}
