package io.github.sweetcornna.universe.core.research;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import io.github.sweetcornna.universe.core.research.Upgrade.Infinite;
import io.github.sweetcornna.universe.core.research.Upgrade.Tier;

class UpgradeTest {
    /** 两种矩阵各由一个科技解锁；升级线 u 前两级用蓝，第 3 级用蓝红，之后无限用白。 */
    private static final TechTree TREE = new TechTree(List.of(
            Tech.lab("blue", List.of(), List.of("x:start"), 1, List.of("x:blue")),
            Tech.lab("red", List.of("blue"), List.of("x:blue"), 1, List.of("x:red")),
            Tech.lab("white", List.of("red"), List.of("x:red"), 1, List.of("x:white"))),
            List.of(
                    new Upgrade("u", "x:icon", List.of("blue"), List.of(
                            new Tier(List.of("x:blue"), 2), new Tier(List.of("x:blue"), 3), new Tier(List.of("x:blue", "x:red"), 4)),
                            new Infinite(List.of("x:white"), 10, 5)),
                    new Upgrade("f", "x:icon", List.of("blue"), List.of(new Tier(List.of("x:blue"), 1)), null)));

    @Test
    void pointsGrowLinearlyInTheInfiniteTail() {
        Upgrade u = TREE.upgrade("u").orElseThrow();
        assertEquals(2, u.points(1));
        assertEquals(4, u.points(3));
        assertEquals(10, u.points(4));
        assertEquals(15, u.points(5));
        assertEquals(List.of("x:white"), u.matrices(9));
        assertTrue(u.isInfiniteLevel(4));
        assertFalse(u.isInfiniteLevel(3));
        assertEquals(Integer.MAX_VALUE, u.maxLevel());
        assertEquals(Integer.MAX_VALUE, u.points(Integer.MAX_VALUE));
        Upgrade f = TREE.upgrade("f").orElseThrow();
        assertEquals(1, f.maxLevel());
        assertThrows(IllegalArgumentException.class, () -> f.points(2));
        assertThrows(IllegalArgumentException.class, () -> u.points(0));
    }

    @Test
    void nextLevelNeedsTheTechsOfItsMatrices() {
        ResearchState state = new ResearchState();
        assertFalse(state.available(TREE, "u"));
        state.complete("blue");
        assertTrue(state.available(TREE, "u"));
        state.setLevel("u", 2);
        // 第 3 级要红矩阵
        assertFalse(state.available(TREE, "u"));
        assertEquals(List.of("blue", "red"), TREE.requirements(TREE.upgrade("u").orElseThrow(), 3));
        state.complete("red");
        assertTrue(state.available(TREE, "u"));
        state.setLevel("u", 3);
        assertFalse(state.available(TREE, "u"));
        state.complete("white");
        assertTrue(state.available(TREE, "u"));
    }

    @Test
    void finishingALevelKeepsGoingWhileTheMatricesStayTheSame() {
        ResearchState state = new ResearchState(Set.of("blue", "red", "white"), null, Map.of());
        assertTrue(state.start(TREE, "u"));
        assertEquals(List.of("x:blue"), state.currentMatrices(TREE));
        assertNull(state.addPoint(TREE));
        assertEquals("u", state.addPoint(TREE));
        assertEquals(1, state.level("u"));
        assertEquals(0, state.progress("u"));
        // 第 2 级也用蓝矩阵：接着研究
        assertEquals("u", state.current());
        assertEquals(3, state.pointsNeeded(TREE, "u"));
        state.addPoint(TREE);
        state.addPoint(TREE);
        assertEquals("u", state.addPoint(TREE));
        // 第 3 级换成蓝红：停下来让玩家自己选
        assertEquals(2, state.level("u"));
        assertNull(state.current());
        assertTrue(state.start(TREE, "u"));
        for (int i = 0; i < 4; i++) {
            state.addPoint(TREE);
        }
        assertEquals(3, state.level("u"));
        assertNull(state.current());
        // 无限档一直是白矩阵：一级接一级
        assertTrue(state.start(TREE, "u"));
        for (int i = 0; i < 10 + 15; i++) {
            state.addPoint(TREE);
        }
        assertEquals(5, state.level("u"));
        assertEquals("u", state.current());
        assertEquals(List.of("x:white"), state.currentMatrices(TREE));
    }

    @Test
    void finiteLinesStopAtTheirLastLevel() {
        ResearchState state = new ResearchState(Set.of("blue"), null, Map.of());
        Upgrade f = TREE.upgrade("f").orElseThrow();
        assertTrue(state.start(TREE, "f"));
        assertEquals("f", state.addPoint(TREE));
        assertTrue(state.isMaxed(f));
        assertNull(state.current());
        assertFalse(state.available(TREE, "f"));
        assertFalse(state.start(TREE, "f"));
        assertEquals(0, state.pointsNeeded(TREE, "f"));
        assertEquals(List.of(), state.currentMatrices(TREE));
    }

    @Test
    void levelsSurviveARoundTripAndClear() {
        ResearchState state = new ResearchState(Set.of("blue"), "u", Map.of("u", 1), Map.of("u", 2, "f", 0));
        assertEquals(2, state.level("u"));
        assertEquals(Map.of("u", 2), state.levels());
        state.setLevel("u", 0);
        assertEquals(0, state.level("u"));
        state.setLevel("f", 1);
        state.clear();
        assertEquals(Map.of(), state.levels());
    }

    @Test
    void treeRejectsBadUpgrades() {
        List<Tech> techs = List.of(Tech.lab("t", List.of(), List.of("x:m"), 1, List.of()));
        List<Tier> tier = List.of(new Tier(List.of("x:m"), 1));
        assertThrows(IllegalArgumentException.class, () -> new TechTree(techs, List.of(new Upgrade("t", "x:i", List.of(), tier, null))));
        assertThrows(IllegalArgumentException.class, () -> new TechTree(techs, List.of(new Upgrade("u", "x:i", List.of("nope"), tier, null))));
        assertThrows(IllegalArgumentException.class, () -> new Upgrade("u", "x:i", List.of(), List.of(), null));
    }

    @Test
    void defaultUpgradesMatchTheDesign() {
        TechTree tree = TechTree.DEFAULT;
        assertEquals(18, tree.upgrades().size());
        // 有限几级：矿脉利用 4、研究速度 3、载具引擎 5、射线 4；容量和太阳帆 6 级封顶
        assertEquals(4, tree.upgrade(Upgrades.VEIN_UTILIZATION).orElseThrow().tiers().size());
        assertEquals(3, tree.upgrade(Upgrades.RESEARCH_SPEED).orElseThrow().tiers().size());
        assertEquals(5, tree.upgrade(Upgrades.CARRIER_ENGINE).orElseThrow().tiers().size());
        assertEquals(4, tree.upgrade(Upgrades.RAY_EFFICIENCY).orElseThrow().tiers().size());
        assertEquals(6, tree.upgrade(Upgrades.CARRIER_CAPACITY).orElseThrow().maxLevel());
        assertEquals(6, tree.upgrade(Upgrades.SAIL_LIFETIME).orElseThrow().maxLevel());
        // 货物集装两级就叠到 4 个；宇宙探索 4 级看得到整个银河
        assertEquals(2, tree.upgrade(Upgrades.BELT_STACKING).orElseThrow().maxLevel());
        assertEquals(Upgrades.EXPLORATION_MAX, tree.upgrade(Upgrades.UNIVERSE_EXPLORATION).orElseThrow().maxLevel());
        // 机甲（ADR-0023）：驱动引擎 4 级、机械骨骼 8 级、批量建造 4 级封顶，其余无限
        assertEquals(4, tree.upgrade(Upgrades.DRIVE_ENGINE).orElseThrow().maxLevel());
        assertEquals(8, tree.upgrade(Upgrades.MECHANICAL_FRAME).orElseThrow().maxLevel());
        assertEquals(4, tree.upgrade(Upgrades.MASS_CONSTRUCTION).orElseThrow().maxLevel());
        assertEquals(Integer.MAX_VALUE, tree.upgrade(Upgrades.MECHA_CORE).orElseThrow().maxLevel());
        assertEquals(5, tree.upgrade(Upgrades.COMMUNICATION_CONTROL).orElseThrow().tiers().size());
        // 舰队和战斗（ADR-0033）：能量护盾 5 级、编队扩充 2 级、舰队扩充 1 级
        assertEquals(Upgrades.ENERGY_SHIELD_MAX, tree.upgrade(Upgrades.ENERGY_SHIELD).orElseThrow().maxLevel());
        assertEquals(2, tree.upgrade(Upgrades.SQUADRON_EXPANSION).orElseThrow().maxLevel());
        assertEquals(1, tree.upgrade(Upgrades.FLEET_EXPANSION).orElseThrow().maxLevel());
        assertEquals(List.of("ground_squadron"), tree.upgrade(Upgrades.SQUADRON_EXPANSION).orElseThrow().requires());
        assertEquals(List.of("space_fleet"), tree.upgrade(Upgrades.FLEET_EXPANSION).orElseThrow().requires());
        for (Upgrade upgrade : tree.upgrades()) {
            for (int level = 1; level <= upgrade.tiers().size() + 3 && level <= upgrade.maxLevel(); level++) {
                assertTrue(TechTree.MATRICES.containsAll(upgrade.matrices(level)), upgrade.id() + " " + level);
                // 每种矩阵都有解锁它的科技，所以前置一定能完成
                for (String matrix : upgrade.matrices(level)) {
                    assertTrue(tree.unlockedBy(matrix) != null, matrix);
                }
            }
            if (upgrade.infinite() != null) {
                assertEquals(List.of("universe:universe_matrix"), upgrade.infinite().matrices());
            }
        }
    }

    @Test
    void effectsFollowTheDesign() {
        assertEquals(1.4, Upgrades.miningSpeed(4), 1e-9);
        assertEquals(Math.pow(0.94, 4), Upgrades.veinConsumption(4), 1e-12);
        assertEquals(1.75, Upgrades.researchSpeed(3), 1e-9);
        assertEquals(2.25, Upgrades.carrierSpeed(5), 1e-9);
        assertEquals(55, Upgrades.carrierCapacity(50, 1));
        assertEquals(80, Upgrades.carrierCapacity(50, 6));
        assertEquals(800, Upgrades.carrierCapacity(500, 6));
        assertEquals(0.85 * 0.85, Upgrades.rayLoss(2), 1e-12);
        assertEquals(108_000 + 6 * 18_000, Upgrades.sailLifetime(6));
        assertEquals(List.of("20", "11.6"), Upgrades.effectArgs(Upgrades.VEIN_UTILIZATION, 2));
        assertEquals(List.of("55", "550"), Upgrades.effectArgs(Upgrades.CARRIER_CAPACITY, 1));
        assertEquals(List.of("105"), Upgrades.effectArgs(Upgrades.SAIL_LIFETIME, 1));
        assertEquals(List.of("15"), Upgrades.effectArgs(Upgrades.RAY_EFFICIENCY, 1));
    }

    @Test
    void mechaUpgradesFollowTheOriginal() {
        // 能量核心 120 MJ → 3.2 GJ，之后每级 +2.4 GJ（本模组的 kJ 对应原作的 MJ）
        assertEquals(120_000, Upgrades.mechaCapacity(0));
        assertEquals(3_200_000, Upgrades.mechaCapacity(5));
        assertEquals(5_600_000, Upgrades.mechaCapacity(6));
        // 燃烧室 800 W → 1.6 kW → 2.4 kW，之后每级 +1 kW
        assertEquals(40, Upgrades.fuelPower(0));
        assertEquals(80, Upgrades.fuelPower(4));
        assertEquals(120, Upgrades.fuelPower(5));
        assertEquals(170, Upgrades.fuelPower(6));
        assertEquals(0, Upgrades.flightSpeed(0));
        assertEquals(2, Upgrades.flightSpeed(9));
        assertEquals(0.6, Upgrades.frameBonus(6), 1e-9);
        assertEquals(1.0, Upgrades.frameBonus(8), 1e-9);
        assertEquals(List.of(3, 4, 6, 6, 9, 12, 15), java.util.stream.IntStream.rangeClosed(0, 6).map(Upgrades::drones).boxed().toList());
        assertEquals(4, Upgrades.droneTasks(2));
        assertEquals(8, Upgrades.droneTasks(3));
        assertEquals(List.of(6, 8, 10, 12, 15, 18, 21), java.util.stream.IntStream.rangeClosed(0, 6).map(Upgrades::droneSpeed).boxed().toList());
        assertEquals(300, Upgrades.buildLimit(0));
        assertEquals(Upgrades.UNLIMITED, Upgrades.buildLimit(4));
        assertEquals(8, Upgrades.botCapacity(0));
        assertEquals(20, Upgrades.botCapacity(6));
        assertEquals(List.of("200 kJ"), Upgrades.effectArgs(Upgrades.MECHA_CORE, 1));
        assertEquals(List.of("3.2 MJ"), Upgrades.effectArgs(Upgrades.MECHA_CORE, 5));
        assertEquals(List.of("1 kW"), Upgrades.effectArgs(Upgrades.ENERGY_CIRCUIT, 1));
        assertEquals(List.of("2.4 kW"), Upgrades.effectArgs(Upgrades.ENERGY_CIRCUIT, 5));
        assertEquals(List.of("1.3"), Upgrades.effectArgs(Upgrades.DRIVE_ENGINE, 2));
        assertEquals(List.of("80"), Upgrades.effectArgs(Upgrades.MECHANICAL_FRAME, 7));
        assertEquals(List.of("6", "8"), Upgrades.effectArgs(Upgrades.COMMUNICATION_CONTROL, 3));
        assertEquals(List.of("∞"), Upgrades.effectArgs(Upgrades.MASS_CONSTRUCTION, 4));
        assertEquals("800 W", Upgrades.watts(40));
        assertEquals("999 J", Upgrades.energy(999));
        assertEquals("1 kJ", Upgrades.energy(999.96));
    }

    @Test
    void fleetAndShieldUpgradesFollowTheDesign() {
        assertEquals(0, Upgrades.energyShield(0));
        assertEquals(40, Upgrades.energyShield(2));
        assertEquals(100, Upgrades.energyShield(5));
        assertEquals(100, Upgrades.energyShield(9));
        assertEquals(List.of("60"), Upgrades.effectArgs(Upgrades.ENERGY_SHIELD, 3));
        assertEquals(List.of("2"), Upgrades.effectArgs(Upgrades.SQUADRON_EXPANSION, 1));
        assertEquals(List.of("3"), Upgrades.effectArgs(Upgrades.SQUADRON_EXPANSION, 2));
        assertEquals(List.of("2"), Upgrades.effectArgs(Upgrades.FLEET_EXPANSION, 1));
    }

    @Test
    void beltStackGrowsToFour() {
        assertEquals(2, Upgrades.beltStack(0));
        assertEquals(3, Upgrades.beltStack(1));
        assertEquals(4, Upgrades.beltStack(2));
        assertEquals(4, Upgrades.beltStack(9));
        assertEquals(List.of("3"), Upgrades.effectArgs(Upgrades.BELT_STACKING, 1));
    }

    @Test
    void universeExplorationRevealsMoreOfTheMap() {
        // 去过的星系：0 级看得到行星，1 级起还有资源
        assertEquals(Upgrades.Reveal.PLANETS, Upgrades.reveal(0, true, 0));
        assertEquals(Upgrades.Reveal.RESOURCES, Upgrades.reveal(1, true, 0));
        // 没去过的：1 级还是只有恒星
        assertEquals(Upgrades.Reveal.STAR, Upgrades.reveal(1, false, 5));
        assertEquals(Upgrades.Reveal.PLANETS, Upgrades.reveal(2, false, 10));
        assertEquals(Upgrades.Reveal.STAR, Upgrades.reveal(2, false, 10.5));
        assertEquals(Upgrades.Reveal.RESOURCES, Upgrades.reveal(3, false, 30));
        assertEquals(Upgrades.Reveal.STAR, Upgrades.reveal(3, false, 31));
        assertEquals(Upgrades.Reveal.RESOURCES, Upgrades.reveal(4, false, Double.POSITIVE_INFINITY));
    }
}
