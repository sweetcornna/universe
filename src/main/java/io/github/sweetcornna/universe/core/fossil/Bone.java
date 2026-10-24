package io.github.sweetcornna.universe.core.fossil;

/**
 * 一块骨头：哪个类群的哪个部位（ADR-0034 §三）。8 × 5 = 40 种，各是一种物品，注册名是 {@link #id()}。
 */
public record Bone(BoneFamily family, BonePart part) {
    public Bone {
        if (family == null || part == null) {
            throw new IllegalArgumentException("bone needs a family and a part");
        }
    }

    /** 物品的注册名，也是语言键的后缀：{@code bone_<类群>_<部位>}。 */
    public String id() {
        return "bone_" + this.family.id() + "_" + this.part.id();
    }

    /** 一块骨头的基准价（信用点）：类群的稀有度定基准，部位定倍数。 */
    public double value() {
        return this.family.rarity().value() * this.part.valueFactor();
    }

    /** 全部 40 种，先按类群、再按部位排。 */
    public static java.util.List<Bone> all() {
        java.util.List<Bone> bones = new java.util.ArrayList<>(BoneFamily.values().length * BonePart.values().length);
        for (BoneFamily family : BoneFamily.values()) {
            for (BonePart part : BonePart.values()) {
                bones.add(new Bone(family, part));
            }
        }
        return java.util.List.copyOf(bones);
    }

    /** 从注册名认回来；不是骨头返回空。 */
    public static Bone byId(String id) {
        if (!id.startsWith("bone_")) {
            return null;
        }
        for (Bone bone : all()) {
            if (bone.id().equals(id)) {
                return bone;
            }
        }
        return null;
    }
}
