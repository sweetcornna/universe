package io.github.sweetcornna.universe.logistics;

import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.factory.MachineBlock;
import io.github.sweetcornna.universe.registry.UniverseBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** 物流配送器（ADR-0023）：底座、立柱，顶上一块整格宽的停机坪，机器人从坪上起落。 */
public class DistributorBlock extends MachineBlock {
    private static final VoxelShape SHAPE = Shapes.or(Block.box(1, 0, 1, 15, 4, 15), Block.box(4, 4, 4, 12, 10, 12),
            Block.box(0, 10, 0, 16, 12, 16));

    public DistributorBlock(Properties properties) {
        super(properties, UniverseBlockEntities.LOGISTICS_DISTRIBUTOR);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    /** 放下它的玩家是主人，控制面板远程改模式时认他（ADR-0033 §三）。 */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity by, ItemStack stack) {
        super.setPlacedBy(level, pos, state, by, stack);
        if (!level.isClientSide() && by instanceof Player player && level.getBlockEntity(pos) instanceof DistributorBlockEntity distributor) {
            distributor.setOwner(player.getUUID());
        }
    }
}
