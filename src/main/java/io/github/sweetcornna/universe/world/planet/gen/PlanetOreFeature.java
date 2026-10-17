package io.github.sweetcornna.universe.world.planet.gen;

import java.util.Optional;

import com.mojang.serialization.MapCodec;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.block.OreKind;
import io.github.sweetcornna.universe.core.galaxy.Planet;
import io.github.sweetcornna.universe.core.galaxy.Resource;
import io.github.sweetcornna.universe.registry.UniverseBlocks;
import io.github.sweetcornna.universe.world.planet.Planets;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
 * 星球矿脉（玩法设计 §9.1）：按星球的资源表，每种有矿石的资源在每个区块里撒若干条矿脉。丰度决定条数和大小，
 * 稀有的矿（光学晶体、奇异物质……）再按 {@link OreKind#frequency()} 打折。
 *
 * <p>一半矿脉贴着地表往下 3–23 格，峭壁、洞壁上能直接看到；其余散在地表以下的整个岩层里。只替换星岩，
 * 不碰岩层（峡谷、方山的崖壁保持干净的条纹）。
 */
public record PlanetOreFeature() implements Feature {
    public static final MapCodec<PlanetOreFeature> CODEC = MapCodec.unit(PlanetOreFeature::new);
    public static final ResourceKey<Feature> KEY = ResourceKey.create(Registries.FEATURE, Universe.id("planet_ores"));
    public static final ResourceKey<PlacedFeature> PLACED = ResourceKey.create(Registries.PLACED_FEATURE, Universe.id("planet_ores"));
    /** 丰度 1、常见程度 1 的矿每个区块的矿脉数。 */
    static final double VEINS_PER_CHUNK = 14;
    /** 贴近地表的矿脉占比。 */
    private static final double SHALLOW = 0.5;
    private static final Direction[] DIRECTIONS = Direction.values();

    @Override
    public MapCodec<PlanetOreFeature> codec() {
        return CODEC;
    }

    @Override
    public boolean place(WorldGenLevel level, ChunkGenerator chunkGenerator, RandomSource random, BlockPos origin) {
        Optional<Planet> found = Planets.planet(level.getLevel());
        if (found.isEmpty()) {
            return false;
        }
        int x0 = origin.getX();
        int z0 = origin.getZ();
        int bottom = level.getMinY() + 2;
        boolean placed = false;
        for (Resource.Deposit deposit : found.get().resources()) {
            OreKind kind = OreKind.of(deposit.resource());
            if (kind == null || deposit.abundance() <= 0) {
                continue;
            }
            double expected = deposit.abundance() * VEINS_PER_CHUNK * kind.frequency();
            int veins = (int) expected + (random.nextDouble() < expected - (int) expected ? 1 : 0);
            int size = kind.veinSize() + (int) Math.round(deposit.abundance() * kind.veinSize());
            BlockState ore = UniverseBlocks.ORES.get(kind).get().defaultBlockState();
            for (int i = 0; i < veins; i++) {
                int x = x0 + 2 + random.nextInt(12);
                int z = z0 + 2 + random.nextInt(12);
                int top = level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, x, z) - 3;
                if (top <= bottom) {
                    continue;
                }
                int y = random.nextDouble() < SHALLOW
                        ? Math.max(bottom, top - random.nextInt(21))
                        : bottom + random.nextInt(top - bottom);
                placed |= vein(level, random, new BlockPos(x, y, z), ore, size + random.nextInt(4));
            }
        }
        return placed;
    }

    /** 随机游走长出一团矿：不时退回起点，团得紧一些，不会拉成细线。 */
    private static boolean vein(WorldGenLevel level, RandomSource random, BlockPos start, BlockState ore, int size) {
        BlockPos.MutableBlockPos cursor = start.mutable();
        int placed = 0;
        for (int step = 0; step < size * 3 && placed < size; step++) {
            if (level.getBlockState(cursor).is(UniverseBlocks.PLANET_ROCK)) {
                level.setBlock(cursor, ore, 2);
                placed++;
            }
            if (random.nextInt(4) == 0) {
                cursor.set(start);
            }
            cursor.move(DIRECTIONS[random.nextInt(DIRECTIONS.length)]);
        }
        return placed > 0;
    }
}
