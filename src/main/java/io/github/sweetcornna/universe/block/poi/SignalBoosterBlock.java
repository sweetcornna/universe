package io.github.sweetcornna.universe.block.poi;

import io.github.sweetcornna.universe.network.OpenSignalBoosterPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 信号增强器（ADR-0014）：右键打开界面，放进一份导航数据、选一种兴趣点，定位附近这个玩家还没去过的最近一个。
 * 兴趣点里有现成的，玩家也能自己做了放下。
 */
public class SignalBoosterBlock extends Block {
    private static final VoxelShape SHAPE = Shapes.or(
            Block.box(1, 0, 1, 15, 4, 15),
            Block.box(4, 4, 4, 12, 11, 12),
            Block.box(7, 11, 7, 9, 16, 9));
    private static final DustParticleOptions SIGNAL = new DustParticleOptions(0xFF6E9A, 0.8F);

    public SignalBoosterBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (player instanceof ServerPlayer serverPlayer) {
            PacketDistributor.sendToPlayer(serverPlayer, new OpenSignalBoosterPayload(pos));
        }
        return InteractionResult.SUCCESS;
    }

    /** 天线顶上一闪一闪的红灯。 */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (level.getGameTime() % 20 < 10 && random.nextInt(2) == 0) {
            level.addParticle(SIGNAL, pos.getX() + 0.5, pos.getY() + 1.05, pos.getZ() + 0.5, 0, 0, 0);
        }
    }
}
