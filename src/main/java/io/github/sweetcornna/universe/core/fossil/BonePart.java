package io.github.sweetcornna.universe.core.fossil;

/**
 * 骨架的部位（ADR-0034 §三）。骨架展台上每个部位一格，只收对应部位的骨头。只往后加。
 *
 * @param valueFactor 这个部位的价钱是类群基准价的几倍：头骨最值钱，尾骨最便宜
 * @param findWeight  化石土里挖到这个部位的权重：头骨少，四肢、尾骨多
 */
public enum BonePart {
    SKULL("skull", 1.4, 0.7),
    NECK("neck", 0.9, 1.1),
    RIBCAGE("ribcage", 1.2, 0.8),
    /** 四肢；巨蠕的是体节环。 */
    LIMBS("limbs", 1.0, 1.2),
    TAIL("tail", 0.7, 1.2);

    private final String id;
    private final double valueFactor;
    private final double findWeight;

    BonePart(String id, double valueFactor, double findWeight) {
        this.id = id;
        this.valueFactor = valueFactor;
        this.findWeight = findWeight;
    }

    public String id() {
        return this.id;
    }

    public double valueFactor() {
        return this.valueFactor;
    }

    public double findWeight() {
        return this.findWeight;
    }

    public static BonePart byOrdinal(int ordinal) {
        BonePart[] values = values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : null;
    }

    public static BonePart byId(String id) {
        for (BonePart part : values()) {
            if (part.id.equals(id)) {
                return part;
            }
        }
        return null;
    }
}
