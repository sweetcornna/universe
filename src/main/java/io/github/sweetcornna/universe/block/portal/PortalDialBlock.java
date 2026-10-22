package io.github.sweetcornna.universe.block.portal;

import io.github.sweetcornna.universe.portal.Portals;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** 先驱者传送门的拨号台（ADR-0024）：右键打开拨号盘，按六个认识的符文拨出别的星球的地址。挖不掉。 */
public class PortalDialBlock extends Block {
    public PortalDialBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (level instanceof ServerLevel serverLevel && player instanceof ServerPlayer serverPlayer) {
            Portals.openDial(serverPlayer, serverLevel, pos);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(4) == 0) {
            level.addParticle(ParticleTypes.ENCHANT, pos.getX() + 0.5, pos.getY() + 1.3, pos.getZ() + 0.5,
                    (random.nextDouble() - 0.5) * 0.6, -0.4, (random.nextDouble() - 0.5) * 0.6);
        }
    }
}
