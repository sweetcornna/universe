package io.github.sweetcornna.universe.core.stats;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jspecify.annotations.Nullable;

/**
 * 配方图（ADR-0033 §四）：一种物品由哪些配方做出来（上游），又被哪些配方用掉（下游）。服务端从配方表建一份，统计面板
 * 选中一种物品时从这里查，参考产量也按这里的原料、产物数量算。
 */
public final class RecipeGraph {
    public static final RecipeGraph EMPTY = new RecipeGraph(List.of());

    /** 一种原料：能用的几种物品（标签原料有好几种）和数量。 */
    public record Input(List<String> items, int count) {
        public Input {
            items = List.copyOf(items);
        }
    }

    public record Output(String item, int count) {
    }

    /**
     * 一个配方。machine 是做它的设备的物品 ID（工作台是 {@code minecraft:crafting_table}），time 是标准时间（刻，速度 1 的
     * 设备做一次要多久），工作台的配方是 0。
     */
    public record Recipe(String id, String machine, List<Input> inputs, List<Output> outputs, int time) {
        public Recipe {
            inputs = List.copyOf(inputs);
            outputs = List.copyOf(outputs);
        }

        /** 做一次产出几个这种物品。 */
        public int produces(String item) {
            int count = 0;
            for (Output output : this.outputs) {
                if (output.item().equals(item)) {
                    count += output.count();
                }
            }
            return count;
        }

        /** 做一次用掉几个这种物品（标签原料里有它也算）。 */
        public int consumes(String item) {
            int count = 0;
            for (Input input : this.inputs) {
                if (input.items().contains(item)) {
                    count += input.count();
                }
            }
            return count;
        }
    }

    private final Map<String, Recipe> recipes = new HashMap<>();
    private final Map<String, List<Recipe>> producers = new HashMap<>();
    private final Map<String, List<Recipe>> consumers = new HashMap<>();

    /** 同一种物品的上下游按设备、再按配方 ID 排，服务端每次排出来一样。 */
    public RecipeGraph(Collection<Recipe> recipes) {
        Comparator<Recipe> order = Comparator.comparing((Recipe recipe) -> recipe.time() == 0).thenComparing(Recipe::machine)
                .thenComparing(Recipe::id);
        for (Recipe recipe : recipes) {
            this.recipes.put(recipe.id(), recipe);
            Set<String> outputs = new LinkedHashSet<>();
            recipe.outputs().forEach(output -> outputs.add(output.item()));
            outputs.forEach(item -> this.producers.computeIfAbsent(item, key -> new ArrayList<>()).add(recipe));
            Set<String> inputs = new LinkedHashSet<>();
            recipe.inputs().forEach(input -> inputs.addAll(input.items()));
            inputs.forEach(item -> this.consumers.computeIfAbsent(item, key -> new ArrayList<>()).add(recipe));
        }
        this.producers.values().forEach(list -> list.sort(order));
        this.consumers.values().forEach(list -> list.sort(order));
    }

    public @Nullable Recipe get(String id) {
        return this.recipes.get(id);
    }

    /** 做出这种物品的配方（上游）。 */
    public List<Recipe> producers(String item) {
        return List.copyOf(this.producers.getOrDefault(item, List.of()));
    }

    /** 用掉这种物品的配方（下游）。 */
    public List<Recipe> consumers(String item) {
        return List.copyOf(this.consumers.getOrDefault(item, List.of()));
    }

    public int size() {
        return this.recipes.size();
    }
}
