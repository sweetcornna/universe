package io.github.sweetcornna.universe.core.research;

import java.util.List;

import org.jspecify.annotations.Nullable;

/**
 * 升级科技（ADR-0018）：一条线分好几级，一级一级地研究。前几级各有固定的矩阵和点数；有无限档的线之后一直用
 * 无限档的矩阵研究下去，每级比上一级多 {@code step} 点。
 *
 * @param id       ID，语言键是 {@code tech.universe.<id>}，和科技共用一个命名空间
 * @param icon     科技树里画的物品（物品 ID）
 * @param requires 研究第 1 级之前要完成的科技
 * @param tiers    有限的各级，第 i 个是第 i+1 级
 * @param infinite 无限档，没有时研究完有限的几级就满级了
 */
public record Upgrade(String id, String icon, List<String> requires, List<Tier> tiers, @Nullable Infinite infinite) {
    public Upgrade {
        requires = List.copyOf(requires);
        tiers = List.copyOf(tiers);
        if (tiers.isEmpty()) {
            throw new IllegalArgumentException(id + ": upgrade needs at least one tier");
        }
    }

    /** 有限的一级：研究站每点消耗的矩阵和要攒的点数。 */
    public record Tier(List<String> matrices, int points) {
        public Tier {
            matrices = List.copyOf(matrices);
            if (matrices.isEmpty() || points <= 0) {
                throw new IllegalArgumentException("tier needs matrices and points");
            }
        }
    }

    /** 无限档：紧接着有限各级的那一级要 {@code base} 点，之后每级多 {@code step} 点。 */
    public record Infinite(List<String> matrices, int base, int step) {
        public Infinite {
            matrices = List.copyOf(matrices);
            if (matrices.isEmpty() || base <= 0 || step < 0) {
                throw new IllegalArgumentException("bad infinite tier");
            }
        }
    }

    /** 最高几级；有无限档时没有上限。 */
    public int maxLevel() {
        return this.infinite == null ? this.tiers.size() : Integer.MAX_VALUE;
    }

    /** 研究第 level 级（从 1 数）时每点消耗的矩阵。 */
    public List<String> matrices(int level) {
        if (level <= this.tiers.size()) {
            return this.tiers.get(this.checkLevel(level) - 1).matrices();
        }
        return this.infiniteTier(level).matrices();
    }

    /** 研究第 level 级要攒的点数。 */
    public int points(int level) {
        if (level <= this.tiers.size()) {
            return this.tiers.get(this.checkLevel(level) - 1).points();
        }
        Infinite tail = this.infiniteTier(level);
        long points = tail.base() + (long) tail.step() * (level - this.tiers.size() - 1);
        return (int) Math.min(Integer.MAX_VALUE, points);
    }

    /** 第 level 级是不是无限档里的。 */
    public boolean isInfiniteLevel(int level) {
        return this.infinite != null && level > this.tiers.size();
    }

    private int checkLevel(int level) {
        if (level < 1) {
            throw new IllegalArgumentException(this.id + ": level " + level);
        }
        return level;
    }

    private Infinite infiniteTier(int level) {
        if (this.infinite == null) {
            throw new IllegalArgumentException(this.id + " has no level " + level);
        }
        return this.infinite;
    }
}
