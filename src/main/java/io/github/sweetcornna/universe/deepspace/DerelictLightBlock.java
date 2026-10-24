package io.github.sweetcornna.universe.deepspace;

import io.github.sweetcornna.universe.core.deepspace.Part;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * 舱里的灯（ADR-0034 §四）：残骸上还亮着昏黄的应急灯；结构完整度掉到 30 以下变成闪红光的警报灯。
 * 前哨站没电，灯是黑的。
 */
public class DerelictLightBlock extends DerelictBlock {
    public static final BooleanProperty LIT = BlockStateProperties.LIT;
    public static final BooleanProperty ALARM = BooleanProperty.create("alarm");
    private static final DustParticleOptions RED = new DustParticleOptions(0xFF2A1A, 0.9F);

    public DerelictLightBlock(Properties properties) {
        super(Part.LIGHT, properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(LIT, true).setValue(ALARM, false));
    }

    /** 亮度：警报灯 11，应急灯 6，没电 0。 */
    public static int light(BlockState state) {
        return state.getValue(ALARM) ? 11 : state.getValue(LIT) ? 6 : 0;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LIT, ALARM);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (state.getValue(ALARM) && random.nextInt(3) == 0) {
            level.addParticle(RED, pos.getX() + random.nextDouble(), pos.getY() + random.nextDouble(), pos.getZ() + random.nextDouble(), 0, 0, 0);
        }
    }
}
