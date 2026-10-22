package io.github.sweetcornna.universe.core.vehicle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class VehicleKindTest {
    @Test
    void cargoMatchesTheTable() {
        assertEquals(27, VehicleKind.EXOCRAFT.cargoSlots());
        assertEquals(18, VehicleKind.HOVER_CAR.cargoSlots());
        assertEquals(54, VehicleKind.HEAVY_ROVER.cargoSlots());
        assertEquals(27, VehicleKind.EXO_MECH.cargoSlots());
        assertEquals(18, VehicleKind.SUBMARINE.cargoSlots());
    }

    @Test
    void topSpeedsMatchTheTable() {
        assertEquals(RoverDrive.MAX_SPEED, WheelDrive.ROVER.maxSpeed(), 1e-12);
        assertEquals(RoverDrive.MAX_SPEED * 1.4, WheelDrive.HOVER.maxSpeed(), 1e-12, "悬浮车最快");
        assertEquals(RoverDrive.MAX_SPEED * 0.7, WheelDrive.HEAVY.maxSpeed(), 1e-12, "重型车慢");
        assertEquals(1.6, VehicleKind.SUBMARINE.speedFactor(), 1e-9);
        assertEquals(0.6, VehicleKind.EXO_MECH.speedFactor(), 1e-9);
        for (VehicleKind kind : VehicleKind.values()) {
            assertTrue(kind.speedFactor() <= VehicleKind.HOVER_CAR.speedFactor() || kind == VehicleKind.SUBMARINE,
                    "陆上悬浮车最快：" + kind);
        }
    }

    @Test
    void driveProfilesReachTheirTopSpeed() {
        for (WheelDrive drive : new WheelDrive[] {WheelDrive.ROVER, WheelDrive.HOVER, WheelDrive.HEAVY}) {
            double v = 0;
            for (int i = 0; i < 400; i++) {
                v = drive.speed(v, 1, true, true);
            }
            assertEquals(drive.maxSpeed(), v, 1e-9);
        }
    }

    @Test
    void heavyRoverStartsSlowerButBrakesHarder() {
        assertTrue(WheelDrive.HEAVY.acceleration() < WheelDrive.ROVER.acceleration());
        assertTrue(WheelDrive.HEAVY.brake() > WheelDrive.ROVER.brake());
    }

    @Test
    void hoverCarSlidesFurther() {
        int rover = coastTicks(WheelDrive.ROVER, WheelDrive.ROVER.maxSpeed());
        int hover = coastTicks(WheelDrive.HOVER, WheelDrive.ROVER.maxSpeed());
        assertTrue(hover > rover * 1.5, "悬浮车松了油门滑得远：" + hover + " / " + rover);
    }

    private static int coastTicks(WheelDrive drive, double speed) {
        int ticks = 0;
        while (speed > 0 && ticks < 10000) {
            speed = drive.speed(speed, 0, true, true);
            ticks++;
        }
        return ticks;
    }

    @Test
    void onlySubmarinesNeedWater() {
        for (VehicleKind kind : VehicleKind.values()) {
            assertEquals(kind == VehicleKind.SUBMARINE, kind.aquatic(), kind.id());
        }
    }

    @Test
    void lookupById() {
        for (VehicleKind kind : VehicleKind.values()) {
            assertEquals(kind, VehicleKind.byId(kind.id()));
        }
        assertNull(VehicleKind.byId("spaceship"));
    }
}
