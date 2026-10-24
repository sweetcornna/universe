package io.github.sweetcornna.universe.core.deepspace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;

class HulkLayoutTest {
    @Test
    void hulksAreSixtyToOneTwentyLongWithOneReactor() {
        for (long seed = 0; seed < 60; seed++) {
            Layout hulk = HulkLayout.generate(seed);
            int[] min = hulk.min();
            int[] max = hulk.max();
            int length = max[0] - min[0] + 1;
            assertTrue(length >= HulkLayout.MIN_LENGTH - 2 && length <= HulkLayout.MAX_LENGTH + 4, "长度 " + length + "（种子 " + seed + "）");
            assertTrue(max[1] - min[1] < 40, "高度放得进太空维度");
            Map<Part, Integer> counts = hulk.counts();
            assertEquals(1, counts.getOrDefault(Part.REACTOR, 0), "一艘残骸一个堆芯");
            assertTrue(counts.getOrDefault(Part.SHIELDING, 0) >= 26, "堆芯包在屏蔽层里");
            assertTrue(counts.getOrDefault(Part.CONDUIT, 0) >= 6, "主梁里有导管");
            assertTrue(counts.getOrDefault(Part.CARGO, 0) >= 1, "有货柜");
            assertTrue(counts.getOrDefault(Part.RADIATOR, 0) > 40, "有散热板");
            assertTrue(counts.getOrDefault(Part.TANK, 0) > 20, "有推进剂罐");
            assertTrue(counts.getOrDefault(Part.WINDOW, 0) > 0 && counts.getOrDefault(Part.LIGHT, 0) > 0);
        }
    }

    @Test
    void sameSeedSameHulk() {
        assertEquals(HulkLayout.generate(42).cells(), HulkLayout.generate(42).cells());
        assertTrue(!HulkLayout.generate(42).cells().equals(HulkLayout.generate(43).cells()));
    }

    @Test
    void reactorSitsInsideItsShielding() {
        Layout hulk = HulkLayout.generate(7);
        Layout.Cell core = hulk.first(Part.REACTOR).orElseThrow();
        int shielded = 0;
        for (Layout.Cell cell : hulk.cells()) {
            if (cell.part() == Part.SHIELDING && Math.abs(cell.x() - core.x()) <= 1 && Math.abs(cell.y() - core.y()) <= 1
                    && Math.abs(cell.z() - core.z()) <= 1) {
                shielded++;
            }
        }
        assertEquals(26, shielded, "堆芯四面八方都是屏蔽层，要先拆一块才拿得到");
    }

    @Test
    void outpostHasTerminalCratesAndDevices() {
        for (long seed = 0; seed < 30; seed++) {
            Layout outpost = OutpostLayout.generate(seed);
            Map<Part, Integer> counts = outpost.counts();
            assertEquals(1, counts.getOrDefault(Part.TERMINAL, 0), "一台数据终端");
            int crates = counts.getOrDefault(Part.CRATE, 0);
            assertTrue(crates >= 3 && crates <= 5, "三到五个补给箱：" + crates);
            assertTrue(counts.getOrDefault(Part.DEVICE, 0) >= 3, "能拆的设备");
            assertTrue(counts.getOrDefault(Part.SOLAR, 0) > 30, "太阳能板");
            assertEquals(0, counts.getOrDefault(Part.REACTOR, 0), "前哨站没有反应堆");
        }
    }
}
