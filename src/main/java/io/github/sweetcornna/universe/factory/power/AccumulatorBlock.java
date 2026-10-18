package io.github.sweetcornna.universe.factory.power;

import io.github.sweetcornna.universe.factory.MachineBlock;
import io.github.sweetcornna.universe.registry.UniverseBlockEntities;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/** 蓄电器：侧面有四格电量指示。 */
public class AccumulatorBlock extends MachineBlock {
    public static final int MAX_CHARGE = 4;
    public static final IntegerProperty CHARGE = IntegerProperty.create("charge", 0, MAX_CHARGE);

    public AccumulatorBlock(Properties properties) {
        super(properties, UniverseBlockEntities.ACCUMULATOR);
        this.registerDefaultState(this.defaultBlockState().setValue(CHARGE, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(CHARGE);
    }
}
