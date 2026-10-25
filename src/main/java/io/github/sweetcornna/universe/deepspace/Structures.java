package io.github.sweetcornna.universe.deepspace;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.core.deepspace.Layout;
import io.github.sweetcornna.universe.core.deepspace.Part;
import io.github.sweetcornna.universe.registry.UniverseBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.storage.loot.LootTable;

/** 把残骸、前哨站的样子（{@link Layout}）搭到世界里，熔毁时拆掉还剩下的部分。 */
public final class Structures {
    /** 前哨站仓库里补给箱的战利品表。 */
    public static final ResourceKey<LootTable> OUTPOST_LOOT = ResourceKey.create(Registries.LOOT_TABLE, Universe.id("chests/deep_space_outpost"));
    /** 搭和拆都只通知客户端：不触发邻居更新，也就不会报给 {@link Hulks} 扣完整度。 */
    private static final int PLACE_FLAGS = Block.UPDATE_CLIENTS;
    private static final int CLEAR_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_SUPPRESS_DROPS | Block.UPDATE_SKIP_BLOCK_ENTITY_SIDEEFFECTS;

    private Structures() {
    }

    /**
     * 搭一座。
     *
     * @param outpost 是前哨站：灯是黑的
     */
    public static void place(ServerLevel level, Placement placement, Layout layout, boolean outpost) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (Layout.Cell cell : layout.cells()) {
            cursor.set(placement.world(cell));
            // 不压掉别人已经放在那里的方块（比如老存档里有人在这儿搭过东西）
            if (!level.getBlockState(cursor).isAir()) {
                continue;
            }
            level.setBlock(cursor, state(cell, placement, outpost), PLACE_FLAGS);
            if (cell.part() == Part.CRATE) {
                RandomizableContainer.setBlockEntityLootTable(level, level.getRandom(), cursor, OUTPOST_LOOT);
            }
        }
    }

    /** 拆掉还是残骸方块的格子（熔毁、开发命令清理）。 */
    public static int clear(ServerLevel level, Placement placement, Layout layout) {
        int removed = 0;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (Layout.Cell cell : layout.cells()) {
            cursor.set(placement.world(cell));
            BlockState there = level.getBlockState(cursor);
            if (derelict(there)) {
                level.setBlock(cursor, Blocks.AIR.defaultBlockState(), CLEAR_FLAGS);
                removed++;
            }
        }
        return removed;
    }

    /**
     * 这片范围里的区块是不是都加载好了。没好就给每个区块挂一张限时的加载票（{@link TicketType#PORTAL}，15 秒），区块在后台加载、
     * 生成，不在这一刻同步生成；隔一会儿再来问。搭、拆之前先问这个，免得一刻里同步生成十几二十个区块。
     */
    public static boolean ready(ServerLevel level, BoundingBox box) {
        ServerChunkCache chunks = level.getChunkSource();
        int x0 = SectionPos.blockToSectionCoord(box.minX());
        int x1 = SectionPos.blockToSectionCoord(box.maxX());
        int z0 = SectionPos.blockToSectionCoord(box.minZ());
        int z1 = SectionPos.blockToSectionCoord(box.maxZ());
        boolean ready = true;
        for (int x = x0; x <= x1 && ready; x++) {
            for (int z = z0; z <= z1 && ready; z++) {
                ready = chunks.getChunkNow(x, z) != null;
            }
        }
        if (!ready) {
            // 已经加载了的也挂上，免得等别的区块的时候它又卸载了
            keepLoaded(level, box);
        }
        return ready;
    }

    /** 给这片范围里的区块挂上（续上）加载票。 */
    public static void keepLoaded(ServerLevel level, BoundingBox box) {
        ServerChunkCache chunks = level.getChunkSource();
        for (int x = SectionPos.blockToSectionCoord(box.minX()); x <= SectionPos.blockToSectionCoord(box.maxX()); x++) {
            for (int z = SectionPos.blockToSectionCoord(box.minZ()); z <= SectionPos.blockToSectionCoord(box.maxZ()); z++) {
                chunks.addTicketWithRadius(TicketType.PORTAL, new ChunkPos(x, z), 0);
            }
        }
    }

    /** 是残骸、前哨站的方块（熔毁时炸掉的就是这些）。 */
    public static boolean derelict(BlockState state) {
        return state.getBlock() instanceof DerelictBlock || state.getBlock() instanceof DerelictConduitBlock
                || state.getBlock() instanceof DerelictWindowBlock || state.getBlock() instanceof DerelictDeviceBlock;
    }

    /** 一格该放什么方块。 */
    static BlockState state(Layout.Cell cell, Placement placement, boolean outpost) {
        return switch (cell.part()) {
            case PLATING -> UniverseBlocks.DERELICT_PLATING.get().defaultBlockState();
            case FRAME -> UniverseBlocks.DERELICT_FRAME.get().defaultBlockState();
            case RADIATOR -> UniverseBlocks.DERELICT_RADIATOR.get().defaultBlockState();
            case TANK -> UniverseBlocks.DERELICT_TANK.get().defaultBlockState();
            case CONDUIT -> UniverseBlocks.DERELICT_CONDUIT.get().defaultBlockState()
                    .setValue(RotatedPillarBlock.AXIS, placement.axis(Direction.Axis.X));
            case CARGO -> UniverseBlocks.DERELICT_CARGO.get().defaultBlockState();
            case SHIELDING -> UniverseBlocks.REACTOR_SHIELDING.get().defaultBlockState();
            case REACTOR -> UniverseBlocks.REACTOR_CORE.get().defaultBlockState();
            case WINDOW -> UniverseBlocks.DERELICT_WINDOW.get().defaultBlockState();
            case LIGHT -> UniverseBlocks.DERELICT_LIGHT.get().defaultBlockState().setValue(DerelictLightBlock.LIT, !outpost);
            case DECK -> UniverseBlocks.DERELICT_DECK.get().defaultBlockState();
            case ENGINE -> UniverseBlocks.DERELICT_ENGINE.get().defaultBlockState();
            case SOLAR -> UniverseBlocks.OUTPOST_SOLAR.get().defaultBlockState();
            case ANTENNA -> UniverseBlocks.OUTPOST_ANTENNA.get().defaultBlockState();
            case DEVICE -> UniverseBlocks.OUTPOST_DEVICE.get().defaultBlockState()
                    .setValue(HorizontalDirectionalBlock.FACING, placement.facing(deviceFacing(cell)));
            case TERMINAL -> UniverseBlocks.DATA_TERMINAL.get().defaultBlockState()
                    .setValue(HorizontalDirectionalBlock.FACING, placement.facing(Direction.WEST));
            case CRATE -> UniverseBlocks.SUPPLY_CRATE.get().defaultBlockState();
        };
    }

    /** 设备的正面朝舱里：靠两侧墙的朝中线，放在中线上的朝 +x（仓库门口）。 */
    private static Direction deviceFacing(Layout.Cell cell) {
        if (cell.z() > 0) {
            return Direction.NORTH;
        }
        return cell.z() < 0 ? Direction.SOUTH : Direction.EAST;
    }
}
