package io.github.sweetcornna.universe.world.planet.gen;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import io.github.sweetcornna.universe.core.galaxy.Liquid;
import io.github.sweetcornna.universe.core.galaxy.Planet;
import io.github.sweetcornna.universe.core.galaxy.TerrainProfile;
import io.github.sweetcornna.universe.core.terrain.BiomeKind;
import io.github.sweetcornna.universe.core.terrain.BiomeLayout;
import io.github.sweetcornna.universe.core.terrain.Surface;
import io.github.sweetcornna.universe.core.terrain.TerrainModel;
import io.github.sweetcornna.universe.registry.UniverseBlocks;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.NoiseRouter;
import net.minecraft.world.level.levelgen.NoiseSettings;
import net.minecraft.world.level.levelgen.Noises;
import net.minecraft.world.level.levelgen.VerticalAnchor;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunction;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunctions;
import net.minecraft.world.level.levelgen.material.MaterialRules;
import net.minecraft.world.level.levelgen.material.condition.MaterialCondition;
import net.minecraft.world.level.levelgen.material.rule.MaterialRule;
import net.minecraft.world.level.levelgen.placement.CaveSurface;
import net.minecraft.world.level.levelgen.synth.NormalNoise;

/**
 * 按星球参数组装区块生成器（技术架构 §3.3）。
 *
 * <p>沿用原版的 {@link NoiseBasedChunkGenerator}，换掉噪声路由、地表规则和群系源：
 * 地表高度来自核心层的 {@link TerrainModel}，在它上面叠加原版的三维噪声做悬崖凹进，
 * 洞穴直接复用原版的芝士洞、意面洞、面条洞（种子随星球变，所以每颗星球的洞穴都不一样）。
 */
public final class PlanetChunkGenerators {
    private static final int MIN_Y = TerrainModel.MIN_Y;
    private static final int HEIGHT = TerrainModel.MAX_Y - TerrainModel.MIN_Y + 1;
    /** 每格高度差换算成多少密度，和原版地表附近的梯度相当，原版洞穴的阈值才能直接用。 */
    private static final float DENSITY_PER_BLOCK = 0.125F;

    private PlanetChunkGenerators() {
    }

    public static ChunkGenerator create(RegistryAccess registries, Planet planet) {
        PlanetGenSpec spec = PlanetGenSpec.of(planet);
        HolderGetter<DensityFunction> functions = registries.lookupOrThrow(Registries.DENSITY_FUNCTION);
        HolderGetter<NormalNoise> noises = registries.lookupOrThrow(Registries.NOISE);
        HolderGetter<Biome> biomes = registries.lookupOrThrow(Registries.BIOME);
        return new NoiseBasedChunkGenerator(new PlanetBiomeSource(spec, biomes), Holder.direct(settings(spec, functions, noises, biomes)));
    }

    static NoiseGeneratorSettings settings(PlanetGenSpec spec, HolderGetter<DensityFunction> functions,
            HolderGetter<NormalNoise> noises, HolderGetter<Biome> biomes) {
        DensityFunction height = new PlanetHeightFunction(spec);
        boolean liquid = spec.liquid() != Liquid.NONE;
        Optional<Aquifer.Config> aquifers = liquid ? Optional.of(aquifers(noises, height)) : Optional.empty();
        return new NoiseGeneratorSettings(
                NoiseSettings.create(MIN_Y, HEIGHT),
                UniverseBlocks.PLANET_ROCK.get().defaultBlockState(),
                fluid(spec.liquid()),
                router(spec, height, functions, noises),
                Holder.direct(materials(spec, biomes)),
                List.of(),
                spec.terrain().seaLevel(),
                false,
                aquifers,
                false,
                NoiseGeneratorSettings.DebugFunctions.EMPTY);
    }

    private static BlockState fluid(Liquid liquid) {
        return switch (liquid) {
            case WATER, ICE -> Blocks.WATER.defaultBlockState();
            // 酸海和含水层都是酸液（ADR-0030）；以前生成的区块还是水
            case ACID -> io.github.sweetcornna.universe.registry.UniverseBlocks.ACID.get().defaultBlockState();
            case LAVA -> Blocks.LAVA.defaultBlockState();
            case NONE -> Blocks.AIR.defaultBlockState();
        };
    }

    static NoiseRouter router(PlanetGenSpec spec, DensityFunction height, HolderGetter<DensityFunction> functions,
            HolderGetter<NormalNoise> noises) {
        TerrainProfile p = spec.terrain();
        DensityFunction y = DensityFunctions.yClampedGradient(MIN_Y * 2, TerrainModel.MAX_Y * 2, MIN_Y * 2, TerrainModel.MAX_Y * 2);
        DensityFunction shaped = DensityFunctions.mul(DensityFunctions.sub(height, y), DensityFunctions.constant(DENSITY_PER_BLOCK));
        if (p.overhangs() > 0.01) {
            // 原版的三维地形噪声，让坡面出现凹进和悬垂
            shaped = DensityFunctions.add(shaped, vanilla(functions, "overworld/base_3d_noise").mul((float) (p.overhangs() * 2)));
        }
        if (p.floatingIslands()) {
            shaped = DensityFunctions.max(shaped, new PlanetIslandFunction(spec));
        }
        DensityFunction slopedCheese = DensityFunctions.cache(shaped);

        DensityFunction caves = slopedCheese;
        if (p.caves() > 0.05) {
            // 洞穴的深度从周围最低的地表算起，陡崖附近的山体不会被掏空
            DensityFunction roofDrop = DensityFunctions.sub(new PlanetCaveRoofFunction(spec), height).mul(DENSITY_PER_BLOCK);
            DensityFunction caveDepth = DensityFunctions.cache(DensityFunctions.add(slopedCheese, roofDrop));
            DensityFunction entrances = vanilla(functions, "overworld/caves/entrances");
            DensityFunction surfaceWithEntrances = DensityFunctions.min(slopedCheese, entrances.mul(5.0F));
            caves = DensityFunctions.rangeChoice(caveDepth, -1000000.0F, 1.5625F, surfaceWithEntrances,
                    underground(functions, noises, caveDepth, p.caves()));
        }
        DensityFunction finalDensity = DensityFunctions.interpolated(slide(caves).mul(0.64F), 4, 8).squeeze();
        if (p.caves() > 0.4) {
            finalDensity = DensityFunctions.min(finalDensity, vanilla(functions, "overworld/caves/noodle"));
        }
        finalDensity = DensityFunctions.add(finalDensity, DensityFunctions.beardifier());

        DensityFunction zero = DensityFunctions.zero();
        return new NoiseRouter(zero, zero, zero, zero, zero, zero, DensityFunctions.interpolated(height, 16, 1), finalDensity);
    }

    /**
     * 原版的地下洞穴，多了一个强度参数：强度越低，芝士洞越少越小。
     * 写法照抄 {@code NoiseRouterData.underground}。
     */
    private static DensityFunction underground(HolderGetter<DensityFunction> functions, HolderGetter<NormalNoise> noises,
            DensityFunction slopedCheese, double intensity) {
        DensityFunction spaghetti2d = vanilla(functions, "overworld/caves/spaghetti_2d");
        DensityFunction roughness = vanilla(functions, "overworld/caves/spaghetti_roughness_function");
        DensityFunction layers = DensityFunctions.noise(noises.getOrThrow(Noises.CAVE_LAYER), 8.0).square().mul(4.0F);
        DensityFunction cheese = DensityFunctions.noise(noises.getOrThrow(Noises.CAVE_CHEESE), 0.6666666666666666);
        float cheeseOffset = 0.27F + (float) (1 - intensity) * 0.45F;
        DensityFunction solidCheese = DensityFunctions.add(
                cheese.add(cheeseOffset).clamp(-1.0F, 1.0F),
                slopedCheese.mul(-0.64F).add(1.5F).clamp(0.0F, 0.5F));
        DensityFunction subtractions = DensityFunctions.min(
                DensityFunctions.min(DensityFunctions.add(layers, solidCheese), vanilla(functions, "overworld/caves/entrances")),
                DensityFunctions.add(spaghetti2d, roughness));
        DensityFunction pillars = vanilla(functions, "overworld/caves/pillars");
        DensityFunction cutPillars = DensityFunctions.rangeChoice(pillars, -1000000.0F, 0.03F,
                DensityFunctions.constant(-1000000.0F), pillars);
        return DensityFunctions.max(subtractions, cutPillars);
    }

    /** 顶部渐变成空气、底部渐变成实心，和原版主世界相同。 */
    private static DensityFunction slide(DensityFunction density) {
        DensityFunction top = DensityFunctions.yClampedGradient(MIN_Y + HEIGHT - 80, MIN_Y + HEIGHT - 64, 1.0F, 0.0F);
        DensityFunction result = DensityFunctions.lerp(top, -0.078125F, density);
        DensityFunction bottom = DensityFunctions.yClampedGradient(MIN_Y, MIN_Y + 24, 0.0F, 1.0F);
        return DensityFunctions.lerp(bottom, 0.1171875F, result);
    }

    private static Aquifer.Config aquifers(HolderGetter<NormalNoise> noises, DensityFunction height) {
        return new Aquifer.Config(
                DensityFunctions.noise(noises.getOrThrow(Noises.AQUIFER_BARRIER), 0.5),
                DensityFunctions.noise(noises.getOrThrow(Noises.AQUIFER_FLUID_LEVEL_FLOODEDNESS), 0.67),
                DensityFunctions.noise(noises.getOrThrow(Noises.AQUIFER_FLUID_LEVEL_SPREAD), 0.7142857142857143),
                DensityFunctions.noise(noises.getOrThrow(Noises.AQUIFER_LAVA)),
                DensityFunctions.constant(-1.0F),
                height);
    }

    private static DensityFunction vanilla(HolderGetter<DensityFunction> functions, String path) {
        ResourceKey<DensityFunction> key = ResourceKey.create(Registries.DENSITY_FUNCTION, Identifier.withDefaultNamespace(path));
        return new DensityFunctions.HolderHolder(functions.getOrThrow(key));
    }

    /**
     * 地表规则：最底下几层基岩；地表按群系铺表层和填充方块，陡坡露出岩石，水下换成水底方块。
     * 其余位置都是默认方块星岩。
     */
    static MaterialRule materials(PlanetGenSpec spec, HolderGetter<Biome> biomes) {
        MaterialCondition onFloor = MaterialRules.stoneDepthCheck(0, false, CaveSurface.FLOOR);
        MaterialCondition underFloor = MaterialRules.stoneDepthCheck(0, true, CaveSurface.FLOOR);
        MaterialCondition deepUnderFloor = MaterialRules.stoneDepthCheck(0, true, 6, CaveSurface.FLOOR);
        MaterialCondition notUnderwater = MaterialRules.waterBlockCheck(-1, 0);
        MaterialCondition steep = new PlanetSteepCondition(spec);
        MaterialRule rock = state(Surface.ROCK);

        BiomeLayout layout = spec.layout();
        MaterialRule strata = state(Surface.STRATA);
        int strataBottom = spec.terrain().seaLevel() - 24;
        List<MaterialRule> perBiome = new ArrayList<>();
        for (BiomeKind kind : layout.possible()) {
            MaterialRule top = state(layout.top(kind));
            if (kind.stratified()) {
                // 方山、峡谷：平顶铺表层，崖壁和内部从海平面下方开始全是彩色岩层
                MaterialRule rules = MaterialRules.sequence(
                        MaterialRules.ifTrue(onFloor, MaterialRules.sequence(
                                MaterialRules.ifTrue(MaterialRules.not(notUnderwater), state(kind.underwater())),
                                MaterialRules.ifTrue(MaterialRules.not(steep), top))),
                        MaterialRules.ifTrue(MaterialRules.yBlockCheck(VerticalAnchor.absolute(strataBottom), 0), strata));
                perBiome.add(MaterialRules.ifTrue(MaterialRules.isBiome(biomes, PlanetBiomes.key(kind)), rules));
                continue;
            }
            // 火山坡上的火山灰里穿插着老熔岩的岩石带，高山雪原上露出一块块岩石
            if (kind == BiomeKind.VOLCANIC || kind == BiomeKind.ALPINE) {
                double threshold = kind == BiomeKind.VOLCANIC ? 0.05 : 0.3;
                top = MaterialRules.sequence(MaterialRules.ifTrue(MaterialRules.noiseCondition2d(Noises.SURFACE, threshold), rock), top);
            }
            MaterialRule filler = state(kind.filler());
            MaterialRule cliff = kind == BiomeKind.GLACIER ? state(Surface.ICE) : rock;
            MaterialRule floor = MaterialRules.sequence(
                    MaterialRules.ifTrue(notUnderwater, MaterialRules.sequence(MaterialRules.ifTrue(steep, cliff), top)),
                    state(kind.underwater()));
            // 冰川的冰层更厚
            MaterialCondition fillerDepth = kind == BiomeKind.GLACIER || kind == BiomeKind.DESERT ? deepUnderFloor : underFloor;
            MaterialRule rules = MaterialRules.sequence(
                    MaterialRules.ifTrue(onFloor, floor),
                    MaterialRules.ifTrue(fillerDepth, MaterialRules.sequence(MaterialRules.ifTrue(steep, cliff), filler)));
            perBiome.add(MaterialRules.ifTrue(MaterialRules.isBiome(biomes, PlanetBiomes.key(kind)), rules));
        }
        // 活火山口：表层 3 格换成熔岩，火山口底是平的，熔岩湖不会外流
        if (spec.model().hasVolcanoes()) {
            perBiome.addFirst(MaterialRules.ifTrue(new PlanetLavaCondition(spec),
                    MaterialRules.ifTrue(MaterialRules.stoneDepthCheck(2, false, CaveSurface.FLOOR),
                            MaterialRules.state(Blocks.LAVA.defaultBlockState()))));
        }
        return MaterialRules.sequence(
                MaterialRules.ifTrue(
                        MaterialRules.verticalGradient("universe:bedrock_floor", VerticalAnchor.bottom(), VerticalAnchor.aboveBottom(5)),
                        MaterialRules.state(Blocks.BEDROCK.defaultBlockState())),
                MaterialRules.ifTrue(MaterialRules.abovePreliminarySurface(), MaterialRules.sequence(perBiome)));
    }

    private static MaterialRule state(Surface surface) {
        return MaterialRules.state(block(surface));
    }

    public static BlockState block(Surface surface) {
        return (switch (surface) {
            case TURF -> UniverseBlocks.PLANET_TURF;
            case SOIL -> UniverseBlocks.PLANET_SOIL;
            case SAND -> UniverseBlocks.PLANET_SAND;
            case ROCK -> UniverseBlocks.PLANET_ROCK;
            case REGOLITH -> UniverseBlocks.PLANET_REGOLITH;
            case SNOW -> UniverseBlocks.PLANET_SNOW;
            case ICE -> UniverseBlocks.PLANET_ICE;
            case ASH -> UniverseBlocks.PLANET_ASH;
            case CRYSTAL -> UniverseBlocks.PLANET_CRYSTAL;
            case STRATA -> UniverseBlocks.PLANET_STRATA;
        }).get().defaultBlockState();
    }
}
