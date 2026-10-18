package io.github.sweetcornna.universe.core.sky;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import io.github.sweetcornna.universe.core.galaxy.Liquid;
import io.github.sweetcornna.universe.core.galaxy.Orbit;
import io.github.sweetcornna.universe.core.galaxy.Palette;
import io.github.sweetcornna.universe.core.galaxy.Planet;
import io.github.sweetcornna.universe.core.galaxy.PlanetType;
import io.github.sweetcornna.universe.core.galaxy.Star;
import io.github.sweetcornna.universe.core.galaxy.StarSystem;
import io.github.sweetcornna.universe.core.procgen.Colors;

/**
 * 从一颗星球上看出去的天空：恒星、其他行星、卫星各在什么方向，显示多大，亮面多少。
 *
 * <p>位置按圆轨道算（{@link Orbit#angleAt}），时间以母星日计。真实的视大小在方块世界里几乎看不见，
 * 显示时压缩成"大的更大、小的也看得见"：恒星约 10 度，行星 1.5–4 度，从卫星上看母行星可达几十度。
 */
public final class SkyModel {
    /** 一个天文单位换算成地球半径、太阳半径。 */
    private static final double EARTH_RADIUS_AU = 6371.0 / 1.496e8;
    private static final double SUN_RADIUS_AU = 696_000.0 / 1.496e8;

    private SkyModel() {
    }

    /**
     * 观察者在 {@code days}（母星日）时看到的天体，按距离从远到近排好，画的时候近的盖住远的。
     *
     * @param system   观察者所在的恒星系
     * @param observer 观察者站的星球（行星或卫星）
     */
    public static List<SkyBody> bodies(StarSystem system, Planet observer, double days) {
        Planet parent = observer.moon() ? system.planets().get(observer.id().planet()) : null;
        double[] here = position(system, observer, parent, days);
        List<SkyBody> result = new ArrayList<>();
        result.add(star(system.star(), here));
        for (Planet planet : system.planets()) {
            if (planet == observer || planet == parent) {
                continue;
            }
            result.add(body(SkyBody.Kind.PLANET, planet, position(system, planet, null, days), here));
        }
        if (parent != null) {
            result.add(body(SkyBody.Kind.PARENT, parent, position(system, parent, null, days), here));
            for (Planet sibling : parent.moons()) {
                if (sibling != observer) {
                    result.add(body(SkyBody.Kind.SIBLING, sibling, position(system, sibling, parent, days), here));
                }
            }
        } else {
            for (Planet moon : observer.moons()) {
                result.add(body(SkyBody.Kind.MOON, moon, position(system, moon, observer, days), here));
            }
        }
        result.sort(Comparator.comparingDouble(SkyBody::distance).reversed());
        return result;
    }

    /** 以恒星为原点的位置（AU），黄道面是 xy 平面。 */
    static double[] position(StarSystem system, Planet body, Planet parent, double days) {
        if (body.moon()) {
            Planet host = parent != null ? parent : system.planets().get(body.id().planet());
            double[] base = orbitPoint(host.orbit(), days);
            double[] offset = orbitPoint(body.orbit(), days);
            return new double[] {base[0] + offset[0], base[1] + offset[1], base[2] + offset[2]};
        }
        return orbitPoint(body.orbit(), days);
    }

    private static double[] orbitPoint(Orbit orbit, double days) {
        double angle = orbit.angleAt(days);
        double inclination = Math.toRadians(orbit.inclination());
        double x = orbit.radius() * Math.cos(angle);
        double y = orbit.radius() * Math.sin(angle);
        return new double[] {x, y * Math.cos(inclination), y * Math.sin(inclination)};
    }

    private static SkyBody star(Star star, double[] here) {
        double distance = length(here);
        double real = Math.toDegrees(2 * Math.atan(star.radius() * SUN_RADIUS_AU / distance));
        // 母星看太阳约 0.53 度，显示成 10 度左右，和原版太阳差不多大；致密天体和黑洞另算
        double shown = StarLook.skyDiameter(star, clamp(10 * Math.pow(real / 0.533, 0.6), 3, 40));
        return new SkyBody(SkyBody.Kind.STAR, null, 0, 0, shown, 1, distance, star.color(), star.color(), false);
    }

    private static SkyBody body(SkyBody.Kind kind, Planet planet, double[] there, double[] here) {
        double[] toStar = {-here[0], -here[1], -here[2]};
        double[] toBody = {there[0] - here[0], there[1] - here[1], there[2] - here[2]};
        double distance = length(toBody);
        double longitude = Math.atan2(cross2(toStar, toBody), dot2(toStar, toBody));
        double latitude = Math.atan2(toBody[2], Math.hypot(toBody[0], toBody[1]));
        double real = Math.toDegrees(2 * Math.atan(planet.radius() * EARTH_RADIUS_AU / distance));
        // 亮面比例：天体处"指向恒星"和"指向观察者"的夹角
        double[] bodyToStar = {-there[0], -there[1], -there[2]};
        double[] bodyToObserver = {-toBody[0], -toBody[1], -toBody[2]};
        double cos = dot3(bodyToStar, bodyToObserver) / (length(bodyToStar) * length(bodyToObserver));
        double illumination = (1 + cos) / 2;
        int[] colors = bodyColors(planet);
        return new SkyBody(kind, planet, longitude, latitude, displayDiameter(kind, real), illumination, distance,
                colors[0], colors[1], planet.rings());
    }

    /** 远处看到的星球的主色和第二种颜色（云带、陆地或熔岩），太空里画星球也用它。 */
    public static int[] bodyColors(Planet planet) {
        Palette palette = planet.palette();
        if (planet.type() == PlanetType.GAS_GIANT) {
            return new int[] {palette.surface(), palette.soil()};
        } else if (planet.liquid() == Liquid.WATER || planet.liquid() == Liquid.ACID) {
            // 有海的星球从远处看主要是海和云
            return new int[] {Colors.lerp(palette.liquid(), palette.cloud(), 0.25), palette.surface()};
        } else if (planet.liquid() == Liquid.LAVA) {
            // 暗色的岩壳，裂缝里透出熔岩的光
            return new int[] {palette.rock(), palette.liquid()};
        } else if (planet.liquid() == Liquid.ICE) {
            return new int[] {Colors.lerp(palette.surface(), 0xF0F4FF, 0.4), palette.liquid()};
        }
        return new int[] {palette.surface(), palette.rock()};
    }

    /** 真实视直径（度）压缩成显示用的大小。 */
    static double displayDiameter(SkyBody.Kind kind, double real) {
        if (kind == SkyBody.Kind.PARENT) {
            return clamp(real * 5, 8, 60);
        }
        double arcsec = real * 3600;
        double shown = 1.2 + 1.5 * Math.log10(1 + arcsec / 8);
        if (kind == SkyBody.Kind.MOON) {
            shown *= 1.5;
        }
        return clamp(shown, 1.2, 25);
    }

    private static double dot2(double[] a, double[] b) {
        return a[0] * b[0] + a[1] * b[1];
    }

    private static double cross2(double[] a, double[] b) {
        return a[0] * b[1] - a[1] * b[0];
    }

    private static double dot3(double[] a, double[] b) {
        return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
    }

    private static double length(double[] v) {
        return Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }
}
