package io.github.sweetcornna.universe.block.sentinel;

import io.github.sweetcornna.universe.registry.UniverseItems;
import io.github.sweetcornna.universe.sentinel.SentinelPillars;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;

/**
 * 守卫塔底下的终端（ADR-0026）：右键关停这颗星球的守卫机 {@value SentinelPillars#SHUTDOWN_MINUTES} 分钟，
 * 一个游戏日一次；被通缉时不响应。拿着步行机主脑右键（或潜行空手右键）取出守卫机型多功能工具。挖不掉。
 */
public class PillarTerminalBlock extends HorizontalDirectionalBlock {
    private static final int SCREEN_COLOR = 0xFFB43A;

    public PillarTerminalBlock(Properties properties) {
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
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
            BlockHitResult hitResult) {
        if (!stack.is(UniverseItems.WALKER_BRAIN.get())) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        if (level instanceof ServerLevel serverLevel && player instanceof ServerPlayer serverPlayer) {
            SentinelPillars.useTerminalForTool(serverPlayer, serverLevel, pos);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (level instanceof ServerLevel serverLevel && player instanceof ServerPlayer serverPlayer) {
            if (player.isSecondaryUseActive()) {
                SentinelPillars.useTerminalForTool(serverPlayer, serverLevel, pos);
            } else {
                SentinelPillars.useTerminal(serverPlayer, serverLevel, pos);
            }
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(3) != 0) {
            return;
        }
        // 屏幕前飘着几点琥珀色的光
        Direction facing = state.getValue(FACING);
        double x = pos.getX() + 0.5 + facing.getStepX() * 0.56 + (random.nextDouble() - 0.5) * 0.5;
        double y = pos.getY() + 0.3 + random.nextDouble() * 0.5;
        double z = pos.getZ() + 0.5 + facing.getStepZ() * 0.56 + (random.nextDouble() - 0.5) * 0.5;
        level.addParticle(new DustParticleOptions(SCREEN_COLOR, 0.5F), x, y, z, 0, 0.02, 0);
    }
}
