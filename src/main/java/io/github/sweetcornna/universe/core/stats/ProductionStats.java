package io.github.sweetcornna.universe.core.stats;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
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
 *
 * <p>M18（ADR-0033 §四）加了：星际物流运进运出的物品（按对面是哪个维度记）、各种发电设备和用电设备的电，几个维度合起来看
 * （一个星系），以及一种物品的曲线。
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

    /** 一种物品每分钟的产出和消耗，以及星际物流从别处运进来、运出去的。 */
    public record Row(String item, double produced, double consumed, double imported, double exported) {
        public Row(String item, double produced, double consumed) {
            this(item, produced, consumed, 0, 0);
        }
    }

    /** 一种设备（方块 ID）每刻平均的发电或用电（焦耳）。 */
    public record Part(String kind, double power) {
    }

    /**
     * 一份快照：物品按产出加消耗从多到少排，电力是每刻的平均值（焦耳）：发电能力、用电需求、充进蓄电器和物流站的电；
     * 满足率是实际供上的电除以需求（没有需求时为 1）。发电和用电还按设备的种类分开，从多到少排。
     */
    public record Snapshot(List<Row> rows, double generation, double demand, double charging, double satisfaction,
            List<Part> generators, List<Part> consumers) {
        public static final Snapshot EMPTY = new Snapshot(List.of(), 0, 0, 0, 1);

        public Snapshot(List<Row> rows, double generation, double demand, double charging, double satisfaction) {
            this(rows, generation, demand, charging, satisfaction, List.of(), List.of());
        }
    }

    /**
     * 一条曲线：从旧到新每格的值。物品是每分钟多少个，电是每刻平均多少焦耳（produced 是发电能力，consumed 是需求）。
     * 最近一分钟 6 格、每格 10 秒，十分钟 10 格、一小时 60 格，每格 1 分钟。最新的一格按已经过去的时间折算。
     */
    public record History(int bucketTicks, double[] produced, double[] consumed) {
    }

    /** 和对面一个维度之间每分钟运进来、运出去多少。 */
    public record Trade(String partner, double imported, double exported) {
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

        /** 第 stamp 个短格（10 秒）或长格（1 分钟）里的量；那一格已经被新的覆盖了或者没记过时是 0。 */
        long at(boolean shortSlot, long stamp) {
            if (stamp < 0) {
                return 0;
            }
            if (shortSlot) {
                int i = (int) (stamp % SHORT_SLOTS);
                return this.shortStamps[i] == stamp ? this.shortValues[i] : 0;
            }
            int i = (int) (stamp % LONG_SLOTS);
            return this.longStamps[i] == stamp ? this.longValues[i] : 0;
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
        /** 运进来的，键是 {@link #tradeKey}（物品和从哪个维度来）；运出去的键是物品和运到哪个维度。 */
        private final Map<String, Series> imports = new TreeMap<>();
        private final Map<String, Series> exports = new TreeMap<>();
        /** 各种发电设备的发电能力、各种用电设备的需求（焦耳），键是方块 ID。 */
        private final Map<String, Series> generators = new TreeMap<>();
        private final Map<String, Series> consumers = new TreeMap<>();
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

        public Map<String, Series> imports() {
            return this.imports;
        }

        public Map<String, Series> exports() {
            return this.exports;
        }

        public Map<String, Series> generators() {
            return this.generators;
        }

        public Map<String, Series> consumers() {
            return this.consumers;
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

    /** 星际运输船把货从 from 运到了 to：to 记一笔进口，from 记一笔出口。同一个维度里的不算。 */
    public void shipped(String from, String to, String item, long count, long now) {
        if (count <= 0 || from.equals(to)) {
            return;
        }
        this.ledger(to, now).imports.computeIfAbsent(tradeKey(item, from), key -> new Series()).add(count, now);
        this.ledger(from, now).exports.computeIfAbsent(tradeKey(item, to), key -> new Series()).add(count, now);
    }

    /** 记一段时间里一种设备的发电能力和用电需求（焦耳）。 */
    public void device(String dimension, String kind, long generation, long demand, long now) {
        if (generation <= 0 && demand <= 0) {
            return;
        }
        Ledger ledger = this.ledger(dimension, now);
        if (generation > 0) {
            ledger.generators.computeIfAbsent(kind, key -> new Series()).add(generation, now);
        }
        if (demand > 0) {
            ledger.consumers.computeIfAbsent(kind, key -> new Series()).add(demand, now);
        }
    }

    public static String tradeKey(String item, String partner) {
        return item + "|" + partner;
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
            ledger.imports.values().removeIf(series -> series.stale(now));
            ledger.exports.values().removeIf(series -> series.stale(now));
            ledger.generators.values().removeIf(series -> series.stale(now));
            ledger.consumers.values().removeIf(series -> series.stale(now));
            if (ledger.produced.isEmpty() && ledger.consumed.isEmpty() && ledger.imports.isEmpty() && ledger.exports.isEmpty()
                    && ledger.generation.stale(now) && ledger.demand.stale(now)) {
                it.remove();
            }
        }
    }

    /** 某个维度（{@code null} 是全部加起来）在某个时间范围里的快照。 */
    public Snapshot snapshot(@Nullable String dimension, Range range, long now) {
        return this.snapshot(dimension == null ? null : List.of(dimension), range, now);
    }

    /**
     * 几个维度（一个星系；{@code null} 是全部）合起来的快照。进出口只算和这几个维度以外的往来，全部合起来看时没有进出口。
     */
    public Snapshot snapshot(@Nullable Collection<String> dimensions, Range range, long now) {
        List<Ledger> chosen = this.chosen(dimensions);
        if (chosen.isEmpty()) {
            return Snapshot.EMPTY;
        }
        Map<String, double[]> items = new HashMap<>();
        Map<String, double[]> generators = new HashMap<>();
        Map<String, double[]> consumers = new HashMap<>();
        double generation = 0;
        double demand = 0;
        double supplied = 0;
        double charged = 0;
        for (Ledger ledger : chosen) {
            long covered = ledger.covered(range, now);
            double perMinute = (double) LONG_TICKS / covered;
            ledger.produced.forEach((item, series) -> items.computeIfAbsent(item, key -> new double[4])[0] += series.sum(range, now) * perMinute);
            ledger.consumed.forEach((item, series) -> items.computeIfAbsent(item, key -> new double[4])[1] += series.sum(range, now) * perMinute);
            ledger.imports.forEach((key, series) -> {
                if (external(key, dimensions)) {
                    items.computeIfAbsent(tradeItem(key), k -> new double[4])[2] += series.sum(range, now) * perMinute;
                }
            });
            ledger.exports.forEach((key, series) -> {
                if (external(key, dimensions)) {
                    items.computeIfAbsent(tradeItem(key), k -> new double[4])[3] += series.sum(range, now) * perMinute;
                }
            });
            ledger.generators.forEach((kind, series) -> generators.computeIfAbsent(kind, k -> new double[1])[0] += (double) series.sum(range, now) / covered);
            ledger.consumers.forEach((kind, series) -> consumers.computeIfAbsent(kind, k -> new double[1])[0] += (double) series.sum(range, now) / covered);
            generation += (double) ledger.generation.sum(range, now) / covered;
            demand += (double) ledger.demand.sum(range, now) / covered;
            supplied += (double) ledger.supplied.sum(range, now) / covered;
            charged += (double) ledger.charged.sum(range, now) / covered;
        }
        List<Row> rows = new ArrayList<>();
        items.forEach((item, amounts) -> {
            if (amounts[0] > 0 || amounts[1] > 0 || amounts[2] > 0 || amounts[3] > 0) {
                rows.add(new Row(item, amounts[0], amounts[1], amounts[2], amounts[3]));
            }
        });
        rows.sort(Comparator.comparingDouble((Row row) -> row.produced() + row.consumed() + row.imported() + row.exported()).reversed()
                .thenComparing(Row::item));
        double satisfaction = demand > 0 ? Math.min(1, supplied / demand) : 1;
        return new Snapshot(List.copyOf(rows), generation, demand, charged, satisfaction, parts(generators), parts(consumers));
    }

    /** 一种物品在几个维度（{@code null} 是全部）合起来每分钟的产出、消耗和进出口（仪表盘用，不用算整份快照）。 */
    public Row row(@Nullable Collection<String> dimensions, String item, Range range, long now) {
        double[] amounts = new double[4];
        String prefix = item + "|";
        for (Ledger ledger : this.chosen(dimensions)) {
            double perMinute = (double) LONG_TICKS / ledger.covered(range, now);
            Series produced = ledger.produced.get(item);
            Series consumed = ledger.consumed.get(item);
            amounts[0] += produced == null ? 0 : produced.sum(range, now) * perMinute;
            amounts[1] += consumed == null ? 0 : consumed.sum(range, now) * perMinute;
            ledger.imports.forEach((key, series) -> {
                if (key.startsWith(prefix) && external(key, dimensions)) {
                    amounts[2] += series.sum(range, now) * perMinute;
                }
            });
            ledger.exports.forEach((key, series) -> {
                if (key.startsWith(prefix) && external(key, dimensions)) {
                    amounts[3] += series.sum(range, now) * perMinute;
                }
            });
        }
        return new Row(item, amounts[0], amounts[1], amounts[2], amounts[3]);
    }

    /** 一种物品在几个维度（{@code null} 是全部）合起来的曲线。 */
    public History history(@Nullable Collection<String> dimensions, String item, Range range, long now) {
        List<Ledger> chosen = this.chosen(dimensions);
        List<@Nullable Series> produced = new ArrayList<>();
        List<@Nullable Series> consumed = new ArrayList<>();
        for (Ledger ledger : chosen) {
            produced.add(ledger.produced.get(item));
            consumed.add(ledger.consumed.get(item));
        }
        return history(range, now, produced, consumed, true);
    }

    /** 几个维度合起来的电：发电能力和需求，每刻平均多少焦耳。 */
    public History powerHistory(@Nullable Collection<String> dimensions, Range range, long now) {
        List<Ledger> chosen = this.chosen(dimensions);
        List<@Nullable Series> generation = new ArrayList<>();
        List<@Nullable Series> demand = new ArrayList<>();
        for (Ledger ledger : chosen) {
            generation.add(ledger.generation);
            demand.add(ledger.demand);
        }
        return history(range, now, generation, demand, false);
    }

    /** 一种物品和这几个维度以外的各个维度之间每分钟的往来，按往来的量从多到少排。 */
    public List<Trade> trade(@Nullable Collection<String> dimensions, String item, Range range, long now) {
        Map<String, double[]> partners = new TreeMap<>();
        String prefix = item + "|";
        for (Ledger ledger : this.chosen(dimensions)) {
            double perMinute = (double) LONG_TICKS / ledger.covered(range, now);
            ledger.imports.forEach((key, series) -> {
                if (key.startsWith(prefix) && external(key, dimensions)) {
                    partners.computeIfAbsent(key.substring(prefix.length()), k -> new double[2])[0] += series.sum(range, now) * perMinute;
                }
            });
            ledger.exports.forEach((key, series) -> {
                if (key.startsWith(prefix) && external(key, dimensions)) {
                    partners.computeIfAbsent(key.substring(prefix.length()), k -> new double[2])[1] += series.sum(range, now) * perMinute;
                }
            });
        }
        List<Trade> trades = new ArrayList<>();
        partners.forEach((partner, amounts) -> {
            if (amounts[0] > 0 || amounts[1] > 0) {
                trades.add(new Trade(partner, amounts[0], amounts[1]));
            }
        });
        trades.sort(Comparator.comparingDouble((Trade trade) -> trade.imported() + trade.exported()).reversed().thenComparing(Trade::partner));
        return List.copyOf(trades);
    }

    private List<Ledger> chosen(@Nullable Collection<String> dimensions) {
        if (dimensions == null) {
            return List.copyOf(this.ledgers.values());
        }
        List<Ledger> chosen = new ArrayList<>();
        for (String dimension : dimensions) {
            Ledger ledger = this.ledgers.get(dimension);
            if (ledger != null && !chosen.contains(ledger)) {
                chosen.add(ledger);
            }
        }
        return chosen;
    }

    /** 往来的对面不在这几个维度里；全部合起来看时没有外面。 */
    private static boolean external(String tradeKey, @Nullable Collection<String> dimensions) {
        return dimensions != null && !dimensions.contains(tradeKey.substring(tradeKey.indexOf('|') + 1));
    }

    private static String tradeItem(String tradeKey) {
        return tradeKey.substring(0, tradeKey.indexOf('|'));
    }

    private static List<Part> parts(Map<String, double[]> kinds) {
        List<Part> parts = new ArrayList<>();
        kinds.forEach((kind, power) -> {
            if (power[0] > 0) {
                parts.add(new Part(kind, power[0]));
            }
        });
        parts.sort(Comparator.comparingDouble(Part::power).reversed().thenComparing(Part::kind));
        return List.copyOf(parts);
    }

    /** 把几条序列按格加起来。perMinute 为真时折成每分钟多少（物品），否则是每刻平均多少（电）。 */
    private static History history(Range range, long now, List<@Nullable Series> first, List<@Nullable Series> second, boolean perMinute) {
        boolean shortSlot = range == Range.MINUTE;
        int bucket = shortSlot ? SHORT_TICKS : LONG_TICKS;
        int count = shortSlot ? SHORT_SLOTS : range.minutes();
        long current = now / bucket;
        double[] a = new double[count];
        double[] b = new double[count];
        for (int i = 0; i < count; i++) {
            long stamp = current - (count - 1 - i);
            long ticks = i == count - 1 ? now % bucket + 1 : bucket;
            double scale = perMinute ? (double) LONG_TICKS / ticks : 1.0 / ticks;
            long sumA = 0;
            long sumB = 0;
            for (Series series : first) {
                sumA += series == null ? 0 : series.at(shortSlot, stamp);
            }
            for (Series series : second) {
                sumB += series == null ? 0 : series.at(shortSlot, stamp);
            }
            a[i] = sumA * scale;
            b[i] = sumB * scale;
        }
        return new History(bucket, a, b);
    }
}
