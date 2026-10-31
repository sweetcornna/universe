package io.github.sweetcornna.universe.datagen;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

/**
 * 读数据生成写出来的物品标签（{@code src/generated/resources/data/<命名空间>/tags/item}），把配方里的标签原料展开成物品。
 * 只展开本模组自己的标签（{@code #universe:…}）和 ADR-0039 给本模组材料定的通用标签（{@code #c:ingots/titanium} 这类）：
 * 原版的、别人定义的标签（{@code #minecraft:logs}、{@code #c:foods/raw_fish}）本模组只往里加了几样，文件里不是全部，原样留着，
 * 由各个测试自己处理。
 */
final class GeneratedTags {
    private static final Path DATA = Path.of("src/generated/resources/data");
    private static final Map<String, List<String>> CACHE = new ConcurrentHashMap<>();
    /** 本模组材料的通用标签（{@code UniverseItemTags.COMMON} 和矿石）的路径开头。 */
    private static final List<String> COMMON_PREFIXES = List.of("c:ingots/", "c:raw_materials/", "c:dusts/", "c:ores/", "c:silicon");

    private GeneratedTags() {
    }

    /** 原料里的一项：本模组写出来的标签换成里面的物品（嵌套的标签一起展开），别的原样。 */
    static List<String> expand(String id) {
        if (!id.startsWith("#")) {
            return List.of(id);
        }
        return CACHE.computeIfAbsent(id, key -> {
            Set<String> items = new LinkedHashSet<>();
            add(key, items, new LinkedHashSet<>());
            return List.copyOf(items);
        });
    }

    /** 一串原料展开以后的物品。 */
    static List<String> expandAll(List<String> ids) {
        List<String> all = new ArrayList<>();
        ids.forEach(id -> expand(id).stream().filter(item -> !all.contains(item)).forEach(all::add));
        return all;
    }

    /** 这个标签展不展开：本模组自己的、本模组材料的通用标签，而且数据生成写了文件。 */
    static boolean generated(String tag) {
        if (!tag.startsWith("#")) {
            return false;
        }
        String id = tag.substring(1);
        boolean owned = id.startsWith("universe:") || COMMON_PREFIXES.stream().anyMatch(id::startsWith);
        return owned && Files.isRegularFile(file(tag));
    }

    private static void add(String tag, Set<String> items, Set<String> seen) {
        if (!seen.add(tag)) {
            return;
        }
        Path file = file(tag);
        if (!generated(tag)) {
            items.add(tag);
            return;
        }
        try {
            for (JsonElement value : JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject()
                    .getAsJsonArray("values")) {
                String entry = value.isJsonPrimitive() ? value.getAsString() : value.getAsJsonObject().get("id").getAsString();
                if (entry.startsWith("#")) {
                    add(entry, items, seen);
                } else {
                    items.add(entry);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Path file(String tag) {
        String id = tag.substring(1);
        int colon = id.indexOf(':');
        return DATA.resolve(id.substring(0, colon)).resolve("tags/item").resolve(id.substring(colon + 1) + ".json");
    }
}
