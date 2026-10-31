package io.github.sweetcornna.universe.core.microbe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import io.github.sweetcornna.universe.core.ecology.ResourcePlant;
import io.github.sweetcornna.universe.core.fishing.Habitat;
import io.github.sweetcornna.universe.core.galaxy.Galaxy;
import io.github.sweetcornna.universe.core.galaxy.GalaxyGenerator;
import io.github.sweetcornna.universe.core.galaxy.Hazard;
import io.github.sweetcornna.universe.core.galaxy.Planet;
import io.github.sweetcornna.universe.core.galaxy.PlanetType;
import io.github.sweetcornna.universe.core.galaxy.StarSystem;

class MicrobiomeTest {
    private static final Galaxy GALAXY = GalaxyGenerator.generate(20261016L, 64);

    private static List<Planet> planets() {
        List<Planet> planets = new ArrayList<>();
        for (StarSystem system : GALAXY.systems()) {
            planets.addAll(system.bodies());
        }
        return planets;
    }

    @Test
    void homeIsTheFixedEarthList() {
        Microbiome home = Microbiome.of(GALAXY.homePlanet());
        assertEquals(EarthMicrobes.ALL, home.strains());
        assertEquals(7, home.strains().size());
        assertEquals(List.of("earth:rhizobium_leguminosarum", "earth:synechococcus_elongatus", "earth:methanosarcina_barkeri",
                "earth:acidithiobacillus_ferrooxidans", "earth:psychrobacter_arcticus", "earth:aliivibrio_fischeri",
                "earth:ideonella_sakaiensis"), home.strains().stream().map(MicrobeStrain::key).toList());
        assertEquals(Set.of(Gene.NITROGENASE), EarthMicrobes.RHIZOBIUM.genes());
        assertEquals(Set.of(Gene.OXYGENIC_PHOTOSYNTHESIS, Gene.CARBON_CONCENTRATING), EarthMicrobes.SYNECHOCOCCUS.genes());
        assertEquals(Set.of(Gene.METHANOGENESIS), EarthMicrobes.METHANOSARCINA.genes());
        assertEquals(Set.of(Gene.IRON_SULFUR_OXIDATION, Gene.PROTON_PUMP), EarthMicrobes.ACIDITHIOBACILLUS.genes());
        assertEquals(Set.of(Gene.ANTIFREEZE_PROTEIN), EarthMicrobes.PSYCHROBACTER.genes());
        assertEquals(Set.of(Gene.LUCIFERASE), EarthMicrobes.ALIIVIBRIO.genes());
        assertEquals(Set.of(Gene.PETASE), EarthMicrobes.IDEONELLA.genes());
        assertEquals("豌豆根瘤菌", EarthMicrobes.RHIZOBIUM.nameZh());
        // 北极冷杆菌在 4 °C 下一两天翻一倍（Bakermans 等 2003）
        double coldDoubling = EarthMicrobes.PSYCHROBACTER.doublingHours() / EarthMicrobes.PSYCHROBACTER.growthFactor(277.15);
        assertTrue(coldDoubling > 18 && coldDoubling < 48, "4 °C doubling " + coldDoubling);
        // 快慢的比例照现实：费氏弧菌 1 小时，巴氏甲烷八叠球菌 15 小时
        assertEquals(15, EarthMicrobes.METHANOSARCINA.doublingTicks() / EarthMicrobes.ALIIVIBRIO.doublingTicks(), 1e-9);
        assertEquals("Ideonella sakaiensis", EarthMicrobes.IDEONELLA.nameEn());
        for (MicrobeStrain strain : EarthMicrobes.ALL) {
            assertTrue(strain.earthly());
            Gene signature = strain.metabolism().signature();
            assertTrue(signature == null || strain.has(signature), strain.key());
        }
        // 母星的来源：每种都至少有一种菌
        assertEquals(List.of(SampleSource.SOIL, SampleSource.WATER, SampleSource.ICE, SampleSource.SEDIMENT, SampleSource.ORE,
                SampleSource.ACID, SampleSource.JUNK).stream().sorted().toList(), Microbiome.sources(GALAXY.homePlanet()));
        assertEquals(List.of(EarthMicrobes.SYNECHOCOCCUS, EarthMicrobes.ALIIVIBRIO), home.in(SampleSource.WATER));
    }

    @Test
    void gasGiantsHaveNone() {
        int giants = 0;
        for (Planet planet : planets()) {
            if (planet.type() == PlanetType.GAS_GIANT) {
                giants++;
                assertTrue(Microbiome.of(planet).strains().isEmpty());
                assertTrue(Microbiome.sources(planet).isEmpty());
            }
        }
        assertTrue(giants > 0);
    }

    @Test
    void deterministic() {
        for (Planet planet : planets()) {
            assertEquals(Microbiome.of(planet), Microbiome.of(planet), planet.id().path());
        }
    }

    @Test
    void strainsAreConsistent() {
        int alien = 0;
        for (Planet planet : planets()) {
            Microbiome microbiome = Microbiome.of(planet);
            Set<String> keys = new HashSet<>();
            Set<String> names = new HashSet<>();
            List<SampleSource> sources = Microbiome.sources(planet);
            for (MicrobeStrain strain : microbiome.strains()) {
                String where = planet.id().path() + " " + strain.key() + " " + strain.nameZh();
                assertTrue(keys.add(strain.key()), "duplicate key " + where);
                assertTrue(names.add(strain.nameZh()), "duplicate name " + where);
                assertTrue(strain.minK() < strain.optK() && strain.optK() < strain.maxK(), where);
                assertTrue(strain.maxK() <= MicrobeStrain.MAX_LIFE_K, where);
                assertTrue(strain.minK() >= MicrobeStrain.MIN_LIFE_K, where);
                // 现实里最快的需钠弧菌 10 分钟，慢的岩石内生菌、冷的产甲烷菌要几天
                assertTrue(strain.doublingHours() >= 0.15 && strain.doublingHours() <= 130, where + " " + strain.doublingHours());
                Gene signature = strain.metabolism().signature();
                assertTrue(signature == null || strain.has(signature), where);
                assertTrue(sources.containsAll(strain.sources()), where);
                if (strain.earthly()) {
                    continue;
                }
                assertEquals(1, strain.sources().size(), where);
                alien++;
                assertTrue(strain.key().startsWith(planet.id().path() + "#"), where);
                assertTrue(strain.nameZh().startsWith(planet.name().zh()), where);
                // 嗜冷的有抗冻蛋白、嗜热的有嗜热酶，反过来也一样
                assertEquals(strain.optK() < 290, strain.psychrophile(), where);
                assertEquals(strain.optK() >= 328, strain.thermophile(), where);
                if (strain.metabolism() == Metabolism.METHANOGEN) {
                    assertEquals(Morphology.ARCHAEON, strain.morphology(), where);
                }
                SampleSource source = strain.sources().iterator().next();
                switch (source) {
                    case HOT_SPRING -> assertTrue(strain.thermophile(), where);
                    case ICE -> assertTrue(strain.psychrophile(), where);
                    case ACID -> assertTrue(strain.has(Gene.PROTON_PUMP), where);
                    case ORE -> assertTrue(strain.has(Gene.IRON_SULFUR_OXIDATION), where);
                    case SALT -> assertTrue(strain.has(Gene.COMPATIBLE_SOLUTES), where);
                    default -> {
                    }
                }
                assertFalse(source.tissue(), where);
            }
            if (planet.landable()) {
                // 垃圾里总是大阪堺菌
                assertEquals(List.of(EarthMicrobes.IDEONELLA), microbiome.in(SampleSource.JUNK), planet.id().path());
            }
        }
        assertTrue(alien > 200, "too few alien strains: " + alien);
    }

    @Test
    void lifelessPlanetsOnlyHaveRockAndIce() {
        int lifeless = 0;
        for (Planet planet : planets()) {
            if (!planet.landable() || !planet.ecology().lifeless() || planet.id().equals(GalaxyGenerator.HOME_PLANET_ID)) {
                continue;
            }
            lifeless++;
            for (SampleSource source : Microbiome.sources(planet)) {
                assertTrue(source == SampleSource.ROCK || source == SampleSource.ICE || source == SampleSource.JUNK,
                        planet.id().path() + " " + source);
            }
            assertTrue(Microbiome.of(planet).strains().size() <= 3);
        }
        assertTrue(lifeless > 5, "too few lifeless planets: " + lifeless);
    }

    @Test
    void irradiatedRockHasRadiationResistance() {
        int found = 0;
        for (Planet planet : planets()) {
            if (planet.landable() && planet.hasHazard(Hazard.RADIATION)) {
                for (MicrobeStrain strain : Microbiome.of(planet).in(SampleSource.ROCK)) {
                    if (strain.has(Gene.DNA_REPAIR)) {
                        found++;
                    }
                }
            }
        }
        assertTrue(found > 3, "radiation-resistant rock strains: " + found);
    }

    @Test
    void hotSpringStrainsReachTheLimitsOfLife() {
        double hottest = 0;
        for (Planet planet : planets()) {
            for (MicrobeStrain strain : Microbiome.of(planet).in(SampleSource.HOT_SPRING)) {
                hottest = Math.max(hottest, strain.maxK());
            }
        }
        assertTrue(hottest > 385, "hottest strain grows only up to " + hottest);
    }

    @Test
    void everyGeneCanBeFound() {
        EnumSet<Gene> found = EnumSet.noneOf(Gene.class);
        for (Planet planet : planets()) {
            for (MicrobeStrain strain : Microbiome.of(planet).strains()) {
                found.addAll(strain.genes());
            }
        }
        for (Habitat habitat : Habitat.values()) {
            found.addAll(TissueGenes.fish(habitat));
        }
        for (ResourcePlant plant : ResourcePlant.values()) {
            found.addAll(TissueGenes.plant(plant));
        }
        assertEquals(EnumSet.allOf(Gene.class), found);
        // 只靠微生物也全都找得到
        EnumSet<Gene> microbial = EnumSet.noneOf(Gene.class);
        for (Planet planet : planets()) {
            for (MicrobeStrain strain : Microbiome.of(planet).strains()) {
                microbial.addAll(strain.genes());
            }
        }
        assertEquals(EnumSet.allOf(Gene.class), microbial);
    }
}
