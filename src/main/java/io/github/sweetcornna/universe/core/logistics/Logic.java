package io.github.sweetcornna.universe.core.logistics;

/** 物品格的供需设置（ADR-0015），本地（无人机）和远程（运输船）各一个。 */
public enum Logic {
    /** 仓储：只存放，不主动参与运输。 */
    STORAGE,
    /** 供应：别的站要这种物品时从这里拿。 */
    SUPPLY,
    /** 需求：从供应这种物品的站运过来，补到上限为止。 */
    DEMAND;

    /** 界面上点一下换到下一种。 */
    public Logic next() {
        return values()[(this.ordinal() + 1) % values().length];
    }
}
