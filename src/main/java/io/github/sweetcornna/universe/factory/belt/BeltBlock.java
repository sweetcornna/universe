package io.github.sweetcornna.universe.factory.belt;

import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.core.factory.BeltGraph;
import io.github.sweetcornna.universe.core.factory.BeltTier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Prediction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
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
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 传送带（ADR-0011）：朝着玩家面对的方向运送物品。平的、上坡、下坡三种（潜行空手右键切换），平的传送带会根据相邻的
 * 传送带自动显示成弯道。空手右键拿走这格上的东西，拿着物品右键往上放一个；站在上面的生物和掉在上面的物品会被带走。
 */
public class BeltBlock extends BaseEntityBlock {
    public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;
    public static final EnumProperty<BeltSlope> SLOPE = EnumProperty.create("slope", BeltSlope.class);
    public static final EnumProperty<BeltCurve> CURVE = EnumProperty.create("curve", BeltCurve.class);

    private static final VoxelShape FLAT_SHAPE = Block.box(0, 0, 0, 16, 4, 16);
    // 坡道的碰撞箱是四级台阶（朝北时，北边是前面）
    private static final Map<Direction, VoxelShape> UP_SHAPES = Shapes.rotateHorizontal(Shapes.or(
            Block.box(0, 0, 12, 16, 4, 16), Block.box(0, 0, 8, 16, 8, 12), Block.box(0, 0, 4, 16, 12, 8), Block.box(0, 0, 0, 16, 16, 4)));
    private static final Map<Direction, VoxelShape> DOWN_SHAPES = Shapes.rotateHorizontal(Shapes.or(
            Block.box(0, 0, 12, 16, 16, 16), Block.box(0, 0, 8, 16, 12, 12), Block.box(0, 0, 4, 16, 8, 8), Block.box(0, 0, 0, 16, 4, 4)));

    private final BeltTier tier;

    public BeltBlock(Properties properties, BeltTier tier) {
        super(properties);
        this.tier = tier;
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(SLOPE, BeltSlope.FLAT)
                .setValue(CURVE, BeltCurve.NONE));
    }

    public BeltTier tier() {
        return this.tier;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, SLOPE, CURVE);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = this.defaultBlockState().setValue(FACING, context.getHorizontalDirection());
        return state.setValue(CURVE, curve(context.getLevel(), context.getClickedPos(), state));
    }

    /** 按相邻的传送带算弯道（服务端的线路重建之后还会再校正一次）。 */
    static BeltCurve curve(LevelReader level, BlockPos pos, BlockState state) {
        StateNode self = new StateNode(pos.getX(), pos.getY(), pos.getZ(), state);
        return BeltCurve.of(BeltGraph.curve(self, (x, y, z) -> {
            BlockPos at = new BlockPos(x, y, z);
            if (!level.hasChunkAt(at)) {
                return null;
            }
            BlockState other = level.getBlockState(at);
            return other.getBlock() instanceof BeltBlock ? new StateNode(x, y, z, other) : null;
        }));
    }

    /** 方块状态包成核心层的传送带节点。 */
    private record StateNode(int x, int y, int z, BlockState state) implements BeltGraph.Node {
        @Override
        public int facing() {
            return this.state.getValue(FACING).get2DDataValue();
        }

        @Override
        public BeltGraph.Slope slope() {
            return this.state.getValue(SLOPE).core();
        }

        @Override
        public BeltTier tier() {
            return this.state.getBlock() instanceof BeltBlock belt ? belt.tier : BeltTier.MK1;
        }
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BeltBlockEntity(pos, state);
    }

    // ---- 形状

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(SLOPE)) {
            case FLAT -> FLAT_SHAPE;
            case UP -> UP_SHAPES.get(state.getValue(FACING));
            case DOWN -> DOWN_SHAPES.get(state.getValue(FACING));
        };
    }

    // ---- 交互

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
            BlockHitResult hitResult) {
        // 空手也会走到这里（物品是空的），交给 useWithoutItem 拿东西或改坡度
        if (stack.isEmpty()) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        // 拿着传送带照常放置；带组件的物品（工具、附魔书……）放不上去
        if (stack.getItem() instanceof BlockItem blockItem && blockItem.getBlock() instanceof BeltBlock || !BeltNetwork.canCarry(stack)) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide() && BeltNetwork.of(level).insertAt(pos, BeltNetwork.HALF, BeltNetwork.id(stack.getItem()))) {
            stack.consume(1, player);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (player.isShiftKeyDown()) {
            if (!level.isClientSide()) {
                level.setBlock(pos, state.setValue(SLOPE, state.getValue(SLOPE).next()).setValue(CURVE, BeltCurve.NONE), Block.UPDATE_ALL);
            }
            return InteractionResult.SUCCESS;
        }
        if (!level.isClientSide()) {
            List<Item> items = BeltNetwork.of(level).takeAll(pos);
            for (Item item : items) {
                player.getInventory().placeItemBackInInventory(new ItemStack(item), Prediction.SERVER_ONLY);
            }
        }
        return InteractionResult.SUCCESS;
    }

    /** 掉在上面的物品一个一个放上传送带；站在上面的生物顺着传送带走（潜行时不动）。 */
    @Override
    public void stepOn(Level level, BlockPos pos, BlockState state, Entity entity) {
        super.stepOn(level, pos, state, entity);
        if (entity instanceof ItemEntity itemEntity) {
            if (!level.isClientSide()) {
                ItemStack stack = itemEntity.getItem();
                if (BeltNetwork.canCarry(stack) && BeltNetwork.of(level).insertAt(pos, BeltNetwork.HALF, BeltNetwork.id(stack.getItem()))) {
                    ItemStack rest = stack.copy();
                    rest.shrink(1);
                    if (rest.isEmpty()) {
                        itemEntity.discard();
                    } else {
                        itemEntity.setItem(rest);
                    }
                }
            }
            return;
        }
        if (entity.isSteppingCarefully()) {
            return;
        }
        Direction facing = state.getValue(FACING);
        double speed = this.tier.blocksPerSecond() / 20;
        Vec3 motion = entity.getDeltaMovement();
        double along = motion.x * facing.getStepX() + motion.z * facing.getStepZ();
        if (along < speed) {
            double push = Math.min(speed - along, speed * 0.5);
            entity.setDeltaMovement(motion.add(facing.getStepX() * push, 0, facing.getStepZ() * push));
        }
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }
}
