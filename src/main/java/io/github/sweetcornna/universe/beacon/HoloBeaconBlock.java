package io.github.sweetcornna.universe.beacon;

import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.network.HoloBeaconPayloads;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 全息信标（ADR-0033 §六）：一个矮矮的投影台，上面浮着一个图标、一行字和一道光柱。右键改设置和备忘；放下就登记进
 * {@link HoloBeacons}，HUD 和星图照着它标。
 */
public class HoloBeaconBlock extends BaseEntityBlock {
    private static final VoxelShape SHAPE = Shapes.or(
            Block.box(1, 0, 1, 15, 3, 15),
            Block.box(4, 3, 4, 12, 5, 12),
            Block.box(5.5, 5, 5.5, 10.5, 6, 10.5));

    public HoloBeaconBlock(Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new HoloBeaconBlockEntity(pos, state);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.getBlockEntity(pos) instanceof HoloBeaconBlockEntity beacon) {
            beacon.register();
        }
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
        HoloBeacons.get(level.getServer()).remove(level.getServer(), level.dimension(), pos);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (player instanceof ServerPlayer serverPlayer && level.getBlockEntity(pos) instanceof HoloBeaconBlockEntity) {
            PacketDistributor.sendToPlayer(serverPlayer, new HoloBeaconPayloads.Open(pos));
        }
        return InteractionResult.SUCCESS;
    }
}
