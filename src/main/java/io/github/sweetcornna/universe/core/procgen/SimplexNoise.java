package io.github.sweetcornna.universe.core.procgen;

/**
 * 可设种子的单纯形噪声，2D 和 3D，单次采样的输出约在 [-1, 1]。
 *
 * <p>算法是 Stefan Gustavson 公开的参考实现，排列表用 {@link Rng} 按种子洗牌。
 * 不依赖 Minecraft 的噪声类，地形模型可以在核心层里单元测试、离线渲染预览图。
 * 实例构造后不可变，可以在世界生成的多个线程间共享。
 */
public final class SimplexNoise {
    private static final double[] GRAD_X = {1, -1, 1, -1, 1, -1, 1, -1, 0, 0, 0, 0};
    private static final double[] GRAD_Y = {1, 1, -1, -1, 0, 0, 0, 0, 1, -1, 1, -1};
    private static final double[] GRAD_Z = {0, 0, 0, 0, 1, 1, -1, -1, 1, 1, -1, -1};
    private static final double F2 = 0.5 * (Math.sqrt(3.0) - 1.0);
    private static final double G2 = (3.0 - Math.sqrt(3.0)) / 6.0;
    private static final double F3 = 1.0 / 3.0;
    private static final double G3 = 1.0 / 6.0;

    private final short[] perm = new short[512];
    private final short[] permMod12 = new short[512];

    public SimplexNoise(long seed) {
        short[] p = new short[256];
        for (int i = 0; i < 256; i++) {
            p[i] = (short) i;
        }
        Rng rng = Rng.of(seed);
        for (int i = 255; i > 0; i--) {
            int j = rng.nextInt(i + 1);
            short t = p[i];
            p[i] = p[j];
            p[j] = t;
        }
        for (int i = 0; i < 512; i++) {
            this.perm[i] = p[i & 255];
            this.permMod12[i] = (short) (this.perm[i] % 12);
        }
    }

    private static int floor(double x) {
        int xi = (int) x;
        return x < xi ? xi - 1 : xi;
    }

    public double noise(double x, double y) {
        double s = (x + y) * F2;
        int i = floor(x + s);
        int j = floor(y + s);
        double t = (i + j) * G2;
        double x0 = x - (i - t);
        double y0 = y - (j - t);
        int i1 = x0 > y0 ? 1 : 0;
        int j1 = 1 - i1;
        double x1 = x0 - i1 + G2;
        double y1 = y0 - j1 + G2;
        double x2 = x0 - 1.0 + 2.0 * G2;
        double y2 = y0 - 1.0 + 2.0 * G2;
        int ii = i & 255;
        int jj = j & 255;
        double n = 0;
        double t0 = 0.5 - x0 * x0 - y0 * y0;
        if (t0 > 0) {
            int g = this.permMod12[ii + this.perm[jj]];
            t0 *= t0;
            n += t0 * t0 * (GRAD_X[g] * x0 + GRAD_Y[g] * y0);
        }
        double t1 = 0.5 - x1 * x1 - y1 * y1;
        if (t1 > 0) {
            int g = this.permMod12[ii + i1 + this.perm[jj + j1]];
            t1 *= t1;
            n += t1 * t1 * (GRAD_X[g] * x1 + GRAD_Y[g] * y1);
        }
        double t2 = 0.5 - x2 * x2 - y2 * y2;
        if (t2 > 0) {
            int g = this.permMod12[ii + 1 + this.perm[jj + 1]];
            t2 *= t2;
            n += t2 * t2 * (GRAD_X[g] * x2 + GRAD_Y[g] * y2);
        }
        return 70.0 * n;
    }

    public double noise(double x, double y, double z) {
        double s = (x + y + z) * F3;
        int i = floor(x + s);
        int j = floor(y + s);
        int k = floor(z + s);
        double t = (i + j + k) * G3;
        double x0 = x - (i - t);
        double y0 = y - (j - t);
        double z0 = z - (k - t);
        int i1;
        int j1;
        int k1;
        int i2;
        int j2;
        int k2;
        if (x0 >= y0) {
            if (y0 >= z0) {
                i1 = 1; j1 = 0; k1 = 0; i2 = 1; j2 = 1; k2 = 0;
            } else if (x0 >= z0) {
                i1 = 1; j1 = 0; k1 = 0; i2 = 1; j2 = 0; k2 = 1;
            } else {
                i1 = 0; j1 = 0; k1 = 1; i2 = 1; j2 = 0; k2 = 1;
            }
        } else if (y0 < z0) {
            i1 = 0; j1 = 0; k1 = 1; i2 = 0; j2 = 1; k2 = 1;
        } else if (x0 < z0) {
            i1 = 0; j1 = 1; k1 = 0; i2 = 0; j2 = 1; k2 = 1;
        } else {
            i1 = 0; j1 = 1; k1 = 0; i2 = 1; j2 = 1; k2 = 0;
        }
        double x1 = x0 - i1 + G3;
        double y1 = y0 - j1 + G3;
        double z1 = z0 - k1 + G3;
        double x2 = x0 - i2 + 2.0 * G3;
        double y2 = y0 - j2 + 2.0 * G3;
        double z2 = z0 - k2 + 2.0 * G3;
        double x3 = x0 - 1.0 + 3.0 * G3;
        double y3 = y0 - 1.0 + 3.0 * G3;
        double z3 = z0 - 1.0 + 3.0 * G3;
        int ii = i & 255;
        int jj = j & 255;
        int kk = k & 255;
        double n = 0;
        double t0 = 0.6 - x0 * x0 - y0 * y0 - z0 * z0;
        if (t0 > 0) {
            int g = this.permMod12[ii + this.perm[jj + this.perm[kk]]];
            t0 *= t0;
            n += t0 * t0 * (GRAD_X[g] * x0 + GRAD_Y[g] * y0 + GRAD_Z[g] * z0);
        }
        double t1 = 0.6 - x1 * x1 - y1 * y1 - z1 * z1;
        if (t1 > 0) {
            int g = this.permMod12[ii + i1 + this.perm[jj + j1 + this.perm[kk + k1]]];
            t1 *= t1;
            n += t1 * t1 * (GRAD_X[g] * x1 + GRAD_Y[g] * y1 + GRAD_Z[g] * z1);
        }
        double t2 = 0.6 - x2 * x2 - y2 * y2 - z2 * z2;
        if (t2 > 0) {
            int g = this.permMod12[ii + i2 + this.perm[jj + j2 + this.perm[kk + k2]]];
            t2 *= t2;
            n += t2 * t2 * (GRAD_X[g] * x2 + GRAD_Y[g] * y2 + GRAD_Z[g] * z2);
        }
        double t3 = 0.6 - x3 * x3 - y3 * y3 - z3 * z3;
        if (t3 > 0) {
            int g = this.permMod12[ii + 1 + this.perm[jj + 1 + this.perm[kk + 1]]];
            t3 *= t3;
            n += t3 * t3 * (GRAD_X[g] * x3 + GRAD_Y[g] * y3 + GRAD_Z[g] * z3);
        }
        return 32.0 * n;
    }

    /**
     * 分形布朗运动：频率逐层翻倍、振幅逐层减半地叠加，按总振幅归一化。
     * 每层错开采样原点，避免各层在原点附近对齐。
     */
    private static final double RIDGE_SOFTNESS = 0.12;
    /** 脊状噪声每层振幅的衰减。比 fbm 的 0.5 小，高频层叠在脊线上不会把坡度堆得太陡。 */
    private static final double RIDGE_GAIN = 0.42;

    public double fbm(double x, double y, int octaves) {
        double sum = 0;
        double amplitude = 1;
        double norm = 0;
        double frequency = 1;
        for (int o = 0; o < octaves; o++) {
            sum += amplitude * this.noise(x * frequency + o * 31.7, y * frequency - o * 17.3);
            norm += amplitude;
            amplitude *= 0.5;
            frequency *= 2;
        }
        return sum / norm;
    }

    public double fbm(double x, double y, double z, int octaves) {
        double sum = 0;
        double amplitude = 1;
        double norm = 0;
        double frequency = 1;
        for (int o = 0; o < octaves; o++) {
            sum += amplitude * this.noise(x * frequency + o * 31.7, y * frequency + o * 7.9, z * frequency - o * 17.3);
            norm += amplitude;
            amplitude *= 0.5;
            frequency *= 2;
        }
        return sum / norm;
    }

    /**
     * 脊状多重分形，结果在 [0, 1]：取 1 − |噪声| 得到山脊，上一层越高下一层权重越大，山脊上细节更多、谷地更平滑。
     * 绝对值做了软化，脊线是窄而圆的山梁，不是一格宽的刀刃（刀刃在方块世界里会变成一排细针）。
     */
    public double ridged(double x, double y, int octaves) {
        double sum = 0;
        double amplitude = 0.5;
        double norm = 0;
        double frequency = 1;
        double weight = 1;
        for (int o = 0; o < octaves; o++) {
            double n = this.noise(x * frequency + o * 31.7, y * frequency - o * 17.3);
            double signal = 1 - (Math.sqrt(n * n + RIDGE_SOFTNESS * RIDGE_SOFTNESS) - RIDGE_SOFTNESS);
            signal *= signal;
            signal *= weight;
            weight = Math.min(1, Math.max(0, signal * 2));
            sum += signal * amplitude;
            norm += amplitude;
            amplitude *= RIDGE_GAIN;
            frequency *= 2;
        }
        return sum / norm;
    }
}
