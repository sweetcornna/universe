package io.github.sweetcornna.universe.datagen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import io.github.sweetcornna.universe.core.balance.Progression;
import io.github.sweetcornna.universe.core.research.TechTree;
import io.github.sweetcornna.universe.core.trade.ValueGraph;

/**
 * 数值平衡（ADR-0018）：拿数据生成的设备配方跑进度模型，检查每档科技一档比一档贵、没有断崖，每段游戏时间里研究占的比例合适。
 * 模型的报告打到标准输出，`gradlew test` 的测试报告里能看到。
 *
 * <p>读的是 {@code src/generated/resources}，改了配方要先跑 {@code runData}。
 */
class BalanceTest {
    private static final Path RECIPES = Path.of("src/generated/resources/data/universe/recipe");
    /** 参考工厂里自动化的只有设备配方，工作台的配方不算。 */
    private static final List<String> MACHINE_DIRS = List.of("smelting", "assembling", "refining", "chemical", "particle");
    /** 相邻两档研究时间最多差几倍。 */
    private static final double MAX_STAGE_RATIO = 3;
    /** 相邻两档每个科技要的矿（中位数）最多差几倍。工厂也在变大，所以比研究时间宽。 */
    private static final double MAX_TECH_RATIO = 6;

    private static Progression model;

    @BeforeAll
    static void load() throws IOException {
        model = Progression.of(TechTree.DEFAULT, machineRecipes());
        model.report().forEach(System.out::println);
    }

    @Test
    void everyMatrixHasARecipeChain() {
        for (String matrix : TechTree.MATRICES) {
            assertTrue(model.ore(matrix) > 0, matrix);
        }
    }

    @Test
    void stagesGrowWithoutACliff() {
        // 白矩阵只有"任务完成"一个科技，是收尾，不和前一档比总时长
        List<Map.Entry<Progression.Stage, Double>> stages = new ArrayList<>(model.stageHours().entrySet());
        for (int i = 1; i < stages.size() - 1; i++) {
            double before = stages.get(i - 1).getValue();
            double after = stages.get(i).getValue();
            String label = stages.get(i - 1).getKey().matrices() + " -> " + stages.get(i).getKey().matrices() + "：" + before + " -> " + after;
            assertTrue(after >= before, "研究时间变短了 " + label);
            assertTrue(after <= before * MAX_STAGE_RATIO, "研究时间断崖 " + label);
        }
    }

    @Test
    void techsGetPricierStageByStage() {
        List<Map.Entry<Progression.Stage, Double>> medians = new ArrayList<>(model.medianOre().entrySet());
        for (int i = 1; i < medians.size(); i++) {
            double before = medians.get(i - 1).getValue();
            double after = medians.get(i).getValue();
            String label = medians.get(i - 1).getKey().matrices() + " -> " + medians.get(i).getKey().matrices() + "：" + before + " -> " + after;
            assertTrue(after > before, "后一档的科技反而便宜 " + label);
            // "任务完成"是收尾的大研究，只要求它最贵
            if (i < medians.size() - 1) {
                assertTrue(after <= before * MAX_TECH_RATIO, "科技的价钱断崖 " + label);
            }
        }
    }

    @Test
    void researchPacesTheLateGameWithoutStallingIt() {
        List<Progression.Window> windows = model.windows(Progression.MILESTONES);
        assertEquals(4, windows.size());
        // 开局到蓝矩阵自动化只有上交物品的科技
        assertEquals(0, windows.get(0).research(), 1e-9);
        // 到第一次跃迁：主要在造油路和开飞船，研究不该占一半以上
        assertTrue(windows.get(1).share() <= 0.5, "到第一次跃迁 " + windows.get(1));
        // 到第一片球壳、到任务完成：研究和造火箭占三到八成多，留出盖工厂和探索的时间
        assertBetween(windows.get(2), 0.3, 0.8);
        assertBetween(windows.get(3), 0.4, 0.85);
    }

    private static void assertBetween(Progression.Window window, double low, double high) {
        assertTrue(window.share() >= low && window.share() <= high, window.end().tech() + " " + window.share() + "：" + window);
    }

    /** 数据生成的设备配方，换成价钱和平衡共用的配方格式。 */
    static List<ValueGraph.Recipe> machineRecipes() throws IOException {
        assertTrue(Files.isDirectory(RECIPES), "找不到数据生成的结果：" + RECIPES.toAbsolutePath());
        List<ValueGraph.Recipe> recipes = new ArrayList<>();
        for (String dir : MACHINE_DIRS) {
            try (Stream<Path> files = Files.list(RECIPES.resolve(dir))) {
                for (Path file : files.filter(path -> path.toString().endsWith(".json")).toList()) {
                    recipes.add(recipe(JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject()));
                }
            }
        }
        return recipes;
    }

    private static ValueGraph.Recipe recipe(JsonObject json) {
        List<ValueGraph.Input> inputs = new ArrayList<>();
        for (JsonElement element : json.getAsJsonArray("ingredients")) {
            JsonObject entry = element.getAsJsonObject();
            JsonElement ingredient = entry.get("ingredient");
            List<String> choices = new ArrayList<>();
            if (ingredient.isJsonArray()) {
                ingredient.getAsJsonArray().forEach(choice -> choices.add(choice.getAsString()));
            } else {
                choices.add(ingredient.getAsString());
            }
            inputs.add(new ValueGraph.Input(choices, entry.has("count") ? entry.get("count").getAsInt() : 1));
        }
        List<ValueGraph.Output> outputs = new ArrayList<>();
        for (JsonElement element : json.getAsJsonArray("results")) {
            JsonObject result = element.getAsJsonObject();
            outputs.add(new ValueGraph.Output(result.get("id").getAsString(), result.has("count") ? result.get("count").getAsInt() : 1));
        }
        return new ValueGraph.Recipe(inputs, outputs, json.get("time").getAsInt() / 20.0);
    }
}
