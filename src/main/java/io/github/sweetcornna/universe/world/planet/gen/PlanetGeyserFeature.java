package io.github.sweetcornna.universe.world.planet.gen;

import java.util.Optional;

import com.mojang.serialization.MapCodec;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.core.galaxy.Planet;
import io.github.sweetcornna.universe.core.hazard.GeyserKind;
import io.github.sweetcornna.universe.core.hazard.HazardSites;
import io.github.sweetcornna.universe.core.terrain.BiomeKind;
import io.github.sweetcornna.universe.registry.UniverseBlocks;
import io.github.sweetcornna.universe.world.planet.Planets;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

/**
 * 间歇泉（ADR-0034 §六）：冰冻、毒雾、灼热、奇异星球上有水或冰的地方（{@link HazardSites#geyser}），大约每
 * {@value #RARITY} 个区块一处泉眼，一处 {@value #MIN_VENTS}–{@value #MAX_VENTS} 个喷口。泉眼周围的地面结着一层矿壳，
 * 中间略微隆起；喷口照星球分种类（{@link GeyserKind}）。
 *
 * <p>所有星球群系都挂着这个地物，是不是这种星球、这里有没有水在放的时候判断。放在地物的最后一个阶段，兴趣点和地标之后，
 * 地面平整的地方才放，免得压在建筑上。
 */
public record PlanetGeyserFeature() implements Feature {
    public static final MapCodec<PlanetGeyserFeature> CODEC = MapCodec.unit(PlanetGeyserFeature::new);
    public static final ResourceKey<Feature> KEY = ResourceKey.create(Registries.FEATURE, Universe.id("planet_geyser"));
    public static final ResourceKey<PlacedFeature> PLACED = ResourceKey.create(Registries.PLACED_FEATURE, Universe.id("planet_geyser"));
    static final int RARITY = 24;
    static final int MIN_VENTS = 2;
    static final int MAX_VENTS = 5;
    /** 结壳铺多大（格）。 */
    private static final int CRUST = 5;

    @Override
    public MapCodec<PlanetGeyserFeature> codec() {
        return CODEC;
    }

    @Override
    public boolean place(WorldGenLevel level, ChunkGenerator chunkGenerator, RandomSource random, BlockPos origin) {
        Optional<Planet> found = Planets.planet(level.getLevel());
        if (found.isEmpty()) {
            return false;
        }
        Planet planet = found.get();
        Optional<GeyserKind> kind = GeyserKind.of(planet.type());
        if (kind.isEmpty() || random.nextInt(RARITY) != 0) {
            return false;
        }
        int x = origin.getX() + 5 + random.nextInt(6);
        int z = origin.getZ() + 5 + random.nextInt(6);
        int y = level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, x, z);
        BlockPos center = new BlockPos(x, y - 1, z);
        BiomeKind biome = PlanetBiomes.kind(level.getBiome(center));
        if (!HazardSites.geyser(planet.type(), planet.liquid(), biome) || !flat(level, x, y, z)) {
            return false;
        }
        crust(level, random, kind.get(), x, z);
        int vents = MIN_VENTS + random.nextInt(MAX_VENTS - MIN_VENTS + 1);
        BlockState vent = UniverseBlocks.GEYSER_VENTS.get(kind.get()).get().defaultBlockState();
        int placed = 0;
        for (int i = 0; i < vents * 3 && placed < vents; i++) {
            int vx = x + (i == 0 ? 0 : random.nextInt(5) - 2);
            int vz = z + (i == 0 ? 0 : random.nextInt(5) - 2);
            int vy = level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, vx, vz) - 1;
            BlockPos pos = new BlockPos(vx, vy, vz);
            if (level.getBlockState(pos).getBlock() instanceof io.github.sweetcornna.universe.hazard.GeyserVentBlock || !this.open(level, pos)) {
                continue;
            }
            level.setBlock(pos, vent, 2);
            // 世界生成时先把方块实体建出来，区块加载时照常开始走节奏
            level.getBlockEntity(pos);
            placed++;
        }
        return placed > 0;
    }

    /** 泉眼周围平整、地面上没有水：四个角和中间高低差不超过 2 格。 */
    private static boolean flat(WorldGenLevel level, int x, int y, int z) {
        int[][] corners = {{-3, -3}, {3, -3}, {-3, 3}, {3, 3}};
        for (int[] c : corners) {
            if (Math.abs(level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, x + c[0], z + c[1]) - y) > 2) {
                return false;
            }
        }
        return level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z) == y;
    }

    /** 喷口上方要空着：雪层、草之类的清掉，别的东西挡着就不放。 */
    private boolean open(WorldGenLevel level, BlockPos pos) {
        BlockState ground = level.getBlockState(pos);
        if (ground.isAir() || !level.getFluidState(pos).isEmpty() || level.getBlockEntity(pos) != null) {
            return false;
        }
        for (int dy = 1; dy <= 2; dy++) {
            BlockPos above = pos.above(dy);
            BlockState state = level.getBlockState(above);
            if (state.isAir()) {
                continue;
            }
            if (!state.canBeReplaced() || !level.getFluidState(above).isEmpty()) {
                return false;
            }
            level.setBlock(above, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 2);
        }
        return true;
    }

    /** 一片结壳：越靠中间越密，边上零零星星；中间几格高出一层。 */
    private static void crust(WorldGenLevel level, RandomSource random, GeyserKind kind, int x, int z) {
        BlockState crust = UniverseBlocks.GEYSER_CRUSTS.get(kind).get().defaultBlockState();
        for (int dx = -CRUST; dx <= CRUST; dx++) {
            for (int dz = -CRUST; dz <= CRUST; dz++) {
                double distance = Math.sqrt(dx * dx + dz * dz);
                double chance = 1.05 - distance / CRUST;
                if (random.nextDouble() > chance) {
                    continue;
                }
                int y = level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, x + dx, z + dz) - 1;
                BlockPos pos = new BlockPos(x + dx, y, z + dz);
                BlockState ground = level.getBlockState(pos);
                if (ground.isAir() || !level.getFluidState(pos).isEmpty() || level.getBlockEntity(pos) != null
                        || !ground.isSolidRender()) {
                    continue;
                }
                level.setBlock(pos, crust, 2);
                if (distance < 1.6 && random.nextInt(3) != 0 && level.getBlockState(pos.above()).canBeReplaced()) {
                    level.setBlock(pos.above(), crust, 2);
                }
            }
        }
    }
}
