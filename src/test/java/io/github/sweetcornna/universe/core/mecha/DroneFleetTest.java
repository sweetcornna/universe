package io.github.sweetcornna.universe.core.mecha;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import io.github.sweetcornna.universe.core.mecha.DroneFleet.State;
import io.github.sweetcornna.universe.core.mecha.DroneFleet.Task;

class DroneFleetTest {
    /** 一片虚影：认领过的不再给，建好的记下来。 */
    private static final class Site implements DroneFleet.Planner {
        final List<Task> open = new ArrayList<>();
        final Set<Task> claimed = new LinkedHashSet<>();
        final List<Task> built = new ArrayList<>();
        final Set<Task> fail = new LinkedHashSet<>();

        Site(int... xs) {
            for (int i = 0; i < xs.length; i++) {
                this.open.add(new Task(0, i, xs[i], 64, 0));
            }
        }

        @Override
        public List<Task> next(double x, double y, double z, int max) {
            List<Task> candidates = new ArrayList<>(this.open);
            candidates.sort(Comparator.comparingDouble(t -> Math.abs(t.x() - x)));
            List<Task> batch = DroneFleet.batch(candidates, max);
            this.open.removeAll(batch);
            this.claimed.addAll(batch);
            return batch;
        }

        @Override
        public boolean build(Task task) {
            this.claimed.remove(task);
            if (this.fail.contains(task)) {
                this.open.add(task);
                return false;
            }
            this.built.add(task);
            return true;
        }

        @Override
        public void release(Task task) {
            this.claimed.remove(task);
            this.open.add(task);
        }
    }

    @Test
    void batchTakesNeighboursOfTheNearest() {
        List<Task> candidates = List.of(new Task(0, 0, 0, 0, 0), new Task(0, 1, 10, 0, 0), new Task(0, 2, 3, 1, 0), new Task(0, 3, 4, 0, 4),
                new Task(0, 4, 5, 0, 0));
        List<Task> batch = DroneFleet.batch(candidates, 4);
        // 10 和 5 离第一个超过 4 格；从下往上
        assertEquals(List.of(0, 3, 2), batch.stream().map(Task::index).toList());
        assertEquals(1, DroneFleet.batch(candidates, 1).size());
        assertEquals(List.of(), DroneFleet.batch(List.of(), 4));
    }

    @Test
    void dronesFlyOutBuildAndComeBack() {
        Site site = new Site(10, 11, 12, 13, 14, 30);
        DroneFleet fleet = new DroneFleet();
        List<DroneFleet.Change> first = fleet.tick(0.5, 64.5, 0.5, 2, 0.5, 4, true, site);
        // 第一架带走 10–13，第二架带走 14（30 离 14 太远，不在一批）
        assertEquals(2, first.size());
        assertEquals(State.OUTBOUND, first.get(0).state());
        assertEquals(4, fleet.drones().get(0).tasks().size());
        assertEquals(1, fleet.drones().get(1).tasks().size());
        assertEquals(2, fleet.flying());
        for (int t = 0; t < 400 && site.built.size() < 6; t++) {
            fleet.tick(0.5, 64.5, 0.5, 2, 0.5, 4, true, site);
        }
        assertEquals(6, site.built.size());
        for (int t = 0; t < 200 && fleet.flying() > 0; t++) {
            fleet.tick(0.5, 64.5, 0.5, 2, 0.5, 4, true, site);
        }
        assertEquals(0, fleet.flying());
        assertTrue(site.claimed.isEmpty());
    }

    @Test
    void noPowerKeepsThemDocked() {
        Site site = new Site(5);
        DroneFleet fleet = new DroneFleet();
        assertEquals(List.of(), fleet.tick(0, 64, 0, 3, 0.3, 4, false, site));
        assertEquals(3, fleet.drones().size());
        assertEquals(0, fleet.flying());
        assertEquals(1, site.open.size());
    }

    @Test
    void recallReturnsUnbuiltTasks() {
        Site site = new Site(20, 21, 22);
        DroneFleet fleet = new DroneFleet();
        fleet.tick(0, 64, 0, 1, 0.3, 4, true, site);
        assertEquals(3, site.claimed.size());
        List<DroneFleet.Change> changes = fleet.recall(site);
        assertEquals(State.RETURNING, changes.getFirst().state());
        assertEquals(3, site.open.size());
        assertTrue(site.claimed.isEmpty());
        fleet.dock(site);
        assertEquals(0, fleet.flying());
    }

    @Test
    void failedBuildGoesBackToTheSite() {
        Site site = new Site(3);
        site.fail.add(site.open.getFirst());
        DroneFleet fleet = new DroneFleet();
        for (int t = 0; t < 40; t++) {
            fleet.tick(0.5, 64.5, 0.5, 1, 0.5, 4, t == 0, site);
        }
        assertEquals(List.of(), site.built);
        assertEquals(1, site.open.size());
        assertEquals(0, fleet.flying());
    }

    @Test
    void returningDronesChaseThePlayer() {
        Site site = new Site(2);
        DroneFleet fleet = new DroneFleet();
        double px = 0.5;
        for (int t = 0; t < 30 && site.built.isEmpty(); t++) {
            fleet.tick(px, 64.5, 0.5, 1, 0.4, 4, t == 0, site);
        }
        assertEquals(1, site.built.size());
        assertEquals(State.RETURNING, fleet.drones().getFirst().state());
        // 玩家走开了：无人机追过去
        px = 20.5;
        for (int t = 0; t < 100 && fleet.flying() > 0; t++) {
            fleet.tick(px, 64.5, 0.5, 1, 0.4, 4, false, site);
        }
        assertEquals(0, fleet.flying());
        assertEquals(px, fleet.drones().getFirst().x(), 1e-9);
    }
}
