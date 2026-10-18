package io.github.sweetcornna.universe.block.poi;

import io.github.sweetcornna.universe.poi.PoiInteractions;
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

/**
 * 先驱者遗迹中间的石碑（ADR-0014）：刻着发光的符文，挖不掉。每个玩家第一次接触一块石碑得到勘探数据和一段先驱者的线索。
 */
public class MonolithBlock extends Block {
    public MonolithBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (level instanceof ServerLevel serverLevel && player instanceof ServerPlayer serverPlayer) {
            PoiInteractions.touchMonolith(serverPlayer, serverLevel, pos);
        }
        return InteractionResult.SUCCESS;
    }

    /** 符文周围飘着附魔台那样的字符。 */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(3) == 0) {
            double angle = random.nextDouble() * Math.PI * 2;
            double x = pos.getX() + 0.5 + Math.cos(angle) * 1.4;
            double z = pos.getZ() + 0.5 + Math.sin(angle) * 1.4;
            double y = pos.getY() + 0.2 + random.nextDouble() * 1.2;
            level.addParticle(ParticleTypes.ENCHANT, pos.getX() + 0.5, pos.getY() + 0.8, pos.getZ() + 0.5,
                    x - pos.getX() - 0.5, y - pos.getY() - 0.8, z - pos.getZ() - 0.5);
        }
    }
}
