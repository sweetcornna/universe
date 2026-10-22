package io.github.sweetcornna.universe.world;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalLong;

import io.github.sweetcornna.universe.core.galaxy.Planet;
import io.github.sweetcornna.universe.station.Stations;
import io.github.sweetcornna.universe.world.freighter.FreighterChunkGenerator;
import io.github.sweetcornna.universe.world.freighter.FreighterDimensions;
import io.github.sweetcornna.universe.world.planet.PlanetDimensionTypes;
import io.github.sweetcornna.universe.world.planet.Planets;
import io.github.sweetcornna.universe.world.planet.gen.PlanetChunkGenerators;
import io.github.sweetcornna.universe.world.space.SpaceChunkGenerator;
import io.github.sweetcornna.universe.world.space.SpaceDimensions;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterList;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterLists;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.dimension.BuiltinDimensionTypes;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.FlatLevelSource;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.flat.FlatLayerInfo;
import net.minecraft.world.level.levelgen.flat.FlatLevelGeneratorSettings;
import net.minecraft.world.level.levelgen.structure.StructureSet;

/**
 * 运行时维度的地形模板。
 *
 * <p>模板名写进存档（{@code RuntimeDimensionsData}），改名要附带迁移。
 */
public enum DimensionTemplate {
    /**
     * 平坦地形：基岩加两层石头，表层方块按维度 ID 从调色板里挑，传送时一眼能看出到了哪个维度。
     * 不生成结构和地物，维度类型借用主世界的。S1 原型的测试地形。
     */
    FLAT,
    /**
     * 星球：维度 ID 对应星团里的一颗可着陆星球，按星球参数组装区块生成器（技术架构 §3.3），
     * 维度类型按昼夜周期挑选，地形种子用星球自己的种子。
     */
    PLANET,
    /** 原版主世界的地形生成器，开发时用来对照星球地形的生成速度（{@code /universe planet bench}）。 */
    VANILLA,
    /** 太空：一个恒星系一个，虚空里只有一座空间站（{@link SpaceDimensions}，ADR-0017）。 */
    SPACE,
    /** 货船的船舱：一艘货船一个，虚空里一套机库、舰桥和甲板（{@link FreighterDimensions}，ADR-0024）。 */
    FREIGHTER;

    private static final List<Block> SURFACE_PALETTE = List.of(
            Blocks.GRASS_BLOCK, Blocks.SAND, Blocks.SNOW_BLOCK, Blocks.RED_SAND, Blocks.END_STONE,
            Blocks.MOSS_BLOCK, Blocks.DEEPSLATE, Blocks.TERRACOTTA, Blocks.CALCITE, Blocks.BASALT);

    /** 存档里使用的名称。 */
    public String serializedName() {
        return this.name().toLowerCase(Locale.ROOT);
    }

    public static Optional<DimensionTemplate> byName(String name) {
        for (DimensionTemplate template : values()) {
            if (template.serializedName().equals(name)) {
                return Optional.of(template);
            }
        }
        return Optional.empty();
    }

    /** 按模板组装维度。维度类型和群系必须是数据包里已有的：它们属于登录时同步的注册表，运行时不能新增。 */
    LevelStem createLevelStem(MinecraftServer server, ResourceKey<Level> key) {
        return switch (this) {
            case FLAT -> flat(server.registryAccess(), key.identifier());
            case PLANET -> planet(server, key);
            case VANILLA -> vanilla(server.registryAccess());
            case SPACE -> space(server, key);
            case FREIGHTER -> freighter(server);
        };
    }

    private static LevelStem vanilla(RegistryAccess registries) {
        Holder<DimensionType> type = registries.lookupOrThrow(Registries.DIMENSION_TYPE).getOrThrow(BuiltinDimensionTypes.OVERWORLD);
        Holder<MultiNoiseBiomeSourceParameterList> biomes = registries.lookupOrThrow(Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST)
                .getOrThrow(MultiNoiseBiomeSourceParameterLists.OVERWORLD);
        Holder<NoiseGeneratorSettings> settings = registries.lookupOrThrow(Registries.NOISE_SETTINGS).getOrThrow(NoiseGeneratorSettings.OVERWORLD);
        return new LevelStem(type, new NoiseBasedChunkGenerator(MultiNoiseBiomeSource.createFromPreset(biomes), settings));
    }

    private static LevelStem planet(MinecraftServer server, ResourceKey<Level> key) {
        Planet planet = Planets.generatedPlanet(Planets.galaxy(server), key).orElseThrow(
                () -> new IllegalArgumentException("Dimension " + key.identifier() + " is not a landable planet"));
        RegistryAccess registries = server.registryAccess();
        Holder<DimensionType> type = registries.lookupOrThrow(Registries.DIMENSION_TYPE)
                .getOrThrow(PlanetDimensionTypes.forCycle(planet.dayCycle()));
        // 用星球自己的种子：洞穴、矿石等原版噪声每颗星球都不一样
        return new LevelStem(type, PlanetChunkGenerators.create(registries, planet), OptionalLong.of(planet.seed()));
    }

    /**
     * 没有地层、不装饰、不生成结构的平坦生成器就是虚空；群系用原版的虚空，不下雨。生成区块时放上恒星系的空间站。
     */
    private static LevelStem space(MinecraftServer server, ResourceKey<Level> key) {
        RegistryAccess registries = server.registryAccess();
        Holder<DimensionType> type = registries.lookupOrThrow(Registries.DIMENSION_TYPE).getOrThrow(SpaceDimensions.TYPE);
        Holder<Biome> biome = registries.lookupOrThrow(Registries.BIOME).getOrThrow(Biomes.THE_VOID);
        FlatLevelGeneratorSettings settings = new FlatLevelGeneratorSettings(Optional.of(HolderSet.empty()), biome, List.of());
        return new LevelStem(type, new SpaceChunkGenerator(settings, Stations.site(server, key)));
    }

    /** 和太空一样是不下雨的虚空，维度类型换成货船的（床能设重生点）。 */
    private static LevelStem freighter(MinecraftServer server) {
        RegistryAccess registries = server.registryAccess();
        Holder<DimensionType> type = registries.lookupOrThrow(Registries.DIMENSION_TYPE).getOrThrow(FreighterDimensions.TYPE);
        Holder<Biome> biome = registries.lookupOrThrow(Registries.BIOME).getOrThrow(Biomes.THE_VOID);
        FlatLevelGeneratorSettings settings = new FlatLevelGeneratorSettings(Optional.of(HolderSet.empty()), biome, List.of());
        return new LevelStem(type, new FreighterChunkGenerator(settings));
    }

    private static LevelStem flat(RegistryAccess registries, Identifier id) {
        Holder<DimensionType> type = registries.lookupOrThrow(Registries.DIMENSION_TYPE).getOrThrow(BuiltinDimensionTypes.OVERWORLD);
        Holder<Biome> biome = registries.lookupOrThrow(Registries.BIOME).getOrThrow(Biomes.PLAINS);
        // String#hashCode 的算法是 Java 规范固定的，同一个 ID 总是得到同一种表层方块
        Block surface = SURFACE_PALETTE.get(Math.floorMod(id.toString().hashCode(), SURFACE_PALETTE.size()));
        List<FlatLayerInfo> layers = List.of(
                new FlatLayerInfo(1, Blocks.BEDROCK),
                new FlatLayerInfo(2, Blocks.STONE),
                new FlatLayerInfo(1, surface));
        // 结构集合给空集而不是 Optional.empty()：后者表示"全部结构"，会让每个维度都去算要塞位置
        Optional<HolderSet<StructureSet>> noStructures = Optional.of(HolderSet.empty());
        FlatLevelGeneratorSettings settings = new FlatLevelGeneratorSettings(noStructures, biome, List.of())
                .withBiomeAndLayers(layers, noStructures, biome);
        return new LevelStem(type, new FlatLevelSource(settings));
    }
}
