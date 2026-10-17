package io.github.sweetcornna.universe.core.ecology;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.github.sweetcornna.universe.core.procgen.Rng;

/**
 * 把一棵树拼成方块：主干从原点 (0, 0, 0) 向上长，原点下面一格是地面。
 *
 * <p>同一物种的树形相似，每棵树再按自己的随机数变化高度、分枝、倾斜方向。结果限定在
 * 水平 ±{@link #REACH} 格、高 {@link #MAX_HEIGHT} 格以内：世界生成时树从区块里长出来，
 * 只能写进相邻的区块。
 */
public final class TreeBuilder {
    /** 水平方向最远伸出几格。 */
    public static final int REACH = 8;
    /** 最高几格。 */
    public static final int MAX_HEIGHT = 26;

    private final TreeSpecies species;
    private final Rng rng;
    /** 打包的坐标 → 部位；同一格先放下的优先级低的部位会被高的覆盖。 */
    private final Map<Long, Part> blocks = new LinkedHashMap<>();

    private TreeBuilder(TreeSpecies species, Rng rng) {
        this.species = species;
        this.rng = rng;
    }

    /** 树的组成部分，按优先级从高到低。 */
    public enum Part {
        LOG_Y, LOG_X, LOG_Z,
        /** 肉质的实心方块：巨菌的菌盖、柱状肉茎的茎。 */
        CAP,
        CRYSTAL,
        BLOOM,
        LEAVES;

        public boolean log() {
            return this == LOG_Y || this == LOG_X || this == LOG_Z;
        }

        /** 主干一类的结构，生长时不能挤占已有的方块。 */
        public boolean structural() {
            return this.log() || this == CAP || this == CRYSTAL;
        }
    }

    /** 一格方块。 */
    public record Block(int x, int y, int z, Part part) {
    }

    public static List<Block> build(TreeSpecies species, Rng rng) {
        TreeBuilder builder = new TreeBuilder(species, rng);
        builder.grow();
        List<Block> result = new ArrayList<>(builder.blocks.size());
        builder.blocks.forEach((key, part) -> result.add(new Block(unpackX(key), unpackY(key), unpackZ(key), part)));
        return result;
    }

    private void grow() {
        int height = this.species.height() + this.rng.range(0, this.species.heightRange());
        switch (this.species.shape()) {
            case SHRUB -> this.shrub(height);
            case ROUND -> this.round(height);
            case CONIFER -> this.conifer(height);
            case UMBRELLA -> this.umbrella(height);
            case PALM -> this.palm(height);
            case WEEPING -> this.weeping(height);
            case MUSHROOM -> this.mushroom(height);
            case BAOBAB -> this.baobab(height);
            case SPIRAL -> this.spiral(height);
            case COLUMN -> this.column(height);
            case CRYSTAL -> this.crystal(height);
        }
        this.addBlooms();
    }

    // ---------------------------------------------------------------- 各种树形

    private void shrub(int height) {
        for (int y = 0; y < height; y++) {
            this.put(0, y, 0, Part.LOG_Y);
        }
        double r = this.species.canopy() + this.rng.range(0, 0.8);
        this.blob(0, height + 0.3, 0, r, Math.max(1, r * 0.7), r, 0.9);
    }

    private void round(int height) {
        double angle = this.rng.range(0, Math.PI * 2);
        double lean = this.species.lean() * height * 0.5;
        double topX = Math.cos(angle) * lean;
        double topZ = Math.sin(angle) * lean;
        this.trunk(0, 0, topX, height, topZ);
        int canopy = this.species.canopy();
        int branches = this.rng.range(2, 4);
        for (int i = 0; i < branches; i++) {
            double a = angle + Math.PI * 2 * i / branches + this.rng.range(-0.5, 0.5);
            double y0 = height * this.rng.range(0.55, 0.8);
            double length = canopy * this.rng.range(0.6, 1.0);
            double x0 = topX * y0 / height;
            double z0 = topZ * y0 / height;
            double x1 = x0 + Math.cos(a) * length;
            double z1 = z0 + Math.sin(a) * length;
            double y1 = y0 + length * 0.7;
            this.line(x0, y0, z0, x1, y1, z1);
            this.blob(x1, y1 + 0.5, z1, canopy * 0.65, canopy * 0.5, canopy * 0.65, this.species.leafDensity());
        }
        this.blob(topX, height + 0.5, topZ, canopy, canopy * 0.8, canopy, this.species.leafDensity());
    }

    private void conifer(int height) {
        this.trunk(0, 0, 0, height, 0);
        int canopy = this.species.canopy();
        int bottom = Math.max(2, (int) Math.round(height * this.rng.range(0.2, 0.35)));
        int top = height + 1;
        int tier = this.rng.range(2, 3);
        for (int y = bottom; y <= top; y++) {
            double t = (double) (y - bottom) / (top - bottom);
            // 一层一层的裙边：每层底部最宽，往上收窄
            double step = (double) ((y - bottom) % tier) / tier;
            double r = canopy * Math.pow(1 - t, 0.9) * (1 - 0.35 * step) + 0.4;
            this.disc(0, y, 0, r, this.species.leafDensity());
        }
        this.put(0, top, 0, Part.LEAVES);
        this.put(0, top + 1, 0, Part.LEAVES);
    }

    private void umbrella(int height) {
        double angle = this.rng.range(0, Math.PI * 2);
        double lean = this.species.lean() * height * 0.6;
        double topX = Math.cos(angle) * lean;
        double topZ = Math.sin(angle) * lean;
        this.trunk(0, 0, topX, height, topZ);
        int canopy = this.species.canopy();
        int branches = this.rng.range(3, 5);
        for (int i = 0; i < branches; i++) {
            double a = Math.PI * 2 * i / branches + this.rng.range(-0.4, 0.4);
            double length = canopy * this.rng.range(0.5, 0.8);
            double x1 = topX + Math.cos(a) * length;
            double z1 = topZ + Math.sin(a) * length;
            double y1 = height + this.rng.range(0.5, 2);
            this.line(topX, height - 1, topZ, x1, y1, z1);
            this.blob(x1, y1 + 0.8, z1, canopy * 0.55, 1.1, canopy * 0.55, this.species.leafDensity());
        }
        // 扁平的伞盖
        this.blob(topX, height + 1.5, topZ, canopy, 1.2, canopy, this.species.leafDensity());
    }

    private void palm(int height) {
        double angle = this.rng.range(0, Math.PI * 2);
        double bend = this.species.lean() * height * 0.6;
        double px = 0;
        double pz = 0;
        for (int y = 0; y <= height; y++) {
            double t = (double) y / height;
            double x = Math.cos(angle) * bend * t * t;
            double z = Math.sin(angle) * bend * t * t;
            if (y > 0) {
                this.line(px, y - 1, pz, x, y, z);
            } else {
                this.put(0, 0, 0, Part.LOG_Y);
            }
            px = x;
            pz = z;
        }
        int canopy = this.species.canopy();
        int fronds = this.rng.range(6, 9);
        double top = height + 1;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                this.put(round(px) + dx, (int) top, round(pz) + dz, Part.LEAVES);
            }
        }
        this.put(round(px), (int) top + 1, round(pz), Part.LEAVES);
        for (int i = 0; i < fronds; i++) {
            double a = Math.PI * 2 * i / fronds + this.rng.range(-0.25, 0.25);
            double length = canopy + this.rng.range(0.5, 2);
            double droop = this.rng.range(1.8, 3);
            for (double s = 0.6; s <= length; s += 0.5) {
                double x = px + Math.cos(a) * s;
                double z = pz + Math.sin(a) * s;
                double y = top + 0.4 - Math.pow(s / length, 2) * droop + (s < length * 0.4 ? 0.3 : 0);
                this.put(round(x), (int) Math.floor(y), round(z), Part.LEAVES);
            }
        }
        if (this.species.bloomChance() > 0) {
            int fruits = this.rng.range(2, 4);
            for (int i = 0; i < fruits; i++) {
                double a = this.rng.range(0, Math.PI * 2);
                this.putIfEmpty(round(px + Math.cos(a)), height - 1, round(pz + Math.sin(a)), Part.BLOOM);
            }
        }
    }

    private void weeping(int height) {
        this.trunk(0, 0, 0, height, 0);
        int canopy = this.species.canopy();
        // 圆顶形的树冠，几根粗枝撑开
        int branches = this.rng.range(3, 4);
        for (int i = 0; i < branches; i++) {
            double a = Math.PI * 2 * i / branches + this.rng.range(-0.4, 0.4);
            double length = canopy * 0.6;
            this.line(0, height - 1, 0, Math.cos(a) * length, height + 0.5, Math.sin(a) * length);
        }
        this.blob(0, height + 0.8, 0, canopy, canopy * 0.55, canopy, this.species.leafDensity());
        // 只从树冠外缘垂下枝条，树下留出空间
        List<int[]> hangs = new ArrayList<>();
        for (Map.Entry<Long, Part> entry : this.blocks.entrySet()) {
            long key = entry.getKey();
            int x = unpackX(key);
            int y = unpackY(key);
            int z = unpackZ(key);
            if (entry.getValue() == Part.LEAVES && Math.hypot(x, z) >= canopy * 0.55
                    && !this.blocks.containsKey(pack(x, y - 1, z))) {
                hangs.add(new int[] {x, y, z});
            }
        }
        for (int[] start : hangs) {
            if (!this.rng.chance(0.4)) {
                continue;
            }
            int length = this.rng.range(1, Math.max(1, Math.min(5, start[1] - 2)));
            for (int i = 1; i <= length; i++) {
                this.putIfEmpty(start[0], start[1] - i, start[2], Part.LEAVES);
            }
        }
    }

    private void mushroom(int height) {
        double offset = this.species.thick() ? 0.5 : 0;
        int canopy = this.species.canopy();
        int capHeight = Math.max(2, (int) Math.round(canopy * this.rng.range(0.5, 0.8)));
        // 菌柄一直通到菌盖顶，菌盖才和它连在一起
        this.trunk(0, 0, 0, height + capHeight - 1, 0);
        for (int dy = -1; dy <= capHeight; dy++) {
            double r = capRadius(canopy, capHeight, dy);
            // 上一层盖不住的部分也要填上，上下两层才共面相连
            double above = dy == capHeight ? 0 : capRadius(canopy, capHeight, dy + 1);
            int y = height + dy;
            int reach = (int) Math.ceil(r);
            for (int x = -reach - 1; x <= reach + 1; x++) {
                for (int z = -reach - 1; z <= reach + 1; z++) {
                    double d = Math.hypot(x - offset, z - offset);
                    if (dy == -1) {
                        // 菌盖边缘向下卷一圈
                        if (d <= canopy + 0.5 && d > canopy - 0.6) {
                            this.put(x, y, z, Part.CAP);
                        }
                    } else if (d <= r && (d > r - 1.3 || d > above - 1.0 || dy == capHeight)) {
                        this.put(x, y, z, Part.CAP);
                    } else if (dy == 0 && d <= r - 1 && d > 1 && this.species.bloomChance() > 0 && this.rng.chance(0.35)) {
                        // 菌褶
                        this.put(x, y, z, Part.BLOOM);
                    }
                }
            }
        }
    }

    private static double capRadius(int canopy, int capHeight, int dy) {
        double t = Math.max(0, (double) dy / (capHeight + 0.5));
        return (canopy + 0.5) * Math.sqrt(Math.max(0, 1 - t * t));
    }

    private void baobab(int height) {
        // 基部三格粗，往上两格粗
        int base = (int) Math.round(height * 0.5);
        for (int y = 0; y < height; y++) {
            for (int x = -1; x <= 2; x++) {
                for (int z = -1; z <= 2; z++) {
                    boolean core = x >= 0 && x <= 1 && z >= 0 && z <= 1;
                    boolean corner = (x == -1 || x == 2) && (z == -1 || z == 2);
                    if (core || y < base && !corner && this.rng.chance(0.85)) {
                        this.put(x, y, z, Part.LOG_Y);
                    }
                }
            }
        }
        int branches = this.rng.range(3, 5);
        for (int i = 0; i < branches; i++) {
            double a = Math.PI * 2 * i / branches + this.rng.range(-0.4, 0.4);
            double length = this.rng.range(2, 3.5);
            double x1 = 0.5 + Math.cos(a) * length;
            double z1 = 0.5 + Math.sin(a) * length;
            double y1 = height + this.rng.range(0.5, 2);
            this.line(0.5, height - 1, 0.5, x1, y1, z1);
            double r = this.species.canopy() * this.rng.range(0.6, 0.9);
            this.blob(x1, y1 + 0.6, z1, r, r * 0.7, r, this.species.leafDensity());
        }
    }

    private void spiral(int height) {
        double radius = this.species.canopy() + this.rng.range(0, 0.8);
        double turns = this.rng.range(0.8, 1.5);
        double phase = this.rng.range(0, Math.PI * 2);
        double px = 0;
        double pz = 0;
        this.put(0, 0, 0, Part.LOG_Y);
        for (int y = 1; y <= height; y++) {
            double t = (double) y / height;
            // 螺旋从中心起步，半径逐渐展开
            double r = radius * Math.min(1, t * 2.5);
            double a = phase + t * turns * Math.PI * 2;
            double x = Math.cos(a) * r;
            double z = Math.sin(a) * r;
            this.line(px, y - 1, pz, x, y, z);
            px = x;
            pz = z;
            if (y > 2 && y < height - 1 && y % 3 == 0 && this.rng.chance(0.6)) {
                Part orb = this.species.bloomChance() > 0 && this.rng.chance(0.5) ? Part.BLOOM : Part.LEAVES;
                this.sphere(x * 1.6, y, z * 1.6, 1.2, orb);
            }
        }
        this.blob(px, height + 1.5, pz, 2.2, 1.8, 2.2, this.species.leafDensity());
    }

    private void column(int height) {
        for (int y = 0; y < height; y++) {
            this.put(0, y, 0, Part.CAP);
        }
        int arms = this.species.canopy();
        List<int[]> directions = new ArrayList<>(List.of(new int[] {1, 0}, new int[] {-1, 0}, new int[] {0, 1}, new int[] {0, -1}));
        for (int i = 0; i < arms && !directions.isEmpty() && height >= 4; i++) {
            int[] d = directions.remove(this.rng.nextInt(directions.size()));
            int y0 = this.rng.range(1, height - 3);
            int out = this.rng.range(1, 2);
            for (int s = 1; s <= out; s++) {
                this.put(d[0] * s, y0, d[1] * s, Part.CAP);
            }
            int up = this.rng.range(1, Math.max(1, height - y0 - 2));
            for (int s = 1; s <= up; s++) {
                this.put(d[0] * out, y0 + s, d[1] * out, Part.CAP);
            }
            if (this.species.bloomChance() > 0 && this.rng.chance(0.5)) {
                this.putIfEmpty(d[0] * out, y0 + up + 1, d[1] * out, Part.BLOOM);
            }
        }
        if (this.species.bloomChance() > 0) {
            this.putIfEmpty(0, height, 0, Part.BLOOM);
        }
    }

    private void crystal(int height) {
        int spires = this.rng.range(3, 6);
        for (int i = 0; i < spires; i++) {
            boolean main = i == 0;
            double a = this.rng.range(0, Math.PI * 2);
            double tilt = main ? this.rng.range(0, 0.15) : this.rng.range(0.25, 0.6);
            double length = main ? height : height * this.rng.range(0.4, 0.75);
            double bx = main ? 0 : Math.cos(a) * this.rng.range(0.5, 1.5);
            double bz = main ? 0 : Math.sin(a) * this.rng.range(0.5, 1.5);
            double ex = bx + Math.cos(a) * Math.sin(tilt) * length;
            double ez = bz + Math.sin(a) * Math.sin(tilt) * length;
            double ey = Math.cos(tilt) * length;
            // 中轴先连成一线，再按粗细裹上一层
            this.line(bx, 0, bz, ex, ey, ez, Part.CRYSTAL);
            int steps = (int) Math.ceil(length * 2);
            for (int s = 0; s <= steps; s++) {
                double t = (double) s / steps;
                double x = bx + (ex - bx) * t;
                double y = ey * t;
                double z = bz + (ez - bz) * t;
                double r = (this.species.canopy() * 0.45) * (1 - t) + 0.3;
                this.sphere(x, y, z, r, Part.CRYSTAL);
            }
        }
    }

    // ---------------------------------------------------------------- 基本形状

    /** 主干：从地面到 (topX, height, topZ)，粗干是 2×2。 */
    private void trunk(double x0, double z0, double topX, int height, double topZ) {
        if (this.species.thick()) {
            for (int dx = 0; dx <= 1; dx++) {
                for (int dz = 0; dz <= 1; dz++) {
                    this.line(x0 + dx, 0, z0 + dz, topX + dx, height, topZ + dz);
                }
            }
        } else {
            this.line(x0, 0, z0, topX, height, topZ);
        }
    }

    /** 一段树枝：沿线放木头，相邻两格总是共面相连，木纹方向取这段的主方向。 */
    private void line(double x0, double y0, double z0, double x1, double y1, double z1) {
        this.line(x0, y0, z0, x1, y1, z1, null);
    }

    /** 同上，{@code part} 为 null 时按方向选木头，否则全用这个部位。 */
    private void line(double x0, double y0, double z0, double x1, double y1, double z1, Part part) {
        double dx = x1 - x0;
        double dy = y1 - y0;
        double dz = z1 - z0;
        Part axis;
        if (part != null) {
            axis = part;
        } else if (Math.abs(dy) >= Math.abs(dx) && Math.abs(dy) >= Math.abs(dz)) {
            axis = Part.LOG_Y;
        } else {
            axis = Math.abs(dx) >= Math.abs(dz) ? Part.LOG_X : Part.LOG_Z;
        }
        int steps = (int) Math.ceil(Math.max(Math.abs(dx), Math.max(Math.abs(dy), Math.abs(dz))) * 3) + 1;
        int px = round(x0);
        int py = (int) Math.round(y0);
        int pz = round(z0);
        this.put(px, py, pz, axis);
        for (int s = 1; s <= steps; s++) {
            double t = (double) s / steps;
            int x = round(x0 + dx * t);
            int y = (int) Math.round(y0 + dy * t);
            int z = round(z0 + dz * t);
            // 一次只改一个坐标，免得两格只靠棱角相连
            while (x != px || y != py || z != pz) {
                if (y != py) {
                    py += Integer.signum(y - py);
                } else if (x != px) {
                    px += Integer.signum(x - px);
                } else {
                    pz += Integer.signum(z - pz);
                }
                this.put(px, py, pz, axis);
            }
        }
    }

    /** 椭球形的一团树叶，外缘按密度随机缺一些，看起来不那么规整。 */
    private void blob(double cx, double cy, double cz, double rx, double ry, double rz, double density) {
        int x0 = (int) Math.floor(cx - rx);
        int x1 = (int) Math.ceil(cx + rx);
        int y0 = (int) Math.floor(cy - ry);
        int y1 = (int) Math.ceil(cy + ry);
        int z0 = (int) Math.floor(cz - rz);
        int z1 = (int) Math.ceil(cz + rz);
        for (int x = x0; x <= x1; x++) {
            for (int y = y0; y <= y1; y++) {
                for (int z = z0; z <= z1; z++) {
                    double ddx = (x - cx) / (rx + 0.3);
                    double ddy = (y - cy) / (ry + 0.3);
                    double ddz = (z - cz) / (rz + 0.3);
                    double d = ddx * ddx + ddy * ddy + ddz * ddz;
                    if (d > 1) {
                        continue;
                    }
                    if (d > 0.55 && this.rng.nextDouble() > density * (1.6 - d)) {
                        continue;
                    }
                    this.putIfEmpty(x, y, z, Part.LEAVES);
                }
            }
        }
    }

    /** 水平的一圈树叶。 */
    private void disc(double cx, int y, double cz, double r, double density) {
        int reach = (int) Math.ceil(r);
        for (int x = -reach; x <= reach; x++) {
            for (int z = -reach; z <= reach; z++) {
                double d = Math.hypot(x + 0.0 - cx, z + 0.0 - cz) / (r + 0.3);
                if (d > 1 || d > 0.6 && this.rng.nextDouble() > density * (1.7 - d)) {
                    continue;
                }
                this.putIfEmpty(round(cx) + x, y, round(cz) + z, Part.LEAVES);
            }
        }
    }

    /** 实心的小球。 */
    private void sphere(double cx, double cy, double cz, double r, Part part) {
        int reach = (int) Math.ceil(r);
        for (int x = -reach; x <= reach; x++) {
            for (int y = -reach; y <= reach; y++) {
                for (int z = -reach; z <= reach; z++) {
                    int bx = round(cx) + x;
                    int by = (int) Math.round(cy) + y;
                    int bz = round(cz) + z;
                    double ddx = bx - cx;
                    double ddy = by - cy;
                    double ddz = bz - cz;
                    if (ddx * ddx + ddy * ddy + ddz * ddz <= (r + 0.35) * (r + 0.35)) {
                        this.put(bx, by, bz, part);
                    }
                }
            }
        }
    }

    /** 在树叶下面和外侧随机挂上花果。 */
    private void addBlooms() {
        double chance = this.species.bloomChance();
        if (chance <= 0) {
            return;
        }
        List<long[]> spots = new ArrayList<>();
        for (Map.Entry<Long, Part> entry : this.blocks.entrySet()) {
            if (entry.getValue() != Part.LEAVES) {
                continue;
            }
            long key = entry.getKey();
            int x = unpackX(key);
            int y = unpackY(key);
            int z = unpackZ(key);
            if (!this.blocks.containsKey(pack(x, y - 1, z)) && this.rng.chance(chance)) {
                spots.add(new long[] {x, y - 1, z});
            }
        }
        for (long[] spot : spots) {
            this.putIfEmpty((int) spot[0], (int) spot[1], (int) spot[2], Part.BLOOM);
        }
    }

    private void put(int x, int y, int z, Part part) {
        if (Math.abs(x) > REACH || Math.abs(z) > REACH || y < 0 || y >= MAX_HEIGHT) {
            return;
        }
        this.blocks.merge(pack(x, y, z), part, (old, added) -> added.ordinal() < old.ordinal() ? added : old);
    }

    private void putIfEmpty(int x, int y, int z, Part part) {
        if (!this.blocks.containsKey(pack(x, y, z))) {
            this.put(x, y, z, part);
        }
    }

    private static int round(double v) {
        return (int) Math.floor(v + 0.5);
    }

    static long pack(int x, int y, int z) {
        return ((long) (x & 0xFFFF) << 32) | ((long) (y & 0xFFFF) << 16) | (z & 0xFFFF);
    }

    static int unpackX(long key) {
        return (short) (key >>> 32);
    }

    static int unpackY(long key) {
        return (short) (key >>> 16);
    }

    static int unpackZ(long key) {
        return (short) key;
    }
}
