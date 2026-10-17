package io.github.sweetcornna.universe.core.ecology;

import java.util.EnumMap;
import java.util.Map;

/**
 * 树的形态。树由方块拼成（{@link TreeBuilder}），尺寸、颜色、开不开花由物种决定，每棵树再随机变化。
 */
public enum TreeShape {
    SHRUB("灌木", false, Map.of(Biotope.MEADOW, 1.0, Biotope.ARID, 1.5, Biotope.COLD, 1.5, Biotope.WETLAND, 0.5,
            Biotope.FOREST, 0.5, Biotope.CRYSTAL, 0.5, Biotope.WASTE, 0.5)),
    ROUND("阔叶树", false, Map.of(Biotope.FOREST, 3.0, Biotope.MEADOW, 2.0, Biotope.WETLAND, 1.0)),
    CONIFER("针叶树", false, Map.of(Biotope.COLD, 3.0, Biotope.FOREST, 2.0, Biotope.MEADOW, 0.5)),
    UMBRELLA("伞形树", false, Map.of(Biotope.MEADOW, 2.0, Biotope.ARID, 1.0, Biotope.FOREST, 1.0, Biotope.CRYSTAL, 1.0)),
    PALM("棕榈", false, Map.of(Biotope.ARID, 1.5, Biotope.WETLAND, 1.0, Biotope.FOREST, 1.0, Biotope.MEADOW, 0.5)),
    WEEPING("垂枝树", false, Map.of(Biotope.WETLAND, 3.0, Biotope.FOREST, 1.0, Biotope.FUNGAL, 1.0)),
    MUSHROOM("巨菌", true, Map.of(Biotope.FUNGAL, 3.0, Biotope.WETLAND, 1.0, Biotope.FOREST, 0.5)),
    BAOBAB("瓶干树", false, Map.of(Biotope.ARID, 2.0, Biotope.MEADOW, 1.5)),
    SPIRAL("螺旋树", true, Map.of(Biotope.FOREST, 1.0, Biotope.CRYSTAL, 1.5, Biotope.FUNGAL, 1.0, Biotope.MEADOW, 0.5)),
    COLUMN("柱状肉茎", false, Map.of(Biotope.ARID, 3.0, Biotope.WASTE, 1.0)),
    CRYSTAL("晶簇树", true, Map.of(Biotope.CRYSTAL, 3.0, Biotope.COLD, 0.5, Biotope.WASTE, 0.5));

    private final String nameZh;
    private final boolean alien;
    private final Map<Biotope, Double> affinity;

    TreeShape(String nameZh, boolean alien, Map<Biotope, Double> affinity) {
        this.nameZh = nameZh;
        this.alien = alien;
        this.affinity = new EnumMap<>(affinity);
    }

    public String nameZh() {
        return this.nameZh;
    }

    /** 地球上没有的样子：宜居星球上少见。 */
    public boolean alien() {
        return this.alien;
    }

    public double affinity(Biotope biotope) {
        return this.affinity.getOrDefault(biotope, 0.0);
    }
}
