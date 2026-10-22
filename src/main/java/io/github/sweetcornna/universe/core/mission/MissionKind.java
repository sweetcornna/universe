package io.github.sweetcornna.universe.core.mission;

import java.util.Locale;

/** 空间站任务的种类（ADR-0017）。只往后加：存档按名字认，网络按序号。 */
public enum MissionKind {
    /** 送货：空间站缺的大类里的一样东西，带够件数回来交。 */
    DELIVERY,
    /** 清剿：在哪儿都行，打下 N 架海盗战机。 */
    BOUNTY,
    /** 采矿：打碎 N 颗小行星。 */
    MINING,
    /** 测绘：用分析目镜分析这个恒星系里 N 种动植物。 */
    SURVEY,
    /** 守卫机：打掉 N 架守卫机，地上的、太空里的都算（ADR-0027 §六，只有佣兵会的使者给）。 */
    SENTINELS,
    /** 地标：去这个恒星系某颗星球上的一处地标，走到跟前（ADR-0027 §六，只有探险会的使者给）。 */
    LANDMARK;

    public String key() {
        return this.name().toLowerCase(Locale.ROOT);
    }
}
