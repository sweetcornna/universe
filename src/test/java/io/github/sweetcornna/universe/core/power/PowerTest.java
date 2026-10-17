package io.github.sweetcornna.universe.core.power;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

import io.github.sweetcornna.universe.core.power.PowerSystem.TowerSpec;

class PowerTest {
    private static final TowerSpec TOWER = new TowerSpec(12, 8);

    /** 测试用的设备：固定的需求、发电、充放电上限，记下最后一次结算。 */
    private static final class Probe implements PowerDevice {
        long demand;
        long generation;
        long chargeLimit;
        long dischargeLimit;
        double satisfaction = -1;
        double load = -1;
        long stored;

        static Probe consumer(long demand) {
            Probe p = new Probe();
            p.demand = demand;
            return p;
        }

        static Probe generator(long generation) {
            Probe p = new Probe();
            p.generation = generation;
            return p;
        }

        static Probe accumulator(long charge, long discharge) {
            Probe p = new Probe();
            p.chargeLimit = charge;
            p.dischargeLimit = discharge;
            return p;
        }

        @Override
        public long demand() {
            return this.demand;
        }

        @Override
        public long generation() {
            return this.generation;
        }

        @Override
        public long chargeLimit() {
            return this.chargeLimit;
        }

        @Override
        public long dischargeLimit() {
            return this.dischargeLimit;
        }

        @Override
        public void settle(double satisfaction, double load, long stored) {
            this.satisfaction = satisfaction;
            this.load = load;
            this.stored = stored;
        }
    }

    @Test
    void towersInRangeJoinAndABridgeMergesGrids() {
        PowerSystem power = new PowerSystem();
        power.addTower(1, 0, 64, 0, TOWER);
        power.addTower(2, 10, 64, 0, TOWER);
        power.addTower(3, 40, 64, 0, TOWER);
        assertSame(power.gridOfTower(1), power.gridOfTower(2));
        assertNotSame(power.gridOfTower(1), power.gridOfTower(3));
        assertEquals(2, power.grids().size());
        power.addTower(4, 20, 64, 0, TOWER);
        power.addTower(5, 30, 64, 0, TOWER);
        assertEquals(1, power.grids().size());
        assertEquals(5, power.gridOfTower(3).towerCount());
    }

    @Test
    void removingAMiddleTowerSplitsTheGrid() {
        PowerSystem power = new PowerSystem();
        for (int i = 0; i < 5; i++) {
            power.addTower(i, i * 10, 64, 0, TOWER);
        }
        power.addDevice(100, 2, 64, 3, Probe.consumer(10));
        power.addDevice(101, 41, 64, 1, Probe.consumer(10));
        assertSame(power.gridOf(100), power.gridOf(101));
        power.removeTower(2);
        assertEquals(2, power.grids().size());
        assertNotSame(power.gridOf(100), power.gridOf(101));
        assertEquals(2, power.gridOf(100).towerCount());
        assertEquals(1, power.gridOf(100).deviceCount());
        power.addTower(2, 20, 64, 0, TOWER);
        assertSame(power.gridOf(100), power.gridOf(101));
        assertEquals(2, power.gridOf(100).deviceCount());
    }

    @Test
    void devicesFollowCoverage() {
        PowerSystem power = new PowerSystem();
        power.addDevice(1, 30, 64, 0, Probe.consumer(5));
        assertNull(power.gridOf(1));
        power.addTower(10, 25, 64, 0, TOWER);
        assertNotNull(power.gridOf(1));
        // 上下也有范围
        power.addDevice(2, 25, 80, 0, Probe.consumer(5));
        assertNull(power.gridOf(2));
        // 塔拆了换到另一座还覆盖它的塔上
        power.addTower(11, 36, 64, 0, TOWER);
        power.removeTower(10);
        assertSame(power.gridOfTower(11), power.gridOf(1));
        power.removeTower(11);
        assertNull(power.gridOf(1));
        power.removeDevice(1);
        assertTrue(power.grids().isEmpty());
    }

    @Test
    void surplusChargesAccumulatorsAndGeneratorsThrottle() {
        PowerSystem power = new PowerSystem();
        power.addTower(1, 0, 64, 0, TOWER);
        Probe motor = Probe.consumer(300);
        Probe turbine = Probe.generator(1000);
        Probe battery = Probe.accumulator(200, 500);
        power.addDevice(10, 1, 64, 0, motor);
        power.addDevice(11, 2, 64, 0, turbine);
        power.addDevice(12, 3, 64, 0, battery);
        power.tick();
        assertEquals(1, motor.satisfaction, 1e-9);
        assertEquals(200, battery.stored);
        assertEquals(0.5, turbine.load, 1e-9);
        PowerSystem.Grid grid = power.gridOf(10);
        assertEquals(300, grid.supplied());
        assertEquals(200, grid.charged());
    }

    @Test
    void deficitDrainsAccumulatorsThenThrottlesEveryone() {
        PowerSystem power = new PowerSystem();
        power.addTower(1, 0, 64, 0, TOWER);
        Probe a = Probe.consumer(600);
        Probe b = Probe.consumer(400);
        Probe turbine = Probe.generator(500);
        Probe battery = Probe.accumulator(100, 300);
        power.addDevice(10, 1, 64, 0, a);
        power.addDevice(11, 1, 64, 1, b);
        power.addDevice(12, 2, 64, 0, turbine);
        power.addDevice(13, 3, 64, 0, battery);
        power.tick();
        assertEquals(0.8, a.satisfaction, 1e-9);
        assertEquals(0.8, b.satisfaction, 1e-9);
        assertEquals(-300, battery.stored);
        assertEquals(1, turbine.load, 1e-9);
        // 蓄电器空了：只剩发电
        battery.dischargeLimit = 0;
        power.tick();
        assertEquals(0.5, a.satisfaction, 1e-9);
        // 没接上电网的设备收到 0
        Probe outside = Probe.consumer(10);
        power.addDevice(20, 500, 64, 500, outside);
        power.tick();
        assertEquals(0, outside.satisfaction, 1e-9);
    }

    /** 随机增删塔，每一步都和暴力算出来的连通分量对照（S6：合并、拆分可靠）。 */
    @Test
    void randomEditsMatchBruteForce() {
        Random random = new Random(42);
        PowerSystem power = new PowerSystem();
        List<int[]> towers = new ArrayList<>();
        List<int[]> devices = new ArrayList<>();
        TowerSpec[] specs = {new TowerSpec(12, 8), new TowerSpec(30, 4)};
        int nextId = 0;
        for (int step = 0; step < 600; step++) {
            int op = random.nextInt(10);
            if (op < 5 || towers.size() < 5) {
                int specIndex = random.nextInt(10) == 0 ? 1 : 0;
                int[] t = {nextId++, random.nextInt(120), 60 + random.nextInt(16), random.nextInt(120), specIndex};
                towers.add(t);
                power.addTower(t[0], t[1], t[2], t[3], specs[specIndex]);
            } else if (op < 8) {
                int[] t = towers.remove(random.nextInt(towers.size()));
                power.removeTower(t[0]);
            } else if (op < 9 || devices.isEmpty()) {
                int[] d = {nextId++, random.nextInt(120), 60 + random.nextInt(16), random.nextInt(120)};
                devices.add(d);
                power.addDevice(d[0], d[1], d[2], d[3], Probe.consumer(1));
            } else {
                int[] d = devices.remove(random.nextInt(devices.size()));
                power.removeDevice(d[0]);
            }
            check(power, towers, devices, specs);
        }
    }

    private static void check(PowerSystem power, List<int[]> towers, List<int[]> devices, TowerSpec[] specs) {
        int n = towers.size();
        int[] component = new int[n];
        java.util.Arrays.fill(component, -1);
        int components = 0;
        for (int i = 0; i < n; i++) {
            if (component[i] >= 0) {
                continue;
            }
            ArrayDeque<Integer> queue = new ArrayDeque<>();
            component[i] = components;
            queue.add(i);
            while (!queue.isEmpty()) {
                int[] a = towers.get(queue.poll());
                for (int j = 0; j < n; j++) {
                    int[] b = towers.get(j);
                    if (component[j] < 0 && PowerSystem.connects(a[1], a[2], a[3], specs[a[4]].connectRange(),
                            b[1], b[2], b[3], specs[b[4]].connectRange())) {
                        component[j] = components;
                        queue.add(j);
                    }
                }
            }
            components++;
        }
        assertEquals(components, power.grids().size());
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                boolean same = power.gridOfTower(towers.get(i)[0]) == power.gridOfTower(towers.get(j)[0]);
                assertEquals(component[i] == component[j], same, "towers " + i + " and " + j);
            }
        }
        int attached = 0;
        for (int[] d : devices) {
            boolean covered = false;
            for (int[] t : towers) {
                covered |= PowerSystem.covers(t[1], t[2], t[3], specs[t[4]].coverRange(), d[1], d[2], d[3]);
            }
            PowerSystem.Grid grid = power.gridOf(d[0]);
            assertEquals(covered, grid != null, "device " + d[0]);
            if (grid != null) {
                attached++;
            }
        }
        int counted = 0;
        for (PowerSystem.Grid grid : power.grids()) {
            counted += grid.deviceCount();
        }
        assertEquals(attached, counted);
    }

    /** S6 的通过标准：200 台设备的电网每刻结算低于 0.5 ms；再看看两万台的规模。 */
    @Test
    void settlementStaysWithinBudget() {
        for (int size : new int[] {200, 20_000}) {
            PowerSystem power = new PowerSystem();
            int side = (int) Math.ceil(Math.sqrt(size));
            for (int i = 0; i < side; i += 4) {
                for (int j = 0; j < side; j += 4) {
                    power.addTower((long) i * side + j, i * 3, 64, j * 3, TOWER);
                }
            }
            for (int k = 0; k < size; k++) {
                int i = k / side;
                int j = k % side;
                Probe probe = k % 10 == 0 ? Probe.generator(900) : k % 10 == 1 ? Probe.accumulator(50, 50) : Probe.consumer(100);
                power.addDevice(1_000_000 + k, i * 3, 64, j * 3, probe);
            }
            for (int t = 0; t < 200; t++) {
                power.tick();
            }
            int ticks = 1000;
            long start = System.nanoTime();
            for (int t = 0; t < ticks; t++) {
                power.tick();
            }
            double ms = (System.nanoTime() - start) / 1e6 / ticks;
            System.out.printf("power: %d devices, %d grids: %.4f ms/tick%n", size, power.grids().size(), ms);
            if (size == 200) {
                assertTrue(ms < 0.5, ms + " ms/tick");
            }
        }
    }
}
