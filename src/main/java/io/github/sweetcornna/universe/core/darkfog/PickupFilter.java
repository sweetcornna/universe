package io.github.sweetcornna.universe.core.darkfog;

/** 战场分析基地捡东西的过滤（ADR-0028 §五）。存档里记的是序号，以后只能往后加。 */
public enum PickupFilter {
    /** 什么都捡。 */
    ALL("all"),
    /** 只捡黑雾的掉落。 */
    DARK_FOG("dark_fog"),
    /** 只捡储物格里已经有的东西。 */
    STORED("stored"),
    /** 不捡。 */
    OFF("off");

    private final String id;

    PickupFilter(String id) {
        this.id = id;
    }

    public String id() {
        return this.id;
    }

    /**
     * 捡不捡这件东西。
     *
     * @param darkFogLoot 是黑雾的掉落
     * @param stored      储物格里已经有这种东西
     */
    public boolean accepts(boolean darkFogLoot, boolean stored) {
        return switch (this) {
            case ALL -> true;
            case DARK_FOG -> darkFogLoot;
            case STORED -> stored;
            case OFF -> false;
        };
    }

    /** 界面上按一下换下一种。 */
    public PickupFilter next() {
        PickupFilter[] values = values();
        return values[(this.ordinal() + 1) % values.length];
    }

    public static PickupFilter byIndex(int index) {
        PickupFilter[] values = values();
        return index >= 0 && index < values.length ? values[index] : ALL;
    }
}
