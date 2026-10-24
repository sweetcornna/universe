package io.github.sweetcornna.universe.core.stats;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.jspecify.annotations.Nullable;

/**
 * 参考产量（ADR-0033 §四）：所有在用某个配方、正在运转的设备满速时每分钟做多少。设备运转时每秒报一次自己的配方和满速时
 * 每分钟做几次，两秒多没报就当它停了。
 */
public final class ReferenceRates {
    /** 设备每隔多少刻报一次。 */
    public static final int REPORT_TICKS = 20;
    /** 多久没报就当停了。 */
    public static final int EXPIRE_TICKS = REPORT_TICKS * 2 + 5;

    /** 一种物品满速时每分钟做出、用掉多少，以及有几台设备在做、在用。 */
    public record Rate(double produced, double consumed, int producers, int consumers) {
        public static final Rate NONE = new Rate(0, 0, 0, 0);
    }

    private static final class Entry {
        String recipe = "";
        double crafts;
        double outputFactor;
        long time;
    }

    private final Map<String, Map<Long, Entry>> devices = new HashMap<>();

    /** 这一刻该不该报：每台设备错开，每 {@link #REPORT_TICKS} 刻报一次。 */
    public static boolean due(long now, long position) {
        return Math.floorMod(now + Long.hashCode(position * 0x9E3779B97F4A7C15L), REPORT_TICKS) == 0;
    }

    /**
     * 一台设备在运转。
     *
     * @param craftsPerMinute 满速（电满、速度和增产剂的加速都算上）时每分钟做几次
     * @param outputFactor 产物的倍数（增产剂的额外产出是 1 加上额外的比例，没有是 1）
     */
    public void report(String dimension, long position, String recipe, double craftsPerMinute, double outputFactor, long now) {
        Entry entry = this.devices.computeIfAbsent(dimension, key -> new HashMap<>()).computeIfAbsent(position, key -> new Entry());
        entry.recipe = recipe;
        entry.crafts = craftsPerMinute;
        entry.outputFactor = outputFactor;
        entry.time = now;
    }

    public void remove(String dimension, long position) {
        Map<Long, Entry> entries = this.devices.get(dimension);
        if (entries != null) {
            entries.remove(position);
        }
    }

    /** 一种物品在几个维度（{@code null} 是全部）里的参考产量。 */
    public Rate rate(RecipeGraph graph, @Nullable Collection<String> dimensions, String item, long now) {
        double produced = 0;
        double consumed = 0;
        int producers = 0;
        int consumers = 0;
        for (Map.Entry<String, Map<Long, Entry>> dimension : this.devices.entrySet()) {
            if (dimensions != null && !dimensions.contains(dimension.getKey())) {
                continue;
            }
            for (Entry entry : dimension.getValue().values()) {
                if (now - entry.time > EXPIRE_TICKS) {
                    continue;
                }
                RecipeGraph.Recipe recipe = graph.get(entry.recipe);
                if (recipe == null) {
                    continue;
                }
                int out = recipe.produces(item);
                if (out > 0) {
                    produced += entry.crafts * out * entry.outputFactor;
                    producers++;
                }
                int in = recipe.consumes(item);
                if (in > 0) {
                    consumed += entry.crafts * in;
                    consumers++;
                }
            }
        }
        return new Rate(produced, consumed, producers, consumers);
    }

    /** 这台设备在报哪个配方；没报或者已经停了时为空。 */
    public @Nullable String recipeAt(String dimension, long position, long now) {
        Map<Long, Entry> entries = this.devices.get(dimension);
        Entry entry = entries == null ? null : entries.get(position);
        return entry == null || now - entry.time > EXPIRE_TICKS ? null : entry.recipe;
    }

    /** 扔掉停了的设备。 */
    public void prune(long now) {
        for (var it = this.devices.values().iterator(); it.hasNext();) {
            Map<Long, Entry> entries = it.next();
            entries.values().removeIf(entry -> now - entry.time > EXPIRE_TICKS);
            if (entries.isEmpty()) {
                it.remove();
            }
        }
    }

    /** 记着的设备有几台（包括还没扔掉的停了的）。 */
    public int size() {
        int size = 0;
        for (Map<Long, Entry> entries : this.devices.values()) {
            size += entries.size();
        }
        return size;
    }

    public void clear() {
        this.devices.clear();
    }
}
