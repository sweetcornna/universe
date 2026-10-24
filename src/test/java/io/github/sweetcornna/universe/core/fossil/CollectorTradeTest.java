package io.github.sweetcornna.universe.core.fossil;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/** 收藏家收展品、换骨头（ADR-0034 §三）。 */
class CollectorTradeTest {
    private static Bone bone(BoneFamily family, BonePart part) {
        return new Bone(family, part);
    }

    @Test
    void exhibitPriceDoublesWhenCompleteAndMoreWhenPure() {
        Skeleton pure = Skeleton.pure(BoneFamily.TITAN);
        double sum = pure.bones().stream().mapToDouble(Bone::value).sum();
        assertEquals(Math.round(sum * 2 * 1.5), CollectorTrade.exhibitPrice(pure));
        Skeleton mixed = pure.with(BonePart.TAIL, BoneFamily.PREDATOR);
        double mixedSum = mixed.bones().stream().mapToDouble(Bone::value).sum();
        assertEquals(Math.round(mixedSum * 2), CollectorTrade.exhibitPrice(mixed));
        Skeleton partial = pure.with(BonePart.TAIL, null);
        double partialSum = partial.bones().stream().mapToDouble(Bone::value).sum();
        assertEquals(Math.round(partialSum), CollectorTrade.exhibitPrice(partial));
        assertEquals(0, CollectorTrade.exhibitPrice(Skeleton.EMPTY));
        // 拼完整比拆开卖值钱
        assertTrue(CollectorTrade.exhibitPrice(mixed) > mixedSum);
    }

    @Test
    void bonesAreWorthMoreWhenRarer() {
        for (BonePart part : BonePart.values()) {
            double last = 0;
            for (BoneFamily.Rarity rarity : BoneFamily.Rarity.values()) {
                for (BoneFamily family : BoneFamily.values()) {
                    if (family.rarity() == rarity) {
                        assertTrue(bone(family, part).value() > last, family + " " + part);
                    }
                }
                last = rarity.value() * part.valueFactor();
            }
        }
        // 头骨最值钱
        assertTrue(bone(BoneFamily.WORM, BonePart.SKULL).value() > bone(BoneFamily.WORM, BonePart.TAIL).value());
    }

    @Test
    void swapNeedsThreeBonesAndNoRarerThanTheBest() {
        Bone common = bone(BoneFamily.TITAN, BonePart.TAIL);
        Bone rare = bone(BoneFamily.WORM, BonePart.NECK);
        Bone exotic = bone(BoneFamily.EXOTIC, BonePart.LIMBS);
        assertEquals(CollectorTrade.Swap.NEED_THREE, CollectorTrade.check(Arrays.asList(common, common, null), common));
        assertEquals(CollectorTrade.Swap.OK, CollectorTrade.check(List.of(common, common, common), bone(BoneFamily.LONGNECK, BonePart.SKULL)));
        assertEquals(CollectorTrade.Swap.TOO_RARE, CollectorTrade.check(List.of(common, common, common), bone(BoneFamily.MARINE, BonePart.SKULL)));
        assertEquals(CollectorTrade.Swap.OK, CollectorTrade.check(List.of(common, rare, common), bone(BoneFamily.ARTHROPOD, BonePart.SKULL)));
        assertEquals(CollectorTrade.Swap.TOO_RARE, CollectorTrade.check(List.of(common, rare, common), bone(BoneFamily.EXOTIC, BonePart.SKULL)));
        assertEquals(CollectorTrade.Swap.OK, CollectorTrade.check(List.of(exotic, common, common), bone(BoneFamily.EXOTIC, BonePart.SKULL)));
    }

    @Test
    void rawValuesCoverEveryBone() {
        Map<String, Double> values = CollectorTrade.rawValues();
        assertEquals(40, values.size());
        for (Bone bone : Bone.all()) {
            assertEquals(bone.value(), values.get("universe:" + bone.id()));
        }
    }
}
