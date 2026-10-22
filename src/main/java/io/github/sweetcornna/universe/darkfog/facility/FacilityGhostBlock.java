package io.github.sweetcornna.universe.darkfog.facility;

import org.jspecify.annotations.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 残影（ADR-0028 §四）：黑雾打掉的设施原地留下的半透明蓝色方块。不挡路、一碰就碎、不掉东西；
 * 拿着同一种方块右键就照原来的朝向放回去。画面由 {@code FacilityGhostRenderer} 按方块实体里的原方块画。
 */
public class FacilityGhostBlock extends BaseEntityBlock {
    private static final VoxelShape TALL = Block.box(0, 0, 0, 16, 32, 16);

    public FacilityGhostBlock(Properties properties) {
        super(properties);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new FacilityGhostBlockEntity(pos, state);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return level.getBlockEntity(pos) instanceof FacilityGhostBlockEntity ghost && ghost.tall() ? TALL : Shapes.block();
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
            BlockHitResult hitResult) {
        if (!(level.getBlockEntity(pos) instanceof FacilityGhostBlockEntity ghost) || ghost.original().isAir()
                || !stack.is(ghost.original().getBlock().asItem())) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        if (level instanceof ServerLevel serverLevel && player instanceof ServerPlayer serverPlayer) {
            if (FacilityRepairs.rebuild(serverLevel, pos, serverPlayer)) {
                stack.consume(1, player);
            } else {
                player.sendOverlayMessage(Component.translatable("message.universe.facility_ghost.blocked").withStyle(ChatFormatting.RED));
            }
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof FacilityGhostBlockEntity ghost && !ghost.original().isAir()) {
            player.sendOverlayMessage(Component.translatable("message.universe.facility_ghost.hint", ghost.original().getBlock().getName()));
        }
        return InteractionResult.SUCCESS;
    }
}
