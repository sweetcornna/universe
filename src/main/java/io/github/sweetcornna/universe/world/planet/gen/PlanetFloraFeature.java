package io.github.sweetcornna.universe.world.planet.gen;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.mojang.serialization.MapCodec;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.block.ClamBlock;
import io.github.sweetcornna.universe.block.FloraBlocks;
import io.github.sweetcornna.universe.core.ecology.Flora;
import io.github.sweetcornna.universe.core.ecology.FloraLayout;
import io.github.sweetcornna.universe.core.ecology.Biotope;
import io.github.sweetcornna.universe.core.ecology.PlantSpecies;
import io.github.sweetcornna.universe.core.ecology.ResourceFlora;
import io.github.sweetcornna.universe.core.ecology.ResourcePlant;
import io.github.sweetcornna.universe.core.ecology.TreeBuilder;
import io.github.sweetcornna.universe.core.ecology.TreeSpecies;
import io.github.sweetcornna.universe.core.galaxy.Planet;
import io.github.sweetcornna.universe.core.poi.Landmark;
import io.github.sweetcornna.universe.core.procgen.Rng;
import io.github.sweetcornna.universe.core.terrain.BiomeKind;
import io.github.sweetcornna.universe.poi.Landmarks;
import io.github.sweetcornna.universe.registry.UniverseBlocks;
import io.github.sweetcornna.universe.world.planet.Planets;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

/**
 * 星球植物（玩法设计 §8）：每个区块先长地面植物，再种树，最后放资源植物（ADR-0026）。物种、颜色、分布都由 {@code core.ecology} 按星球算，
 * 这里只负责找地面、放方块。
 *
 * <p>所有星球群系都在植被阶段登记这一个地物（放在区块角上，自己遍历 16×16 列），不是星球维度时什么也不做。
 * 树最多伸出 {@link TreeBuilder#REACH} 格，落在相邻区块里，世界生成允许写这么远。
 */
public record PlanetFloraFeature() implements Feature {
    public static final MapCodec<PlanetFloraFeature> CODEC = MapCodec.unit(PlanetFloraFeature::new);
    public static final ResourceKey<Feature> KEY = ResourceKey.create(Registries.FEATURE, Universe.id("planet_flora"));
    public static final ResourceKey<PlacedFeature> PLACED = ResourceKey.create(Registries.PLACED_FEATURE, Universe.id("planet_flora"));
    /** 星球种子 → 分布，几颗星球同时生成时各用各的。 */
    private static final Map<Long, FloraLayout> LAYOUTS = new LinkedHashMap<>(8, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Long, FloraLayout> eldest) {
            return this.size() > 8;
        }
    };
    private static final Map<Long, ResourceFlora> RESOURCES = new LinkedHashMap<>(8, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Long, ResourceFlora> eldest) {
            return this.size() > 8;
        }
    };
    /** 洞穴髓球从地表往下至少几格开始找。 */
    private static final int CAVE_DEPTH = 8;
    /** 洞顶最多几格高。 */
    private static final int CAVE_ROOF = 8;
    /** 甲壳蚌上面至少有几格水。 */
    private static final int CLAM_DEPTH = 8;
    /** 地标占地半径的上限（建筑不超过中心 15 格）。 */
    private static final int LANDMARK_RADIUS = 15;

    @Override
    public MapCodec<PlanetFloraFeature> codec() {
        return CODEC;
    }

    public static Optional<FloraLayout> layout(Planet planet) {
        if (planet.ecology().lifeless()) {
            return Optional.empty();
        }
        synchronized (LAYOUTS) {
            return Optional.of(LAYOUTS.computeIfAbsent(planet.seed(), seed -> new FloraLayout(Flora.of(planet), planet.ecology())));
        }
    }

    @Override
    public boolean place(WorldGenLevel level, ChunkGenerator chunkGenerator, RandomSource random, BlockPos origin) {
        Optional<Planet> planet = Planets.planet(level.getLevel());
        if (planet.isEmpty()) {
            return false;
        }
        Optional<FloraLayout> found = layout(planet.get());
        if (found.isPresent()) {
            this.flora(level, random, origin, planet.get(), found.get());
        }
        // 资源植物最后放，只占前面留下的空地（ADR-0026），原来的植物和树一株不变
        this.resources(level, planet.get(), origin);
        return true;
    }

    private void flora(WorldGenLevel level, RandomSource random, BlockPos origin, Planet planet, FloraLayout layout) {
        int x0 = origin.getX();
        int z0 = origin.getZ();
        // 先长地面植物，树再盖上去（树能挤掉植物）：反过来的话树冠下的地面找不到，林下就空了
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int dx = 0; dx < 16; dx++) {
            for (int dz = 0; dz < 16; dz++) {
                int x = x0 + dx;
                int z = z0 + dz;
                int y = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z);
                pos.set(x, y, z);
                BiomeKind biome = PlanetBiomes.kind(level.getBiome(pos));
                if (biome == null || !level.isEmptyBlock(pos) || !soil(level.getBlockState(pos.below()))) {
                    continue;
                }
                PlantSpecies plant = layout.plantAt(x, z, biome, random.nextDouble(), random.nextDouble());
                if (plant != null) {
                    BlockState state = UniverseBlocks.PLANTS.get(plant.form()).get().defaultBlockState()
                            .setValue(FloraBlocks.VARIANT, plant.variant())
                            .setValue(FloraBlocks.LIT, plant.glowing());
                    level.setBlock(pos, state, 2);
                }
            }
        }
        pos.set(x0 + 8, 0, z0 + 8);
        pos.setY(level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, pos.getX(), pos.getZ()));
        BiomeKind center = PlanetBiomes.kind(level.getBiome(pos));
        int attempts = center == null ? 0 : layout.treeAttempts(pos.getX(), pos.getZ(), center, random.nextDouble());
        List<Landmark> landmarks = attempts == 0 ? List.of() : landmarks(planet, x0, z0);
        for (int i = 0; i < attempts; i++) {
            int x = x0 + random.nextInt(16);
            int z = z0 + random.nextInt(16);
            int y = level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, x, z);
            BlockPos base = new BlockPos(x, y, z);
            BiomeKind biome = PlanetBiomes.kind(level.getBiome(base));
            if (biome == null || !soil(level.getBlockState(base.below())) || !replaceable(level.getBlockState(base))) {
                continue;
            }
            TreeSpecies tree = layout.treeAt(x, z, biome, random.nextDouble());
            if (tree != null) {
                long seed = random.nextLong();
                if (!clearing(landmarks, x, z)) {
                    this.grow(level, base, tree, seed);
                }
            }
        }
    }

    private static ResourceFlora resourceFlora(Planet planet) {
        synchronized (RESOURCES) {
            return RESOURCES.computeIfAbsent(planet.seed(), seed -> new ResourceFlora(seed, planet.type(), planet.ecology().lifeless()));
        }
    }

    /** 地表一丛丛的资源植物和洞里的髓球。用自己的随机数，不动世界生成的随机数。 */
    private void resources(WorldGenLevel level, Planet planet, BlockPos origin) {
        ResourceFlora flora = resourceFlora(planet);
        int chunkX = origin.getX() >> 4;
        int chunkZ = origin.getZ() >> 4;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (ResourceFlora.Spot spot : flora.spots(chunkX, chunkZ)) {
            pos.set(spot.x(), level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, spot.x(), spot.z()), spot.z());
            if (!level.isEmptyBlock(pos)) {
                continue;
            }
            // 岩石、风化层、沙子也算能长植物的地，先按矿物地面算（盐晶簇长在上面）
            BlockState below = level.getBlockState(pos.below());
            boolean rock = ground(below);
            if (!rock && !soil(below)) {
                continue;
            }
            BiomeKind biome = PlanetBiomes.kind(level.getBiome(pos));
            ResourcePlant plant = flora.pick(biome == null ? null : Biotope.of(biome), rock, spot.roll());
            if (plant != null) {
                level.setBlock(pos, UniverseBlocks.RESOURCE_PLANTS.get(plant).get().defaultBlockState(), 2);
            }
        }
        BlockState marrow = UniverseBlocks.RESOURCE_PLANTS.get(ResourcePlant.CAVE_MARROW).get().defaultBlockState();
        for (ResourceFlora.Spot column : flora.caveColumns(chunkX, chunkZ)) {
            int top = level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, column.x(), column.z()) - CAVE_DEPTH;
            int bottom = level.getMinY() + 4;
            // 从上往下找第一处洞穴的地面：脚下是岩石，头顶几格内有洞顶
            for (int y = top; y > bottom; y--) {
                pos.set(column.x(), y, column.z());
                if (level.isEmptyBlock(pos) && ground(level.getBlockState(pos.below())) && roofed(level, pos)) {
                    level.setBlock(pos, marrow, 2);
                    break;
                }
            }
        }
        if (ResourceFlora.seabedLife(planet)) {
            this.seabed(level, flora, chunkX, chunkZ);
        }
    }

    /** 海底的烛光海带和甲壳蚌（ADR-0026）：海带长在至少两格深的水底，蚌只在 {@link #CLAM_DEPTH} 格以上的深水里。 */
    private void seabed(WorldGenLevel level, ResourceFlora flora, int chunkX, int chunkZ) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        BlockState kelp = UniverseBlocks.RESOURCE_PLANTS.get(ResourcePlant.CANDLE_KELP).get().defaultBlockState();
        for (ResourceFlora.Spot spot : flora.seabedSpots(chunkX, chunkZ)) {
            pos.set(spot.x(), level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, spot.x(), spot.z()), spot.z());
            if (water(level, pos, 2) && sturdy(level, pos.below())) {
                level.setBlock(pos, kelp, 2);
            }
        }
        for (ResourceFlora.Spot spot : flora.clamSpots(chunkX, chunkZ)) {
            pos.set(spot.x(), level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, spot.x(), spot.z()), spot.z());
            if (water(level, pos, CLAM_DEPTH) && sturdy(level, pos.below())) {
                Direction facing = Direction.from2DDataValue((int) (spot.roll() * 4));
                level.setBlock(pos, UniverseBlocks.CLAM.get().defaultBlockState().setValue(ClamBlock.FACING, facing), 2);
            }
        }
    }

    /** 从这一格往上 {@code depth} 格都是静止的水。 */
    private static boolean water(WorldGenLevel level, BlockPos pos, int depth) {
        BlockPos.MutableBlockPos at = pos.mutable();
        for (int i = 0; i < depth; i++) {
            BlockState state = level.getBlockState(at);
            if (!state.is(Blocks.WATER) || !state.getFluidState().isSource()) {
                return false;
            }
            at.move(Direction.UP);
        }
        return true;
    }

    private static boolean sturdy(WorldGenLevel level, BlockPos pos) {
        return level.getBlockState(pos).isFaceSturdy(level, pos, Direction.UP);
    }

    /** 洞顶在 {@link #CAVE_ROOF} 格以内。 */
    private static boolean roofed(WorldGenLevel level, BlockPos floor) {
        BlockPos.MutableBlockPos pos = floor.mutable();
        for (int i = 1; i <= CAVE_ROOF; i++) {
            pos.move(Direction.UP);
            if (ground(level.getBlockState(pos))) {
                return true;
            }
        }
        return false;
    }

    /** 星球的岩石、沙子、风化层这些地面（盐晶簇、髓球长在上面）。 */
    private static boolean ground(BlockState state) {
        return state.is(UniverseBlocks.PLANET_ROCK.get()) || state.is(UniverseBlocks.PLANET_REGOLITH.get())
                || state.is(UniverseBlocks.PLANET_SAND.get()) || state.is(UniverseBlocks.PLANET_ASH.get())
                || state.is(UniverseBlocks.PLANET_STRATA.get()) || state.is(UniverseBlocks.PLANET_COBBLESTONE.get());
    }

    private void grow(WorldGenLevel level, BlockPos base, TreeSpecies tree, long seed) {
        List<TreeBuilder.Block> blocks = TreeBuilder.build(tree, Rng.of(seed));
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        // 离别的树干太近就不种，免得几棵树的树干挤成一团
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                for (int dy = 0; dy <= 3; dy++) {
                    BlockState near = level.getBlockState(pos.setWithOffset(base, dx, dy, dz));
                    if (near.is(UniverseBlocks.PLANET_LOG.get()) || near.is(UniverseBlocks.PLANET_CAP.get())) {
                        return;
                    }
                }
            }
        }
        // 主干底部必须是空地、下面是能长东西的地面，否则这棵树不种（例如贴着崖壁）
        for (TreeBuilder.Block block : blocks) {
            if (block.part().structural() && block.y() <= 2) {
                pos.setWithOffset(base, block.x(), block.y(), block.z());
                if (!replaceable(level.getBlockState(pos))) {
                    return;
                }
                if (block.y() == 0 && !this.rooted(level, pos, this.state(TreeBuilder.Part.LOG_Y, tree))) {
                    return;
                }
            }
        }
        for (TreeBuilder.Block block : blocks) {
            pos.setWithOffset(base, block.x(), block.y(), block.z());
            BlockState current = level.getBlockState(pos);
            boolean free = block.part().structural() ? replaceable(current) || current.is(UniverseBlocks.PLANET_LEAVES.get())
                    : replaceable(current);
            if (free) {
                level.setBlock(pos, this.state(block.part(), tree), 2);
            }
        }
    }

    /** 主干底下是地面；悬空一两格就往下补树根。 */
    private boolean rooted(WorldGenLevel level, BlockPos.MutableBlockPos pos, BlockState root) {
        BlockPos.MutableBlockPos below = pos.mutable().move(Direction.DOWN);
        for (int i = 0; i < 3; i++) {
            BlockState state = level.getBlockState(below);
            if (soil(state) || state.is(UniverseBlocks.PLANET_LOG.get())) {
                for (BlockPos.MutableBlockPos fill = pos.mutable().move(Direction.DOWN); fill.getY() > below.getY(); fill.move(Direction.DOWN)) {
                    level.setBlock(fill, root, 2);
                }
                return true;
            }
            if (!replaceable(state)) {
                return false;
            }
            below.move(Direction.DOWN);
        }
        return false;
    }

    private BlockState state(TreeBuilder.Part part, TreeSpecies tree) {
        return switch (part) {
            case LOG_Y, LOG_X, LOG_Z -> UniverseBlocks.PLANET_LOG.get().defaultBlockState()
                    .setValue(RotatedPillarBlock.AXIS, part == TreeBuilder.Part.LOG_Y ? Direction.Axis.Y
                            : part == TreeBuilder.Part.LOG_X ? Direction.Axis.X : Direction.Axis.Z)
                    .setValue(FloraBlocks.VARIANT, tree.index());
            case LEAVES -> UniverseBlocks.PLANET_LEAVES.get().defaultBlockState()
                    .setValue(LeavesBlock.PERSISTENT, true)
                    .setValue(FloraBlocks.VARIANT, tree.index());
            case CAP -> UniverseBlocks.PLANET_CAP.get().defaultBlockState().setValue(FloraBlocks.VARIANT, tree.index());
            case BLOOM -> UniverseBlocks.PLANET_BLOOM.get().defaultBlockState()
                    .setValue(FloraBlocks.VARIANT, tree.index())
                    .setValue(FloraBlocks.LIT, tree.bloomGlows());
            case CRYSTAL -> UniverseBlocks.PLANET_CRYSTAL.get().defaultBlockState();
        };
    }

    /** 区块附近地上的地标（海底的不算）。 */
    private static List<Landmark> landmarks(Planet planet, int x0, int z0) {
        return Landmarks.layout(planet)
                .map(layout -> layout.within(x0 + 8, z0 + 8, 12 + LANDMARK_RADIUS + TreeBuilder.REACH).stream()
                        .filter(landmark -> !landmark.type().seabed()).toList())
                .orElse(List.of());
    }

    /**
     * 地标占地再往外 {@link TreeBuilder#REACH} 格以内不种树。地标在自己的区块里清了场，可相邻区块后生成，
     * 那边的树冠会伸到建筑和着陆台上。
     */
    private static boolean clearing(List<Landmark> landmarks, int x, int z) {
        for (Landmark landmark : landmarks) {
            int reach = landmark.type().radius() + TreeBuilder.REACH;
            if (landmark.distanceSq(x + 0.5, z + 0.5) <= reach * reach) {
                return true;
            }
        }
        return false;
    }

    private static boolean soil(BlockState state) {
        return state.is(FloraBlocks.PLANT_SOIL);
    }

    /** 空气、植物、雪层这些可以直接替换的方块。 */
    private static boolean replaceable(BlockState state) {
        return state.isAir() || state.canBeReplaced() && state.getFluidState().isEmpty();
    }
}
