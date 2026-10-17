package io.github.sweetcornna.universe.core.ecology;

import java.util.List;

import io.github.sweetcornna.universe.core.galaxy.PlanetType;
import io.github.sweetcornna.universe.core.terrain.BiomeKind;

/**
 * 生境：物种按生境挑地方长。31 种群系归成 8 类，每种群系另有地面植物和树木的密度系数。
 * 海洋、冰川、熔岩海不长陆生植物。
 */
public enum Biotope {
    /** 草原、高地、河谷。 */
    MEADOW,
    /** 林地、雨林、异形密林。 */
    FOREST,
    /** 湿地、毒沼、河岸。 */
    WETLAND,
    /** 荒漠、方山、峡谷、尘埃盆地、海滩。 */
    ARID,
    /** 冻原、雪原、雪峰。 */
    COLD,
    /** 菌林。 */
    FUNGAL,
    /** 晶簇原。 */
    CRYSTAL,
    /** 风化层、灰烬、玄武岩这些几乎不长东西的地方，只有地衣一类。 */
    WASTE;

    /** 群系属于哪种生境；不长陆生植物的群系返回 null。 */
    public static Biotope of(BiomeKind biome) {
        return switch (biome) {
            case GRASSLAND, HIGHLANDS -> MEADOW;
            case FOREST, JUNGLE, ALIEN_THICKET -> FOREST;
            case WETLAND, RIVER, TOXIC_MARSH -> WETLAND;
            case DESERT, BEACH, DUST_BASIN, SULFUR_FLATS, MESA, CANYON -> ARID;
            case ALPINE, TUNDRA, SNOWFIELD -> COLD;
            case FUNGAL_FOREST -> FUNGAL;
            case CRYSTAL_FIELDS -> CRYSTAL;
            case ASH_WASTES, BASALT_FIELDS, VOLCANIC, REGOLITH_PLAINS, CRATERED_HIGHLANDS, RADIANT_WASTES -> WASTE;
            case OCEAN, DEEP_OCEAN, FROZEN_OCEAN, GLACIER, LAVA_SEA, ACID_SEA -> null;
        };
    }

    /** 地面植物的密度系数：乘上星球的植被覆盖，就是一格地面长植物的概率（再按斑块起伏）。 */
    public static double plantDensity(BiomeKind biome) {
        return switch (biome) {
            case GRASSLAND -> 0.9;
            case JUNGLE, ALIEN_THICKET -> 0.8;
            case WETLAND -> 0.85;
            case FUNGAL_FOREST -> 0.75;
            case RIVER, TOXIC_MARSH -> 0.7;
            case FOREST, HIGHLANDS -> 0.55;
            case TUNDRA -> 0.4;
            case CRYSTAL_FIELDS -> 0.25;
            case SNOWFIELD -> 0.15;
            case BEACH -> 0.12;
            case ALPINE -> 0.08;
            case DESERT -> 0.06;
            case REGOLITH_PLAINS, RADIANT_WASTES, MESA, CANYON, SULFUR_FLATS -> 0.05;
            case CRATERED_HIGHLANDS, DUST_BASIN -> 0.04;
            case ASH_WASTES, BASALT_FIELDS, VOLCANIC -> 0.03;
            case OCEAN, DEEP_OCEAN, FROZEN_OCEAN, GLACIER, LAVA_SEA, ACID_SEA -> 0;
        };
    }

    /** 树木的密度系数：乘上星球的树木密度和 {@link FloraLayout#TREES_PER_CHUNK}，是一个区块平均有几棵树。 */
    public static double treeDensity(BiomeKind biome) {
        return switch (biome) {
            case JUNGLE -> 1.5;
            case ALIEN_THICKET -> 1.3;
            case FUNGAL_FOREST -> 1.2;
            case FOREST -> 1.0;
            case TOXIC_MARSH -> 0.35;
            case RIVER -> 0.3;
            case WETLAND -> 0.25;
            case CRYSTAL_FIELDS -> 0.15;
            case HIGHLANDS -> 0.12;
            case TUNDRA -> 0.08;
            case GRASSLAND, BEACH -> 0.06;
            case SNOWFIELD -> 0.04;
            case DESERT, MESA, CANYON -> 0.03;
            case ALPINE, SULFUR_FLATS -> 0.02;
            case DUST_BASIN, RADIANT_WASTES -> 0.01;
            default -> 0;
        };
    }

    /** 这类星球上会出现的生境，按重要程度排序：物种先保证排在前面的生境有份。 */
    public static List<Biotope> forPlanet(PlanetType type) {
        return switch (type) {
            case HABITABLE, OCEAN -> List.of(MEADOW, FOREST, WETLAND, COLD, ARID);
            case TOXIC -> List.of(FUNGAL, WETLAND, ARID);
            case EXOTIC -> List.of(MEADOW, FOREST, CRYSTAL, WETLAND);
            case FROZEN -> List.of(COLD, WASTE);
            case SCORCHED -> List.of(WASTE, ARID);
            case BARREN, IRRADIATED -> List.of(WASTE, ARID);
            case GAS_GIANT -> List.of();
        };
    }
}
