package io.github.sweetcornna.universe.core.factory;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class BeltTest {
    private static final int U = BeltLine.UNITS_PER_BLOCK;

    /** 每条线路的位置从前往后递减、相邻至少隔一个间距、不越界。 */
    private static void assertValid(BeltLine line) {
        int limit = line.length();
        for (int i = 0; i < line.count(); i++) {
            int p = line.position(i);
            assertTrue(p <= limit && p >= 0, "item " + i + " at " + p + ", limit " + limit);
            limit = p - BeltLine.SPACING;
        }
    }

    @Test
    void itemsMoveAtTierSpeedAndStopAtTheEnd() {
        BeltLine line = new BeltLine(10, BeltTier.MK1);
        assertTrue(line.insert(0, 7));
        for (int i = 0; i < 50; i++) {
            line.tick();
        }
        assertEquals(50 * BeltTier.MK1.speed(), line.position(0));
        for (int i = 0; i < 500; i++) {
            line.tick();
        }
        assertEquals(line.length(), line.position(0));
        assertEquals(7, line.item(0));
        assertEquals(1.2, BeltTier.MK1.blocksPerSecond(), 1e-9);
    }

    @Test
    void blockedItemsQueueUpWithSpacing() {
        BeltLine line = new BeltLine(10, BeltTier.MK2);
        for (int i = 0; i < 5; i++) {
            assertTrue(line.insert(i * 150, i));
        }
        for (int i = 0; i < 200; i++) {
            line.tick();
        }
        assertArrayEquals(new int[] {2000, 1960, 1920, 1880, 1840}, line.positions());
        // 最前面的是最早放得最远的那个
        assertArrayEquals(new int[] {4, 3, 2, 1, 0}, line.items());
    }

    @Test
    void insertRejectsCrowdedOrOutOfRangePositions() {
        BeltLine line = new BeltLine(2, BeltTier.MK1);
        assertTrue(line.insert(0, 1));
        assertFalse(line.insert(20, 2));
        assertTrue(line.insert(40, 2));
        assertTrue(line.insert(200, 3));
        assertFalse(line.insert(180, 4));
        assertFalse(line.insert(401, 4));
        assertFalse(line.insert(-1, 4));
        assertTrue(line.insert(100, 5));
        assertArrayEquals(new int[] {200, 100, 40, 0}, line.positions());
        assertArrayEquals(new int[] {3, 5, 2, 1}, line.items());
        assertValid(line);
    }

    @Test
    void throughputMatchesTier() {
        for (BeltTier tier : BeltTier.values()) {
            BeltSystem system = new BeltSystem();
            BeltLine a = new BeltLine(7, tier);
            BeltLine b = new BeltLine(5, tier);
            int[] delivered = {0};
            system.add(a);
            system.add(b);
            system.connect(a, BeltOutput.into(b, 0));
            system.connect(b, (item, overshoot) -> {
                delivered[0]++;
                return true;
            });
            int measured = 0;
            for (int t = 0; t < 600; t++) {
                // 每刻供两个，最快的传送带每刻要上 1.5 个
                a.offer(2 * t);
                a.offer(2 * t + 1);
                system.tick();
                if (t == 199) {
                    measured = delivered[0];
                }
                assertValid(a);
                assertValid(b);
            }
            double perSecond = (delivered[0] - measured) / 20.0;
            assertEquals(tier.itemsPerSecond(), perSecond, tier.itemsPerSecond() * 0.03, tier + " delivered " + perSecond + "/s");
        }
        assertEquals(6, BeltTier.MK1.itemsPerSecond(), 1e-9);
        assertEquals(12, BeltTier.MK2.itemsPerSecond(), 1e-9);
        assertEquals(30, BeltTier.MK3.itemsPerSecond(), 1e-9);
    }

    @Test
    void blockedOutputBacksUpAndResumes() {
        BeltSystem system = new BeltSystem();
        BeltLine line = new BeltLine(3, BeltTier.MK2);
        boolean[] open = {false};
        List<Integer> received = new ArrayList<>();
        system.add(line);
        system.connect(line, (item, overshoot) -> open[0] && received.add(item));
        for (int t = 0; t < 300; t++) {
            line.insert(0, t);
            system.tick();
        }
        assertEquals(3 * U / BeltLine.SPACING + 1, line.count(), "full line");
        open[0] = true;
        for (int t = 0; t < 100; t++) {
            system.tick();
        }
        assertEquals(16, received.size());
        assertEquals(0, line.count());
        // 先进先出
        for (int i = 1; i < received.size(); i++) {
            assertTrue(received.get(i) > received.get(i - 1));
        }
    }

    @Test
    void sideLoadingFillsGapsWithoutCrowding() {
        BeltSystem system = new BeltSystem();
        BeltLine main = new BeltLine(20, BeltTier.MK1);
        BeltLine side = new BeltLine(4, BeltTier.MK1);
        int[] delivered = {0};
        system.add(main);
        system.add(side);
        system.connect(side, BeltOutput.into(main, 10 * U));
        system.connect(main, (item, overshoot) -> {
            delivered[0]++;
            return true;
        });
        int inserted = 0;
        int halfway = 0;
        for (int t = 0; t < 2000; t++) {
            // 主线每 8 刻来一个（每秒 2.5 个，间隔够侧线插进一个），侧线一直放满
            if (t % 8 == 0 && main.offer(1)) {
                inserted++;
            }
            if (side.offer(2)) {
                inserted++;
            }
            system.tick();
            assertValid(main);
            assertValid(side);
            if (t == 999) {
                halfway = delivered[0];
            }
        }
        assertEquals(inserted, delivered[0] + system.itemCount());
        double perSecond = (delivered[0] - halfway) / 50.0;
        assertTrue(perSecond > 4.5 && perSecond <= BeltTier.MK1.itemsPerSecond() + 0.1, "merged " + perSecond + "/s");
    }

    @Test
    void takePicksTheNearestMatchingItem() {
        BeltLine line = new BeltLine(4, BeltTier.MK1);
        line.insert(0, 1);
        line.insert(100, 2);
        line.insert(300, 3);
        line.insert(500, 2);
        assertEquals(2, line.take(320, 250, item -> item == 2));
        assertArrayEquals(new int[] {300, 100, 0}, line.positions());
        assertEquals(BeltLine.EMPTY, line.take(700, 50, item -> true));
        assertEquals(3, line.take(310, 20, item -> true));
        assertEquals(BeltLine.EMPTY, line.takeAtEnd(item -> true));
        assertValid(line);
    }

    @Test
    void splitAndConcatKeepEveryItem() {
        BeltLine line = new BeltLine(10, BeltTier.MK2);
        for (int p = 0; p <= line.length(); p += 120) {
            line.insert(p, p);
        }
        int count = line.count();
        BeltLine[] parts = line.split(4 * U);
        assertEquals(4 * U, parts[0].length());
        assertEquals(6 * U, parts[1].length());
        assertEquals(count, parts[0].count() + parts[1].count());
        assertValid(parts[0]);
        assertValid(parts[1]);
        BeltLine joined = BeltLine.concat(parts[0], parts[1]);
        assertArrayEquals(line.positions(), joined.positions());
        assertArrayEquals(line.items(), joined.items());

        // 前一段堵在尽头、后一段起点也有东西时，合起来要让出间距
        BeltLine first = new BeltLine(1, BeltTier.MK1);
        BeltLine second = new BeltLine(1, BeltTier.MK1);
        first.insert(U, 1);
        second.insert(0, 2);
        BeltLine merged = BeltLine.concat(first, second);
        assertEquals(2, merged.count());
        assertValid(merged);
    }

    @Test
    void closedLoopConservesItems() {
        BeltSystem system = new BeltSystem();
        BeltLine[] loop = {new BeltLine(5, BeltTier.MK3), new BeltLine(7, BeltTier.MK3), new BeltLine(3, BeltTier.MK3)};
        for (BeltLine line : loop) {
            system.add(line);
            // 八成满
            for (int p = line.length() - 25; p >= 0; p -= 50) {
                line.insert(p, p);
            }
        }
        for (int i = 0; i < loop.length; i++) {
            system.connect(loop[i], BeltOutput.into(loop[(i + 1) % loop.length], 0));
        }
        int items = system.itemCount();
        for (int t = 0; t < 2000; t++) {
            system.tick();
            for (BeltLine line : loop) {
                assertValid(line);
            }
        }
        assertEquals(items, system.itemCount());
    }

    @Test
    void restoreDropsInvalidEntries() {
        BeltLine line = BeltLine.restore(400, 12, new int[] {400, 390, 300, -5, 100}, new int[] {1, 2, 3, 4, 5}, new int[] {9});
        assertArrayEquals(new int[] {400, 300, 100}, line.positions());
        assertArrayEquals(new int[] {1, 3, 5}, line.items());
        assertArrayEquals(new int[] {9}, line.waiting());
    }

    /** S5 的通过标准：1 万段传送带、4 万个物品，每刻推进低于 2 ms。 */
    @Test
    void tenThousandSegmentsAndFortyThousandItemsStayWithinBudget() {
        BeltSystem system = new BeltSystem();
        // 10 个环，每个环 10 条 100 格的线路；每条放 400 个物品（八成满）
        for (int ring = 0; ring < 10; ring++) {
            BeltLine[] lines = new BeltLine[10];
            for (int i = 0; i < lines.length; i++) {
                lines[i] = new BeltLine(100, BeltTier.MK2);
                for (int k = 0; k < 400; k++) {
                    lines[i].insert(lines[i].length() - 10 - k * 50, ring * 4000 + i * 400 + k);
                }
                system.add(lines[i]);
            }
            for (int i = 0; i < lines.length; i++) {
                system.connect(lines[i], BeltOutput.into(lines[(i + 1) % lines.length], 0));
            }
        }
        assertEquals(40_000, system.itemCount());
        for (int t = 0; t < 400; t++) {
            system.tick();
        }
        BeltLine probe = system.lines().get(0);
        int headBefore = probe.item(0);
        int ticks = 2000;
        long start = System.nanoTime();
        for (int t = 0; t < ticks; t++) {
            system.tick();
        }
        double ms = (System.nanoTime() - start) / 1e6 / ticks;
        System.out.printf("belts: 10000 segments, %d items: %.3f ms/tick%n", system.itemCount(), ms);
        assertEquals(40_000, system.itemCount());
        // 物品真的在环上转，不是堵死了
        assertTrue(probe.item(0) != headBefore);
        assertTrue(ms < 2.0, ms + " ms/tick");
    }
}
