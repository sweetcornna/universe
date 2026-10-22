package io.github.sweetcornna.universe.block.landmark;

import io.github.sweetcornna.universe.block.station.TradeTerminalBlock;
import io.github.sweetcornna.universe.trade.Trading;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * 交易站的交易终端（ADR-0026）：和空间站的一样右键交易，行情照本恒星系的空间站，货少一些。挖不掉。
 */
public class OutpostTradeTerminalBlock extends TradeTerminalBlock {
    public OutpostTradeTerminalBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (level instanceof ServerLevel serverLevel && player instanceof ServerPlayer serverPlayer) {
            Trading.openOutpost(serverPlayer, serverLevel, pos);
        }
        return InteractionResult.SUCCESS;
    }
}
