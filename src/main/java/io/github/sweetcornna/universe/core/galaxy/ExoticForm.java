package io.github.sweetcornna.universe.core.galaxy;

import java.util.Locale;
import java.util.Optional;

import io.github.sweetcornna.universe.core.procgen.Rng;
import io.github.sweetcornna.universe.core.procgen.Seeds;

/**
 * 奇异星球的子类型（ADR-0030 §二）：每颗奇异星球有一种整颗星球都看得到的奇特地貌，都照着地球和火星上真有的地貌做。
 *
 * <p>由星球种子按 {@code Seeds.derive(seed, "exotic")} 派生，不进 {@link Planet} 记录，也不改星球别的属性；
 * 客户端用同一个种子算，不用网络传。派生结果属于存档格式。
 */
public enum ExoticForm {
    /** 六棱的玄武岩柱，成片的石阶伸进海里，偶尔一座孤立的柱状岩塔（巨人堤道、魔鬼塔）。 */
    BASALT_COLUMNS("柱状玄武岩", "Columnar basalt"),
    /** 细高的石柱，顶上压着一块硬岩，成片挤在一起（布莱斯峡谷的石林）。 */
    HOODOOS("石林", "Hoodoos"),
    /** 几十格长的斜晶柱从地里穿出来，一簇簇的（奈卡水晶洞）。 */
    GIANT_CRYSTALS("巨晶", "Giant crystals"),
    /** 半埋在地里的圆石球，大的带白色裂纹，地上撒着小黑球（摩拉基大圆石、火星"蓝莓"）。 */
    CONCRETIONS("结核球", "Concretions"),
    /** 平地上一张多边形的网（盐沼的盐壳、火星和冻土上的冰楔多边形）。 */
    POLYGONS("多边形地面", "Polygonal ground"),
    /** 岩层里的天然石拱和石鳍（拱门国家公园）。 */
    ARCHES("天然石拱", "Natural arches");

    private static final ExoticForm[] VALUES = values();

    private final String nameZh;
    private final String nameEn;

    ExoticForm(String nameZh, String nameEn) {
        this.nameZh = nameZh;
        this.nameEn = nameEn;
    }

    public String nameZh() {
        return this.nameZh;
    }

    public String nameEn() {
        return this.nameEn;
    }

    public String serializedName() {
        return this.name().toLowerCase(Locale.ROOT);
    }

    /** 这颗星球的奇异地貌；不是奇异星球时为空。 */
    public static Optional<ExoticForm> of(Planet planet) {
        return planet.type() == PlanetType.EXOTIC ? Optional.of(of(planet.seed())) : Optional.empty();
    }

    /** 按星球种子挑一种（不看星球类型）。 */
    public static ExoticForm of(long planetSeed) {
        return VALUES[Rng.of(Seeds.derive(planetSeed, "exotic")).nextInt(VALUES.length)];
    }
}
