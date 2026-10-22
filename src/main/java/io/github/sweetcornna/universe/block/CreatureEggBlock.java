package io.github.sweetcornna.universe.block;

import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.core.ecology.Incubation;
import io.github.sweetcornna.universe.registry.UniverseBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 放在地上的生物卵（ADR-0026 §九）：5 分钟孵出幼崽，蛋壳先后裂两道。右键看孵了多少，挖掉还是原来那个卵（进度不留）。
 * 要放在实心的方块上。
 */
public class CreatureEggBlock extends BaseEntityBlock {
    public static final IntegerProperty HATCH = BlockStateProperties.HATCH;
    private static final VoxelShape SHAPE = Block.column(8.0, 0.0, 10.0);

    public CreatureEggBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(HATCH, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(HATCH);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return canSupportCenter(level, pos.below(), Direction.UP);
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction directionToNeighbour,
            BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
        return directionToNeighbour == Direction.DOWN && !this.canSurvive(state, level, pos) ? Blocks.AIR.defaultBlockState()
                : super.updateShape(state, level, ticks, pos, directionToNeighbour, neighbourPos, neighbourState, random);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CreatureEggBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() ? null : createTickerHelper(type, UniverseBlockEntities.CREATURE_EGG.get(), CreatureEggBlockEntity::serverTick);
    }

    /** 母亲没有主人的卵（野生动物的、命令给的），孵出来归放下它的人。 */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity by, ItemStack stack) {
        if (!level.isClientSide() && by instanceof Player player && level.getBlockEntity(pos) instanceof CreatureEggBlockEntity egg) {
            egg.claim(player.getUUID());
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (level.isClientSide() || !(level.getBlockEntity(pos) instanceof CreatureEggBlockEntity egg)) {
            return InteractionResult.SUCCESS;
        }
        Component message;
        if (egg.egg() == null) {
            message = Component.translatable("message.universe.creature_egg.infertile");
        } else {
            message = Component.translatable("message.universe.creature_egg.incubating", egg.egg().speciesName(),
                    Math.round(Incubation.progress(egg.ticks()) * 100));
        }
        player.sendOverlayMessage(message);
        return InteractionResult.SUCCESS;
    }
}
