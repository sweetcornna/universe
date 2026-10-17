package io.github.sweetcornna.universe.core.sky;

import io.github.sweetcornna.universe.core.galaxy.Planet;

/**
 * 天空里的一个天体。
 *
 * <p>方向用黄道坐标：经度从恒星的方向起算，向东为正；纬度是离开黄道面的角度。客户端把整个天球按太阳的
 * 运行角度转动，恒星就沿原版太阳的路径走，其他天体和它保持相对位置。
 *
 * @param kind            种类
 * @param planet          对应的星球；恒星为 null
 * @param longitude       黄道经度（弧度），恒星为 0
 * @param latitude        黄道纬度（弧度）
 * @param angularDiameter 显示用的视直径（度）。真实的视直径小得看不见，这里压缩过
 * @param illumination    被照亮的比例 0–1（满月为 1）
 * @param distance        到观察者的距离（AU），用来决定遮挡的先后
 * @param color           主色
 * @param bandColor       第二种颜色：气态巨行星的云带，类地星球的陆地
 * @param rings           是否画行星环
 */
public record SkyBody(
        Kind kind,
        Planet planet,
        double longitude,
        double latitude,
        double angularDiameter,
        double illumination,
        double distance,
        int color,
        int bandColor,
        boolean rings) {
    public enum Kind {
        STAR,
        /** 同一恒星系里的其他行星。 */
        PLANET,
        /** 观察者所在行星的卫星。 */
        MOON,
        /** 观察者站在卫星上时，它绕着的那颗行星。 */
        PARENT,
        /** 观察者站在卫星上时，同一颗行星的其他卫星。 */
        SIBLING
    }
}
