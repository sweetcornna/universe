package io.github.sweetcornna.universe.core.sky;

import io.github.sweetcornna.universe.core.galaxy.Atmosphere;
import io.github.sweetcornna.universe.core.galaxy.Liquid;
import io.github.sweetcornna.universe.core.galaxy.Palette;
import io.github.sweetcornna.universe.core.galaxy.Planet;
import io.github.sweetcornna.universe.core.galaxy.PlanetType;
import io.github.sweetcornna.universe.core.procgen.Colors;
import io.github.sweetcornna.universe.core.procgen.Rng;
import io.github.sweetcornna.universe.core.procgen.Seeds;

/**
 * 一颗星球从太空里看是什么样子（ADR-0021）：照哪张 NASA 照片画、三种颜色、云、极冠、大气的散射和行星环。
 * 着色器 {@code sky_planet} 按它画。
 *
 * <p>颜色从星球的配色来，但往真实照片的色调拉：降低饱和度，亮度落在实测的反照率范围里（月面 0.12、
 * 木卫二 0.6、金星的云顶 0.7）。颜色都是线性的（已经去掉 sRGB 的伽马）。
 *
 * @param style        表面画法
 * @param a            主色：海、高地、云带的亮色
 * @param b            第二种颜色：陆地、月海、云带的暗色
 * @param c            第三种颜色：沙漠、极冠、风暴
 * @param clouds       云量 0–1
 * @param seaLevel     海平面（地形噪声 0–1 的阈值）
 * @param iceCap       极冠从极点往下伸多远（纬度的正弦）
 * @param relief       陨石坑的深浅
 * @param emissive     自发光强度：熔岩、奇异星球的缝
 * @param aurora       夜面两极的极光强度
 * @param rayleigh     瑞利散射的竖直光学厚度（红、绿、蓝）；没有大气为 0
 * @param atmosphereTop 大气顶离地表多高（星球半径的倍数）；没有大气为 0
 * @param haze         雾霾的颜色（线性）
 * @param hazeDepth    雾霾的竖直光学厚度
 * @param ringColor    行星环的颜色（线性）
 * @param ringOpacity  行星环最密处的不透明度；没有环为 0
 * @param seed         图案的种子 0–1
 */
public record PlanetAppearance(
        Style style,
        double[] a,
        double[] b,
        double[] c,
        double clouds,
        double seaLevel,
        double iceCap,
        double relief,
        double emissive,
        double aurora,
        double[] rayleigh,
        double atmosphereTop,
        double[] haze,
        double hazeDepth,
        double[] ringColor,
        double ringOpacity,
        double seed) {
    /** 母星那样的大气：瑞利散射在红、绿、蓝三段的竖直光学厚度。 */
    static final double[] EARTH_RAYLEIGH = {0.05, 0.115, 0.28};

    /** 着色器里的画法；括号里是参照的 NASA 照片。 */
    public enum Style {
        /** 有海有陆地（阿波罗 17 号的"蓝色弹珠"）。 */
        EARTH,
        /** 几乎全是海的水世界。 */
        OCEAN,
        /** 没有大气的岩石星球（阿波罗 11 号拍的满月、水星）。 */
        MOON,
        /** 稀薄大气的锈色沙漠（海盗号拼的火星）。 */
        MARS,
        /** 冰壳上交错的红褐色条纹（伽利略号的木卫二）。 */
        ICE,
        /** 硫黄和火山口（伽利略号的木卫一）。 */
        VOLCANIC,
        /** 看不见地面的浓密云层（水手 10 号的金星）。 */
        VENUS,
        /** 橙色或黄绿色的厚雾（卡西尼号的土卫六）。 */
        HAZE,
        /** 对比强烈的云带和大风暴（朱诺号的木星）。 */
        JUPITER,
        /** 淡金色、对比柔和的云带（卡西尼号的土星）。 */
        SATURN,
        /** 深蓝色的冰巨星（旅行者 2 号的海王星）。 */
        ICE_GIANT,
        /** 奇异星球：发光的缝把地表切成一块块。 */
        EXOTIC;

        /** 着色器里的编号。 */
        public int shaderId() {
            return this.ordinal() + 1;
        }
    }

    public static PlanetAppearance of(Planet planet) {
        Rng rng = Rng.of(Seeds.derive(planet.seed(), "sky/appearance"));
        Palette palette = planet.palette();
        Atmosphere atmosphere = planet.atmosphere();
        Style style = style(planet);
        double pressure = atmosphere.present() ? atmosphere.pressure() : 0;
        double[] a;
        double[] b;
        double[] c;
        double clouds = 0;
        double sea = 0.5;
        double cap = 0;
        double relief = 1;
        double emissive = 0;
        double aurora = planet.type() == PlanetType.IRRADIATED ? 0.8 : 0;
        double hazeDepth = 0;
        double[] haze = linear(palette.fog(), 0.8, 1.0);
        double top = 0;
        double[] rayleigh = {0, 0, 0};
        switch (style) {
            case EARTH, OCEAN -> {
                int ocean = planet.liquid() == Liquid.ACID ? Colors.lerp(palette.liquid(), 0x9AB020, 0.3) : palette.liquid();
                a = linear(Colors.lerp(ocean, 0x0A2A5A, 0.5), 0.95, 0.045);
                b = linear(Colors.lerp(palette.grass(), 0x3C5A28, 0.45), 0.85, 0.1);
                c = linear(Colors.lerp(palette.sand(), 0xB4916A, 0.4), 0.85, 0.22);
                clouds = clamp(0.3 + 0.12 * pressure + rng.range(-0.06, 0.06), 0.22, 0.58);
                sea = style == Style.OCEAN ? rng.range(0.64, 0.7) : rng.range(0.5, 0.56);
                // 地球大气里的气溶胶近乎白色，不跟着配色里的雾色走
                haze = linear(Colors.lerp(palette.fog(), 0xE8ECF0, 0.7), 0.3, 1.0);
                cap = clamp((285 - planet.temperature()) / 150, 0.03, 0.3);
            }
            case MOON -> {
                a = linear(Colors.lerp(palette.rock(), 0x9A968F, 0.7), 0.12, 0.16);
                b = linear(Colors.lerp(palette.rock(), 0x4A4845, 0.7), 0.15, 0.06);
                c = a;
                relief = rng.range(0.8, 1.3);
                if (planet.type() == PlanetType.SCORCHED) {
                    // 水星那样偏暖的灰
                    a = linear(Colors.lerp(palette.rock(), 0x8C7E6E, 0.6), 0.3, 0.14);
                }
            }
            case MARS -> {
                a = linear(Colors.lerp(palette.surface(), 0xB4603A, 0.45), 0.95, 0.22);
                b = linear(Colors.lerp(palette.rock(), 0x5A3426, 0.45), 0.55, 0.11);
                c = linear(0xEEEEF0, 0.1, 0.85);
                cap = clamp((250 - planet.temperature()) / 600, 0.015, 0.08);
                clouds = pressure > 0.2 ? 0.2 : 0;
                relief = 0.7;
            }
            case ICE -> {
                a = linear(Colors.lerp(palette.surface(), 0xE6E2DA, 0.65), 0.2, 0.62);
                b = linear(Colors.lerp(palette.accent(), 0x8C5032, 0.55), 0.6, 0.28);
                c = a;
            }
            case VOLCANIC -> {
                a = linear(Colors.lerp(palette.sand(), 0xD7C860, 0.6), 0.7, 0.45);
                b = linear(Colors.lerp(palette.liquid(), 0x8C5A2A, 0.75), 0.85, 0.17);
                c = linear(0xF0EEE0, 0.1, 0.8);
                emissive = 2.4;
            }
            case VENUS -> {
                a = linear(Colors.lerp(palette.cloud(), 0xE8DAB4, 0.6), 0.35, 0.7);
                b = linear(Colors.lerp(palette.fog(), 0xC8A878, 0.6), 0.4, 0.56);
                c = a;
            }
            case HAZE -> {
                a = linear(Colors.lerp(palette.fog(), 0xD49A46, 0.35), 1.0, 0.3);
                b = linear(Colors.lerp(palette.sky(), 0xA87430, 0.35), 0.7, 0.24);
                c = a;
                haze = linear(Colors.lerp(palette.fog(), 0xD8A050, 0.3), 0.8, 0.9);
            }
            case JUPITER -> {
                a = linear(Colors.lerp(palette.surface(), 0xE6D2B0, 0.55), 0.85, 0.5);
                b = linear(Colors.lerp(palette.soil(), 0x9A6A46, 0.5), 0.95, 0.24);
                c = linear(Colors.lerp(palette.rock(), 0xC05A3A, 0.55), 0.8, 0.3);
            }
            case SATURN -> {
                a = linear(Colors.lerp(palette.surface(), 0xD8B478, 0.75), 0.95, 0.44);
                b = linear(Colors.lerp(palette.soil(), 0xA88050, 0.75), 1.0, 0.3);
                c = linear(0xF0E6D2, 0.2, 0.7);
            }
            case ICE_GIANT -> {
                a = linear(Colors.lerp(palette.surface(), 0x3C6ED2, 0.6), 0.8, 0.32);
                b = linear(Colors.lerp(palette.soil(), 0x24489A, 0.6), 0.85, 0.22);
                c = linear(0xE6F0FF, 0.15, 0.9);
            }
            default -> {
                a = linear(palette.surface(), 0.85, 0.3);
                b = linear(palette.rock(), 0.85, 0.2);
                c = linear(palette.accent(), 1.0, 0.9);
                emissive = 1.6;
            }
        }
        if (atmosphere.present() || planet.type() == PlanetType.GAS_GIANT) {
            double[] sky = linear(palette.sky(), 1.0, 1.0);
            double max = Math.max(sky[0], Math.max(sky[1], Math.max(sky[2], 1e-3)));
            // 天空是什么颜色，散射出来就是什么颜色；气压越高越厚
            double strength = planet.type() == PlanetType.GAS_GIANT ? 0.12 : Math.min(2.5, Math.pow(Math.max(pressure, 0.05), 0.7));
            boolean giant = planet.type() == PlanetType.GAS_GIANT;
            for (int i = 0; i < 3; i++) {
                // 气态巨行星边缘的薄雾按地球的瑞利散射算，不随配色
                double tint = giant ? 1 : 0.35 + 0.65 * sky[i] / max;
                rayleigh[i] = EARTH_RAYLEIGH[2] * strength * tint * tint * (0.45 + 0.55 * EARTH_RAYLEIGH[i] / EARTH_RAYLEIGH[2]);
            }
            top = planet.type() == PlanetType.GAS_GIANT ? 0.012 : 0.022 + 0.01 * Math.min(pressure, 4);
            hazeDepth = 0.02 + 0.06 * planet.weather().wind() * (planet.liquid() == Liquid.NONE ? 1 : 0.4);
            switch (style) {
                case MARS -> hazeDepth = 0.12;
                case VENUS -> {
                    hazeDepth = 0.6;
                    top = 0.04;
                }
                case HAZE -> {
                    hazeDepth = 1.2;
                    top = 0.07;
                }
                default -> {
                }
            }
        }
        double ringOpacity = 0;
        double[] ringColor = {0, 0, 0};
        if (planet.rings()) {
            ringOpacity = rng.range(0.7, 0.95);
            ringColor = linear(Colors.lerp(palette.accent(), 0xD2BE9C, 0.7), 0.6, 0.62);
        }
        return new PlanetAppearance(style, a, b, c, clouds, sea, cap, relief, emissive, aurora, rayleigh, top, haze, hazeDepth,
                ringColor, ringOpacity, rng.nextDouble());
    }

    /** 平均反照率，用来定曝光：暗的月面和亮的云顶都画得出细节，像探测器拍照时各自调过曝光。 */
    public double meanAlbedo() {
        double land = switch (this.style) {
            case EARTH -> 0.5 * (luminance(this.b) + luminance(this.c)) * (1 - this.seaLevel) + luminance(this.a) * this.seaLevel;
            case OCEAN -> luminance(this.a) * 0.8 + luminance(this.b) * 0.2;
            default -> 0.5 * (luminance(this.a) + luminance(this.b));
        };
        return land * (1 - this.clouds) + 0.8 * this.clouds;
    }

    /** 曝光倍数：平均反照率 0.3 时为 1.15，暗的星球加亮，但最多加到一倍半。 */
    public double exposure() {
        return 1.15 * clamp(Math.sqrt(0.3 / Math.max(this.meanAlbedo(), 0.02)), 0.8, 1.5);
    }

    private static double luminance(double[] color) {
        return 0.2126 * color[0] + 0.7152 * color[1] + 0.0722 * color[2];
    }

    /** 星球按哪张照片画。 */
    public static Style style(Planet planet) {
        Atmosphere atmosphere = planet.atmosphere();
        double pressure = atmosphere.present() ? atmosphere.pressure() : 0;
        return switch (planet.type()) {
            case GAS_GIANT -> planet.temperature() < 90 ? Style.ICE_GIANT : planet.rings() ? Style.SATURN : Style.JUPITER;
            case HABITABLE -> planet.liquid() == Liquid.NONE ? Style.MARS : Style.EARTH;
            case OCEAN -> Style.OCEAN;
            case BARREN -> pressure > 0.05 ? Style.MARS : Style.MOON;
            case FROZEN -> pressure > 1.5 ? Style.HAZE : Style.ICE;
            case SCORCHED -> planet.liquid() == Liquid.LAVA ? Style.VOLCANIC : pressure >= 2 ? Style.VENUS : Style.MOON;
            case TOXIC -> pressure >= 2.5 ? Style.HAZE : planet.liquid() == Liquid.NONE ? Style.MARS : Style.EARTH;
            case IRRADIATED -> Style.MARS;
            case EXOTIC -> Style.EXOTIC;
        };
    }

    /**
     * 配色里的一种颜色换成着色器用的线性颜色：先把饱和度乘 {@code saturation}，再把亮度（平均反照率）调到 {@code albedo}。
     */
    static double[] linear(int color, double saturation, double albedo) {
        double r = toLinear(Colors.red(color) / 255.0);
        double g = toLinear(Colors.green(color) / 255.0);
        double b = toLinear(Colors.blue(color) / 255.0);
        double gray = 0.2126 * r + 0.7152 * g + 0.0722 * b;
        r = gray + (r - gray) * saturation;
        g = gray + (g - gray) * saturation;
        b = gray + (b - gray) * saturation;
        double mean = Math.max(1e-4, 0.2126 * r + 0.7152 * g + 0.0722 * b);
        double scale = albedo / mean;
        return new double[] {clamp(r * scale, 0, 1), clamp(g * scale, 0, 1), clamp(b * scale, 0, 1)};
    }

    static double toLinear(double srgb) {
        return srgb <= 0.04045 ? srgb / 12.92 : Math.pow((srgb + 0.055) / 1.055, 2.4);
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }
}
