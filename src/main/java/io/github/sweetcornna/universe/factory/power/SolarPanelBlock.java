package io.github.sweetcornna.universe.factory.power;

import java.util.function.Supplier;

import io.github.sweetcornna.universe.factory.MachineBlock;
import io.github.sweetcornna.universe.factory.MachineBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** 太阳能板：底座和立柱上顶着一整格宽的板子，一排排摆开时可以在板子上走。 */
public class SolarPanelBlock extends MachineBlock {
    private static final VoxelShape SHAPE = Shapes.or(Block.box(5, 0, 5, 11, 2, 11), Block.box(7, 2, 7, 9, 12, 9), Block.box(0, 12, 0, 16, 13, 16));

    public SolarPanelBlock(Properties properties, Supplier<? extends BlockEntityType<? extends MachineBlockEntity>> type) {
        super(properties, type);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }
}
