package io.github.sweetcornna.universe.core.stats;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.github.sweetcornna.universe.core.balance.Progression;

class PacingTest {
    private static final long HOUR = (long) Pacing.TICKS_PER_HOUR;

    @Test
    void everyModelMilestoneHasACheckpoint() {
        // 平衡模型的四个里程碑都要在节奏记录里对得上，目标时长一样
        for (Progression.Milestone milestone : Progression.MILESTONES) {
            String key = milestone.firstShell() ? "story/first_shell" : "tech/" + milestone.tech();
            Pacing.Checkpoint checkpoint = Pacing.CHECKPOINTS.stream().filter(c -> c.key().equals(key)).findFirst().orElseThrow();
            assertEquals(milestone.hours(), checkpoint.targetHours(), 1e-9, key);
        }
    }

    @Test
    void segmentsCompareWithTheModel() {
        // 蓝矩阵玩到 6 小时（模型 4），跃迁引擎玩到 11 小时（模型 10）：第一段 1.5 倍，第二段 5 对 6
        List<Pacing.Row> rows = Pacing.report(Map.of("tech/electromagnetic_matrix", 6 * HOUR, "tech/warp_drive", 11 * HOUR,
                "story/first_space", 8 * HOUR));
        Pacing.Row blue = row(rows, "tech/electromagnetic_matrix");
        assertEquals(6, blue.hours().getAsDouble(), 1e-9);
        assertEquals(1.5, blue.ratio().getAsDouble(), 1e-9);
        Pacing.Row warp = row(rows, "tech/warp_drive");
        assertEquals(5, warp.segmentHours().getAsDouble(), 1e-9);
        assertEquals(6, warp.targetSegmentHours().getAsDouble(), 1e-9);
        // 没有目标的节点只记时间，不算一段
        Pacing.Row space = row(rows, "story/first_space");
        assertEquals(8, space.hours().getAsDouble(), 1e-9);
        assertTrue(space.ratio().isEmpty());
    }

    @Test
    void missingCheckpointBreaksTheNextSegment() {
        // 没到蓝矩阵就研究了跃迁（比如用命令）：跃迁这一段没法算
        List<Pacing.Row> rows = Pacing.report(Map.of("tech/warp_drive", 11 * HOUR));
        assertFalse(row(rows, "tech/electromagnetic_matrix").hours().isPresent());
        assertTrue(row(rows, "tech/warp_drive").segmentHours().isEmpty());
        assertTrue(row(rows, "tech/warp_drive").targetSegmentHours().isPresent());
    }

    private static Pacing.Row row(List<Pacing.Row> rows, String key) {
        return rows.stream().filter(row -> row.checkpoint().key().equals(key)).findFirst().orElseThrow();
    }
}
