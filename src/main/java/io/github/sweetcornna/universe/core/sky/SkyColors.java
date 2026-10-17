package io.github.sweetcornna.universe.core.sky;

import io.github.sweetcornna.universe.core.galaxy.Atmosphere;
import io.github.sweetcornna.universe.core.galaxy.Liquid;
import io.github.sweetcornna.universe.core.galaxy.Palette;
import io.github.sweetcornna.universe.core.galaxy.Planet;
import io.github.sweetcornna.universe.core.galaxy.Star;
import io.github.sweetcornna.universe.core.procgen.Colors;

/**
 * 一颗星球的天空和大气光学参数，由大气、配色和恒星决定。
 *
 * <p>这里给的是正午、晴天时的值；入夜变暗、下雨变灰由原版的时间线和天气在这之上调整。
 * 没有大气就没有散射：白天天空也是黑的，星星一直看得见，地平线没有雾，也没有云。大气越稀薄，
 * 天空越暗、星星在白天越容易看见；浓密大气的雾更浓、云更低更厚。稀薄而多尘的大气日落偏蓝，
 * 和白天的天色相反（像火星）。
 *
 * @param daySky       正午的天空色
 * @param dayFog       正午的雾色
 * @param sunset       日出日落时地平线的颜色
 * @param sunsetAlpha  日出日落光晕的强度 0–1
 * @param cloud        云色
 * @param cloudAlpha   云的不透明度 0–1，0 表示没有云
 * @param skyLight     日光的颜色：恒星的颜色冲淡一些，红矮星下的世界偏红
 * @param waterFog     水下的雾色
 * @param daytimeStars 白天还能看到的星星亮度 0–1（真空为 1）
 * @param scatter      散射强度，0 是真空，1 接近母星，浓密大气可以超过 1
 * @param fogStart     大气雾开始的距离（方块）
 * @param fogEnd       大气雾完全遮住远景的距离（方块）
 * @param stormColor   沙尘、火山灰、晶尘天气时雾和天空趋向的颜色
 */
public record SkyColors(
        int daySky,
        int dayFog,
        int sunset,
        double sunsetAlpha,
        int cloud,
        double cloudAlpha,
        int skyLight,
        int waterFog,
        double daytimeStars,
        double scatter,
        double fogStart,
        double fogEnd,
        int stormColor) {
    /** 原版主世界大气雾的结束距离，等于没有大气雾。 */
    public static final double NO_FOG = 1024;
    /** 最强的沙尘暴里能看多远（方块）。 */
    public static final double STORM_FOG_END = 36;

    public static SkyColors of(Planet planet, Star star) {
        Palette palette = planet.palette();
        Atmosphere atmosphere = planet.atmosphere();
        int skyLight = Colors.lerp(0xFFFFFF, star.color(), 0.45);
        int waterFog = Colors.lerp(0x050A14, palette.liquid(), 0.4);
        int stormColor = switch (planet.weather().precipitation()) {
            case ASH -> Colors.lerp(0x4A4440, palette.rock(), 0.3);
            case CRYSTAL -> Colors.lerp(palette.accent(), 0xFFFFFF, 0.35);
            default -> Colors.lerp(palette.sand(), palette.fog(), 0.3);
        };
        if (!atmosphere.present()) {
            return new SkyColors(0x000000, 0x000000, 0x000000, 0, palette.cloud(), 0, skyLight, waterFog, 1, 0, 0, NO_FOG,
                    stormColor);
        }
        // 气压 0.01 到 3 映射到散射强度 0.15 到 1.2
        double scatter = clamp(0.15 + 0.85 * Math.log10(1 + atmosphere.pressure() * 9), 0.15, 1.2);
        double visible = Math.min(scatter, 1);
        int daySky = Colors.lerp(0x05060A, palette.sky(), visible);
        int dayFog = Colors.lerp(daySky, palette.fog(), visible);
        int sunset;
        if (atmosphere.kind() == Atmosphere.Kind.THIN) {
            sunset = Colors.lerp(complement(palette.sky()), 0xFFFFFF, 0.25);
        } else {
            sunset = Colors.lerp(0xFF8833, palette.accent(), 0.25);
        }
        double sunsetAlpha = clamp(scatter, 0.2, 1);
        double daytimeStars = clamp(1 - scatter * 1.1, 0, 1);
        double cloudAlpha = switch (atmosphere.kind()) {
            case NONE -> 0;
            // 稀薄大气只有干燥的星球没有云
            case THIN -> planet.liquid() == Liquid.NONE ? 0 : 0.45;
            case BREATHABLE -> 0.8;
            case TOXIC -> 0.85;
            case DENSE -> 0.95;
        };
        double fogStart = 0;
        double fogEnd = switch (atmosphere.kind()) {
            case DENSE -> clamp(260 / Math.sqrt(atmosphere.pressure()), 140, 260);
            case TOXIC -> 420;
            default -> NO_FOG;
        };
        if (atmosphere.kind() == Atmosphere.Kind.DENSE) {
            fogStart = 8;
        }
        return new SkyColors(daySky, dayFog, sunset, sunsetAlpha, palette.cloud(), cloudAlpha, skyLight, waterFog,
                daytimeStars, scatter, fogStart, fogEnd, stormColor);
    }

    /** 色相转半圈、亮度不变的颜色。 */
    static int complement(int color) {
        int r = Colors.red(color);
        int g = Colors.green(color);
        int b = Colors.blue(color);
        int max = Math.max(r, Math.max(g, b));
        int min = Math.min(r, Math.min(g, b));
        return Colors.rgb(max + min - r, max + min - g, max + min - b);
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }
}
