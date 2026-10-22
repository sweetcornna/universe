package io.github.sweetcornna.universe.block.portal;

import io.github.sweetcornna.universe.core.portal.PortalAddress;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/** 先驱者传送门石环上刻着符文的石块（ADR-0024）：六块连起来就是这座门的地址。挖不掉。 */
public class PortalGlyphBlock extends Block {
    public static final IntegerProperty GLYPH = IntegerProperty.create("glyph", 0, PortalAddress.GLYPHS - 1);

    public PortalGlyphBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(GLYPH, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(GLYPH);
    }
}
