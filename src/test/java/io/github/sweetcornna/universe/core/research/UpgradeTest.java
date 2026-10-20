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
        assertEquals(6, tree.upgrades().size());
        // 有限几级：矿脉利用 4、研究速度 3、载具引擎 5、射线 4；容量和太阳帆 6 级封顶
        assertEquals(4, tree.upgrade(Upgrades.VEIN_UTILIZATION).orElseThrow().tiers().size());
        assertEquals(3, tree.upgrade(Upgrades.RESEARCH_SPEED).orElseThrow().tiers().size());
        assertEquals(5, tree.upgrade(Upgrades.CARRIER_ENGINE).orElseThrow().tiers().size());
        assertEquals(4, tree.upgrade(Upgrades.RAY_EFFICIENCY).orElseThrow().tiers().size());
        assertEquals(6, tree.upgrade(Upgrades.CARRIER_CAPACITY).orElseThrow().maxLevel());
        assertEquals(6, tree.upgrade(Upgrades.SAIL_LIFETIME).orElseThrow().maxLevel());
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
}
