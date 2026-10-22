package io.github.sweetcornna.universe.core.trade;

import io.github.sweetcornna.universe.core.procgen.Rng;
import io.github.sweetcornna.universe.core.procgen.Seeds;

/**
 * 星球上交易站的货（ADR-0026）：价钱照本恒星系空间站的行情，货少一些。生存必需的几样（燃料、氧气、防护电池、
 * 空间翘曲器）总有，别的每样按交易站的种子有四成的机会摆出来；技术模块只摆一个。
 */
public final class OutpostStock {
    /** 普通货物摆出来的概率。 */
    static final double SHARE = 0.4;
    /** 摆几个技术模块。 */
    public static final int MODULES = 1;

    private OutpostStock() {
    }

    /**
     * 这个交易站卖不卖这样东西。
     *
     * @param seed      交易站的种子
     * @param item      物品 ID
     * @param essential 生存必需品
     */
    public static boolean carries(long seed, String item, boolean essential) {
        return essential || Rng.of(Seeds.derive(Seeds.derive(seed, "outpost_stock"), item)).chance(SHARE);
    }
}
