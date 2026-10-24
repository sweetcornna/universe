package io.github.sweetcornna.universe.core.research;

import java.util.ArrayList;
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
 *
 * <p>研究队列（ADR-0033 §五）：当前的研究完成或停下时，从队列里挑第一个能研究的接着研究，前置没好的跳过、留在队里。
 * 同一条升级科技可以排好几次，一次是一级。
 */
public final class ResearchState {
    /** 队列最多排几项：通关前 8 项，通关后 32 项。 */
    public static final int QUEUE_LIMIT = 8;
    public static final int QUEUE_LIMIT_COMPLETE = 32;

    private final Set<String> done = new LinkedHashSet<>();
    private final Map<String, Integer> progress = new HashMap<>();
    private final Map<String, Integer> levels = new HashMap<>();
    private final List<String> queue = new ArrayList<>();
    private @Nullable String current;

    public ResearchState() {
    }

    public ResearchState(Set<String> done, @Nullable String current, Map<String, Integer> progress) {
        this(done, current, progress, Map.of());
    }

    public ResearchState(Set<String> done, @Nullable String current, Map<String, Integer> progress, Map<String, Integer> levels) {
        this(done, current, progress, levels, List.of());
    }

    public ResearchState(Set<String> done, @Nullable String current, Map<String, Integer> progress, Map<String, Integer> levels,
            List<String> queue) {
        this.done.addAll(done);
        this.queue.addAll(queue);
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

    /** 排着队的研究，按顺序。 */
    public List<String> queue() {
        return Collections.unmodifiableList(this.queue);
    }

    public static int queueLimit(boolean gameComplete) {
        return gameComplete ? QUEUE_LIMIT_COMPLETE : QUEUE_LIMIT;
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
        this.advance(tree);
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
        // 下一级用的矩阵一样就接着研究（白矩阵的无限档会一直研究下去），换了矩阵或满级了就停。
        // 队里排着别的就先研究队里的；队里的都还研究不了，再接着研究这一条
        boolean keep = this.available(tree, id) && upgrade.matrices(next + 1).equals(upgrade.matrices(next));
        if (!keep || !this.queue.isEmpty()) {
            this.current = null;
            this.advance(tree);
            if (this.current == null && keep) {
                this.current = id;
            }
        }
        return id;
    }

    /**
     * 能不能排进队：队没满；是要研究站的科技或升级科技；科技没完成、没在研究、没排过，升级科技排到的那一级不超过满级；
     * 前置（升级科技是排到的那一级的前置）都已完成、正在研究，或者已经在队里。
     */
    public boolean canEnqueue(TechTree tree, String id, int limit) {
        if (this.queue.size() >= limit) {
            return false;
        }
        Upgrade upgrade = tree.upgrade(id).orElse(null);
        if (upgrade != null) {
            int level = this.plannedLevel(id, this.queue.size());
            return level <= upgrade.maxLevel() && this.reachable(tree.requirements(upgrade, level));
        }
        Tech tech = tree.get(id).orElse(null);
        if (tech == null || tech.isHandIn() || this.done.contains(id) || id.equals(this.current) || this.queue.contains(id)) {
            return false;
        }
        return this.reachable(tech.requires());
    }

    /** 排到队尾。没在研究时马上开始研究队里第一个能研究的。 */
    public boolean enqueue(TechTree tree, String id, int limit) {
        if (!this.canEnqueue(tree, id, limit)) {
            return false;
        }
        this.queue.add(id);
        if (this.current == null) {
            this.advance(tree);
        }
        return true;
    }

    /** 从队里去掉第 index 项；排在队里、要靠它当前置的也一起去掉。 */
    public boolean dequeue(TechTree tree, int index) {
        if (index < 0 || index >= this.queue.size()) {
            return false;
        }
        this.queue.remove(index);
        boolean changed = true;
        while (changed) {
            changed = false;
            for (int i = 0; i < this.queue.size(); i++) {
                String id = this.queue.get(i);
                Upgrade upgrade = tree.upgrade(id).orElse(null);
                int level = this.plannedLevel(id, i);
                boolean keep = upgrade != null
                        ? level <= upgrade.maxLevel() && this.reachable(tree.requirements(upgrade, level))
                        : this.reachable(tree.get(id).map(Tech::requires).orElse(List.of()));
                if (!keep) {
                    this.queue.remove(i);
                    changed = true;
                    break;
                }
            }
        }
        return true;
    }

    /** 把第 index 项往前（delta 为负）或往后挪。 */
    public boolean move(int index, int delta) {
        int target = index + delta;
        if (delta == 0 || index < 0 || index >= this.queue.size() || target < 0 || target >= this.queue.size()) {
            return false;
        }
        this.queue.add(target, this.queue.remove(index));
        return true;
    }

    public void clearQueue() {
        this.queue.clear();
    }

    /**
     * 当前没在研究时，开始研究队里第一个能研究的，把它从队里拿掉；前置没好的跳过、留在队里。已经完成的科技、
     * 排超了满级的升级科技先从队里去掉。
     *
     * @return 开始研究的 ID，没开始新的研究时为空
     */
    public @Nullable String advance(TechTree tree) {
        for (int i = this.queue.size() - 1; i >= 0; i--) {
            if (this.obsolete(tree, i)) {
                this.queue.remove(i);
            }
        }
        if (this.current != null) {
            return null;
        }
        for (int i = 0; i < this.queue.size(); i++) {
            String id = this.queue.get(i);
            if (this.start(tree, id)) {
                this.queue.remove(i);
                return id;
            }
        }
        return null;
    }

    /** 队里第 index 项研究的是第几级：升级科技是现在的级数加上正在研究的一级和排在它前面的次数，科技是 0。 */
    public int plannedLevel(TechTree tree, int index) {
        if (index < 0 || index >= this.queue.size()) {
            return 0;
        }
        String id = this.queue.get(index);
        return tree.upgrade(id).isPresent() ? this.plannedLevel(id, index) : 0;
    }

    private int plannedLevel(String id, int index) {
        int level = this.level(id) + (id.equals(this.current) ? 1 : 0) + 1;
        for (int i = 0; i < index; i++) {
            if (this.queue.get(i).equals(id)) {
                level++;
            }
        }
        return level;
    }

    /** 前置都已完成、正在研究，或者在队里。 */
    private boolean reachable(List<String> requires) {
        for (String required : requires) {
            if (!this.done.contains(required) && !required.equals(this.current) && !this.queue.contains(required)) {
                return false;
            }
        }
        return true;
    }

    /** 队里这一项已经用不着了：不认识、要上交物品、完成了、正在研究、排重了，或者升级科技排超了满级。 */
    private boolean obsolete(TechTree tree, int index) {
        String id = this.queue.get(index);
        Upgrade upgrade = tree.upgrade(id).orElse(null);
        if (upgrade != null) {
            return this.plannedLevel(id, index) > upgrade.maxLevel();
        }
        Tech tech = tree.get(id).orElse(null);
        return tech == null || tech.isHandIn() || this.done.contains(id) || id.equals(this.current) || this.queue.indexOf(id) != index;
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

    /** 一份副本。 */
    public ResearchState copy() {
        return new ResearchState(this.done, this.current, this.progress, this.levels, this.queue);
    }

    /** 换成另一份进度（GameTest 恢复现场用）。 */
    public void copyFrom(ResearchState other) {
        this.clear();
        this.done.addAll(other.done);
        this.progress.putAll(other.progress);
        this.levels.putAll(other.levels);
        this.queue.addAll(other.queue);
        this.current = other.current;
    }

    public void clear() {
        this.done.clear();
        this.progress.clear();
        this.levels.clear();
        this.queue.clear();
        this.current = null;
    }
}
