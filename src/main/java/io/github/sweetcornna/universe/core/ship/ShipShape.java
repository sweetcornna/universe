package io.github.sweetcornna.universe.core.ship;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 船身的形状：每个方块相对"原点格"的坐标。
 *
 * <p>原点格取底层、水平方向上最靠近几何中心的那一格，飞船实体就放在它的底面中心，绕它转。
 * 方块中心相对转轴都是整数格，所以转了 90° 的整数倍之后方块仍然对得上网格，拆船时能原样放回去。
 */
public final class ShipShape {
    private final int[] xs;
    private final int[] ys;
    private final int[] zs;
    private final int minX;
    private final int maxX;
    private final int maxY;
    private final int minZ;
    private final int maxZ;
    private final boolean[] exposed;

    public ShipShape(int[] xs, int[] ys, int[] zs) {
        if (xs.length != ys.length || xs.length != zs.length || xs.length == 0) {
            throw new IllegalArgumentException("bad shape");
        }
        this.xs = xs.clone();
        this.ys = ys.clone();
        this.zs = zs.clone();
        int x0 = Integer.MAX_VALUE;
        int x1 = Integer.MIN_VALUE;
        int y1 = Integer.MIN_VALUE;
        int z0 = Integer.MAX_VALUE;
        int z1 = Integer.MIN_VALUE;
        Set<Long> all = new HashSet<>();
        for (int i = 0; i < xs.length; i++) {
            x0 = Math.min(x0, xs[i]);
            x1 = Math.max(x1, xs[i]);
            y1 = Math.max(y1, ys[i]);
            z0 = Math.min(z0, zs[i]);
            z1 = Math.max(z1, zs[i]);
            all.add(ShipAssembler.pack(xs[i], ys[i], zs[i]));
        }
        this.minX = x0;
        this.maxX = x1;
        this.maxY = y1;
        this.minZ = z0;
        this.maxZ = z1;
        // 外露的方块（至少一面不挨着船身）才需要参与碰撞
        this.exposed = new boolean[xs.length];
        for (int i = 0; i < xs.length; i++) {
            this.exposed[i] = !all.contains(ShipAssembler.pack(xs[i] + 1, ys[i], zs[i]))
                    || !all.contains(ShipAssembler.pack(xs[i] - 1, ys[i], zs[i]))
                    || !all.contains(ShipAssembler.pack(xs[i], ys[i] + 1, zs[i]))
                    || !all.contains(ShipAssembler.pack(xs[i], ys[i] - 1, zs[i]))
                    || !all.contains(ShipAssembler.pack(xs[i], ys[i], zs[i] + 1))
                    || !all.contains(ShipAssembler.pack(xs[i], ys[i], zs[i] - 1));
        }
    }

    /**
     * 从世界坐标建形状。
     *
     * @return 形状，以及原点格的世界坐标（{@link ShipAssembler#pack} 打包）
     */
    public static Placed fromWorld(List<Long> positions) {
        int x0 = Integer.MAX_VALUE;
        int x1 = Integer.MIN_VALUE;
        int y0 = Integer.MAX_VALUE;
        int z0 = Integer.MAX_VALUE;
        int z1 = Integer.MIN_VALUE;
        for (long p : positions) {
            x0 = Math.min(x0, ShipAssembler.unpackX(p));
            x1 = Math.max(x1, ShipAssembler.unpackX(p));
            y0 = Math.min(y0, ShipAssembler.unpackY(p));
            z0 = Math.min(z0, ShipAssembler.unpackZ(p));
            z1 = Math.max(z1, ShipAssembler.unpackZ(p));
        }
        int ox = Math.floorDiv(x0 + x1, 2);
        int oz = Math.floorDiv(z0 + z1, 2);
        int n = positions.size();
        int[] xs = new int[n];
        int[] ys = new int[n];
        int[] zs = new int[n];
        for (int i = 0; i < n; i++) {
            long p = positions.get(i);
            xs[i] = ShipAssembler.unpackX(p) - ox;
            ys[i] = ShipAssembler.unpackY(p) - y0;
            zs[i] = ShipAssembler.unpackZ(p) - oz;
        }
        return new Placed(new ShipShape(xs, ys, zs), ox, y0, oz);
    }

    /** 形状和它的原点格在世界里的位置。 */
    public record Placed(ShipShape shape, int originX, int originY, int originZ) {
    }

    public int size() {
        return this.xs.length;
    }

    public int x(int i) {
        return this.xs[i];
    }

    public int y(int i) {
        return this.ys[i];
    }

    public int z(int i) {
        return this.zs[i];
    }

    public boolean exposed(int i) {
        return this.exposed[i];
    }

    public int minX() {
        return this.minX;
    }

    public int maxX() {
        return this.maxX;
    }

    public int height() {
        return this.maxY + 1;
    }

    public int minZ() {
        return this.minZ;
    }

    public int maxZ() {
        return this.maxZ;
    }

    /** 水平方向上离转轴最远的方块角有多远（转起来扫过的半径）。 */
    public double radius() {
        double r = 0;
        for (int i = 0; i < this.xs.length; i++) {
            double dx = Math.abs(this.xs[i]) + 0.5;
            double dz = Math.abs(this.zs[i]) + 0.5;
            r = Math.max(r, Math.sqrt(dx * dx + dz * dz));
        }
        return r;
    }

    /**
     * 把局部坐标按偏航转到世界方向（偏航 0 不转，增大是顺时针从上往下看，和原版实体一致）。
     *
     * @return {x, z}
     */
    public static double[] rotate(double x, double z, float yaw) {
        double r = Math.toRadians(yaw);
        double c = Math.cos(r);
        double s = Math.sin(r);
        return new double[] {x * c - z * s, x * s + z * c};
    }

    /** 偏航对齐到最近的 90° 倍数，返回四分之一圈的圈数（0–3）。 */
    public static int quarterTurns(float yaw) {
        return Math.floorMod(Math.round(yaw / 90f), 4);
    }

    /** 按四分之一圈转整数坐标（和 {@link #rotate} 同向）。 */
    public static int[] rotateQuarter(int x, int z, int turns) {
        return switch (Math.floorMod(turns, 4)) {
            case 1 -> new int[] {-z, x};
            case 2 -> new int[] {-x, -z};
            case 3 -> new int[] {z, -x};
            default -> new int[] {x, z};
        };
    }
}
