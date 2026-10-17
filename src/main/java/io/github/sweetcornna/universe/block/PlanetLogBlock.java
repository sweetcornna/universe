package io.github.sweetcornna.universe.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;

/** 星球树木的树干，颜色按星球上第几种树（变体号）染。 */
public class PlanetLogBlock extends RotatedPillarBlock {
    public PlanetLogBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.defaultBlockState().setValue(FloraBlocks.VARIANT, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(FloraBlocks.VARIANT);
    }
}
