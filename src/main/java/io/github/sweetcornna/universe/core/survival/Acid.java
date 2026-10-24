package io.github.sweetcornna.universe.core.survival;

import io.github.sweetcornna.universe.core.procgen.Colors;

/**
 * 酸液（ADR-0030 §一）：毒性星球的海和地下含水层，照浓硫酸做。比水重、稍稠、流得慢一点。
 *
 * <ul>
 *   <li>泡在酸里：毒性防护掉得快（按 {@link #IMMERSED_SEVERITY} 加进毒性的强度），庇护所不管用；一件外骨骼都没穿时
 *       没有防护，直接受伤。防护耗尽后按 {@link #BURN_INTERVAL} 灼伤 {@link #BURN_DAMAGE}，比一般的危害快。</li>
 *   <li>酸雨：和毒雾算在一起，淋着雨时毒性的强度加 {@link #RAIN_SEVERITY}。</li>
 *   <li>别的生物泡在酸里同样灼伤；掉进去的物品过 {@link #CORRODE_DELAY} 刻开始腐蚀，之后每 {@link #CORRODE_INTERVAL} 刻
 *       蚀掉一部分，一组 64 个大约一分钟蚀光。</li>
 * </ul>
 */
public final class Acid {
    /** 泡在酸里加的毒性强度：整套外骨骼 5 分钟的防护，有毒性的星球上一分钟掉光。 */
    public static final double IMMERSED_SEVERITY = 4;
    /** 淋酸雨加的毒性强度。 */
    public static final double RAIN_SEVERITY = 0.5;
    /** 防护耗尽后每隔多少刻灼伤一次、伤多少。 */
    public static final int BURN_INTERVAL = 20;
    public static final float BURN_DAMAGE = 2;
    /** 物品掉进酸里多少刻后开始腐蚀，之后每隔多少刻蚀一次。 */
    public static final int CORRODE_DELAY = 40;
    public static final int CORRODE_INTERVAL = 20;
    /** 一次蚀掉一组的几分之一（至少一个）。 */
    static final int CORRODE_SHARE = 16;
    /** 浓硫酸的颜色：偏黄绿、发暗。不在酸海星球上（比如倒在主世界）用这个。 */
    public static final int DEFAULT_COLOR = 0xB7C74A;
    /** 星球的液体色往黄绿偏多少。 */
    static final double TINT_BIAS = 0.35;

    private Acid() {
    }

    /**
     * 这一刻要蚀掉几个。
     *
     * @param count  这一组还有几个
     * @param ticks  已经在酸里泡了多少刻（从 1 数起）
     */
    public static int corroded(int count, int ticks) {
        if (count <= 0 || ticks < CORRODE_DELAY || (ticks - CORRODE_DELAY) % CORRODE_INTERVAL != 0) {
            return 0;
        }
        return Math.min(count, Math.max(1, count / CORRODE_SHARE));
    }

    /** 酸液的颜色：星球的液体色往黄绿偏一点；{@code liquid} 小于 0 时用 {@link #DEFAULT_COLOR}。 */
    public static int color(int liquid) {
        return liquid < 0 ? DEFAULT_COLOR : Colors.lerp(liquid, DEFAULT_COLOR, TINT_BIAS);
    }
}
