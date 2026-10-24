package io.github.sweetcornna.universe.deepspace;

import io.github.sweetcornna.universe.core.deepspace.Part;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.TransparentBlock;
import net.minecraft.world.level.block.state.BlockState;

/** 残骸的舷窗（ADR-0034 §四）：裂了的厚玻璃，看得见舱里。 */
public class DerelictWindowBlock extends TransparentBlock {
    public DerelictWindowBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
        Hulks.removed(level, pos, Part.WINDOW);
    }
}
