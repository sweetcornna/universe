package io.github.sweetcornna.universe.block.ship;

import io.github.sweetcornna.universe.poi.PoiInteractions;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 坠毁飞船上损坏的驾驶台（ADR-0024）：冒着烟。背包里有 4 个钛锭和 2 个电路板时右键，修好变回普通驾驶台，朝向不变。
 */
public class DamagedHelmBlock extends HorizontalDirectionalBlock {
    private static final VoxelShape SHAPE = Block.box(1, 0, 1, 15, 12, 15);

    public DamagedHelmBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection());
    }

    @Override
    protected VoxelShape getShape(BlockState state, net.minecraft.world.level.BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    /** 坏掉的驾驶台冒烟、打火花：烟从驾驶舱顶上冒出来，远远就看得见。 */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(2) == 0) {
            level.addAlwaysVisibleParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, true,
                    pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.8, pos.getY() + 2.1,
                    pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.8, 0, 0.06, 0);
        }
        if (random.nextInt(5) == 0) {
            level.addParticle(ParticleTypes.ELECTRIC_SPARK, pos.getX() + 0.2 + random.nextDouble() * 0.6, pos.getY() + 0.6,
                    pos.getZ() + 0.2 + random.nextDouble() * 0.6, (random.nextDouble() - 0.5) * 0.2, 0.1, (random.nextDouble() - 0.5) * 0.2);
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (level instanceof ServerLevel serverLevel && player instanceof ServerPlayer serverPlayer) {
            PoiInteractions.repairHelm(serverPlayer, serverLevel, pos, state);
        }
        return InteractionResult.SUCCESS;
    }
}
