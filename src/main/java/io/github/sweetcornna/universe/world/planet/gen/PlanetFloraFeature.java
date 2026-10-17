package io.github.sweetcornna.universe.world.planet.gen;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.mojang.serialization.MapCodec;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.block.FloraBlocks;
import io.github.sweetcornna.universe.core.ecology.Flora;
import io.github.sweetcornna.universe.core.ecology.FloraLayout;
import io.github.sweetcornna.universe.core.ecology.PlantSpecies;
import io.github.sweetcornna.universe.core.ecology.TreeBuilder;
import io.github.sweetcornna.universe.core.ecology.TreeSpecies;
import io.github.sweetcornna.universe.core.galaxy.Planet;
import io.github.sweetcornna.universe.core.procgen.Rng;
import io.github.sweetcornna.universe.core.terrain.BiomeKind;
import io.github.sweetcornna.universe.registry.UniverseBlocks;
import io.github.sweetcornna.universe.world.planet.Planets;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

/**
 * 星球植物（玩法设计 §8）：每个区块先长地面植物，再种树。物种、颜色、分布都由 {@code core.ecology} 按星球算，
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
        Optional<FloraLayout> found = Planets.planet(level.getLevel()).flatMap(PlanetFloraFeature::layout);
        if (found.isEmpty()) {
            return false;
        }
        FloraLayout layout = found.get();
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
                this.grow(level, base, tree, random.nextLong());
            }
        }
        return true;
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

    private static boolean soil(BlockState state) {
        return state.is(FloraBlocks.PLANT_SOIL);
    }

    /** 空气、植物、雪层这些可以直接替换的方块。 */
    private static boolean replaceable(BlockState state) {
        return state.isAir() || state.canBeReplaced() && state.getFluidState().isEmpty();
    }
}
