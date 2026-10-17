package io.github.sweetcornna.universe.core.ecology;

import java.util.Set;

import io.github.sweetcornna.universe.core.procgen.NameGenerator.Name;

/**
 * 一种地面植物。
 *
 * @param index     在星球植物列表里的序号
 * @param name      物种名
 * @param form      形态，决定用哪个方块
 * @param variant   方块状态里的变体号（0–3），同一形态的物种各占一个
 * @param body      主体颜色（叶、茎）
 * @param detail    点缀颜色（花、果、菌盖、晶尖）
 * @param glowing   会不会发光
 * @param biotopes  长在哪些生境
 * @param abundance 相对多度，同一处的几个候选物种按它挑
 */
public record PlantSpecies(
        int index,
        Name name,
        PlantForm form,
        int variant,
        int body,
        int detail,
        boolean glowing,
        Set<Biotope> biotopes,
        double abundance) {
}
