package io.github.sweetcornna.universe.core.dyson;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import io.github.sweetcornna.universe.core.dyson.DysonGrid.Template;

class DysonSphereTest {
    private static final double LIMIT = DysonSphere.LATITUDE_BASE;

    @Test
    void newSphereHasOneOrbitAndRadiiAreClamped() {
        DysonSphere sphere = DysonSphere.create(1, DysonSphere.maxRadius(0.5));
        assertEquals(0.45, sphere.maxRadius(), 1e-9);
        assertEquals(1, sphere.orbits().size());
        assertEquals(0.27, sphere.orbit(1).radius(), 1e-9);
        int id = sphere.addLayer(5, 120, -30, Template.GEODESIC, 1);
        DysonLayer layer = sphere.layer(id);
        assertEquals(0.45, layer.radius(), 1e-9);
        assertEquals(90, layer.inclination(), 1e-9);
        assertEquals(330, layer.ascendingNode(), 1e-9);
        // 最内侧行星很近时，上限至少 0.06
        assertEquals(0.06, DysonSphere.maxRadius(0.02), 1e-9);
    }

    @Test
    void theAdrExampleLayerCosts440RocketsAnd2500Sails() {
        DysonSphere sphere = new DysonSphere(1, 0.3);
        DysonLayer layer = sphere.layer(sphere.addLayer(0.1, 0, 0, Template.GEODESIC, 1));
        assertEquals(440, layer.structureCapacity(LIMIT));
        assertEquals(2513, layer.cellCapacity(LIMIT), 10);
        // 建完约 80 kW：每焦/刻在界面上是 20 W
        double watts = sphere.shellCapacity(LIMIT) * 20;
        assertEquals(80_000, watts, 1_500);
    }

    @Test
    void rocketsBuildNodesFirstThenFramesAndNeverOvershoot() {
        DysonSphere sphere = new DysonSphere(1, 0.3);
        int id = sphere.addLayer(0.1, 0, 0, Template.GEODESIC, 1);
        DysonLayer layer = sphere.layer(id);
        sphere.advance(0);
        long now = 0;
        int launched = 0;
        // 节点全部预定之前，框架一枚都不发
        while (true) {
            DysonSphere.Rocket rocket = sphere.launchRocket(LIMIT, now);
            if (rocket == null) {
                break;
            }
            assertTrue(rocket.node());
            launched++;
        }
        assertEquals(300, launched);
        assertFalse(layer.empty());
        // 火箭到了，节点建完，开始建框架
        now += DysonSphere.ROCKET_FLIGHT;
        sphere.advance(now);
        for (int i = 0; i < 10; i++) {
            assertTrue(layer.nodeBuilt(i));
        }
        assertFalse(layer.nodeBuilt(10));
        int frames = 0;
        DysonSphere.Rocket rocket;
        while ((rocket = sphere.launchRocket(LIMIT, now)) != null) {
            assertFalse(rocket.node());
            frames++;
        }
        assertEquals(140, frames);
        now += DysonSphere.ROCKET_FLIGHT;
        sphere.advance(now);
        assertEquals(440, sphere.structurePoints());
        assertNull(sphere.launchRocket(LIMIT, now));
        assertFalse(sphere.hasTarget(LIMIT));
        // 放宽纬度以后又有能建的
        assertTrue(sphere.hasTarget(DysonSphere.LATITUDE_STRESS_2));
    }

    @Test
    void shapeChangesOnlyWhileTheLayerIsEmpty() {
        DysonSphere sphere = new DysonSphere(1, 0.3);
        int id = sphere.addLayer(0.1, 0, 0, Template.GEODESIC, 1);
        assertTrue(sphere.updateLayer(id, 0.2, 10, 20, Template.HEXAGONAL, 2));
        assertEquals(80, sphere.layer(id).grid().nodeCount());
        sphere.launchRocket(DysonSphere.LATITUDE_STRESS_2, 0);
        assertFalse(sphere.updateLayer(id, 0.25, 10, 20, Template.HEXAGONAL, 2));
        // 朝向还能改
        assertTrue(sphere.updateLayer(id, 0.2, 45, 90, Template.HEXAGONAL, 2));
        assertEquals(45, sphere.layer(id).inclination(), 1e-9);
        // 删掉层，在路上的火箭作废
        assertTrue(sphere.removeLayer(id));
        assertTrue(sphere.rockets().isEmpty());
    }

    @Test
    void sailsExpireAfterTheirLifetime() {
        DysonSphere sphere = DysonSphere.create(1, 0.3);
        sphere.advance(0);
        for (int i = 0; i < 100; i++) {
            assertTrue(sphere.launchSail(1, 0));
        }
        assertFalse(sphere.launchSail(7, 0));
        assertEquals(100, sphere.sails());
        assertEquals(100 * DysonSphere.SAIL_POWER, sphere.generation(), 1e-9);
        sphere.advance(DysonSphere.SAIL_LIFETIME - 20);
        assertEquals(100, sphere.sails());
        sphere.advance(DysonSphere.SAIL_LIFETIME + SwarmOrbit.BUCKET);
        assertEquals(0, sphere.sails());
    }

    @Test
    void readyShellsAbsorbSailsFromTheSwarm() {
        DysonSphere sphere = DysonSphere.create(2, 0.3);
        int id = sphere.addLayer(0.1, 0, 0, Template.GEODESIC, 1);
        DysonLayer layer = sphere.layer(id);
        sphere.advance(0);
        long now = 0;
        for (int i = 0; i < 5000; i++) {
            sphere.launchSail(1, now);
        }
        // 节点建完但框架没建，球壳还不能铺
        while (sphere.launchRocket(LIMIT, now) != null) {
            // 先把节点发完
        }
        now += DysonSphere.ROCKET_FLIGHT;
        sphere.advance(now);
        assertEquals(0, layer.cellPoints());
        while (sphere.launchRocket(LIMIT, now) != null) {
            // 再把框架发完
        }
        now += DysonSphere.ROCKET_FLIGHT;
        sphere.advance(now);
        for (int s = 0; s < 10; s++) {
            assertTrue(layer.shellReady(s), "shell " + s);
        }
        // 10 个节点，每分钟各吸 10 张（框架到达的那一段已经吸了一些）
        long before = layer.cellPoints();
        sphere.advance(now + 1200);
        long cells = layer.cellPoints();
        assertEquals(before + 100, cells);
        assertEquals(5000 - cells, sphere.sails());
        // 帆轮流铺进各片壳
        for (int s = 0; s < 10; s++) {
            assertTrue(Math.abs(layer.cells(s) - cells / 10.0) <= 1, "shell " + s);
        }
        // 光照系数 2：结构点 440 × 4.8 + 单元点 × 0.75 + 帆 × 1.8
        assertEquals(2 * (440 * 4.8 + cells * 0.75 + (5000 - cells) * 1.8), sphere.generation(), 1e-6);
    }

    @Test
    void receiversShareTheSphereProportionally() {
        DysonSphere sphere = DysonSphere.create(1, 0.3);
        sphere.advance(0);
        for (int i = 0; i < 1000; i++) {
            sphere.launchSail(1, 0);
        }
        // 1800 焦/刻；第一刻没有上一刻的需求，全给
        assertEquals(1, sphere.supplyFraction(), 1e-9);
        sphere.request(2400);
        sphere.request(1200);
        sphere.roll();
        assertEquals(0.5, sphere.supplyFraction(), 1e-9);
        sphere.request(600);
        sphere.draw(450);
        sphere.roll();
        assertEquals(1, sphere.supplyFraction(), 1e-9);
        assertEquals(450, sphere.lastDrawn(), 1e-9);
    }

    @Test
    void restoreRebuildsReservations() {
        DysonSphere sphere = new DysonSphere(1, 0.3);
        int id = sphere.addLayer(0.1, 0, 0, Template.GEODESIC, 1);
        DysonSphere.Rocket rocket = sphere.launchRocket(LIMIT, 0);
        assertNotNull(rocket);
        DysonLayer layer = sphere.layer(id);
        DysonSphere copy = new DysonSphere(1, 0.3);
        copy.restoreLayer(id, layer.radius(), layer.inclination(), layer.ascendingNode(), layer.grid().template(), layer.grid().density(),
                layer.nodeProgress(), layer.frameProgress(), layer.cellProgress());
        copy.restoreRocket(rocket);
        copy.restoreLastAdvance(0);
        assertFalse(copy.layer(id).empty());
        copy.advance(DysonSphere.ROCKET_FLIGHT);
        assertEquals(1, copy.structurePoints());
    }

    @Test
    void orientationTiltsThePole() {
        double[] pole = DysonSphere.orient(new double[] {0, 1, 0}, 90, 0, 0);
        assertEquals(0, pole[0], 1e-9);
        assertEquals(0, pole[1], 1e-9);
        assertEquals(1, Math.abs(pole[2]), 1e-9);
        double[] same = DysonSphere.orient(new double[] {0, 1, 0}, 0, 123, 45);
        assertEquals(1, same[1], 1e-9);
    }

    @Test
    void debugHelpersFillLayersAndOrbits() {
        DysonSphere sphere = new DysonSphere(1.0, 0.5);
        int layer = sphere.addLayer(0.2, 0, 0, DysonGrid.Template.GEODESIC, 1);
        int orbit = sphere.addOrbit(0.3, 0, 0);
        long revision = sphere.revision();
        assertEquals(0, sphere.fullShells());
        assertEquals(0, sphere.builtLayers());
        assertTrue(sphere.complete(layer, DysonSphere.LATITUDE_BASE));
        assertTrue(sphere.fullShells() > 0);
        assertEquals(1, sphere.builtLayers());
        assertTrue(sphere.revision() > revision);
        DysonLayer built = sphere.layer(layer);
        assertEquals(built.structureCapacity(DysonSphere.LATITUDE_BASE), built.structurePoints());
        assertEquals(built.cellCapacity(DysonSphere.LATITUDE_BASE), built.cellPoints());
        assertFalse(sphere.hasTarget(DysonSphere.LATITUDE_BASE));
        // 纬度放宽以后又有能建的了
        assertTrue(sphere.hasTarget(DysonSphere.LATITUDE_STRESS_2));
        assertTrue(sphere.shellPower() > 0);
        assertFalse(sphere.complete(99, DysonSphere.LATITUDE_BASE));
        sphere.advance(0);
        assertTrue(sphere.addSails(orbit, 500, 0, DysonSphere.SAIL_LIFETIME));
        assertEquals(500, sphere.orbit(orbit).sails());
        assertFalse(sphere.addSails(orbit, 0, 0, DysonSphere.SAIL_LIFETIME));
        assertFalse(sphere.addSails(99, 10, 0, DysonSphere.SAIL_LIFETIME));
        // 寿命到了就没了
        sphere.advance(DysonSphere.SAIL_LIFETIME + 1);
        assertEquals(0, sphere.orbit(orbit).sails());
    }

    /** 发射井每刻都问下一个目标，结果记下了（M21）：预定、建成、读档、拆掉、换纬度限制以后都要重新找。 */
    @Test
    void nextTargetFollowsEveryChange() {
        DysonLayer layer = new DysonLayer(1, 0.1, 0, 0, Template.GEODESIC, 1);
        DysonLayer.Target first = layer.nextTarget(LIMIT);
        assertNotNull(first);
        assertTrue(first.node());
        // 预定满了就轮到下一个
        layer.reserve(first, DysonLayer.NODE_SP);
        DysonLayer.Target second = layer.nextTarget(LIMIT);
        assertNotNull(second);
        assertFalse(second.equals(first));
        // 退掉预定又回到第一个
        layer.reserve(first, -DysonLayer.NODE_SP);
        assertEquals(first, layer.nextTarget(LIMIT));
        // 一枚一枚建满第一个节点
        for (int i = 0; i < DysonLayer.NODE_SP; i++) {
            layer.reserve(first, 1);
            layer.build(first);
        }
        assertTrue(layer.nodeBuilt(first.index()));
        assertEquals(second, layer.nextTarget(LIMIT));
        // 读档读成全部建完：没有可建的
        int[] nodes = new int[layer.grid().nodeCount()];
        java.util.Arrays.fill(nodes, DysonLayer.NODE_SP);
        int[] frames = new int[layer.grid().frameCount()];
        for (int f = 0; f < frames.length; f++) {
            frames[f] = layer.frameCost(f);
        }
        layer.restore(nodes, frames, new int[layer.grid().shellCount()]);
        assertNull(layer.nextTarget(LIMIT));
        assertNull(layer.nextTarget(LIMIT));
        // 拆掉一个节点：它又成了目标
        int node = second.index();
        layer.demolish(DysonLayer.Element.NODE, node, new java.util.HashSet<>(), new java.util.HashSet<>());
        assertEquals(new DysonLayer.Target(true, node), layer.nextTarget(LIMIT));
        // 纬度限制收到 0：只剩赤道上的节点能建
        DysonLayer.Target equator = layer.nextTarget(0);
        if (equator != null) {
            assertTrue(layer.grid().nodeWithin(equator.index(), 0));
        }
        assertEquals(new DysonLayer.Target(true, node), layer.nextTarget(LIMIT));
    }
}
