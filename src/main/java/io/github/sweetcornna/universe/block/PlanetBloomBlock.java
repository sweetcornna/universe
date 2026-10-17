package io.github.sweetcornna.universe.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;

/** 树上的花果，有的物种会发光。 */
public class PlanetBloomBlock extends Block {
    public PlanetBloomBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FloraBlocks.VARIANT, 0).setValue(FloraBlocks.LIT, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FloraBlocks.VARIANT, FloraBlocks.LIT);
    }
}
