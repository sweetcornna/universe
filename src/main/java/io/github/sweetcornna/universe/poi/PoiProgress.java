package io.github.sweetcornna.universe.poi;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * 玩家在兴趣点上的进度（ADR-0014）：去过哪些兴趣点（信号增强器跳过它们），读过哪些终端、碰过哪些石碑（奖励只给一次），
 * 先驱者的线索讲到第几段。挂在玩家身上，死亡后保留。
 */
public final class PoiProgress {
    private static final Codec<Set<Long>> LONG_SET = Codec.LONG.listOf().xmap(HashSet::new, ArrayList::new);
    public static final MapCodec<PoiProgress> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            LONG_SET.optionalFieldOf("visited", Set.of()).forGetter(p -> p.visited),
            LONG_SET.optionalFieldOf("claimed", Set.of()).forGetter(p -> p.claimed),
            Codec.INT.optionalFieldOf("lore", 0).forGetter(p -> p.lore),
            Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("monoliths", Map.of()).forGetter(PoiProgress::monolithsByName),
            Codec.INT.optionalFieldOf("responses", 0).forGetter(p -> p.responses)
    ).apply(i, PoiProgress::new));

    private final Set<Long> visited;
    private final Set<Long> claimed;
    private int lore;
    /** 碰过的石碑 → 它讲的是第几段线索（再碰一次时重新显示）。 */
    private final Map<Long, Integer> monoliths;
    /** 先驱者的回应听了几段（ADR-0018）。 */
    private int responses;

    public PoiProgress() {
        this(Set.of(), Set.of(), 0, Map.of(), 0);
    }

    private PoiProgress(Set<Long> visited, Set<Long> claimed, int lore, Map<String, Integer> monoliths, int responses) {
        this.visited = new HashSet<>(visited);
        this.claimed = new HashSet<>(claimed);
        this.lore = lore;
        this.responses = responses;
        this.monoliths = new HashMap<>();
        monoliths.forEach((key, index) -> {
            try {
                this.monoliths.put(Long.parseLong(key), index);
            } catch (NumberFormatException ignored) {
                // 坏掉的条目跳过
            }
        });
    }

    private Map<String, Integer> monolithsByName() {
        Map<String, Integer> map = new HashMap<>();
        this.monoliths.forEach((key, index) -> map.put(Long.toString(key), index));
        return map;
    }

    /** 记下去过这个兴趣点；第一次去时返回真。 */
    public boolean visit(long key) {
        return this.visited.add(key);
    }

    public boolean visited(long key) {
        return this.visited.contains(key);
    }

    /** 领一次奖励（终端、石碑）；第一次时返回真。 */
    public boolean claim(long key) {
        return this.claimed.add(key);
    }

    /** 领过没有（不记下）。 */
    public boolean claimed(long key) {
        return this.claimed.contains(key);
    }

    /** 这块石碑讲第几段线索：第一次碰时按顺序给下一段，读完一轮从头再来。 */
    public int monolithLore(long key, int count) {
        return this.monoliths.computeIfAbsent(key, k -> this.lore++ % count);
    }

    /** 先驱者的回应讲第几段：每碰一次石碑给下一段，听完一轮从头再来。 */
    public int nextResponse(int count) {
        return this.responses++ % count;
    }

    public int visitedCount() {
        return this.visited.size();
    }

    List<Long> visitedKeys() {
        return List.copyOf(this.visited);
    }
}
