package io.github.sweetcornna.universe.core.poi;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

/** 聚落和交易站里 NPC 站的地方（ADR-0027 §五）。 */
class SettlementLayoutTest {
    private static Landmark settlement(int x, int z, long seed, int rotation) {
        return new Landmark(LandmarkType.SETTLEMENT, x, z, 0, 0, 0, seed, rotation);
    }

    private static Landmark tradingPost(int x, int z, int rotation) {
        return new Landmark(LandmarkType.TRADING_POST, x, z, 0, 0, 0, 7, rotation);
    }

    /** 世界坐标转回本地坐标（{@link SettlementLayout#world} 反过来）。 */
    private static int[] local(Landmark landmark, int[] world) {
        int dx = world[0] - landmark.x();
        int dz = world[1] - landmark.z();
        return switch (Math.floorMod(landmark.rotation(), 4)) {
            case 1 -> new int[] {dz, -dx};
            case 2 -> new int[] {-dx, -dz};
            case 3 -> new int[] {-dz, dx};
            default -> new int[] {dx, dz};
        };
    }

    private static int chebyshev(int[] a, int[] b) {
        return Math.max(Math.abs(a[0] - b[0]), Math.abs(a[1] - b[1]));
    }

    @Test
    void rotationMatchesTheStructurePlacer() {
        // 和 PoiStructures.Placer.at 一样：顺时针 90° 时 (x, z) → (-z, x)
        assertArrayEquals(new int[] {3, 5}, SettlementLayout.rotate(0, 3, 5));
        assertArrayEquals(new int[] {-5, 3}, SettlementLayout.rotate(1, 3, 5));
        assertArrayEquals(new int[] {-3, -5}, SettlementLayout.rotate(2, 3, 5));
        assertArrayEquals(new int[] {5, -3}, SettlementLayout.rotate(3, 3, 5));
        assertArrayEquals(SettlementLayout.rotate(1, 3, 5), SettlementLayout.rotate(-3, 3, 5));
        // "前方"（本地 -z）转一次朝东
        assertArrayEquals(new int[] {1, 0}, SettlementLayout.rotate(1, 0, -1));
    }

    @Test
    void npcSpotsFollowTheCenterAndRotation() {
        for (int rotation = 0; rotation < 4; rotation++) {
            Landmark here = settlement(100, -200, 5, rotation);
            Landmark there = settlement(-3000, 4000, 5, rotation);
            List<int[]> a = SettlementLayout.npcSpots(here);
            List<int[]> b = SettlementLayout.npcSpots(there);
            assertEquals(3, a.size());
            for (int i = 0; i < a.size(); i++) {
                // 平移中心，站的地方跟着平移；转回本地坐标都一样
                assertEquals(a.get(i)[0] - 100, b.get(i)[0] + 3000);
                assertEquals(a.get(i)[1] + 200, b.get(i)[1] - 4000);
                assertArrayEquals(local(here, a.get(i)), local(settlement(0, 0, 5, 0), SettlementLayout.npcSpots(settlement(0, 0, 5, 0)).get(i)));
            }
        }
        // 四个朝向的站位各不相同
        Set<String> seen = new HashSet<>();
        for (int rotation = 0; rotation < 4; rotation++) {
            int[] first = SettlementLayout.npcSpots(settlement(0, 0, 5, rotation)).get(0);
            seen.add(first[0] + "," + first[1]);
        }
        assertEquals(4, seen.size());
    }

    @Test
    void npcSpotsStayInsideTheSettlementAndOutOfTheBuildings() {
        for (long seed = 0; seed < 64; seed++) {
            for (int rotation = 0; rotation < 4; rotation++) {
                Landmark landmark = settlement(37, -91, seed, rotation);
                List<int[]> spots = SettlementLayout.npcSpots(landmark);
                Set<String> distinct = new HashSet<>();
                int[] stall = SettlementLayout.stall();
                for (int[] spot : spots) {
                    distinct.add(spot[0] + "," + spot[1]);
                    assertTrue(landmark.distance(spot[0] + 0.5, spot[1] + 0.5) <= LandmarkType.SETTLEMENT.radius() - 3, "离中心太远");
                    int[] l = local(landmark, spot);
                    for (int[] hut : SettlementLayout.huts(landmark)) {
                        // 最大的小屋再往外一格（门口）也不站人
                        assertTrue(chebyshev(l, hut) > SettlementLayout.HUT_HALF + 1, "站进了小屋");
                    }
                    assertTrue(chebyshev(l, SettlementLayout.stone()) >= 2, "站在知识石上");
                    assertTrue(l[0] < stall[0] || l[0] > stall[2] || l[1] < stall[1] || l[1] > stall[3], "站进了货摊");
                    for (int[] lamp : SettlementLayout.lamps()) {
                        assertTrue(chebyshev(l, lamp) >= 1, "站在灯上");
                    }
                    // 不挡从广场到交易终端的那条路（x = 终端的 x）
                    assertNotEquals(SettlementLayout.terminal()[0], l[0]);
                }
                assertEquals(3, distinct.size());
            }
        }
    }

    @Test
    void buildingsStayInsideTheFifteenBlockLimit() {
        int[] stall = SettlementLayout.stall();
        assertTrue(Math.hypot(Math.max(Math.abs(stall[0]), Math.abs(stall[2])), stall[3]) <= 15);
        for (long seed = 0; seed < 16; seed++) {
            Landmark landmark = settlement(0, 0, seed, 0);
            List<int[]> huts = SettlementLayout.huts(landmark);
            for (int[] hut : huts) {
                int far = SettlementLayout.HUT_HALF + 1;
                assertTrue(Math.hypot(Math.abs(hut[0]) + far, Math.abs(hut[1]) + far) <= 15, "小屋伸出 15 格");
                // 小屋之间、小屋和货摊、广场之间不重叠
                for (int[] other : huts) {
                    assertTrue(hut == other || chebyshev(hut, other) > 2 * SettlementLayout.HUT_HALF + 1);
                }
                assertTrue(Math.abs(hut[0]) - SettlementLayout.HUT_HALF > stall[2]);
                assertTrue(Math.abs(hut[0]) - SettlementLayout.HUT_HALF >= SettlementLayout.PLAZA);
            }
        }
    }

    @Test
    void hutsAreThreeOrFourAndDeterministic() {
        Set<Integer> counts = new HashSet<>();
        for (long seed = 0; seed < 64; seed++) {
            Landmark landmark = settlement(5, 5, seed, (int) (seed & 3));
            List<int[]> huts = SettlementLayout.huts(landmark);
            assertTrue(huts.size() == 3 || huts.size() == 4);
            counts.add(huts.size());
            List<int[]> again = SettlementLayout.huts(settlement(5, 5, seed, (int) (seed & 3)));
            for (int i = 0; i < huts.size(); i++) {
                assertArrayEquals(huts.get(i), again.get(i));
            }
            assertEquals(spots(SettlementLayout.npcSpots(landmark)), spots(SettlementLayout.npcSpots(settlement(5, 5, seed, (int) (seed & 3)))));
        }
        assertEquals(Set.of(3, 4), counts);
    }

    @Test
    void tradingPostVisitorStandsBesideTheTerminal() {
        // 交易站（OutpostStructures.tradingPost）：屋子 |x|、|z| ≤ 3 是墙，终端在 (0, 2)，补给箱在 (±2, 2)，门在 (0, -3)
        for (int rotation = 0; rotation < 4; rotation++) {
            Landmark post = tradingPost(-640, 512, rotation);
            int[] spot = SettlementLayout.tradingPostSpot(post);
            int[] l = local(post, spot);
            assertTrue(Math.abs(l[0]) < 3 && Math.abs(l[1]) < 3, "站进了墙");
            assertEquals(1, chebyshev(l, new int[] {0, 2}), "离终端不是一格");
            assertNotEquals(0, l[0], "挡着门口到终端的路");
            assertTrue(chebyshev(l, new int[] {2, 2}) > 0 && chebyshev(l, new int[] {-2, 2}) > 0, "站在补给箱上");
            assertArrayEquals(spot, SettlementLayout.tradingPostSpot(tradingPost(-640, 512, rotation)));
        }
    }

    private static List<String> spots(List<int[]> spots) {
        List<String> list = new ArrayList<>();
        spots.forEach(s -> list.add(s[0] + "," + s[1]));
        return list;
    }
}
