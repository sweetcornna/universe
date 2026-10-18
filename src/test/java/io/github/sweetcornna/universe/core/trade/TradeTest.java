package io.github.sweetcornna.universe.core.trade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import io.github.sweetcornna.universe.core.galaxy.Galaxy;
import io.github.sweetcornna.universe.core.galaxy.GalaxyGenerator;
import io.github.sweetcornna.universe.core.galaxy.StarSystem;
import io.github.sweetcornna.universe.core.trade.ValueGraph.Input;
import io.github.sweetcornna.universe.core.trade.ValueGraph.Recipe;

class TradeTest {
    private static final Map<String, Double> RAW = Map.of("ore", 4.0, "coal", 3.0, "copper", 4.0);

    @Test
    void valuesClimbTheRecipeChain() {
        List<Recipe> recipes = List.of(
                Recipe.of("ingot", 1, 1, new Input(List.of("ore"), 1)),
                Recipe.of("board", 2, 1, new Input(List.of("ingot"), 2), new Input(List.of("copper"), 1)),
                Recipe.of("chip", 1, 10, new Input(List.of("board"), 4), new Input(List.of("ingot"), 2)));
        Map<String, Double> values = ValueGraph.compute(RAW, recipes);
        double ingot = (4 + ValueGraph.SECOND_VALUE) * (1 + ValueGraph.MARKUP);
        assertEquals(ingot, values.get("ingot"), 1e-9);
        double board = (2 * ingot + 4 + ValueGraph.SECOND_VALUE) * (1 + ValueGraph.MARKUP) / 2;
        assertEquals(board, values.get("board"), 1e-9);
        assertTrue(values.get("chip") > 4 * board + 2 * ingot, "加工品比原料之和贵");
        assertEquals(4.0, values.get("ore"));
    }

    @Test
    void cheapestRouteWinsAndCyclesDoNotInflate() {
        List<Recipe> recipes = List.of(
                Recipe.of("ingot", 1, 1, new Input(List.of("ore"), 1)),
                // 绕远路：用两个锭做一个锭，不会把价钱抬上去
                Recipe.of("ingot", 1, 1, new Input(List.of("ingot"), 2)),
                // 可选原料取便宜的
                Recipe.of("plate", 1, 1, new Input(List.of("ingot", "coal"), 1)),
                // 原料表里的东西不按配方改价
                Recipe.of("ore", 4, 1, new Input(List.of("coal"), 1)),
                // 推不出来的原料：这个配方不算
                Recipe.of("mystery", 1, 1, new Input(List.of("unobtainium"), 1)));
        Map<String, Double> values = ValueGraph.compute(RAW, recipes);
        assertEquals((4 + ValueGraph.SECOND_VALUE) * (1 + ValueGraph.MARKUP), values.get("ingot"), 1e-9);
        assertEquals((3 + ValueGraph.SECOND_VALUE) * (1 + ValueGraph.MARKUP), values.get("plate"), 1e-9);
        assertEquals(4.0, values.get("ore"));
        assertFalse(values.containsKey("mystery"));
    }

    @Test
    void byproductsShareTheCost() {
        Recipe cracking = new Recipe(List.of(new Input(List.of("ore"), 2)),
                List.of(new ValueGraph.Output("a", 1), new ValueGraph.Output("b", 3)), 0);
        Map<String, Double> values = ValueGraph.compute(RAW, List.of(cracking));
        assertEquals(8 * (1 + ValueGraph.MARKUP) / 4, values.get("a"), 1e-9);
        assertEquals(values.get("a"), values.get("b"), 1e-9);
    }

    @Test
    void economiesAreSeededAndCoverAllTypes() {
        Galaxy galaxy = GalaxyGenerator.generate(12345L, GalaxyGenerator.DEFAULT_SYSTEM_COUNT);
        Map<Economy.Type, Integer> types = new EnumMap<>(Economy.Type.class);
        Set<Economy.Wealth> wealths = new HashSet<>();
        for (StarSystem system : galaxy.systems()) {
            Economy economy = Economy.of(system);
            assertEquals(economy, Economy.of(system), "同一个恒星系总是同一种经济");
            types.merge(economy.type(), 1, Integer::sum);
            wealths.add(economy.wealth());
        }
        assertEquals(Economy.HOME, Economy.of(galaxy.systems().get(0)));
        assertTrue(types.size() >= 6, "64 个恒星系里各种经济都有：" + types);
        assertEquals(3, wealths.size());
    }

    @Test
    void demandAndSupplyShiftPrices() {
        Economy mining = new Economy(Economy.Type.MINING, Economy.Wealth.AVERAGE);
        double component = mining.sellPrice(100, TradeCategory.COMPONENT);
        double raw = mining.sellPrice(100, TradeCategory.RAW);
        double neutral = mining.sellPrice(100, TradeCategory.BIO);
        assertTrue(component > neutral && neutral > raw, "缺的收得贵，产的收得便宜");
        assertTrue(mining.buyPrice(100, TradeCategory.RAW) < mining.buyPrice(100, TradeCategory.BIO), "自己产的卖得便宜");
        Economy rich = new Economy(Economy.Type.MINING, Economy.Wealth.WEALTHY);
        assertTrue(rich.sellPrice(100, TradeCategory.BIO) > neutral);
        assertTrue(rich.buyPrice(100, TradeCategory.BIO) < mining.buyPrice(100, TradeCategory.BIO));
    }

    @Test
    void sellingFloodsTheMarketAndRecovers() {
        Market market = new Market(Economy.HOME, List.of());
        double first = market.sellUnitPrice("ore", 4, TradeCategory.RAW, 0);
        double stack = market.sell("ore", 4, TradeCategory.RAW, 64, 0);
        assertTrue(stack < first * 64 && stack > first * 64 * 0.7, "一组矿石压价两三成：" + stack / (first * 64));
        double after = market.sellUnitPrice("ore", 4, TradeCategory.RAW, 0);
        assertTrue(after < first * 0.8);
        // 分两次卖和一次卖完拿到的一样
        Market a = new Market(Economy.HOME, List.of());
        Market b = new Market(Economy.HOME, List.of());
        double once = a.sell("chip", 3000, TradeCategory.ADVANCED, 10, 0);
        double twice = b.sell("chip", 3000, TradeCategory.ADVANCED, 4, 0) + b.sell("chip", 3000, TradeCategory.ADVANCED, 6, 0);
        assertEquals(once, twice, 1e-6);
        // 两天后压价减半
        assertEquals(64, market.pressure("ore", 0), 1e-9);
        assertEquals(32, market.pressure("ore", Market.HALF_LIFE), 1e-9);
        assertTrue(market.sellUnitPrice("ore", 4, TradeCategory.RAW, Market.HALF_LIFE * 6) > first * 0.97);
    }

    @Test
    void buyingDrainsStockAndNeverUndercutsSelling() {
        Economy mining = new Economy(Economy.Type.MINING, Economy.Wealth.WEALTHY);
        Market.Listing fuel = new Market.Listing("fuel", 50, TradeCategory.ENERGY, 20, 20, 0);
        Market market = new Market(mining, List.of(fuel));
        double full = market.buyUnitPrice(fuel, 0);
        // 采矿经济缺能源：收购价比卖价的基准还高，卖价要托底
        assertTrue(full >= mining.sellPrice(50, TradeCategory.ENERGY) * Market.SPREAD - 1e-9);
        double cost = market.buy(fuel, 15, 0);
        assertTrue(cost > 15 * full - 1e-9);
        assertEquals(5, fuel.stock(0));
        assertTrue(market.buyUnitPrice(fuel, 0) > full);
        assertTrue(market.buy(fuel, 6, 0) < 0, "存货不够买不了");
        assertEquals(5, fuel.stock(0));
        assertEquals(15, fuel.stock(Market.RESTOCK / 2));
        assertEquals(20, fuel.stock(Market.RESTOCK * 3));
        // 任何一样东西：同一座空间站买进卖出都亏
        for (Economy.Type type : Economy.Type.values()) {
            for (Economy.Wealth wealth : Economy.Wealth.values()) {
                Economy economy = new Economy(type, wealth);
                for (TradeCategory category : TradeCategory.values()) {
                    Market.Listing listing = new Market.Listing("x", 100, category, 10, 10, 0);
                    Market m = new Market(economy, List.of(listing));
                    assertTrue(m.buyUnitPrice(listing, 0) > m.sellUnitPrice("x", 100, category, 0), type + " " + wealth + " " + category);
                }
            }
        }
    }

    @Test
    void stationsStockStaplesAndLocalGoods() {
        List<Goods> catalog = List.of(
                new Goods("warper", 80, TradeCategory.ENERGY, true, Economy.Wealth.POOR),
                new Goods("cannon", 9000, TradeCategory.TECH, true, Economy.Wealth.WEALTHY),
                Goods.of("ore_a", 4, TradeCategory.RAW), Goods.of("ore_b", 5, TradeCategory.RAW),
                Goods.of("board", 12, TradeCategory.COMPONENT), Goods.of("gear", 10, TradeCategory.COMPONENT));
        Economy poorMining = new Economy(Economy.Type.MINING, Economy.Wealth.POOR);
        List<String> items = Goods.stock(1, poorMining, catalog, 0).stream().map(Market.Listing::item).toList();
        assertEquals(List.of("ore_a", "ore_b", "warper"), items);
        Economy richFactory = new Economy(Economy.Type.MANUFACTURING, Economy.Wealth.WEALTHY);
        List<Market.Listing> listings = Goods.stock(1, richFactory, catalog, 0);
        assertEquals(List.of("board", "cannon", "gear", "warper"), listings.stream().map(Market.Listing::item).toList());
        // 贸易经济从所有大类里挑
        Economy trading = new Economy(Economy.Type.TRADING, Economy.Wealth.AVERAGE);
        assertEquals(5, Goods.stock(1, trading, catalog, 0).size());
        Market.Listing cannon = listings.get(1);
        assertTrue(cannon.capacity() >= 1 && cannon.capacity() < 10);
        assertNotEquals(cannon.capacity(), listings.get(0).capacity());
    }
}
