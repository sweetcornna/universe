package io.github.sweetcornna.universe.factory.belt;

import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.registry.UniverseBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** 分流器（ADR-0015）：四面接传送带的小方块，进出由传送带的朝向决定。 */
public class SplitterBlock extends BaseEntityBlock {
    private static final VoxelShape SHAPE = Block.box(1, 0, 1, 15, 8, 15);

    public SplitterBlock(Properties properties) {
        super(properties);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SplitterBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide() || type != UniverseBlockEntities.SPLITTER.get()) {
            return null;
        }
        return (tickLevel, pos, tickState, blockEntity) -> ((SplitterBlockEntity) blockEntity).serverTick();
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }
}
