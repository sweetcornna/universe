package io.github.sweetcornna.universe.logistics;

import java.util.Optional;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/**
 * 记着主人的设备（物流站、轨道采集器、配送器，ADR-0033 §三）：控制面板远程改设置时认主人。玩家亲手放下、贴蓝图、黑雾打掉以后
 * 重建，都经这里记主人。
 */
public interface Owned {
    /** 主人；0.14 的档里没记的是空的，谁都能远程改。 */
    Optional<UUID> owner();

    /** 记成 {@code owner} 的；{@code null} 是没有主人（重建 0.14 留下的设备时照原样不记）。 */
    void setOwner(@Nullable UUID owner);
}
