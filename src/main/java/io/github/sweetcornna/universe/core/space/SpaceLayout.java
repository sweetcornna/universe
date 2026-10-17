package io.github.sweetcornna.universe.core.space;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import io.github.sweetcornna.universe.core.galaxy.BodyId;
import io.github.sweetcornna.universe.core.galaxy.Planet;
import io.github.sweetcornna.universe.core.galaxy.StarSystem;

/**
 * 恒星系在太空维度里的布局（玩法设计 §6）。
 *
 * <p>恒星在原点，行星在 y = 0 的黄道面上，按轨道相位摆开；轨道半径按对数压缩，相邻行星隔一两千格，
 * 开脉冲引擎几秒到十几秒就能飞到。卫星排在母行星外侧。行星暂时不沿轨道移动（M2 的简化）。
 *
 * <p>每颗星球有一个"大气球"，飞船飞进去就切换到这颗星球的维度。
 */
public final class SpaceLayout {
    /** 恒星的半径（格）：飞船靠近到这里就会被挡住。 */
    public static final double STAR_RADIUS = 400;

    private SpaceLayout() {
    }

    /**
     * 太空里的一个天体。
     *
     * @param radius 大气球的半径（格）
     */
    public record Body(Planet planet, double x, double y, double z, double radius) {
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
        for (Planet planet : system.planets()) {
            double distance = orbitDistance(planet.orbit().radius());
            double angle = planet.orbit().phase() * 2 * Math.PI;
            double x = Math.cos(angle) * distance;
            double z = Math.sin(angle) * distance;
            double radius = sphereRadius(planet);
            bodies.add(new Body(planet, x, 0, z, radius));
            double moonDistance = radius + 150;
            for (Planet moon : planet.moons()) {
                double moonRadius = sphereRadius(moon);
                moonDistance += moonRadius;
                double moonAngle = moon.orbit().phase() * 2 * Math.PI;
                bodies.add(new Body(moon, x + Math.cos(moonAngle) * moonDistance, 0, z + Math.sin(moonAngle) * moonDistance, moonRadius));
                moonDistance += moonRadius + 120;
            }
        }
        return bodies;
    }

    /** 轨道半径（AU）在太空维度里对应的距离（格）。 */
    public static double orbitDistance(double au) {
        return 1500 + 3000 * Math.log(1 + au / 0.25) / Math.log(2);
    }

    /** 大气球的半径：母星大小的星球 110 格，气态巨行星几百格。 */
    public static double sphereRadius(Planet planet) {
        return 60 + 50 * planet.radius();
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

    /**
     * 离开星球时在太空里出现的位置：大气球外沿船头方向一点。
     *
     * @param yaw 船头偏航（度，0 朝 +z）
     * @return {x, y, z}
     */
    public static double[] exitPoint(Body body, float yaw) {
        double r = body.radius() + 24;
        double angle = Math.toRadians(yaw);
        return new double[] {body.x() - Math.sin(angle) * r, body.y(), body.z() + Math.cos(angle) * r};
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
