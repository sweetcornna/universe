package io.github.sweetcornna.universe.core.trade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.github.sweetcornna.universe.core.survival.MultitoolClass;
import io.github.sweetcornna.universe.core.survival.MultitoolSpec;
import io.github.sweetcornna.universe.core.tech.Grade;
import io.github.sweetcornna.universe.core.tech.ModuleHost;
import io.github.sweetcornna.universe.core.tech.TechModule;

class MultitoolMarketTest {
    @Test
    void sameDaySameToolsNextDayNew() {
        List<MultitoolMarket.Offer> today = MultitoolMarket.offers(99, Economy.Wealth.AVERAGE, 12);
        assertEquals(MultitoolMarket.TOOLS_PER_DAY, today.size());
        assertEquals(today, MultitoolMarket.offers(99, Economy.Wealth.AVERAGE, 12));
        assertNotEquals(today, MultitoolMarket.offers(99, Economy.Wealth.AVERAGE, 13));
        assertNotEquals(today, MultitoolMarket.offers(100, Economy.Wealth.AVERAGE, 12));
    }

    @Test
    void noSentinelToolsAlienRareRicherStationsSellBetter() {
        Map<MultitoolClass, Integer> types = new EnumMap<>(MultitoolClass.class);
        int[] poor = new int[4];
        int[] wealthy = new int[4];
        for (long day = 0; day < 400; day++) {
            for (MultitoolMarket.Offer offer : MultitoolMarket.offers(7, Economy.Wealth.WEALTHY, day)) {
                types.merge(offer.type(), 1, Integer::sum);
                wealthy[offer.grade().ordinal()]++;
            }
            for (MultitoolMarket.Offer offer : MultitoolMarket.offers(7, Economy.Wealth.POOR, day)) {
                poor[offer.grade().ordinal()]++;
            }
        }
        assertEquals(0, types.getOrDefault(MultitoolClass.SENTINEL, 0));
        assertTrue(types.getOrDefault(MultitoolClass.ALIEN, 0) > 0);
        assertTrue(types.get(MultitoolClass.ALIEN) < types.get(MultitoolClass.PISTOL));
        assertTrue(wealthy[Grade.S.ordinal()] > poor[Grade.S.ordinal()]);
        assertTrue(poor[Grade.C.ordinal()] > wealthy[Grade.C.ordinal()]);
    }

    @Test
    void toolsComeWithModulesForTheMultitool() {
        for (long day = 0; day < 60; day++) {
            for (MultitoolMarket.Offer offer : MultitoolMarket.offers(3, Economy.Wealth.WEALTHY, day)) {
                List<TechModule> modules = offer.modules();
                assertEquals(modules, offer.modules(), "same seed, same modules");
                assertEquals(MultitoolMarket.INSTALLED[offer.grade().ordinal()], modules.size());
                assertTrue(modules.size() <= offer.spec().working());
                assertEquals(modules.size(), modules.stream().map(TechModule::type).distinct().count(), "repeated module");
                for (TechModule module : modules) {
                    assertEquals(ModuleHost.MULTITOOL, module.type().host());
                    assertEquals(offer.grade(), module.grade());
                }
            }
        }
    }

    @Test
    void pricesRiseWithGradeAndTradeInIsHalf() {
        long last = 0;
        for (Grade grade : Grade.values()) {
            long price = MultitoolMarket.price(new MultitoolSpec(MultitoolClass.PISTOL, grade, 0));
            assertTrue(price > last);
            last = price;
        }
        MultitoolSpec alien = new MultitoolSpec(MultitoolClass.ALIEN, Grade.A, 0);
        MultitoolSpec pistol = new MultitoolSpec(MultitoolClass.PISTOL, Grade.A, 0);
        assertTrue(MultitoolMarket.price(alien) > MultitoolMarket.price(pistol));
        assertEquals(MultitoolMarket.price(alien) / 2, MultitoolMarket.tradeIn(alien), 1);
        assertEquals(MultitoolMarket.SENTINEL_TRADE_IN, MultitoolMarket.tradeIn(MultitoolSpec.sentinel(Grade.S)));
        assertEquals(60, MultitoolMarket.legacyTradeIn(120));
        assertEquals(0, MultitoolMarket.legacyTradeIn(-5));
        assertEquals(-1000, MultitoolMarket.due(4000, 5000), "折价比标价高，差额退回");
    }

    @Test
    void tradingSidewaysNeverPaysForItself() {
        // 拿一把买来的工具换同档的任何一把，都要倒贴钱
        List<MultitoolClass> sold = List.of(MultitoolClass.PISTOL, MultitoolClass.RIFLE, MultitoolClass.EXPERIMENTAL, MultitoolClass.ALIEN);
        for (MultitoolClass from : sold) {
            for (Grade grade : Grade.values()) {
                long tradeIn = MultitoolMarket.tradeIn(new MultitoolSpec(from, grade, 0));
                for (MultitoolClass to : sold) {
                    assertTrue(MultitoolMarket.due(MultitoolMarket.price(new MultitoolSpec(to, grade, 0)), tradeIn) > 0);
                }
            }
        }
    }
}
