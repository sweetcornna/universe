package io.github.sweetcornna.universe.core.galaxy;

/**
 * 一颗恒星。数值以太阳为 1。
 *
 * @param luminosity  光度
 * @param mass        质量，决定行星的公转周期
 * @param radius      半径，决定在行星天空中的视大小
 * @param temperature 表面温度（K）；黑洞为 0
 * @param color       在天空中的颜色 {@code 0xRRGGBB}；黑洞是吸积盘的颜色
 */
public record Star(StarClass starClass, double luminosity, double mass, double radius, double temperature, int color) {
    /** 母星系的恒星：固定为太阳的参数（玩法设计 §1.2）。 */
    public static final Star HOME = new Star(StarClass.G, 1.0, 1.0, 1.0, 5778, 0xFFF4EA);

    /** 宜居带的中心（AU）：在这里的行星平衡温度和母星相近。 */
    public double habitableZone() {
        return Math.sqrt(this.luminosity);
    }

    /** 雪线（AU）：比这里远的地方水会结冰，气态巨行星多在雪线以外。 */
    public double frostLine() {
        return 2.7 * Math.sqrt(this.luminosity);
    }
}
