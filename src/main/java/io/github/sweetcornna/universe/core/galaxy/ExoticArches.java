package io.github.sweetcornna.universe.core.galaxy;

import java.util.ArrayList;
import java.util.List;

import io.github.sweetcornna.universe.core.procgen.Seeds;

/**
 * 天然石拱（ADR-0030 §二）：砂岩先被侵蚀成一道道薄薄的石鳍，石鳍中间掏穿就成了石拱（拱门国家公园）。
 *
 * <p>一处石鳍群里几道平行的鳍，走向一致；有的鳍上开一个洞，剩下的部分就是拱。按列生成：把这一列投到鳍的走向上，
 * 算出离鳍面的距离和沿鳍的位置，在洞的范围内只保留洞顶那一段（拱背），洞外是整道鳍。两个拱脚各自
 * {@link ExoticTerrain#rooted} 到自己那一列的地面，不会悬空。
 */
final class ExoticArches implements ExoticTerrain.Formation {
    /** 石鳍群按这么大的格子分布。 */
    private static final int CELL = 96;
    /** 一群几道鳍。 */
    private static final int MAX_FINS = 4;
    /** 鳍之间隔几格。 */
    private static final int SPACING = 9;
    /** 鳍的长度。 */
    private static final int BASE_LENGTH = 34;
    private static final int LENGTH_RELIEF = 30;
    /** 鳍的高度。 */
    private static final int BASE_HEIGHT = 12;
    private static final int HEIGHT_RELIEF = 12;
    /** 鳍有多厚（半厚，格）。 */
    private static final double THICKNESS = 1.6;

    /**
     * 一道石鳍。
     *
     * @param x     鳍的中心
     * @param z     鳍的中心
     * @param base  鳍脚的地面高度
     * @param ux    走向（单位向量）
     * @param uz    走向
     * @param hole  洞在鳍上的中心位置（沿走向的参数，0 是中心）；没有洞时是 NaN
     * @param holeW 洞的半宽
     * @param holeH 洞顶高（从鳍脚算起）
     */
    private record Fin(double x, double z, int base, double ux, double uz, double length, int height,
            double hole, double holeW, double holeH) {
    }

    private final ExoticTerrain terrain;

    ExoticArches(ExoticTerrain terrain) {
        this.terrain = terrain;
    }

    @Override
    public ExoticTerrain.Prepared prepare(int x0, int z0, ExoticTerrain.Columns columns) {
        List<Fin> fins = this.collect(x0, z0);
        return (x, z, dx, dz, ground, state, out) -> {
            if (state != ExoticTerrain.DRY) {
                return;
            }
            for (Fin fin : fins) {
                if (this.fin(x, z, fin, ground, out)) {
                    return;
                }
            }
        };
    }

    /** 碰到这个区块的鳍。 */
    private List<Fin> collect(int x0, int z0) {
        List<Fin> found = new ArrayList<>(MAX_FINS);
        int reach = BASE_LENGTH + LENGTH_RELIEF + MAX_FINS * SPACING;
        int minCx = Math.floorDiv(x0 - reach, CELL);
        int maxCx = Math.floorDiv(x0 + 15 + reach, CELL);
        int minCz = Math.floorDiv(z0 - reach, CELL);
        int maxCz = Math.floorDiv(z0 + 15 + reach, CELL);
        for (int cx = minCx; cx <= maxCx; cx++) {
            for (int cz = minCz; cz <= maxCz; cz++) {
                this.group(cx, cz, found);
            }
        }
        return found;
    }

    /** 格子 (cx, cz) 里的一处石鳍群。 */
    private void group(int cx, int cz, List<Fin> out) {
        long seed = this.terrain.cellSeed("arch", cx, cz);
        if (ExoticTerrain.hash01(seed, 0, 0) > 0.6) {
            return;
        }
        int gx = cx * CELL + (int) (ExoticTerrain.hash01(seed, 1, 0) * CELL);
        int gz = cz * CELL + (int) (ExoticTerrain.hash01(seed, 0, 1) * CELL);
        if (!this.terrain.dry(gx, gz)) {
            return;
        }
        double yaw = ExoticTerrain.hash01(seed, 2, 2) * Math.PI;
        double ux = Math.cos(yaw);
        double uz = Math.sin(yaw);
        // 鳍并排排开的方向：走向转 90°
        double nx = -uz;
        double nz = ux;
        int fins = 1 + (int) (ExoticTerrain.hash01(seed, 3, 3) * MAX_FINS);
        for (int i = 0; i < fins; i++) {
            long s = Seeds.derive(seed, i);
            double offset = (i - (fins - 1) / 2.0) * SPACING;
            double fx = gx + 0.5 + nx * offset;
            double fz = gz + 0.5 + nz * offset;
            int ix = (int) Math.floor(fx);
            int iz = (int) Math.floor(fz);
            if (!this.terrain.dry(ix, iz)) {
                continue;
            }
            double length = BASE_LENGTH + ExoticTerrain.hash01(s, 1, 1) * LENGTH_RELIEF;
            if (this.terrain.keepout.blocked(ix, iz, (int) (length / 2) + 2)) {
                continue;
            }
            int height = (int) ((BASE_HEIGHT + ExoticTerrain.hash01(s, 2, 2) * HEIGHT_RELIEF) * this.terrain.lift);
            // 一半的鳍上开洞成拱，另一半还是整道鳍
            boolean arch = ExoticTerrain.hash01(s, 3, 3) < 0.55;
            double hole = arch ? (ExoticTerrain.hash01(s, 4, 4) - 0.5) * length * 0.5 : Double.NaN;
            double holeW = arch ? 4 + ExoticTerrain.hash01(s, 5, 5) * 8 : 0;
            double holeH = arch ? height * (0.5 + ExoticTerrain.hash01(s, 6, 6) * 0.25) : 0;
            out.add(new Fin(fx, fz, (int) Math.floor(this.terrain.ground.height(ix, iz)), ux, uz, length, height,
                    hole, holeW, holeH));
        }
    }

    /** 这一列落在这道鳍里的那一段。 */
    private boolean fin(int x, int z, Fin fin, int ground, List<ExoticTerrain.Span> out) {
        double px = x + 0.5 - fin.x();
        double pz = z + 0.5 - fin.z();
        // 沿走向的位置和离鳍面的距离
        double along = px * fin.ux() + pz * fin.uz();
        double across = Math.abs(-px * fin.uz() + pz * fin.ux());
        double half = fin.length() / 2;
        if (Math.abs(along) > half || across > THICKNESS) {
            return false;
        }
        // 鳍两端削薄、变矮
        double taper = ExoticTerrain.smooth(half, half * 0.6, Math.abs(along));
        if (across > THICKNESS * Math.max(0.35, taper)) {
            return false;
        }
        int height = (int) (fin.height() * taper);
        if (height < 2) {
            return false;
        }
        int top = this.terrain.clampTop(fin.base() + height);
        if (top <= ground) {
            return false;
        }
        if (!Double.isNaN(fin.hole())) {
            double d = Math.abs(along - fin.hole());
            if (d < fin.holeW()) {
                // 洞里：洞顶那一段是拱背，拱背以下是空的。拱背太薄就不画，免得剩一条浮空的板
                int arch = (int) (fin.base() + fin.holeH() * Math.sqrt(1 - d / fin.holeW() * (d / fin.holeW())));
                int bottom = Math.max(arch + 1, ground + 1);
                if (top - bottom < 1) {
                    return false;
                }
                out.add(new ExoticTerrain.Span(bottom, top, ExoticTerrain.Material.ARCH, false));
                return true;
            }
        }
        // 拱脚和整道鳍：底端补到这一列真实的地面
        int before = out.size();
        ExoticTerrain.rooted(out, Math.min(ground + 1, fin.base() + 1), top, ExoticTerrain.Material.ARCH, ground,
                ExoticTerrain.MAX_ROOT);
        return out.size() > before;
    }
}
