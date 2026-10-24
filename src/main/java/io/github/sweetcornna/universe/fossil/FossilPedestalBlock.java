package io.github.sweetcornna.universe.fossil;

import io.github.sweetcornna.universe.core.fossil.Bone;
import io.github.sweetcornna.universe.registry.UniverseItems;
import io.github.sweetcornna.universe.registry.sound.FossilSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
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

/**
 * 化石底座（ADR-0034 §三）：一根石头的展示柱，上面放一块骨头，哪个部位都行。拿着骨头右键放上去，空手右键拿下来。
 */
public class FossilPedestalBlock extends BaseEntityBlock {
    private static final VoxelShape SHAPE = Shapes.or(
            Block.box(1, 0, 1, 15, 2, 15),
            Block.box(4, 2, 4, 12, 10, 12),
            Block.box(2, 10, 2, 14, 12, 14));

    public FossilPedestalBlock(Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new FossilPedestalBlockEntity(pos, state);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
            BlockHitResult hitResult) {
        Bone bone = BoneItem.boneOf(stack);
        if (bone == null || !(level.getBlockEntity(pos) instanceof FossilPedestalBlockEntity pedestal)) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        if (pedestal.bone() != null) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        if (!level.isClientSide()) {
            pedestal.setBone(bone);
            stack.consume(1, player);
            level.playSound(null, pos, FossilSounds.BONE_PLACE.get(), SoundSource.BLOCKS, 0.9F, 0.95F + level.getRandom().nextFloat() * 0.1F);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!(level.getBlockEntity(pos) instanceof FossilPedestalBlockEntity pedestal) || pedestal.bone() == null) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide()) {
            Bone bone = pedestal.bone();
            pedestal.setBone(null);
            Fossils.give(player, new ItemStack(UniverseItems.BONES.get(bone).get()));
            level.playSound(null, pos, FossilSounds.BONE_TAKE.get(), SoundSource.BLOCKS, 0.8F, 1.0F);
        }
        return InteractionResult.SUCCESS;
    }
}
