package io.github.sweetcornna.universe.dyson;

import io.github.sweetcornna.universe.factory.MachineBlock;
import io.github.sweetcornna.universe.factory.TallMachineBlock;
import io.github.sweetcornna.universe.registry.UniverseBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/** 戴森球的设备方块（ADR-0016）。转动的炮管、碟面、火箭由方块实体渲染器画。 */
public final class DysonBlocks {
    private DysonBlocks() {
    }

    /** 电磁轨道弹射器：方形底座上一个转台，炮管由渲染器画。 */
    public static class Ejector extends MachineBlock {
        private static final VoxelShape SHAPE = Shapes.or(Block.box(0, 0, 0, 16, 6, 16), Block.box(3, 6, 3, 13, 10, 13));

        public Ejector(Properties properties) {
            super(properties, UniverseBlockEntities.EM_RAIL_EJECTOR);
        }

        @Override
        protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
            return SHAPE;
        }
    }

    /** 射线接收站：底座和支柱，碟面由渲染器画，跟着恒星转。 */
    public static class Receiver extends MachineBlock {
        private static final VoxelShape SHAPE = Shapes.or(Block.box(1, 0, 1, 15, 5, 15), Block.box(5, 5, 5, 11, 10, 11));

        public Receiver(Properties properties) {
            super(properties, UniverseBlockEntities.RAY_RECEIVER);
        }

        @Override
        protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
            return SHAPE;
        }
    }

    /** 垂直发射井：两格高的井筒，上半截顶上是井口。 */
    public static class Silo extends TallMachineBlock {
        private static final VoxelShape LOWER = Block.box(0, 0, 0, 16, 16, 16);
        private static final VoxelShape UPPER = Shapes.or(Block.box(0, 0, 0, 16, 12, 16), Block.box(1, 12, 1, 15, 14, 15));

        public Silo(Properties properties) {
            super(properties, UniverseBlockEntities.VERTICAL_LAUNCHING_SILO);
        }

        @Override
        protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
            return state.getValue(HALF) == DoubleBlockHalf.UPPER ? UPPER : LOWER;
        }

        @Override
        public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
            // 客户端也走：发射时的尾焰和白汽
            if (level.isClientSide() && state.getValue(HALF) == DoubleBlockHalf.LOWER && type == UniverseBlockEntities.VERTICAL_LAUNCHING_SILO.get()) {
                return (tickLevel, pos, tickState, blockEntity) -> ((LaunchSiloBlockEntity) blockEntity).clientTick();
            }
            return super.getTicker(level, state, type);
        }

        @Override
        public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
            if (!state.getValue(WORKING) || state.getValue(HALF) != DoubleBlockHalf.UPPER || random.nextInt(3) != 0) {
                return;
            }
            // 井口冒出的白汽（加注推进剂）
            level.addParticle(ParticleTypes.CLOUD, pos.getX() + 0.3 + random.nextDouble() * 0.4, pos.getY() + 0.9,
                    pos.getZ() + 0.3 + random.nextDouble() * 0.4, 0, 0.03, 0);
        }
    }

    /** 微型粒子对撞机：工作时环里闪着紫光。 */
    public static class Collider extends MachineBlock {
        public Collider(Properties properties) {
            super(properties, UniverseBlockEntities.MINIATURE_PARTICLE_COLLIDER);
        }

        @Override
        public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
            if (!state.getValue(WORKING)) {
                return;
            }
            if (random.nextInt(2) == 0) {
                double angle = random.nextDouble() * Math.PI * 2;
                level.addParticle(ParticleTypes.ELECTRIC_SPARK, pos.getX() + 0.5 + Math.cos(angle) * 0.55, pos.getY() + 0.5 + random.nextDouble() * 0.3,
                        pos.getZ() + 0.5 + Math.sin(angle) * 0.55, 0, 0, 0);
            }
        }
    }
}
