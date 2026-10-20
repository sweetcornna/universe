package io.github.sweetcornna.universe.core.mission;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import io.github.sweetcornna.universe.core.trade.Economy;
import io.github.sweetcornna.universe.core.trade.Goods;
import io.github.sweetcornna.universe.core.trade.TradeCategory;

class MissionBoardTest {
    private static final Economy MINING = new Economy(Economy.Type.MINING, Economy.Wealth.AVERAGE);
    private static final List<Goods> CATALOG = List.of(
            Goods.of("universe:circuit_board", 20, TradeCategory.COMPONENT),
            Goods.of("universe:gear", 8, TradeCategory.COMPONENT),
            Goods.of("minecraft:iron_ingot", 5, TradeCategory.REFINED),
            Goods.of("universe:hydrogen", 6, TradeCategory.ENERGY),
            Goods.of("minecraft:dirt", 0.5, TradeCategory.RAW),
            Goods.of("universe:quantum_chip", 900, TradeCategory.COMPONENT),
            new Goods("universe:space_warper", 120, TradeCategory.ENERGY, true, Economy.Wealth.POOR));
    private static final Map<String, Goods> BY_ID = CATALOG.stream().collect(Collectors.toMap(Goods::item, Function.identity()));

    @Test
    void sameDayGivesSameMissions() {
        assertEquals(MissionBoard.offers(42, 3, MINING, 10, CATALOG, 8), MissionBoard.offers(42, 3, MINING, 10, CATALOG, 8));
    }

    @Test
    void threeDifferentKindsADay() {
        for (long day = 0; day < 50; day++) {
            List<Mission> offers = MissionBoard.offers(42, 3, MINING, day, CATALOG, 8);
            assertEquals(3, offers.size());
            Set<MissionKind> kinds = new HashSet<>();
            for (int slot = 0; slot < 3; slot++) {
                Mission mission = offers.get(slot);
                assertEquals(slot, mission.slot());
                assertEquals(3, mission.system());
                assertEquals(day, mission.day());
                kinds.add(mission.kind());
            }
            assertEquals(3, kinds.size(), "第 " + day + " 天");
        }
    }

    @Test
    void missionsChangeFromDayToDay() {
        Set<List<Mission>> seen = new HashSet<>();
        for (long day = 0; day < 10; day++) {
            seen.add(MissionBoard.offers(42, 3, MINING, day, CATALOG, 8).stream().map(m -> new Mission(0, 0, m.slot(), m.kind(), m.item(),
                    m.count(), m.reward(), m.bonus(), m.bonusCount())).toList());
        }
        assertTrue(seen.size() > 5);
        assertNotEquals(MissionBoard.offers(42, 3, MINING, 10, CATALOG, 8), MissionBoard.offers(43, 3, MINING, 10, CATALOG, 8));
    }

    @Test
    void deliveryAsksForWhatTheStationLacks() {
        int deliveries = 0;
        for (long day = 0; day < 200; day++) {
            for (Mission mission : MissionBoard.offers(7, 1, MINING, day, CATALOG, 8)) {
                if (mission.kind() != MissionKind.DELIVERY) {
                    assertEquals("", mission.item());
                    continue;
                }
                deliveries++;
                Goods goods = BY_ID.get(mission.item());
                // 采矿经济缺零件和能源；泥土太便宜、量子芯片太贵、空间翘曲器是常备品，都不要
                assertTrue(MINING.type().demands(goods.category()), mission.item());
                assertTrue(goods.value() >= 4 && goods.value() <= 250 && !goods.staple(), mission.item());
                assertTrue(mission.count() >= 2 && mission.count() <= 64);
                double total = goods.value() * mission.count();
                assertTrue(total >= 290 && total <= 910, "总价 " + total);
                assertEquals(Math.round(total * 1.6 / 10) * 10, mission.reward());
            }
        }
        assertTrue(deliveries > 100);
    }

    @Test
    void tradingEconomyTakesAnyCategory() {
        Economy trading = new Economy(Economy.Type.TRADING, Economy.Wealth.AVERAGE);
        List<Goods> wanted = MissionBoard.wanted(trading, CATALOG);
        assertEquals(List.of("minecraft:iron_ingot", "universe:circuit_board", "universe:gear", "universe:hydrogen"),
                wanted.stream().map(Goods::item).toList());
    }

    @Test
    void noDeliveryWithoutWantedGoodsAndNoSurveyWithoutLife() {
        for (long day = 0; day < 50; day++) {
            List<Mission> offers = MissionBoard.offers(9, 2, MINING, day, List.of(), 0);
            assertEquals(3, offers.size());
            for (Mission mission : offers) {
                assertTrue(mission.kind() == MissionKind.BOUNTY || mission.kind() == MissionKind.MINING);
            }
        }
    }

    @Test
    void countsAndRewards() {
        for (long day = 0; day < 200; day++) {
            for (Mission mission : MissionBoard.offers(11, 4, MINING, day, CATALOG, 3)) {
                switch (mission.kind()) {
                    case BOUNTY -> {
                        assertTrue(mission.count() >= 2 && mission.count() <= 5);
                        assertEquals(mission.count() * 220L, mission.reward());
                    }
                    case MINING -> {
                        assertTrue(mission.count() >= 4 && mission.count() <= 10);
                        assertEquals(mission.count() * 70L, mission.reward());
                    }
                    case SURVEY -> {
                        // 只有 3 种动植物，最多要 3 种
                        assertTrue(mission.count() >= 2 && mission.count() <= 3);
                        assertEquals(mission.count() * 150L, mission.reward());
                    }
                    case DELIVERY -> {
                    }
                }
            }
        }
    }

    @Test
    void wealthScalesRewards() {
        Economy poor = new Economy(Economy.Type.MINING, Economy.Wealth.POOR);
        Economy wealthy = new Economy(Economy.Type.MINING, Economy.Wealth.WEALTHY);
        List<Mission> a = MissionBoard.offers(5, 1, poor, 3, CATALOG, 8);
        List<Mission> b = MissionBoard.offers(5, 1, wealthy, 3, CATALOG, 8);
        for (int i = 0; i < 3; i++) {
            // 同一个种子、同一天，任务一样，只有报酬不同
            assertEquals(a.get(i).kind(), b.get(i).kind());
            assertEquals(a.get(i).count(), b.get(i).count());
            assertTrue(b.get(i).reward() > a.get(i).reward());
        }
        assertEquals(0.85, MissionBoard.factor(Economy.Wealth.POOR));
        assertEquals(1.25, MissionBoard.factor(Economy.Wealth.WEALTHY));
    }

    @Test
    void bonusesAreOccasional() {
        int total = 0;
        int navigation = 0;
        int module = 0;
        for (long day = 0; day < 1000; day++) {
            for (Mission mission : MissionBoard.offers(13, 5, MINING, day, CATALOG, 8)) {
                total++;
                switch (mission.bonus()) {
                    case NAVIGATION_DATA -> {
                        navigation++;
                        assertTrue(mission.bonusCount() >= 1 && mission.bonusCount() <= 3);
                    }
                    case MODULE -> {
                        module++;
                        assertEquals(1, mission.bonusCount());
                    }
                    case NONE -> assertEquals(0, mission.bonusCount());
                }
            }
        }
        assertEquals(0.25, navigation / (double) total, 0.03);
        assertEquals(0.12, module / (double) total, 0.03);
    }

    @Test
    void progress() {
        Mission bounty = new Mission(1, 0, 0, MissionKind.BOUNTY, "", 4, 880, Mission.Bonus.NONE, 0);
        assertEquals(0, MissionBoard.progress(bounty, 10, 10, 0));
        assertEquals(3, MissionBoard.progress(bounty, 13, 10, 0));
        assertEquals(4, MissionBoard.progress(bounty, 20, 10, 0));
        Mission survey = new Mission(1, 0, 1, MissionKind.SURVEY, "", 3, 450, Mission.Bonus.NONE, 0);
        assertEquals(2, MissionBoard.progress(survey, 99, 0, 2));
        Mission delivery = new Mission(1, 0, 2, MissionKind.DELIVERY, "minecraft:iron_ingot", 30, 240, Mission.Bonus.NONE, 0);
        assertEquals(0, MissionBoard.progress(delivery, 99, 0, 9));
        assertFalse(bounty.id().equals(survey.id()));
        assertEquals("1:0:0", bounty.id());
    }

    @Test
    void dayOfGameTime() {
        assertEquals(0, MissionBoard.day(0));
        assertEquals(0, MissionBoard.day(23_999));
        assertEquals(1, MissionBoard.day(24_000));
    }
}
