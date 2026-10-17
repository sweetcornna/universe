package io.github.sweetcornna.universe.block;

import io.github.sweetcornna.universe.core.ecology.PlantForm;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.VegetationBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** 一种形态的地面植物（{@link PlantForm}），颜色按星球和变体号染。 */
public class PlanetPlantBlock extends VegetationBlock {
    private static final VoxelShape SHAPE = Block.column(12.0, 0.0, 13.0);
    private static final VoxelShape FLAT = Block.column(16.0, 0.0, 1.0);

    private final PlantForm form;

    public PlanetPlantBlock(PlantForm form, Properties properties) {
        super(properties);
        this.form = form;
        this.registerDefaultState(this.stateDefinition.any().setValue(FloraBlocks.VARIANT, 0).setValue(FloraBlocks.LIT, false));
    }

    public PlantForm form() {
        return this.form;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FloraBlocks.VARIANT, FloraBlocks.LIT);
    }

    @Override
    protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) {
        return state.is(FloraBlocks.PLANT_SOIL);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return this.form == PlantForm.LICHEN ? FLAT : SHAPE;
    }
}
