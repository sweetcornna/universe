package io.github.sweetcornna.universe.blueprint;

import org.jspecify.annotations.Nullable;

import net.minecraft.nbt.CompoundTag;

/**
 * 能被蓝图记下设置的方块实体（ADR-0018）：配方、过滤、物流站的供需、弹射器的轨道这类玩家调过的东西。库存和电量不算。
 */
public interface BlueprintSettings {
    /** 存进蓝图的设置；没什么可存的返回空。 */
    @Nullable CompoundTag blueprintSettings();

    /** 放蓝图时把设置用到刚放下的方块上。 */
    void applyBlueprintSettings(CompoundTag settings);
}
