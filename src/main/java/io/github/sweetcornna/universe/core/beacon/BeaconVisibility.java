package io.github.sweetcornna.universe.core.beacon;

/**
 * 全息信标能被看到的范围（ADR-0033 §六）：本星球、本恒星系、整个星团。HUD 只标同一颗星球上的信标；星图上标范围到恒星系
 * 和星团的信标：到恒星系的只有人在那个恒星系时才标，到星团的在哪都标。
 */
public enum BeaconVisibility {
    PLANET("planet"),
    SYSTEM("system"),
    CLUSTER("cluster");

    private final String key;

    BeaconVisibility(String key) {
        this.key = key;
    }

    public String key() {
        return this.key;
    }

    /** 界面上点一下换到下一种。 */
    public BeaconVisibility next() {
        BeaconVisibility[] all = values();
        return all[(this.ordinal() + 1) % all.length];
    }

    /**
     * 看的人能不能看到这个信标。
     *
     * @param sameDimension 和信标在同一个维度（同一颗星球、同一片太空）
     * @param sameSystem    和信标在同一个恒星系
     */
    public boolean visible(boolean sameDimension, boolean sameSystem) {
        return switch (this) {
            case PLANET -> sameDimension;
            case SYSTEM -> sameDimension || sameSystem;
            case CLUSTER -> true;
        };
    }

    /** 星图上标不标：范围到恒星系以上，而且看得到。 */
    public boolean onStarMap(boolean sameSystem) {
        return this != PLANET && this.visible(false, sameSystem);
    }

    public static BeaconVisibility byKey(String key) {
        for (BeaconVisibility visibility : values()) {
            if (visibility.key.equals(key)) {
                return visibility;
            }
        }
        return PLANET;
    }

    public static BeaconVisibility byOrdinal(int ordinal) {
        BeaconVisibility[] all = values();
        return ordinal >= 0 && ordinal < all.length ? all[ordinal] : PLANET;
    }
}
