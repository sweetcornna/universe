package io.github.sweetcornna.universe.core.ship;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.github.sweetcornna.universe.core.galaxy.StarClass;
import io.github.sweetcornna.universe.core.space.DriveTier;
import io.github.sweetcornna.universe.core.tech.Grade;
import io.github.sweetcornna.universe.core.tech.ModuleType;
import io.github.sweetcornna.universe.core.tech.TechBonuses;
import io.github.sweetcornna.universe.core.tech.TechModule;

class ShipRatingTest {
    private static Map<ShipPart, Integer> parts(Object... partsAndCounts) {
        Map<ShipPart, Integer> counts = new EnumMap<>(ShipPart.class);
        for (int i = 0; i < partsAndCounts.length; i += 2) {
            counts.merge((ShipPart) partsAndCounts[i], (Integer) partsAndCounts[i + 1], Integer::sum);
        }
        return counts;
    }

    @Test
    void plainShipIsAClassCShuttle() {
        ShipRating rating = ShipRating.of(parts(ShipPart.HULL, 30, ShipPart.HELM, 1, ShipPart.THRUSTER, 4, ShipPart.FUEL_TANK, 1), 0);
        assertEquals(ShipType.SHUTTLE, rating.type());
        assertEquals(Grade.C, rating.grade());
        assertEquals(3, rating.slots());
        assertEquals(0, rating.bonus(), 1e-9);
    }

    @Test
    void typeFollowsTheStrongestSpecialty() {
        assertEquals(ShipType.FIGHTER, ShipRating.of(parts(ShipPart.HELM, 1, ShipPart.PHOTON_CANNON, 2), 0).type());
        assertEquals(ShipType.HAULER, ShipRating.of(parts(ShipPart.HELM, 1, ShipPart.PHOTON_CANNON, 1), 54).type());
        assertEquals(ShipType.EXPLORER, ShipRating.of(parts(ShipPart.HELM, 1, ShipPart.WARP_DRIVE, 2), 27).type());
        // 一门炮不够格：3 分 < 6 分
        assertEquals(ShipType.SHUTTLE, ShipRating.of(parts(ShipPart.HELM, 1, ShipPart.PHOTON_CANNON, 1), 0).type());
    }

    @Test
    void gradeFollowsAdvancedPartsAndAlloy() {
        // 一半功能部件是 Mk.II：35 分，B
        assertEquals(Grade.B, ShipRating.of(parts(ShipPart.HELM, 1, ShipPart.THRUSTER, 2, ShipPart.THRUSTER_MK2, 2), 0).grade());
        // 全是 Mk.II：70 分，A
        assertEquals(Grade.A, ShipRating.of(parts(ShipPart.HELM, 1, ShipPart.THRUSTER_MK2, 4), 0).grade());
        // 再加一半的合金船体板：70 + 15，S
        ShipRating s = ShipRating.of(parts(ShipPart.HELM, 1, ShipPart.THRUSTER_MK2, 4, ShipPart.ALLOY_PLATING, 5), 0);
        assertEquals(Grade.S, s.grade());
        assertEquals(6, s.slots());
        assertEquals(0.35, s.bonus(), 1e-9);
    }

    @Test
    void specialtyBonusGoesToTheTypesStat() {
        ShipStats base = ShipStats.of(ShipPart.HELM, 1, ShipPart.WARP_DRIVE_MK2, 2, ShipPart.THRUSTER_MK2, 4, ShipPart.SHIELD_GENERATOR, 1);
        ShipRating explorerS = new ShipRating(ShipType.EXPLORER, Grade.S, 90);
        ShipStats tuned = base.tuned(explorerS, TechBonuses.NONE, DriveTier.BASIC);
        assertEquals(base.warpRange() * 1.35, tuned.warpRange(), 1e-9);
        assertEquals(base.shield(), tuned.shield(), 1e-9);
        ShipStats hauler = base.tuned(new ShipRating(ShipType.HAULER, Grade.A, 60), TechBonuses.NONE, DriveTier.BASIC);
        assertEquals(base.shield() * 1.2, hauler.shield(), 1e-9);
        assertEquals(base.hull() * 1.2, hauler.hull(), 1e-9);
        ShipStats shuttle = base.tuned(new ShipRating(ShipType.SHUTTLE, Grade.B, 30), TechBonuses.NONE, DriveTier.BASIC);
        assertEquals(1 / 1.1, shuttle.fuelUse(), 1e-9);
        ShipStats fighter = base.tuned(new ShipRating(ShipType.FIGHTER, Grade.B, 30), TechBonuses.NONE, DriveTier.BASIC);
        assertEquals(1.1, fighter.damage(), 1e-9);
    }

    @Test
    void modulesAddRangeAndShield() {
        ShipStats base = ShipStats.of(ShipPart.HELM, 1, ShipPart.WARP_DRIVE, 1, ShipPart.THRUSTER, 4, ShipPart.SHIELD_GENERATOR, 1);
        TechModule booster = new TechModule(ModuleType.HYPERDRIVE_BOOSTER, Grade.A, List.of(5.0));
        TechModule deflector = new TechModule(ModuleType.DEFLECTOR, Grade.B, List.of(0.2));
        ShipStats tuned = base.tuned(ShipRating.DEFAULT, TechBonuses.of(List.of(booster, deflector)), DriveTier.CADMIUM);
        assertEquals(base.warpRange() + 5, tuned.warpRange(), 1e-9);
        assertEquals(base.shield() * 1.2, tuned.shield(), 1e-9);
        assertEquals(DriveTier.CADMIUM, tuned.drive());
        // 没有跃迁引擎时技术也不给航程
        ShipStats noWarp = ShipStats.of(ShipPart.HELM, 1, ShipPart.THRUSTER, 4).tuned(ShipRating.DEFAULT, TechBonuses.of(List.of(booster)), DriveTier.BASIC);
        assertEquals(0, noWarp.warpRange(), 1e-9);
    }

    @Test
    void driveTiersGateStarClasses() {
        double basic = 0;
        double total = 0;
        for (StarClass star : StarClass.values()) {
            total += star.weight();
            if (DriveTier.required(star) == DriveTier.BASIC) {
                basic += star.weight();
            }
        }
        // 大约八成的恒星不要档次
        assertTrue(basic / total > 0.75 && basic / total < 0.85, "basic share " + basic / total);
        assertTrue(DriveTier.BASIC.reaches(StarClass.G));
        assertFalse(DriveTier.BASIC.reaches(StarClass.RED_GIANT));
        assertTrue(DriveTier.CADMIUM.reaches(StarClass.RED_GIANT));
        assertFalse(DriveTier.CADMIUM.reaches(StarClass.A));
        assertTrue(DriveTier.EMERIL.reaches(StarClass.WHITE_DWARF));
        assertFalse(DriveTier.EMERIL.reaches(StarClass.O));
        assertTrue(DriveTier.INDIUM.reaches(StarClass.BLACK_HOLE));
    }
}
