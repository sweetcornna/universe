package io.github.sweetcornna.universe.core.galaxy;

/**
 * 恒星类型（玩法设计 §1.3）。
 *
 * <p>数值都以太阳为 1：光度、质量、半径。生成时在给定范围内对数均匀取值，权重决定出现频率。
 * 中子星和黑洞几乎不发可见光，光度取很小的值，行星温度由潮汐加热和吸积盘之类的设定兜底（见 {@code GalaxyGenerator}）。
 */
public enum StarClass {
    M("M 型红矮星", "Red dwarf", 38, 0.004, 0.08, 2600, 3800, 0.1, 0.6, 0.15, 0.6),
    K("K 型橙矮星", "Orange dwarf", 18, 0.08, 0.6, 3900, 5200, 0.6, 0.9, 0.6, 0.9),
    G("G 型黄矮星", "Yellow dwarf", 12, 0.6, 1.6, 5300, 6000, 0.9, 1.1, 0.9, 1.15),
    F("F 型白黄星", "Yellow-white star", 8, 1.6, 5.0, 6000, 7500, 1.1, 1.5, 1.15, 1.6),
    A("A 型白星", "White star", 5, 5.0, 25.0, 7500, 10000, 1.5, 2.5, 1.6, 2.5),
    B("B 型蓝白巨星", "Blue giant", 2.5, 25.0, 3000.0, 10000, 30000, 2.5, 16, 2.5, 8),
    O("O 型蓝超巨星", "Blue supergiant", 0.6, 3000.0, 50000.0, 30000, 45000, 16, 60, 8, 15),
    RED_GIANT("红巨星", "Red giant", 4, 60.0, 1000.0, 3200, 4500, 0.8, 3, 15, 100),
    WHITE_DWARF("白矮星", "White dwarf", 5, 0.0005, 0.02, 8000, 25000, 0.5, 1.2, 0.008, 0.02),
    NEUTRON_STAR("中子星", "Neutron star", 1.5, 0.0001, 0.001, 600000, 1000000, 1.2, 2.2, 0.000015, 0.000015),
    BLACK_HOLE("黑洞", "Black hole", 0.4, 0.0001, 0.0001, 0, 0, 5, 30, 0.00002, 0.0001);

    private final String nameZh;
    private final String nameEn;
    private final double weight;
    private final double minLuminosity;
    private final double maxLuminosity;
    private final double minTemperature;
    private final double maxTemperature;
    private final double minMass;
    private final double maxMass;
    private final double minRadius;
    private final double maxRadius;

    StarClass(String nameZh, String nameEn, double weight, double minLuminosity, double maxLuminosity,
            double minTemperature, double maxTemperature, double minMass, double maxMass, double minRadius, double maxRadius) {
        this.nameZh = nameZh;
        this.nameEn = nameEn;
        this.weight = weight;
        this.minLuminosity = minLuminosity;
        this.maxLuminosity = maxLuminosity;
        this.minTemperature = minTemperature;
        this.maxTemperature = maxTemperature;
        this.minMass = minMass;
        this.maxMass = maxMass;
        this.minRadius = minRadius;
        this.maxRadius = maxRadius;
    }

    public String nameZh() {
        return this.nameZh;
    }

    public String nameEn() {
        return this.nameEn;
    }

    public double weight() {
        return this.weight;
    }

    public double minLuminosity() {
        return this.minLuminosity;
    }

    public double maxLuminosity() {
        return this.maxLuminosity;
    }

    public double minTemperature() {
        return this.minTemperature;
    }

    public double maxTemperature() {
        return this.maxTemperature;
    }

    public double minMass() {
        return this.minMass;
    }

    public double maxMass() {
        return this.maxMass;
    }

    public double minRadius() {
        return this.minRadius;
    }

    public double maxRadius() {
        return this.maxRadius;
    }

    /** 致密天体：中子星、黑洞。它们周围的行星几乎不受星光照射，辐射却很强。 */
    public boolean isCompact() {
        return this == NEUTRON_STAR || this == BLACK_HOLE;
    }

    /** 强辐射源：O、B 型星和中子星。 */
    public boolean isHighRadiation() {
        return this == O || this == B || this == NEUTRON_STAR;
    }
}
