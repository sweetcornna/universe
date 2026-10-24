package io.github.sweetcornna.universe.core.fossil;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 化石收藏家的两样生意（ADR-0034 §三）。
 *
 * <ul>
 *   <li>收展品：价钱 = 部件价钱之和，完整的 ×2，五件同一类群的再 ×1.5。</li>
 *   <li>换骨头：交 3 块不要的骨头，换 1 块指定类群、指定部位的。换来的类群不能比交上去最稀有的那块更稀有，
 *       不然拿三块常见的就能换出奇异种。</li>
 * </ul>
 */
public final class CollectorTrade {
    /** 换一块要交几块。 */
    public static final int SWAP_COST = 3;
    public static final double COMPLETE_BONUS = 2.0;
    public static final double PURE_BONUS = 1.5;

    private CollectorTrade() {
    }

    /** 一件展品收多少信用点；空的不收（0）。 */
    public static long exhibitPrice(Skeleton skeleton) {
        double sum = 0;
        for (Bone bone : skeleton.bones()) {
            sum += bone.value();
        }
        if (skeleton.complete()) {
            sum *= COMPLETE_BONUS;
            if (skeleton.pure()) {
                sum *= PURE_BONUS;
            }
        }
        return Math.round(sum);
    }

    /** 换骨头行不行。 */
    public enum Swap {
        OK,
        /** 不够 3 块。 */
        NEED_THREE,
        /** 要换的比交上去的都稀有。 */
        TOO_RARE
    }

    /** 交上去的骨头（空格写 null）换 {@code wanted} 行不行。 */
    public static Swap check(List<Bone> given, Bone wanted) {
        int count = 0;
        BoneFamily.Rarity best = null;
        for (Bone bone : given) {
            if (bone == null) {
                continue;
            }
            count++;
            if (best == null || bone.family().rarity().ordinal() > best.ordinal()) {
                best = bone.family().rarity();
            }
        }
        if (count < SWAP_COST) {
            return Swap.NEED_THREE;
        }
        return wanted.family().rarity().ordinal() > best.ordinal() ? Swap.TOO_RARE : Swap.OK;
    }

    /** 40 种骨头的基准价，按物品 ID（{@code universe:bone_...}）并进价钱表（{@code RawValues}）。 */
    public static Map<String, Double> rawValues() {
        Map<String, Double> values = new HashMap<>();
        for (Bone bone : Bone.all()) {
            values.put("universe:" + bone.id(), bone.value());
        }
        return values;
    }
}
