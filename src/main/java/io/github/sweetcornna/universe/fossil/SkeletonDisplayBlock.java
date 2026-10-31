package io.github.sweetcornna.universe.fossil;

import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.advancement.MilestoneTrigger;
import io.github.sweetcornna.universe.core.fossil.Bone;
import io.github.sweetcornna.universe.core.fossil.Skeleton;
import io.github.sweetcornna.universe.registry.sound.FossilSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 骨架展台（ADR-0034 §三）：一块矮的石台，正面一块铜铭牌。上面按五个部位拼出一副立体的骨架，头朝着放下时人面对的方向。
 *
 * <ul>
 *   <li>空手右键打开界面：五格放骨头、封装成化石展品。</li>
 *   <li>拿着骨头右键：这个部位空着就直接放上去。</li>
 *   <li>拿着化石展品对空的展台右键：把展品里的骨架摆回来。</li>
 * </ul>
 */
public class SkeletonDisplayBlock extends BaseEntityBlock {
    public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;
    /** 台面的高度（像素）。 */
    public static final int TOP = 5;
    private static final VoxelShape SHAPE = Shapes.or(Block.box(0, 0, 0, 16, 3, 16), Block.box(1, 3, 1, 15, TOP, 15));

    public SkeletonDisplayBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        // 骨架的头朝着放的人
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SkeletonDisplayBlockEntity(pos, state);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
            BlockHitResult hitResult) {
        if (!(level.getBlockEntity(pos) instanceof SkeletonDisplayBlockEntity display)) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        if (stack.getItem() instanceof FossilExhibitItem) {
            Skeleton skeleton = FossilExhibitItem.skeleton(stack);
            if (skeleton.isEmpty()) {
                return InteractionResult.TRY_WITH_EMPTY_HAND;
            }
            if (!display.skeleton().isEmpty()) {
                if (!level.isClientSide()) {
                    player.sendOverlayMessage(Component.translatable("message.universe.skeleton_display.occupied").withStyle(ChatFormatting.YELLOW));
                }
                return InteractionResult.FAIL;
            }
            if (!level.isClientSide()) {
                display.setSkeleton(skeleton);
                stack.consume(1, player);
                level.playSound(null, pos, FossilSounds.UNPACK.get(), SoundSource.BLOCKS, 0.9F, 1.0F);
            }
            return InteractionResult.SUCCESS;
        }
        Bone bone = BoneItem.boneOf(stack);
        if (bone == null || display.skeleton().has(bone.part())) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        if (!level.isClientSide()) {
            display.insert(bone);
            if (display.skeleton().complete() && player instanceof ServerPlayer serverPlayer) {
                MilestoneTrigger.reach(serverPlayer, MilestoneTrigger.SKELETON);
            }
            stack.consume(1, player);
            level.playSound(null, pos, FossilSounds.BONE_PLACE.get(), SoundSource.BLOCKS, 0.9F, 0.95F + level.getRandom().nextFloat() * 0.1F);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (player instanceof ServerPlayer serverPlayer && level.getBlockEntity(pos) instanceof SkeletonDisplayBlockEntity display) {
            open(serverPlayer, display);
        }
        return InteractionResult.SUCCESS;
    }

    /** 打开展台的界面。 */
    public static void open(ServerPlayer player, SkeletonDisplayBlockEntity display) {
        player.openMenu(new SimpleMenuProvider((id, inventory, p) -> new SkeletonDisplayMenu(id, inventory, display),
                display.getBlockState().getBlock().getName()), buf -> buf.writeBlockPos(display.getBlockPos()));
    }
}
