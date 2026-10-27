package io.github.sweetcornna.universe.core.chem.reaction;

/**
 * 配方里的一样物品和个数。
 *
 * @param item  注册名（{@code universe:xxx}）
 * @param count 一批用掉或做出几个
 */
public record ItemCount(String item, int count) {
    public ItemCount {
        if (count <= 0) {
            throw new IllegalArgumentException("count must be positive: " + count);
        }
    }
}
