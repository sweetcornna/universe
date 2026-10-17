package io.github.sweetcornna.universe.core.terrain;

import java.util.EnumSet;
import java.util.Set;

import io.github.sweetcornna.universe.core.galaxy.Liquid;
import io.github.sweetcornna.universe.core.galaxy.Planet;
import io.github.sweetcornna.universe.core.galaxy.PlanetType;

/**
 * 按星球类型和地形采样挑群系：先按海拔分出深海、海洋、海岸、河谷、低地、高地、山峰，再用局部温度和湿度细分。
 *
 * <p>星球自身的温度会整体偏移局部温度：偏冷的宜居星球冻原更多，偏热的荒漠更多。
 */
public final class BiomeLayout {
    private final PlanetType type;
    private final Liquid liquid;
    private final double temperatureBias;
    private final boolean trees;
    private final boolean flora;

    public BiomeLayout(PlanetType type, Liquid liquid, double planetTemperature, double flora, double trees) {
        this.type = type;
        this.liquid = liquid;
        this.temperatureBias = TerrainModel.clamp((planetTemperature - 288) / 40, -1, 1) * 0.6;
        this.flora = flora > 0.15;
        this.trees = trees > 0.2;
    }

    public static BiomeLayout of(Planet planet) {
        return new BiomeLayout(planet.type(), planet.liquid(), planet.temperature(),
                planet.ecology().flora(), planet.ecology().trees());
    }

    public BiomeKind pick(TerrainModel.Sample s) {
        double e = s.elevation();
        double t = TerrainModel.clamp(s.temperature() + this.temperatureBias, -1, 1);
        double h = s.humidity();
        boolean liquidSurface = this.liquid != Liquid.NONE;

        if (liquidSurface && e < 0) {
            return this.ocean(e, t);
        }
        if (liquidSurface && s.river() > 0.55 && e < 6) {
            return this.river(t);
        }
        // 标志性地貌优先：火山、方山、峡谷、沙丘各有专属的群系
        if (s.volcanic() > 0.3) {
            return e > 120 && (t < 0 || this.type == PlanetType.FROZEN) ? BiomeKind.ALPINE : BiomeKind.VOLCANIC;
        }
        if (s.mesa() > 0.08 || s.canyon() > 0.35) {
            return s.mesa() * 3 >= s.canyon() ? BiomeKind.MESA : BiomeKind.CANYON;
        }
        if (s.dunes() > 0.35) {
            return this.dunes();
        }
        return switch (this.type) {
            case HABITABLE, OCEAN -> this.temperate(s, e, t, h);
            case FROZEN -> {
                if (e > 35 || s.mountains() > 10) {
                    yield BiomeKind.ALPINE;
                }
                yield e < -4 || h > 0.3 ? BiomeKind.GLACIER : BiomeKind.SNOWFIELD;
            }
            case SCORCHED -> {
                if (e < 3 && liquidSurface) {
                    yield BiomeKind.ASH_WASTES;
                }
                yield e > 30 || s.mountains() > 8 || h > 0.1 ? BiomeKind.BASALT_FIELDS : BiomeKind.ASH_WASTES;
            }
            case BARREN -> this.airless(s, e, BiomeKind.REGOLITH_PLAINS);
            case IRRADIATED -> this.airless(s, e, BiomeKind.RADIANT_WASTES);
            case TOXIC -> {
                if (e < 3 && liquidSurface) {
                    yield BiomeKind.TOXIC_MARSH;
                }
                if (h > 0.2 && this.flora) {
                    yield BiomeKind.FUNGAL_FOREST;
                }
                yield e < 10 && h > -0.3 ? BiomeKind.TOXIC_MARSH : BiomeKind.SULFUR_FLATS;
            }
            case EXOTIC -> {
                if (e < 3 && liquidSurface) {
                    yield this.shore(t);
                }
                if (e > 35 || s.mountains() > 10) {
                    yield BiomeKind.CRYSTAL_FIELDS;
                }
                if (h > 0.25 && this.flora) {
                    yield BiomeKind.ALIEN_THICKET;
                }
                yield h < -0.2 ? BiomeKind.CRYSTAL_FIELDS : BiomeKind.GRASSLAND;
            }
            case GAS_GIANT -> BiomeKind.REGOLITH_PLAINS;
        };
    }

    private BiomeKind temperate(TerrainModel.Sample s, double e, double t, double h) {
        if (e < 3) {
            return this.shore(t);
        }
        if (s.mountains() > 18 && e > 40 || e > 75) {
            return BiomeKind.ALPINE;
        }
        if (t < -0.45) {
            return BiomeKind.TUNDRA;
        }
        if (e > 32 || s.mountains() > 8) {
            return BiomeKind.HIGHLANDS;
        }
        if (t > 0.5 && h < 0) {
            return BiomeKind.DESERT;
        }
        if (e < 8 && h > 0.35) {
            return BiomeKind.WETLAND;
        }
        if (this.trees && h > 0.45 && t > 0.2) {
            return BiomeKind.JUNGLE;
        }
        if (this.trees && h > 0.05) {
            return BiomeKind.FOREST;
        }
        return BiomeKind.GRASSLAND;
    }

    private BiomeKind dunes() {
        return switch (this.type) {
            case BARREN, IRRADIATED, GAS_GIANT -> BiomeKind.DUST_BASIN;
            case TOXIC -> BiomeKind.SULFUR_FLATS;
            case FROZEN -> BiomeKind.SNOWFIELD;
            case SCORCHED -> BiomeKind.ASH_WASTES;
            case HABITABLE, OCEAN, EXOTIC -> BiomeKind.DESERT;
        };
    }

    /**
     * 这颗星球上某种群系的表层方块。方山、峡谷的台面随星球类型变化：
     * 宜居星球上长着草，冰冻星球积雪，荒芜星球是风化层，炙热星球覆着灰烬。
     */
    public Surface top(BiomeKind kind) {
        if (!kind.stratified()) {
            return kind.top();
        }
        return switch (this.type) {
            case HABITABLE, OCEAN, EXOTIC, TOXIC -> this.flora ? Surface.TURF : Surface.SAND;
            case FROZEN -> Surface.SNOW;
            case BARREN, IRRADIATED, GAS_GIANT -> Surface.REGOLITH;
            case SCORCHED -> Surface.ASH;
        };
    }

    /** 没有液体、几乎没有大气的星球：低洼处积尘，高处是陨坑密布的高地。 */
    private BiomeKind airless(TerrainModel.Sample s, double e, BiomeKind plains) {
        if (e < -6) {
            return BiomeKind.DUST_BASIN;
        }
        if (e > 18 || s.mountains() > 6) {
            return BiomeKind.CRATERED_HIGHLANDS;
        }
        return plains;
    }

    private BiomeKind ocean(double e, double t) {
        return switch (this.liquid) {
            case LAVA -> BiomeKind.LAVA_SEA;
            case ACID -> BiomeKind.ACID_SEA;
            case ICE -> BiomeKind.FROZEN_OCEAN;
            case WATER, NONE -> {
                if (t < -0.6 || this.type == PlanetType.FROZEN) {
                    yield BiomeKind.FROZEN_OCEAN;
                }
                yield e < -20 ? BiomeKind.DEEP_OCEAN : BiomeKind.OCEAN;
            }
        };
    }

    private BiomeKind shore(double t) {
        return switch (this.liquid) {
            case LAVA -> BiomeKind.ASH_WASTES;
            case ACID -> BiomeKind.TOXIC_MARSH;
            case ICE -> BiomeKind.SNOWFIELD;
            case WATER, NONE -> t < -0.5 ? BiomeKind.TUNDRA : BiomeKind.BEACH;
        };
    }

    private BiomeKind river(double t) {
        return switch (this.liquid) {
            case LAVA -> BiomeKind.ASH_WASTES;
            case ACID -> BiomeKind.TOXIC_MARSH;
            case ICE -> BiomeKind.SNOWFIELD;
            case WATER, NONE -> t < -0.5 ? BiomeKind.TUNDRA : BiomeKind.RIVER;
        };
    }

    /** 这颗星球可能出现的全部群系，供群系源登记。宁多勿少：{@link #pick} 返回的群系必须都在里面。 */
    public Set<BiomeKind> possible() {
        Set<BiomeKind> set = EnumSet.of(BiomeKind.VOLCANIC, BiomeKind.ALPINE, BiomeKind.MESA, BiomeKind.CANYON, this.dunes());
        if (this.liquid != Liquid.NONE) {
            set.addAll(this.liquidKinds());
        }
        switch (this.type) {
            case HABITABLE, OCEAN -> set.addAll(EnumSet.of(BiomeKind.BEACH, BiomeKind.TUNDRA, BiomeKind.ALPINE,
                    BiomeKind.HIGHLANDS, BiomeKind.DESERT, BiomeKind.WETLAND, BiomeKind.JUNGLE, BiomeKind.FOREST,
                    BiomeKind.GRASSLAND));
            case FROZEN -> set.addAll(EnumSet.of(BiomeKind.ALPINE, BiomeKind.GLACIER, BiomeKind.SNOWFIELD));
            case SCORCHED -> set.addAll(EnumSet.of(BiomeKind.ASH_WASTES, BiomeKind.BASALT_FIELDS));
            case BARREN -> set.addAll(EnumSet.of(BiomeKind.DUST_BASIN, BiomeKind.CRATERED_HIGHLANDS, BiomeKind.REGOLITH_PLAINS));
            case IRRADIATED -> set.addAll(EnumSet.of(BiomeKind.DUST_BASIN, BiomeKind.CRATERED_HIGHLANDS, BiomeKind.RADIANT_WASTES));
            case TOXIC -> set.addAll(EnumSet.of(BiomeKind.TOXIC_MARSH, BiomeKind.FUNGAL_FOREST, BiomeKind.SULFUR_FLATS));
            case EXOTIC -> set.addAll(EnumSet.of(BiomeKind.CRYSTAL_FIELDS, BiomeKind.ALIEN_THICKET, BiomeKind.GRASSLAND));
            case GAS_GIANT -> set.add(BiomeKind.REGOLITH_PLAINS);
        }
        return set;
    }

    /** 有液体时，海洋、海岸、河谷可能用到的群系。 */
    private Set<BiomeKind> liquidKinds() {
        return switch (this.liquid) {
            case LAVA -> EnumSet.of(BiomeKind.LAVA_SEA, BiomeKind.ASH_WASTES);
            case ACID -> EnumSet.of(BiomeKind.ACID_SEA, BiomeKind.TOXIC_MARSH);
            case ICE -> EnumSet.of(BiomeKind.FROZEN_OCEAN, BiomeKind.SNOWFIELD);
            case WATER, NONE -> EnumSet.of(BiomeKind.OCEAN, BiomeKind.DEEP_OCEAN, BiomeKind.FROZEN_OCEAN,
                    BiomeKind.TUNDRA, BiomeKind.BEACH, BiomeKind.RIVER);
        };
    }
}
