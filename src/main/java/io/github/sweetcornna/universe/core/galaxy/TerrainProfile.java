package io.github.sweetcornna.universe.core.galaxy;

import java.util.List;

/**
 * 地形参数，由适配层组装成原版的噪声地形生成器（技术架构 §3.3）。高度单位是方块。
 *
 * @param seaLevel        海平面高度；没有液体的星球也有这个值，作为低地的参考
 * @param baseHeight      陆地的平均高度
 * @param continentScale  大尺度起伏的水平尺寸，越大地形越开阔
 * @param heightVariation 大尺度起伏的幅度
 * @param hilliness       中尺度丘陵的幅度
 * @param roughness       小尺度细节 0–1
 * @param ridges          山脊感 0–1：越大山越尖、越像连绵的山脉
 * @param terraces        台地感 0–1：越大越像一级级的平顶山
 * @param craters         陨坑密度 0–1
 * @param caves           洞穴密度 0–1
 * @param overhangs       悬崖和悬空地形 0–1，用三维噪声实现
 * @param spikes          尖柱（晶簇、玄武岩柱）的密度 0–1
 * @param floatingIslands 是否有浮空岛（奇异星球）
 * @param landforms       标志性地貌及其强度
 */
public record TerrainProfile(
        int seaLevel,
        int baseHeight,
        double continentScale,
        double heightVariation,
        double hilliness,
        double roughness,
        double ridges,
        double terraces,
        double craters,
        double caves,
        double overhangs,
        double spikes,
        boolean floatingIslands,
        List<Landform.Feature> landforms) {
    /** 母星（主世界）不用这套参数生成，这里只是占位，数值接近原版。 */
    public static final TerrainProfile HOME = new TerrainProfile(63, 72, 600, 40, 16, 0.5, 0.3, 0, 0, 0.5, 0.2, 0, false, List.of());

    public TerrainProfile {
        landforms = List.copyOf(landforms);
    }

    /** 某种地貌的强度，没有时为 0。 */
    public double strength(Landform landform) {
        for (Landform.Feature feature : this.landforms) {
            if (feature.landform() == landform) {
                return feature.strength();
            }
        }
        return 0;
    }

    public TerrainProfile withLandforms(List<Landform.Feature> newLandforms) {
        return new TerrainProfile(this.seaLevel, this.baseHeight, this.continentScale, this.heightVariation, this.hilliness,
                this.roughness, this.ridges, this.terraces, this.craters, this.caves, this.overhangs, this.spikes,
                this.floatingIslands, newLandforms);
    }
}
