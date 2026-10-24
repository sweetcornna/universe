package io.github.sweetcornna.universe.core.fossil;

import java.util.Locale;

/**
 * 古生物的类群（ADR-0034 §三）。每个类群五个部位，各自一组方块拼的骨头模型（{@link BoneShapes}）。只往后加：
 * 骨架展台和化石展品按序号存。
 *
 * @param rarity 稀有度：决定化石土里出得多不多、骨头值多少钱
 * @param size   骨头的大小（模型按这个缩放）：不同类群的部位拼在一起各管各的大小
 * @param tint   骨头的颜色：所有类群共用一张骨头的贴图，按这个颜色染
 */
public enum BoneFamily {
    /** 巨兽：大型食草动物，带角的大头骨、粗柱子一样的腿、尾巴末端一个骨锤。 */
    TITAN("titan", Rarity.COMMON, 1.45F, 0xF3EAD8),
    /** 掠食兽：两条腿走路的猎手，深长的头骨和一排利齿，前肢短小，尾巴又长又直。 */
    PREDATOR("predator", Rarity.COMMON, 1.1F, 0xECDCC0),
    /** 长颈兽：小脑袋、长脖子、桶一样的胸廓和鞭子似的长尾。 */
    LONGNECK("longneck", Rarity.COMMON, 1.25F, 0xE4DED0),
    /** 翼兽：长喙、头冠，前肢是翼骨，骨头细而中空。 */
    FLYER("flyer", Rarity.UNCOMMON, 0.85F, 0xF5F1E6),
    /** 巨蠕：一节一节的环，口是一圈朝里的牙；"四肢"是体节环。 */
    WORM("worm", Rarity.RARE, 1.15F, 0xEAD6CE),
    /** 甲壳兽：外骨骼，一片片的背甲、大颚、分节的长腿和尾刺。 */
    ARTHROPOD("arthropod", Rarity.RARE, 0.95F, 0xE8C886),
    /** 海兽：长吻、大眼眶，四肢是鳍，尾巴末端是尾鳍。 */
    MARINE("marine", Rarity.UNCOMMON, 1.15F, 0xD9E2E6),
    /** 奇异种：只在奇异星球上，三个眼眶、往外翻的肋骨、分叉的尾巴。 */
    EXOTIC("exotic", Rarity.VERY_RARE, 1.0F, 0xDCCCF2);

    /** 稀有度：化石土里抽到的权重和一块骨头的基准价（信用点）。 */
    public enum Rarity {
        COMMON(10, 300),
        UNCOMMON(4, 700),
        RARE(1.6, 1500),
        VERY_RARE(1.2, 3500);

        private final double weight;
        private final double value;

        Rarity(double weight, double value) {
            this.weight = weight;
            this.value = value;
        }

        public double weight() {
            return this.weight;
        }

        public double value() {
            return this.value;
        }

        public String id() {
            return this.name().toLowerCase(Locale.ROOT);
        }
    }

    private final String id;
    private final Rarity rarity;
    private final float size;
    private final int tint;

    BoneFamily(String id, Rarity rarity, float size, int tint) {
        this.id = id;
        this.rarity = rarity;
        this.size = size;
        this.tint = tint;
    }

    public String id() {
        return this.id;
    }

    public Rarity rarity() {
        return this.rarity;
    }

    public float size() {
        return this.size;
    }

    public int tint() {
        return this.tint;
    }

    /** 按序号找；越界（存档里坏掉的数）返回空。 */
    public static BoneFamily byOrdinal(int ordinal) {
        BoneFamily[] values = values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : null;
    }

    public static BoneFamily byId(String id) {
        for (BoneFamily family : values()) {
            if (family.id.equals(id)) {
                return family;
            }
        }
        return null;
    }
}
