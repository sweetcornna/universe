package io.github.sweetcornna.universe.core.blueprint;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** 蓝图的作者、版本、说明和"只贴设备"（ADR-0033 §九）。 */
public final class BlueprintMeta {
    public static final int MAX_DESCRIPTION = 96;
    /** HUD 上最多列几种材料。 */
    public static final int COST_LINES = 8;

    private BlueprintMeta() {
    }

    /** 同名的蓝图已经存过这些版本时，这次存是第几版：最大的加 1，没有就是 1。 */
    public static int nextVersion(Iterable<Integer> versions) {
        int max = 0;
        for (int version : versions) {
            max = Math.max(max, version);
        }
        return max + 1;
    }

    /** "只贴设备"时放不放这个方块：设备（有方块实体或标了标签的）放，地板、墙、装饰跳过；平时都放。 */
    public static boolean keep(boolean devicesOnly, boolean device) {
        return !devicesOnly || device;
    }

    /** 材料清单的一行：要多少、身上有多少。 */
    public record Cost<K>(K item, int need, int have) {
        public boolean shortage() {
            return this.have < this.need;
        }
    }

    /** 要花的材料：缺的排前面，再按数量从多到少；身上没有的按 0 算。 */
    public static <K> List<Cost<K>> costs(Map<K, Integer> need, Map<K, Integer> have) {
        List<Cost<K>> list = new ArrayList<>();
        need.forEach((item, count) -> {
            if (count > 0) {
                list.add(new Cost<>(item, count, have.getOrDefault(item, 0)));
            }
        });
        list.sort(Comparator.comparing((Cost<K> cost) -> !cost.shortage()).thenComparing((Cost<K> cost) -> -cost.need()));
        return list;
    }
}
