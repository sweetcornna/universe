package io.github.sweetcornna.universe.block;

import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.core.ecology.PlacedFlora;
import io.github.sweetcornna.universe.core.ecology.TreeSpecies;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;

/** 只有变体号的星球植物方块：巨菌的菌盖、柱状肉茎。玩家放下时显示成所在星球上长这种方块的树种。 */
public class FloraVariantBlock extends Block {
    public FloraVariantBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FloraBlocks.VARIANT, 0));
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = super.getStateForPlacement(context);
        if (state == null) {
            return null;
        }
        BlockPos pos = context.getClickedPos();
        TreeSpecies tree = PlacedFlora.cap(FloraBlocks.flora(context.getLevel()), FloraBlocks.biotope(context.getLevel(), pos), FloraBlocks.hash(pos));
        return tree == null ? state : state.setValue(FloraBlocks.VARIANT, tree.index());
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FloraBlocks.VARIANT);
    }
}
