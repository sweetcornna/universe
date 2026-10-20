package io.github.sweetcornna.universe.core.perf;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 分项计时的累计（ADR-0019）：每一项累计了多少纳秒、调用了几次，服务器一共走了几刻、每刻花了多少纳秒。
 * 报告按平均每刻的耗时从大到小排。各项之间不嵌套，加起来就是本模组每刻的耗时。
 */
public final class PerfStats {
    private final Map<String, long[]> sections = new LinkedHashMap<>();
    private long ticks;
    private long serverNanos;

    /** 记一次：这一项花了 nanos 纳秒。 */
    public void add(String section, long nanos) {
        long[] entry = this.sections.computeIfAbsent(section, key -> new long[2]);
        entry[0] += nanos;
        entry[1]++;
    }

    /** 服务器走完一刻，这一刻一共花了 nanos 纳秒。 */
    public void tick(long nanos) {
        this.ticks++;
        this.serverNanos += nanos;
    }

    public long ticks() {
        return this.ticks;
    }

    /** 服务器平均每刻多少毫秒。 */
    public double serverMsPerTick() {
        return this.ticks == 0 ? 0 : this.serverNanos / 1e6 / this.ticks;
    }

    /** 本模组各项加起来平均每刻多少毫秒。 */
    public double totalMsPerTick() {
        if (this.ticks == 0) {
            return 0;
        }
        long total = 0;
        for (long[] entry : this.sections.values()) {
            total += entry[0];
        }
        return total / 1e6 / this.ticks;
    }

    /**
     * 报告的一行。
     *
     * @param msPerTick    平均每刻多少毫秒
     * @param callsPerTick 平均每刻调用几次
     * @param share        占服务器每刻耗时的比例
     */
    public record Line(String section, double msPerTick, double callsPerTick, double share) {
    }

    public List<Line> report() {
        List<Line> lines = new ArrayList<>();
        if (this.ticks == 0) {
            return lines;
        }
        for (Map.Entry<String, long[]> entry : this.sections.entrySet()) {
            long[] value = entry.getValue();
            lines.add(new Line(entry.getKey(), value[0] / 1e6 / this.ticks, value[1] / (double) this.ticks,
                    this.serverNanos == 0 ? 0 : value[0] / (double) this.serverNanos));
        }
        lines.sort(Comparator.comparingDouble(Line::msPerTick).reversed());
        return lines;
    }
}
