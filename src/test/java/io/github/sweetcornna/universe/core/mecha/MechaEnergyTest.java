package io.github.sweetcornna.universe.core.mecha;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MechaEnergyTest {
    @Test
    void fuelBurnsIntoTheCoreAtTheChamberPower() {
        MechaEnergy energy = new MechaEnergy();
        assertTrue(energy.wantsFuel(1000));
        energy.addFuel(100);
        assertFalse(energy.wantsFuel(1000));
        assertEquals(40, energy.burn(1000, 40), 1e-9);
        assertEquals(40, energy.burn(1000, 40), 1e-9);
        // 最后只剩 20
        assertEquals(20, energy.burn(1000, 40), 1e-9);
        assertEquals(100, energy.energy(), 1e-9);
        assertEquals(0, energy.burning(), 1e-9);
        assertEquals(0, energy.burn(1000, 40), 1e-9);
        assertTrue(energy.wantsFuel(1000));
    }

    @Test
    void fullCoreStopsBurningAndKeepsTheRest() {
        MechaEnergy energy = new MechaEnergy(90, 0, 0);
        energy.addFuel(100);
        assertEquals(10, energy.burn(100, 40), 1e-9);
        assertEquals(90, energy.burning(), 1e-9);
        assertEquals(100, energy.burningTotal(), 1e-9);
        assertEquals(0, energy.burn(100, 40), 1e-9);
        // 满着不再要燃料
        assertFalse(energy.wantsFuel(100));
        energy.draw(30);
        assertEquals(30, energy.burn(100, 40), 1e-9);
    }

    @Test
    void drawingMoreThanTheCoreHoldsEmptiesIt() {
        MechaEnergy energy = new MechaEnergy(50, 0, 0);
        assertTrue(energy.draw(20));
        assertEquals(30, energy.energy(), 1e-9);
        assertFalse(energy.draw(31));
        assertTrue(energy.empty());
        assertTrue(energy.draw(0));
    }

    @Test
    void chargingStopsAtCapacity() {
        MechaEnergy energy = new MechaEnergy(990, 0, 0);
        assertEquals(10, energy.room(1000), 1e-9);
        assertEquals(10, energy.charge(750, 1000), 1e-9);
        assertEquals(0, energy.charge(750, 1000), 1e-9);
        assertEquals(1, energy.share(1000), 1e-9);
        energy.clamp(500);
        assertEquals(500, energy.energy(), 1e-9);
    }

    @Test
    void drainsFollowTheDesign() {
        // 悬停 1.2 kW，每 m/s 100 W
        assertEquals(60 + 5 * 11, MechaEnergy.flight(11), 1e-9);
        // 机械骨骼只算多出来的速度：两倍速时一半
        assertEquals(5 * 8 * 0.5, MechaEnergy.frame(8, 1.0), 1e-9);
        assertEquals(0, MechaEnergy.frame(8, 0), 1e-9);
        // 6 m/s 的无人机 360 W
        assertEquals(18, MechaEnergy.drone(6), 1e-9);
    }

    @Test
    void logisticsSlotsAskForTheGap() {
        LogisticsSlot slot = new LogisticsSlot("x:belt", 64, 128);
        assertEquals(128, slot.supply(0));
        assertEquals(65, slot.supply(63));
        assertEquals(0, slot.supply(64));
        assertEquals(0, slot.collect(128));
        assertEquals(2, slot.collect(130));
        // 上下限都是 0：全部收走
        LogisticsSlot trash = new LogisticsSlot("x:stone", 0, 0);
        assertEquals(0, trash.supply(0));
        assertEquals(5, trash.collect(5));
        // 上限小于下限时拉到下限，超出范围的截断
        assertEquals(new LogisticsSlot("x:a", 10, 10), new LogisticsSlot("x:a", 10, 3));
        assertEquals(LogisticsSlot.LIMIT, new LogisticsSlot("x:a", 0, 100_000).max());
        assertEquals(0, LogisticsSlot.EMPTY.supply(0));
        assertTrue(LogisticsSlot.EMPTY.isEmpty());
    }
}
