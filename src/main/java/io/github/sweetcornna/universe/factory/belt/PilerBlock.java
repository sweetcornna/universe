package io.github.sweetcornna.universe.factory.belt;

import io.github.sweetcornna.universe.registry.UniverseBlockEntities;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.Shapes;

/** 自动集装机（ADR-0022）：串在传送带中间，不用设置。 */
public class PilerBlock extends BeltPassBlock {
    public PilerBlock(Properties properties) {
        super(properties, UniverseBlockEntities.AUTOMATIC_PILER, Shapes.or(Block.box(0, 0, 0, 16, 9, 16), Block.box(1, 9, 1, 15, 14, 15)));
    }
}
