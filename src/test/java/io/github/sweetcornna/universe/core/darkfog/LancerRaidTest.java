package io.github.sweetcornna.universe.core.darkfog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class LancerRaidTest {
    private static LancerRaid pastWarning(int launched) {
        LancerRaid raid = new LancerRaid(launched);
        for (int i = 0; i < LancerRaid.WARNING_TICKS; i++) {
            raid.tick(0, 0, 0);
        }
        return raid;
    }

    @Test
    void warnsThenDives() {
        LancerRaid raid = new LancerRaid(6);
        assertEquals(LancerRaid.WARNING_TICKS, raid.warningLeft());
        for (int i = 0; i < LancerRaid.WARNING_TICKS - 1; i++) {
            assertEquals(LancerRaid.Phase.WARNING, raid.tick(0, 0, 0));
        }
        assertEquals(1, raid.warningLeft());
        assertEquals(LancerRaid.Phase.DIVE, raid.tick(0, 0, 0));
        assertEquals(0, raid.warningLeft());
    }

    @Test
    void startsBombingOnceEveryoneArrivesOrTimeRunsOut() {
        LancerRaid raid = pastWarning(6);
        assertEquals(LancerRaid.Phase.DIVE, raid.tick(6, 3, 36));
        assertEquals(LancerRaid.Phase.BOMB, raid.tick(6, 6, 36));

        LancerRaid slow = pastWarning(6);
        for (int i = 0; i < LancerRaid.DIVE_MAX_TICKS - 1; i++) {
            assertEquals(LancerRaid.Phase.DIVE, slow.tick(6, 2, 36));
        }
        assertEquals(LancerRaid.Phase.BOMB, slow.tick(6, 2, 36));
    }

    @Test
    void leavesWhenTheBombsRunOutOrTimeIsUp() {
        LancerRaid raid = pastWarning(4);
        raid.tick(4, 4, 24);
        assertEquals(LancerRaid.Phase.BOMB, raid.tick(4, 4, 3));
        assertEquals(LancerRaid.Phase.LEAVE, raid.tick(4, 4, 0));

        LancerRaid long_ = pastWarning(4);
        long_.tick(4, 4, 24);
        for (int i = 0; i < LancerRaid.BOMB_MAX_TICKS - 1; i++) {
            assertEquals(LancerRaid.Phase.BOMB, long_.tick(4, 4, 24));
        }
        assertEquals(LancerRaid.Phase.LEAVE, long_.tick(4, 4, 24));
    }

    @Test
    void retreatsWhenOnlyOneOrTwoAreLeft() {
        assertTrue(LancerRaid.retreat(8, 2));
        assertFalse(LancerRaid.retreat(8, 3));
        assertFalse(LancerRaid.retreat(2, 1), "a raid of two fights to the end");
        LancerRaid raid = pastWarning(8);
        raid.tick(8, 8, 48);
        assertEquals(LancerRaid.Phase.LEAVE, raid.tick(2, 2, 40));
    }

    @Test
    void endsWhenNobodyIsLeft() {
        LancerRaid raid = pastWarning(5);
        raid.tick(5, 5, 30);
        assertEquals(LancerRaid.Phase.OVER, raid.tick(0, 0, 0));

        LancerRaid leaving = pastWarning(5);
        leaving.tick(5, 5, 30);
        leaving.tick(5, 5, 0);
        assertEquals(LancerRaid.Phase.LEAVE, leaving.phase());
        assertEquals(LancerRaid.Phase.LEAVE, leaving.tick(3, 0, 0));
        assertEquals(LancerRaid.Phase.OVER, leaving.tick(0, 0, 0));

        LancerRaid stuck = pastWarning(5);
        stuck.tick(5, 5, 30);
        stuck.tick(5, 5, 0);
        for (int i = 0; i < LancerRaid.LEAVE_MAX_TICKS - 1; i++) {
            stuck.tick(5, 0, 0);
        }
        assertEquals(LancerRaid.Phase.OVER, stuck.tick(5, 0, 0));
    }

    @Test
    void abortSkipsTheWarningOrSendsThemHome() {
        LancerRaid warning = new LancerRaid(3);
        warning.abort();
        assertEquals(LancerRaid.Phase.OVER, warning.phase());
        LancerRaid diving = pastWarning(3);
        diving.abort();
        assertEquals(LancerRaid.Phase.LEAVE, diving.phase());
    }

    @Test
    void skippingTheWarningLaunchesNextTick() {
        LancerRaid raid = new LancerRaid(3);
        raid.tick(0, 0, 0);
        raid.skipWarning();
        assertEquals(LancerRaid.Phase.DIVE, raid.tick(0, 0, 0));
    }

    @Test
    void bombSplashFallsOffToHalfAtTheEdge() {
        assertEquals(1, LancerRaid.splash(0), 1e-9);
        assertEquals(0.5, LancerRaid.splash(LancerRaid.BOMB_RADIUS), 1e-9);
        assertEquals(0, LancerRaid.splash(LancerRaid.BOMB_RADIUS + 0.1), 1e-9);
    }

    @Test
    void altitudeBands() {
        assertTrue(LancerRaid.highAltitude(LancerRaid.BOMB_HEIGHT), "bombing lancers count as high altitude");
        assertFalse(LancerRaid.highAltitude(6));
        assertTrue(LancerRaid.cruiseHeight(LancerRaid.Phase.LEAVE) > LancerRaid.LEAVE_HEIGHT);
        assertEquals(LancerRaid.BOMB_HEIGHT, LancerRaid.cruiseHeight(LancerRaid.Phase.DIVE));
    }

    @Test
    void bombersStayOutsideShieldDomes() {
        assertEquals(64 + LancerRaid.BOMB_HEIGHT, LancerRaid.bombAltitude(64, Double.NaN));
        // 低矮的穹顶顶不到轰炸高度：照常
        assertEquals(64 + LancerRaid.BOMB_HEIGHT, LancerRaid.bombAltitude(64, 70));
        // 高的穹顶：停在外壳以上
        assertEquals(230 + LancerRaid.DOME_CLEARANCE, LancerRaid.bombAltitude(64, 230));
    }
}
