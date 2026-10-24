package io.github.sweetcornna.universe.hazard;

import io.github.sweetcornna.universe.core.hazard.GeyserKind;
import net.minecraft.world.level.block.Block;

/**
 * 喷口边上的结壳（ADR-0034 §六）：泉水里析出的矿物一层层结在地上，挖开出东西（照星球分：硫、盐华、霜晶、光学晶体，
 * 见 {@link GeyserKind#crustDrop()}；掉落表由数据生成）。
 */
public class GeyserCrustBlock extends Block {
    private final GeyserKind kind;

    public GeyserCrustBlock(GeyserKind kind, Properties properties) {
        super(properties);
        this.kind = kind;
    }

    public GeyserKind kind() {
        return this.kind;
    }
}
