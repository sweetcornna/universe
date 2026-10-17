package io.github.sweetcornna.universe.core.sky;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.sweetcornna.universe.core.galaxy.Galaxy;
import io.github.sweetcornna.universe.core.galaxy.GalaxyGenerator;
import io.github.sweetcornna.universe.core.galaxy.Planet;
import io.github.sweetcornna.universe.core.galaxy.StarSystem;

class SkyModelTest {
    private static final Galaxy GALAXY = GalaxyGenerator.generate(20261016L, 64);

    @Test
    void everyBodyOfTheSystemIsInTheSky() {
        for (StarSystem system : GALAXY.systems().subList(0, 10)) {
            for (Planet observer : system.bodies()) {
                List<SkyBody> sky = SkyModel.bodies(system, observer, 123.4);
                long stars = sky.stream().filter(b -> b.kind() == SkyBody.Kind.STAR).count();
                assertEquals(1, stars);
                // 恒星、其他行星、自己的卫星（站在卫星上时是母行星和它的其他卫星）；别的行星的卫星太小不画
                int moons = observer.moon()
                        ? system.planets().get(observer.id().planet()).moons().size()
                        : observer.moons().size();
                assertEquals(system.planets().size() + moons, sky.size(), observer.id().toString());
                for (SkyBody body : sky) {
                    assertTrue(body.angularDiameter() > 0 && body.angularDiameter() <= 60, body.toString());
                    assertTrue(body.illumination() >= 0 && body.illumination() <= 1, body.toString());
                    assertTrue(Math.abs(body.longitude()) <= Math.PI + 1e-9);
                    assertTrue(Math.abs(body.latitude()) <= Math.PI / 2 + 1e-9);
                }
                // 远的先画
                for (int i = 1; i < sky.size(); i++) {
                    assertTrue(sky.get(i - 1).distance() >= sky.get(i).distance());
                }
            }
        }
    }

    @Test
    void outerPlanetAtOppositionIsFullAndOppositeTheStar() {
        StarSystem system = GALAXY.systems().stream()
                .filter(s -> s.planets().size() >= 3)
                .findFirst().orElseThrow();
        Planet inner = system.planets().get(0);
        Planet outer = system.planets().get(system.planets().size() - 1);
        // 扫一段时间，找外行星离冲日最近的时刻
        double best = 0;
        double bestDays = 0;
        for (double days = 0; days < 4000; days += 0.5) {
            SkyBody body = find(SkyModel.bodies(system, inner, days), outer);
            double away = Math.abs(body.longitude());
            if (away > best) {
                best = away;
                bestDays = days;
            }
        }
        SkyBody atOpposition = find(SkyModel.bodies(system, inner, bestDays), outer);
        assertTrue(best > Math.PI * 0.95, "never near opposition: " + best);
        assertTrue(atOpposition.illumination() > 0.95, "not full at opposition: " + atOpposition.illumination());
    }

    @Test
    void moonsSeeTheirParentLarge() {
        for (StarSystem system : GALAXY.systems()) {
            for (Planet planet : system.planets()) {
                for (Planet moon : planet.moons()) {
                    SkyBody parent = SkyModel.bodies(system, moon, 10).stream()
                            .filter(b -> b.kind() == SkyBody.Kind.PARENT).findFirst().orElseThrow();
                    assertTrue(parent.angularDiameter() >= 8, moon.id() + " sees parent at " + parent.angularDiameter());
                    return;
                }
            }
        }
    }

    @Test
    void airlessSkyIsBlackWithDaytimeStars() {
        for (StarSystem system : GALAXY.systems()) {
            for (Planet planet : system.bodies()) {
                SkyColors colors = SkyColors.of(planet, system.star());
                if (!planet.atmosphere().present()) {
                    assertEquals(0x000000, colors.daySky());
                    assertEquals(1.0, colors.daytimeStars());
                    assertEquals(0, colors.cloudAlpha());
                    assertEquals(0, colors.sunsetAlpha());
                } else {
                    assertTrue(colors.scatter() >= 0.15);
                    assertTrue(colors.fogEnd() > colors.fogStart());
                }
            }
        }
    }

    @Test
    void starFieldIsDeterministicAndOnTheUnitSphere() {
        StarField a = StarField.generate(42, 500);
        StarField b = StarField.generate(42, 500);
        assertEquals(500, a.count());
        float[] d = a.data();
        for (int i = 0; i < d.length; i++) {
            assertEquals(d[i], b.data()[i]);
        }
        for (int i = 0; i < a.count(); i++) {
            int o = i * StarField.STRIDE;
            double len = Math.sqrt(d[o] * d[o] + d[o + 1] * d[o + 1] + d[o + 2] * d[o + 2]);
            assertEquals(1, len, 1e-4);
        }
    }

    @Test
    void complementKeepsBrightness() {
        // 偏红的天空，日落偏蓝
        int sunset = SkyColors.complement(0xC08060);
        assertTrue((sunset & 0xFF) > (sunset >> 16 & 0xFF));
        assertEquals(0xC0, Math.max(sunset >> 16 & 0xFF, Math.max(sunset >> 8 & 0xFF, sunset & 0xFF)));
    }

    private static SkyBody find(List<SkyBody> sky, Planet planet) {
        return sky.stream().filter(b -> b.planet() == planet).findFirst().orElseThrow();
    }
}
