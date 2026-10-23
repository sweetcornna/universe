package io.github.sweetcornna.universe.core.logistics;

import java.util.List;

import io.github.sweetcornna.universe.core.galaxy.StarSystem;
import io.github.sweetcornna.universe.core.space.SpaceLayout;

/**
 * 运输船在恒星系太空维度里的航线（ADR-0030 §五）。一段航程的两头各是一种"端"：
 * <ul>
 * <li>{@link #PAD}：站就在这个太空维度里（轨道采集器、太空里的星际物流站），从停机坪起飞。
 * <li>{@link #SPHERE}：站在这个恒星系的某颗星球上，运输船先在星球上空升起（星球维度里画），过 {@link #LIFT_TICKS}
 * 刻从大气球表面钻出来；到达时反过来，提前这么久钻进大气球。
 * <li>{@link #EDGE}：站在别的恒星系，运输船飞到恒星系边上朝那颗恒星的方向跃迁走；从别处来的在那里跃迁进来。
 * </ul>
 * 星球维度里看不见的那头记作 {@link #AWAY}。航线是一条二次贝塞尔曲线，直线会穿过恒星时往外绕。
 * 坐标都是太空维度的方块坐标，恒星在原点。
 */
public final class SpaceRoute {
    public static final int AWAY = 0;
    public static final int PAD = 1;
    public static final int SPHERE = 2;
    public static final int EDGE = 3;

    /** 在星球上起飞、降落各用多少刻；和星球维度里画的一样。 */
    public static final double LIFT_TICKS = 200;
    /** 跨恒星系时在本恒星系里飞多久（刻），剩下的时间在跃迁里。 */
    public static final double WARP_CRUISE_TICKS = 600;
    /** 跃迁前后多少刻里船身被拉长。 */
    public static final double STRETCH_TICKS = 30;
    /** 钻出大气球时离球面多高（格）。 */
    static final double SPHERE_ALTITUDE = 40;
    /** 恒星系的边在最外面那颗天体外多远（格）。 */
    static final double EDGE_MARGIN = 20000;
    /** 航线离恒星至少多远：恒星半径的倍数。 */
    static final double STAR_CLEARANCE = 3;

    private SpaceRoute() {
    }

    /** 恒星系的边离恒星多远（格）：最外面那颗天体（连同大气球）再往外 {@link #EDGE_MARGIN}。 */
    public static double edgeRadius(List<SpaceLayout.Body> bodies) {
        double farthest = 0;
        for (SpaceLayout.Body body : bodies) {
            farthest = Math.max(farthest, Math.sqrt(body.x() * body.x() + body.y() * body.y() + body.z() * body.z()) + body.radius());
        }
        return farthest + EDGE_MARGIN;
    }

    /**
     * 去 {@code toward} 那个恒星系时在本恒星系边上的跃迁点：恒星连线在黄道面上的方向。两颗恒星一上一下、几乎重合时
     * 朝 +x。
     */
    public static double[] edgePoint(StarSystem here, double towardX, double towardZ, double edgeRadius) {
        double dx = towardX - here.x();
        double dz = towardZ - here.z();
        double length = Math.hypot(dx, dz);
        if (length < 1e-9) {
            return new double[] {edgeRadius, 0, 0};
        }
        return new double[] {dx / length * edgeRadius, 0, dz / length * edgeRadius};
    }

    /** 运输船钻出（钻进）大气球的位置：从星球中心朝航线另一头 {@code target} 的方向，大气球外 {@link #SPHERE_ALTITUDE} 格。 */
    public static double[] spherePoint(SpaceLayout.Body body, double[] target) {
        double dx = target[0] - body.x();
        double dy = target[1] - body.y();
        double dz = target[2] - body.z();
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        double r = body.radius() + SPHERE_ALTITUDE;
        if (length < 1e-9) {
            return new double[] {body.x(), body.y() + r, body.z()};
        }
        return new double[] {body.x() + dx / length * r, body.y() + dy / length * r, body.z() + dz / length * r};
    }

    /**
     * 贝塞尔曲线的控制点：两头的中点；中点离恒星太近（直线会擦着或穿过恒星）时沿径向推到安全距离以外，
     * 中点正好在恒星上时往上推。
     */
    public static double[] control(double[] from, double[] to, double starRadius) {
        double mx = (from[0] + to[0]) / 2;
        double my = (from[1] + to[1]) / 2;
        double mz = (from[2] + to[2]) / 2;
        double safe = starRadius * STAR_CLEARANCE;
        double distance = Math.sqrt(mx * mx + my * my + mz * mz);
        if (distance >= safe) {
            return new double[] {mx, my, mz};
        }
        // 曲线的中点是 (from + to) / 4 + control / 2：要让它落在安全距离上，控制点推到两倍远
        double[] direction = distance < 1e-9 ? new double[] {0, 1, 0} : new double[] {mx / distance, my / distance, mz / distance};
        double push = 2 * safe;
        return new double[] {direction[0] * push, direction[1] * push, direction[2] * push};
    }

    /**
     * 在太空维度里看得见的那一段（刻）：{start, end}。{@code fromKind} 那头在 {@code legStart} 出发，{@code toKind} 那头在
     * {@code legEnd} 到。星球那头去掉起降的时间；跨恒星系的只留靠近本恒星系那头的 {@link #WARP_CRUISE_TICKS}。
     * 航程太短时起降最多各占四成。
     */
    public static double[] window(int fromKind, int toKind, double legStart, double legEnd) {
        double leg = Math.max(0, legEnd - legStart);
        double lift = Math.min(LIFT_TICKS, leg * 0.4);
        double start = legStart + (fromKind == SPHERE ? lift : 0);
        double end = legEnd - (toKind == SPHERE ? lift : 0);
        if (toKind == EDGE) {
            end = Math.min(end, start + Math.min(WARP_CRUISE_TICKS, (end - start) / 2));
        } else if (fromKind == EDGE) {
            start = Math.max(start, end - Math.min(WARP_CRUISE_TICKS, (end - start) / 2));
        }
        return new double[] {start, Math.max(start, end)};
    }

    /**
     * 走过的比例：两头都停着（停机坪、星球）时慢慢加速、慢慢减速；飞向跃迁点时一路加速，跃迁进来的从全速慢慢减下来。
     */
    public static double ease(int fromKind, int toKind, double t) {
        t = Math.max(0, Math.min(1, t));
        if (toKind == EDGE) {
            return t * t * t;
        }
        if (fromKind == EDGE) {
            double u = 1 - t;
            return 1 - u * u * u;
        }
        return t * t * (3 - 2 * t);
    }

    /** 二次贝塞尔曲线上比例 s 处的点。 */
    public static double[] point(double[] from, double[] control, double[] to, double s) {
        double a = (1 - s) * (1 - s);
        double b = 2 * (1 - s) * s;
        double c = s * s;
        return new double[] {a * from[0] + b * control[0] + c * to[0], a * from[1] + b * control[1] + c * to[1],
                a * from[2] + b * control[2] + c * to[2]};
    }

    /** 跃迁前后船身拉长的倍数：离跃迁越近越长，最多 6 倍；不跃迁时是 1。 */
    public static double stretch(int fromKind, int toKind, double start, double end, double now) {
        double ticks;
        if (toKind == EDGE) {
            ticks = end - now;
        } else if (fromKind == EDGE) {
            ticks = now - start;
        } else {
            return 1;
        }
        if (ticks >= STRETCH_TICKS || ticks < 0) {
            return 1;
        }
        double k = 1 - ticks / STRETCH_TICKS;
        return 1 + 5 * k * k;
    }
}
