package io.github.sweetcornna.universe.terraform;

import java.util.Optional;
import java.util.OptionalInt;

import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.core.galaxy.StarSystem;
import io.github.sweetcornna.universe.core.space.SpaceLayout;
import io.github.sweetcornna.universe.core.terraform.Terraform;
import io.github.sweetcornna.universe.factory.MachineBlock;
import io.github.sweetcornna.universe.registry.UniverseBlockEntities;
import io.github.sweetcornna.universe.world.planet.Planets;
import io.github.sweetcornna.universe.world.space.SpaceDimensions;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CarpetBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** 星球改造的方块（ADR-0036 §六）。设备的逻辑在方块实体里，这里管粒子、放的位置；还有播种站周围长出来的地衣。 */
public final class TerraformBlocks {
    private TerraformBlocks() {
    }

    /** 大气释放塔：工作时顶上的喷口往上冒一股股白气。 */
    public static class ReleaseTower extends MachineBlock {
        public ReleaseTower(Properties properties) {
            super(properties, UniverseBlockEntities.ATMOSPHERE_RELEASE_TOWER);
        }

        @Override
        public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
            if (!state.getValue(WORKING)) {
                return;
            }
            double x = pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.25;
            double z = pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.25;
            level.addParticle(ParticleTypes.CLOUD, x, pos.getY() + 1.05, z, 0, 0.12 + random.nextDouble() * 0.06, 0);
            if (random.nextInt(3) == 0) {
                level.addParticle(ParticleTypes.WHITE_SMOKE, x, pos.getY() + 1.4, z, 0, 0.08, 0);
            }
        }
    }

    /**
     * 轨道反射镜：只能放在太空里、能改造的星球的大气球外 {@value #RANGE} 格以内，对着最近的那颗。每面让星球的平均温度高
     * {@value Terraform#MIRROR_KELVIN} K；切成遮阳板就低这么多。拆掉就少一面。
     */
    public static class OrbitalMirror extends MachineBlock {
        /** 离星球大气球表面多远以内能放。 */
        public static final double RANGE = 200;
        /** 切成了遮阳板。 */
        public static final BooleanProperty SHADE = BooleanProperty.create("shade");
        /** 半格高的一块镜板，铺开来拼成一大片镜面。 */
        private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 8, 16);

        public OrbitalMirror(Properties properties) {
            super(properties, UniverseBlockEntities.ORBITAL_MIRROR);
            this.registerDefaultState(this.defaultBlockState().setValue(SHADE, false));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            super.createBlockStateDefinition(builder);
            builder.add(SHADE);
        }

        @Override
        protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
            return SHAPE;
        }

        @Override
        protected boolean useShapeForLightOcclusion(BlockState state) {
            return true;
        }

        @Override
        public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
            if (context.getLevel() instanceof ServerLevel level && target(level, context.getClickedPos()).isEmpty()) {
                if (context.getPlayer() != null) {
                    context.getPlayer().sendOverlayMessage(why(level, context.getClickedPos()));
                }
                return null;
            }
            return super.getStateForPlacement(context);
        }

        @Override
        public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
            // 镜面偶尔闪一下光
            if (state.getValue(WORKING) && !state.getValue(SHADE) && random.nextInt(12) == 0) {
                level.addParticle(ParticleTypes.END_ROD, pos.getX() + random.nextDouble(), pos.getY() + 0.52, pos.getZ() + random.nextDouble(),
                        0, 0.01, 0);
            }
        }

        /** 放不下时告诉玩家原因：不在太空里、这个星系没有能改造的星球，或者离最近的那颗还有多远、在哪个方向。 */
        private static Component why(ServerLevel level, BlockPos pos) {
            if (SpaceDimensions.system(level.dimension()).isEmpty()) {
                return Component.translatable("message.universe.orbital_mirror.not_in_space");
            }
            Optional<SpaceLayout.Body> nearest = nearest(level, pos);
            if (nearest.isEmpty()) {
                return Component.translatable("message.universe.orbital_mirror.no_planet");
            }
            SpaceLayout.Body body = nearest.get();
            double distance = body.distanceTo(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) - body.radius();
            String[] directions = {"east", "southeast", "south", "southwest", "west", "northwest", "north", "northeast"};
            double angle = Math.toDegrees(Math.atan2(body.z() - pos.getZ(), body.x() - pos.getX()));
            String direction = directions[Math.floorMod(Math.round((float) angle / 45F), 8)];
            return Component.translatable("message.universe.orbital_mirror.too_far", body.planet().name().zh(), body.planet().name().en(),
                    (int) Math.ceil(distance - RANGE), Component.translatable("direction.universe." + direction), (int) RANGE);
        }

        /** 对着的星球：大气球外 {@value #RANGE} 格以内、能改造的星球（有几颗时取最近的）。 */
        public static Optional<SpaceLayout.Body> target(ServerLevel level, BlockPos pos) {
            double x = pos.getX() + 0.5;
            double y = pos.getY() + 0.5;
            double z = pos.getZ() + 0.5;
            return nearest(level, pos).filter(body -> body.distanceTo(x, y, z) - body.radius() <= RANGE);
        }

        /** 这个星系里离得最近的能改造的星球（按到大气球的距离）。 */
        private static Optional<SpaceLayout.Body> nearest(ServerLevel level, BlockPos pos) {
            OptionalInt system = SpaceDimensions.system(level.dimension());
            if (system.isEmpty()) {
                return Optional.empty();
            }
            StarSystem star = Planets.galaxy(level.getServer()).systems().get(system.getAsInt());
            double x = pos.getX() + 0.5;
            double y = pos.getY() + 0.5;
            double z = pos.getZ() + 0.5;
            SpaceLayout.Body best = null;
            double bestDistance = Double.MAX_VALUE;
            for (SpaceLayout.Body body : SpaceLayout.of(star)) {
                double distance = body.distanceTo(x, y, z) - body.radius();
                if (Terraform.terraformable(body.planet()) && distance < bestDistance) {
                    bestDistance = distance;
                    best = body;
                }
            }
            return Optional.ofNullable(best);
        }
    }

    /** 生态播种站：浇水时顶上的喷头洒出细细的水雾，播种时冒几粒孢子。 */
    public static class EcologySeeder extends MachineBlock {
        public EcologySeeder(Properties properties) {
            super(properties, UniverseBlockEntities.ECOLOGY_SEEDER);
        }

        @Override
        public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
            if (!state.getValue(WORKING)) {
                return;
            }
            double x = pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.4;
            double z = pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.4;
            if (random.nextInt(2) == 0) {
                level.addParticle(ParticleTypes.SPLASH, x, pos.getY() + 1.05, z, (random.nextDouble() - 0.5) * 0.2, 0.15, (random.nextDouble() - 0.5) * 0.2);
            }
            if (random.nextInt(6) == 0) {
                level.addParticle(ParticleTypes.SPORE_BLOSSOM_AIR, x, pos.getY() + 1.2, z, 0, 0.02, 0);
            }
        }
    }

    /**
     * 壳状地衣：贴着岩石、土长的一层，不挡路，在上面放方块会把它盖掉，底下的方块没了就掉。挖了什么都不掉，
     * 要它就得让星球长出来。
     */
    public static class Lichen extends CarpetBlock {
        public Lichen(Properties properties) {
            super(properties);
        }

        @Override
        protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
            BlockPos below = pos.below();
            return level.getBlockState(below).isFaceSturdy(level, below, Direction.UP);
        }
    }
}
