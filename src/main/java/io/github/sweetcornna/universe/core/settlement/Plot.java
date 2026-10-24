package io.github.sweetcornna.universe.core.settlement;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

/**
 * 一块地基（ADR-0034 §二）：空着、在施工、等开张、开着，或者被海盗打坏了。
 *
 * <p>施工分三段，每段先交齐材料，再等 {@link SettlementRules#STAGE_WAIT} 刻；三段都完了去施工终端点开张。升级也是交材料再等，
 * 等的时候建筑照常起作用。打坏了交齐修理的材料马上修好，不用等。
 */
public final class Plot {
    /** 地基现在的样子。 */
    public enum Phase {
        /** 空地。 */
        EMPTY,
        /** 在交这一段（或者升级、修理）的材料。 */
        DELIVER,
        /** 材料交齐了，在等。 */
        WAIT,
        /** 三段都完了，等开张。 */
        OPENING,
        /** 开着。 */
        ACTIVE,
        /** 被打坏了，交材料修。 */
        DAMAGED
    }

    private @Nullable BuildingType type;
    /** 完成了几段（0–3）。 */
    private int stage;
    /** 档次（0–3：C、B、A、S）。 */
    private int grade;
    /** 在升级（交完材料、等完以后档次加一）。 */
    private boolean upgrading;
    /** 这一段等完的游戏刻；0 是没在等。 */
    private long readyAt;
    /** 这一段开始等的游戏刻（施工动画按它算进度）。 */
    private long waitStart;
    private boolean opened;
    private boolean damaged;
    /** 这一段已经交了多少：键是 {@link Cost#item()}。 */
    private final Map<String, Integer> delivered = new LinkedHashMap<>();

    public @Nullable BuildingType type() {
        return this.type;
    }

    public int stage() {
        return this.stage;
    }

    public int grade() {
        return this.grade;
    }

    public boolean upgrading() {
        return this.upgrading;
    }

    public long readyAt() {
        return this.readyAt;
    }

    public long waitStart() {
        return this.waitStart;
    }

    public boolean opened() {
        return this.opened;
    }

    public boolean damaged() {
        return this.damaged;
    }

    public Map<String, Integer> delivered() {
        return this.delivered;
    }

    public Phase phase() {
        if (this.type == null) {
            return Phase.EMPTY;
        }
        if (this.damaged) {
            return Phase.DAMAGED;
        }
        if (this.readyAt > 0 && this.stage < BuildingType.STAGES) {
            return Phase.WAIT;
        }
        if (this.stage < BuildingType.STAGES) {
            return Phase.DELIVER;
        }
        // 升级时照常开着：要交的材料看 costs()，在等看 readyAt()
        return this.opened ? Phase.ACTIVE : Phase.OPENING;
    }

    /** 建筑起作用：开着，没被打坏。升级的时候也起作用。 */
    public boolean active() {
        return this.type != null && this.stage >= BuildingType.STAGES && this.opened && !this.damaged;
    }

    /** 盖完了（三段都完了），开没开张都算：维护费按这个收。 */
    public boolean built() {
        return this.type != null && this.stage >= BuildingType.STAGES;
    }

    /** 现在要交的材料：施工的这一段、升级、修理；不用交是空表。 */
    public List<Cost> costs() {
        if (this.type == null || this.readyAt > 0) {
            return List.of();
        }
        if (this.damaged) {
            return BuildingType.repairCost();
        }
        if (this.stage < BuildingType.STAGES) {
            return this.type.stageCost(this.stage);
        }
        if (this.upgrading) {
            return BuildingType.upgradeCost(this.grade + 1);
        }
        return List.of();
    }

    /** 这一样还差几个。 */
    public int missing(Cost cost) {
        return Math.max(0, cost.count() - this.delivered.getOrDefault(cost.item(), 0));
    }

    /** 还差的总数。 */
    public int missing() {
        int missing = 0;
        for (Cost cost : this.costs()) {
            missing += this.missing(cost);
        }
        return missing;
    }

    /** 开工：空地上立起 {@code type} 的施工终端。 */
    public void start(BuildingType type) {
        if (this.type != null) {
            throw new IllegalStateException("plot taken");
        }
        this.type = type;
        this.stage = 0;
        this.grade = 0;
        this.opened = false;
        this.damaged = false;
        this.upgrading = false;
        this.readyAt = 0;
        this.delivered.clear();
    }

    /** 开始升级；已经是最高档、没开张、打坏了、在等都不行。 */
    public boolean startUpgrade() {
        if (!this.active() || this.upgrading || this.readyAt > 0 || this.grade >= BuildingType.GRADES - 1) {
            return false;
        }
        this.upgrading = true;
        this.delivered.clear();
        return true;
    }

    /** 取消：还没盖完的拆掉变回空地，在升级的停下（交了的材料不退）。返回原来在施工。 */
    public boolean cancel() {
        if (this.type == null) {
            return false;
        }
        if (this.stage < BuildingType.STAGES) {
            this.clear();
            return true;
        }
        if (this.upgrading) {
            this.upgrading = false;
            this.readyAt = 0;
            this.delivered.clear();
            return true;
        }
        return false;
    }

    /** 拆掉，变回空地。 */
    public void clear() {
        this.type = null;
        this.stage = 0;
        this.grade = 0;
        this.opened = false;
        this.damaged = false;
        this.upgrading = false;
        this.readyAt = 0;
        this.waitStart = 0;
        this.delivered.clear();
    }

    /**
     * 交 {@code count} 个 {@code cost} 那一样，最多交到够；返回实际收下几个。交齐以后：修理马上修好，施工、升级开始等。
     */
    public int deliver(Cost cost, int count, long now) {
        int taken = Math.min(count, this.missing(cost));
        if (taken <= 0) {
            return 0;
        }
        this.delivered.merge(cost.item(), taken, Integer::sum);
        if (this.missing() == 0) {
            this.delivered.clear();
            if (this.damaged) {
                this.damaged = false;
            } else {
                this.waitStart = now;
                this.readyAt = now + SettlementRules.STAGE_WAIT;
            }
        }
        return taken;
    }

    /** 到时间了就完成这一段（或者升级）；返回完成了什么，没有是 null。 */
    public @Nullable Finished tick(long now) {
        if (this.readyAt <= 0 || now < this.readyAt) {
            return null;
        }
        this.readyAt = 0;
        this.waitStart = 0;
        if (this.stage < BuildingType.STAGES) {
            this.stage++;
            return this.stage >= BuildingType.STAGES ? Finished.BUILT : Finished.STAGE;
        }
        if (this.upgrading) {
            this.upgrading = false;
            this.grade++;
            return Finished.UPGRADED;
        }
        return null;
    }

    /** 跳过等待（调试用）。 */
    public void skipWait(long now) {
        if (this.readyAt > now) {
            this.readyAt = now;
        }
    }

    /** 开张；三段没盖完、已经开过都不行。 */
    public boolean open() {
        if (this.type == null || this.stage < BuildingType.STAGES || this.opened) {
            return false;
        }
        this.opened = true;
        return true;
    }

    /** 被海盗打坏；开着的建筑才会被打坏。 */
    public boolean damage() {
        if (!this.active()) {
            return false;
        }
        this.damaged = true;
        this.upgrading = false;
        this.readyAt = 0;
        this.delivered.clear();
        return true;
    }

    /** 这一段等了多少（0–1），施工动画用。 */
    public double progress(long now) {
        if (this.readyAt <= 0) {
            return 0;
        }
        long span = Math.max(1, this.readyAt - this.waitStart);
        return Math.clamp((now - this.waitStart) / (double) span, 0, 1);
    }

    /** 读档用。 */
    public void restore(@Nullable BuildingType type, int stage, int grade, boolean upgrading, long readyAt, long waitStart, boolean opened,
            boolean damaged, Map<String, Integer> delivered) {
        this.type = type;
        this.stage = Math.clamp(stage, 0, BuildingType.STAGES);
        this.grade = Math.clamp(grade, 0, BuildingType.GRADES - 1);
        this.upgrading = upgrading;
        this.readyAt = readyAt;
        this.waitStart = waitStart;
        this.opened = opened;
        this.damaged = damaged;
        this.delivered.clear();
        this.delivered.putAll(delivered);
    }

    /** {@link #tick} 完成了什么。 */
    public enum Finished {
        STAGE,
        BUILT,
        UPGRADED
    }
}
