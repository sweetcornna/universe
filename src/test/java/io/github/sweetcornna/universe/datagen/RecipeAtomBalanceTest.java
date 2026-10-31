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
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import io.github.sweetcornna.universe.core.chem.Formula;
import io.github.sweetcornna.universe.core.chem.Substance;
import io.github.sweetcornna.universe.core.chem.Substances;

/**
 * 设备配方的原子守恒（ADR-0037 §二）：进出的物品都是纯物质（{@link Substances} 里写了化学式）的配方，两边每种元素的原子数要一样。
 * 有混合物、虚构物质、零件、标签的配方不查；冶炼（还原剂和炉渣不算物品）、装配（造零件）不查。
 *
 * <p>读的是 {@code src/generated/resources}，改了数据生成要先跑 {@code runData}。
 */
class RecipeAtomBalanceTest {
    private static final Path RECIPES = Path.of("src/generated/resources/data/universe/recipe");
    /** 要查的设备：化工厂、电解槽、反应釜、精炼、粒子对撞、生物反应器。 */
    private static final Set<String> CHECKED = Set.of("universe:chemical", "universe:electrolysis", "universe:reactor", "universe:refining",
            "universe:particle", "universe:bioreactor");
    /** 物质的化学式没错、配方是故意不守恒的，写清为什么。 */
    private static final Map<String, String> EXEMPT = Map.of(
            "chemical/graphene", "照戴森球计划用掉硫酸。现实的 Hummers 法里硫酸是插层剂和氧化的介质，用完成了废酸，不回收",
            "chemical/carbon_nanotube", "照戴森球计划用掉钛，这是紫矩阵前去别的星球的门槛。现实里钛不进纳米管，算作生长用的基底；"
                    + "提纯时烧掉无定形碳，三份碳出两份管",
            "chemical/graphene_from_fire_ice", "甲烷裂解出的碳和氢都对得上；水合物里的 5.75 份水在化工厂里排掉，产物格放不下第三种");

    @Test
    void pureRecipesConserveAtoms() throws IOException {
        assertTrue(Files.isDirectory(RECIPES), "找不到数据生成的结果：" + RECIPES.toAbsolutePath());
        List<String> broken = new ArrayList<>();
        List<String> checked = new ArrayList<>();
        for (Path file : jsonFiles()) {
            JsonObject json = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
            if (!CHECKED.contains(json.get("type").getAsString()) || !json.has("ingredients") || !json.has("results")) {
                continue;
            }
            String name = RECIPES.relativize(file).toString().replace('\\', '/').replace(".json", "");
            Map<String, Double> left = new TreeMap<>();
            Map<String, Double> right = new TreeMap<>();
            boolean pure = true;
            for (JsonElement element : json.getAsJsonArray("ingredients")) {
                JsonObject sized = element.getAsJsonObject();
                pure &= add(left, sized.get("ingredient"), sized.has("count") ? sized.get("count").getAsInt() : 1);
            }
            for (JsonElement element : json.getAsJsonArray("results")) {
                JsonObject result = element.getAsJsonObject();
                pure &= add(right, result.get("id"), result.has("count") ? result.get("count").getAsInt() : 1);
            }
            if (!pure || EXEMPT.containsKey(name)) {
                continue;
            }
            checked.add(name);
            if (!left.equals(right)) {
                broken.add(name + "：" + left + " → " + right);
            }
        }
        assertEquals(List.of(), broken, "原子不守恒的配方");
        // 反应釜的反应式都按纯物质写（费托合成的精炼油、煤气化的煤是混合物，另由 ReactionTest 按化学式查）
        assertTrue(checked.stream().filter(name -> name.startsWith("reactor/")).count() >= 25, "查到的反应釜配方太少：" + checked);
        assertTrue(checked.contains("chemical/sulfuric_acid_from_sulfur") && checked.contains("electrolysis/water"), "该查的没查到：" + checked);
    }

    /** 一样物品的原子数加进去；不是一样确定的纯物质（标签、几选一、混合物、虚构、零件）返回 false。 */
    private static boolean add(Map<String, Double> atoms, JsonElement item, int count) {
        if (!item.isJsonPrimitive() || item.getAsString().startsWith("#")) {
            return false;
        }
        Optional<Substance> substance = Substances.of(item.getAsString());
        if (substance.isEmpty() || substance.get().kind() != Substance.Kind.PURE) {
            return false;
        }
        Formula.add(atoms, substance.get().formula(), count);
        return true;
    }

    private static List<Path> jsonFiles() throws IOException {
        try (Stream<Path> files = Files.walk(RECIPES)) {
            return files.filter(file -> file.toString().endsWith(".json")).sorted().toList();
        }
    }
}
