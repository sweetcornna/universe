package io.github.sweetcornna.universe.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;

/** 只有变体号的星球植物方块：巨菌的菌盖、柱状肉茎。 */
public class FloraVariantBlock extends Block {
    public FloraVariantBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FloraBlocks.VARIANT, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FloraBlocks.VARIANT);
    }
}
