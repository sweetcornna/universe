package io.github.sweetcornna.universe.core.galaxy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

class GalaxyGeneratorTest {
    private static final long[] SEEDS = {0L, 42L, -7L, 20261016L, 123456789L};

    @Test
    void sameSeedGivesSameGalaxy() {
        assertEquals(GalaxyGenerator.generate(42L, 64), GalaxyGenerator.generate(42L, 64));
    }

    @Test
    void differentSeedsGiveDifferentGalaxies() {
        assertNotEquals(GalaxyGenerator.generate(42L, 64), GalaxyGenerator.generate(43L, 64));
    }

    @Test
    void cacheReturnsSameInstance() {
        assertSame(GalaxyGenerator.get(99L, 16), GalaxyGenerator.get(99L, 16));
    }

    @Test
    void homeSystemFollowsTheDesign() {
        for (long seed : SEEDS) {
            Galaxy galaxy = GalaxyGenerator.generate(seed, 64);
            StarSystem home = galaxy.home();
            assertEquals(0, home.index());
            assertEquals(0, home.x());
            assertEquals(Star.HOME, home.star());
            assertEquals(4, home.planets().size());

            Planet homeworld = galaxy.homePlanet();
            assertEquals(GalaxyGenerator.HOME_PLANET_ID, homeworld.id());
            assertEquals(PlanetType.HABITABLE, homeworld.type());
            assertEquals(1.0, homeworld.gravity());
            assertTrue(homeworld.atmosphere().breathable());
            assertEquals(1, homeworld.moons().size());

            Planet moon = homeworld.moons().getFirst();
            assertEquals(GalaxyGenerator.HOME_MOON_ID, moon.id());
            assertEquals("月球", moon.name().zh());
            assertEquals(PlanetType.BARREN, moon.type());
            assertFalse(moon.atmosphere().present());
            assertTrue(moon.landable());
            // 路线图 M2 的退出标准：飞到卫星采集钛
            assertTrue(moon.abundance(Resource.TITANIUM) >= 0.4);

            assertEquals(PlanetType.BARREN, home.planets().get(2).type());
            Planet giant = home.planets().get(3);
            assertEquals(PlanetType.GAS_GIANT, giant.type());
            assertFalse(giant.landable());
            assertFalse(giant.moons().isEmpty());
        }
    }

    @Test
    void bodiesHaveSaneParameters() {
        for (long seed : SEEDS) {
            Galaxy galaxy = GalaxyGenerator.generate(seed, 64);
            for (StarSystem system : galaxy.systems()) {
                double lastOrbit = 0;
                for (Planet planet : system.planets()) {
                    assertTrue(planet.orbit().radius() > lastOrbit, "行星应按轨道由内到外排列");
                    lastOrbit = planet.orbit().radius();
                    checkBody(planet);
                    for (Planet moon : planet.moons()) {
                        assertTrue(moon.moon());
                        assertTrue(moon.moons().isEmpty(), "卫星不该再有卫星");
                        assertEquals(planet.id(), moon.id().parent());
                        checkBody(moon);
                    }
                }
            }
        }
    }

    private static void checkBody(Planet body) {
        String where = body.id() + " " + body.type();
        assertTrue(body.hazards().size() <= 2, where + " 危害超过两种");
        assertTrue(body.temperature() >= 20 && body.temperature() < 5000, where + " 温度 " + body.temperature());
        assertTrue(body.gravity() > 0.03 && body.gravity() < 2.7, where + " 重力 " + body.gravity());
        if (body.landable()) {
            int min = body.moon() ? 2048 : 8192;
            int max = body.moon() ? 5120 : 30208;
            if (!body.id().equals(GalaxyGenerator.HOME_PLANET_ID)) {
                assertTrue(body.worldSize() >= min && body.worldSize() <= max, where + " 世界大小 " + body.worldSize());
                assertEquals(0, body.worldSize() % 512);
            }
        } else {
            assertEquals(PlanetType.GAS_GIANT, body.type());
        }
        if (body.type() == PlanetType.HABITABLE) {
            assertTrue(body.atmosphere().breathable(), where);
            assertEquals(Liquid.WATER, body.liquid(), where);
        }
        if (!body.atmosphere().present()) {
            assertEquals(WeatherProfile.Precipitation.NONE, body.weather().precipitation(), where + " 真空里不该有天气");
        }
        if (body.liquid() == Liquid.NONE) {
            assertTrue(body.terrain().baseHeight() > body.terrain().seaLevel(), where + " 没有液体时陆地应高于海平面");
        }
        assertEquals(body.id(), BodyId.parse(body.id().path()).orElseThrow());
    }

    @Test
    void galaxyHasVarietyAndEnoughDestinations() {
        Map<PlanetType, Integer> types = new EnumMap<>(PlanetType.class);
        Set<StarClass> stars = EnumSet.noneOf(StarClass.class);
        Set<DayCycle> days = EnumSet.noneOf(DayCycle.class);
        Set<WeatherProfile.Precipitation> weathers = EnumSet.noneOf(WeatherProfile.Precipitation.class);
        for (long seed : SEEDS) {
            Galaxy galaxy = GalaxyGenerator.generate(seed, 64);
            int landable = 0;
            for (StarSystem system : galaxy.systems()) {
                stars.add(system.star().starClass());
                for (Planet body : system.bodies()) {
                    types.merge(body.type(), 1, Integer::sum);
                    days.add(body.dayCycle());
                    weathers.add(body.weather().precipitation());
                    if (body.landable()) {
                        landable++;
                    }
                }
            }
            // 立项书：上百颗可登陆星球
            assertTrue(landable >= 150 && landable <= 700, "可登陆星球 " + landable + " 颗");
        }
        assertEquals(EnumSet.allOf(PlanetType.class), types.keySet(), "每种星球类型都应出现：" + types);
        assertTrue(stars.size() >= 9, "恒星类型太少：" + stars);
        assertEquals(EnumSet.allOf(DayCycle.class), days);
        assertEquals(EnumSet.allOf(WeatherProfile.Precipitation.class), weathers);
    }

    @Test
    void someRockyPlanetsHaveRings() {
        int ringed = 0;
        for (long seed : SEEDS) {
            Galaxy galaxy = GalaxyGenerator.generate(seed, 64);
            assertFalse(galaxy.homePlanet().rings(), "母星不带环");
            for (StarSystem system : galaxy.systems()) {
                for (Planet body : system.bodies()) {
                    if (body.rings() && body.type() != PlanetType.GAS_GIANT) {
                        assertFalse(body.id().isMoon(), "卫星不带环：" + body.id());
                        if (body.landable()) {
                            ringed++;
                        }
                    }
                }
            }
        }
        // 每个星系十来颗能登陆、带环的岩石行星
        assertTrue(ringed >= 5 * 8, "带环的可登陆岩石行星只有 " + ringed + " 颗");
    }

    @Test
    void systemsAreSpreadOut() {
        Galaxy galaxy = GalaxyGenerator.generate(42L, 64);
        double nearestSum = 0;
        for (StarSystem a : galaxy.systems()) {
            double nearest = Double.MAX_VALUE;
            for (StarSystem b : galaxy.systems()) {
                if (a != b) {
                    nearest = Math.min(nearest, a.distanceTo(b));
                }
            }
            assertTrue(nearest >= 1.25, "恒星系挨得太近：" + nearest);
            nearestSum += nearest;
        }
        double average = nearestSum / galaxy.systems().size();
        assertTrue(average > 3 && average < 8, "平均最近邻距离 " + average + " 光年");
    }

    @Test
    void bodyLookupWorks() {
        Galaxy galaxy = GalaxyGenerator.generate(42L, 64);
        assertEquals(galaxy.homePlanet(), galaxy.body(GalaxyGenerator.HOME_PLANET_ID).orElseThrow());
        assertEquals(galaxy.homePlanet().moons().getFirst(), galaxy.body(GalaxyGenerator.HOME_MOON_ID).orElseThrow());
        assertTrue(galaxy.body(BodyId.planet(64, 0)).isEmpty());
        assertTrue(galaxy.body(BodyId.planet(0, 9)).isEmpty());
        assertTrue(galaxy.body(BodyId.moon(0, 1, 5)).isEmpty());
        assertTrue(BodyId.parse("s1/p2/m3").isPresent());
        assertTrue(BodyId.parse("s1/x2").isEmpty());
    }
}
