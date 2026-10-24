package io.github.sweetcornna.universe.settlement;

import io.github.sweetcornna.universe.core.civ.Race;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * 聚落建筑（ADR-0034 §二）按种族用的方块，和 {@code SettlementStructures} 里原来那几间小屋一个路数：
 * 商旅族是砂岩墙、云杉木框、橘色的布顶；学者族是白墙、石英柱、浅蓝玻璃、平顶；戍卫族是深板岩砖、黑石、厚墙和垛口。
 *
 * @param found    地基
 * @param floor    地板
 * @param post     立柱
 * @param wall     墙
 * @param band     墙上的一道腰线（升到 B 档以后有）
 * @param window   窗
 * @param roof     平顶、屋脊
 * @param roofStair 坡顶的台阶
 * @param roofSlab 屋檐的半砖
 * @param trim     装饰的雕纹方块
 * @param fence    栏杆
 * @param lamp     立着的灯
 * @param light    嵌在墙里、地里的灯
 * @param door     门
 * @param border   地基外面一圈的边
 * @param road     路
 * @param cloth    布（遮阳棚、旗）的两种颜色
 * @param rich     S 档的贵重装饰
 * @param metal    A 档的金属装饰
 */
public record Palette(BlockState found, BlockState floor, BlockState post, BlockState wall, BlockState band, BlockState window, BlockState roof,
        Block roofStair, Block roofSlab, BlockState trim, BlockState fence, BlockState lamp, BlockState light, Block door, BlockState border,
        BlockState road, DyeColor clothA, DyeColor clothB, BlockState rich, BlockState metal) {

    public static Palette of(Race race) {
        return switch (race) {
            case TRADER -> new Palette(Blocks.CUT_SANDSTONE.defaultBlockState(), Blocks.SPRUCE_PLANKS.defaultBlockState(),
                    Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState(), Blocks.SMOOTH_SANDSTONE.defaultBlockState(),
                    Blocks.DYED_TERRACOTTA.orange().defaultBlockState(), Blocks.GLASS_PANE.defaultBlockState(), Blocks.SPRUCE_PLANKS.defaultBlockState(),
                    Blocks.WOOL_STAIRS.orange(), Blocks.WOOL_SLAB.orange(), Blocks.CHISELED_SANDSTONE.defaultBlockState(),
                    Blocks.SPRUCE_FENCE.defaultBlockState(), Blocks.LANTERN.defaultBlockState(), Blocks.SHROOMLIGHT.defaultBlockState(),
                    Blocks.SPRUCE_DOOR, Blocks.DYED_TERRACOTTA.orange().defaultBlockState(), Blocks.DIRT_PATH.defaultBlockState(),
                    DyeColor.ORANGE, DyeColor.WHITE, Blocks.GOLD_BLOCK.defaultBlockState(), Blocks.CUT_COPPER.waxed().unaffected().defaultBlockState());
            case SCHOLAR -> new Palette(Blocks.SMOOTH_STONE.defaultBlockState(), Blocks.SMOOTH_QUARTZ.defaultBlockState(),
                    Blocks.QUARTZ_PILLAR.defaultBlockState(), Blocks.CONCRETE.white().defaultBlockState(),
                    Blocks.CONCRETE.lightBlue().defaultBlockState(), Blocks.STAINED_GLASS_PANE.lightBlue().defaultBlockState(),
                    Blocks.SMOOTH_QUARTZ.defaultBlockState(), Blocks.SMOOTH_QUARTZ_STAIRS, Blocks.SMOOTH_QUARTZ_SLAB,
                    Blocks.CHISELED_QUARTZ_BLOCK.defaultBlockState(), Blocks.IRON_BARS.defaultBlockState(), Blocks.SEA_LANTERN.defaultBlockState(),
                    Blocks.SEA_LANTERN.defaultBlockState(), Blocks.BIRCH_DOOR, Blocks.CONCRETE.white().defaultBlockState(),
                    Blocks.SMOOTH_QUARTZ.defaultBlockState(), DyeColor.LIGHT_BLUE, DyeColor.WHITE, Blocks.QUARTZ_BRICKS.defaultBlockState(),
                    Blocks.IRON_BLOCK.defaultBlockState());
            case WARDEN -> new Palette(Blocks.POLISHED_BLACKSTONE.defaultBlockState(), Blocks.DEEPSLATE_TILES.defaultBlockState(),
                    Blocks.POLISHED_BASALT.defaultBlockState(), Blocks.DEEPSLATE_BRICKS.defaultBlockState(),
                    Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState(), Blocks.IRON_BARS.defaultBlockState(),
                    Blocks.DEEPSLATE_TILES.defaultBlockState(), Blocks.DEEPSLATE_TILE_STAIRS, Blocks.DEEPSLATE_TILE_SLAB,
                    Blocks.CHISELED_POLISHED_BLACKSTONE.defaultBlockState(), Blocks.POLISHED_BLACKSTONE_BRICK_WALL.defaultBlockState(),
                    Blocks.SOUL_LANTERN.defaultBlockState(), Blocks.PEARLESCENT_FROGLIGHT.defaultBlockState(), Blocks.DARK_OAK_DOOR,
                    Blocks.DEEPSLATE_TILES.defaultBlockState(), Blocks.POLISHED_BLACKSTONE.defaultBlockState(), DyeColor.RED, DyeColor.BLACK,
                    Blocks.GILDED_BLACKSTONE.defaultBlockState(), Blocks.IRON_BLOCK.defaultBlockState());
        };
    }

    /** 朝 {@code facing}（高的一边）的坡顶台阶；{@code top} 是倒过来的。 */
    public BlockState stair(Direction facing, boolean top) {
        return this.roofStair.defaultBlockState().setValue(StairBlock.FACING, facing).setValue(StairBlock.HALF, top ? Half.TOP : Half.BOTTOM);
    }

    public BlockState slab(boolean top) {
        return this.roofSlab.defaultBlockState().setValue(SlabBlock.TYPE, top ? SlabType.TOP : SlabType.BOTTOM);
    }

    public BlockState wool(boolean second) {
        return Blocks.WOOL.pick(second ? this.clothB : this.clothA).defaultBlockState();
    }

    public BlockState carpet(boolean second) {
        return Blocks.CARPET.pick(second ? this.clothB : this.clothA).defaultBlockState();
    }

    public BlockState hanging() {
        return this.lamp.hasProperty(LanternBlock.HANGING) ? this.lamp.setValue(LanternBlock.HANGING, true) : this.lamp;
    }
}
