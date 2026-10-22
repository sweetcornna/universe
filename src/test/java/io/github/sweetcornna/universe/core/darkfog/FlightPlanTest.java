package io.github.sweetcornna.universe.core.darkfog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

import org.junit.jupiter.api.Test;

import io.github.sweetcornna.universe.core.galaxy.Galaxy;
import io.github.sweetcornna.universe.core.galaxy.GalaxyGenerator;
import io.github.sweetcornna.universe.core.galaxy.StarSystem;
import io.github.sweetcornna.universe.core.procgen.Rng;

class FlightPlanTest {
    private static final Galaxy GALAXY = GalaxyGenerator.generate(43, GalaxyGenerator.DEFAULT_SYSTEM_COUNT);

    @Test
    void relayGoesThroughThreePhases() {
        Flight flight = FlightPlan.relay(1, 3, "universe:s3/p1", 5, 100, 0, -200, 1000, Rng.of(9));
        assertEquals(Flight.Phase.DEPART, flight.phase(1000));
        assertEquals(Flight.Phase.CRUISE, flight.phase(1000 + FlightPlan.RELAY_DEPART_TICKS));
        long cruise = flight.arriveStart() - flight.departEnd();
        assertTrue(cruise >= FlightPlan.RELAY_CRUISE_MIN_TICKS && cruise <= FlightPlan.RELAY_CRUISE_MAX_TICKS, "cruise " + cruise);
        assertEquals(Flight.Phase.ARRIVE, flight.phase(flight.arriveStart()));
        assertEquals(FlightPlan.RELAY_DESCENT_TICKS, flight.end() - flight.arriveStart());
        assertEquals(Flight.Phase.DONE, flight.phase(flight.end()));
        assertEquals(0, flight.progress(1000), 1e-9);
        assertEquals(1, flight.progress(flight.end() + 50), 1e-9);
    }

    @Test
    void seedsTakeLongerTheFartherTheyGo() {
        assertEquals(12 * 1200, FlightPlan.seedCruiseTicks(0.5));
        assertEquals((8 + 3 * 6) * 1200, FlightPlan.seedCruiseTicks(6));
        assertEquals(60 * 1200, FlightPlan.seedCruiseTicks(40));
        assertTrue(FlightPlan.seedCruiseTicks(8) > FlightPlan.seedCruiseTicks(5));
    }

    @Test
    void seedPicksNearestSystemWithoutHive() {
        List<StarSystem> systems = GALAXY.systems();
        int from = GalaxyGenerator.HOME_SYSTEM;
        OptionalInt first = FlightPlan.seedTarget(systems, from, system -> false);
        assertTrue(first.isPresent());
        // 和挨个比出来的最近的一样
        StarSystem origin = systems.get(from);
        StarSystem nearest = systems.stream().filter(s -> s.index() != from).min((a, b) -> Double.compare(origin.distanceTo(a), origin.distanceTo(b)))
                .orElseThrow();
        assertEquals(nearest.index(), first.getAsInt());
        // 那边有了巢穴就去下一个
        OptionalInt second = FlightPlan.seedTarget(systems, from, system -> system == first.getAsInt());
        assertTrue(second.isPresent());
        assertNotEquals(first.getAsInt(), second.getAsInt());
        assertTrue(origin.distanceTo(systems.get(second.getAsInt())) >= origin.distanceTo(nearest));
        // 都有了就哪也不去
        assertFalse(FlightPlan.seedTarget(systems, from, system -> true).isPresent());
    }

    @Test
    void seedStaysWithinRange() {
        List<StarSystem> systems = GALAXY.systems();
        for (StarSystem origin : systems) {
            OptionalInt target = FlightPlan.seedTarget(systems, origin.index(), system -> false);
            target.ifPresent(t -> assertTrue(origin.distanceTo(systems.get(t)) <= FlightPlan.SEED_RANGE));
        }
    }

    @Test
    void landingSiteKeepsAwayFromBasesAndShields() {
        List<int[]> bases = new ArrayList<>();
        bases.add(new int[] {300, 0});
        bases.add(new int[] {-300, 0});
        for (long seed = 0; seed < 40; seed++) {
            Optional<int[]> site = FlightPlan.landingSite(0, 0, Rng.of(seed), bases, 200, (x, z) -> z > 0);
            if (site.isEmpty()) {
                continue;
            }
            int[] s = site.get();
            double distance = Math.hypot(s[0], s[1]);
            assertTrue(distance >= FlightPlan.LANDING_MIN - 1 && distance <= FlightPlan.LANDING_MAX + 1, "distance " + distance);
            assertTrue(FlightPlan.clear(s[0], s[1], bases, 200), "too close to a base");
            assertTrue(s[1] + 0.5 <= 0, "landed under a shield");
        }
    }

    @Test
    void landingFailsWhenEverythingIsShielded() {
        assertTrue(FlightPlan.landingSite(0, 0, Rng.of(1), List.of(), 100, (x, z) -> true).isEmpty());
    }

    @Test
    void retryPushesTheDescentBack() {
        Flight flight = FlightPlan.relay(2, 0, "universe:s0/p2", 0, 0, 0, 0, 0, Rng.of(3));
        long now = flight.arriveStart() + 10;
        flight.land("universe:s0/p2", 10, 20, -1);
        flight.retryArrival(now, FlightPlan.LANDING_RETRY_TICKS);
        assertEquals(Flight.Phase.CRUISE, flight.phase(now));
        assertFalse(flight.siteChosen());
        assertEquals(1, flight.attempts());
        assertEquals(now + FlightPlan.LANDING_RETRY_TICKS, flight.arriveStart());
        assertEquals(FlightPlan.RELAY_DESCENT_TICKS, flight.end() - flight.arriveStart());
    }

    @Test
    void skippingJumpsToTheArrival() {
        Flight flight = FlightPlan.seed(3, 0, 5, 6, 2, 0, 0, 0, 0);
        flight.skipTo(Flight.Phase.ARRIVE, 100);
        assertEquals(Flight.Phase.ARRIVE, flight.phase(100));
        assertEquals(FlightPlan.SEED_ARRIVE_TICKS, flight.end() - flight.arriveStart());
        flight.skipTo(Flight.Phase.DONE, 150);
        assertEquals(Flight.Phase.DONE, flight.phase(150));
    }

    @Test
    void descentSlowsNearTheGround() {
        assertEquals(FlightPlan.DESCENT_HEIGHT, FlightPlan.descentHeight(0), 1e-9);
        assertEquals(0, FlightPlan.descentHeight(1), 1e-9);
        double early = FlightPlan.descentHeight(0) - FlightPlan.descentHeight(0.1);
        double late = FlightPlan.descentHeight(0.9) - FlightPlan.descentHeight(1);
        assertTrue(early > late * 5);
    }
}
