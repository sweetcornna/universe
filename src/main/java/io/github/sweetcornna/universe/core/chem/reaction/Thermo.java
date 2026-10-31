package io.github.sweetcornna.universe.core.chem.reaction;

/**
 * 化学平衡（ADR-0036 §四）：K(T) = exp(−(ΔH(T) − TΔS(T))/RT)，ΔH、ΔS 按热容随温度修正（{@link Shomate}），
 * 按化学计量比进料，用压强和摩尔分数解出平衡时的反应进度。标准压强取 1 bar，只算气相的物质，按理想气体算。
 */
public final class Thermo {
    /** 摩尔气体常数（J/(mol·K)）。 */
    public static final double R = 8.314462618;
    /** 标准压强（bar）。 */
    public static final double STANDARD_PRESSURE = 1;
    private static final double EDGE = 1e-12;
    private static final int ITERATIONS = 100;

    private Thermo() {
    }

    /** 平衡常数的自然对数。 */
    public static double lnK(Reaction reaction, double temperature) {
        return -(reaction.enthalpyAt(temperature) - temperature * reaction.entropyAt(temperature)) / (R * temperature);
    }

    public static double equilibriumConstant(Reaction reaction, double temperature) {
        return Math.exp(lnK(reaction, temperature));
    }

    /**
     * 平衡转化率：按化学计量比进料，反应走到平衡时走完了几成，0–1。不可逆的反应为 1。
     *
     * @param pressure 总压（bar）
     */
    public static double equilibriumConversion(Reaction reaction, double temperature, double pressure) {
        if (!reaction.reversible()) {
            return 1;
        }
        double target = lnK(reaction, temperature);
        double lo = EDGE;
        double hi = 1 - EDGE;
        // 反应商随进度单调增大：进度 0 时没有产物，趋于负无穷；进度 1 时没有反应物，趋于正无穷
        for (int i = 0; i < ITERATIONS; i++) {
            double mid = (lo + hi) / 2;
            if (lnQ(reaction, mid, pressure) < target) {
                lo = mid;
            } else {
                hi = mid;
            }
        }
        return (lo + hi) / 2;
    }

    /** 平衡时某种物质在气相里的摩尔分数（检查用）。 */
    public static double equilibriumFraction(Reaction reaction, Species species, double temperature, double pressure) {
        double extent = equilibriumConversion(reaction, temperature, pressure);
        double total = gasMoles(reaction, extent);
        double moles = 0;
        for (Term term : reaction.reactants()) {
            if (term.species() == species) {
                moles += term.moles() * (1 - extent);
            }
        }
        for (Term term : reaction.products()) {
            if (term.species() == species) {
                moles += term.moles() * extent;
            }
        }
        return total > 0 ? moles / total : 0;
    }

    /** 反应进度为 {@code extent} 时反应商的自然对数。 */
    static double lnQ(Reaction reaction, double extent, double pressure) {
        double total = gasMoles(reaction, extent);
        double sum = 0;
        for (Term term : reaction.products()) {
            if (term.species().gas()) {
                sum += term.moles() * Math.log(term.moles() * extent / total * pressure / STANDARD_PRESSURE);
            }
        }
        for (Term term : reaction.reactants()) {
            if (term.species().gas()) {
                sum -= term.moles() * Math.log(term.moles() * (1 - extent) / total * pressure / STANDARD_PRESSURE);
            }
        }
        return sum;
    }

    private static double gasMoles(Reaction reaction, double extent) {
        double total = 0;
        for (Term term : reaction.reactants()) {
            if (term.species().gas()) {
                total += term.moles() * (1 - extent);
            }
        }
        for (Term term : reaction.products()) {
            if (term.species().gas()) {
                total += term.moles() * extent;
            }
        }
        return total;
    }
}
