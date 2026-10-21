package io.github.sweetcornna.universe.core.factory;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CargoTest {
    @Test
    void singlePlainCargoIsTheItemId() {
        // 旧代码把物品编号直接当货物用，这样才兼容
        assertEquals(1234, Cargo.of(1234, 1, 0));
        assertEquals(1234, Cargo.item(1234));
        assertEquals(1, Cargo.count(1234));
        assertEquals(0, Cargo.points(1234));
    }

    @Test
    void packsCountAndPoints() {
        int c = Cargo.of(Cargo.MAX_ITEM - 1, 4, 16);
        assertTrue(c > 0, "最高位不用，货物不会是负数");
        assertEquals(Cargo.MAX_ITEM - 1, Cargo.item(c));
        assertEquals(4, Cargo.count(c));
        assertEquals(16, Cargo.points(c));
        assertEquals(4, Cargo.level(c));
        // 点数超过每个 4 点的截掉
        assertEquals(8, Cargo.points(Cargo.of(7, 2, 30)));
    }

    @Test
    void mergeAndSplitKeepPoints() {
        int a = Cargo.uniform(5, 1, 4);
        int b = Cargo.uniform(5, 2, 1);
        assertTrue(Cargo.canMerge(a, b, 4));
        assertFalse(Cargo.canMerge(a, b, 2));
        assertFalse(Cargo.canMerge(a, Cargo.uniform(6, 1, 0), 4));
        int m = Cargo.merge(a, b);
        assertEquals(3, Cargo.count(m));
        assertEquals(6, Cargo.points(m));
        int[] parts = Cargo.split(m, 1);
        assertEquals(1, Cargo.count(parts[0]));
        assertEquals(2, Cargo.points(parts[0]));
        assertEquals(2, Cargo.count(parts[1]));
        assertEquals(4, Cargo.points(parts[1]));
        assertArrayEquals(new int[] {m, BeltLine.EMPTY}, Cargo.split(m, 3));
    }

    @Test
    void withItemKeepsCountAndPoints() {
        int c = Cargo.of(77, 3, 7);
        int moved = Cargo.withItem(Cargo.withItem(c, 0), 1234);
        assertEquals(1234, Cargo.item(moved));
        assertEquals(3, Cargo.count(moved));
        assertEquals(7, Cargo.points(moved));
        // 普通货物的附加部分是 0，存档里可以不存
        assertEquals(0, Cargo.withItem(Cargo.of(55, 1, 0), 0));
    }

    @Test
    void sprayTopsUpAndCountsSprays() {
        int[] used = {0};
        int c = Cargo.spray(Cargo.of(9, 3, 0), Proliferation.Tier.MK3, used);
        assertEquals(12, Cargo.points(c));
        assertEquals(3, used[0]);
        // 已经喷过更高档的不再喷
        assertEquals(c, Cargo.spray(c, Proliferation.Tier.MK1, used));
        assertEquals(3, used[0]);
        // 只剩两次：四个里喷两个，另外两个还是原来的 1 点
        used[0] = 0;
        int partial = Cargo.spray(Cargo.uniform(9, 4, 1), Proliferation.Tier.MK2, 2, used);
        assertEquals(2, used[0]);
        assertEquals(4, Cargo.count(partial));
        assertEquals(2 * 2 + 2 * 1, Cargo.points(partial));
    }
}
