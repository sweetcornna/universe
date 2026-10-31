package io.github.sweetcornna.universe.chem.reactor;

import io.github.sweetcornna.universe.factory.MachineBlock;
import io.github.sweetcornna.universe.registry.UniverseBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/** 化学反应釜的方块（ADR-0036 §四）。逻辑在方块实体里，这里只管工作时顶上安全阀偶尔泄一口白汽。 */
public class ChemicalReactorBlock extends MachineBlock {
    public ChemicalReactorBlock(Properties properties) {
        super(properties, UniverseBlockEntities.CHEMICAL_REACTOR);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(WORKING) || random.nextInt(8) != 0) {
            return;
        }
        level.addParticle(ParticleTypes.WHITE_SMOKE, pos.getX() + 0.72, pos.getY() + 1.05, pos.getZ() + 0.28,
                (random.nextDouble() - 0.5) * 0.02, 0.05, (random.nextDouble() - 0.5) * 0.02);
    }
}
