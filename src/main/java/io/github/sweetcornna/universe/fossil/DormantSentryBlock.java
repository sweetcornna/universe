package io.github.sweetcornna.universe.fossil;

import io.github.sweetcornna.universe.core.fossil.StoneSentryRules;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 睡着的石哨兵（ADR-0034 §三）：发掘场边上一块蹲着的大石头，石缝里透着一点光。旁边有人挖化石土，它一次比一次躁动
 * （{@link #UNREST} 0–2，石缝越来越亮，往下掉碎石）；到 {@value StoneSentryRules#WAKE_DIGS} 就醒过来，变成浮着的
 * {@link StoneSentry}。拿镐子去敲它、把它挖掉也会把它弄醒。本身不掉东西。
 */
public class DormantSentryBlock extends Block {
    public static final IntegerProperty UNREST = IntegerProperty.create("unrest", 0, StoneSentryRules.WAKE_DIGS - 1);
    private static final VoxelShape SHAPE = Shapes.or(Block.box(1, 0, 1, 15, 11, 15), Block.box(3, 11, 3, 13, 14, 13));

    public DormantSentryBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(UNREST, 0));
    }

    /** 石缝的亮度：睡着时只有一点，越躁动越亮。 */
    public static int light(BlockState state) {
        return 2 + state.getValue(UNREST) * 3;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(UNREST);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected void attack(BlockState state, Level level, BlockPos pos, Player player) {
        if (level instanceof ServerLevel server && !player.isCreative() && !player.isSpectator()) {
            DigSites.stir(server, pos, state, player);
        }
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        BlockState result = super.playerWillDestroy(level, pos, state, player);
        if (level instanceof ServerLevel server && !player.isCreative() && !player.isSpectator()) {
            DigSites.wake(server, pos, state, player);
        }
        return result;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        int unrest = state.getValue(UNREST);
        if (unrest == 0 || random.nextInt(4 - unrest) != 0) {
            return;
        }
        // 躁动的石哨兵往下掉碎石，石缝里冒一点火星
        double x = pos.getX() + 0.15 + random.nextDouble() * 0.7;
        double z = pos.getZ() + 0.15 + random.nextDouble() * 0.7;
        level.addParticle(new BlockParticleOption(ParticleTypes.FALLING_DUST, Blocks.GRAVEL.defaultBlockState()), x, pos.getY() + 0.7, z, 0, 0, 0);
        if (unrest == 2 && random.nextInt(3) == 0) {
            level.addParticle(ParticleTypes.SMALL_FLAME, x, pos.getY() + 0.3 + random.nextDouble() * 0.4, z, 0, 0.01, 0);
        }
    }
}
