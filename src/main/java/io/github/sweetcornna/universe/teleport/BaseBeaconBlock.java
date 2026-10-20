package io.github.sweetcornna.universe.teleport;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 基地信标（ADR-0017）：放下就把这里登记成放的人的一处基地，传送器能传过来；拆掉就删掉。主人右键改名。
 */
public class BaseBeaconBlock extends Block {
    private static final VoxelShape SHAPE = Shapes.or(
            Block.box(2, 0, 2, 14, 3, 14),
            Block.box(5, 3, 5, 11, 13, 11),
            Block.box(4, 13, 4, 12, 16, 12));
    private static final DustParticleOptions GLOW = new DustParticleOptions(0x6EDCF5, 0.9F);

    public BaseBeaconBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (placer instanceof ServerPlayer player) {
            Teleporters.registerBase(player, pos);
        }
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
        BaseData.get(level.getServer()).remove(level.dimension(), pos);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (player instanceof ServerPlayer serverPlayer) {
            Teleporters.openBeacon(serverPlayer, pos);
        }
        return InteractionResult.SUCCESS;
    }

    /** 顶上一圈青色的光点。 */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(3) == 0) {
            double angle = random.nextDouble() * Math.PI * 2;
            level.addParticle(GLOW, pos.getX() + 0.5 + Math.cos(angle) * 0.3, pos.getY() + 1.05, pos.getZ() + 0.5 + Math.sin(angle) * 0.3, 0, 0.02, 0);
        }
    }
}
