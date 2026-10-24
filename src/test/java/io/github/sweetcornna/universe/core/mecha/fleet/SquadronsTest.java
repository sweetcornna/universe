package io.github.sweetcornna.universe.core.mecha.fleet;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import io.github.sweetcornna.universe.core.mecha.fleet.Squadrons.Candidate;

class SquadronsTest {
    @Test
    void unitsFollowTheTable() {
        assertEquals(5, FleetUnitKind.values().length);
        assertFalse(FleetUnitKind.PROTOTYPE.space());
        assertTrue(FleetUnitKind.CORVETTE.space());
        assertEquals(40, FleetUnitKind.PROTOTYPE.maxHealth());
        assertEquals(2000, FleetUnitKind.DESTROYER.maxHealth());
        assertEquals(3, FleetUnitKind.PROTOTYPE.dps(), 1e-9);
        assertEquals(20 * 5 / 15.0, FleetUnitKind.PRECISION_DRONE.dps(), 1e-9);
        assertEquals(16, FleetUnitKind.CORVETTE.dps(), 1e-9);
        assertEquals(1.5, FleetUnitKind.ATTACK_DRONE.splash());
        assertEquals(0, FleetUnitKind.CORVETTE.splash());
        assertEquals(FleetUnitKind.Weapon.PLASMA, FleetUnitKind.DESTROYER.weapon());
        assertEquals(FleetUnitKind.DESTROYER, FleetUnitKind.byId("destroyer").orElseThrow());
        assertTrue(FleetUnitKind.byId("frigate").isEmpty());
        // 满编的原型机编队两三秒打掉一架没升级的突袭者（30 点）
        double squadron = FleetUnitKind.PROTOTYPE.dps() * Squadrons.GROUND_SLOTS;
        assertTrue(30 / squadron >= 1.5 && 30 / squadron <= 3, "一个编队 " + squadron + " 点每秒");
    }

    @Test
    void repairTakesFiftyJoulesPerPointAndAboutFiftySeconds() {
        assertEquals(2, FleetUnitKind.PROTOTYPE.repairPerSecond());
        assertEquals(40, FleetUnitKind.DESTROYER.repairPerSecond());
        assertEquals(2000 * 50, FleetUnitKind.DESTROYER.fullRepairJoules(), 1e-9);
        assertEquals(2, Squadrons.repair(10, 10_000, 2));
        assertEquals(1, Squadrons.repair(1, 10_000, 2));
        // 电只够修 3 点
        assertEquals(3, Squadrons.repair(40, 170, 40));
        assertEquals(0, Squadrons.repair(0, 10_000, 40));
        assertEquals(0, Squadrons.repair(5, 49, 40));
    }

    @Test
    void squadronsOpenWithResearch() {
        assertEquals(0, Squadrons.groundOpen(false, 2));
        assertEquals(1, Squadrons.groundOpen(true, 0));
        assertEquals(3, Squadrons.groundOpen(true, 2));
        assertEquals(3, Squadrons.groundOpen(true, 7));
        assertEquals(0, Squadrons.spaceOpen(false, 1));
        assertEquals(2, Squadrons.spaceOpen(true, 1));
        assertEquals(2, Squadrons.spaceOpen(true, 4));
        assertTrue(Squadrons.open(0, 1, 0));
        assertFalse(Squadrons.open(1, 1, 0));
        assertFalse(Squadrons.open(Squadrons.GROUND_MAX, 3, 0));
        assertTrue(Squadrons.open(Squadrons.GROUND_MAX + 1, 0, 2));
        assertEquals(6, Squadrons.slots(2));
        assertEquals(3, Squadrons.slots(3));
        assertEquals(24, Squadrons.TOTAL_SLOTS);
    }

    @Test
    void flatIndicesCoverEverySlotOnce() {
        Set<Integer> seen = new HashSet<>();
        for (int squadron = 0; squadron < Squadrons.COUNT; squadron++) {
            for (int slot = 0; slot < Squadrons.slots(squadron); slot++) {
                int index = Squadrons.flatIndex(squadron, slot);
                assertTrue(seen.add(index));
                assertArrayEquals(new int[] {squadron, slot}, Squadrons.unflatten(index));
            }
        }
        assertEquals(Squadrons.TOTAL_SLOTS, seen.size());
    }

    @Test
    void groundFormationIsAnArcBehind() {
        // 朝南（偏航 0）：背后是北，z 为负
        for (int slot = 0; slot < Squadrons.GROUND_SLOTS; slot++) {
            double[] p = Squadrons.formation(0, slot, 0, 1);
            assertEquals(3, Math.hypot(p[0], p[2]), 1e-9);
            assertEquals(2, p[1], 1e-9);
            assertTrue(p[2] < 0, "第 " + slot + " 架跑到前面去了");
        }
        // 左右对称
        double[] left = Squadrons.formation(0, 0, 0, 1);
        double[] right = Squadrons.formation(0, Squadrons.GROUND_SLOTS - 1, 0, 1);
        assertEquals(-left[0], right[0], 1e-9);
        assertEquals(left[2], right[2], 1e-9);
        // 第二个编队离得远、高一点
        double[] outer = Squadrons.formation(1, 2, 0, 1);
        assertEquals(4.5, Math.hypot(outer[0], outer[2]), 1e-9);
        assertEquals(2.5, outer[1], 1e-9);
        // 朝东（偏航 -90）：背后是西
        double[] east = Squadrons.formation(0, 2, -90, 1);
        assertTrue(east[0] < -2, "朝东时没排在西边：" + east[0]);
    }

    @Test
    void spaceFormationTrailsBehind() {
        double[] middle = Squadrons.formation(Squadrons.GROUND_MAX, 1, 0, 1);
        assertEquals(0, middle[0], 1e-9);
        assertEquals(-10, middle[2], 1e-9);
        double[] second = Squadrons.formation(Squadrons.GROUND_MAX + 1, 1, 0, 2);
        assertEquals(-36, second[2], 1e-9);
        assertTrue(second[1] > middle[1]);
        double[] wing = Squadrons.formation(Squadrons.GROUND_MAX, 0, 0, 1);
        assertEquals(7, Math.abs(wing[0]), 1e-9);
    }

    @Test
    void pickPrefersWhatTheMechaIsShooting() {
        List<Candidate> candidates = List.of(
                new Candidate(10, 3, false, false),
                new Candidate(12, 8, false, true),
                new Candidate(30, 1, false, false),
                new Candidate(5, 2, true, false));
        assertEquals(1, Squadrons.pick(candidates, 24, false));
        // 没有正在打的：离自己最近的，建筑要开关
        List<Candidate> plain = List.of(candidates.get(0), candidates.get(2), candidates.get(3));
        assertEquals(0, Squadrons.pick(plain, 24, false));
        assertEquals(2, Squadrons.pick(plain, 24, true));
        // 离机甲太远的不打
        assertEquals(-1, Squadrons.pick(List.of(candidates.get(2)), 24, true));
        assertEquals(-1, Squadrons.pick(List.of(), 24, true));
    }

    @Test
    void engagePointStaysOnTheLeash() {
        double[] owner = {0, 64, 0};
        double[] near = Squadrons.engagePoint(owner, new double[] {10, 64, 0}, 6, 0, 2, 48);
        // 站在目标和机甲之间
        assertEquals(4, near[0], 1e-9);
        assertEquals(66, near[1], 1e-9);
        double[] far = Squadrons.engagePoint(owner, new double[] {100, 64, 0}, 6, 0, 0, 48);
        assertTrue(Math.abs(far[0]) <= 48 * 0.9 + 1e-9, "跑出拴绳了：" + far[0]);
    }

    @Test
    void catchUpDoublesWhenFarBehind() {
        assertEquals(0.45, Squadrons.catchUp(0.45, 1), 1e-9);
        assertEquals(0.9, Squadrons.catchUp(0.45, 30), 1e-9);
        // 三架原型机 15、攻击无人机 12、炮艇 50、精准无人机 8
        assertEquals(85, Squadrons.idleJoules(List.of(FleetUnitKind.PROTOTYPE, FleetUnitKind.PROTOTYPE, FleetUnitKind.ATTACK_DRONE,
                FleetUnitKind.CORVETTE, FleetUnitKind.PRECISION_DRONE, FleetUnitKind.PROTOTYPE)), 1e-9);
    }
}
