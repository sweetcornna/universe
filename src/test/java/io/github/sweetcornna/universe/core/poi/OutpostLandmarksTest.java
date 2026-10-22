package io.github.sweetcornna.universe.core.poi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import io.github.sweetcornna.universe.core.tech.Grade;
import io.github.sweetcornna.universe.core.tech.ModuleHost;
import io.github.sweetcornna.universe.core.tech.TechModule;
import io.github.sweetcornna.universe.core.trade.OutpostStock;

/** 交易站、制造设施的规则（ADR-0026）。 */
class OutpostLandmarksTest {
    @Test
    void facilityOffersThreeDifferentHighGradePersonalModules() {
        int s = 0;
        int total = 0;
        for (long seed = 0; seed < 400; seed++) {
            List<TechModule> offers = FacilityModules.offers(seed, 0);
            assertEquals(FacilityModules.COUNT, offers.size());
            Set<Object> types = new HashSet<>();
            for (TechModule module : offers) {
                assertTrue(module.grade() == Grade.A || module.grade() == Grade.S, module.toString());
                assertTrue(module.type().host() != ModuleHost.SHIP, module.toString());
                types.add(module.type());
                total++;
                if (module.grade() == Grade.S) {
                    s++;
                }
            }
            assertEquals(FacilityModules.COUNT, types.size(), "种类要各不相同");
            // 同一台终端每次打开都一样
            assertEquals(offers, FacilityModules.offers(seed, 0));
        }
        assertEquals(FacilityModules.sChance(0), s / (double) total, 0.05);
    }

    @Test
    void rarerStarsGiveMoreSGrades() {
        assertTrue(FacilityModules.sChance(2) > FacilityModules.sChance(1));
        assertTrue(FacilityModules.sChance(1) > FacilityModules.sChance(0));
        assertEquals(FacilityModules.sChance(2), FacilityModules.sChance(9));
    }

    @Test
    void doorOpensAfterTenSecondsOfLaser() {
        DoorBreach door = new DoorBreach();
        for (int t = 0; t < DoorBreach.TICKS - 1; t++) {
            assertFalse(door.hit(1000 + t));
        }
        assertTrue(door.hit(1000 + DoorBreach.TICKS - 1));
        assertEquals(1F, door.progress());
    }

    @Test
    void doorHeatSurvivesOverheatPausesButNotWalkingAway() {
        DoorBreach door = new DoorBreach();
        long now = 0;
        // 照 80 刻、过热停 40 刻，再照：攒的不丢
        for (int t = 0; t < 80; t++) {
            door.hit(now++);
        }
        now += 40;
        door.hit(now);
        assertEquals(81F / DoorBreach.TICKS, door.progress(), 1e-6);
        assertFalse(door.stale(now + DoorBreach.FORGET));
        assertTrue(door.stale(now + DoorBreach.FORGET + 1));
        // 走开太久，从头来
        door.hit(now + DoorBreach.FORGET + 1);
        assertEquals(1F / DoorBreach.TICKS, door.progress(), 1e-6);
    }

    @Test
    void tradingPostsCarryTheEssentialsAndAboutFortyPercentOfTheRest() {
        int carried = 0;
        int total = 0;
        for (long seed = 1; seed <= 50; seed++) {
            assertTrue(OutpostStock.carries(seed, "universe:launch_fuel", true));
            for (int i = 0; i < 40; i++) {
                String item = "universe:item_" + i;
                boolean carries = OutpostStock.carries(seed, item, false);
                assertEquals(carries, OutpostStock.carries(seed, item, false), "同一个交易站同一样东西总是一样");
                carried += carries ? 1 : 0;
                total++;
            }
        }
        assertEquals(0.4, carried / (double) total, 0.05);
        // 不同的交易站摆的不一样
        int differ = 0;
        for (int i = 0; i < 40; i++) {
            String item = "universe:item_" + i;
            if (OutpostStock.carries(1, item, false) != OutpostStock.carries(2, item, false)) {
                differ++;
            }
        }
        assertTrue(differ > 5);
    }
}
