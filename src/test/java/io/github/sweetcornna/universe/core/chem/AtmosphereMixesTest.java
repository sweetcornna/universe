package io.github.sweetcornna.universe.core.chem;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.github.sweetcornna.universe.core.galaxy.Atmosphere.Kind;
import io.github.sweetcornna.universe.core.galaxy.Galaxy;
import io.github.sweetcornna.universe.core.galaxy.GalaxyGenerator;
import io.github.sweetcornna.universe.core.galaxy.Planet;
import io.github.sweetcornna.universe.core.galaxy.PlanetType;
import io.github.sweetcornna.universe.core.galaxy.StarSystem;

/** 星球本来的大气成分（ADR-0036 §二）：照真实的天体定，归一化，种子定了就定了。 */
class AtmosphereMixesTest {
    private static final long[] SEEDS = {0L, 42L, -7L, 20261025L};

    private static List<Planet> bodies(long seed) {
        Galaxy galaxy = GalaxyGenerator.generate(seed, GalaxyGenerator.DEFAULT_SYSTEM_COUNT);
        return galaxy.systems().stream().flatMap(system -> system.bodies().stream()).toList();
    }

    @Test
    void everyMixIsNormalizedAndFitsItsKind() {
        Map<Kind, Integer> seen = new EnumMap<>(Kind.class);
        for (long seed : SEEDS) {
            for (Planet planet : bodies(seed)) {
                AtmosphereMix mix = AtmosphereMixes.base(planet);
                Kind kind = planet.atmosphere().kind();
                seen.merge(kind, 1, Integer::sum);
                if (kind == Kind.NONE) {
                    assertTrue(mix.isEmpty(), planet.id().toString());
                    continue;
                }
                double sum = mix.fractions().values().stream().mapToDouble(Double::doubleValue).sum();
                assertEquals(1, sum, 1e-9, planet.id().toString());
                mix.fractions().values().forEach(f -> assertTrue(f > 0 && f <= 1));
                Gas main = mix.sorted().get(0).getKey();
                if (planet.type() == PlanetType.GAS_GIANT) {
                    assertSame(Gas.HYDROGEN, main);
                    assertTrue(mix.fraction(Gas.HELIUM) > 0.09);
                    continue;
                }
                switch (kind) {
                    case BREATHABLE -> {
                        assertTrue(mix.fraction(Gas.OXYGEN) >= 0.17 && mix.fraction(Gas.OXYGEN) <= 0.26, planet.id().toString());
                        assertSame(Gas.NITROGEN, main);
                        assertTrue(mix.toxicFraction() == 0);
                    }
                    case THIN -> {
                        if (planet.type() == PlanetType.BARREN || planet.type() == PlanetType.IRRADIATED
                                || planet.type() == PlanetType.SCORCHED) {
                            assertSame(Gas.CARBON_DIOXIDE, main, planet.id().toString());
                        }
                        // 稀薄大气里的毒气只有痕量（火星的一氧化碳 0.06%）
                        assertTrue(mix.toxicFraction() < 0.001, planet.id() + " " + mix);
                    }
                    case TOXIC -> assertTrue(mix.exposureIndex(planet.atmosphere().pressure()) > 1, planet.id() + " " + mix);
                    case DENSE -> {
                        assertSame(Gas.CARBON_DIOXIDE, main);
                        assertTrue(mix.fraction(Gas.SULFUR_DIOXIDE) > 0);
                    }
                    default -> {
                    }
                }
            }
        }
        for (Kind kind : Kind.values()) {
            assertTrue(seen.getOrDefault(kind, 0) > 0, "没见到 " + kind);
        }
    }

    @Test
    void homeworldBreathesEarthAir() {
        Planet home = GalaxyGenerator.generate(42L, 16).homePlanet();
        AtmosphereMix mix = AtmosphereMixes.base(home);
        assertEquals(AtmosphereMixes.earth(), mix);
        assertEquals(0.78084, mix.fraction(Gas.NITROGEN), 1e-4);
        assertEquals(0.20946, mix.fraction(Gas.OXYGEN), 1e-4);
        assertEquals(0.00934, mix.fraction(Gas.ARGON), 1e-5);
        assertEquals(0.00042, mix.fraction(Gas.CARBON_DIOXIDE), 1e-6);
        // 氧分压约 21 千帕
        assertEquals(21.2, mix.partial(Gas.OXYGEN, 101.325), 0.1);
    }

    @Test
    void sameSeedSameMix() {
        for (Planet planet : bodies(42L)) {
            assertEquals(AtmosphereMixes.base(planet), AtmosphereMixes.base(planet));
        }
        Planet a = bodies(42L).get(5);
        Planet b = GalaxyGenerator.generate(42L, GalaxyGenerator.DEFAULT_SYSTEM_COUNT).body(a.id()).orElseThrow();
        assertEquals(AtmosphereMixes.base(a), AtmosphereMixes.base(b));
    }

    @Test
    void frozenThinAirIsNitrogenWhenCarbonDioxideFreezesOut() {
        int nitrogen = 0;
        int carbon = 0;
        for (long seed : SEEDS) {
            for (Planet planet : bodies(seed)) {
                if (planet.type() != PlanetType.FROZEN || planet.atmosphere().kind() != Kind.THIN) {
                    continue;
                }
                Gas main = AtmosphereMixes.base(planet).sorted().get(0).getKey();
                if (planet.temperature() < AtmosphereMixes.CO2_FROST) {
                    assertSame(Gas.NITROGEN, main);
                }
                if (planet.temperature() > AtmosphereMixes.CO2_GAS) {
                    assertSame(Gas.CARBON_DIOXIDE, main);
                }
                if (main == Gas.NITROGEN) {
                    nitrogen++;
                } else {
                    carbon++;
                }
            }
        }
        assertTrue(nitrogen > 0, "没有冥王星那样的");
        assertTrue(nitrogen + carbon > 0);
    }

    @Test
    void frozenToxicAirLooksLikeTitan() {
        boolean found = false;
        for (long seed : SEEDS) {
            for (Planet planet : bodies(seed)) {
                if (planet.type() == PlanetType.FROZEN && planet.atmosphere().kind() == Kind.TOXIC) {
                    AtmosphereMix mix = AtmosphereMixes.base(planet);
                    assertSame(Gas.NITROGEN, mix.sorted().get(0).getKey());
                    assertTrue(mix.fraction(Gas.METHANE) >= 0.04);
                    assertTrue(mix.fraction(Gas.HYDROGEN_CYANIDE) > 0);
                    assertTrue(mix.fraction(Gas.AMMONIA) == 0);
                    found = true;
                }
            }
        }
        assertTrue(found);
    }

    @Test
    void exoticBreathableKeepsItsOxygen() {
        for (long seed : SEEDS) {
            for (Planet planet : bodies(seed)) {
                if (planet.type() == PlanetType.EXOTIC && planet.atmosphere().present()) {
                    AtmosphereMix mix = AtmosphereMixes.base(planet);
                    mix.fractions().values().forEach(f -> assertTrue(f > 0));
                    if (planet.atmosphere().breathable()) {
                        assertTrue(mix.fraction(Gas.OXYGEN) >= 0.17);
                        assertTrue(mix.fraction(Gas.NITROGEN) >= 0.29);
                    }
                }
            }
        }
    }

    @Test
    void emptyAndHelpers() {
        assertTrue(AtmosphereMix.of(Map.of()).isEmpty());
        assertTrue(AtmosphereMix.of(Map.of(Gas.ARGON, 0.0)).isEmpty());
        AtmosphereMix mix = AtmosphereMix.of(Map.of(Gas.NITROGEN, 3.0, Gas.SULFUR_DIOXIDE, 1.0));
        assertEquals(0.75, mix.fraction(Gas.NITROGEN), 1e-12);
        assertEquals(0.25, mix.toxicFraction(), 1e-12);
        assertEquals(List.of(Gas.NITROGEN), mix.top(1).stream().map(Map.Entry::getKey).toList());
    }

    /** 接触指数按分压算，几种毒气按 NIOSH 的混合物算法相加。 */
    @Test
    void exposureIndexAddsPartialPressuresOverLimits() {
        // 一个大气压下 35 ppm 一氧化碳正好到限值
        AtmosphereMix co = AtmosphereMix.of(Map.of(Gas.NITROGEN, 1 - 35e-6, Gas.CARBON_MONOXIDE, 35e-6));
        assertEquals(1, co.exposureIndex(1), 1e-9);
        // 同样的分数，气压减半，指数减半；掺氮冲淡分数、分压不变时指数不变
        assertEquals(0.5, co.exposureIndex(0.5), 1e-9);
        AtmosphereMix diluted = AtmosphereMix.of(Map.of(Gas.NITROGEN, 2 - 35e-6, Gas.CARBON_MONOXIDE, 35e-6));
        assertEquals(1, diluted.exposureIndex(2), 1e-9);
        // 一半限值的一氧化碳加一半限值的二氧化硫，合起来正好到限值
        AtmosphereMix both = AtmosphereMix.of(Map.of(Gas.NITROGEN, 1 - 17.5e-6 - 1e-6, Gas.CARBON_MONOXIDE, 17.5e-6,
                Gas.SULFUR_DIOXIDE, 1e-6));
        assertEquals(1, both.exposureIndex(1), 1e-9);
        // 二氧化碳不算毒气，另算一项门槛
        AtmosphereMix air = AtmosphereMix.of(Map.of(Gas.NITROGEN, 0.99, Gas.CARBON_DIOXIDE, 0.01));
        assertEquals(0, air.exposureIndex(1));
        assertEquals(10_000, air.ppmEquivalent(Gas.CARBON_DIOXIDE, 1), 1e-6);
    }

    @Test
    void homeSystemNeighboursAreNotEarth() {
        StarSystem home = GalaxyGenerator.generate(42L, 16).home();
        for (Planet planet : home.bodies()) {
            if (!planet.id().equals(GalaxyGenerator.HOME_PLANET_ID) && planet.atmosphere().present()) {
                assertTrue(!AtmosphereMixes.base(planet).equals(AtmosphereMixes.earth()));
            }
        }
    }
}
