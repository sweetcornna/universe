package io.github.sweetcornna.universe.core.chem.reaction;

import java.util.EnumMap;
import java.util.Map;

/**
 * 每种物质的焓和熵随温度变（ADR-0037）：有 NIST Shomate 拟合的用 {@link Shomate}；没有的（凝聚相、部分有机物）
 * 用 298.15 K 的标准生成焓、标准熵和热容，热容按常数外推：H(T) = ΔfH° + Cp(T − 298.15)，S(T) = S° + Cp ln(T/298.15)。
 * 这些物质多半只出现在不可逆的反应里，只用来显示反应焓，外推的误差不影响平衡。
 */
public final class SpeciesThermo {
    /** 298.15 K 的标准数据：生成焓（kJ/mol）、熵和热容（J/(mol·K)）。 */
    record Standard(double enthalpy, double entropy, double heatCapacity) {
    }

    static final double T0 = 298.15;

    private static final Map<Species, Standard> STANDARD = new EnumMap<>(Species.class);

    static {
        // NIST 化学网络手册（凝聚相、没有 Shomate 拟合的气体）
        STANDARD.put(Species.SULFURIC_ACID, new Standard(-814.0, 156.9, 138.9));
        STANDARD.put(Species.NITRIC_ACID, new Standard(-174.1, 155.6, 109.9));
        STANDARD.put(Species.AMMONIUM_NITRATE, new Standard(-365.6, 151.1, 139.3));
        STANDARD.put(Species.UREA, new Standard(-333.1, 104.6, 93.1));
        STANDARD.put(Species.METHANOL, new Standard(-201.0, 239.9, 44.1));
        STANDARD.put(Species.ETHYLENE, new Standard(52.4, 219.3, 42.9));
        STANDARD.put(Species.NITRIC_OXIDE, new Standard(90.29, 210.76, 29.86));
        STANDARD.put(Species.NITROGEN_DIOXIDE, new Standard(33.10, 240.04, 36.97));
        STANDARD.put(Species.HYDROGEN_CYANIDE, new Standard(135.1, 201.8, 35.9));
        STANDARD.put(Species.HYDROGEN_CHLORIDE, new Standard(-92.31, 186.9, 29.12));
        STANDARD.put(Species.CHLORINE, new Standard(0, 223.08, 33.95));
        STANDARD.put(Species.SULFUR, new Standard(0, 32.07, 22.64));
        STANDARD.put(Species.CARBON, new Standard(0, 5.74, 8.53));
        STANDARD.put(Species.COAL, new Standard(0, 5.74, 8.53));
        // 长链正构烷烃（液）每节 CH₂ 的增量：ΔfH −25.6 kJ/mol、S 32.5、Cp 29.1（己烷到庚烷），一个精炼油两节
        STANDARD.put(Species.HYDROCARBON, new Standard(-51.2, 65.0, 58.2));
        // 聚乙烯一节 C₂H₄：乙烯（气）聚合成固体放热 93.6 kJ/mol、熵减 174 J/(mol·K)
        STANDARD.put(Species.POLYETHYLENE, new Standard(52.4 - 93.6, 219.3 - 174.0, 46.0));
    }

    private SpeciesThermo() {
    }

    /** 有没有这种物质的数据。 */
    public static boolean covers(Species species) {
        return Shomate.covers(species) || STANDARD.containsKey(species);
    }

    /** 焓（J/mol，以单质为零点）。 */
    public static double enthalpy(Species species, double temperature) {
        if (Shomate.covers(species)) {
            return Shomate.enthalpy(species, temperature);
        }
        Standard s = standard(species);
        return 1000 * s.enthalpy() + s.heatCapacity() * (temperature - T0);
    }

    /** 1 bar 下的熵（J/(mol·K)）。 */
    public static double entropy(Species species, double temperature) {
        if (Shomate.covers(species)) {
            return Shomate.entropy(species, temperature);
        }
        Standard s = standard(species);
        return s.entropy() + s.heatCapacity() * Math.log(temperature / T0);
    }

    private static Standard standard(Species species) {
        Standard s = STANDARD.get(species);
        if (s == null) {
            throw new IllegalArgumentException("No thermodynamic data for " + species);
        }
        return s;
    }
}
