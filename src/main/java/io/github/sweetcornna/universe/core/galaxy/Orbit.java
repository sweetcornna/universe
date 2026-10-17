package io.github.sweetcornna.universe.core.galaxy;

/**
 * 圆轨道。行星绕恒星，卫星绕行星。
 *
 * @param radius      轨道半径（AU）
 * @param period      公转周期（母星日）
 * @param phase       起始相位 0–1
 * @param inclination 轨道倾角（度），只影响天空里的位置
 */
public record Orbit(double radius, double period, double phase, double inclination) {
    /** 给定时刻（母星日）的相位角，单位是弧度。 */
    public double angleAt(double days) {
        double turns = this.phase + (this.period > 0 ? days / this.period : 0);
        return (turns - Math.floor(turns)) * 2 * Math.PI;
    }
}
