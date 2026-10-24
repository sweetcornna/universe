package io.github.sweetcornna.universe.hazard;

import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.core.hazard.GeyserKind;
import io.github.sweetcornna.universe.registry.UniverseBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 间歇泉的喷口（ADR-0034 §六）：地面上一个结着矿壳的泉眼，按自己的节奏安静、咕嘟、喷发（{@link GeyserVentBlockEntity}）。
 * 四种照星球分：灼热、毒雾、冰冻、奇异。安静的时候泉眼也会冒一缕汽。
 */
public class GeyserVentBlock extends BaseEntityBlock {
    private final GeyserKind kind;

    public GeyserVentBlock(GeyserKind kind, Properties properties) {
        super(properties);
        this.kind = kind;
    }

    public GeyserKind kind() {
        return this.kind;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new GeyserVentBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide()
                ? createTickerHelper(type, UniverseBlockEntities.GEYSER_VENT.get(), GeyserVentBlockEntity::clientTick)
                : createTickerHelper(type, UniverseBlockEntities.GEYSER_VENT.get(), GeyserVentBlockEntity::serverTick);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        // 安静时泉眼上方偶尔一缕汽
        if (random.nextInt(3) == 0 && level.getBlockState(pos.above()).isAir()) {
            double x = pos.getX() + 0.35 + random.nextDouble() * 0.3;
            double z = pos.getZ() + 0.35 + random.nextDouble() * 0.3;
            level.addParticle(this.kind == GeyserKind.COLD ? ParticleTypes.SNOWFLAKE : ParticleTypes.WHITE_SMOKE, x, pos.getY() + 1.05, z,
                    0, 0.03 + random.nextDouble() * 0.02, 0);
        }
    }
}
