package io.github.sweetcornna.universe.core.fleet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import io.github.sweetcornna.universe.core.procgen.Rng;
import io.github.sweetcornna.universe.core.ship.ShipMarket;

class FleetTest {
    @Test
    void damageSpreadsBeforeAnyoneSinks() {
        Expedition.Offer hopeless = new Expedition.Offer(Expedition.Kind.COMBAT, 5, 40);
        List<Frigate> team = new java.util.ArrayList<>();
        for (int i = 0; i < 4; i++) {
            team.add(new Frigate(i, "T-" + i, FrigateType.SUPPORT, new int[] {1, 1, 1, 1}, List.of(), 2, 0, false));
        }
        for (long seed = 0; seed < 50; seed++) {
            List<Expedition.Event> events = Expedition.resolve(hopeless, team, Rng.of(seed));
            // 七段全败：四艘各伤一次，剩下三次才沉
            assertEquals(4, events.stream().filter(e -> e.outcome() == Expedition.Outcome.DAMAGED).count());
            assertEquals(3, events.stream().filter(e -> e.outcome() == Expedition.Outcome.LOST).count());
        }
    }

    @Test
    void expectedMatchesTheMainArea() {
        Frigate strong = new Frigate(0, "S-0", FrigateType.INDUSTRIAL, new int[] {0, 0, 120, 0}, List.of(), 2, 0, false);
        Expedition.Offer industrial = new Expedition.Offer(Expedition.Kind.INDUSTRIAL, 3, 20);
        double expected = Expedition.expected(industrial, List.of(strong));
        // 工业段（七成多）全赢，别的段全输
        assertEquals(0.7 + 0.3 / 4, expected, 1e-9);
        assertEquals(0, Expedition.expected(industrial, List.of(new Frigate(1, "W-1", FrigateType.SUPPORT, new int[] {1, 1, 1, 1}, List.of(), 2,
                0, false))), 1e-9);
    }

    private static Frigate frigate(int id, FrigateType type, int... base) {
        return new Frigate(id, "AA-100", type, base, List.of(), type == FrigateType.SUPPORT ? 3 : 10, 0, false);
    }

    @Test
    void statsAddSpecialtySupportAndTraits() {
        Frigate combat = new Frigate(0, "KX-417", FrigateType.COMBAT, new int[] {2, 1, 0, 0},
                List.of(FrigateTrait.VETERAN_GUNNERS, FrigateTrait.FAULTY_SENSORS, FrigateTrait.LEAKY_FUEL_LINES), 10, 0, false);
        assertEquals(2 + Frigate.SPECIALIST + 4, combat.stat(Area.COMBAT));
        assertEquals(0, combat.stat(Area.EXPLORATION));
        assertEquals(13, combat.fuelCost());
        Frigate support = frigate(1, FrigateType.SUPPORT, 1, 1, 1, 1);
        assertEquals(1 + Frigate.SUPPORT_ALL, support.stat(Area.TRADE));
        assertEquals(3, support.fuelCost());
    }

    @Test
    void strongTeamsWinAndWeakTeamsGetHurt() {
        Expedition.Offer hard = new Expedition.Offer(Expedition.Kind.COMBAT, 5, 40);
        int strongWins = 0;
        int weakLosses = 0;
        for (long seed = 0; seed < 200; seed++) {
            List<Frigate> strong = List.of(frigate(0, FrigateType.COMBAT, 10, 0, 0, 0), frigate(1, FrigateType.COMBAT, 10, 0, 0, 0),
                    frigate(2, FrigateType.COMBAT, 10, 0, 0, 0), frigate(3, FrigateType.COMBAT, 10, 0, 0, 0));
            List<Expedition.Event> events = Expedition.resolve(hard, strong, Rng.of(seed));
            assertEquals(2 + 5, events.size());
            if (events.stream().filter(Expedition.Event::success).count() >= 5) {
                strongWins++;
            }
            List<Frigate> weak = List.of(frigate(0, FrigateType.TRADE, 0, 0, 0, 2));
            for (Expedition.Event event : Expedition.resolve(hard, weak, Rng.of(seed))) {
                if (event.outcome() == Expedition.Outcome.LOST) {
                    weakLosses++;
                    break;
                }
            }
        }
        // 偏门的遭遇（三成）专精战斗的队伍必输，所以五段以上赢的大约七成
        assertTrue(strongWins > 120, "strong wins " + strongWins);
        assertTrue(weakLosses > 150, "weak losses " + weakLosses);
    }

    @Test
    void aLostFrigateSitsOutTheRest() {
        Expedition.Offer hard = new Expedition.Offer(Expedition.Kind.TRADE, 5, 40);
        List<Frigate> team = List.of(frigate(0, FrigateType.COMBAT, 0, 0, 0, 0));
        List<Expedition.Event> events = Expedition.resolve(hard, team, Rng.of(1));
        boolean lost = false;
        for (Expedition.Event event : events) {
            if (lost) {
                assertEquals(-1, event.frigate());
                assertFalse(event.success());
            }
            if (event.outcome() == Expedition.Outcome.LOST) {
                lost = true;
            }
        }
        assertTrue(lost);
    }

    @Test
    void rewardsFollowTheEncounters() {
        Expedition.Offer offer = new Expedition.Offer(Expedition.Kind.INDUSTRIAL, 3, 22);
        List<Expedition.Event> events = List.of(new Expedition.Event(Area.INDUSTRY, true, -1, Expedition.Outcome.NONE),
                new Expedition.Event(Area.TRADE, true, -1, Expedition.Outcome.NONE));
        Map<String, Integer> rewards = new HashMap<>();
        for (Expedition.Reward r : Expedition.rewards(offer, events, Rng.of(3))) {
            rewards.put(r.key(), r.count());
        }
        assertTrue(rewards.get("universe:titanium_ingot") >= 6);
        // 贸易那段 700×3 起，全部成功再加 500×3
        assertTrue(rewards.get(Expedition.CREDITS) >= 2100 + 1500);
        assertTrue(Expedition.rewards(offer, List.of(new Expedition.Event(Area.INDUSTRY, false, 0, Expedition.Outcome.DAMAGED)), Rng.of(3))
                .isEmpty());
    }

    @Test
    void frigatesRankUpEveryFewTrips() {
        Frigate f = new Frigate(0, "AB-123", FrigateType.EXPLORATION, new int[] {0, 0, 0, 0}, List.of(FrigateTrait.FAULTY_SENSORS), 9, 0,
                false);
        Rng rng = Rng.of(2);
        int ranks = 0;
        for (int i = 0; i < 4 * Frigate.RANK_EVERY; i++) {
            if (f.returned(rng)) {
                ranks++;
            }
        }
        assertEquals(4, ranks);
        assertEquals(5, f.rank());
        int sum = 0;
        for (int v : f.baseStats()) {
            sum += v;
        }
        assertTrue(sum >= 8);
    }

    @Test
    void fuelAndOffers() {
        List<Frigate> team = List.of(frigate(0, FrigateType.COMBAT, 1, 0, 0, 0), frigate(1, FrigateType.SUPPORT, 1, 0, 0, 0));
        assertEquals(2, Expedition.fuelItems(team));
        List<Expedition.Offer> a = Expedition.offers(5, 10);
        assertEquals(Expedition.OFFERS_PER_DAY, a.size());
        assertEquals(a, Expedition.offers(5, 10));
        for (Expedition.Offer o : a) {
            assertTrue(o.difficulty() >= 1 && o.difficulty() <= 5);
        }
    }

    @Test
    void hiredFrigatesAreReasonable() {
        Set<FrigateType> types = new HashSet<>();
        for (long day = 0; day < 60; day++) {
            for (Frigate f : ShipMarket.frigates(11, day)) {
                types.add(f.type());
                long price = ShipMarket.frigatePrice(f);
                assertTrue(price >= 8000 && price <= ShipMarket.FRIGATE_MAX_PRICE);
                assertTrue(f.designation().matches("[A-Z]{2}-\\d{3}"));
            }
        }
        assertEquals(FrigateType.values().length, types.size());
    }

    // ---- 货船

    @Test
    void freighterHullHasAnOpenHangarAndFitsInSpace() {
        for (long seed = 0; seed < 20; seed++) {
            FreighterLayout.Exterior hull = FreighterLayout.exterior(seed);
            int[] b = hull.bounds();
            assertTrue(b[4] - b[1] < 30, "too tall");
            assertTrue(hull.cells().size() > 3000 && hull.cells().size() < 12000, "cells " + hull.cells().size());
            Set<Long> cells = new HashSet<>();
            for (FreighterLayout.Cell c : hull.cells()) {
                cells.add(key(c.x(), c.y(), c.z()));
            }
            // 机库凹口里是空的，口外那点在船外
            int[] min = hull.hangarMin();
            int[] max = hull.hangarMax();
            for (int x = min[0]; x <= max[0]; x++) {
                for (int y = min[1]; y <= max[1]; y++) {
                    for (int z = min[2]; z <= max[2]; z++) {
                        assertFalse(cells.contains(key(x, y, z)), "hangar blocked at " + x + "," + y + "," + z);
                    }
                }
            }
            double[] mouth = hull.mouth();
            assertTrue(mouth[0] < b[0]);
            assertTrue(hull.inHangar((min[0] + max[0]) / 2.0, (min[1] + max[1]) / 2.0, 0));
        }
    }

    @Test
    void interiorHasConsoleFleetTerminalPadsAndAnOpenDoor() {
        Map<FreighterInterior.Part, Integer> parts = new HashMap<>();
        int[] b = FreighterInterior.BOUNDS;
        for (int x = b[0]; x <= b[3]; x++) {
            for (int y = b[1]; y <= b[4]; y++) {
                for (int z = b[2]; z <= b[5]; z++) {
                    FreighterInterior.Part part = FreighterInterior.at(x, y, z);
                    if (part != null) {
                        parts.merge(part, 1, Integer::sum);
                    }
                }
            }
        }
        assertEquals(1, parts.get(FreighterInterior.Part.CONSOLE));
        assertEquals(1, parts.get(FreighterInterior.Part.FLEET_TERMINAL));
        assertTrue(parts.get(FreighterInterior.Part.PAD) > 40);
        assertTrue(parts.get(FreighterInterior.Part.GLASS) > 20);
        // 机库门是开的
        assertEquals(null, FreighterInterior.at(0, 3, -FreighterInterior.HANGAR_HALF - 1));
        // 从机库中间能走到控制台、舰队终端和甲板
        assertTrue(reachable(FreighterInterior.ARRIVAL, FreighterInterior.CONSOLE));
        assertTrue(reachable(FreighterInterior.ARRIVAL, FreighterInterior.FLEET));
        assertTrue(reachable(FreighterInterior.ARRIVAL, new int[] {0, 1, FreighterInterior.DECK_Z1 - 2}));
    }

    /** 在船舱的空格子里按六个方向走得到。 */
    private static boolean reachable(int[] from, int[] to) {
        ArrayDeque<int[]> queue = new ArrayDeque<>();
        Set<Long> seen = new HashSet<>();
        queue.add(from);
        seen.add(key(from[0], from[1], from[2]));
        int[][] dirs = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
        int[] b = FreighterInterior.BOUNDS;
        while (!queue.isEmpty()) {
            int[] p = queue.poll();
            if (p[0] == to[0] && p[1] == to[1] && p[2] == to[2]) {
                return true;
            }
            for (int[] d : dirs) {
                int x = p[0] + d[0];
                int y = p[1] + d[1];
                int z = p[2] + d[2];
                if (x < b[0] || x > b[3] || y < 1 || y > b[4] || z < b[2] + 1 || z > b[5]) {
                    continue;
                }
                FreighterInterior.Part part = FreighterInterior.at(x, y, z);
                boolean target = x == to[0] && y == to[1] && z == to[2];
                if ((part == null || target) && seen.add(key(x, y, z))) {
                    queue.add(new int[] {x, y, z});
                }
            }
        }
        return false;
    }

    @Test
    void derelictHasLootASafeAndAWayIn() {
        for (long seed = 0; seed < 20; seed++) {
            FreighterLayout.Derelict wreck = FreighterLayout.derelict(seed);
            Map<Long, FreighterLayout.Role> cells = new HashMap<>();
            int crates = 0;
            int[] safe = null;
            for (FreighterLayout.Cell c : wreck.all()) {
                cells.put(key(c.x(), c.y(), c.z()), c.role());
                if (c.role() == FreighterLayout.Role.CRATE) {
                    crates++;
                }
                if (c.role() == FreighterLayout.Role.SAFE) {
                    safe = new int[] {c.x(), c.y(), c.z()};
                }
            }
            assertTrue(crates >= 4, "crates " + crates);
            assertTrue(safe != null, "no safe");
            // 从机库中间走到保险箱旁边（只穿过空格子）
            int[] min = wreck.hull().hangarMin();
            int[] max = wreck.hull().hangarMax();
            int[] start = {(min[0] + max[0]) / 2, min[1], 0};
            assertTrue(walk(cells, start, safe), "safe unreachable, seed " + seed);
        }
    }

    private static boolean walk(Map<Long, FreighterLayout.Role> cells, int[] from, int[] to) {
        ArrayDeque<int[]> queue = new ArrayDeque<>();
        Set<Long> seen = new HashSet<>();
        queue.add(from);
        seen.add(key(from[0], from[1], from[2]));
        int[][] dirs = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
        int guard = 0;
        while (!queue.isEmpty() && guard++ < 400000) {
            int[] p = queue.poll();
            if (Math.abs(p[0] - to[0]) + Math.abs(p[1] - to[1]) + Math.abs(p[2] - to[2]) == 1) {
                return true;
            }
            for (int[] d : dirs) {
                int x = p[0] + d[0];
                int y = p[1] + d[1];
                int z = p[2] + d[2];
                // 只在船里找：不出船壳的包围盒
                if (Math.abs(x) > 14 || y < 1 || y > 7 || Math.abs(z) > 45) {
                    continue;
                }
                if (!cells.containsKey(key(x, y, z)) && seen.add(key(x, y, z))) {
                    queue.add(new int[] {x, y, z});
                }
            }
        }
        return false;
    }

    @Test
    void anchorsSitFarFromTheStationAndApart() {
        double[] station = {1000, 0, 0};
        double[] planet = {0, 0, 0};
        double[] a = FreighterRules.anchor(station, planet, 0);
        double[] b = FreighterRules.anchor(station, planet, 1);
        assertEquals(1000 + FreighterRules.ANCHOR_DISTANCE, a[0], 1e-9);
        double dx = a[0] - b[0];
        double dz = a[2] - b[2];
        assertTrue(Math.sqrt(dx * dx + dz * dz) >= FreighterRules.ANCHOR_SPACING - 1e-9);
    }

    private static long key(int x, int y, int z) {
        return ((long) (x + 512) << 40) | ((long) (y + 512) << 20) | (z + 512);
    }
}
