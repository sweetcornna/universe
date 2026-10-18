package io.github.sweetcornna.universe.factory.belt;

import io.github.sweetcornna.universe.core.factory.BeltGraph;
import net.minecraft.util.StringRepresentable;

/** 传送带的坡度（方块状态），对应核心层的 {@link BeltGraph.Slope}。 */
public enum BeltSlope implements StringRepresentable {
    FLAT("flat", BeltGraph.Slope.FLAT),
    UP("up", BeltGraph.Slope.UP),
    DOWN("down", BeltGraph.Slope.DOWN);

    private final String name;
    private final BeltGraph.Slope core;

    BeltSlope(String name, BeltGraph.Slope core) {
        this.name = name;
        this.core = core;
    }

    public BeltGraph.Slope core() {
        return this.core;
    }

    /** 潜行右键时换下一种：平 → 上坡 → 下坡 → 平。 */
    public BeltSlope next() {
        return values()[(this.ordinal() + 1) % values().length];
    }

    @Override
    public String getSerializedName() {
        return this.name;
    }
}
