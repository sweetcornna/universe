package io.github.sweetcornna.universe.core.space;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import io.github.sweetcornna.universe.core.galaxy.BodyId;
import io.github.sweetcornna.universe.core.galaxy.Planet;
import io.github.sweetcornna.universe.core.galaxy.PlanetType;
import io.github.sweetcornna.universe.core.galaxy.StarSystem;
import io.github.sweetcornna.universe.core.sky.StarLook;

/**
 * 恒星系在太空维度里的布局（玩法设计 §6、ADR-0021）。
 *
 * <p>恒星在原点，行星在 y = 0 的黄道面上，按轨道相位摆开。母星大小的星球半径 1200 格，气态巨行星三千到八千格；
 * 轨道半径按对数压缩，相邻行星隔三万到六万格，开脉冲引擎半分钟到一分多钟。卫星绕着母行星排在外侧，
 * 离得近，从卫星的轨道上看母行星有几十度大。行星不沿轨道移动（M2 的简化）。
 *
 * <p>每颗星球有一个"大气球"，比地表大一点，飞船飞进去就切换到这颗星球的维度。
 */
public final class SpaceLayout {
    /** 恒星在太空里比 {@link StarLook#spaceRadius()} 放大的倍数。 */
    public static final double STAR_SCALE = 12;
    /** 0 AU 处离恒星多远（格）。 */
    static final double ORBIT_BASE = 18000;
    /** 轨道半径每翻一倍（从 0.25 AU 起算）多出的距离（格）。 */
    static final double ORBIT_PER_DOUBLING = 64000;
    /** 一颗行星连同卫星和下一颗之间至少留出的空当（格）：开脉冲引擎（40 格/刻）半分钟，加上加速的一两秒。 */
    static final double SYSTEM_GAP = 24000;
    /** 起飞时离大气球外沿多高：母星大小的星球上约 85 格，像在近地轨道上，地平线往下 25° 左右。 */
    static final double EXIT_ALTITUDE = 48;
    /** 起飞的位置离星球北极最多偏开多少（弧度）：地表离出生点越远，出现在大气球上越偏。 */
    static final double EXIT_TILT = Math.toRadians(55);

    private SpaceLayout() {
    }

    /** 恒星的半径（格）：红矮星三千格，红巨星一万多格，白矮星、中子星只有几百格；飞船靠近到这里就会被挡住。 */
    public static double starRadius(StarSystem system) {
        return StarLook.of(system).spaceRadius() * STAR_SCALE;
    }

    /**
     * 太空里的一个天体。
     *
     * @param radius  大气球的半径（格）：飞进去就降落
     * @param surface 地表（云顶）的半径（格），画的时候用
     */
    public record Body(Planet planet, double x, double y, double z, double radius, double surface) {
        public BodyId id() {
            return this.planet.id();
        }

        public double distanceTo(double px, double py, double pz) {
            double dx = px - this.x;
            double dy = py - this.y;
            double dz = pz - this.z;
            return Math.sqrt(dx * dx + dy * dy + dz * dz);
        }
    }

    /** 恒星系里全部行星和卫星在太空里的位置。 */
    public static List<Body> of(StarSystem system) {
        List<Body> bodies = new ArrayList<>();
        double previous = starRadius(system);
        for (Planet planet : system.planets()) {
            double surface = surfaceRadius(planet);
            double radius = sphereRadius(planet);
            // 卫星离母行星的距离：气态巨行星的卫星贴得更近，从卫星上看母行星占半边天
            List<double[]> moons = new ArrayList<>();
            double moonDistance = radius + (planet.type() == PlanetType.GAS_GIANT ? 0.5 : 1.2) * surface + 1500;
            double extent = radius;
            for (Planet moon : planet.moons()) {
                double moonRadius = sphereRadius(moon);
                moonDistance += moonRadius;
                moons.add(new double[] {moonDistance, moonRadius, surfaceRadius(moon), moon.orbit().phase() * 2 * Math.PI});
                extent = moonDistance + moonRadius;
                moonDistance += moonRadius + 1200 + 0.35 * surface;
            }
            // 按轨道算出的距离太近（大行星、卫星多）时往外推，保证和里面那颗连同卫星都不相交
            double distance = Math.max(orbitDistance(planet.orbit().radius()), previous + SYSTEM_GAP + extent);
            previous = distance + extent;
            double angle = planet.orbit().phase() * 2 * Math.PI;
            double x = Math.cos(angle) * distance;
            double z = Math.sin(angle) * distance;
            bodies.add(new Body(planet, x, 0, z, radius, surface));
            for (int i = 0; i < moons.size(); i++) {
                double[] m = moons.get(i);
                bodies.add(new Body(planet.moons().get(i), x + Math.cos(m[3]) * m[0], 0, z + Math.sin(m[3]) * m[0], m[1], m[2]));
            }
        }
        return bodies;
    }

    /** 轨道半径（AU）在太空维度里对应的距离（格）。 */
    public static double orbitDistance(double au) {
        return ORBIT_BASE + ORBIT_PER_DOUBLING * Math.log(1 + au / 0.25) / Math.log(2);
    }

    /** {@link #orbitDistance} 反过来：离恒星多少格相当于多少 AU。比最内侧的轨道还近时按 0 算。 */
    public static double auAt(double distance) {
        return Math.max(0, 0.25 * (Math.pow(2, (distance - ORBIT_BASE) / ORBIT_PER_DOUBLING) - 1));
    }

    /** 地表的半径：母星 1200 格，卫星两三百到六七百格，气态巨行星三千到八千格。 */
    public static double surfaceRadius(Planet planet) {
        return 100 + 1100 * Math.pow(planet.radius(), 0.8);
    }

    /** 大气球的半径：比地表高出 2% 再加 20 格。 */
    public static double sphereRadius(Planet planet) {
        return surfaceRadius(planet) * 1.02 + 20;
    }

    /** 点所在的大气球；不在任何大气球里时返回空。 */
    public static Optional<Body> inside(List<Body> bodies, double x, double y, double z) {
        for (Body body : bodies) {
            if (body.distanceTo(x, y, z) < body.radius()) {
                return Optional.of(body);
            }
        }
        return Optional.empty();
    }

    public static Optional<Body> find(List<Body> bodies, BodyId id) {
        for (Body body : bodies) {
            if (body.id().equals(id)) {
                return Optional.of(body);
            }
        }
        return Optional.empty();
    }

    /** 起飞后离星球中心多远。 */
    public static double exitDistance(Body body) {
        return body.radius() + EXIT_ALTITUDE + 0.03 * body.surface();
    }

    /**
     * 离开星球时在太空里出现的位置：在大气球的北半边，起飞的地方离出生点越远、偏离北极越多，方位跟着地表的方位走。
     * 星球留在脚下，抬头是太空。
     *
     * @param surfaceX  起飞时在星球维度里的 x
     * @param surfaceZ  起飞时在星球维度里的 z
     * @param worldSize 星球的世界边长（格）
     * @return {x, y, z}
     */
    public static double[] exitPoint(Body body, double surfaceX, double surfaceZ, int worldSize) {
        double half = Math.max(1, worldSize / 2.0);
        double tilt = Math.min(1, Math.hypot(surfaceX, surfaceZ) / half) * EXIT_TILT;
        double azimuth = Math.atan2(surfaceZ, surfaceX);
        double r = exitDistance(body);
        double horizontal = Math.sin(tilt) * r;
        return new double[] {body.x() + Math.cos(azimuth) * horizontal, body.y() + Math.cos(tilt) * r, body.z() + Math.sin(azimuth) * horizontal};
    }

    /**
     * 飞进大气球时在星球地表上的落点：进入的方向换算成地图上的位置（经度对 x，纬度对 z），
     * 落在出生点周围 {@code spread} 格以内。
     *
     * @return {x, z}，相对星球的出生点
     */
    public static double[] landingPoint(Body body, double x, double y, double z, double spread) {
        double dx = x - body.x();
        double dy = y - body.y();
        double dz = z - body.z();
        double d = Math.max(1e-6, Math.sqrt(dx * dx + dy * dy + dz * dz));
        double longitude = Math.atan2(dz, dx) / Math.PI;
        double latitude = Math.asin(Math.max(-1, Math.min(1, dy / d))) / (Math.PI / 2);
        return new double[] {longitude * spread, -latitude * spread};
    }
}
