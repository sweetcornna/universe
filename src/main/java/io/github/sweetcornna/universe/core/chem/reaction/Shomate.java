package io.github.sweetcornna.universe.core.chem.reaction;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 气相物质的热容按温度变化：NIST 化学网络手册的 Shomate 方程（数据出自 Chase 1998，NIST-JANAF 热化学表第四版）。
 * t = T / 1000，
 * <ul>
 *   <li>Cp = A + Bt + Ct² + Dt³ + E/t²（J/(mol·K)）</li>
 *   <li>H = At + Bt²/2 + Ct³/3 + Dt⁴/4 − E/t + F（kJ/mol，以单质为零点，298.15 K 时等于标准生成焓）</li>
 *   <li>S = A ln t + Bt + Ct²/2 + Dt³/3 − E/(2t²) + G（J/(mol·K)）</li>
 * </ul>
 * 用它算的反应焓和反应熵随温度变，合成氨在 450 °C 的平衡常数和手册对得上（不修正时偏大约四倍）。
 * 每种物质有一到两段，按温度挑；超出表的范围就用最近的一段外推。水蒸气只有 500 K 以上的一段，300–500 K 外推，误差在 1 kJ/mol 以内。
 */
public final class Shomate {
    /** 一段系数。 */
    record Piece(double maxTemperature, double a, double b, double c, double d, double e, double f, double g) {
    }

    private static final Map<Species, List<Piece>> TABLE = new EnumMap<>(Species.class);

    static {
        TABLE.put(Species.NITROGEN, List.of(
                new Piece(500, 28.98641, 1.853978, -9.647459, 16.63537, 0.000117, -8.671914, 226.4168),
                new Piece(2000, 19.50583, 19.88705, -8.598535, 1.369784, 0.527601, -4.935202, 212.3900)));
        TABLE.put(Species.HYDROGEN, List.of(
                new Piece(1000, 33.066178, -11.363417, 11.432816, -2.772874, -0.158558, -9.980797, 172.707974),
                new Piece(2500, 18.563083, 12.257357, -2.859786, 0.268238, 1.977990, -1.147438, 156.288133)));
        TABLE.put(Species.OXYGEN, List.of(
                new Piece(700, 31.32234, -20.23531, 57.86644, -36.50624, -0.007374, -8.903471, 246.7945),
                new Piece(2000, 30.03235, 8.772972, -3.988133, 0.788313, -0.741599, -11.32468, 236.1663)));
        TABLE.put(Species.AMMONIA, List.of(
                new Piece(1400, 19.99563, 49.77119, -15.37599, 1.921168, 0.189174, -53.30667, 203.8591)));
        TABLE.put(Species.SULFUR_DIOXIDE, List.of(
                new Piece(1200, 21.43049, 74.35094, -57.75217, 16.35534, 0.086731, -305.7688, 254.8872)));
        TABLE.put(Species.SULFUR_TRIOXIDE, List.of(
                new Piece(1200, 24.02503, 119.4607, -94.38686, 26.96237, -0.117517, -407.8526, 253.5186)));
        TABLE.put(Species.CARBON_DIOXIDE, List.of(
                new Piece(1200, 24.99735, 55.18696, -33.69137, 7.948387, -0.136638, -403.6075, 228.2431)));
        TABLE.put(Species.METHANE, List.of(
                new Piece(1300, -0.703029, 108.4773, -42.52157, 5.862788, 0.678565, -76.84376, 158.7163)));
        TABLE.put(Species.WATER, List.of(
                new Piece(1700, 30.09200, 6.832514, 6.793435, -2.534480, 0.082139, -250.8810, 223.3967)));
        TABLE.put(Species.CARBON_MONOXIDE, List.of(
                new Piece(1300, 25.56759, 6.096130, 4.054656, -2.671301, 0.131021, -118.0089, 227.3665)));
    }

    private Shomate() {
    }

    /** 有没有这种物质的数据。 */
    public static boolean covers(Species species) {
        return TABLE.containsKey(species);
    }

    /** 焓（J/mol，以单质为零点）。 */
    public static double enthalpy(Species species, double temperature) {
        Piece p = piece(species, temperature);
        double t = temperature / 1000;
        return 1000 * (p.a * t + p.b * t * t / 2 + p.c * t * t * t / 3 + p.d * t * t * t * t / 4 - p.e / t + p.f);
    }

    /** 1 bar 下的熵（J/(mol·K)）。 */
    public static double entropy(Species species, double temperature) {
        Piece p = piece(species, temperature);
        double t = temperature / 1000;
        return p.a * Math.log(t) + p.b * t + p.c * t * t / 2 + p.d * t * t * t / 3 - p.e / (2 * t * t) + p.g;
    }

    private static Piece piece(Species species, double temperature) {
        List<Piece> pieces = TABLE.get(species);
        if (pieces == null) {
            throw new IllegalArgumentException("No Shomate data for " + species);
        }
        for (Piece piece : pieces) {
            if (temperature <= piece.maxTemperature) {
                return piece;
            }
        }
        return pieces.getLast();
    }
}
