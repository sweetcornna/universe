package io.github.sweetcornna.universe.core.research;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.jspecify.annotations.Nullable;

/**
 * 研究进度（玩法设计 §9.5）：完成了哪些科技、正在研究哪个、每个科技攒了多少点。同一时间只研究一个科技；
 * 换成别的再换回来，攒下的点数不丢。
 */
public final class ResearchState {
    private final Set<String> done = new LinkedHashSet<>();
    private final Map<String, Integer> progress = new HashMap<>();
    private @Nullable String current;

    public ResearchState() {
    }

    public ResearchState(Set<String> done, @Nullable String current, Map<String, Integer> progress) {
        this.done.addAll(done);
        this.current = current;
        progress.forEach((id, points) -> {
            if (points > 0) {
                this.progress.put(id, points);
            }
        });
    }

    public Set<String> done() {
        return Collections.unmodifiableSet(this.done);
    }

    public Map<String, Integer> progress() {
        return Collections.unmodifiableMap(this.progress);
    }

    public @Nullable String current() {
        return this.current;
    }

    public boolean isDone(String id) {
        return this.done.contains(id);
    }

    public int progress(String id) {
        return this.progress.getOrDefault(id, 0);
    }

    /** 前置都完成了、自己还没完成。 */
    public boolean available(TechTree tree, String id) {
        Tech tech = tree.get(id).orElse(null);
        if (tech == null || this.done.contains(id)) {
            return false;
        }
        for (String required : tech.requires()) {
            if (!this.done.contains(required)) {
                return false;
            }
        }
        return true;
    }

    /** 开始研究一个要研究站的科技。 */
    public boolean start(TechTree tree, String id) {
        Tech tech = tree.get(id).orElse(null);
        if (tech == null || tech.isHandIn() || !this.available(tree, id)) {
            return false;
        }
        this.current = id;
        return true;
    }

    /** 停止当前的研究（点数保留）。 */
    public void stop() {
        this.current = null;
    }

    /** 当前研究需要的矩阵，没有在研究时为空。 */
    public java.util.List<String> currentMatrices(TechTree tree) {
        return this.current == null ? java.util.List.of() : tree.get(this.current).map(Tech::matrices).orElse(java.util.List.of());
    }

    /**
     * 研究站攒了一点。
     *
     * @return 这一点让当前科技研究完成时返回它，否则为空
     */
    public @Nullable String addPoint(TechTree tree) {
        String id = this.current;
        Tech tech = id == null ? null : tree.get(id).orElse(null);
        if (tech == null || tech.isHandIn()) {
            return null;
        }
        int points = this.progress.getOrDefault(id, 0) + 1;
        if (points < tech.points()) {
            this.progress.put(id, points);
            return null;
        }
        this.complete(id);
        return id;
    }

    /** 直接完成一个科技（上交物品、命令）。 */
    public void complete(String id) {
        this.done.add(id);
        this.progress.remove(id);
        if (id.equals(this.current)) {
            this.current = null;
        }
    }

    /** 收回一个科技（命令用）。 */
    public void revoke(String id) {
        this.done.remove(id);
    }

    public void clear() {
        this.done.clear();
        this.progress.clear();
        this.current = null;
    }
}
