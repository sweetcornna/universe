package io.github.sweetcornna.universe.core.galaxy;

import io.github.sweetcornna.universe.core.procgen.Colors;

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
    /** 不在任何星球上时（主世界、下界、物品栏）星球方块用的中性配色。 */
    public static final Palette NEUTRAL = new Palette(
            0x9C9C88, 0x7D6A55, 0x8E8E8E, 0xD6CDA6, 0x6E9A4E, 0x82AA5C, 0x3F76E4, 0x78A7FF, 0xC0D8FF, 0xFFFFFF, 0xB896F0);

    /** 母星的配色接近原版平原，只在星图之类的界面里用到。 */
    public static final Palette HOME = new Palette(
            0x7CBD6B, 0x866043, 0x7F7F7F, 0xDBD3A0, 0x59AE30, 0x91BD59, 0x3F76E4, 0x78A7FF, 0xC0D8FF, 0xFFFFFF, 0xF5D33F);

    /** 配色里的一项，方块按它决定染什么颜色。 */
    public enum Channel {
        SURFACE, SOIL, ROCK, SAND, FOLIAGE, GRASS, LIQUID, SKY, FOG, CLOUD, ACCENT,
        /** 雪：近白色，带一点天空的色调。 */
        SNOW,
        /** 冰：淡蓝，带一点液体的颜色。 */
        ICE,
        /** 灰烬：地表色压暗。 */
        ASH;

        public int of(Palette palette) {
            return switch (this) {
                case SURFACE -> palette.surface();
                case SOIL -> palette.soil();
                case ROCK -> palette.rock();
                case SAND -> palette.sand();
                case FOLIAGE -> palette.foliage();
                case GRASS -> palette.grass();
                case LIQUID -> palette.liquid();
                case SKY -> palette.sky();
                case FOG -> palette.fog();
                case CLOUD -> palette.cloud();
                case ACCENT -> palette.accent();
                case SNOW -> Colors.lerp(0xF2F6FA, palette.sky(), 0.12);
                case ICE -> Colors.lerp(0xB4D2F0, palette.liquid(), 0.25);
                case ASH -> Colors.lerp(palette.surface(), 0x3A3634, 0.55);
            };
        }
    }
}
