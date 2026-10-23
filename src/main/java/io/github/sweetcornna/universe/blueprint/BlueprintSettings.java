package io.github.sweetcornna.universe.blueprint;

import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.core.blueprint.BlueprintGeometry;
import net.minecraft.nbt.CompoundTag;

/**
 * 能被蓝图记下设置的方块实体（ADR-0018）：配方、过滤、物流站的供需、弹射器的轨道这类玩家调过的东西。库存和电量不算。
 *
 * <p>和方向有关的设置（分流器的优先出口）用 {@link #SIDE} 记一个水平方向，放蓝图时跟着蓝图一起转。
 */
public interface BlueprintSettings {
    /** 设置里记水平方向的键：north、east、south、west。 */
    String SIDE = "Side";

    /** 存进蓝图的设置；没什么可存的返回空。 */
    @Nullable CompoundTag blueprintSettings();

    /** 放蓝图时把设置用到刚放下的方块上。 */
    void applyBlueprintSettings(CompoundTag settings);

    /** 蓝图顺时针转了 quarterTurns 个 90° 以后的设置：{@link #SIDE} 跟着转，别的不变。不改原来的。 */
    static @Nullable CompoundTag rotated(@Nullable CompoundTag settings, int quarterTurns) {
        if (settings == null || Math.floorMod(quarterTurns, 4) == 0 || !settings.contains(SIDE)) {
            return settings;
        }
        CompoundTag copy = settings.copy();
        copy.putString(SIDE, BlueprintGeometry.rotateSide(settings.getStringOr(SIDE, ""), quarterTurns));
        return copy;
    }
}
