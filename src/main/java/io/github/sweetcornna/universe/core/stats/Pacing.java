package io.github.sweetcornna.universe.core.stats;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;

import io.github.sweetcornna.universe.core.balance.Progression;

/**
 * 节奏记录（ADR-0039）：一局里几个关键节点是在玩到第几个小时到的，和平衡模型（{@link Progression#MILESTONES}）定的目标放在一起比。
 * 测试玩家照常玩，不用另外记时间；玩完 {@code /universe pacing} 就能看出哪一段比模型慢、哪一段快。
 *
 * <p>时间用主世界的游戏刻：单人游戏暂停时不走，算的是真在玩的时间。一小时 72000 刻。
 */
public final class Pacing {
    /** 一小时多少刻。 */
    public static final double TICKS_PER_HOUR = 72_000;

    /**
     * 一个节点。
     *
     * @param key 记录里的键：{@code tech/<科技>} 是研究完这个科技，{@code story/<节点>} 是剧情节点
     * @param targetHours 平衡模型的目标时长（小时）；模型里没有的为 {@code NaN}
     */
    public record Checkpoint(String key, String nameZh, String nameEn, double targetHours) {
        public boolean hasTarget() {
            return !Double.isNaN(this.targetHours);
        }
    }

    /** 一行报告：这个节点到没到、第几个小时到的；和上一个有目标的节点比，这一段用了多久、模型要多久。 */
    public record Row(Checkpoint checkpoint, OptionalDouble hours, OptionalDouble segmentHours, OptionalDouble targetSegmentHours) {
        /** 这一段实际用的时间是模型的几倍；有一头缺数就空着。 */
        public OptionalDouble ratio() {
            if (this.segmentHours.isEmpty() || this.targetSegmentHours.isEmpty() || this.targetSegmentHours.getAsDouble() <= 0) {
                return OptionalDouble.empty();
            }
            return OptionalDouble.of(this.segmentHours.getAsDouble() / this.targetSegmentHours.getAsDouble());
        }
    }

    /** 按成长线排的节点。目标时长从平衡模型的里程碑来，第一片球壳对应模型里"研究完垂直发射井、再铺满一片球壳"。 */
    public static final List<Checkpoint> CHECKPOINTS = List.of(
            new Checkpoint("tech/electromagnetism", "电磁学", "Electromagnetism", Double.NaN),
            new Checkpoint("tech/electromagnetic_matrix", "蓝矩阵", "Electromagnetic matrix", target("electromagnetic_matrix")),
            new Checkpoint("story/first_space", "第一次进太空", "First trip to space", Double.NaN),
            new Checkpoint("tech/warp_drive", "跃迁引擎", "Warp drive", target("warp_drive")),
            new Checkpoint("story/first_warp", "第一次跃迁", "First warp", Double.NaN),
            new Checkpoint("story/first_shell", "第一片球壳", "First Dyson shell", target("vertical_launching_silo")),
            new Checkpoint("story/first_universe_matrix", "第一个宇宙矩阵", "First universe matrix", Double.NaN),
            new Checkpoint("tech/mission_complete", "任务完成", "Mission complete", target("mission_complete")));

    private Pacing() {
    }

    /** 平衡模型里这个科技的目标时长。 */
    private static double target(String tech) {
        return Progression.MILESTONES.stream().filter(milestone -> milestone.tech().equals(tech)).mapToDouble(Progression.Milestone::hours)
                .findFirst().orElse(Double.NaN);
    }

    /**
     * 报告：每个节点一行。只有有目标的节点算"一段"：从上一个有目标的节点（开局算 0）到这一个，实际几个小时、模型几个小时。
     *
     * @param reached 记录：节点的键 → 到的时候（游戏刻）
     */
    public static List<Row> report(Map<String, Long> reached) {
        List<Row> rows = new ArrayList<>();
        double lastTarget = 0;
        OptionalDouble lastHours = OptionalDouble.of(0);
        for (Checkpoint checkpoint : CHECKPOINTS) {
            Long ticks = reached.get(checkpoint.key());
            OptionalDouble hours = ticks == null ? OptionalDouble.empty() : OptionalDouble.of(ticks / TICKS_PER_HOUR);
            if (!checkpoint.hasTarget()) {
                rows.add(new Row(checkpoint, hours, OptionalDouble.empty(), OptionalDouble.empty()));
                continue;
            }
            OptionalDouble segment = hours.isPresent() && lastHours.isPresent()
                    ? OptionalDouble.of(hours.getAsDouble() - lastHours.getAsDouble()) : OptionalDouble.empty();
            rows.add(new Row(checkpoint, hours, segment, OptionalDouble.of(checkpoint.targetHours() - lastTarget)));
            lastTarget = checkpoint.targetHours();
            lastHours = hours;
        }
        return rows;
    }
}
