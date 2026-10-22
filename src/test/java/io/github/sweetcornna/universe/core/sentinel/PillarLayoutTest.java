package io.github.sweetcornna.universe.core.sentinel;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.github.sweetcornna.universe.core.galaxy.BodyId;
import io.github.sweetcornna.universe.core.galaxy.Galaxy;
import io.github.sweetcornna.universe.core.galaxy.GalaxyGenerator;
import io.github.sweetcornna.universe.core.galaxy.Liquid;
import io.github.sweetcornna.universe.core.galaxy.Planet;
import io.github.sweetcornna.universe.core.poi.LandmarkLayout;
import io.github.sweetcornna.universe.core.poi.Poi;
import io.github.sweetcornna.universe.core.poi.PoiLayout;
import io.github.sweetcornna.universe.core.portal.Glyphs;
import io.github.sweetcornna.universe.core.terrain.TerrainModel;

class PillarLayoutTest {
    private static final Galaxy GALAXY = GalaxyGenerator.generate(20261016L, 64);

    private static List<Planet> planets() {
        return GALAXY.systems().stream().flatMap(s -> s.bodies().stream()).toList();
    }

    private static Planet planet(String id) {
        return GALAXY.body(BodyId.parse(id).orElseThrow()).orElseThrow();
    }

    /** 和 {@code PlanetCommand.landingColumn} 一样从原点螺旋往外找第一块平坦的陆地。 */
    private static int[] landing(Planet planet) {
        TerrainModel model = TerrainModel.of(planet);
        for (int ring = 0; ring <= 64; ring++) {
            int r = ring * 48;
            int steps = Math.max(1, ring * 8);
            for (int i = 0; i < steps; i++) {
                double angle = Math.PI * 2 * i / steps;
                int x = (int) Math.round(Math.cos(angle) * r);
                int z = (int) Math.round(Math.sin(angle) * r);
                TerrainModel.Sample s = model.sample(x, z);
                if (s.elevation() > 3 && s.elevation() < 90
                        && Math.abs(model.sample(x + 4, z).height() - s.height()) < 3
                        && Math.abs(model.sample(x, z + 4).height() - s.height()) < 3) {
                    return new int[] {x, z};
                }
            }
        }
        return new int[] {0, 0};
    }

    private static PoiLayout.Ground ground(boolean dry) {
        return new PoiLayout.Ground() {
            @Override
            public double height(int x, int z) {
                return 70;
            }

            @Override
            public boolean dry(int x, int z) {
                return dry;
            }
        };
    }

    @Test
    void pinnedSites() {
        // 存档格式：改了算法，老存档里还没放下的塔会挪地方
        assertArrayEquals(new int[] {643, 74}, PillarLayout.site(planet("s0/p0"), landing(planet("s0/p0"))).orElseThrow());
        assertArrayEquals(new int[] {193, -706}, PillarLayout.site(planet("s0/p1/m0"), landing(planet("s0/p1/m0"))).orElseThrow());
        assertArrayEquals(new int[] {-345, -477}, PillarLayout.site(planet("s0/p2/m0"), landing(planet("s0/p2/m0"))).orElseThrow());
        // 验收用的存档（种子 43）里的海洋星球齐宇西，降落点 (48, 0)
        Galaxy world = GalaxyGenerator.fromGalaxySeed(GalaxyGenerator.galaxySeed(43), GalaxyGenerator.DEFAULT_SYSTEM_COUNT);
        Planet ocean = world.body(BodyId.parse("s54/p2/m0").orElseThrow()).orElseThrow();
        assertArrayEquals(new int[] {48, 0}, landing(ocean));
        assertArrayEquals(new int[] {-468, 551}, PillarLayout.site(ocean, landing(ocean)).orElseThrow());
    }

    @Test
    void everyPlanetWithSentinelsHasOnePillarInsideTheRingAndTheBorder() {
        int pillars = 0;
        for (Planet planet : planets()) {
            String id = planet.id() + " " + planet.type();
            assertEquals(planet.landable() && SentinelLevel.of(planet).present(), PillarLayout.has(planet), id);
            int[] landing = landing(planet);
            Optional<int[]> site = PillarLayout.site(planet, landing);
            assertEquals(PillarLayout.has(planet), site.isPresent(), id);
            if (site.isEmpty()) {
                continue;
            }
            pillars++;
            int x = site.get()[0];
            int z = site.get()[1];
            assertArrayEquals(site.get(), PillarLayout.site(planet, landing).orElseThrow(), id);
            double distance = Math.hypot(x - landing[0], z - landing[1]);
            // 坐标取整，最多差一格
            assertTrue(distance >= PillarLayout.MIN_DISTANCE - 1 && distance <= PillarLayout.MAX_DISTANCE + 1, id + " " + distance);
            int limit = PillarLayout.limit(planet.worldSize());
            assertTrue(Math.abs(x) <= limit && Math.abs(z) <= limit, id);
            int[] portal = Glyphs.offset(planet.seed());
            assertTrue(Math.hypot(x - landing[0] - portal[0], z - landing[1] - portal[1]) > PillarLayout.RADIUS + PillarLayout.SPACING, id);
            for (Poi poi : PoiLayout.of(planet).within(x, z, 64)) {
                assertTrue(poi.distance(x, z) >= PillarLayout.RADIUS + poi.type().radius() + PillarLayout.SPACING, id + " " + poi);
            }
        }
        assertTrue(pillars > 100, "too few pillars: " + pillars);
    }

    @Test
    void mostPillarsStandOnDryLand() {
        int pillars = 0;
        int dry = 0;
        for (Planet planet : planets()) {
            Optional<int[]> site = PillarLayout.site(planet, landing(planet));
            if (site.isEmpty()) {
                continue;
            }
            pillars++;
            TerrainModel model = TerrainModel.of(planet);
            double height = model.sample(site.get()[0], site.get()[1]).height();
            if (planet.liquid() == Liquid.NONE || height >= model.seaLevel() + 1) {
                dry++;
            }
            assertTrue(PillarLayout.estimatedY(planet, site.get()[0], site.get()[1]) >= (int) Math.floor(height));
        }
        assertTrue(dry >= pillars * 0.95, dry + " / " + pillars);
    }

    @Test
    void prefersDryFlatGround() {
        PoiLayout.Ground half = new PoiLayout.Ground() {
            @Override
            public double height(int x, int z) {
                return 70;
            }

            @Override
            public boolean dry(int x, int z) {
                return x >= 0;
            }
        };
        for (long seed = 1; seed <= 20; seed++) {
            int[] site = PillarLayout.site(seed, 0, 0, 0, half, LandmarkLayout.Blocked.NONE);
            assertTrue(site[0] - PillarLayout.RADIUS >= 0, seed + ": " + site[0]);
        }
        // 太陡也不行
        PoiLayout.Ground steep = new PoiLayout.Ground() {
            @Override
            public double height(int x, int z) {
                return x >= 0 ? 70 : 70 + z * 2;
            }

            @Override
            public boolean dry(int x, int z) {
                return true;
            }
        };
        assertTrue(PillarLayout.site(3, 0, 0, 0, steep, LandmarkLayout.Blocked.NONE)[0] >= PillarLayout.RADIUS);
    }

    @Test
    void wetPlanetsStillGetAPillar() {
        for (long seed = 1; seed <= 20; seed++) {
            int[] site = PillarLayout.site(seed, 100, -50, 4096, ground(false), LandmarkLayout.Blocked.NONE);
            double distance = Math.hypot(site[0] - 100, site[1] + 50);
            assertTrue(distance >= PillarLayout.MIN_DISTANCE - 1 && distance <= PillarLayout.MAX_DISTANCE + 1);
        }
    }

    @Test
    void staysAwayFromBlockedPlacesAndInsideTheBorder() {
        LandmarkLayout.Blocked west = (x, z, radius) -> x - radius < 0;
        for (long seed = 1; seed <= 20; seed++) {
            assertTrue(PillarLayout.site(seed, 0, 0, 0, ground(true), west)[0] >= PillarLayout.RADIUS);
            // 降落点在角落：塔只能往里放
            int[] corner = PillarLayout.site(seed, 950, 950, 2048, ground(true), LandmarkLayout.Blocked.NONE);
            int limit = PillarLayout.limit(2048);
            assertTrue(Math.abs(corner[0]) <= limit && Math.abs(corner[1]) <= limit);
        }
        // 到处都撞：从降落点朝星球中心挪
        int[] fallback = PillarLayout.site(5, 900, 0, 2048, ground(true), (x, z, radius) -> true);
        assertArrayEquals(new int[] {900 - PillarLayout.MIN_DISTANCE, 0}, fallback);
        assertFalse(Math.abs(fallback[0]) > PillarLayout.limit(2048));
    }
}
