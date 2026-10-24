package io.github.sweetcornna.universe.core.logistics;

import org.jspecify.annotations.Nullable;

/**
 * 物流配对的层次（ADR-0033 §三）：供需两格找配对时从高到低看，高一层有能配上的就不往下看。
 *
 * <p>不是绝对的：某一层的对方暂时没货或满了，才往下一层找；一格在高一层有对象等着时，低一层的不来抢它（让路）。
 */
public enum Tier {
    /** ① 点对点：两座站互为伙伴（一方加了另一方就算）。 */
    POINT_TO_POINT,
    /** ② 星球路线：这种物品从供方的星球运到需方的星球。 */
    PLANET_ROUTE,
    /** ③ 星系路线：这种物品从供方的星系运到需方的星系。 */
    SYSTEM_ROUTE,
    /** ④ 同组：两座站分在同一组。 */
    GROUP,
    /** ⑤ 普通配对：原来的按距离找最近的。 */
    NORMAL;

    /** 更高（更优先）的那个；{@code null} 当成最低。 */
    public static @Nullable Tier best(@Nullable Tier a, @Nullable Tier b) {
        if (a == null) {
            return b;
        }
        return b == null || a.ordinal() <= b.ordinal() ? a : b;
    }

    /** 这一层比 {@code other} 低（{@code other} 为空时什么都不比它高）。 */
    public boolean below(@Nullable Tier other) {
        return other != null && this.ordinal() > other.ordinal();
    }
}
