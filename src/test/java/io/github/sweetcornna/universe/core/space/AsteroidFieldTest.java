package io.github.sweetcornna.universe.core.space;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import io.github.sweetcornna.universe.core.galaxy.Galaxy;
import io.github.sweetcornna.universe.core.galaxy.GalaxyGenerator;
import io.github.sweetcornna.universe.core.galaxy.StarSystem;
import io.github.sweetcornna.universe.core.procgen.Rng;
import io.github.sweetcornna.universe.core.space.AsteroidField.Belt;
import io.github.sweetcornna.universe.core.space.AsteroidField.Material;
import io.github.sweetcornna.universe.core.space.AsteroidField.Shape;
import io.github.sweetcornna.universe.core.space.AsteroidField.Slot;
import io.github.sweetcornna.universe.core.space.AsteroidField.Voxel;
import io.github.sweetcornna.universe.core.space.AsteroidField.Yield;

class AsteroidFieldTest {
    private static final Galaxy GALAXY = GalaxyGenerator.generate(12345L, GalaxyGenerator.DEFAULT_SYSTEM_COUNT);

    @Test
    void everySystemHasOneOrTwoBeltsClearOfBodies() {
        int two = 0;
        for (StarSystem system : GALAXY.systems()) {
            List<Belt> belts = AsteroidField.belts(system);
            assertTrue(belts.size() == 1 || belts.size() == 2, system.name() + ": " + belts.size());
            assertEquals(belts, AsteroidField.belts(system), "同一个恒星系的小行星带总在同一个地方");
            if (belts.size() == 2) {
                two++;
            }
            for (Belt belt : belts) {
                assertTrue(belt.width() >= AsteroidField.MIN_WIDTH - 1e-6, "带宽 " + belt.width());
                assertTrue(belt.width() <= AsteroidField.MAX_WIDTH + 1e-6);
                assertTrue(belt.halfHeight() <= AsteroidField.MAX_HALF_HEIGHT && belt.halfHeight() >= AsteroidField.MIN_HALF_HEIGHT);
                assertTrue(belt.inner() >= SpaceLayout.starRadius(system) + AsteroidField.STAR_MARGIN - 1e-6, "离恒星太近");
                for (SpaceLayout.Body body : SpaceLayout.of(system)) {
                    double d = Math.sqrt(body.x() * body.x() + body.z() * body.z());
                    boolean clear = belt.outer() <= d - body.radius() - AsteroidField.BODY_MARGIN + 1e-6
                            || belt.inner() >= d + body.radius() + AsteroidField.BODY_MARGIN - 1e-6;
                    assertTrue(clear, system.name() + " 的带压到了 " + body.planet().name());
                }
            }
            if (belts.size() == 2) {
                assertTrue(belts.get(0).outer() <= belts.get(1).inner() + 1e-6, "两条带不重叠");
            }
        }
        assertTrue(two > 0 && two < GALAXY.systems().size(), "有的恒星系两条带，有的一条");
    }

    @Test
    void densityPeaksInTheMiddleOfTheBelt() {
        Belt belt = new Belt(5000, 400, 150);
        assertEquals(1, belt.density(5000, 0, 0), 1e-9);
        assertEquals(1, belt.density(0, 0, -5000), 1e-9);
        assertTrue(belt.density(5150, 0, 0) < belt.density(5050, 0, 0));
        assertTrue(belt.density(5000, 100, 0) < belt.density(5000, 20, 0));
        assertEquals(0, belt.density(5300, 0, 0));
        assertEquals(0, belt.density(5000, 160, 0));
        // 带外最近的一点在带的边上，带里就是自己
        double[] p = belt.nearestPoint(0, 400, 6000);
        assertEquals(0, p[0], 1e-9);
        assertEquals(150, p[1], 1e-9);
        assertEquals(5200, p[2], 1e-9);
        assertEquals(0, belt.distanceTo(4900, 10, 0), 1e-9);
    }

    @Test
    void cellsAreDeterministicAndDenseOnlyInBelts() {
        StarSystem system = GALAXY.systems().get(0);
        List<Belt> belts = AsteroidField.belts(system);
        Belt belt = belts.get(0);
        // 带正中间的一圈格子 vs 带外很远的格子
        int inside = 0;
        int insideCells = 0;
        int outside = 0;
        int outsideCells = 0;
        for (int i = 0; i < 200; i++) {
            double angle = i * 2 * Math.PI / 200;
            int cx = (int) Math.floor(Math.cos(angle) * belt.radius() / AsteroidField.CELL);
            int cz = (int) Math.floor(Math.sin(angle) * belt.radius() / AsteroidField.CELL);
            List<Slot> slots = AsteroidField.cell(system.seed(), belts, cx, -1, cz);
            assertEquals(slots, AsteroidField.cell(system.seed(), belts, cx, -1, cz));
            inside += slots.size();
            insideCells++;
            int fx = (int) Math.floor(Math.cos(angle) * (belts.get(belts.size() - 1).outer() + 6000) / AsteroidField.CELL);
            int fz = (int) Math.floor(Math.sin(angle) * (belts.get(belts.size() - 1).outer() + 6000) / AsteroidField.CELL);
            outside += AsteroidField.cell(system.seed(), belts, fx, 0, fz).size();
            outsideCells++;
        }
        double perCellInside = inside / (double) insideCells;
        double perCellOutside = outside / (double) outsideCells;
        assertTrue(perCellInside > 1.5, "带里每格 " + perCellInside);
        assertTrue(perCellOutside < 0.2, "带外每格 " + perCellOutside);
    }

    @Test
    void slotKeysAreUniqueAroundAPoint() {
        Set<Long> keys = new HashSet<>();
        int count = 0;
        for (int cx = -40; cx <= 40; cx++) {
            for (int cy = -2; cy <= 1; cy++) {
                for (int cz = -40; cz <= 40; cz++) {
                    for (int i = 0; i < AsteroidField.CANDIDATES; i++) {
                        keys.add(Slot.key(cx, cy, cz, i));
                        count++;
                    }
                }
            }
        }
        assertEquals(count, keys.size());
    }

    @Test
    void shapesAreConnectedBlobsWithOreOnTheSurface() {
        Rng rng = Rng.of(7);
        int rich = 0;
        for (int k = 0; k < 400; k++) {
            long seed = rng.nextLong();
            Shape shape = AsteroidField.shape(seed);
            assertEquals(shape.voxels(), AsteroidField.shape(seed).voxels(), "同一个种子长出同样的小行星");
            int n = shape.voxels().size();
            assertTrue(n >= shape.size().min() && n <= shape.size().max());
            Set<Long> cells = new HashSet<>();
            for (Voxel v : shape.voxels()) {
                assertTrue(cells.add(key(v.x(), v.y(), v.z())), "方块不重叠");
                double dx = Math.abs(v.x() + 0.5 - shape.center()[0]) + 0.5;
                double dy = Math.abs(v.y() + 0.5 - shape.center()[1]) + 0.5;
                double dz = Math.abs(v.z() + 0.5 - shape.center()[2]) + 0.5;
                assertTrue(Math.sqrt(dx * dx + dy * dy + dz * dz) <= shape.radius() + 1e-9, "半径包住所有方块");
            }
            assertTrue(shape.radius() <= AsteroidField.MAX_RADIUS, "半径不超过上限：" + shape.radius());
            assertEquals(n, connected(shape.voxels(), cells), "连成一团");
            Map<Material, Integer> ores = shape.ores();
            assertFalse(ores.isEmpty(), "至少一个矿点");
            int oreCount = ores.values().stream().mapToInt(Integer::intValue).sum();
            assertTrue(oreCount < n, "不全是矿");
            if (ores.keySet().stream().anyMatch(Material::rare)) {
                rich++;
            }
            assertEquals(1, Math.sqrt(shape.spinAxis()[0] * shape.spinAxis()[0] + shape.spinAxis()[1] * shape.spinAxis()[1]
                    + shape.spinAxis()[2] * shape.spinAxis()[2]), 1e-9);
            assertEquals(AsteroidField.DURABILITY_SCALE * Math.pow(n, AsteroidField.DURABILITY_EXPONENT), shape.durability(), 1e-9);
        }
        assertTrue(rich > 20 && rich < 100, "一成多的小行星有稀有矿：" + rich);
    }

    @Test
    void contactFindsWhereTheSweepEntersTheSphere() {
        double[] c = {0, 0, 0};
        // 正面撞上：一刻飞 30 格穿过半径 5 的球，法线朝来的方向
        double[] head = AsteroidField.contact(new double[] {-20, 0, 0}, new double[] {30, 0, 0}, c, 5);
        assertNotNull(head);
        assertEquals(-1, head[0], 1e-9);
        // 斜着擦过：进球那一点的法线和速度相对，不是垂直（取最近点的话法线总垂直于速度，撞了也不掉血）
        double[] graze = AsteroidField.contact(new double[] {-20, 3, 0}, new double[] {40, 0, 0}, c, 5);
        assertNotNull(graze);
        assertTrue(graze[0] < -0.5, "法线朝着来的方向：" + graze[0]);
        assertEquals(1, Math.sqrt(graze[0] * graze[0] + graze[1] * graze[1] + graze[2] * graze[2]), 1e-9);
        // 擦不到、还没到、已经飞过去了
        assertNull(AsteroidField.contact(new double[] {-20, 6, 0}, new double[] {40, 0, 0}, c, 5));
        assertNull(AsteroidField.contact(new double[] {-20, 0, 0}, new double[] {10, 0, 0}, c, 5));
        assertNull(AsteroidField.contact(new double[] {10, 0, 0}, new double[] {10, 0, 0}, c, 5));
        // 本来就在球里：从移动后的位置往外推
        double[] inside = AsteroidField.contact(new double[] {0, 1, 0}, new double[] {0, 1, 0}, c, 5);
        assertNotNull(inside);
        assertEquals(1, inside[1], 1e-9);
        assertNull(AsteroidField.contact(new double[] {0, 4, 0}, new double[] {0, 2, 0}, c, 5), "已经出去了");
    }

    @Test
    void yieldComesFromOreVoxels() {
        Rng rng = Rng.of(11);
        for (int k = 0; k < 200; k++) {
            Shape shape = AsteroidField.shape(rng.nextLong());
            List<Yield> yields = AsteroidField.yield(shape, Rng.of(k));
            Map<Material, Integer> ores = shape.ores();
            assertEquals(ores.size(), yields.size());
            for (Yield y : yields) {
                assertTrue(y.material().ore());
                int spots = ores.get(y.material());
                if (y.material().rare()) {
                    assertEquals(spots, y.count());
                } else {
                    assertTrue(y.count() >= spots && y.count() <= spots * 2);
                }
            }
        }
    }

    @Test
    void asteroidsKeepClearOfBodiesAndTheStar() {
        StarSystem system = GALAXY.systems().get(0);
        List<SpaceLayout.Body> bodies = SpaceLayout.of(system);
        double star = SpaceLayout.starRadius(system);
        assertFalse(AsteroidField.clear(bodies, star, star + 10, 0, 0));
        SpaceLayout.Body body = bodies.get(0);
        assertFalse(AsteroidField.clear(bodies, star, body.x() + body.radius() + 10, body.y(), body.z()));
    }

    private static int connected(List<Voxel> voxels, Set<Long> cells) {
        Set<Long> seen = new HashSet<>();
        ArrayDeque<int[]> queue = new ArrayDeque<>();
        Voxel first = voxels.get(0);
        queue.add(new int[] {first.x(), first.y(), first.z()});
        seen.add(key(first.x(), first.y(), first.z()));
        int[][] dirs = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
        while (!queue.isEmpty()) {
            int[] c = queue.poll();
            for (int[] d : dirs) {
                long k = key(c[0] + d[0], c[1] + d[1], c[2] + d[2]);
                if (cells.contains(k) && seen.add(k)) {
                    queue.add(new int[] {c[0] + d[0], c[1] + d[1], c[2] + d[2]});
                }
            }
        }
        return seen.size();
    }

    private static long key(int x, int y, int z) {
        return ((long) (x & 0xFFFF) << 32) | ((long) (y & 0xFFFF) << 16) | (z & 0xFFFF);
    }
}
