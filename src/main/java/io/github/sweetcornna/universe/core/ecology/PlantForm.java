package io.github.sweetcornna.universe.core.ecology;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

/**
 * 地面植物的形态。每种形态是一个方块（{@code universe:planet_<名称>}），有两种外形；
 * 同一颗星球上同一形态最多 {@link #VARIANTS} 个物种，靠方块状态里的变体号区分颜色。
 * 颜色分两层：主体（叶、茎）和点缀（花、果、菌盖、晶尖）。
 */
public enum PlantForm {
    TUFT("草丛", "Tuft", false, false, Map.of(Biotope.MEADOW, 3.0, Biotope.FOREST, 1.0, Biotope.WETLAND, 1.0,
            Biotope.COLD, 2.0, Biotope.ARID, 0.5, Biotope.CRYSTAL, 0.4)),
    BLADES("长叶草", "Blades", false, false, Map.of(Biotope.MEADOW, 2.0, Biotope.WETLAND, 2.0, Biotope.FOREST, 0.5,
            Biotope.COLD, 0.6)),
    FERN("蕨", "Fern", false, false, Map.of(Biotope.FOREST, 3.0, Biotope.WETLAND, 1.5, Biotope.FUNGAL, 1.0,
            Biotope.MEADOW, 0.4)),
    FLOWER("花", "Flower", true, false, Map.of(Biotope.MEADOW, 3.0, Biotope.FOREST, 1.0, Biotope.COLD, 1.0,
            Biotope.WETLAND, 0.6, Biotope.ARID, 0.5)),
    BULB("球茎", "Bulb", true, true, Map.of(Biotope.MEADOW, 0.8, Biotope.FOREST, 1.0, Biotope.WETLAND, 1.0,
            Biotope.FUNGAL, 2.0, Biotope.CRYSTAL, 1.5)),
    FUNGUS("菌菇", "Fungus", true, false, Map.of(Biotope.FUNGAL, 3.0, Biotope.FOREST, 1.5, Biotope.WETLAND, 1.0,
            Biotope.WASTE, 0.3, Biotope.COLD, 0.3)),
    TENDRIL("卷须", "Tendril", true, true, Map.of(Biotope.FUNGAL, 1.5, Biotope.CRYSTAL, 1.0, Biotope.FOREST, 1.0,
            Biotope.WETLAND, 1.0, Biotope.MEADOW, 0.3)),
    REED("芦苇", "Reed", false, false, Map.of(Biotope.WETLAND, 3.0, Biotope.MEADOW, 0.2)),
    SUCCULENT("多肉", "Succulent", false, false, Map.of(Biotope.ARID, 3.0, Biotope.WASTE, 1.0, Biotope.COLD, 0.3,
            Biotope.MEADOW, 0.2)),
    LICHEN("地衣", "Lichen", true, false, Map.of(Biotope.WASTE, 3.0, Biotope.COLD, 2.0, Biotope.ARID, 1.0,
            Biotope.CRYSTAL, 1.0)),
    CRYSTAL_BLOOM("晶花", "Crystal Bloom", true, true, Map.of(Biotope.CRYSTAL, 3.0, Biotope.WASTE, 0.6, Biotope.COLD, 0.5,
            Biotope.ARID, 0.4));

    /** 同一形态在一颗星球上最多几个物种，也是方块状态里变体号的个数。 */
    public static final int VARIANTS = 4;

    private final String nameZh;
    private final String nameEn;
    private final boolean canGlow;
    private final boolean alien;
    private final Map<Biotope, Double> affinity;

    PlantForm(String nameZh, String nameEn, boolean canGlow, boolean alien, Map<Biotope, Double> affinity) {
        this.nameZh = nameZh;
        this.nameEn = nameEn;
        this.canGlow = canGlow;
        this.alien = alien;
        this.affinity = new EnumMap<>(affinity);
    }

    public String nameZh() {
        return this.nameZh;
    }

    public String nameEn() {
        return this.nameEn;
    }

    /** 方块 ID 的路径部分。 */
    public String blockName() {
        return "planet_" + this.name().toLowerCase(Locale.ROOT);
    }

    /** 这种形态有没有会发光的物种。 */
    public boolean canGlow() {
        return this.canGlow;
    }

    /** 地球上没有的样子：宜居星球上少见，奇异、有毒的星球上多见。 */
    public boolean alien() {
        return this.alien;
    }

    /** 在某种生境里出现的权重，0 表示不长在那里。 */
    public double affinity(Biotope biotope) {
        return this.affinity.getOrDefault(biotope, 0.0);
    }
}
