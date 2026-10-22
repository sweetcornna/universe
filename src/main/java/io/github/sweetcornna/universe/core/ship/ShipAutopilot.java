package io.github.sweetcornna.universe.core.ship;

import java.util.List;

import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.core.ship.ShipFlight.Controls;

/**
 * 自动驾驶（ADR-0024）：每刻按船的位置、朝向和速度给出一组操作，交给 {@link ShipFlight} 去飞，和玩家按键是同一套。
 *
 * <p>先转向对准目标，对准了往前推；离得远就开脉冲引擎，剩下的距离只够刹车时松开；高度差大时同时升降。
 * 到了停靠距离、速度降下来就算到了。路上有空间站挡着时用 {@link #around} 排一条从上面（或下面）绕过去的路线。
 *
 * <p>停靠点（{@link Target#dock}）要准得多：货船的机库口只有十几格宽、七格高。先把高度对齐，再对准、按离得多远限速推过去，
 * 升降和前进都按刹车距离提前松手，到了点上一两格内才算到。
 */
public final class ShipAutopilot {
    /** 偏差小于这么多度时不再转向。 */
    static final double AIM = 3;
    /** 偏差小于这么多度时往前推。 */
    static final double ALIGNED = 25;
    /** 偏差小于这么多度时开脉冲引擎。 */
    static final double BOOST_ALIGNED = 8;
    /** 高度差超过这么多格时升降。 */
    static final double LEVEL = 4;
    /** 刹车距离之外再留的余量（格）。 */
    static final double BRAKE_MARGIN = 120;
    /** 到了：离停靠点不到这么远、速度低于 {@link #STOPPED}。 */
    public static final double ARRIVED = 24;
    public static final double STOPPED = 0.6;
    /** 路过的点：到了附近、速度低于这个（格/刻）就接着飞下一段。 */
    static final double PASS_SPEED = 1.5;
    /** 停靠点：离点不到这么远、速度低于 {@link #DOCK_STOPPED} 才算到。 */
    public static final double DOCKED = 2.5;
    static final double DOCK_STOPPED = 0.3;
    /** 停靠时高度差超过这么多格就升降，超过三倍时先不往前推。 */
    static final double DOCK_LEVEL = 0.5;
    /** 停靠时偏差小于这么多度才往前推。 */
    static final double DOCK_ALIGNED = 6;
    /** 停靠时水平速度的上限：每离 {@link #DOCK_EASE} 格多 1 格/刻，最少 {@link #DOCK_SPEED}，最多 {@link #PASS_SPEED}。 */
    static final double DOCK_SPEED = 0.3;
    static final double DOCK_EASE = 20;

    private ShipAutopilot() {
    }

    /**
     * 目的地。
     *
     * @param stop 停在离目标中心多远的地方（比如星球大气层外）
     * @param pass 只是路线上路过的点
     * @param dock 要准确停到的点（进机库）
     */
    public record Target(double x, double y, double z, double stop, boolean pass, boolean dock) {
        public Target(double x, double y, double z, double stop) {
            this(x, y, z, stop, false, false);
        }

        public static Target via(double x, double y, double z) {
            return new Target(x, y, z, 0, true, false);
        }

        public static Target dock(double x, double y, double z) {
            return new Target(x, y, z, 0, false, true);
        }

        /** 从 (x, y, z) 飞过来停下的地方：目标中心朝这边退 {@link #stop} 格。 */
        double[] end(double fromX, double fromY, double fromZ) {
            double dx = fromX - this.x;
            double dy = fromY - this.y;
            double dz = fromZ - this.z;
            double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (this.stop <= 0 || d < 1e-6) {
                return new double[] {this.x, this.y, this.z};
            }
            double k = Math.min(1, this.stop / d);
            return new double[] {this.x + dx * k, this.y + dy * k, this.z + dz * k};
        }
    }

    /**
     * 去 {@code to} 的路线，绕开一片方块范围（空间站）：直线穿过 {@code box}（四周外扩 {@code margin} 格）时，
     * 先竖直升到范围上面（船在范围底下就降到更下面），平飞到停靠点的上方，再下去；不穿过就直接去。
     *
     * @param box {minX, minY, minZ, maxX, maxY, maxZ}，方块坐标，两头都含
     */
    public static List<Target> around(double x, double y, double z, Target to, int[] box, double margin) {
        double[] lo = {box[0] - margin, box[1] - margin, box[2] - margin};
        double[] hi = {box[3] + 1 + margin, box[4] + 1 + margin, box[5] + 1 + margin};
        double[] end = to.end(x, y, z);
        if (!crosses(x, y, z, end[0], end[1], end[2], lo, hi)) {
            return List.of(to);
        }
        double level = y < box[1] ? lo[1] - margin : hi[1] + margin;
        return List.of(Target.via(x, level, z), Target.via(end[0], level, end[2]), to);
    }

    /** 线段 (x0, y0, z0)–(x1, y1, z1) 碰不碰到盒子 [lo, hi]。 */
    static boolean crosses(double x0, double y0, double z0, double x1, double y1, double z1, double[] lo, double[] hi) {
        double[] p = {x0, y0, z0};
        double[] d = {x1 - x0, y1 - y0, z1 - z0};
        double t0 = 0;
        double t1 = 1;
        for (int i = 0; i < 3; i++) {
            if (Math.abs(d[i]) < 1e-9) {
                if (p[i] < lo[i] || p[i] > hi[i]) {
                    return false;
                }
                continue;
            }
            double a = (lo[i] - p[i]) / d[i];
            double b = (hi[i] - p[i]) / d[i];
            t0 = Math.max(t0, Math.min(a, b));
            t1 = Math.min(t1, Math.max(a, b));
            if (t0 > t1) {
                return false;
            }
        }
        return true;
    }

    /** 不知道竖直速度时（按零算，停靠点会晃一点）。 */
    public static @Nullable Controls steer(double x, double y, double z, float yaw, double speed, Target target, ShipStats stats) {
        return steer(x, y, z, yaw, speed, 0, target, stats);
    }

    /**
     * 这一刻的操作；到了返回 {@code null}。
     *
     * @param speed 现在的速度（格/刻）
     * @param vy    其中竖直的分量
     */
    public static @Nullable Controls steer(double x, double y, double z, float yaw, double speed, double vy, Target target, ShipStats stats) {
        double dx = target.x() - x;
        double dy = target.y() - y;
        double dz = target.z() - z;
        if (target.dock()) {
            return dock(dx, dy, dz, yaw, speed, vy, stats);
        }
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        double remaining = distance - target.stop();
        if (remaining < ARRIVED && speed < (target.pass() ? PASS_SPEED : STOPPED)) {
            return null;
        }
        // 偏航 0 朝 +z，增大往右转：前进方向是 (-sin, cos)
        double want = Math.toDegrees(Math.atan2(-dx, dz));
        double delta = wrap(want - yaw);
        boolean right = delta > AIM;
        boolean left = delta < -AIM;
        boolean up = dy > LEVEL && remaining > 0;
        boolean down = dy < -LEVEL && remaining > 0;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        // 正上方或正下方时不用转
        if (horizontal < LEVEL * 2) {
            right = false;
            left = false;
        }
        double aim = Math.abs(delta);
        // 剩下的路只够停下来时就松手，让船自己滑过去停住
        double stopping = ShipFlight.stoppingDistance(stats, speed);
        boolean forward = remaining > stopping && aim < ALIGNED && horizontal >= LEVEL * 2;
        // 开着脉冲引擎多飞一刻就多一截刹车距离，按最高速度留余量
        double boosted = ShipFlight.stoppingDistance(stats, Math.max(speed, stats.impulseSpeed()));
        boolean boost = forward && stats.impulse() > 0 && aim < BOOST_ALIGNED && remaining > boosted + BRAKE_MARGIN;
        return new Controls(forward, false, left, right, up, down, boost);
    }

    private static @Nullable Controls dock(double dx, double dy, double dz, float yaw, double speed, double vy, ShipStats stats) {
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        double flat = Math.sqrt(Math.max(0, speed * speed - vy * vy));
        if (Math.sqrt(horizontal * horizontal + dy * dy) < DOCKED && speed < DOCK_STOPPED) {
            return null;
        }
        // 升降：竖直方向的刹车距离够了就松手，不在高度上来回晃
        double lift = Math.max(1e-4, ShipFlight.spaceLift(stats));
        double rising = vy * vy / (2 * lift);
        boolean up = dy > DOCK_LEVEL && !(vy > 0 && rising >= dy);
        boolean down = dy < -DOCK_LEVEL && !(vy < 0 && rising >= -dy);
        double delta = wrap(Math.toDegrees(Math.atan2(-dx, dz)) - yaw);
        // 离得很近时不再转，免得绕着点打转
        boolean turn = horizontal > DOCKED;
        boolean right = turn && delta > AIM;
        boolean left = turn && delta < -AIM;
        double limit = Math.clamp(horizontal / DOCK_EASE, DOCK_SPEED, PASS_SPEED);
        boolean forward = horizontal > ShipFlight.stoppingDistance(stats, flat) + 0.5 && Math.abs(delta) < DOCK_ALIGNED
                && Math.abs(dy) < DOCK_LEVEL * 3 && flat < limit;
        return new Controls(forward, false, left, right, up, down, false);
    }

    private static double wrap(double degrees) {
        double d = degrees % 360;
        if (d > 180) {
            d -= 360;
        } else if (d < -180) {
            d += 360;
        }
        return d;
    }
}
