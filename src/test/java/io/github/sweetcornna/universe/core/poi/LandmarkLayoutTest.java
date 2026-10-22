package io.github.sweetcornna.universe.core.poi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import io.github.sweetcornna.universe.core.ecology.Fauna;
import io.github.sweetcornna.universe.core.galaxy.Galaxy;
import io.github.sweetcornna.universe.core.galaxy.GalaxyGenerator;
import io.github.sweetcornna.universe.core.galaxy.Planet;
import io.github.sweetcornna.universe.core.galaxy.PlanetType;
import io.github.sweetcornna.universe.core.procgen.Rng;
import io.github.sweetcornna.universe.core.sentinel.SentinelLevel;
import io.github.sweetcornna.universe.core.terrain.TerrainModel;

class LandmarkLayoutTest {
    private static final int SEA = 63;
    private static final Set<LandmarkType> ALL = EnumSet.allOf(LandmarkType.class);
    /** 海底的两种；合成地面的测试都是海底，只用这两种。 */
    private static final Set<LandmarkType> SEABED = EnumSet.of(LandmarkType.SHIPWRECK, LandmarkType.SEABED_RUIN);

    /** 平的地面，高度固定。 */
    private static LandmarkLayout.Ground flat(double height) {
        return new LandmarkLayout.Ground() {
            @Override
            public double height(int x, int z) {
                return height;
            }

            @Override
            public boolean usable(int x, int z) {
                return true;
            }
        };
    }

    /** 处处是 30 格深的平海底。 */
    private static LandmarkLayout deep(long seed) {
        return new LandmarkLayout(seed, SEABED, flat(SEA - 30), SEA, 0, LandmarkLayout.Blocked.NONE);
    }

    private static List<Landmark> cells(LandmarkLayout layout, LandmarkType type, int range) {
        List<Landmark> found = new ArrayList<>();
        for (int cx = -range; cx <= range; cx++) {
            for (int cz = -range; cz <= range; cz++) {
                layout.cell(type, cx, cz).ifPresent(found::add);
            }
        }
        return found;
    }

    @Test
    void sameSeedGivesSameLayout() {
        LandmarkLayout a = deep(42);
        LandmarkLayout b = deep(42);
        LandmarkLayout other = deep(43);
        boolean differs = false;
        for (LandmarkType type : LandmarkType.values()) {
            for (int cx = -8; cx <= 8; cx++) {
                for (int cz = -8; cz <= 8; cz++) {
                    assertEquals(a.cell(type, cx, cz), b.cell(type, cx, cz));
                    differs |= !a.cell(type, cx, cz).equals(other.cell(type, cx, cz));
                }
            }
        }
        assertTrue(differs);
        assertEquals(a.within(100, -300, 5000), b.within(100, -300, 5000));
    }

    @Test
    void queryOrderAndTheSharedCacheDoNotChangeResults() {
        // 两种地标共用一个缓存，沉船还要让海底遗迹让路：先问哪种、问几遍结果都一样，缓存也不会把一种的结果给另一种
        LandmarkLayout wrecksFirst = deep(7);
        LandmarkLayout ruinsFirst = deep(7);
        List<Landmark> wrecks = cells(wrecksFirst, LandmarkType.SHIPWRECK, 10);
        List<Landmark> ruins = cells(wrecksFirst, LandmarkType.SEABED_RUIN, 10);
        assertEquals(ruins, cells(ruinsFirst, LandmarkType.SEABED_RUIN, 10));
        assertEquals(wrecks, cells(ruinsFirst, LandmarkType.SHIPWRECK, 10));
        assertEquals(wrecks, cells(wrecksFirst, LandmarkType.SHIPWRECK, 10));
        assertFalse(wrecks.isEmpty() || ruins.isEmpty());
        Set<Long> keys = new HashSet<>();
        for (LandmarkType type : LandmarkType.values()) {
            for (int cx = -10; cx <= 10; cx++) {
                for (int cz = -10; cz <= 10; cz++) {
                    Optional<Landmark> landmark = wrecksFirst.cell(type, cx, cz);
                    if (landmark.isPresent()) {
                        assertEquals(type, landmark.get().type());
                        assertEquals(cx, landmark.get().cellX());
                        assertEquals(cz, landmark.get().cellZ());
                        assertTrue(keys.add(landmark.get().key()), "领奖记录的编号不能重复");
                    }
                }
            }
        }
    }

    @Test
    void landmarksStayInsideTheirCellAndApartFromEachOther() {
        LandmarkLayout layout = deep(2026);
        List<Landmark> wrecks = cells(layout, LandmarkType.SHIPWRECK, 12);
        List<Landmark> ruins = cells(layout, LandmarkType.SEABED_RUIN, 12);
        for (Landmark landmark : concat(wrecks, ruins)) {
            int grid = landmark.type().grid();
            int x0 = landmark.cellX() * grid;
            int z0 = landmark.cellZ() * grid;
            assertTrue(landmark.x() >= x0 + LandmarkLayout.MARGIN && landmark.x() < x0 + grid - LandmarkLayout.MARGIN, landmark.toString());
            assertTrue(landmark.z() >= z0 + LandmarkLayout.MARGIN && landmark.z() < z0 + grid - LandmarkLayout.MARGIN, landmark.toString());
            assertTrue(landmark.rotation() >= 0 && landmark.rotation() < 4);
        }
        // 海底遗迹给沉船让路：两者之间隔开两边的半径再加 SPACING
        double gap = LandmarkType.SHIPWRECK.radius() + LandmarkType.SEABED_RUIN.radius() + LandmarkLayout.SPACING;
        for (Landmark ruin : ruins) {
            for (Landmark wreck : wrecks) {
                assertTrue(wreck.distance(ruin.x() + 0.5, ruin.z() + 0.5) > gap, ruin + " vs " + wreck);
            }
        }
        // 平坦的深海底：出现的比例接近设定的概率（遗迹还要再给沉船让一点）
        int cellsCount = 25 * 25;
        assertEquals(LandmarkType.SHIPWRECK.chance(), wrecks.size() / (double) cellsCount, 0.08);
        assertTrue(ruins.size() / (double) cellsCount <= LandmarkType.SEABED_RUIN.chance() + 0.08);
        assertTrue(ruins.size() / (double) cellsCount >= LandmarkType.SEABED_RUIN.chance() - 0.15);
    }

    @Test
    void seabedLandmarksNeedDeepFlatUsableSeabed() {
        // 水深正好 SEABED_DEPTH 可以，浅一格不行
        assertFalse(new LandmarkLayout(5, SEABED, flat(SEA - LandmarkLayout.SEABED_DEPTH), SEA, 0, LandmarkLayout.Blocked.NONE)
                .within(0, 0, 6000).isEmpty());
        assertTrue(new LandmarkLayout(5, SEABED, flat(SEA - LandmarkLayout.SEABED_DEPTH + 1), SEA, 0, LandmarkLayout.Blocked.NONE)
                .within(0, 0, 6000).isEmpty());
        // 陡坡：占地范围里的高低差超过要求
        LandmarkLayout.Ground slope = new LandmarkLayout.Ground() {
            @Override
            public double height(int x, int z) {
                return SEA - 60 + Math.floorMod(x, 400) * 0.5;
            }

            @Override
            public boolean usable(int x, int z) {
                return true;
            }
        };
        assertTrue(new LandmarkLayout(5, SEABED, slope, SEA, 0, LandmarkLayout.Blocked.NONE).within(0, 0, 6000).isEmpty());
        // 熔岩湖之类不能用的地面
        LandmarkLayout.Ground lava = new LandmarkLayout.Ground() {
            @Override
            public double height(int x, int z) {
                return SEA - 30;
            }

            @Override
            public boolean usable(int x, int z) {
                return false;
            }
        };
        assertTrue(new LandmarkLayout(5, SEABED, lava, SEA, 0, LandmarkLayout.Blocked.NONE).within(0, 0, 6000).isEmpty());
    }

    @Test
    void landmarksGiveWayToBlockedPlacesAndTheWorldBorder() {
        assertTrue(new LandmarkLayout(42, SEABED, flat(SEA - 30), SEA, 0, (x, z, r) -> true).within(0, 0, 8000).isEmpty());
        // 中心 2000 格以内都被占了（兴趣点、传送门）：地标连占地范围都不伸进去
        LandmarkLayout.Blocked disc = (x, z, r) -> Math.hypot(x, z) < 2000 + r;
        LandmarkLayout blocked = new LandmarkLayout(42, SEABED, flat(SEA - 30), SEA, 0, disc);
        assertFalse(deep(42).within(0, 0, 1900).isEmpty(), "没让路时这里本来有地标");
        List<Landmark> around = blocked.within(0, 0, 4000);
        assertFalse(around.isEmpty());
        for (Landmark landmark : around) {
            assertTrue(Math.hypot(landmark.x(), landmark.z()) >= 2000 + landmark.type().radius(), landmark.toString());
        }
        // 世界边界 4096：中心离边界至少 MARGIN 格
        LandmarkLayout bordered = new LandmarkLayout(42, SEABED, flat(SEA - 30), SEA, 4096, LandmarkLayout.Blocked.NONE);
        int limit = 4096 / 2 - LandmarkLayout.MARGIN;
        assertFalse(bordered.within(0, 0, 3000).isEmpty());
        for (Landmark landmark : bordered.within(0, 0, 6000)) {
            assertTrue(Math.abs(landmark.x()) <= limit && Math.abs(landmark.z()) <= limit, landmark.toString());
        }
    }

    @Test
    void chunkLookupWithinAndNearestAgree() {
        LandmarkLayout layout = deep(99);
        // 一片区块逐个查到的，和 within 查到的是同一批
        int chunks = 60;
        Set<Landmark> byChunk = new HashSet<>();
        for (int cx = -chunks; cx < chunks; cx++) {
            for (int cz = -chunks; cz < chunks; cz++) {
                for (Landmark landmark : layout.inChunk(cx, cz)) {
                    assertEquals(cx, landmark.x() >> 4);
                    assertEquals(cz, landmark.z() >> 4);
                    assertTrue(byChunk.add(landmark));
                }
            }
        }
        Set<Landmark> byRadius = new HashSet<>();
        int edge = chunks * 16;
        for (Landmark landmark : layout.within(0, 0, edge * 1.5)) {
            if (landmark.x() >= -edge && landmark.x() < edge && landmark.z() >= -edge && landmark.z() < edge) {
                byRadius.add(landmark);
            }
        }
        assertFalse(byChunk.isEmpty());
        assertEquals(byRadius, byChunk);
        for (Landmark landmark : byChunk) {
            assertEquals(Optional.of(landmark), layout.nearest(landmark.type(), landmark.x() + 0.5, landmark.z() + 0.5, 1));
        }
        // within 由近到远；nearest 和 within 里同种最近的一个一样
        Rng rng = Rng.of(1);
        for (int i = 0; i < 200; i++) {
            double px = rng.range(-3000.0, 3000.0);
            double pz = rng.range(-3000.0, 3000.0);
            double radius = rng.range(50.0, 1500.0);
            List<Landmark> near = layout.within(px, pz, radius);
            for (int j = 1; j < near.size(); j++) {
                assertTrue(near.get(j - 1).distanceSq(px, pz) <= near.get(j).distanceSq(px, pz));
            }
            for (Landmark landmark : near) {
                assertTrue(landmark.distance(px, pz) <= radius + 1e-9);
            }
            for (LandmarkType type : LandmarkType.values()) {
                Optional<Landmark> expected = near.stream().filter(l -> l.type() == type).findFirst();
                Optional<Landmark> actual = layout.nearest(type, px, pz, radius);
                assertEquals(expected.map(l -> l.distanceSq(px, pz)), actual.map(l -> l.distanceSq(px, pz)), px + "," + pz + " r=" + radius);
            }
        }
    }

    @Test
    void emptyKindsMeanNoLandmarks() {
        LandmarkLayout none = new LandmarkLayout(42, Set.of(), flat(SEA - 30), SEA, 0, LandmarkLayout.Blocked.NONE);
        assertTrue(none.kinds().isEmpty());
        assertTrue(none.within(0, 0, 10_000).isEmpty());
        assertTrue(none.cell(LandmarkType.SHIPWRECK, 0, 0).isEmpty());
        assertTrue(none.nearest(LandmarkType.SEABED_RUIN, 0, 0, 10_000).isEmpty());
        assertTrue(none.inChunk(0, 0).isEmpty());
        // 只有沉船时没有遗迹
        LandmarkLayout wrecks = new LandmarkLayout(42, EnumSet.of(LandmarkType.SHIPWRECK), flat(SEA - 30), SEA, 0, LandmarkLayout.Blocked.NONE);
        assertTrue(wrecks.within(0, 0, 6000).stream().allMatch(l -> l.type() == LandmarkType.SHIPWRECK));
    }

    @Test
    void typesKeepTheirSeedKeysAndOrder() {
        // 派生键和顺序属于存档格式：只往后加
        assertEquals("landmark/shipwreck", LandmarkType.SHIPWRECK.seedKey());
        assertEquals("landmark/seabed_ruin", LandmarkType.SEABED_RUIN.seedKey());
        assertEquals(0, LandmarkType.SHIPWRECK.ordinal());
        assertEquals(1, LandmarkType.SEABED_RUIN.ordinal());
        for (LandmarkType type : LandmarkType.values()) {
            assertTrue(type.radius() <= 15, "建筑不超过中心 15 格");
            assertTrue(type.grid() - 2 * LandmarkLayout.MARGIN > 0);
            assertTrue(LandmarkLayout.MARGIN > 15, "中心离格子边比一个区块远，跨格的区块里不会漏掉地标");
            assertTrue(type.chance() > 0 && type.chance() <= 1);
        }
        assertNotEquals(LandmarkType.SHIPWRECK.color(), LandmarkType.SEABED_RUIN.color());
    }

    // ---------------------------------------------------------------- 真实的星球

    private static List<Planet> planets() {
        Galaxy galaxy = GalaxyGenerator.generate(20261016L, 64);
        return galaxy.systems().stream().flatMap(s -> s.bodies().stream()).toList();
    }

    @Test
    void onlyLandableSeaPlanetsHaveSeabedLandmarks() {
        int seas = 0;
        for (Planet planet : planets()) {
            Set<LandmarkType> kinds = LandmarkLayout.kinds(planet);
            Set<LandmarkType> seabed = EnumSet.noneOf(LandmarkType.class);
            kinds.stream().filter(LandmarkType::seabed).forEach(seabed::add);
            if (!planet.landable()) {
                assertTrue(kinds.isEmpty(), planet.id().toString());
            } else if (Fauna.hasSea(planet.liquid())) {
                assertEquals(SEABED, seabed, planet.id().toString());
                seas++;
            } else {
                assertTrue(seabed.isEmpty(), planet.id() + " " + planet.type() + " " + planet.liquid());
                assertTrue(LandmarkLayout.of(planet, LandmarkLayout.Blocked.NONE).within(0, 0, 3000).stream()
                        .noneMatch(l -> l.type().seabed()));
            }
        }
        assertTrue(seas >= 5, "too few sea planets: " + seas);
    }

    @Test
    void landLandmarksFollowThePlanetRules() {
        boolean facility = false;
        boolean noFacility = false;
        for (Planet planet : planets()) {
            if (!planet.landable()) {
                continue;
            }
            Set<LandmarkType> kinds = LandmarkLayout.kinds(planet);
            String id = planet.id() + " " + planet.type();
            assertTrue(kinds.contains(LandmarkType.OBSERVATORY), id);
            assertTrue(kinds.contains(LandmarkType.SALVAGE_SITE), id);
            assertTrue(kinds.contains(LandmarkType.ABANDONED_BUILDING), id);
            assertEquals(planet.atmosphere().present() && planet.type() != PlanetType.BARREN, kinds.contains(LandmarkType.TRADING_POST), id);
            assertEquals(SentinelLevel.of(planet).present(), kinds.contains(LandmarkType.MANUFACTURING_FACILITY), id);
            facility |= kinds.contains(LandmarkType.MANUFACTURING_FACILITY);
            noFacility |= !kinds.contains(LandmarkType.MANUFACTURING_FACILITY);
            boolean fossils = switch (planet.type()) {
                case HABITABLE, BARREN, SCORCHED, FROZEN -> true;
                default -> false;
            };
            assertEquals(fossils, kinds.contains(LandmarkType.FOSSIL_PIT), id);
        }
        assertTrue(facility && noFacility);
    }

    @Test
    void landLandmarksStayOnDryFlatGround() {
        Set<LandmarkType> land = EnumSet.complementOf(EnumSet.copyOf(SEABED));
        // 海平面以上一格起才算陆地
        List<Landmark> dry = new LandmarkLayout(7, ALL, flat(SEA + 1), SEA, 0, LandmarkLayout.Blocked.NONE).within(0, 0, 6000);
        assertFalse(dry.isEmpty());
        assertTrue(dry.stream().allMatch(l -> land.contains(l.type())));
        assertTrue(new LandmarkLayout(7, land, flat(SEA), SEA, 0, LandmarkLayout.Blocked.NONE).within(0, 0, 6000).isEmpty());
        // 深海底只有海底的两种
        assertTrue(new LandmarkLayout(7, ALL, flat(SEA - 30), SEA, 0, LandmarkLayout.Blocked.NONE).within(0, 0, 6000).stream()
                .allMatch(l -> l.type().seabed()));
    }

    @Test
    void realSeaPlanetsPutLandmarksOnDeepFlatSeabed() {
        int total = 0;
        int checked = 0;
        for (Planet planet : planets()) {
            if (LandmarkLayout.kinds(planet).isEmpty() || checked >= 6) {
                continue;
            }
            checked++;
            TerrainModel terrain = TerrainModel.of(planet);
            LandmarkLayout layout = LandmarkLayout.of(planet, LandmarkLayout.Blocked.NONE);
            assertEquals(layout.within(0, 0, 3000), LandmarkLayout.of(planet, LandmarkLayout.Blocked.NONE).within(0, 0, 3000));
            for (Landmark landmark : layout.within(0, 0, 3000)) {
                total++;
                int r = landmark.type().radius();
                double min = Double.MAX_VALUE;
                double max = -Double.MAX_VALUE;
                for (int i = -1; i <= 1; i++) {
                    for (int j = -1; j <= 1; j++) {
                        int x = landmark.x() + i * r;
                        int z = landmark.z() + j * r;
                        double h = terrain.sample(x, z).height();
                        assertTrue(landmark.type().seabed() ? h <= terrain.seaLevel() - LandmarkLayout.SEABED_DEPTH : h >= terrain.seaLevel() + 1,
                                planet.id() + " " + landmark);
                        assertFalse(terrain.lavaLake(x, z));
                        min = Math.min(min, h);
                        max = Math.max(max, h);
                    }
                }
                assertTrue(max - min <= landmark.type().slope(), planet.id() + " " + landmark);
                if (landmark.type().seabed()) {
                    assertTrue(layout.estimatedY(landmark) <= terrain.seaLevel() - LandmarkLayout.SEABED_DEPTH);
                }
                if (planet.worldSize() > 0) {
                    int limit = planet.worldSize() / 2 - LandmarkLayout.MARGIN;
                    assertTrue(Math.abs(landmark.x()) <= limit && Math.abs(landmark.z()) <= limit);
                }
            }
        }
        assertTrue(checked > 0);
        assertTrue(total > 0, "sea planets should have some landmarks near the landing zone");
    }

    private static List<Landmark> concat(List<Landmark> a, List<Landmark> b) {
        List<Landmark> all = new ArrayList<>(a);
        all.addAll(b);
        return all;
    }
}
