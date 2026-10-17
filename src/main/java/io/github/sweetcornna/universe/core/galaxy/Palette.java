package io.github.sweetcornna.universe.core.galaxy;

/**
 * 星球的配色，颜色都是 {@code 0xRRGGBB}。
 *
 * <p>星球方块用灰度贴图，客户端按当前星球的配色染色（技术架构 §3.4），所以同一种方块在不同星球上颜色不同。
 * 气态巨行星用 {@link #surface()}、{@link #soil()}、{@link #rock()} 作云带的颜色，{@link #accent()} 作行星环。
 *
 * @param surface 地表覆盖层（草皮、苔原、风化层）
 * @param soil    表层下的土壤
 * @param rock    岩石
 * @param sand    沙、沉积物
 * @param foliage 树叶、灌木
 * @param grass   草和小型植物
 * @param liquid  液体
 * @param sky     白天的天空
 * @param fog     雾
 * @param cloud   云
 * @param accent  点缀：花、晶体、发光的东西
 */
public record Palette(
        int surface,
        int soil,
        int rock,
        int sand,
        int foliage,
        int grass,
        int liquid,
        int sky,
        int fog,
        int cloud,
        int accent) {
    /** 母星的配色接近原版平原，只在星图之类的界面里用到。 */
    public static final Palette HOME = new Palette(
            0x7CBD6B, 0x866043, 0x7F7F7F, 0xDBD3A0, 0x59AE30, 0x91BD59, 0x3F76E4, 0x78A7FF, 0xC0D8FF, 0xFFFFFF, 0xF5D33F);
}
