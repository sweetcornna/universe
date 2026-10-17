package io.github.sweetcornna.universe.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.sounds.AmbientLeavesBlockSoundPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;

/**
 * 星球树木的树叶。生成的树叶都是永久的（{@code persistent}）：伞形树、垂枝树的叶子离树干可能超过原版的 6 格，
 * 按原版规则会自己凋落。
 */
public class PlanetLeavesBlock extends LeavesBlock {
    public PlanetLeavesBlock(Properties properties) {
        super(AmbientLeavesBlockSoundPlayer.noAmbientSound(), properties);
        this.registerDefaultState(this.defaultBlockState().setValue(FloraBlocks.VARIANT, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(FloraBlocks.VARIANT);
    }
}
