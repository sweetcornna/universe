package io.github.sweetcornna.universe.core.ship;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.sweetcornna.universe.core.space.DriveTier;
import io.github.sweetcornna.universe.core.tech.Grade;
import io.github.sweetcornna.universe.core.tech.ModuleType;
import io.github.sweetcornna.universe.core.tech.Stat;
import io.github.sweetcornna.universe.core.tech.TechModule;

class ShipTechTest {
    private static final TechModule HULL = TechModule.typical(ModuleType.HULL_REINFORCEMENT, Grade.A);
    private static final TechModule BOOSTER = TechModule.typical(ModuleType.HYPERDRIVE_BOOSTER, Grade.S);

    @Test
    void onlyUnlockedSlotsCount() {
        List<ShipTech.Slot> slots = List.of(
                new ShipTech.Slot(0, HULL, null),
                new ShipTech.Slot(2, null, DriveTier.CADMIUM),
                new ShipTech.Slot(4, BOOSTER, null),
                new ShipTech.Slot(5, null, DriveTier.INDIUM));
        // C 档 3 格：第 4、5 格的模块和铟驱动不算
        ShipTech c = ShipTech.of(slots, ShipRating.slots(Grade.C));
        assertEquals(List.of(HULL), c.modules());
        assertEquals(DriveTier.CADMIUM, c.drive());
        // S 档 6 格：都算，驱动取最高的一档
        ShipTech s = ShipTech.of(slots, ShipRating.slots(Grade.S));
        assertEquals(List.of(HULL, BOOSTER), s.modules());
        assertEquals(DriveTier.INDIUM, s.drive());
        assertSame(ShipTech.NONE, ShipTech.of(List.of(), 6));
        assertSame(ShipTech.NONE, ShipTech.of(List.of(new ShipTech.Slot(5, HULL, null)), 3));
    }

    @Test
    void techTunesTheStats() {
        ShipStats base = ShipStats.of(ShipPart.HELM, 1, ShipPart.THRUSTER, 4, ShipPart.FUEL_TANK, 2, ShipPart.WARP_DRIVE, 1, ShipPart.HULL, 10);
        ShipRating rating = ShipRating.DEFAULT;
        ShipTech tech = new ShipTech(List.of(HULL, BOOSTER), DriveTier.EMERIL);
        ShipStats tuned = base.tuned(rating, tech.bonuses(), tech.drive());
        assertEquals(base.hull() * (1 + HULL.value(Stat.HULL)), tuned.hull(), 1e-9);
        // 超空间增幅加的是光年的绝对值
        assertEquals(base.warpRange() + BOOSTER.value(Stat.WARP_RANGE), tuned.warpRange(), 1e-9);
        assertEquals(DriveTier.EMERIL, tuned.drive());
        assertTrue(tuned.drive().reaches(io.github.sweetcornna.universe.core.galaxy.StarClass.WHITE_DWARF));
    }
}
