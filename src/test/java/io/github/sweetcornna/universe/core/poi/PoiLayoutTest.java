package io.github.sweetcornna.universe.core.poi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import io.github.sweetcornna.universe.core.galaxy.Galaxy;
import io.github.sweetcornna.universe.core.galaxy.GalaxyGenerator;
import io.github.sweetcornna.universe.core.galaxy.Liquid;
import io.github.sweetcornna.universe.core.galaxy.Planet;
import io.github.sweetcornna.universe.core.galaxy.PlanetType;
import io.github.sweetcornna.universe.core.terrain.TerrainModel;

class PoiLayoutTest {
    /** 平地，哪里都能放。 */
    private static final PoiLayout.Ground FLAT = new PoiLayout.Ground() {
        @Override
        public double height(int x, int z) {
            return 70;
        }

        @Override
        public boolean dry(int x, int z) {
            return true;
        }
    };

    private static PoiLayout flat(long seed, PlanetType type) {
        return new PoiLayout(seed, type, FLAT, 0);
    }

    @Test
    void sameSeedGivesSameLayout() {
        PoiLayout a = flat(42, PlanetType.HABITABLE);
        PoiLayout b = flat(42, PlanetType.HABITABLE);
        for (int cx = -10; cx <= 10; cx++) {
            for (int cz = -10; cz <= 10; cz++) {
                assertEquals(a.cell(cx, cz), b.cell(cx, cz));
            }
        }
        assertNotEquals(a.within(0, 0, 3000), flat(43, PlanetType.HABITABLE).within(0, 0, 3000));
    }

    @Test
    void centersStayInsideTheirCellWithMargin() {
        PoiLayout layout = flat(7, PlanetType.EXOTIC);
        Set<Long> keys = new HashSet<>();
        for (int cx = -20; cx <= 20; cx++) {
            for (int cz = -20; cz <= 20; cz++) {
                Optional<Poi> poi = layout.cell(cx, cz);
                if (poi.isEmpty()) {
                    continue;
                }
                Poi p = poi.get();
                assertEquals(cx, p.cellX());
                assertEquals(cz, p.cellZ());
                int lx = p.x() - cx * PoiLayout.CELL;
                int lz = p.z() - cz * PoiLayout.CELL;
                assertTrue(lx >= PoiLayout.MARGIN && lx < PoiLayout.CELL - PoiLayout.MARGIN, p.toString());
                assertTrue(lz >= PoiLayout.MARGIN && lz < PoiLayout.CELL - PoiLayout.MARGIN, p.toString());
                // 建筑半径加中心所在区块的偏移不超出 3×3 区块
                assertTrue(p.type().radius() + 15 < 32);
                assertTrue(p.rotation() >= 0 && p.rotation() < 4);
                assertTrue(keys.add(p.key()), "编号重复");
            }
        }
    }

    @Test
    void densityFollowsPlanetTypeAndAllTypesAppear() {
        Map<PoiType, Integer> counts = new EnumMap<>(PoiType.class);
        int habitable = 0;
        int barren = 0;
        PoiLayout h = flat(11, PlanetType.HABITABLE);
        PoiLayout b = flat(11, PlanetType.BARREN);
        for (int cx = 0; cx < 40; cx++) {
            for (int cz = 0; cz < 40; cz++) {
                Optional<Poi> poi = h.cell(cx, cz);
                if (poi.isPresent()) {
                    habitable++;
                    counts.merge(poi.get().type(), 1, Integer::sum);
                }
                if (b.cell(cx, cz).isPresent()) {
                    barren++;
                }
            }
        }
        // 1600 个格子：宜居 75%，荒芜 40%
        assertTrue(Math.abs(habitable - 1200) < 80, "宜居 " + habitable);
        assertTrue(Math.abs(barren - 640) < 80, "荒芜 " + barren);
        assertEquals(PoiType.values().length, counts.size());
        // 掉落舱、埋藏的科技最多，坠毁飞船最少
        assertTrue(counts.get(PoiType.DROP_POD) > counts.get(PoiType.CRASHED_SHIP));
        assertTrue(counts.get(PoiType.BURIED_TECH) > counts.get(PoiType.CRASHED_SHIP));
        assertTrue(flat(1, PlanetType.GAS_GIANT).within(0, 0, 2000).isEmpty());
    }

    @Test
    void withinAndInChunkAgree() {
        PoiLayout layout = flat(99, PlanetType.TOXIC);
        List<Poi> near = layout.within(300, -200, 600);
        for (int i = 1; i < near.size(); i++) {
            assertTrue(near.get(i - 1).distanceSq(300, -200) <= near.get(i).distanceSq(300, -200));
        }
        for (Poi poi : near) {
            assertTrue(poi.distance(300, -200) <= 600);
            assertEquals(Optional.of(poi), layout.inChunk(poi.x() >> 4, poi.z() >> 4));
            // 同一格子里别的区块没有
            assertEquals(Optional.empty(), layout.inChunk((poi.x() >> 4) + 1, poi.z() >> 4).filter(p -> p.equals(poi)));
        }
        assertFalse(near.isEmpty());
    }

    @Test
    void nearestMatchesBruteForce() {
        PoiLayout layout = flat(5, PlanetType.FROZEN);
        double[][] points = {{0, 0}, {1234, -567}, {-3000, 2500}, {80, 80}, {-159, 161}};
        for (double[] p : points) {
            for (PoiType type : PoiType.values()) {
                Optional<Poi> brute = layout.within(p[0], p[1], 2000).stream().filter(poi -> poi.type() == type).findFirst();
                Optional<Poi> fast = layout.nearest(p[0], p[1], 2000, poi -> poi.type() == type);
                assertEquals(brute.map(poi -> poi.distanceSq(p[0], p[1])), fast.map(poi -> poi.distanceSq(p[0], p[1])),
                        type + " @ " + p[0] + "," + p[1]);
            }
        }
        // 排除掉最近的那个，找到的是第二近的
        Poi first = layout.nearest(0, 0, 2000, poi -> true).orElseThrow();
        Poi second = layout.nearest(0, 0, 2000, poi -> poi.key() != first.key()).orElseThrow();
        assertTrue(second.distanceSq(0, 0) >= first.distanceSq(0, 0));
        assertEquals(layout.within(0, 0, 2000).get(1).key(), second.key());
    }

    @Test
    void wetAndSteepGroundIsAvoided() {
        // 西边（x < 0）是海，东边是陡坡：只有中间一条窄的平地
        PoiLayout.Ground ground = new PoiLayout.Ground() {
            @Override
            public double height(int x, int z) {
                return x > 2000 ? 70 + (x - 2000) * 0.8 : 70;
            }

            @Override
            public boolean dry(int x, int z) {
                return x >= 0;
            }
        };
        PoiLayout layout = new PoiLayout(3, PlanetType.HABITABLE, ground, 0);
        for (Poi poi : layout.within(0, 0, 6000)) {
            assertTrue(poi.x() - poi.type().radius() >= 0, "在海里：" + poi);
            assertTrue(poi.x() + poi.type().radius() <= 2000 + poi.type().slope() / 0.8 + 1, "在陡坡上：" + poi);
        }
    }

    @Test
    void worldBorderLimitsCenters() {
        PoiLayout layout = new PoiLayout(8, PlanetType.HABITABLE, FLAT, 2000);
        List<Poi> all = layout.within(0, 0, 3000);
        assertFalse(all.isEmpty());
        for (Poi poi : all) {
            assertTrue(Math.abs(poi.x()) <= 1000 - PoiLayout.MARGIN && Math.abs(poi.z()) <= 1000 - PoiLayout.MARGIN, poi.toString());
        }
    }

    @Test
    void realPlanetsKeepPoisOnDryLand() {
        Galaxy galaxy = GalaxyGenerator.generate(20261018L, 24);
        int checked = 0;
        for (Planet planet : galaxy.systems().stream().flatMap(s -> s.bodies().stream()).filter(Planet::landable).limit(12).toList()) {
            PoiLayout layout = PoiLayout.of(planet);
            TerrainModel model = TerrainModel.of(planet);
            for (Poi poi : layout.within(0, 0, 900)) {
                if (planet.liquid() != Liquid.NONE) {
                    assertTrue(model.sample(poi.x(), poi.z()).height() >= model.seaLevel() + 1, planet.id() + " " + poi);
                }
                assertTrue(Math.abs(layout.estimatedY(poi) - model.sample(poi.x(), poi.z()).height()) < 1);
                checked++;
            }
        }
        assertTrue(checked > 30, "只检查了 " + checked + " 个");
    }

    @Test
    void outpostLogIsStable() {
        assertEquals(OutpostLog.of(123), OutpostLog.of(123));
        OutpostLog log = OutpostLog.of(456);
        assertTrue(log.code().matches("[A-Z]-\\d{3}"), log.code());
        assertTrue(log.day() >= 12 && log.day() <= 640);
        assertTrue(log.opening() < OutpostLog.OPENINGS && log.situation() < OutpostLog.SITUATIONS
                && log.event() < OutpostLog.EVENTS && log.ending() < OutpostLog.ENDINGS);
    }
}
