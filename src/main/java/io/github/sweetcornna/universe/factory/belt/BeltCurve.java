package io.github.sweetcornna.universe.factory.belt;

import io.github.sweetcornna.universe.core.factory.BeltGraph;
import net.minecraft.util.StringRepresentable;

/** 平的传送带是不是弯道、从哪一侧拐进来（方块状态，只影响外观）；由相邻的传送带决定。 */
public enum BeltCurve implements StringRepresentable {
    NONE("none"),
    LEFT("left"),
    RIGHT("right");

    private final String name;

    BeltCurve(String name) {
        this.name = name;
    }

    public static BeltCurve of(BeltGraph.Side side) {
        return switch (side) {
            case LEFT -> LEFT;
            case RIGHT -> RIGHT;
            case NONE -> NONE;
        };
    }

    @Override
    public String getSerializedName() {
        return this.name;
    }
}
