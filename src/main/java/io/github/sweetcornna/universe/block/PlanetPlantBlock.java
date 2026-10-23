package io.github.sweetcornna.universe.block;

import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.core.ecology.PlacedFlora;
import io.github.sweetcornna.universe.core.ecology.PlantForm;
import io.github.sweetcornna.universe.core.ecology.PlantSpecies;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.VegetationBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** 一种形态的地面植物（{@link PlantForm}），颜色按星球和变体号染。玩家放下时显示成所在星球上这个形态的物种。 */
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
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = super.getStateForPlacement(context);
        if (state == null) {
            return null;
        }
        BlockPos pos = context.getClickedPos();
        PlantSpecies species = PlacedFlora.plant(FloraBlocks.flora(context.getLevel()), this.form,
                FloraBlocks.biotope(context.getLevel(), pos), FloraBlocks.hash(pos));
        if (species == null) {
            // 这颗星球上没有这种形态：默认的变体号，颜色按调色板
            return state;
        }
        return state.setValue(FloraBlocks.VARIANT, species.variant()).setValue(FloraBlocks.LIT, species.glowing());
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
