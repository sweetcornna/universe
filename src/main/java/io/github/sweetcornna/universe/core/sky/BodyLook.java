package io.github.sweetcornna.universe.core.sky;

import io.github.sweetcornna.universe.core.galaxy.Planet;
import io.github.sweetcornna.universe.core.galaxy.PlanetType;
import io.github.sweetcornna.universe.core.procgen.Rng;
import io.github.sweetcornna.universe.core.procgen.Seeds;

/**
 * 一颗星球在别处的天空里长什么样：表面画法、自转轴、自转快慢、行星环的大小。由星球种子决定。
 *
 * @param surface     表面画法
 * @param axisTilt    自转轴偏离黄道面法线的角度（弧度）
 * @param axisAzimuth 自转轴倾斜的方向（弧度）
 * @param spinDays    自转一圈要多少母星日
 * @param ringInner   环的内半径（星球半径的倍数），没有环为 0
 * @param ringOuter   环的外半径
 * @param pattern     表面图案的种子，0–1
 */
public record BodyLook(
        Surface surface,
        double axisTilt,
        double axisAzimuth,
        double spinDays,
        double ringInner,
        double ringOuter,
        double pattern) {
    public enum Surface {
        /** 岩石：高地、低地两种颜色和陨石坑。 */
        ROCKY,
        /** 有海：海、陆地、云和极冠。 */
        OCEAN,
        /** 气态巨行星：沿纬度的云带和风暴。 */
        GAS,
        /** 冰封：发白的冰面和裂纹。 */
        ICE,
        /** 熔岩：暗色岩壳和发光的裂缝。 */
        LAVA;

        /** 着色器里的编号。 */
        public int shaderId() {
            return this.ordinal() + 1;
        }
    }

    public static BodyLook of(Planet planet) {
        Rng rng = Rng.of(Seeds.derive(planet.seed(), "sky/look"));
        Surface surface = surface(planet);
        double tilt = Math.toRadians(rng.range(3.0, 35.0));
        double azimuth = rng.range(0, Math.PI * 2);
        double spin = surface == Surface.GAS ? rng.range(0.35, 0.7) : rng.range(0.8, 3.0);
        double inner = 0;
        double outer = 0;
        if (planet.rings()) {
            inner = rng.range(1.25, 1.6);
            outer = inner + rng.range(0.5, 1.1);
        }
        return new BodyLook(surface, tilt, azimuth, spin, inner, outer, rng.nextDouble());
    }

    static Surface surface(Planet planet) {
        if (planet.type() == PlanetType.GAS_GIANT) {
            return Surface.GAS;
        }
        return switch (planet.liquid()) {
            case WATER, ACID -> Surface.OCEAN;
            case LAVA -> Surface.LAVA;
            case ICE -> Surface.ICE;
            case NONE -> planet.type() == PlanetType.FROZEN ? Surface.ICE : Surface.ROCKY;
        };
    }

    /** 自转轴在黄道坐标里的方向：x 沿黄道面法线，y、z 在黄道面内。 */
    public double[] axis() {
        double s = Math.sin(this.axisTilt);
        return new double[] {Math.cos(this.axisTilt), s * Math.cos(this.axisAzimuth), s * Math.sin(this.axisAzimuth)};
    }

    /** {@code days} 时刻的自转角（弧度，0–2π）。 */
    public double spinAngle(double days) {
        double turns = days / this.spinDays;
        return (turns - Math.floor(turns)) * Math.PI * 2;
    }

    public boolean hasRings() {
        return this.ringOuter > 0;
    }
}
