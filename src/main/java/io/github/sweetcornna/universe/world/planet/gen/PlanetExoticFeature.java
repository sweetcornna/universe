package io.github.sweetcornna.universe.world.planet.gen;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.mojang.serialization.MapCodec;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.core.galaxy.ExoticTerrain;
import io.github.sweetcornna.universe.core.galaxy.Planet;
import io.github.sweetcornna.universe.core.poi.Landmark;
import io.github.sweetcornna.universe.core.poi.LandmarkLayout;
import io.github.sweetcornna.universe.core.poi.Poi;
import io.github.sweetcornna.universe.core.poi.PoiLayout;
import io.github.sweetcornna.universe.poi.Landmarks;
import io.github.sweetcornna.universe.poi.Pois;
import io.github.sweetcornna.universe.registry.UniverseBlocks;
import io.github.sweetcornna.universe.world.planet.Planets;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

/**
 * 奇异星球的地貌（ADR-0030 §二）：石柱、石林、晶柱、结核球、多边形地面、石拱。几何全由核心层的
 * {@link ExoticTerrain} 按星球种子算，这里只把它给出的方块段写进区块。
 *
 * <p>放在地物的第一个阶段（地表之后、矿和植物之前）：石柱上也要长植物，矿按自己的规矩照常生成；兴趣点、地标在
 * 最后一个阶段，自己会清掉占地范围里的东西，这里再按 {@link ExoticTerrain.Keepout} 提前让开一遍，免得它们开在
 * 石拱底下。
 */
public record PlanetExoticFeature() implements Feature {
    public static final MapCodec<PlanetExoticFeature> CODEC = MapCodec.unit(PlanetExoticFeature::new);
    public static final ResourceKey<Feature> KEY = ResourceKey.create(Registries.FEATURE, Universe.id("planet_exotic"));
    public static final ResourceKey<PlacedFeature> PLACED = ResourceKey.create(Registries.PLACED_FEATURE,
            Universe.id("planet_exotic"));
    /** 找附近的地标和兴趣点时往外多找这么远，它们自己的占地半径再单独算。 */
    private static final int CLEARANCE = 48;
    /** 星球种子 → 地貌，几颗星球同时生成时各用各的。 */
    private static final Map<Long, Optional<ExoticTerrain>> TERRAINS = new LinkedHashMap<>(8, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Long, Optional<ExoticTerrain>> eldest) {
            return this.size() > 8;
        }
    };

    @Override
    public MapCodec<PlanetExoticFeature> codec() {
        return CODEC;
    }

    /** 这颗星球的地貌；不是奇异星球时为空。可以在世界生成的线程里调用。 */
    public static Optional<ExoticTerrain> terrain(Planet planet) {
        synchronized (TERRAINS) {
            return TERRAINS.computeIfAbsent(planet.seed(), seed -> create(planet));
        }
    }

    private static Optional<ExoticTerrain> create(Planet planet) {
        // 让开兴趣点和地标（地标的分布本身已经让开了传送门、守卫塔和兴趣点，这里只要再让开地标自己）
        Optional<LandmarkLayout> landmarks = Landmarks.layout(planet);
        Optional<PoiLayout> pois = Pois.layout(planet);
        ExoticTerrain.Keepout keepout = (x, z, radius) -> {
            if (landmarks.isPresent()) {
                for (Landmark landmark : landmarks.get().within(x, z, radius + CLEARANCE)) {
                    if (landmark.distance(x, z) < radius + landmark.type().radius() + 4) {
                        return true;
                    }
                }
            }
            if (pois.isPresent()) {
                for (Poi poi : pois.get().within(x, z, radius + CLEARANCE)) {
                    if (poi.distance(x, z) < radius + poi.type().radius() + 4) {
                        return true;
                    }
                }
            }
            return false;
        };
        return ExoticTerrain.of(planet, PlanetGenSpec.of(planet).model(), keepout);
    }

    @Override
    public boolean place(WorldGenLevel level, ChunkGenerator chunkGenerator, RandomSource random, BlockPos origin) {
        Optional<Planet> planet = Planets.planet(level.getLevel());
        if (planet.isEmpty()) {
            return false;
        }
        Optional<ExoticTerrain> found = terrain(planet.get());
        if (found.isEmpty()) {
            return false;
        }
        int x0 = origin.getX();
        int z0 = origin.getZ();
        ExoticTerrain.Chunk chunk = found.get().chunk(x0, z0, read(level, x0, z0));
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int dx = 0; dx < 16; dx++) {
            for (int dz = 0; dz < 16; dz++) {
                for (ExoticTerrain.Span span : chunk.column(dx, dz)) {
                    BlockState state = state(span.material());
                    for (int y = span.bottom(); y <= span.top(); y++) {
                        pos.set(x0 + dx, y, z0 + dz);
                        if (writable(level, pos, span.embed())) {
                            level.setBlock(pos, state, 2);
                        }
                    }
                }
            }
        }
        return true;
    }

    /** 读出这个区块每一列真实的地面高度和状态。 */
    private static ExoticTerrain.Columns read(WorldGenLevel level, int x0, int z0) {
        int[] ground = new int[256];
        byte[] state = new byte[256];
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int dx = 0; dx < 16; dx++) {
            for (int dz = 0; dz < 16; dz++) {
                int x = x0 + dx;
                int z = z0 + dz;
                int floor = level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, x, z) - 1;
                int surface = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z) - 1;
                ground[dx * 16 + dz] = floor;
                pos.set(x, floor + 1, z);
                BlockState above = level.getBlockState(pos);
                pos.set(x, floor, z);
                byte s;
                if (above.is(Blocks.LAVA) || blocked(level, pos)) {
                    s = ExoticTerrain.BLOCKED;
                } else if (surface > floor && !above.getFluidState().isEmpty()) {
                    // 地面上还有液体（海、湖）
                    s = ExoticTerrain.WET;
                } else {
                    s = ExoticTerrain.DRY;
                }
                state[dx * 16 + dz] = s;
            }
        }
        return new ExoticTerrain.Columns(ground, state);
    }

    /** 露出地表的矿和原油涌泉那一列不盖（ADR-0030 §二）。 */
    private static boolean blocked(WorldGenLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.is(UniverseBlocks.CRUDE_OIL_SEEP.get()) || state.is(Blocks.LAVA)) {
            return true;
        }
        for (var ore : UniverseBlocks.ORES.values()) {
            if (state.is(ore.get())) {
                return true;
            }
        }
        return false;
    }

    /** 这一格能不能写：能嵌进地面的替换天然的岩石土沙，否则只写进空气、液体和植物里。 */
    private static boolean writable(WorldGenLevel level, BlockPos pos, boolean embed) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir() || state.canBeReplaced()) {
            return true;
        }
        return embed && natural(state);
    }

    /** 天然的地面方块（矿不换）。 */
    private static boolean natural(BlockState state) {
        return state.is(UniverseBlocks.PLANET_ROCK.get()) || state.is(UniverseBlocks.PLANET_REGOLITH.get())
                || state.is(UniverseBlocks.PLANET_SAND.get()) || state.is(UniverseBlocks.PLANET_ASH.get())
                || state.is(UniverseBlocks.PLANET_STRATA.get()) || state.is(UniverseBlocks.PLANET_COBBLESTONE.get());
    }

    /** 地貌的材质换成方块：都用星球自己的方块，客户端按星球调色板上色。 */
    private static BlockState state(ExoticTerrain.Material material) {
        return switch (material) {
            // 玄武岩柱：柱身用岩石，柱顶用鹅卵石（顶上风化得更碎），节理用岩层
            case COLUMN -> UniverseBlocks.PLANET_ROCK.get().defaultBlockState();
            case COLUMN_TOP -> UniverseBlocks.PLANET_COBBLESTONE.get().defaultBlockState();
            case JOINT -> UniverseBlocks.PLANET_STRATA.get().defaultBlockState();
            // 石林：柱身是一层层的岩层，顶上压一块硬岩
            case SHAFT -> UniverseBlocks.PLANET_STRATA.get().defaultBlockState();
            case CAPROCK -> UniverseBlocks.PLANET_ROCK.get().defaultBlockState();
            case CRYSTAL -> UniverseBlocks.PLANET_CRYSTAL.get().defaultBlockState();
            // 结核球：球身是岩石，白纹用方解石，小黑球用风化层
            case BOULDER -> UniverseBlocks.PLANET_ROCK.get().defaultBlockState();
            case VEIN -> Blocks.CALCITE.defaultBlockState();
            case SPHERULE -> UniverseBlocks.PLANET_REGOLITH.get().defaultBlockState();
            // 多边形的边：盐壳、冰楔、碎石
            case SALT -> UniverseBlocks.PLANET_SAND.get().defaultBlockState();
            case ICE -> Blocks.PACKED_ICE.defaultBlockState();
            case STONES -> UniverseBlocks.PLANET_COBBLESTONE.get().defaultBlockState();
            case ARCH -> UniverseBlocks.PLANET_STRATA.get().defaultBlockState();
        };
    }
}
