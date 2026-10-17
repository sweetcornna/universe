package io.github.sweetcornna.universe.core.dimension;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * 运行时维度的空闲计时（ADR-0004）：每个维度连续空闲多久，超过上限就该卸载了。
 *
 * <p>"空闲"由适配层判断（没有玩家、没有强制加载的区块等）。忙过一次就从零开始重新计时。
 *
 * @param <K> 维度的键
 */
public final class IdleTimer<K> {
    private final int limit;
    private final Map<K, Integer> idle = new HashMap<>();

    /** @param limit 连续空闲多少刻算超时 */
    public IdleTimer(int limit) {
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be positive: " + limit);
        }
        this.limit = limit;
    }

    /**
     * 推进 {@code elapsed} 刻。
     *
     * @param candidates 当前可以卸载的维度；不在里面的不再计时
     * @param busy       维度这一刻是否在用
     * @return 这一次刚好超时的维度。返回过的从零重新计时，卸载没成功的话要再空闲一整段才会再返回
     */
    public List<K> advance(Collection<K> candidates, Predicate<K> busy, int elapsed) {
        Set<K> present = new HashSet<>(candidates);
        this.idle.keySet().retainAll(present);
        List<K> expired = new ArrayList<>();
        for (K key : candidates) {
            if (busy.test(key)) {
                this.idle.remove(key);
                continue;
            }
            int ticks = this.idle.getOrDefault(key, 0) + elapsed;
            if (ticks >= this.limit) {
                this.idle.remove(key);
                expired.add(key);
            } else {
                this.idle.put(key, ticks);
            }
        }
        return expired;
    }

    /** 维度已经空闲了多少刻（没在计时的是 0）。 */
    public int idleTicks(K key) {
        return this.idle.getOrDefault(key, 0);
    }

    public void clear() {
        this.idle.clear();
    }
}
