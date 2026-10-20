package io.github.sweetcornna.universe.teleport;

import io.github.sweetcornna.universe.factory.MachineBlock;
import io.github.sweetcornna.universe.registry.UniverseBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** 传送器（ADR-0017）：右键打开目的地列表。玩家做的接电网充电，空间站里的不用电。 */
public class TeleporterBlock extends MachineBlock {
    public TeleporterBlock(Properties properties) {
        super(properties, UniverseBlockEntities.TELEPORTER);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (player instanceof ServerPlayer serverPlayer) {
            Teleporters.open(serverPlayer, pos);
        }
        return InteractionResult.SUCCESS;
    }
}
