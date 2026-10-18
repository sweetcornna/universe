package io.github.sweetcornna.universe.core.trade;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 物品的基准价（信用点）：原料按表定价，加工品顺着配方往上推。
 *
 * <p>一个配方产出的每件东西 = （原料的价钱之和 + 耗时 × 每秒的工钱）×（1 + 加工的利润）÷ 产出的件数。一样东西有几条路线时
 * 取最便宜的那条，所以绕远路的配方、循环的配方都不会把价钱抬上去。原料表里的东西不按配方改价。
 *
 * <p>服务端启动和重新加载数据包时从配方算一遍（ADR-0017），数据包改了配方，价钱跟着变。
 */
public final class ValueGraph {
    /** 每秒加工时间值多少信用点。 */
    public static final double SECOND_VALUE = 0.5;
    /** 每道加工的利润。 */
    public static final double MARKUP = 0.15;
    private static final int MAX_PASSES = 64;

    /** 配方的一种原料：几样东西里任选一样，要 count 个。 */
    public record Input(List<String> choices, int count) {
        public Input {
            choices = List.copyOf(choices);
        }
    }

    /** 一件产出。 */
    public record Output(String item, int count) {
    }

    public record Recipe(List<Input> inputs, List<Output> outputs, double seconds) {
        public Recipe {
            inputs = List.copyOf(inputs);
            outputs = List.copyOf(outputs);
        }

        public static Recipe of(String output, int count, double seconds, Input... inputs) {
            return new Recipe(List.of(inputs), List.of(new Output(output, count)), seconds);
        }
    }

    private ValueGraph() {
    }

    /**
     * @param raw 原料的价钱（不会被配方改掉）
     * @return 原料和所有推得出来的东西的价钱；推不出来的不在里面
     */
    public static Map<String, Double> compute(Map<String, Double> raw, List<Recipe> recipes) {
        Map<String, Double> values = new HashMap<>(raw);
        for (int pass = 0; pass < MAX_PASSES; pass++) {
            boolean changed = false;
            for (Recipe recipe : recipes) {
                double cost = recipe.seconds() * SECOND_VALUE;
                boolean known = true;
                for (Input input : recipe.inputs()) {
                    double cheapest = Double.POSITIVE_INFINITY;
                    for (String choice : input.choices()) {
                        Double value = values.get(choice);
                        if (value != null && value < cheapest) {
                            cheapest = value;
                        }
                    }
                    if (cheapest == Double.POSITIVE_INFINITY) {
                        known = false;
                        break;
                    }
                    cost += cheapest * input.count();
                }
                int pieces = recipe.outputs().stream().mapToInt(Output::count).sum();
                if (!known || pieces <= 0) {
                    continue;
                }
                double each = cost * (1 + MARKUP) / pieces;
                for (Output output : recipe.outputs()) {
                    if (raw.containsKey(output.item())) {
                        continue;
                    }
                    Double current = values.get(output.item());
                    // 差得很小时不算变化，免得浮点误差让循环的配方一直转
                    if (current == null || each < current * (1 - 1e-9)) {
                        values.put(output.item(), each);
                        changed = true;
                    }
                }
            }
            if (!changed) {
                break;
            }
        }
        return values;
    }
}
