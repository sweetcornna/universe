package io.github.sweetcornna.universe.core.galaxy;

import java.util.Locale;

/** 星球类型（玩法设计 §2.2）。卫星也用这套类型，另有 {@link Planet#moon()} 标记。 */
public enum PlanetType {
    HABITABLE("宜居", "Habitable"),
    BARREN("荒芜", "Barren"),
    FROZEN("冰冻", "Frozen"),
    SCORCHED("灼热", "Scorched"),
    TOXIC("毒雾", "Toxic"),
    IRRADIATED("辐射", "Irradiated"),
    OCEAN("海洋", "Ocean"),
    EXOTIC("奇异", "Exotic"),
    GAS_GIANT("气态巨行星", "Gas giant");

    private final String nameZh;
    private final String nameEn;

    PlanetType(String nameZh, String nameEn) {
        this.nameZh = nameZh;
        this.nameEn = nameEn;
    }

    public String nameZh() {
        return this.nameZh;
    }

    public String nameEn() {
        return this.nameEn;
    }

    /** 气态巨行星不能登陆。 */
    public boolean landable() {
        return this != GAS_GIANT;
    }

    /** 存档、网络、语言键里用的名称。 */
    public String serializedName() {
        return this.name().toLowerCase(Locale.ROOT);
    }
}
