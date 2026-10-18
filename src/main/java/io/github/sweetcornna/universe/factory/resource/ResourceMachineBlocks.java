package io.github.sweetcornna.universe.factory.resource;

import java.util.function.Supplier;

import io.github.sweetcornna.universe.factory.MachineBlock;
import io.github.sweetcornna.universe.factory.MachineBlockEntity;
import io.github.sweetcornna.universe.factory.TallMachineBlock;
import io.github.sweetcornna.universe.registry.UniverseBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/** 采集和精炼设备的方块：只多了工作时的粒子和声音。 */
public final class ResourceMachineBlocks {
    private ResourceMachineBlocks() {
    }

    /** 采矿机：工作时底下扬起矿石的碎屑。 */
    public static class MiningMachine extends MachineBlock {
        public MiningMachine(Properties properties) {
            super(properties, UniverseBlockEntities.MINING_MACHINE);
        }

        protected MiningMachine(Properties properties, Supplier<? extends BlockEntityType<? extends MachineBlockEntity>> type) {
            super(properties, type);
        }

        @Override
        public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
            if (!state.getValue(WORKING)) {
                return;
            }
            BlockState below = level.getBlockState(pos.below());
            if (!below.isAir() && random.nextInt(2) == 0) {
                Direction side = Direction.Plane.HORIZONTAL.getRandomDirection(random);
                double along = (random.nextDouble() - 0.5) * 0.9;
                level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK, below),
                        pos.getX() + 0.5 + side.getStepX() * 0.55 + side.getStepZ() * along, pos.getY() + 0.05,
                        pos.getZ() + 0.5 + side.getStepZ() * 0.55 + side.getStepX() * along, 0, 0.1, 0);
            }
            if (random.nextInt(25) == 0) {
                level.playLocalSound(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, below.getSoundType().getHitSound(), SoundSource.BLOCKS,
                        0.5F, 0.6F, false);
            }
        }
    }

    /** 高级采矿机（ADR-0015）。 */
    public static class AdvancedMiningMachine extends MiningMachine {
        public AdvancedMiningMachine(Properties properties) {
            super(properties, UniverseBlockEntities.ADVANCED_MINING_MACHINE);
        }
    }

    /** 抽水站：工作时进水口溅起水花。 */
    public static class WaterPump extends MachineBlock {
        public WaterPump(Properties properties) {
            super(properties, UniverseBlockEntities.WATER_PUMP);
        }

        @Override
        public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
            if (state.getValue(WORKING) && random.nextInt(2) == 0) {
                level.addParticle(ParticleTypes.SPLASH, pos.getX() + random.nextDouble(), pos.getY() + 0.02, pos.getZ() + random.nextDouble(),
                        0, 0.05, 0);
            }
        }
    }

    /** 原油萃取站：工作时抽油机咔哒作响，排气口冒烟。 */
    public static class OilExtractor extends TallMachineBlock {
        public OilExtractor(Properties properties) {
            super(properties, UniverseBlockEntities.OIL_EXTRACTOR);
        }

        @Override
        public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
            if (!state.getValue(WORKING) || state.getValue(HALF) != DoubleBlockHalf.LOWER) {
                return;
            }
            if (random.nextInt(4) == 0) {
                level.addParticle(ParticleTypes.SMOKE, pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.6, pos.getY() + 0.8,
                        pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.6, 0, 0.03, 0);
            }
            if (random.nextInt(20) == 0) {
                level.playLocalSound(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, SoundEvents.PISTON_EXTEND, SoundSource.BLOCKS,
                        0.15F, 0.5F, false);
            }
        }
    }

    /** 原油精炼厂：工作时分馏塔顶上的火炬冒烟。 */
    public static class OilRefinery extends TallMachineBlock {
        public OilRefinery(Properties properties) {
            super(properties, UniverseBlockEntities.OIL_REFINERY);
        }

        @Override
        public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
            if (!state.getValue(WORKING) || state.getValue(HALF) != DoubleBlockHalf.UPPER) {
                return;
            }
            // 高的那座塔在背面靠左（模型里 x 2–7、z 9–14，朝北时）
            double[] offset = facingOffset(state.getValue(FACING), 4.5 / 16 - 0.5, 11.5 / 16 - 0.5);
            double x = pos.getX() + 0.5 + offset[0];
            double z = pos.getZ() + 0.5 + offset[1];
            if (random.nextInt(2) == 0) {
                level.addParticle(ParticleTypes.SMALL_FLAME, x, pos.getY() + 1.05, z, 0, 0.02, 0);
            }
            if (random.nextInt(3) == 0) {
                level.addAlwaysVisibleParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, true, x, pos.getY() + 1.1, z, 0, 0.05, 0);
            }
        }
    }

    /** 把朝北时的水平偏移（x 向东、z 向南）转到实际朝向。 */
    static double[] facingOffset(Direction facing, double x, double z) {
        return switch (facing) {
            case SOUTH -> new double[] {-x, -z};
            case WEST -> new double[] {z, -x};
            case EAST -> new double[] {-z, x};
            default -> new double[] {x, z};
        };
    }
}
