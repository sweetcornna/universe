package io.github.sweetcornna.universe.core.fossil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 骨架展台上的一副骨架（ADR-0034 §三）：五个部位各放了哪个类群的骨头，没放的是空。也是化石展品里记着的东西。
 *
 * <p>五格都放上就是完整的骨架，照部件起名：胸廓定"是什么兽"，头骨、颈椎、四肢、尾骨里和胸廓不同的类群
 * （按这个顺序，最多两个）当修饰，比如长颈兽的脖子接在掠食兽身上就是"长颈掠食兽"。五件同一类群的就叫那个类群。
 */
public final class Skeleton {
    /** 名字里最多几个修饰。 */
    public static final int MAX_MODIFIERS = 2;
    public static final Skeleton EMPTY = new Skeleton(new BoneFamily[BonePart.values().length]);

    private final BoneFamily[] families;

    private Skeleton(BoneFamily[] families) {
        this.families = families;
    }

    /** 五个部位按 {@link BonePart} 的顺序，空的写 null。 */
    public static Skeleton of(BoneFamily skull, BoneFamily neck, BoneFamily ribcage, BoneFamily limbs, BoneFamily tail) {
        return new Skeleton(new BoneFamily[] {skull, neck, ribcage, limbs, tail});
    }

    /** 五件同一类群。 */
    public static Skeleton pure(BoneFamily family) {
        return of(family, family, family, family, family);
    }

    public BoneFamily family(BonePart part) {
        return this.families[part.ordinal()];
    }

    public boolean has(BonePart part) {
        return this.families[part.ordinal()] != null;
    }

    /** 这个部位的骨头；空的返回 null。 */
    public Bone bone(BonePart part) {
        BoneFamily family = this.family(part);
        return family == null ? null : new Bone(family, part);
    }

    /** 换掉一个部位（{@code family} 为 null 时拿走）。 */
    public Skeleton with(BonePart part, BoneFamily family) {
        BoneFamily[] copy = this.families.clone();
        copy[part.ordinal()] = family;
        return new Skeleton(copy);
    }

    public Skeleton with(Bone bone) {
        return this.with(bone.part(), bone.family());
    }

    /** 放了几件。 */
    public int count() {
        int count = 0;
        for (BoneFamily family : this.families) {
            if (family != null) {
                count++;
            }
        }
        return count;
    }

    public boolean isEmpty() {
        return this.count() == 0;
    }

    public boolean complete() {
        return this.count() == this.families.length;
    }

    /** 完整而且五件同一类群。 */
    public boolean pure() {
        if (!this.complete()) {
            return false;
        }
        for (BoneFamily family : this.families) {
            if (family != this.families[0]) {
                return false;
            }
        }
        return true;
    }

    /** 放着的骨头，按部位的顺序。 */
    public List<Bone> bones() {
        List<Bone> bones = new ArrayList<>(this.families.length);
        for (BonePart part : BonePart.values()) {
            Bone bone = this.bone(part);
            if (bone != null) {
                bones.add(bone);
            }
        }
        return bones;
    }

    /** 骨架的名字：胸廓的类群和修饰；不完整的没有名字（返回 null）。 */
    public Name name() {
        if (!this.complete()) {
            return null;
        }
        BoneFamily noun = this.family(BonePart.RIBCAGE);
        List<BoneFamily> modifiers = new ArrayList<>(MAX_MODIFIERS);
        for (BonePart part : List.of(BonePart.SKULL, BonePart.NECK, BonePart.LIMBS, BonePart.TAIL)) {
            BoneFamily family = this.family(part);
            if (family != noun && !modifiers.contains(family) && modifiers.size() < MAX_MODIFIERS) {
                modifiers.add(family);
            }
        }
        return new Name(List.copyOf(modifiers), noun);
    }

    /** 骨架的名字：修饰在前，名词在后（"长颈" + "掠食兽"）。 */
    public record Name(List<BoneFamily> modifiers, BoneFamily noun) {
    }

    /** 存档、数据组件用：五个类群的序号，空的写 -1。 */
    public int[] encode() {
        int[] codes = new int[this.families.length];
        for (int i = 0; i < codes.length; i++) {
            codes[i] = this.families[i] == null ? -1 : this.families[i].ordinal();
        }
        return codes;
    }

    /** {@link #encode()} 的反过程；长度不对、序号越界的格当空的。 */
    public static Skeleton decode(int[] codes) {
        BoneFamily[] families = new BoneFamily[BonePart.values().length];
        for (int i = 0; i < families.length && codes != null && i < codes.length; i++) {
            families[i] = BoneFamily.byOrdinal(codes[i]);
        }
        return new Skeleton(families);
    }

    public static Skeleton decode(List<Integer> codes) {
        return decode(codes.stream().mapToInt(Integer::intValue).toArray());
    }

    public List<Integer> codes() {
        return Arrays.stream(this.encode()).boxed().toList();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Skeleton skeleton && Arrays.equals(this.families, skeleton.families);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(this.families);
    }

    @Override
    public String toString() {
        return "Skeleton" + Arrays.toString(this.families);
    }
}
