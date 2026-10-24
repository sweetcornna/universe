package io.github.sweetcornna.universe.core.hazard;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

import io.github.sweetcornna.universe.core.galaxy.Hazard;
import io.github.sweetcornna.universe.core.galaxy.PlanetType;

/**
 * 间歇泉按星球分四种（ADR-0034 §六）：喷出来的东西照星球扣防护，喷口边上的结壳挖出来的东西也不一样。
 *
 * <ul>
 *   <li>灼热星球：滚烫的水汽，结壳里是硫；</li>
 *   <li>毒雾星球：带着硫化物的毒汽，结壳是一层盐华；</li>
 *   <li>冰冻星球：喷出来就冻成冰屑的冷汽（像土卫二的冰羽），结壳里是霜晶；</li>
 *   <li>奇异星球：发着淡光的汽，结壳是硅华，里面有光学晶体。</li>
 * </ul>
 */
public enum GeyserKind {
    HOT(Hazard.HEAT, "universe:sulfur", 1, 3, 0xE8E2D6, "滚烫", "Scalding"),
    TOXIC(Hazard.TOXIC, "universe:salt", 1, 3, 0xC9D98A, "毒汽", "Toxic"),
    COLD(Hazard.COLD, "universe:frost_crystal", 1, 2, 0xDDEEFF, "冷冻", "Cryo"),
    STRANGE(null, "universe:optical_crystal", 1, 1, 0xE6C8F0, "奇异", "Strange");

    private final Hazard hazard;
    private final String crustDrop;
    private final int crustMin;
    private final int crustMax;
    private final int steamColor;
    private final String nameZh;
    private final String nameEn;

    GeyserKind(Hazard hazard, String crustDrop, int crustMin, int crustMax, int steamColor, String nameZh, String nameEn) {
        this.hazard = hazard;
        this.crustDrop = crustDrop;
        this.crustMin = crustMin;
        this.crustMax = crustMax;
        this.steamColor = steamColor;
        this.nameZh = nameZh;
        this.nameEn = nameEn;
    }

    /** 这颗星球上的间歇泉是哪一种；别的星球没有间歇泉。 */
    public static Optional<GeyserKind> of(PlanetType type) {
        return Optional.ofNullable(switch (type) {
            case SCORCHED -> HOT;
            case TOXIC -> TOXIC;
            case FROZEN -> COLD;
            case EXOTIC -> STRANGE;
            default -> null;
        });
    }

    /**
     * 水汽柱扣哪一条防护。奇异星球的照星球自己的第一种危害，没有危害的扣高温（水汽总是烫的）。
     *
     * @param planet 星球的危害
     */
    public Hazard hazard(List<Hazard> planet) {
        if (this.hazard != null) {
            return this.hazard;
        }
        return planet.isEmpty() ? Hazard.HEAT : planet.getFirst();
    }

    /** 结壳挖出来的物品。 */
    public String crustDrop() {
        return this.crustDrop;
    }

    /** 一块结壳最少掉几个（时运另算）。 */
    public int crustMin() {
        return this.crustMin;
    }

    public int crustMax() {
        return this.crustMax;
    }

    /** 水汽的颜色（RGB）。 */
    public int steamColor() {
        return this.steamColor;
    }

    public String nameZh() {
        return this.nameZh;
    }

    public String nameEn() {
        return this.nameEn;
    }

    public String id() {
        return this.name().toLowerCase(Locale.ROOT);
    }
}
