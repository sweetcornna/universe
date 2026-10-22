package io.github.sweetcornna.universe.core.fleet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.github.sweetcornna.universe.core.darkfog.DarkFogLoot;
import io.github.sweetcornna.universe.core.procgen.Rng;
import io.github.sweetcornna.universe.core.tech.Grade;

class HiveSortieTest {
    private static Frigate frigate(int id, FrigateType type, int... base) {
        return new Frigate(id, "HV-" + (100 + id), type, base, List.of(), 10, 0, false);
    }

    @Test
    void difficultyFollowsTheHiveLevel() {
        assertEquals(1, HiveSortie.difficulty(0));
        assertEquals(1, HiveSortie.difficulty(6));
        assertEquals(2, HiveSortie.difficulty(7));
        assertEquals(3, HiveSortie.difficulty(14));
        assertEquals(4, HiveSortie.difficulty(27));
        assertEquals(5, HiveSortie.difficulty(28));
        assertEquals(5, HiveSortie.difficulty(30));
        for (int level = 0; level <= 30; level++) {
            Expedition.Offer offer = HiveSortie.offer(level);
            assertEquals(Expedition.Kind.COMBAT, offer.kind());
            // 时长和同难度的远征一样
            assertEquals(Expedition.MINUTES[offer.difficulty() - 1], offer.minutes());
        }
    }

    @Test
    void everyEncounterIsCombat() {
        List<Frigate> team = List.of(frigate(0, FrigateType.EXPLORATION, 0, 40, 0, 0), frigate(1, FrigateType.TRADE, 0, 0, 0, 40));
        for (long seed = 0; seed < 50; seed++) {
            List<Expedition.Event> events = HiveSortie.resolve(3, team, Rng.of(seed));
            assertEquals(2 + 3, events.size());
            for (Expedition.Event event : events) {
                assertEquals(Area.COMBAT, event.area());
            }
            // 只看战斗：探索、贸易再强也打不赢
            assertEquals(0, HiveSortie.successes(events));
            assertEquals(events, HiveSortie.resolve(3, team, Rng.of(seed)));
        }
    }

    @Test
    void aStrongCombatTeamWinsEverySegment() {
        List<Frigate> team = List.of(frigate(0, FrigateType.COMBAT, 20, 0, 0, 0), frigate(1, FrigateType.COMBAT, 20, 0, 0, 0),
                frigate(2, FrigateType.COMBAT, 20, 0, 0, 0));
        assertEquals(1.0, HiveSortie.chance(team, 2), 1e-9);
        for (long seed = 0; seed < 50; seed++) {
            List<Expedition.Event> events = HiveSortie.resolve(2, team, Rng.of(seed));
            assertEquals(events.size(), HiveSortie.successes(events));
        }
        assertEquals(0, HiveSortie.chance(List.of(frigate(3, FrigateType.TRADE, 0, 0, 0, 30)), 1), 1e-9);
    }

    @Test
    void rewardsAreCreditsWithABonusForAClearSweep() {
        Expedition.Event won = new Expedition.Event(Area.COMBAT, true, -1, Expedition.Outcome.NONE);
        Expedition.Event lost = new Expedition.Event(Area.COMBAT, false, 0, Expedition.Outcome.DAMAGED);
        List<Expedition.Reward> sweep = HiveSortie.rewards(3, List.of(won, won, won), Rng.of(4));
        assertEquals(1, sweep.size());
        assertEquals(Expedition.CREDITS, sweep.get(0).key());
        // 三段各 900 起，全赢再加 1200
        assertTrue(sweep.get(0).count() >= 3 * 900 + 1200, "credits " + sweep.get(0).count());
        assertTrue(sweep.get(0).count() <= 3 * 1350 + 1200, "credits " + sweep.get(0).count());
        int partial = HiveSortie.rewards(3, List.of(won, lost, won), Rng.of(4)).get(0).count();
        assertTrue(partial < 2 * 1350 + 1, "no sweep bonus " + partial);
        assertTrue(HiveSortie.rewards(3, List.of(lost, lost), Rng.of(4)).isEmpty());
    }

    @Test
    void lootComesFromWhatWasDestroyed() {
        assertTrue(HiveSortie.loot(20, 0, 0, Rng.of(1)).isEmpty());
        Map<String, Integer> units = totals(HiveSortie.loot(20, 3, 0, Rng.of(1)));
        assertTrue(units.getOrDefault(DarkFogLoot.STONE, 0) >= 3);
        // 一座建筑按 5 份算：石头至少 1×份数
        Map<String, Integer> buildings = totals(HiveSortie.loot(20, 0, 2, Rng.of(1)));
        assertTrue(buildings.getOrDefault(DarkFogLoot.STONE, 0) >= 10);
        // 高等级的巢穴掉高档的零件
        var seen = new HashSet<String>();
        for (long seed = 0; seed < 40; seed++) {
            seen.addAll(totals(HiveSortie.loot(20, 4, 2, Rng.of(seed))).keySet());
        }
        assertTrue(seen.contains("universe:core_element") || seen.contains("universe:negentropy_singularity"), "parts " + seen);
        assertTrue(seen.contains(DarkFogLoot.MATRIX));
    }

    private static Map<String, Integer> totals(List<Expedition.Reward> rewards) {
        Map<String, Integer> map = new HashMap<>();
        for (Expedition.Reward reward : rewards) {
            map.merge(reward.key(), reward.count(), Integer::sum);
        }
        return map;
    }

    // ---- 舰队

    private static Fleet fleetWith(int count) {
        Fleet fleet = new Fleet();
        fleet.setFreighter(new Fleet.Freighter(0, 7, Grade.C));
        for (int i = 0; i < count; i++) {
            fleet.hire(frigate(i, FrigateType.COMBAT, 20, 0, 0, 0));
        }
        return fleet;
    }

    @Test
    void sortiesLeaveTheDailyMissionsAlone() {
        Fleet fleet = fleetWith(3);
        assertNull(fleet.canSortie(List.of(0, 1)));
        Fleet.Voyage voyage = fleet.sortie(12, HiveSortie.offer(9), List.of(0, 1), 100, Rng.of(5));
        assertTrue(voyage.sortie());
        assertEquals(12, voyage.hive());
        assertEquals(2, voyage.offer().difficulty());
        assertTrue(voyage.events().stream().allMatch(e -> e.area() == Area.COMBAT));
        assertTrue(fleet.taken().isEmpty());
        // 今天的远征任务照样能接，派出去的不能再派
        assertNull(fleet.canLaunch(0, 0, List.of(2)));
        assertEquals(Fleet.Problem.BUSY, fleet.canSortie(List.of(1, 2)));
        // 普通的远征不是清剿
        assertFalse(fleet.launch(0, 0, Expedition.offers(7, 0).get(0), List.of(2), 100, Rng.of(6)).sortie());
        assertEquals(Fleet.Problem.TOO_MANY_VOYAGES, fleetWithVoyages().canSortie(List.of(3)));
    }

    private static Fleet fleetWithVoyages() {
        Fleet fleet = fleetWith(4);
        for (int i = 0; i < Expedition.MAX_ACTIVE; i++) {
            fleet.sortie(i, HiveSortie.offer(0), List.of(i), 0, Rng.of(i));
        }
        return fleet;
    }

    @Test
    void hurryKeepsTheTargetAndClaimPaysCredits() {
        Fleet fleet = fleetWith(2);
        Fleet.Voyage voyage = fleet.sortie(3, HiveSortie.offer(0), List.of(0, 1), 0, Rng.of(8));
        fleet.hurry(10);
        Fleet.Voyage back = fleet.voyages().get(0);
        assertEquals(3, back.hive());
        assertTrue(back.done(10));
        assertEquals(voyage.events(), back.events());
        Fleet.Claim claim = fleet.claim(back);
        assertTrue(fleet.voyages().isEmpty());
        // 两艘 20 战斗打一星的巢穴，段段都赢：只有信用点
        assertEquals(1, claim.rewards().size());
        assertEquals(Expedition.CREDITS, claim.rewards().get(0).key());
        assertTrue(claim.lost().isEmpty());
    }
}
