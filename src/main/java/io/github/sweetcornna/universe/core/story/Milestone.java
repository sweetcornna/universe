package io.github.sweetcornna.universe.core.story;

import java.util.Optional;

/**
 * 剧情的节点（ADR-0018）：整个世界第一次做到时，探测器 UNV-7 发来一条日志；"任务完成"是结局。ID 存进存档，不要改。
 */
public enum Milestone {
    /** 第一次进入太空。 */
    FIRST_SPACE("first_space"),
    /** 第一次跃迁到别的恒星系。 */
    FIRST_WARP("first_warp"),
    /** 第一片铺满的戴森球壳。 */
    FIRST_SHELL("first_shell"),
    /** 第一个宇宙矩阵。 */
    FIRST_UNIVERSE_MATRIX("first_universe_matrix"),
    /** 研究完"任务完成"。 */
    MISSION_COMPLETE("mission_complete");

    private final String id;

    Milestone(String id) {
        this.id = id;
    }

    public String id() {
        return this.id;
    }

    /** 到了这个节点时探测器发不发日志（任务完成放结局画面）。 */
    public boolean hasLog() {
        return this != MISSION_COMPLETE;
    }

    public static Optional<Milestone> byId(String id) {
        for (Milestone milestone : values()) {
            if (milestone.id.equals(id)) {
                return Optional.of(milestone);
            }
        }
        return Optional.empty();
    }
}
