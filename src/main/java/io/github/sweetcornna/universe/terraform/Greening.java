package io.github.sweetcornna.universe.terraform;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.registry.UniverseBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * 生态播种站周围的绿化（ADR-0036 §六）：照现实里的演替，裸岩和土上先长一层壳状地衣（灰绿的、橙黄的），生物量多了以后
 * 地衣底下的土长成苔藓，岩石上的地衣有一部分换成苔藓垫。范围随星球的生物量变大（{@link TerraformRules#greeningRadius}）。
 *
 * <p>每次只挑几格看，只在正在运转的区块里改，不去加载别的区块。生物量掉下去不把方块变回去。
 */
public final class Greening {
    /** 地衣能长的岩石：星球的岩石、碎石、风化层、火山灰，原版的石头、圆石、沙砾。 */
    public static final TagKey<Block> ROCK = TagKey.create(Registries.BLOCK, Universe.id("greening_rock"));
    /** 地衣先长、后来变成苔藓的土和沙（现实里荒漠上的生物结皮也是这样起步的）。 */
    public static final TagKey<Block> SOIL = TagKey.create(Registries.BLOCK, Universe.id("greening_soil"));
    /** 每隔几刻长一次。 */
    public static final int INTERVAL = 20;
    /** 每次挑几格。半径 32 格的圆约三千格，一秒两格，铺满要半个多小时。 */
    public static final int ATTEMPTS = 2;
    /** 地面比播种站高出或低下这么多格就不管了（山崖下、坑底）。 */
    private static final int VERTICAL = 24;

    private Greening() {
    }

    /**
     * 在 {@code center} 周围半径 {@code radius} 的圆里随机挑 {@code attempts} 格试着长一次。
     *
     * @return 改了几格
     */
    public static int spread(ServerLevel level, BlockPos center, int radius, double biomass, RandomSource random, int attempts) {
        if (radius <= 0 || !(biomass > 0)) {
            return 0;
        }
        int changed = 0;
        for (int i = 0; i < attempts; i++) {
            int dx = random.nextInt(2 * radius + 1) - radius;
            int dz = random.nextInt(2 * radius + 1) - radius;
            if (dx * dx + dz * dz > radius * radius) {
                continue;
            }
            int x = center.getX() + dx;
            int z = center.getZ() + dz;
            if (!level.shouldTickBlocksAt(ChunkPos.pack(x >> 4, z >> 4))) {
                continue;
            }
            BlockPos ground = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1, z);
            if (Math.abs(ground.getY() - center.getY()) > VERTICAL) {
                continue;
            }
            if (grow(level, ground, biomass, random)) {
                changed++;
            }
        }
        return changed;
    }

    /** 在这块地面上长一步：光着的盖上地衣；已经有地衣、生物量也够了的换成苔藓。 */
    public static boolean grow(ServerLevel level, BlockPos ground, double biomass, RandomSource random) {
        BlockState floor = level.getBlockState(ground);
        boolean soil = floor.is(SOIL);
        if (!soil && !floor.is(ROCK)) {
            return false;
        }
        BlockPos above = ground.above();
        BlockState cover = level.getBlockState(above);
        if (cover.is(UniverseBlocks.GRAY_LICHEN.get()) || cover.is(UniverseBlocks.ORANGE_LICHEN.get())) {
            if (biomass < TerraformRules.MOSS_BIOMASS || random.nextInt(3) != 0) {
                return false;
            }
            if (soil) {
                level.setBlock(above, Blocks.AIR.defaultBlockState(), 3);
                level.setBlock(ground, Blocks.MOSS_BLOCK.defaultBlockState(), 3);
                return true;
            }
            // 岩石上的地衣一半留着，一半让给苔藓垫
            if (random.nextBoolean()) {
                level.setBlock(above, Blocks.MOSS_CARPET.defaultBlockState(), 3);
                return true;
            }
            return false;
        }
        if (!cover.isAir() || !floor.isFaceSturdy(level, ground, Direction.UP)) {
            return false;
        }
        // 橙黄的石黄衣多长在岩石上，土上多是灰绿的
        boolean orange = random.nextInt(100) < (soil ? 15 : 35);
        level.setBlock(above, (orange ? UniverseBlocks.ORANGE_LICHEN : UniverseBlocks.GRAY_LICHEN).get().defaultBlockState(), 3);
        return true;
    }
}
