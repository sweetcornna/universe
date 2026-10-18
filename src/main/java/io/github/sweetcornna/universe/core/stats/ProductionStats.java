package io.github.sweetcornna.universe.core.stats;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.jspecify.annotations.Nullable;

/**
 * 生产统计（ADR-0015）：每个维度里每种物品的产出和消耗，以及电网的发电能力、用电需求和实际供上的电。
 *
 * <p>时间用游戏刻。最近一分钟按 10 秒一格记（6 格），最近一小时按 1 分钟一格记（60 格）；查询时把窗口里的格子加起来，
 * 按实际覆盖的时间折成每分钟（最新的一格还没满，刚开始记账时也不满一个窗口）。
 */
public final class ProductionStats {
    public static final int SHORT_TICKS = 200;
    public static final int SHORT_SLOTS = 6;
    public static final int LONG_TICKS = 1200;
    public static final int LONG_SLOTS = 60;

    /** 统计的时间范围。 */
    public enum Range {
        MINUTE(1), TEN_MINUTES(10), HOUR(60);

        private final int minutes;

        Range(int minutes) {
            this.minutes = minutes;
        }

        public int minutes() {
            return this.minutes;
        }
    }

    /** 一种物品每分钟的产出和消耗。 */
    public record Row(String item, double produced, double consumed) {
    }

    /**
     * 一份快照：物品按产出加消耗从多到少排，电力是每刻的平均值（焦耳）：发电能力、用电需求、充进蓄电器和物流站的电；
     * 满足率是实际供上的电除以需求（没有需求时为 1）。
     */
    public record Snapshot(List<Row> rows, double generation, double demand, double charging, double satisfaction) {
        public static final Snapshot EMPTY = new Snapshot(List.of(), 0, 0, 0, 1);
    }

    /** 一个量的时间序列：短格和长格两个环形数组，每格记着它是第几个 10 秒（第几分钟）。 */
    public static final class Series {
        private final long[] shortStamps = new long[SHORT_SLOTS];
        private final long[] shortValues = new long[SHORT_SLOTS];
        private final long[] longStamps = new long[LONG_SLOTS];
        private final long[] longValues = new long[LONG_SLOTS];

        public Series() {
            Arrays.fill(this.shortStamps, -1);
            Arrays.fill(this.longStamps, -1);
        }

        public void add(long amount, long now) {
            long shortStamp = now / SHORT_TICKS;
            int s = (int) (shortStamp % SHORT_SLOTS);
            if (this.shortStamps[s] != shortStamp) {
                this.shortStamps[s] = shortStamp;
                this.shortValues[s] = 0;
            }
            this.shortValues[s] += amount;
            long longStamp = now / LONG_TICKS;
            int l = (int) (longStamp % LONG_SLOTS);
            if (this.longStamps[l] != longStamp) {
                this.longStamps[l] = longStamp;
                this.longValues[l] = 0;
            }
            this.longValues[l] += amount;
        }

        /** 窗口里的总量。 */
        public long sum(Range range, long now) {
            long total = 0;
            if (range == Range.MINUTE) {
                long current = now / SHORT_TICKS;
                for (int i = 0; i < SHORT_SLOTS; i++) {
                    if (this.shortStamps[i] > current - SHORT_SLOTS && this.shortStamps[i] <= current) {
                        total += this.shortValues[i];
                    }
                }
            } else {
                long current = now / LONG_TICKS;
                for (int i = 0; i < LONG_SLOTS; i++) {
                    if (this.longStamps[i] > current - range.minutes() && this.longStamps[i] <= current) {
                        total += this.longValues[i];
                    }
                }
            }
            return total;
        }

        /** 一小时里一点都没有记过：可以扔掉了。 */
        boolean stale(long now) {
            long current = now / LONG_TICKS;
            for (long stamp : this.longStamps) {
                if (stamp > current - LONG_SLOTS) {
                    return false;
                }
            }
            return true;
        }

        // 存档用

        public long[] shortStamps() {
            return this.shortStamps.clone();
        }

        public long[] shortValues() {
            return this.shortValues.clone();
        }

        public long[] longStamps() {
            return this.longStamps.clone();
        }

        public long[] longValues() {
            return this.longValues.clone();
        }

        public static Series restore(long[] shortStamps, long[] shortValues, long[] longStamps, long[] longValues) {
            Series series = new Series();
            System.arraycopy(shortStamps, 0, series.shortStamps, 0, Math.min(SHORT_SLOTS, shortStamps.length));
            System.arraycopy(shortValues, 0, series.shortValues, 0, Math.min(SHORT_SLOTS, shortValues.length));
            System.arraycopy(longStamps, 0, series.longStamps, 0, Math.min(LONG_SLOTS, longStamps.length));
            System.arraycopy(longValues, 0, series.longValues, 0, Math.min(LONG_SLOTS, longValues.length));
            return series;
        }
    }

    /** 一个维度的账。 */
    public static final class Ledger {
        private final Map<String, Series> produced = new TreeMap<>();
        private final Map<String, Series> consumed = new TreeMap<>();
        private final Series generation;
        private final Series demand;
        private final Series supplied;
        private final Series charged;
        /** 开始记账的那一刻，刚开始时窗口按实际记过的时间算。 */
        private final long start;

        public Ledger(long start) {
            this(start, new Series(), new Series(), new Series(), new Series());
        }

        public Ledger(long start, Series generation, Series demand, Series supplied, Series charged) {
            this.start = start;
            this.generation = generation;
            this.demand = demand;
            this.supplied = supplied;
            this.charged = charged;
        }

        public long start() {
            return this.start;
        }

        public Map<String, Series> produced() {
            return this.produced;
        }

        public Map<String, Series> consumed() {
            return this.consumed;
        }

        public Series generation() {
            return this.generation;
        }

        public Series demand() {
            return this.demand;
        }

        public Series supplied() {
            return this.supplied;
        }

        public Series charged() {
            return this.charged;
        }

        /** 窗口实际覆盖了多少刻：最新一格没满，刚开始记账时也不满。 */
        long covered(Range range, long now) {
            long window = range == Range.MINUTE
                    ? (long) (SHORT_SLOTS - 1) * SHORT_TICKS + now % SHORT_TICKS + 1
                    : (long) (range.minutes() - 1) * LONG_TICKS + now % LONG_TICKS + 1;
            return Math.max(1, Math.min(window, now - this.start + 1));
        }
    }

    private final Map<String, Ledger> ledgers = new TreeMap<>();

    public void produced(String dimension, String item, long count, long now) {
        if (count > 0) {
            this.ledger(dimension, now).produced.computeIfAbsent(item, key -> new Series()).add(count, now);
        }
    }

    public void consumed(String dimension, String item, long count, long now) {
        if (count > 0) {
            this.ledger(dimension, now).consumed.computeIfAbsent(item, key -> new Series()).add(count, now);
        }
    }

    /** 记一段时间里电网的发电能力、用电需求、实际供上的电和充进蓄电器的电（焦耳）。 */
    public void power(String dimension, long generation, long demand, long supplied, long charged, long now) {
        if (generation <= 0 && demand <= 0 && charged <= 0 && !this.ledgers.containsKey(dimension)) {
            return;
        }
        Ledger ledger = this.ledger(dimension, now);
        ledger.generation.add(generation, now);
        ledger.demand.add(demand, now);
        ledger.supplied.add(supplied, now);
        ledger.charged.add(charged, now);
    }

    private Ledger ledger(String dimension, long now) {
        return this.ledgers.computeIfAbsent(dimension, key -> new Ledger(now));
    }

    /** 有账的维度。 */
    public Set<String> dimensions() {
        return Collections.unmodifiableSet(this.ledgers.keySet());
    }

    public Map<String, Ledger> ledgers() {
        return Collections.unmodifiableMap(this.ledgers);
    }

    public void restore(String dimension, Ledger ledger) {
        this.ledgers.put(dimension, ledger);
    }

    /** 扔掉一小时都没动静的物品和维度。 */
    public void prune(long now) {
        for (var it = this.ledgers.values().iterator(); it.hasNext();) {
            Ledger ledger = it.next();
            ledger.produced.values().removeIf(series -> series.stale(now));
            ledger.consumed.values().removeIf(series -> series.stale(now));
            if (ledger.produced.isEmpty() && ledger.consumed.isEmpty() && ledger.generation.stale(now) && ledger.demand.stale(now)) {
                it.remove();
            }
        }
    }

    /** 某个维度（{@code null} 是全部加起来）在某个时间范围里的快照。 */
    public Snapshot snapshot(@Nullable String dimension, Range range, long now) {
        List<Ledger> chosen = new ArrayList<>();
        if (dimension == null) {
            chosen.addAll(this.ledgers.values());
        } else if (this.ledgers.containsKey(dimension)) {
            chosen.add(this.ledgers.get(dimension));
        }
        if (chosen.isEmpty()) {
            return Snapshot.EMPTY;
        }
        Map<String, double[]> items = new HashMap<>();
        double generation = 0;
        double demand = 0;
        double supplied = 0;
        double charged = 0;
        for (Ledger ledger : chosen) {
            long covered = ledger.covered(range, now);
            double perMinute = (double) LONG_TICKS / covered;
            ledger.produced.forEach((item, series) -> items.computeIfAbsent(item, key -> new double[2])[0] += series.sum(range, now) * perMinute);
            ledger.consumed.forEach((item, series) -> items.computeIfAbsent(item, key -> new double[2])[1] += series.sum(range, now) * perMinute);
            generation += (double) ledger.generation.sum(range, now) / covered;
            demand += (double) ledger.demand.sum(range, now) / covered;
            supplied += (double) ledger.supplied.sum(range, now) / covered;
            charged += (double) ledger.charged.sum(range, now) / covered;
        }
        List<Row> rows = new ArrayList<>();
        items.forEach((item, amounts) -> {
            if (amounts[0] > 0 || amounts[1] > 0) {
                rows.add(new Row(item, amounts[0], amounts[1]));
            }
        });
        rows.sort(Comparator.comparingDouble((Row row) -> row.produced() + row.consumed()).reversed().thenComparing(Row::item));
        double satisfaction = demand > 0 ? Math.min(1, supplied / demand) : 1;
        return new Snapshot(List.copyOf(rows), generation, demand, charged, satisfaction);
    }
}
