package io.github.sweetcornna.universe.core.balance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.sweetcornna.universe.core.dyson.DysonGrid;
import io.github.sweetcornna.universe.core.dyson.DysonLayer;
import io.github.sweetcornna.universe.core.dyson.DysonSphere;
import io.github.sweetcornna.universe.core.research.Tech;
import io.github.sweetcornna.universe.core.research.TechTree;
import io.github.sweetcornna.universe.core.trade.ValueGraph;
import io.github.sweetcornna.universe.core.trade.ValueGraph.Input;
import io.github.sweetcornna.universe.core.trade.ValueGraph.Output;
import io.github.sweetcornna.universe.core.trade.ValueGraph.Recipe;

class ProgressionTest {
    private static final String BLUE = TechTree.MATRICES.get(0);
    private static final String RED = TechTree.MATRICES.get(1);

    private static Input in(String item, int count) {
        return new Input(List.of(item), count);
    }

    /** 蓝矩阵 = 2 个铁矿 + 1 个铜矿；红矩阵 = 1 个氢 + 1 个重氢，氢从原油来；太阳帆 = 1 个铁矿。 */
    private static final List<Recipe> RECIPES = List.of(
            Recipe.of(BLUE, 1, 3, in("minecraft:raw_iron", 2), in("minecraft:raw_copper", 1)),
            Recipe.of("universe:solar_sail", 1, 2, in("minecraft:raw_iron", 1)),
            new Recipe(List.of(in("universe:crude_oil", 2)), List.of(new Output("universe:refined_oil", 1), new Output("universe:hydrogen", 1)), 4),
            Recipe.of(RED, 1, 6, in("universe:hydrogen", 1), in("universe:deuterium", 1)));

    private static final List<Progression.Stage> STAGES = List.of(
            new Progression.Stage(List.of(BLUE), 30, 1),
            new Progression.Stage(List.of(BLUE, RED), 120, 100));

    private static final TechTree TREE = new TechTree(List.of(
            Tech.handIn("start", List.of(), List.of(new Tech.ItemCost(BLUE, 10)), List.of()),
            Tech.lab("a", List.of("start"), List.of(BLUE), 60, List.of()),
            Tech.lab("b", List.of("a"), List.of(BLUE, RED), 100, List.of()),
            Tech.lab("side", List.of("a"), List.of(BLUE, RED), 50, List.of())));

    private static Progression model() {
        return new Progression(TREE, RECIPES, STAGES, new Progression.Shell(10, 100));
    }

    @Test
    void oreCostFollowsRecipesAndSplitsByproducts() {
        Progression model = model();
        assertEquals(3, model.ore(BLUE), 1e-9);
        // 2 个原油出 2 件东西，每件 1 个原油；重氢 = 1 个氢 + 分馏塔的工时
        double oil = Progression.EXTRACTED.get("universe:crude_oil");
        assertEquals(oil, model.ore("universe:hydrogen"), 1e-9);
        double deuterium = oil + Progression.EXTRACTED.get(Progression.FRACTIONATOR);
        assertEquals(deuterium, model.ore("universe:deuterium"), 1e-9);
        assertEquals(oil + deuterium, model.ore(RED), 1e-9);
        // 临界光子：约 6.17 张帆发的电，加上接收站的工时
        assertEquals(6.17 + Progression.EXTRACTED.get(Progression.RECEIVER), model.ore("universe:critical_photon"), 1e-9);
        // 上交物品的科技按上交的东西算
        assertEquals(30, model.ore(TREE.get("start").orElseThrow()), 1e-9);
    }

    @Test
    void extractedResourcesAreWeighedAgainstAMiner() {
        // 一台采矿机一分钟 12 个矿；抽水站一分钟 50 个水，一个水折合 12/50 个矿
        assertEquals(12, Progression.MINER_ORE_PER_MINUTE, 1e-9);
        assertEquals(12.0 / 50, Progression.EXTRACTED.get("universe:water"), 1e-9);
        assertEquals(12.0 / 60, Progression.EXTRACTED.get("universe:crude_oil"), 1e-9);
        assertEquals(12.0 / 6, Progression.EXTRACTED.get(Progression.RECEIVER), 1e-9);
        // 一个光子 1.2 MJ，一张帆 90 分钟里每刻 1.8 焦
        assertEquals(1_200_000 / (1.8 * 108_000), Progression.SAILS_PER_PHOTON, 1e-9);
        assertEquals(12.0 / 18, Progression.EXTRACTED.get(Progression.FRACTIONATOR), 1e-9);
    }

    @Test
    void researchTimeIsLimitedByOreOrLabs() {
        Progression model = model();
        // 第一档：每点 3 个矿，每分钟 30 个矿能研究 10 点，但只有 1 台研究站（每分钟 12 点）——矿先不够
        assertEquals(60 / 10.0 / 60, model.hours(TREE.get("a").orElseThrow()), 1e-9);
        // 矿给到每分钟 300 个，就轮到研究站不够：1 台每分钟 12 点
        Progression slowLabs = new Progression(TREE, RECIPES,
                List.of(new Progression.Stage(List.of(BLUE), 300, 1), STAGES.get(1)), new Progression.Shell(10, 100));
        assertEquals(60 / Progression.LAB_POINTS_PER_MINUTE / 60, slowLabs.hours(TREE.get("a").orElseThrow()), 1e-9);
        assertEquals(0, model.hours(TREE.get("start").orElseThrow()));
    }

    @Test
    void windowsCountEachTechOnce() {
        Progression model = model();
        Progression.Milestone first = new Progression.Milestone("a", 2, false);
        Progression.Milestone second = new Progression.Milestone("b", 5, false);
        List<Progression.Window> windows = model.windows(List.of(first, second));
        assertEquals(2, windows.get(0).length(), 1e-9);
        assertEquals(model.hours(TREE.get("a").orElseThrow()), windows.get(0).research(), 1e-9);
        // 第二段只有 b：同一档的 side 不是 b 的前置，不算
        assertEquals(3, windows.get(1).length(), 1e-9);
        assertEquals(model.hours(TREE.get("b").orElseThrow()), windows.get(1).research(), 1e-9);
        assertEquals(0, windows.get(1).build());
    }

    @Test
    void firstShellIsTheCheapestWithinTheLatitudeLimit() {
        DysonGrid grid = DysonGrid.of(DysonGrid.Template.GEODESIC, 1);
        Progression.Shell shell = Progression.firstShell(grid, 0.2, DysonSphere.LATITUDE_BASE);
        // 至少三个节点，每个 30 个结构点，外加三条框架
        assertTrue(shell.rockets() > 3 * DysonLayer.NODE_SP, shell.toString());
        assertTrue(shell.sails() > 0);
        // 半径大一倍，框架长一倍、球壳面积四倍
        Progression.Shell far = Progression.firstShell(grid, 0.4, DysonSphere.LATITUDE_BASE);
        assertTrue(far.rockets() > shell.rockets());
        assertEquals(4, far.sails() / (double) shell.sails(), 0.05);
    }

    @Test
    void valueGraphWithoutWagesOrMarkupJustAddsUp() {
        var values = ValueGraph.compute(java.util.Map.of("x", 1.0), List.of(Recipe.of("y", 2, 10, in("x", 3))), 0, 0);
        assertEquals(1.5, values.get("y"), 1e-9);
    }
}
