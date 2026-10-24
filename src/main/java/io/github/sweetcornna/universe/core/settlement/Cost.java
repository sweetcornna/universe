package io.github.sweetcornna.universe.core.settlement;

/**
 * 施工、升级、修理、接管要交的一样东西（ADR-0034 §二）。
 *
 * @param item  物品 ID（{@code universe:steel}），或者 {@code #} 开头的物品标签（{@code #universe:settlement/stone}）
 * @param count 要几个
 */
public record Cost(String item, int count) {
    /** 聚落用的三个材料标签：石料、木料、玻璃（各种星球的石头、原木、木板都算）。 */
    public static final String STONE = "#universe:settlement/stone";
    public static final String TIMBER = "#universe:settlement/timber";
    public static final String GLASS = "#universe:settlement/glass";

    public Cost {
        if (count <= 0) {
            throw new IllegalArgumentException("count " + count);
        }
    }

    public boolean tag() {
        return this.item.startsWith("#");
    }

    /** 去掉 {@code #} 的标签 ID，或者物品 ID。 */
    public String id() {
        return this.tag() ? this.item.substring(1) : this.item;
    }

    static Cost of(String item, int count) {
        return new Cost(item.startsWith("#") || item.contains(":") ? item : "universe:" + item, count);
    }
}
