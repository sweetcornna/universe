package io.github.sweetcornna.universe.darkfog.turret;

import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.core.darkfog.TurretKind;
import io.github.sweetcornna.universe.factory.MachineBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 炮塔的方块（ADR-0028 §五）：底座是方块模型，转台和炮管由 {@code TurretRenderer} 按目标转。客户端也走 tick，
 * 让炮塔头平滑地转过去。
 */
public class TurretBlock extends MachineBlock {
    private static final VoxelShape SHAPE = Shapes.or(Block.box(0, 0, 0, 16, 7, 16), Block.box(3, 7, 3, 13, 15, 13));

    private final TurretKind kind;
    private final Supplier<? extends BlockEntityType<TurretBlockEntity>> type;

    public TurretBlock(Properties properties, TurretKind kind, Supplier<? extends BlockEntityType<TurretBlockEntity>> type) {
        super(properties, type);
        this.kind = kind;
        this.type = type;
    }

    public TurretKind kind() {
        return this.kind;
    }

    BlockEntityType<TurretBlockEntity> blockEntityType() {
        return this.type.get();
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide() && type == this.type.get()) {
            return (tickLevel, pos, tickState, blockEntity) -> ((TurretBlockEntity) blockEntity).clientTick();
        }
        return super.getTicker(level, state, type);
    }
}
