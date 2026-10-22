package io.github.sweetcornna.universe.core.darkfog;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.sweetcornna.universe.core.darkfog.TurretTargeting.Candidate;

class TurretTargetingTest {
    private static Candidate at(double distance) {
        return new Candidate(distance, true, true, true, false, false);
    }

    @Test
    void picksTheNearest() {
        assertEquals(1, TurretTargeting.choose(TurretKind.GAUSS, List.of(at(20), at(5), at(12))));
        assertEquals(-1, TurretTargeting.choose(TurretKind.GAUSS, List.of()));
    }

    @Test
    void facilityAttackersComeFirst() {
        Candidate attacker = new Candidate(25, true, true, true, true, false);
        assertEquals(1, TurretTargeting.choose(TurretKind.GAUSS, List.of(at(3), attacker)));
    }

    @Test
    void skipsOutOfRangeHiddenAndFlyingForTheCannon() {
        Candidate far = new Candidate(3, false, true, true, false, false);
        Candidate hidden = new Candidate(4, true, false, true, false, false);
        Candidate flying = new Candidate(5, true, true, false, false, false);
        Candidate ok = at(30);
        assertEquals(3, TurretTargeting.choose(TurretKind.IMPLOSION, List.of(far, hidden, flying, ok)));
        // 高斯炮打飞的
        assertEquals(2, TurretTargeting.choose(TurretKind.GAUSS, List.of(far, hidden, flying, ok)));
    }

    @Test
    void onlyMissileAndPlasmaTurretsTakeHighAltitudeTargets() {
        Candidate lancer = new Candidate(4, true, true, false, true, false, true);
        Candidate raider = at(20);
        for (TurretKind kind : TurretKind.values()) {
            boolean antiAir = kind == TurretKind.MISSILE || kind == TurretKind.PLASMA;
            assertEquals(antiAir, TurretTargeting.eligible(kind, lancer), kind.name());
            // 打不到高空的就打地面那个，哪怕高空的那个正在炸设施、离得更近
            assertEquals(antiAir ? 0 : 1, TurretTargeting.choose(kind, List.of(lancer, raider)), kind.name());
        }
        // 现在的目标升到高空了：高斯炮换目标
        Candidate risen = new Candidate(6, true, true, false, false, true, true);
        assertEquals(1, TurretTargeting.choose(TurretKind.GAUSS, List.of(risen, raider)));
        assertEquals(0, TurretTargeting.choose(TurretKind.PLASMA, List.of(risen, raider)));
    }

    @Test
    void keepsTheCurrentTargetUnlessSomethingIsHittingFacilities() {
        Candidate current = new Candidate(20, true, true, true, false, true);
        assertEquals(1, TurretTargeting.choose(TurretKind.GAUSS, List.of(at(5), current)));
        Candidate attacker = new Candidate(30, true, true, true, true, false);
        assertEquals(2, TurretTargeting.choose(TurretKind.GAUSS, List.of(at(5), current, attacker)));
        // 现在的目标看不见了就换
        Candidate lost = new Candidate(20, true, false, true, false, true);
        assertEquals(0, TurretTargeting.choose(TurretKind.GAUSS, List.of(at(5), lost)));
    }
}
