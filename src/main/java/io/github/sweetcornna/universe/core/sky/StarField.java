package io.github.sweetcornna.universe.core.sky;

import io.github.sweetcornna.universe.core.procgen.Colors;
import io.github.sweetcornna.universe.core.procgen.Rng;
import io.github.sweetcornna.universe.core.procgen.Seeds;

/**
 * 一个恒星系的星空：远处恒星的方向、大小、颜色和亮度，由恒星系种子决定，同一个恒星系里每颗星球看到的相同。
 *
 * <p>约三分之一的星集中在一条倾斜的大圆带上，像银河。数据按"每颗星 8 个数"平铺在数组里，
 * 客户端直接拿去建顶点缓冲。
 */
public final class StarField {
    /** 每颗星占用的数：方向 x、y、z，大小（度），颜色 r、g、b（0–1），亮度 0–1。 */
    public static final int STRIDE = 8;

    private final float[] data;
    private final double[] galacticPole;

    private StarField(float[] data, double[] galacticPole) {
        this.data = data;
        this.galacticPole = galacticPole;
    }

    public static StarField generate(long systemSeed, int count) {
        Rng rng = Rng.of(Seeds.derive(systemSeed, "sky/stars"));
        // 银河带的法线方向
        double[] pole = randomDirection(rng);
        float[] data = new float[count * STRIDE];
        for (int i = 0; i < count; i++) {
            double[] dir;
            if (rng.chance(0.35)) {
                dir = nearGreatCircle(rng, pole, 0.12);
            } else {
                dir = randomDirection(rng);
            }
            // 大部分星很暗，少数很亮
            double magnitude = Math.pow(rng.nextDouble(), 3);
            double size = 0.12 + 0.35 * magnitude + (rng.chance(0.01) ? 0.3 : 0);
            int color = Colors.blackbody(starTemperature(rng));
            int o = i * STRIDE;
            data[o] = (float) dir[0];
            data[o + 1] = (float) dir[1];
            data[o + 2] = (float) dir[2];
            data[o + 3] = (float) size;
            data[o + 4] = Colors.red(color) / 255f;
            data[o + 5] = Colors.green(color) / 255f;
            data[o + 6] = Colors.blue(color) / 255f;
            data[o + 7] = (float) (0.25 + 0.75 * magnitude);
        }
        return new StarField(data, pole);
    }

    public int count() {
        return this.data.length / STRIDE;
    }

    /** 银河带所在大圆的法线（单位向量），客户端沿这个大圆画一条淡淡的光带。 */
    public double[] galacticPole() {
        return this.galacticPole.clone();
    }

    /** 平铺的数据，不要修改。 */
    public float[] data() {
        return this.data;
    }

    /** 恒星表面温度的分布：红矮星最多，蓝白星少。 */
    private static double starTemperature(Rng rng) {
        double r = rng.nextDouble();
        if (r < 0.45) {
            return rng.range(3000, 4000);
        } else if (r < 0.75) {
            return rng.range(4000, 5600);
        } else if (r < 0.92) {
            return rng.range(5600, 7500);
        }
        return rng.range(7500, 15000);
    }

    private static double[] randomDirection(Rng rng) {
        double z = rng.range(-1.0, 1.0);
        double angle = rng.range(0, Math.PI * 2);
        double r = Math.sqrt(1 - z * z);
        return new double[] {r * Math.cos(angle), r * Math.sin(angle), z};
    }

    /** 靠近以 {@code pole} 为法线的大圆、带一点高斯散布的方向。 */
    private static double[] nearGreatCircle(Rng rng, double[] pole, double spread) {
        // 大圆平面上的两个正交基
        double[] a = normalize(cross(pole, Math.abs(pole[2]) < 0.9 ? new double[] {0, 0, 1} : new double[] {1, 0, 0}));
        double[] b = cross(pole, a);
        double angle = rng.range(0, Math.PI * 2);
        double off = rng.gaussian() * spread;
        double[] dir = new double[3];
        for (int k = 0; k < 3; k++) {
            dir[k] = a[k] * Math.cos(angle) + b[k] * Math.sin(angle) + pole[k] * off;
        }
        return normalize(dir);
    }

    private static double[] cross(double[] u, double[] v) {
        return new double[] {u[1] * v[2] - u[2] * v[1], u[2] * v[0] - u[0] * v[2], u[0] * v[1] - u[1] * v[0]};
    }

    private static double[] normalize(double[] v) {
        double len = Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
        return new double[] {v[0] / len, v[1] / len, v[2] / len};
    }
}
