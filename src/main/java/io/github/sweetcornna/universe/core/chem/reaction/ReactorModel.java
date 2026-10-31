package io.github.sweetcornna.universe.core.chem.reaction;

/**
 * 反应釜的速度和用电（ADR-0036 §四）。
 *
 * <ul>
 *   <li>单程转化率 = 平衡转化率 × (1 − e^(−Da))，Da 见 {@link Reaction}。低温平衡好但慢，高温快但平衡差，
 *       于是放热反应有一个产量最高的温度；吸热的重整没有这个取舍，靠镍催化剂烧结压住上限。
 *       不用催化剂的反应活性按 1，高温失活见 {@link Reaction#limitTemperature()}。</li>
 *   <li>每分钟做的批数 = 单程转化率 × {@link Reaction#baseBatches()}。</li>
 *   <li>用电（J/刻）= 底数 + 压缩（和 ln 压强成正比，等温压缩功就是这样）+ 保温（和设定温度与星球气温的差成正比）
 *       + 吸热反应的反应热。放热反应的热不变成电。</li>
 * </ul>
 *
 * <p>能量和燃料对得上：火力发电厂烧一个氢得 4700 刻 × 15 J = 70 500 J，氢的低热值 242 kJ/mol，
 * 所以每摩尔 1 kJ 折成 {@link #JOULES_PER_KJ_MOL} J。重整每批至少要 206.1 × 291.3 ≈ 60 000 J 的反应热，
 * 产出的氢和一氧化碳烧掉拿回的电不会多过甲烷的燃料值加上反应釜的用电，造不出永动机。
 */
public final class ReactorModel {
    /** 每摩尔 1 kJ 折成多少 J（游戏里的电）。 */
    public static final double JOULES_PER_KJ_MOL = 291.3;
    /** 用电的底数（J/刻）。化工厂 36，量子化工厂 108。 */
    public static final double BASE_POWER = 24;
    /** 压缩：ln(压强/1 bar) 每 1 用多少 J/刻；300 bar 约 57。 */
    public static final double COMPRESSION_POWER = 10;
    /** 保温：设定温度和星球气温每差 1 K 用多少 J/刻；差 900 K 约 54。 */
    public static final double HEAT_LOSS_POWER = 0.06;
    /** 一分钟多少刻。 */
    static final double TICKS_PER_MINUTE = 1200;
    /** 找最佳温度时的步长（K）。 */
    static final double SEARCH_STEP = 1;

    private ReactorModel() {
    }

    /** 达姆科勒数：反应在釜里待的这段时间里能走多远。 */
    public static double damkohler(Reaction reaction, double temperature, double pressure, boolean catalyzed) {
        Catalyst catalyst = reaction.catalyst();
        double activity = catalyst == null ? 1 : catalyzed ? catalyst.activity(temperature) : Catalyst.UNCATALYZED;
        return reaction.prefactor() * Math.exp(-reaction.activation() / (Thermo.R * temperature)) * activity
                * reaction.limitFactor(temperature) * pressure / (pressure + reaction.pressureHalf());
    }

    /** 单程转化率，0–1。 */
    public static double singlePass(Reaction reaction, double temperature, double pressure, boolean catalyzed) {
        return Thermo.equilibriumConversion(reaction, temperature, pressure)
                * -Math.expm1(-damkohler(reaction, temperature, pressure, catalyzed));
    }

    /**
     * 这个工况下跑出来的数。
     *
     * @param catalyzed 催化剂格里放了对的催化剂；不用催化剂的反应不看它
     * @param ambient   星球的平均气温（K）
     */
    public static ReactorResult evaluate(Reaction reaction, ReactorSettings settings, boolean catalyzed, double ambient) {
        double t = settings.temperature();
        double p = settings.pressure();
        boolean full = catalyzed || reaction.catalyst() == null;
        double equilibrium = Thermo.equilibriumConversion(reaction, t, p);
        double singlePass = equilibrium * -Math.expm1(-damkohler(reaction, t, p, full));
        double batches = singlePass * reaction.baseBatches();
        double product = batches * reaction.mainProduct().count();
        return new ReactorResult(equilibrium, singlePass, batches, product, power(reaction, settings, batches, ambient), full);
    }

    /**
     * 用电（J/刻）。
     *
     * @param batchesPerMinute 每分钟做几批（吸热反应按它算反应热）
     */
    public static double power(Reaction reaction, ReactorSettings settings, double batchesPerMinute, double ambient) {
        double compression = COMPRESSION_POWER * Math.log(settings.pressure() / ReactorSettings.MIN_PRESSURE);
        double heatLoss = HEAT_LOSS_POWER * Math.abs(settings.temperature() - ambient);
        double reactionHeat = Math.max(0, reactionHeat(reaction, settings.temperature())) * batchesPerMinute / TICKS_PER_MINUTE;
        return BASE_POWER + compression + heatLoss + reactionHeat;
    }

    /** 一批在 298 K 的反应热折成的电（J），正的吸热、负的放热。 */
    public static double reactionHeat(Reaction reaction) {
        return reaction.enthalpy() / 1000 * JOULES_PER_KJ_MOL;
    }

    /** 一批在反应温度下的反应热折成的电（J）：重整在 850 °C 约 227 kJ/mol，比 298 K 多一成。 */
    public static double reactionHeat(Reaction reaction, double temperature) {
        return reaction.enthalpyAt(temperature) / 1000 * JOULES_PER_KJ_MOL;
    }

    /** 这个压强下、放了催化剂时主产物最多的温度（K），按 {@link #SEARCH_STEP} 找。 */
    public static double bestTemperature(Reaction reaction, double pressure) {
        double best = ReactorSettings.MIN_TEMPERATURE;
        double bestRate = -1;
        for (double t = ReactorSettings.MIN_TEMPERATURE; t <= ReactorSettings.MAX_TEMPERATURE; t += SEARCH_STEP) {
            double rate = singlePass(reaction, t, pressure, true);
            if (rate > bestRate) {
                bestRate = rate;
                best = t;
            }
        }
        return best;
    }
}
