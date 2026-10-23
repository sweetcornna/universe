package io.github.sweetcornna.universe.core.galaxy;

import java.util.ArrayList;
import java.util.List;

/**
 * 结核球（ADR-0030 §二）：泥里慢慢长出来的圆石球，海浪把外面的软泥冲走就露在滩上，半埋在地里（摩拉基大圆石）；
 * 大的那几颗表面裂开一道道白色的方解石纹。地上还撒着一层小黑球（火星上的"蓝莓"）。
 *
 * <p>球按格子分布，球心压在地面下一点，所以露出来的是一个球冠。半径大于 {@link #VEINED} 的球表面带白纹：
 * 纹路按球面上的经纬方向定，同一颗球从哪个区块看都一样。小黑球单独一层，密度由另一个派生键决定。
 */
final class ExoticConcretions implements ExoticTerrain.Formation {
    /** 大球按这么大的格子分布。 */
    private static final int CELL = 13;
    /** 球的基准半径。 */
    private static final double BASE_RADIUS = 1.8;
    /** 半径的起伏幅度。 */
    private static final double RELIEF = 2.6;
    /** 半径超过这个数的球带白纹。 */
    private static final double VEINED = 2.8;
    /** 球心埋在地面下多深（按半径的比例）。 */
    private static final double SINK = 0.35;
    /** 一颗球最远伸出自己的格子多少格。 */
    private static final int REACH = 5;
    /** 小黑球的格子。 */
    private static final int SPHERULE_CELL = 3;

    /** 一颗结核球。 */
    private record Boulder(double x, double y, double z, double radius, boolean veined, long seed) {
    }

    private final ExoticTerrain terrain;

    ExoticConcretions(ExoticTerrain terrain) {
        this.terrain = terrain;
    }

    @Override
    public ExoticTerrain.Prepared prepare(int x0, int z0, ExoticTerrain.Columns columns) {
        List<Boulder> boulders = this.collect(x0, z0);
        return (x, z, dx, dz, ground, state, out) -> {
            if (state != ExoticTerrain.DRY) {
                return;
            }
            boolean covered = false;
            for (Boulder boulder : boulders) {
                if (this.boulder(x, z, boulder, ground, out)) {
                    covered = true;
                }
            }
            if (!covered) {
                this.spherule(x, z, ground, out);
            }
        };
    }

    /** 碰到这个区块的球。 */
    private List<Boulder> collect(int x0, int z0) {
        List<Boulder> found = new ArrayList<>(8);
        int minCx = Math.floorDiv(x0 - REACH, CELL);
        int maxCx = Math.floorDiv(x0 + 15 + REACH, CELL);
        int minCz = Math.floorDiv(z0 - REACH, CELL);
        int maxCz = Math.floorDiv(z0 + 15 + REACH, CELL);
        for (int cx = minCx; cx <= maxCx; cx++) {
            for (int cz = minCz; cz <= maxCz; cz++) {
                Boulder boulder = this.at(cx, cz);
                if (boulder != null) {
                    found.add(boulder);
                }
            }
        }
        return found;
    }

    private Boulder at(int cx, int cz) {
        long seed = this.terrain.cellSeed("concretion", cx, cz);
        if (ExoticTerrain.hash01(seed, 0, 0) > 0.55) {
            return null;
        }
        int x = cx * CELL + (int) (ExoticTerrain.hash01(seed, 1, 0) * CELL);
        int z = cz * CELL + (int) (ExoticTerrain.hash01(seed, 0, 1) * CELL);
        if (!this.terrain.dry(x, z)) {
            return null;
        }
        double radius = (BASE_RADIUS + ExoticTerrain.hash01(seed, 2, 2) * RELIEF) * Math.sqrt(this.terrain.lift);
        if (this.terrain.keepout.blocked(x, z, (int) Math.ceil(radius) + 2)) {
            return null;
        }
        double ground = Math.floor(this.terrain.ground.height(x, z));
        return new Boulder(x + 0.5, ground - radius * SINK, z + 0.5, radius, radius > VEINED, seed);
    }

    /** 这一列落在球里的那一段。 */
    private boolean boulder(int x, int z, Boulder boulder, int ground, List<ExoticTerrain.Span> out) {
        double px = x + 0.5;
        double pz = z + 0.5;
        double dx = px - boulder.x();
        double dz = pz - boulder.z();
        double dsq = dx * dx + dz * dz;
        double rsq = boulder.radius() * boulder.radius();
        if (dsq >= rsq) {
            return false;
        }
        double half = Math.sqrt(rsq - dsq);
        int bottom = (int) Math.ceil(boulder.y() - half);
        int top = this.terrain.clampTop((int) Math.floor(boulder.y() + half));
        if (top <= ground) {
            return false;
        }
        // 埋进地里的那截不画，和地面齐平就行
        bottom = Math.max(bottom, ground - 1);
        if (top < bottom) {
            return false;
        }
        // 白纹：球面上按经纬方向分几道，纹路只在球的外壳那一层
        if (boulder.veined() && top - ground >= 2) {
            double angle = Math.atan2(dz, dx);
            double bands = 5 + Math.floor(ExoticTerrain.hash01(boulder.seed(), 9, 9) * 3);
            double phase = ExoticTerrain.hash01(boulder.seed(), 8, 8) * Math.PI;
            if (Math.abs(Math.sin(angle * bands + phase)) > 0.86 && Math.sqrt(dsq) > boulder.radius() * 0.45) {
                out.add(new ExoticTerrain.Span(bottom, top, ExoticTerrain.Material.VEIN, true));
                return true;
            }
        }
        out.add(new ExoticTerrain.Span(bottom, top, ExoticTerrain.Material.BOULDER, true));
        return true;
    }

    /** 地上撒的小黑球：一格一颗，贴在地面上。 */
    private void spherule(int x, int z, int ground, List<ExoticTerrain.Span> out) {
        int cx = Math.floorDiv(x, SPHERULE_CELL);
        int cz = Math.floorDiv(z, SPHERULE_CELL);
        long seed = this.terrain.cellSeed("spherule", cx, cz);
        if (ExoticTerrain.hash01(seed, 0, 0) > 0.3) {
            return;
        }
        int sx = cx * SPHERULE_CELL + (int) (ExoticTerrain.hash01(seed, 1, 0) * SPHERULE_CELL);
        int sz = cz * SPHERULE_CELL + (int) (ExoticTerrain.hash01(seed, 0, 1) * SPHERULE_CELL);
        if (sx != x || sz != z || this.terrain.keepout.blocked(x, z, 1)) {
            return;
        }
        int top = this.terrain.clampTop(ground + 1);
        if (top <= ground) {
            return;
        }
        out.add(new ExoticTerrain.Span(top, top, ExoticTerrain.Material.SPHERULE, false));
    }
}
