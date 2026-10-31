package io.github.sweetcornna.universe.datagen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
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

import io.github.sweetcornna.universe.core.ecology.ResourcePlant;
import io.github.sweetcornna.universe.core.fishing.FishSpecies;
import io.github.sweetcornna.universe.core.fishing.FishTable;
import io.github.sweetcornna.universe.core.fishing.Junk;
import io.github.sweetcornna.universe.core.fishing.Rarity;
import io.github.sweetcornna.universe.core.fossil.Bone;
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
            "universe:particle", NS + "miniature_particle_collider",
            "universe:nutrient", NS + "nutrient_processor",
            "universe:portable_refining", NS + "portable_refiner",
            "universe:electrolysis", NS + "electrolyzer"
            , "universe:reactor", NS + "chemical_reactor"
            , "universe:bioreactor", NS + "bioreactor"
            // M20 反应釜（B）、生物反应器（D）的设备（前面加逗号）加在这行上面；Map.of 最多十对
            );
    /**
     * 不靠配方、由设备从世界里抽出来的东西；勘探数据靠多功能工具的分析目镜发现新东西拿到；重氢由分馏塔从氢里分出来
     * （轨道采集器也能采，但它本身要更晚的科技）；临界光子由射线接收站在光子模式下产出（戴森云有帆就行）。
     * 蛋、绒毛、胶质是驯服的异星动物产的，喂得起饲料就能养；生物卵是喂饱的伙伴下的（ADR-0026 §九）。
     * 黑雾的零件和黑雾矩阵是打黑雾掉的（{@code DarkFogLoot}，不走战利品表），有多功能工具就打得动（ADR-0028 §六）。
     * 酸液桶是拿空桶在酸海里舀的，和原版打水一样（ADR-0030 §一）。
     * 鱼、杂物、漂流瓶、补给舱是钓鱼器钓上来的（ADR-0034 §一），配方里按标签用鱼；骨头是化石土按星球换出来的
     * （{@code universe:fossil_bone} 战利品函数，表里只写了一块占位）；化石展品是骨架展台封装出来的（§三）。
     *
     * <p>值按 {@link #available} 判断，所以可以是原版物品或标签。
     */
    private static final Map<String, String> EXTRACTED = extracted(Map.ofEntries(
            Map.entry(NS + "crude_oil", NS + "oil_extractor"),
            Map.entry(NS + "water", NS + "water_pump"),
            Map.entry(NS + "acid_bucket", "minecraft:bucket"),
            Map.entry(NS + "survey_data", NS + "multitool"),
            Map.entry(NS + "deuterium", NS + "fractionator"),
            Map.entry(NS + "critical_photon", NS + "ray_receiver"),
            Map.entry(NS + "alien_egg", NS + "herbivore_feed"),
            Map.entry(NS + "alien_fluff", NS + "herbivore_feed"),
            Map.entry(NS + "bio_gel", NS + "carnivore_feed"),
            Map.entry(NS + "creature_egg", NS + "herbivore_feed"),
            Map.entry(NS + "energy_shard", NS + "multitool"),
            Map.entry(NS + "silicon_neuron", NS + "multitool"),
            Map.entry(NS + "matter_recombinator", NS + "multitool"),
            Map.entry(NS + "negentropy_singularity", NS + "multitool"),
            Map.entry(NS + "core_element", NS + "multitool"),
            Map.entry(NS + "dark_fog_matrix", NS + "multitool"),
            // 大气里的气体：大气提取器取（ADR-0036 §三）。一氧化碳、氰化氢在哪儿都不到千分之一，取不了
            Map.entry(NS + "nitrogen", NS + "atmospheric_extractor"),
            Map.entry(NS + "carbon_dioxide", NS + "atmospheric_extractor"),
            Map.entry(NS + "methane", NS + "atmospheric_extractor"),
            Map.entry(NS + "argon", NS + "atmospheric_extractor"),
            Map.entry(NS + "helium", NS + "atmospheric_extractor"),
            Map.entry(NS + "sulfur_dioxide", NS + "atmospheric_extractor"),
            Map.entry(NS + "ammonia", NS + "atmospheric_extractor")
            // 样本是采样管采来的，菌种管是培养箱分离的，改造种苗是基因编辑台编辑的（ADR-0036 §五）
            , Map.entry(NS + "sample", NS + "sample_tube")
            , Map.entry(NS + "strain_tube", NS + "incubator")
            , Map.entry(NS + "engineered_seedling", NS + "gene_editor")
            // 镍是生物反应器浸铜时偶尔多出的（BioreactorBlockEntity 里按概率出，不写在配方里，ADR-0036 §五）
            , Map.entry(NS + "nickel_ingot", NS + "bioreactor")
            // 铂也是，比镍少见（ADR-0037）
            , Map.entry(NS + "platinum_ingot", NS + "bioreactor")
            // M20 别的几条线（B–E）的（前面加逗号）加在这行上面
            ));
    /** 生存里本来就拿不到的：挖不掉的涌泉、掉落舱和石碑、空间站的交易终端和建材，只在坠毁点箱子里的损坏工具，机甲的建造无人机。刷怪蛋另算。 */
    private static final Set<String> UNOBTAINABLE = Set.of(NS + "crude_oil_seep", NS + "damaged_multitool", NS + "drop_pod",
            NS + "monolith", NS + "trade_terminal",
            NS + "station_hull", NS + "station_floor", NS + "station_glass", NS + "station_light", NS + "station_pad", NS + "station_trim",
            NS + "station_solar_panel", NS + "ship_repair", NS + "station_teleporter", NS + "mission_board", NS + "ship_dealer", NS + "multitool_cabinet",
            // 货船的外壳和船舱里打不坏的方块（ADR-0024）
            NS + "freighter_plate", NS + "freighter_engine", NS + "freighter_console", NS + "fleet_terminal",
            // 先驱者传送门挖不掉（ADR-0024）
            NS + "portal_stone", NS + "portal_glyph", NS + "portal_dial",
            // 守卫塔挖不掉（ADR-0026）
            NS + "sentinel_pillar_plating", NS + "sentinel_pillar_light", NS + "sentinel_pillar_terminal",
            // 交易站、制造设施、观测站的终端和加固外壳、加固门挖不掉（ADR-0026）
            NS + "outpost_trade_terminal", NS + "facility_terminal", NS + "observatory_terminal", NS + "reinforced_plating",
            NS + "reinforced_door",
            // 知识石挖不掉，读过的记录按坐标记（ADR-0027）
            NS + "knowledge_stone",
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
            "universe:particle", new int[] {3, 2},
            "universe:nutrient", new int[] {3, 1},
            "universe:portable_refining", new int[] {1, 1},
            "universe:electrolysis", new int[] {2, 3}
            // 反应釜三个原料格、三个产物格；反应式最多两种产物（ADR-0037）
            , "universe:reactor", new int[] {3, 2}
            , "universe:bioreactor", new int[] {3, 2}
            // M20 反应釜（B）、生物反应器（D）的格子数（前面加逗号）加在这行上面；Map.of 最多十对
            );
    /** 兴趣点的建筑里天然放着的方块（ADR-0014），挖了按各自的战利品表掉落。 */
    private static final Set<String> STRUCTURE_BLOCKS = Set.of(NS + "data_terminal", NS + "supply_crate", NS + "tech_cache",
            NS + "smoldering_wreck", NS + "damaged_ship_helm", NS + "clam", NS + "sealed_crate",
            // 野外的地标（ADR-0026）：化石坑的化石土、打捞点的打捞舱；雷击石是风暴里雷劈出来的
            NS + "fossil_dirt", NS + "salvage_capsule", NS + "fulgurite",
            // 间歇泉的喷口和结壳是世界生成的地物（ADR-0034 §六）
            NS + "hot_geyser_vent", NS + "hot_geyser_crust", NS + "toxic_geyser_vent", NS + "toxic_geyser_crust",
            NS + "cold_geyser_vent", NS + "cold_geyser_crust", NS + "strange_geyser_vent", NS + "strange_geyser_crust");

    private record Recipe(String name, List<List<String>> ingredients, String research) {
    }

    private static Map<String, String> extracted(Map<String, String> base) {
        Map<String, String> all = new HashMap<>(base);
        String rig = NS + "fishing_rig";
        for (FishSpecies fish : FishTable.ALL) {
            all.put(NS + fish.itemId(), rig);
        }
        all.put("#" + NS + "fish", rig);
        for (Rarity rarity : Rarity.values()) {
            all.put("#" + NS + "fish/" + rarity.key(), rig);
        }
        for (Junk junk : Junk.values()) {
            all.put(NS + junk.id(), rig);
        }
        all.put(NS + "message_bottle", rig);
        all.put(NS + "sealed_cache", rig);
        for (Bone bone : Bone.all()) {
            all.put(NS + bone.id(), NS + "fossil_dirt");
        }
        all.put(NS + "fossil_exhibit", NS + "skeleton_display");
        return Map.copyOf(all);
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
                if (available(entry.getValue(), have)) {
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
        // 资源植物（ADR-0026）：世界生成长在星球上，方块没有物品，收获和打掉都得到产物
        for (ResourcePlant plant : ResourcePlant.values()) {
            collectItems(read(DATA.resolve("loot_table/blocks/" + plant.id() + ".json")), natural);
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
