package io.github.sweetcornna.universe.datagen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import io.github.sweetcornna.universe.core.trade.RawValues;
import io.github.sweetcornna.universe.core.trade.ValueGraph;
import io.github.sweetcornna.universe.core.trade.ValueGraph.Input;
import io.github.sweetcornna.universe.core.trade.ValueGraph.Output;

/**
 * 交易品的价钱（ADR-0017）：按数据生成的配方和 {@link RawValues} 推一遍基准价，打了交易大类标签的东西都要有价钱，
 * 主要的加工链价钱要一级比一级高。原版配方不在数据生成里，用到的几个原版中间品在这里按原版的配方补上。
 *
 * <p>读的是 {@code src/generated/resources}，改了数据生成要先跑 {@code runData}。
 */
class TradeValuesTest {
    private static final Path DATA = Path.of("src/generated/resources/data/universe");
    /** 本模组配方用到的原版中间品，照原版的配方（手工合成算 1 秒，熔炉 10 秒）。 */
    private static final List<ValueGraph.Recipe> VANILLA = List.of(
            ValueGraph.Recipe.of("minecraft:iron_ingot", 1, 5, new Input(List.of("minecraft:raw_iron"), 1)),
            ValueGraph.Recipe.of("minecraft:copper_ingot", 1, 5, new Input(List.of("minecraft:raw_copper"), 1)),
            ValueGraph.Recipe.of("minecraft:gold_ingot", 1, 5, new Input(List.of("minecraft:raw_gold"), 1)),
            ValueGraph.Recipe.of("minecraft:stone", 1, 10, new Input(List.of("minecraft:cobblestone"), 1)),
            ValueGraph.Recipe.of("minecraft:stone_bricks", 4, 1, new Input(List.of("minecraft:stone"), 4)),
            ValueGraph.Recipe.of("minecraft:glass", 1, 10, new Input(List.of("minecraft:sand"), 1)),
            ValueGraph.Recipe.of("minecraft:glass_pane", 16, 1, new Input(List.of("minecraft:glass"), 6)),
            ValueGraph.Recipe.of("minecraft:oak_planks", 4, 1, new Input(List.of("minecraft:oak_log"), 1)),
            ValueGraph.Recipe.of("minecraft:stick", 4, 1, new Input(List.of("minecraft:oak_planks"), 2)),
            ValueGraph.Recipe.of("minecraft:iron_nugget", 9, 1, new Input(List.of("minecraft:iron_ingot"), 1)),
            ValueGraph.Recipe.of("minecraft:iron_block", 1, 1, new Input(List.of("minecraft:iron_ingot"), 9)),
            ValueGraph.Recipe.of("minecraft:copper_block", 1, 1, new Input(List.of("minecraft:copper_ingot"), 9)),
            ValueGraph.Recipe.of("minecraft:redstone_block", 1, 1, new Input(List.of("minecraft:redstone"), 9)),
            ValueGraph.Recipe.of("minecraft:smooth_stone", 1, 10, new Input(List.of("minecraft:stone"), 1)),
            ValueGraph.Recipe.of("minecraft:furnace", 1, 1, new Input(List.of("minecraft:cobblestone"), 8)),
            ValueGraph.Recipe.of("minecraft:chest", 1, 1, new Input(List.of("minecraft:oak_planks"), 8)),
            ValueGraph.Recipe.of("minecraft:bucket", 1, 1, new Input(List.of("minecraft:iron_ingot"), 3)),
            ValueGraph.Recipe.of("minecraft:hopper", 1, 1, new Input(List.of("minecraft:iron_ingot"), 5),
                    new Input(List.of("minecraft:chest"), 1)),
            ValueGraph.Recipe.of("minecraft:piston", 1, 1, new Input(List.of("minecraft:oak_planks"), 3),
                    new Input(List.of("minecraft:cobblestone"), 4), new Input(List.of("minecraft:iron_ingot"), 1),
                    new Input(List.of("minecraft:redstone"), 1)),
            ValueGraph.Recipe.of("minecraft:comparator", 1, 1, new Input(List.of("minecraft:stone"), 3),
                    new Input(List.of("minecraft:quartz"), 1), new Input(List.of("minecraft:redstone"), 3)),
            ValueGraph.Recipe.of("minecraft:daylight_detector", 1, 1, new Input(List.of("minecraft:glass"), 3),
                    new Input(List.of("minecraft:quartz"), 3), new Input(List.of("minecraft:oak_planks"), 3)),
            ValueGraph.Recipe.of("minecraft:lightning_rod", 1, 1, new Input(List.of("minecraft:copper_ingot"), 3)),
            ValueGraph.Recipe.of("minecraft:leather", 1, 1, new Input(List.of("minecraft:rabbit_hide"), 4)),
            ValueGraph.Recipe.of("minecraft:iron_bars", 16, 1, new Input(List.of("minecraft:iron_ingot"), 6)),
            ValueGraph.Recipe.of("minecraft:compass", 1, 1, new Input(List.of("minecraft:iron_ingot"), 4),
                    new Input(List.of("minecraft:redstone"), 1)),
            ValueGraph.Recipe.of("minecraft:glass_bottle", 3, 1, new Input(List.of("minecraft:glass"), 3)),
            ValueGraph.Recipe.of("minecraft:white_wool", 1, 1, new Input(List.of("minecraft:string"), 4)),
            ValueGraph.Recipe.of("minecraft:blast_furnace", 1, 1, new Input(List.of("minecraft:iron_ingot"), 5),
                    new Input(List.of("minecraft:furnace"), 1), new Input(List.of("minecraft:smooth_stone"), 3)),
            ValueGraph.Recipe.of("minecraft:spyglass", 1, 1, new Input(List.of("minecraft:copper_ingot"), 2),
                    new Input(List.of("minecraft:amethyst_shard"), 1)),
            ValueGraph.Recipe.of("minecraft:blaze_powder", 2, 1, new Input(List.of("minecraft:blaze_rod"), 1)),
            ValueGraph.Recipe.of("minecraft:magma_cream", 1, 1, new Input(List.of("minecraft:blaze_powder"), 1),
                    new Input(List.of("minecraft:slime_ball"), 1)));
    /** 原料表和配方里的标签：测试里各用一样东西代表。 */
    private static final Map<String, String> TAGS = Map.of(
            "#minecraft:logs", "minecraft:oak_log",
            "#minecraft:planks", "minecraft:oak_planks",
            "#minecraft:leaves", "minecraft:oak_leaves",
            "#minecraft:coals", "minecraft:coal",
            "#minecraft:wool", "minecraft:white_wool",
            "#c:dyes", "minecraft:white_dye");

    /** 价钱要一级比一级高的加工链。 */
    private static final List<List<String>> CHAINS = List.of(
            List.of("minecraft:raw_iron", "minecraft:iron_ingot", "universe:gear"),
            List.of("universe:raw_titanium", "universe:titanium_ingot", "universe:titanium_alloy"),
            List.of("universe:circuit_board", "universe:microcrystalline_component", "universe:processor", "universe:quantum_chip"),
            List.of("universe:magnetic_coil", "universe:electric_motor", "universe:electromagnetic_turbine", "universe:super_magnetic_ring"),
            List.of("universe:energy_matrix", "universe:structure_matrix", "universe:information_matrix",
                    "universe:gravity_matrix"));

    @Test
    void tradeGoodsHaveSensiblePrices() throws IOException {
        assertTrue(Files.isDirectory(DATA), "找不到数据生成的结果：" + DATA.toAbsolutePath());
        Map<String, Double> raw = new HashMap<>();
        RawValues.DEFAULT.forEach((key, value) -> raw.put(TAGS.getOrDefault(key, key), value));
        List<ValueGraph.Recipe> recipes = new ArrayList<>(VANILLA);
        recipes.addAll(recipes());
        Map<String, Double> values = ValueGraph.compute(raw, recipes);

        Set<String> missing = new TreeSet<>();
        Map<String, Double> table = new LinkedHashMap<>();
        for (Path tag : jsonFiles(DATA.resolve("tags/item/trade"))) {
            String category = tag.getFileName().toString().replace(".json", "");
            for (JsonElement element : read(tag).getAsJsonObject().getAsJsonArray("values")) {
                String item = element.getAsString();
                if (values.containsKey(item)) {
                    table.put(category + " " + item, values.get(item));
                } else if (!item.equals("universe:multitool")) {
                    missing.add(item);
                }
            }
        }
        StringBuilder report = new StringBuilder("交易品基准价：\n");
        table.forEach((key, value) -> report.append(String.format(Locale.ROOT, "  %-48s %10.1f%n", key, value)));
        System.out.println(report);
        assertEquals(Set.of(), missing, "打了交易标签却算不出价钱");
        for (List<String> chain : CHAINS) {
            for (int i = 1; i < chain.size(); i++) {
                assertTrue(values.get(chain.get(i)) > values.get(chain.get(i - 1)), chain.get(i) + " 应该比 " + chain.get(i - 1) + " 贵");
            }
        }
    }

    private static List<ValueGraph.Recipe> recipes() throws IOException {
        List<ValueGraph.Recipe> out = new ArrayList<>();
        for (Path file : jsonFiles(DATA.resolve("recipe"))) {
            JsonObject json = read(file).getAsJsonObject();
            String type = json.get("type").getAsString();
            List<Input> inputs = new ArrayList<>();
            double seconds = 1;
            if (json.has("key")) {
                // 有序合成：按图案里每个字符出现的次数
                Map<Character, Integer> counts = new HashMap<>();
                json.getAsJsonArray("pattern").forEach(row -> row.getAsString().chars().forEach(c -> counts.merge((char) c, 1, Integer::sum)));
                json.getAsJsonObject("key").entrySet().forEach(entry ->
                        inputs.add(new Input(choices(entry.getValue()), counts.getOrDefault(entry.getKey().charAt(0), 0))));
            }
            if (json.has("ingredients")) {
                for (JsonElement element : json.getAsJsonArray("ingredients")) {
                    if (element.isJsonObject() && element.getAsJsonObject().has("ingredient")) {
                        JsonObject sized = element.getAsJsonObject();
                        inputs.add(new Input(choices(sized.get("ingredient")), sized.has("count") ? sized.get("count").getAsInt() : 1));
                    } else {
                        inputs.add(new Input(choices(element), 1));
                    }
                }
            }
            if (json.has("ingredient")) {
                inputs.add(new Input(choices(json.get("ingredient")), 1));
            }
            if (json.has("cookingtime")) {
                seconds = json.get("cookingtime").getAsInt() / 20.0;
            }
            if (json.has("time")) {
                seconds = json.get("time").getAsInt() / 20.0;
            }
            List<Output> outputs = new ArrayList<>();
            if (json.has("result")) {
                outputs.add(output(json.get("result")));
            }
            if (json.has("results")) {
                json.getAsJsonArray("results").forEach(result -> outputs.add(output(result)));
            }
            if (!inputs.isEmpty() && !outputs.isEmpty() && !type.equals("minecraft:crafting_special_repairitem")) {
                out.add(new ValueGraph.Recipe(inputs, outputs, seconds));
            }
        }
        return out;
    }

    private static Output output(JsonElement element) {
        if (element.isJsonPrimitive()) {
            return new Output(element.getAsString(), 1);
        }
        JsonObject object = element.getAsJsonObject();
        return new Output(object.get("id").getAsString(), object.has("count") ? object.get("count").getAsInt() : 1);
    }

    /** 一个原料：一个物品或几个可选的物品；标签换成代表它的那样东西，染料都算一种。 */
    private static List<String> choices(JsonElement element) {
        List<String> choices = new ArrayList<>();
        if (element.isJsonArray()) {
            element.getAsJsonArray().forEach(child -> choices.add(child.getAsString()));
        } else {
            choices.add(element.getAsString());
        }
        return choices.stream().map(choice -> choice.startsWith("#c:dyes") ? TAGS.get("#c:dyes") : TAGS.getOrDefault(choice, choice)).toList();
    }

    private static List<Path> jsonFiles(Path dir) throws IOException {
        try (Stream<Path> files = Files.walk(dir)) {
            return files.filter(file -> file.toString().endsWith(".json")).sorted().toList();
        }
    }

    private static JsonElement read(Path file) throws IOException {
        return JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
    }
}
