package io.github.sweetcornna.universe.core.research;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jspecify.annotations.Nullable;

/**
 * 研究进度（玩法设计 §9.5）：完成了哪些科技、升级科技到了几级、正在研究哪个、每个科技攒了多少点。同一时间只研究
 * 一个科技；换成别的再换回来，攒下的点数不丢。升级科技攒的是下一级的点数。
 */
public final class ResearchState {
    private final Set<String> done = new LinkedHashSet<>();
    private final Map<String, Integer> progress = new HashMap<>();
    private final Map<String, Integer> levels = new HashMap<>();
    private @Nullable String current;

    public ResearchState() {
    }

    public ResearchState(Set<String> done, @Nullable String current, Map<String, Integer> progress) {
        this(done, current, progress, Map.of());
    }

    public ResearchState(Set<String> done, @Nullable String current, Map<String, Integer> progress, Map<String, Integer> levels) {
        this.done.addAll(done);
        this.current = current;
        progress.forEach((id, points) -> {
            if (points > 0) {
                this.progress.put(id, points);
            }
        });
        levels.forEach((id, level) -> {
            if (level > 0) {
                this.levels.put(id, level);
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

    public Map<String, Integer> levels() {
        return Collections.unmodifiableMap(this.levels);
    }

    /** 升级科技研究到了第几级，没研究过是 0。 */
    public int level(String id) {
        return this.levels.getOrDefault(id, 0);
    }

    /** 升级科技满级了没有（有无限档的永远不会满）。 */
    public boolean isMaxed(Upgrade upgrade) {
        return this.level(upgrade.id()) >= upgrade.maxLevel();
    }

    /** 研究完要攒多少点：科技是它的点数，升级科技是下一级的点数；上交物品的、满级的、不认识的是 0。 */
    public int pointsNeeded(TechTree tree, String id) {
        Upgrade upgrade = tree.upgrade(id).orElse(null);
        if (upgrade != null) {
            return this.isMaxed(upgrade) ? 0 : upgrade.points(this.level(id) + 1);
        }
        return tree.get(id).map(Tech::points).orElse(0);
    }

    /** 前置都完成了、自己还没完成。升级科技是：没满级，下一级的前置和矩阵的科技都完成了。 */
    public boolean available(TechTree tree, String id) {
        Upgrade upgrade = tree.upgrade(id).orElse(null);
        if (upgrade != null) {
            return !this.isMaxed(upgrade) && this.done.containsAll(tree.requirements(upgrade, this.level(id) + 1));
        }
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

    /** 开始研究一个要研究站的科技或升级科技。 */
    public boolean start(TechTree tree, String id) {
        if (tree.upgrade(id).isEmpty()) {
            Tech tech = tree.get(id).orElse(null);
            if (tech == null || tech.isHandIn()) {
                return false;
            }
        }
        if (!this.available(tree, id)) {
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
    public List<String> currentMatrices(TechTree tree) {
        String id = this.current;
        if (id == null) {
            return List.of();
        }
        Upgrade upgrade = tree.upgrade(id).orElse(null);
        if (upgrade != null) {
            return this.isMaxed(upgrade) ? List.of() : upgrade.matrices(this.level(id) + 1);
        }
        return tree.get(id).map(Tech::matrices).orElse(List.of());
    }

    /**
     * 研究站攒了一点。
     *
     * @return 这一点让当前科技（或升级科技的一级）研究完成时返回它的 ID，否则为空
     */
    public @Nullable String addPoint(TechTree tree) {
        String id = this.current;
        Upgrade upgrade = id == null ? null : tree.upgrade(id).orElse(null);
        if (upgrade != null) {
            return this.addUpgradePoint(tree, upgrade);
        }
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

    private @Nullable String addUpgradePoint(TechTree tree, Upgrade upgrade) {
        String id = upgrade.id();
        if (this.isMaxed(upgrade)) {
            this.current = null;
            return null;
        }
        int next = this.level(id) + 1;
        int points = this.progress.getOrDefault(id, 0) + 1;
        if (points < upgrade.points(next)) {
            this.progress.put(id, points);
            return null;
        }
        this.progress.remove(id);
        this.levels.put(id, next);
        // 下一级用的矩阵一样就接着研究（白矩阵的无限档会一直研究下去），换了矩阵或满级了就停
        if (!this.available(tree, id) || !upgrade.matrices(next + 1).equals(upgrade.matrices(next))) {
            this.current = null;
        }
        return id;
    }

    /** 直接设升级科技的级数（命令用）。 */
    public void setLevel(String id, int level) {
        this.progress.remove(id);
        if (level > 0) {
            this.levels.put(id, level);
        } else {
            this.levels.remove(id);
        }
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
        this.levels.clear();
        this.current = null;
    }
}
