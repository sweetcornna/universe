package io.github.sweetcornna.universe.core.ship;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.github.sweetcornna.universe.core.tech.Grade;
import io.github.sweetcornna.universe.core.tech.ModuleType;
import io.github.sweetcornna.universe.core.tech.TechModule;
import io.github.sweetcornna.universe.core.trade.Economy;

class ShipMarketTest {
    @Test
    void sameDaySameShipsNextDayNew() {
        List<ShipMarket.Offer> today = ShipMarket.ships(99, Economy.Wealth.AVERAGE, 12);
        assertEquals(ShipMarket.SHIPS_PER_DAY, today.size());
        assertEquals(today, ShipMarket.ships(99, Economy.Wealth.AVERAGE, 12));
        assertNotEquals(today, ShipMarket.ships(99, Economy.Wealth.AVERAGE, 13));
    }

    @Test
    void betterShipsComeWithMoreTech() {
        for (long day = 0; day < 60; day++) {
            for (ShipMarket.Offer offer : ShipMarket.ships(7, Economy.Wealth.WEALTHY, day)) {
                List<TechModule> modules = offer.modules();
                assertEquals(modules, offer.modules(), "same seed, same modules");
                assertTrue(modules.size() <= ShipMarket.INSTALLED[offer.grade().ordinal()]);
                assertTrue(modules.size() <= ShipRating.slots(offer.grade()), "more modules than slots");
                assertEquals(modules.size(), modules.stream().map(TechModule::type).distinct().count(), "repeated module");
                Map<ShipPart, Integer> parts = offer.design().parts();
                for (TechModule module : modules) {
                    assertEquals(offer.grade(), module.grade());
                    if (module.type() == ModuleType.FIRE_CONTROL) {
                        assertTrue(parts.getOrDefault(ShipPart.PHOTON_CANNON, 0) > 0, "fire control on an unarmed ship");
                    }
                }
                if (offer.grade() == Grade.C) {
                    assertTrue(modules.isEmpty());
                }
            }
        }
    }

    @Test
    void richSystemsSellBetterShips() {
        Map<Grade, Integer> poor = new EnumMap<>(Grade.class);
        Map<Grade, Integer> rich = new EnumMap<>(Grade.class);
        for (long day = 0; day < 500; day++) {
            for (ShipMarket.Offer o : ShipMarket.ships(5, Economy.Wealth.POOR, day)) {
                poor.merge(o.grade(), 1, Integer::sum);
            }
            for (ShipMarket.Offer o : ShipMarket.ships(5, Economy.Wealth.WEALTHY, day)) {
                rich.merge(o.grade(), 1, Integer::sum);
            }
        }
        assertEquals(0, poor.getOrDefault(Grade.S, 0));
        assertTrue(rich.getOrDefault(Grade.A, 0) > poor.getOrDefault(Grade.A, 0) * 2);
        assertTrue(rich.getOrDefault(Grade.S, 0) > 0);
    }

    @Test
    void pricesScaleWithGrade() {
        assertEquals(1000, ShipMarket.price(1000, Grade.C));
        assertEquals(2400, ShipMarket.price(1000, Grade.S));
        assertEquals(600, ShipMarket.resale(1000));
        assertEquals(120000, ShipMarket.freighterPrice(Grade.C));
        int freighterDays = 0;
        for (long day = 0; day < 400; day++) {
            if (ShipMarket.freighter(17, Economy.Wealth.AVERAGE, day).isPresent()) {
                freighterDays++;
            }
        }
        assertTrue(freighterDays > 60 && freighterDays < 140, "days " + freighterDays);
    }
}
