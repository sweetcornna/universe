package io.github.sweetcornna.universe.settlement;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;

/**
 * 聚落终端（ADR-0034 §二）：广场上交易棚边有一台，没有总督时在这里接管，有了总督打开概况。开工时地基前沿也立一台（施工终端），
 * 右键看这块地基的施工、交材料、开张、升级、修理；总督办公室门口那台打开概况。挖不掉，地基上的由聚落自己放、自己拆。
 */
public class SettlementTerminalBlock extends HorizontalDirectionalBlock {
    public SettlementTerminalBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (level instanceof ServerLevel serverLevel && player instanceof ServerPlayer serverPlayer) {
            Settlements.useTerminal(serverPlayer, serverLevel, pos);
        }
        return InteractionResult.SUCCESS;
    }
}
