package io.github.sweetcornna.universe.core.ecology;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import io.github.sweetcornna.universe.core.galaxy.Galaxy;
import io.github.sweetcornna.universe.core.galaxy.GalaxyGenerator;
import io.github.sweetcornna.universe.core.galaxy.Planet;

class PlacedFloraTest {
    private static final Galaxy GALAXY = GalaxyGenerator.generate(20261022L, 48);

    private static List<Flora> floras() {
        return GALAXY.systems().stream().flatMap(s -> s.bodies().stream()).filter(Planet::landable).map(Flora::of).toList();
    }

    @Test
    void picksASpeciesOfTheSameFormFromThisPlanet() {
        int checked = 0;
        for (Flora flora : floras()) {
            for (PlantSpecies species : flora.plants()) {
                for (Biotope biotope : species.biotopes()) {
                    PlantSpecies placed = PlacedFlora.plant(flora, species.form(), biotope, PlacedFlora.hash(checked, 64, -checked));
                    assertNotNull(placed);
                    assertEquals(species.form(), placed.form());
                    assertTrue(flora.plants().contains(placed));
                    // 有长在这个生境的就挑那些
                    assertTrue(placed.biotopes().contains(biotope), placed + " 不长在 " + biotope);
                    checked++;
                }
            }
        }
        assertTrue(checked > 50, "检查的太少：" + checked);
    }

    @Test
    void fallsBackToAnyBiotopeAndToNullWithoutTheForm() {
        for (Flora flora : floras()) {
            for (PlantForm form : PlantForm.values()) {
                boolean has = flora.plants().stream().anyMatch(p -> p.form() == form);
                PlantSpecies placed = PlacedFlora.plant(flora, form, null, 12345);
                assertEquals(has, placed != null, form + " 在 " + flora.plants());
            }
        }
        assertNull(PlacedFlora.plant(Flora.NEUTRAL, PlantForm.values()[0], Biotope.MEADOW, 1));
        assertNull(PlacedFlora.cap(Flora.NEUTRAL, Biotope.FUNGAL, 1));
    }

    @Test
    void samePositionSamePickAndDifferentPositionsSpreadOut() {
        for (Flora flora : floras()) {
            for (PlantForm form : PlantForm.values()) {
                List<PlantSpecies> options = flora.plants().stream().filter(p -> p.form() == form).toList();
                if (options.size() < 2) {
                    continue;
                }
                long pos = PlacedFlora.hash(3, 70, 9);
                assertEquals(PlacedFlora.plant(flora, form, null, pos), PlacedFlora.plant(flora, form, null, pos));
                Set<PlantSpecies> seen = new HashSet<>();
                for (int x = 0; x < 400; x++) {
                    seen.add(PlacedFlora.plant(flora, form, null, PlacedFlora.hash(x, 70, x * 7)));
                }
                // 多度差得再多，400 个位置里也该挑到不止一种
                assertTrue(seen.size() >= 2, form + " 只挑到 " + seen);
                return;
            }
        }
        throw new AssertionError("没有一颗星球同一形态有两种植物");
    }

    @Test
    void capsComeFromMushroomsAndColumns() {
        int found = 0;
        for (Flora flora : floras()) {
            TreeSpecies cap = PlacedFlora.cap(flora, null, 77);
            boolean has = flora.trees().stream().anyMatch(t -> t.shape() == TreeShape.MUSHROOM || t.shape() == TreeShape.COLUMN);
            assertEquals(has, cap != null);
            if (cap != null) {
                assertTrue(cap.shape() == TreeShape.MUSHROOM || cap.shape() == TreeShape.COLUMN);
                found++;
            }
        }
        assertTrue(found > 0, "没有一颗星球长巨菌或柱状肉茎");
    }
}
