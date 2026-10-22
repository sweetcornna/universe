package io.github.sweetcornna.universe.core.sky;

import io.github.sweetcornna.universe.core.galaxy.Star;
import io.github.sweetcornna.universe.core.galaxy.StarClass;
import io.github.sweetcornna.universe.core.galaxy.StarSystem;
import io.github.sweetcornna.universe.core.procgen.Colors;
import io.github.sweetcornna.universe.core.procgen.Rng;
import io.github.sweetcornna.universe.core.procgen.Seeds;

/**
 * 一颗恒星画出来的样子（ADR-0012）：主序星的米粒组织和黑子，红巨星少而大的对流斑和起伏的边缘，
 * 白矮星的衍射星芒和行星状星云，脉冲星扫过的两道光束和星云，黑洞的阴影、光子环和被引力透镜弯折的吸积盘。
 *
 * <p>只描述"画成什么样"，由恒星类型和恒星系种子决定；客户端的着色器按这里的数画。长度以恒星的半径为单位，
 * 黑洞以阴影的半径为单位。
 *
 * @param style        画法
 * @param spaceRadius  在太空维度里的半径（格），飞船也飞不进这个范围；黑洞是阴影的半径
 * @param glowColor    日冕和光晕的颜色
 * @param granulation  米粒组织的尺度 0–1，越大斑块越粗
 * @param activity     活动性 0–1：黑子的多少
 * @param spikes       衍射星芒的强度 0–1
 * @param nebula       星云的强度 0–1（白矮星的行星状星云、脉冲星的星风云），没有为 0
 * @param nebulaColor  星云外圈的颜色
 * @param nebulaRadius 星云的半径
 * @param period       脉冲星的自转周期（秒，画面上放慢过）
 * @param beamTilt     脉冲星磁轴和自转轴的夹角（弧度）
 * @param axisTilt     脉冲星自转轴、黑洞吸积盘法线和视线的夹角（弧度）
 * @param axisRoll     自转轴（吸积盘）在画面里的方位角（弧度）
 * @param diskInner    吸积盘的内半径（阴影半径的倍数）
 * @param diskOuter    吸积盘的外半径
 * @param diskHot      吸积盘内圈的颜色
 * @param spin         吸积盘的转向：1 逆时针，-1 顺时针
 * @param seed         着色器里噪声的偏移 0–1
 */
public record StarLook(
        Style style,
        double spaceRadius,
        int glowColor,
        double granulation,
        double activity,
        double spikes,
        double nebula,
        int nebulaColor,
        double nebulaRadius,
        double period,
        double beamTilt,
        double axisTilt,
        double axisRoll,
        double diskInner,
        double diskOuter,
        int diskHot,
        int spin,
        double seed) {

    public enum Style {
        /** 主序星。 */
        MAIN_SEQUENCE(0),
        RED_GIANT(1),
        WHITE_DWARF(2),
        /** 中子星画成脉冲星。 */
        PULSAR(3),
        BLACK_HOLE(4);

        private final int shaderId;

        Style(int shaderId) {
            this.shaderId = shaderId;
        }

        /** 着色器里的编号。 */
        public int shaderId() {
            return this.shaderId;
        }
    }

    public static StarLook of(StarSystem system) {
        return of(system.star(), system.seed());
    }

    public static StarLook of(Star star, long systemSeed) {
        Rng rng = Rng.of(Seeds.derive(systemSeed, "sky/star_look"));
        double seed = rng.nextDouble();
        int color = star.color();
        StarClass type = star.starClass();
        return switch (type) {
            case RED_GIANT -> new StarLook(Style.RED_GIANT, 1000, Colors.lerp(color, 0xFF3010, 0.35), 1.0, 0,
                    0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, seed);
            case WHITE_DWARF -> {
                // 一半的白矮星还裹着自己抛出的行星状星云：里面青绿，外圈偏红
                boolean nebula = rng.chance(0.5);
                int outer = Colors.lerp(0xFF5A4A, 0xB070FF, rng.nextDouble() * 0.6);
                yield new StarLook(Style.WHITE_DWARF, 45, Colors.lerp(color, 0xC8DCFF, 0.5), 0, 0, 1.0,
                        nebula ? rng.range(0.6, 1.0) : 0, outer, rng.range(7, 11), 0, 0, 0, 0, 0, 0, 0, 1, seed);
            }
            case NEUTRON_STAR -> new StarLook(Style.PULSAR, 14, 0x9AB4FF, 0, 0, 0.6, rng.range(0.5, 0.9),
                    Colors.lerp(0xFF9050, 0xFFC070, rng.nextDouble()), rng.range(14, 20), rng.range(1.0, 2.6),
                    Math.toRadians(rng.range(30, 65)), Math.toRadians(rng.range(55, 80)), rng.range(0, 2 * Math.PI),
                    0, 0, 0, 1, seed);
            // 阴影大小随质量（5–30 个太阳）
            case BLACK_HOLE -> blackHole(rng, 100 + 2.4 * (star.mass() - 5), color, seed);
            default -> {
                // 主序星：越冷的米粒越粗、黑子越多（红矮星是耀斑星），蓝巨星表面干净，日冕亮、带衍射星芒
                double heat = mainSequenceHeat(type);
                double radius = switch (type) {
                    case M -> 260;
                    case K -> 320;
                    case G -> 380;
                    case F -> 430;
                    case A -> 480;
                    case B -> 600;
                    default -> 760;
                };
                double activity = (1 - heat) * rng.range(0.5, 1.0);
                double spikes = type == StarClass.O || type == StarClass.B ? 0.35 : 0;
                yield new StarLook(Style.MAIN_SEQUENCE, radius, color, 1 - heat, activity, spikes,
                        0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, seed);
            }
        };
    }

    /**
     * 黑洞：吸积盘接近侧视，像《星际穿越》里的卡冈图雅。恒星是黑洞的系和太空里另外放的黑洞（{@code BlackHoles}）都用这个。
     *
     * @param rng    依次取盘面的倾角、方位、内外半径和转向
     * @param shadow 阴影的半径（格）
     * @param color  吸积盘外圈的颜色
     */
    public static StarLook blackHole(Rng rng, double shadow, int color, double seed) {
        return new StarLook(Style.BLACK_HOLE, shadow, color, 0, 0, 0, 0, 0, 0, 0, 0,
                Math.toRadians(rng.range(76, 86)), Math.toRadians(rng.range(-18, 18)),
                rng.range(1.25, 1.5), rng.range(5.2, 6.8), Colors.lerp(color, 0xFFF4DC, 0.75), rng.chance(0.5) ? 1 : -1, seed);
    }

    /** 主序星从冷到热排成 0–1（M 为 0，O 为 1）。 */
    private static double mainSequenceHeat(StarClass type) {
        return switch (type) {
            case M -> 0.0;
            case K -> 0.2;
            case G -> 0.4;
            case F -> 0.6;
            case A -> 0.75;
            case B -> 0.9;
            default -> 1.0;
        };
    }

    /**
     * 在星球天空里显示的视直径（度）。主序星和红巨星照 {@link SkyModel} 压缩过的大小；致密天体真实的视大小
     * 小得看不见，画成一个亮点（靠光晕、星芒、光束显眼）；黑洞的阴影按质量定，吸积盘在它外面再大好几倍。
     *
     * @param shown 按真实大小压缩后的视直径
     */
    public static double skyDiameter(Star star, double shown) {
        return switch (star.starClass()) {
            case WHITE_DWARF -> Math.clamp(shown * 0.5, 1.6, 3.0);
            case NEUTRON_STAR -> 1.2;
            case BLACK_HOLE -> 5.5 + (star.mass() - 5) / 8;
            default -> shown;
        };
    }

    /**
     * 画恒星的四边形要多大（半宽是半径的几倍）：要装下光晕、星芒、光束、星云或吸积盘。
     *
     * @param haloRadius 光晕的半径（恒星半径的倍数），由视大小和大气决定
     */
    public double extent(double haloRadius) {
        double extent = Math.max(2, haloRadius);
        if (this.spikes > 0) {
            extent = Math.max(extent, 14 * this.spikes);
        }
        if (this.nebula > 0) {
            extent = Math.max(extent, this.nebulaRadius * 1.15);
        }
        return switch (this.style) {
            case PULSAR -> Math.max(extent, 40);
            case BLACK_HOLE -> Math.max(extent, this.diskOuter * 1.45);
            case RED_GIANT -> Math.max(extent, 1.6);
            default -> extent;
        };
    }
}
