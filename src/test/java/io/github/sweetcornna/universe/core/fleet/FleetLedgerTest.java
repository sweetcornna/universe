package io.github.sweetcornna.universe.core.fleet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import io.github.sweetcornna.universe.core.galaxy.Galaxy;
import io.github.sweetcornna.universe.core.galaxy.GalaxyGenerator;
import io.github.sweetcornna.universe.core.galaxy.Planet;
import io.github.sweetcornna.universe.core.galaxy.StarSystem;
import io.github.sweetcornna.universe.core.procgen.Rng;
import io.github.sweetcornna.universe.core.space.SpaceLayout;
import io.github.sweetcornna.universe.core.space.StationLayout;
import io.github.sweetcornna.universe.core.tech.Grade;
import io.github.sweetcornna.universe.core.tech.ModuleHost;

class FleetLedgerTest {
    private static Fleet fleetWith(int frigates) {
        Fleet fleet = new Fleet();
        fleet.setFreighter(new Fleet.Freighter(0, 42, Grade.B));
        Rng rng = Rng.of(9);
        for (int i = 0; i < frigates; i++) {
            fleet.hire(Frigate.roll(i, rng));
        }
        return fleet;
    }

    @Test
    void hiringNeedsAFreighterAndStopsAtTwelve() {
        Fleet none = new Fleet();
        assertEquals(Fleet.Problem.NO_FREIGHTER, none.canHire());
        Fleet fleet = fleetWith(FreighterRules.MAX_FRIGATES);
        assertEquals(Fleet.Problem.FLEET_FULL, fleet.canHire());
        Set<Integer> ids = new HashSet<>();
        for (Frigate frigate : fleet.frigates()) {
            assertTrue(ids.add(frigate.id()), "duplicate id");
        }
        assertEquals(FreighterRules.MAX_FRIGATES, fleet.nextFrigate());
    }

    @Test
    void launchChecksTheTeam() {
        Fleet fleet = fleetWith(7);
        Expedition.Offer offer = new Expedition.Offer(Expedition.Kind.TRADE, 2, 16);
        assertEquals(Fleet.Problem.NO_TEAM, fleet.canLaunch(3, 0, List.of()));
        assertEquals(Fleet.Problem.TEAM_TOO_BIG, fleet.canLaunch(3, 0, List.of(0, 1, 2, 3, 4, 5)));
        assertEquals(Fleet.Problem.UNKNOWN_FRIGATE, fleet.canLaunch(3, 0, List.of(99)));
        fleet.frigate(6).setDamaged(true);
        assertEquals(Fleet.Problem.DAMAGED, fleet.canLaunch(3, 0, List.of(6)));
        assertNull(fleet.canLaunch(3, 0, List.of(0, 1)));
        fleet.launch(3, 0, offer, List.of(0, 1), 1000, Rng.of(1));
        assertEquals(Fleet.Problem.TAKEN, fleet.canLaunch(3, 0, List.of(2)));
        assertEquals(Fleet.Problem.BUSY, fleet.canLaunch(3, 1, List.of(1, 2)));
        assertTrue(fleet.busy(0));
        assertEquals(5, fleet.docked().size());
        fleet.launch(3, 1, offer, List.of(2), 1000, Rng.of(2));
        fleet.launch(3, 2, offer, List.of(3), 1000, Rng.of(3));
        assertEquals(Fleet.Problem.TOO_MANY_VOYAGES, fleet.canLaunch(4, 0, List.of(4)));
    }

    @Test
    void eventsRevealOverTime() {
        Fleet fleet = fleetWith(2);
        Expedition.Offer offer = new Expedition.Offer(Expedition.Kind.COMBAT, 3, 22);
        Fleet.Voyage voyage = fleet.launch(0, 0, offer, List.of(0, 1), 100, Rng.of(5));
        int n = voyage.events().size();
        assertEquals(5, n);
        assertEquals(0, voyage.revealed(100));
        int last = 0;
        for (long t = 100; t <= voyage.end(); t += 200) {
            int revealed = voyage.revealed(t);
            assertTrue(revealed >= last && revealed <= n);
            last = revealed;
        }
        assertFalse(voyage.done(voyage.end() - 1));
        long ticks = offer.ticks();
        assertEquals(0, voyage.revealed(100 + ticks / (n + 1) - 1));
        assertEquals(1, voyage.revealed(100 + ticks / (n + 1) + 1));
        assertEquals(n, voyage.revealed(100 + ticks * n / (n + 1)));
        assertEquals(n, voyage.revealed(voyage.end()));
        assertTrue(voyage.done(voyage.end()));
    }

    @Test
    void claimAppliesLossesDamageAndRanks() {
        int lostSeen = 0;
        int damagedSeen = 0;
        for (long seed = 0; seed < 60; seed++) {
            Fleet fleet = fleetWith(5);
            Expedition.Offer offer = new Expedition.Offer(Expedition.Kind.BALANCED, 5, 40);
            Fleet.Voyage voyage = fleet.launch(0, 0, offer, List.of(0, 1, 2, 3, 4), 0, Rng.of(seed));
            Fleet.Claim claim = fleet.claim(voyage);
            assertTrue(fleet.voyages().isEmpty());
            for (Frigate lost : claim.lost()) {
                assertNull(fleet.frigate(lost.id()));
            }
            for (Frigate damaged : claim.damaged()) {
                assertTrue(fleet.frigate(damaged.id()).damaged());
            }
            assertEquals(5 - claim.lost().size(), fleet.frigates().size());
            for (Frigate frigate : fleet.frigates()) {
                assertEquals(1, frigate.expeditions());
            }
            lostSeen += claim.lost().size();
            damagedSeen += claim.damaged().size();
        }
        // 五星的均衡任务对刚雇来的护卫舰很难：有损失
        assertTrue(lostSeen + damagedSeen > 0);
    }

    @Test
    void sameVoyageSameRewards() {
        Expedition.Offer offer = new Expedition.Offer(Expedition.Kind.EXPLORATION, 4, 30);
        Fleet a = fleetWith(3);
        Fleet b = fleetWith(3);
        Fleet.Claim ca = a.claim(a.launch(1, 2, offer, List.of(0, 1, 2), 0, Rng.of(77)));
        Fleet.Claim cb = b.claim(b.launch(1, 2, offer, List.of(0, 1, 2), 0, Rng.of(77)));
        assertEquals(ca.rewards(), cb.rewards());
    }

    @Test
    void hurryBringsVoyagesBackWithTheSameEvents() {
        Fleet fleet = fleetWith(3);
        Fleet.Voyage voyage = fleet.launch(0, 0, new Expedition.Offer(Expedition.Kind.TRADE, 2, 16), List.of(0, 1), 1000, Rng.of(4));
        assertFalse(fleet.voyages().getFirst().done(2000));
        fleet.hurry(2000);
        Fleet.Voyage back = fleet.voyages().getFirst();
        assertTrue(back.done(2000));
        assertEquals(voyage.events(), back.events());
        assertEquals(voyage.seed(), back.seed());
        assertEquals(voyage.team(), back.team());
    }

    @Test
    void chanceMatchesTheDice() {
        assertEquals(0, Expedition.chance(0, 1));
        assertEquals(1, Expedition.chance(1000, 5));
        int need = Expedition.required(3);
        assertEquals(0.5, Expedition.chance(need, 3), 1e-9);
        Rng rng = Rng.of(4);
        int strength = (int) Math.round(need / 1.1);
        int wins = 0;
        for (int i = 0; i < 20000; i++) {
            if (strength * rng.range(0.75, 1.25) >= need) {
                wins++;
            }
        }
        assertEquals(Expedition.chance(strength, 3), wins / 20000.0, 0.02);
    }

    @Test
    void repairAndDismiss() {
        Fleet fleet = fleetWith(2);
        assertFalse(fleet.repair(0));
        fleet.frigate(0).setDamaged(true);
        assertTrue(fleet.repair(0));
        assertFalse(fleet.frigate(0).damaged());
        fleet.launch(0, 0, new Expedition.Offer(Expedition.Kind.TRADE, 1, 10), List.of(1), 0, Rng.of(1));
        assertFalse(fleet.dismiss(1));
        assertTrue(fleet.dismiss(0));
        assertEquals(1, fleet.frigates().size());
    }

    @Test
    void oldTakenOffersArePruned() {
        Fleet fleet = fleetWith(4);
        Expedition.Offer offer = new Expedition.Offer(Expedition.Kind.TRADE, 1, 10);
        fleet.claim(fleet.launch(1, 0, offer, List.of(0), 0, Rng.of(1)));
        fleet.claim(fleet.launch(5, 1, offer, List.of(1), 0, Rng.of(2)));
        assertEquals(Set.of("5/1"), fleet.taken());
    }

    @Test
    void rewardModulesAreShipModules() {
        Rng rng = Rng.of(3);
        int good = 0;
        for (int i = 0; i < 200; i++) {
            var module = Fleet.rewardModule(5, rng);
            assertEquals(ModuleHost.SHIP, module.type().host());
            if (module.grade().ordinal() >= Grade.A.ordinal()) {
                good++;
            }
        }
        assertTrue(good > 20, "A/S modules " + good);
    }

    @Test
    void frigateModelsFitTheirBerths() {
        FreighterLayout.Exterior exterior = FreighterLayout.exterior(5);
        Set<Long> used = new HashSet<>();
        for (FreighterLayout.Cell cell : exterior.cells()) {
            used.add(key(cell.x(), cell.y(), cell.z()));
        }
        for (FrigateType type : FrigateType.values()) {
            List<FreighterLayout.Cell> cells = FrigateModel.cells(type);
            assertTrue(cells.size() > 20, type + " too small");
            assertTrue(cells.stream().anyMatch(c -> c.role() == FreighterLayout.Role.ENGINE), type + " has no engine");
            for (FreighterLayout.Cell cell : cells) {
                assertTrue(Math.abs(cell.x()) <= FrigateModel.HALF_WIDTH && Math.abs(cell.z()) <= FrigateModel.HALF_LENGTH, type + " too big");
            }
        }
        // 十二个泊位互不重叠，也不碰货船
        for (int slot = 0; slot < FreighterRules.MAX_FRIGATES; slot++) {
            int[] berth = FrigateModel.berth(exterior, slot);
            for (FreighterLayout.Cell cell : FrigateModel.cells(FrigateType.values()[slot % FrigateType.values().length])) {
                assertTrue(used.add(key(berth[0] + cell.x(), berth[1] + cell.y(), berth[2] + cell.z())), "berth " + slot + " overlaps");
            }
        }
    }

    @Test
    void anchorsStayClearOfPlanetsInEverySystem() {
        Galaxy galaxy = GalaxyGenerator.generate(20261016L, 64);
        int checked = 0;
        for (StarSystem system : galaxy.systems()) {
            Optional<StationLayout.Site> site = StationLayout.site(system);
            Optional<Planet> planet = StationLayout.anchor(system);
            if (site.isEmpty() || planet.isEmpty()) {
                continue;
            }
            List<SpaceLayout.Body> bodies = SpaceLayout.of(system);
            SpaceLayout.Body body = SpaceLayout.find(bodies, planet.get().id()).orElseThrow();
            double[] station = site.get().center();
            for (int slot = 0; slot < 4; slot++) {
                double[] spot = FreighterRules.anchor(station, new double[] {body.x(), body.y(), body.z()}, slot, bodies,
                        SpaceLayout.starRadius(system));
                if (FreighterRules.clear(spot, bodies, SpaceLayout.starRadius(system))) {
                    checked++;
                }
            }
        }
        // 每个系都能找到地方
        assertTrue(checked >= 250, "clear anchors " + checked);
    }

    private static long key(int x, int y, int z) {
        return ((long) (x + 512) << 40) | ((long) (y + 512) << 20) | (z + 512);
    }
}
