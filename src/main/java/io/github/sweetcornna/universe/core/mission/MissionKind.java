package io.github.sweetcornna.universe.core.mission;

import java.util.Locale;

/** 空间站任务的种类（ADR-0017）。 */
public enum MissionKind {
    /** 送货：空间站缺的大类里的一样东西，带够件数回来交。 */
    DELIVERY,
    /** 清剿：在哪儿都行，打下 N 架海盗战机。 */
    BOUNTY,
    /** 采矿：打碎 N 颗小行星。 */
    MINING,
    /** 测绘：用分析目镜分析这个恒星系里 N 种动植物。 */
    SURVEY;

    public String key() {
        return this.name().toLowerCase(Locale.ROOT);
    }
}
