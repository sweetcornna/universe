package io.github.sweetcornna.universe.datagen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import io.github.sweetcornna.universe.core.research.Tech;
import io.github.sweetcornna.universe.core.research.TechTree;

/**
 * 生存可达性：从原版物品、天然方块和生物掉落出发，按数据生成的配方和科技树一路推下去，
 * 每个科技都要能完成，每个物品都要能拿到。改了配方或科技树后，这里能抓到"生存里卡死"的情况。
 *
 * <p>读的是 {@code src/generated/resources}，改了数据生成要先跑 {@code runData}。
 */
class SurvivalProgressionTest {
    private static final Path DATA = Path.of("src/generated/resources/data/universe");
    private static final Path ITEMS = Path.of("src/generated/resources/assets/universe/items");
    private static final String NS = "universe:";
    /** 设备配方要在哪台设备里做。 */
    private static final Map<String, String> MACHINES = Map.of(
            "universe:smelting", NS + "arc_smelter",
            "universe:assembling", NS + "assembler",
            "universe:refining", NS + "oil_refinery",
            "universe:chemical", NS + "chemical_plant",
            "universe:particle", NS + "miniature_particle_collider");
    /**
     * 不靠配方、由设备从世界里抽出来的东西；勘探数据靠多功能工具的分析目镜发现新东西拿到；重氢由分馏塔从氢里分出来
     * （轨道采集器也能采，但它本身要更晚的科技）；临界光子由射线接收站在光子模式下产出（戴森云有帆就行）。
     * 蛋、绒毛、胶质是驯服的异星动物产的，喂得起饲料就能养。
     */
    private static final Map<String, String> EXTRACTED = Map.of(
            NS + "crude_oil", NS + "oil_extractor",
            NS + "water", NS + "water_pump",
            NS + "survey_data", NS + "multitool",
            NS + "deuterium", NS + "fractionator",
            NS + "critical_photon", NS + "ray_receiver",
            NS + "alien_egg", NS + "herbivore_feed",
            NS + "alien_fluff", NS + "herbivore_feed",
            NS + "bio_gel", NS + "carnivore_feed");
    /** 生存里本来就拿不到的：挖不掉的涌泉、掉落舱和石碑、空间站的交易终端和建材，只在坠毁点箱子里的损坏工具，机甲的建造无人机。刷怪蛋另算。 */
    private static final Set<String> UNOBTAINABLE = Set.of(NS + "crude_oil_seep", NS + "damaged_multitool", NS + "drop_pod",
            NS + "monolith", NS + "trade_terminal",
            NS + "station_hull", NS + "station_floor", NS + "station_glass", NS + "station_light", NS + "station_pad", NS + "station_trim",
            NS + "station_solar_panel", NS + "ship_repair", NS + "station_teleporter", NS + "mission_board",
            // Mk.II 部件只能在空间站买、在坠毁飞船里拆到（ADR-0017）
            NS + "ship_thruster_mk2", NS + "fuel_tank_mk2", NS + "impulse_engine_mk2", NS + "warp_drive_mk2", NS + "alloy_hull_plating",
            // 建造无人机是机甲自带的，物品只用来画在世界里飞的样子（ADR-0023）
            NS + "construction_drone");
    /** 每类设备配方最多几种原料、几种产物：同类设备里格子最多的那台（{@code RecipeMachines}）。 */
    private static final Map<String, int[]> SLOTS = Map.of(
            "universe:smelting", new int[] {3, 1},
            "universe:assembling", new int[] {4, 1},
            "universe:refining", new int[] {2, 2},
            "universe:chemical", new int[] {3, 2},
            "universe:particle", new int[] {3, 2});
    /** 兴趣点的建筑里天然放着的方块（ADR-0014），挖了按各自的战利品表掉落。 */
    private static final Set<String> STRUCTURE_BLOCKS = Set.of(NS + "data_terminal", NS + "supply_crate", NS + "tech_cache",
            NS + "smoldering_wreck");

    private record Recipe(String name, List<List<String>> ingredients, String research) {
    }

    private record Producer(Recipe recipe, List<String> results) {
    }

    @Test
    void everyTechAndItemIsReachableInSurvival() throws IOException {
        assertTrue(Files.isDirectory(DATA), "找不到数据生成的结果：" + DATA.toAbsolutePath());
        Set<String> have = new HashSet<>(naturalSources());
        List<Producer> producers = recipes();
        Set<String> done = new HashSet<>();

        boolean changed = true;
        while (changed) {
            changed = false;
            for (Map.Entry<String, String> entry : EXTRACTED.entrySet()) {
                if (have.contains(entry.getValue())) {
                    changed |= have.add(entry.getKey());
                }
            }
            for (Producer producer : producers) {
                Recipe recipe = producer.recipe();
                if (!recipe.research().isEmpty() && !done.contains(recipe.research())) {
                    continue;
                }
                if (recipe.ingredients().stream().allMatch(choices -> choices.stream().anyMatch(item -> available(item, have)))) {
                    changed |= have.addAll(producer.results());
                }
            }
            for (Tech tech : TechTree.DEFAULT.techs()) {
                if (done.contains(tech.id()) || !done.containsAll(tech.requires())) {
                    continue;
                }
                boolean affordable = tech.isHandIn()
                        ? tech.handIn().stream().allMatch(cost -> available(cost.item(), have))
                        : have.contains(NS + "matrix_lab") && tech.matrices().stream().allMatch(have::contains);
                if (affordable) {
                    changed |= done.add(tech.id());
                }
            }
        }

        List<String> stuckTechs = TechTree.DEFAULT.techs().stream().map(Tech::id).filter(id -> !done.contains(id)).toList();
        assertEquals(List.of(), stuckTechs, "生存里完成不了的科技");
        Set<String> missing = new TreeSet<>();
        for (String item : items()) {
            if (!have.contains(item) && !UNOBTAINABLE.contains(item) && !item.endsWith("_spawn_egg")) {
                missing.add(item);
            }
        }
        assertEquals(Set.of(), missing, "生存里拿不到的物品");
    }

    /** 配方的原料、产物种数不能多于设备的格子，否则设备永远开不了工（M8 以前钛合金就是这样）。 */
    @Test
    void machineRecipesFitTheirMachines() throws IOException {
        List<String> tooBig = new ArrayList<>();
        for (Path file : jsonFiles(DATA.resolve("recipe"))) {
            JsonObject json = read(file).getAsJsonObject();
            int[] slots = SLOTS.get(json.get("type").getAsString());
            if (slots == null) {
                continue;
            }
            int inputs = json.getAsJsonArray("ingredients").size();
            int outputs = json.getAsJsonArray("results").size();
            if (inputs > slots[0] || outputs > slots[1]) {
                tooBig.add(file.getFileName() + "：" + inputs + " 种原料、" + outputs + " 种产物");
            }
        }
        assertEquals(List.of(), tooBig, "设备放不下的配方");
    }

    private static boolean available(String item, Set<String> have) {
        // 原版物品和原版、公共标签都当成拿得到；本模组的东西要推出来
        return item.startsWith("minecraft:") || item.startsWith("#minecraft:") || item.startsWith("#c:") || have.contains(item);
    }

    /** 本模组的所有物品。 */
    private static List<String> items() throws IOException {
        try (Stream<Path> files = Files.list(ITEMS)) {
            return files.map(file -> NS + file.getFileName().toString().replace(".json", "")).toList();
        }
    }

    /** 世界里天然有的：星球方块和矿石本身、兴趣点建筑里的方块、它们的掉落、生物掉落、兴趣点箱子里的东西。 */
    private static Set<String> naturalSources() throws IOException {
        Set<String> natural = new HashSet<>();
        for (String item : items()) {
            String path = item.substring(NS.length());
            if (path.startsWith("planet_") || path.endsWith("_ore") || STRUCTURE_BLOCKS.contains(item)) {
                natural.add(item);
                Path loot = DATA.resolve("loot_table/blocks/" + path + ".json");
                if (Files.exists(loot)) {
                    collectItems(read(loot), natural);
                }
            }
        }
        for (Path loot : jsonFiles(DATA.resolve("loot_table/entities"))) {
            collectItems(read(loot), natural);
        }
        for (Path loot : jsonFiles(DATA.resolve("loot_table/chests"))) {
            collectItems(read(loot), natural);
        }
        return natural;
    }

    private static void collectItems(JsonElement element, Set<String> out) {
        if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            if (object.has("type") && object.get("type").getAsString().equals("minecraft:item") && object.has("name")) {
                out.add(object.get("name").getAsString());
            }
            object.entrySet().forEach(entry -> collectItems(entry.getValue(), out));
        } else if (element.isJsonArray()) {
            element.getAsJsonArray().forEach(child -> collectItems(child, out));
        }
    }

    private static List<Producer> recipes() throws IOException {
        List<Producer> producers = new ArrayList<>();
        for (Path file : jsonFiles(DATA.resolve("recipe"))) {
            JsonObject json = read(file).getAsJsonObject();
            String type = json.get("type").getAsString();
            List<List<String>> ingredients = new ArrayList<>();
            if (json.has("key")) {
                json.getAsJsonObject("key").entrySet().forEach(entry -> ingredients.add(choices(entry.getValue())));
            }
            if (json.has("ingredients")) {
                for (JsonElement element : json.getAsJsonArray("ingredients")) {
                    JsonElement ingredient = element.isJsonObject() && element.getAsJsonObject().has("ingredient")
                            ? element.getAsJsonObject().get("ingredient") : element;
                    ingredients.add(choices(ingredient));
                }
            }
            if (json.has("ingredient")) {
                ingredients.add(choices(json.get("ingredient")));
            }
            if (MACHINES.containsKey(type)) {
                ingredients.add(List.of(MACHINES.get(type)));
            }
            List<String> results = new ArrayList<>();
            if (json.has("result")) {
                results.add(json.getAsJsonObject("result").get("id").getAsString());
            }
            if (json.has("results")) {
                json.getAsJsonArray("results").forEach(result -> results.add(result.getAsJsonObject().get("id").getAsString()));
            }
            String research = json.has("research") ? json.get("research").getAsString() : "";
            producers.add(new Producer(new Recipe(file.toString(), ingredients, research), results));
        }
        return producers;
    }

    /** 一个原料：一个物品、一个标签或几个可选的物品。 */
    private static List<String> choices(JsonElement element) {
        if (element.isJsonArray()) {
            JsonArray array = element.getAsJsonArray();
            List<String> choices = new ArrayList<>();
            array.forEach(child -> choices.add(child.getAsString()));
            return choices;
        }
        return List.of(element.getAsString());
    }

    private static List<Path> jsonFiles(Path dir) throws IOException {
        try (Stream<Path> files = Files.walk(dir)) {
            return files.filter(file -> file.toString().endsWith(".json")).toList();
        }
    }

    private static JsonElement read(Path file) throws IOException {
        return JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
    }
}
