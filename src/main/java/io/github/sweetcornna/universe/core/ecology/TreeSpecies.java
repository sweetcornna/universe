package io.github.sweetcornna.universe.core.ecology;

import java.util.Set;

import io.github.sweetcornna.universe.core.procgen.NameGenerator.Name;

/**
 * 一种树（也包括灌木、巨菌、柱状肉茎、晶簇树这些用方块拼出来的大型植物）。
 *
 * @param index       在星球树木列表里的序号，也是木头、树叶等方块的变体号（0–3）
 * @param name        物种名
 * @param shape       形态
 * @param height      主干的基本高度（格）
 * @param heightRange 每棵树在基本高度上随机增加 0 到这么多格
 * @param canopy      树冠半径（格）；螺旋树是螺旋半径，柱状肉茎是侧枝数
 * @param leafDensity 树冠的饱满程度 0–1，越小越稀疏
 * @param lean        主干倾斜程度（弧度量级），棕榈最明显
 * @param thick       主干两格粗
 * @param wood        树干颜色（巨菌是菌柄）
 * @param leaves      树叶颜色（巨菌是菌盖，柱状肉茎是肉茎）
 * @param bloom       花、果的颜色
 * @param bloomChance 树冠上长花果的概率，0 表示不开花
 * @param bloomGlows  花果会不会发光
 * @param biotopes    长在哪些生境
 * @param abundance   相对多度
 */
public record TreeSpecies(
        int index,
        Name name,
        TreeShape shape,
        int height,
        int heightRange,
        int canopy,
        double leafDensity,
        double lean,
        boolean thick,
        int wood,
        int leaves,
        int bloom,
        double bloomChance,
        boolean bloomGlows,
        Set<Biotope> biotopes,
        double abundance) {
    /** 一颗星球上最多几种树，也是树木方块变体号的个数。 */
    public static final int MAX = 4;
}
