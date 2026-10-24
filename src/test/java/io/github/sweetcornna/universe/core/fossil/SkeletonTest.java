package io.github.sweetcornna.universe.core.fossil;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

/** 骨头、骨架和骨架的名字（ADR-0034 §三）。 */
class SkeletonTest {
    @Test
    void familiesAndPartsKeepTheirOrder() {
        // 展台和展品按序号存：只能往后加
        assertEquals(List.of("titan", "predator", "longneck", "flyer", "worm", "arthropod", "marine", "exotic"),
                java.util.Arrays.stream(BoneFamily.values()).map(BoneFamily::id).toList());
        assertEquals(List.of("skull", "neck", "ribcage", "limbs", "tail"), java.util.Arrays.stream(BonePart.values()).map(BonePart::id).toList());
        assertEquals(BoneFamily.Rarity.COMMON, BoneFamily.TITAN.rarity());
        assertEquals(BoneFamily.Rarity.UNCOMMON, BoneFamily.MARINE.rarity());
        assertEquals(BoneFamily.Rarity.RARE, BoneFamily.WORM.rarity());
        assertEquals(BoneFamily.Rarity.VERY_RARE, BoneFamily.EXOTIC.rarity());
    }

    @Test
    void fortyBonesWithUniqueIds() {
        List<Bone> bones = Bone.all();
        assertEquals(40, bones.size());
        Set<String> ids = new HashSet<>();
        for (Bone bone : bones) {
            assertTrue(bone.id().matches("bone_[a-z]+_[a-z]+"), bone.id());
            assertTrue(ids.add(bone.id()), bone.id());
            assertEquals(bone, Bone.byId(bone.id()));
        }
        assertNull(Bone.byId("bone_dragon_skull"));
        assertNull(Bone.byId("skull_fossil"));
        assertEquals("bone_worm_limbs", new Bone(BoneFamily.WORM, BonePart.LIMBS).id());
    }

    @Test
    void namesComeFromTheRibcageAndTheOddParts() {
        // ADR 的例子：长颈兽的脖子接在掠食兽身上
        Skeleton chimera = Skeleton.pure(BoneFamily.PREDATOR).with(BonePart.NECK, BoneFamily.LONGNECK);
        Skeleton.Name name = chimera.name();
        assertNotNull(name);
        assertEquals(BoneFamily.PREDATOR, name.noun());
        assertEquals(List.of(BoneFamily.LONGNECK), name.modifiers());
        // 五件同一类群：就叫那个类群
        Skeleton.Name pure = Skeleton.pure(BoneFamily.MARINE).name();
        assertEquals(BoneFamily.MARINE, pure.noun());
        assertTrue(pure.modifiers().isEmpty());
        // 修饰最多两个，按头骨、颈椎、四肢、尾骨的顺序，重复的只算一次
        Skeleton mixed = Skeleton.of(BoneFamily.FLYER, BoneFamily.WORM, BoneFamily.TITAN, BoneFamily.FLYER, BoneFamily.EXOTIC);
        assertEquals(List.of(BoneFamily.FLYER, BoneFamily.WORM), mixed.name().modifiers());
        assertEquals(BoneFamily.TITAN, mixed.name().noun());
        // 不完整的没有名字
        assertNull(Skeleton.pure(BoneFamily.TITAN).with(BonePart.TAIL, null).name());
    }

    @Test
    void countsCompleteAndPure() {
        assertTrue(Skeleton.EMPTY.isEmpty());
        assertEquals(0, Skeleton.EMPTY.count());
        Skeleton three = Skeleton.EMPTY.with(new Bone(BoneFamily.TITAN, BonePart.SKULL)).with(new Bone(BoneFamily.TITAN, BonePart.TAIL))
                .with(new Bone(BoneFamily.WORM, BonePart.LIMBS));
        assertEquals(3, three.count());
        assertFalse(three.complete());
        assertFalse(three.pure());
        assertEquals(List.of(new Bone(BoneFamily.TITAN, BonePart.SKULL), new Bone(BoneFamily.WORM, BonePart.LIMBS),
                new Bone(BoneFamily.TITAN, BonePart.TAIL)), three.bones());
        assertTrue(Skeleton.pure(BoneFamily.FLYER).pure());
        assertFalse(Skeleton.pure(BoneFamily.FLYER).with(BonePart.SKULL, BoneFamily.TITAN).pure());
    }

    @Test
    void encodesForSavesAndComponents() {
        Skeleton skeleton = Skeleton.of(BoneFamily.EXOTIC, null, BoneFamily.TITAN, BoneFamily.ARTHROPOD, null);
        int[] codes = skeleton.encode();
        assertArrayEquals(new int[] {7, -1, 0, 5, -1}, codes);
        assertEquals(skeleton, Skeleton.decode(codes));
        assertEquals(skeleton, Skeleton.decode(skeleton.codes()));
        assertEquals(skeleton.hashCode(), Skeleton.decode(codes).hashCode());
        // 坏掉的数：越界的格当空的，长度不够的补空
        assertEquals(Skeleton.EMPTY.with(BonePart.SKULL, BoneFamily.PREDATOR), Skeleton.decode(new int[] {1, 99}));
        assertEquals(Skeleton.EMPTY, Skeleton.decode((int[]) null));
    }
}
