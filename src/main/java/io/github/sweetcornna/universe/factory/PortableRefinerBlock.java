package io.github.sweetcornna.universe.factory;

import io.github.sweetcornna.universe.registry.UniverseBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 便携精炼机（ADR-0026）：十二格见方的小箱子。拆下来时里面的原料、燃料、产物跟着物品走（战利品表复制容器组件），
 * 像潜影盒一样；创造模式里拆也掉出带东西的物品。
 */
public class PortableRefinerBlock extends MachineBlock {
    private static final VoxelShape SHAPE = Block.box(2, 0, 2, 14, 12, 14);

    public PortableRefinerBlock(Properties properties) {
        super(properties, UniverseBlockEntities.PORTABLE_REFINER);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide() && player.preventsBlockDrops() && level.getBlockEntity(pos) instanceof FuelRecipeMachineBlockEntity machine
                && !machine.isEmpty()) {
            ItemStack stack = new ItemStack(this);
            stack.applyComponents(machine.collectComponents());
            ItemEntity entity = new ItemEntity(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, stack);
            entity.setDefaultPickUpDelay();
            level.addFreshEntity(entity);
        }
        return super.playerWillDestroy(level, pos, state, player);
    }
}
