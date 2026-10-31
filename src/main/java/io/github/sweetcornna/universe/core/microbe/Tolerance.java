package io.github.sweetcornna.universe.core.microbe;

import java.util.Set;

/**
 * 一个生物能扛住什么（ADR-0036 §五、§六）：基数温度，和四种不靠温度的耐受。改造星球时生态播种站拿它对星球的条件。
 * 数字照现实的纪录：
 *
 * <ul>
 *   <li>温度：生命的两头是 −15 °C（盐生钝顶浮球菌）和 122 °C（甲烷嗜高热菌 116 株），见 {@link MicrobeStrain}。</li>
 *   <li>酸：带质子泵的到 pH 0（嗜苦古菌 Picrophilus torridus 在 pH 0.06 还能长，最适 0.7，Schleper 等 1995）；
 *       不带的到 pH 4 上下（大肠杆菌最低约 4.4）。</li>
 *   <li>盐：带相容溶质的到氯化钠饱和，约 5.2 mol/L（盐沼盐杆菌）；不带的到 1 mol/L 上下（海水约 0.6）。</li>
 *   <li>电离辐射：带 DNA 修复的扛 5 kGy 不掉活性（耐辐射奇球菌），超嗜热古菌再带上它到 30 kGy（嗜辐射热球菌
 *       Thermococcus gammatolerans，Jolivet 等 2003）；不带的约 0.3 kGy 就死掉九成（大肠杆菌 O157:H7 的 D10，
 *       食品辐照的数据）。人的致死剂量约 5 Gy。</li>
 *   <li>紫外：带紫外防护色素（伪枝藻素、类菌孢素氨基酸）的扛得住稀薄大气、真空下的强紫外。拟色球藻在国际空间站外面
 *       模拟火星的条件下放了一年半还活着（BIOMEX，2014–2016）。</li>
 * </ul>
 *
 * <p>外加的抗冻蛋白把最低温度降 {@link #ANTIFREEZE_SHIFT}，外加的嗜热酶把最高温度升 {@link #THERMOSTABLE_SHIFT}，
 * 最适温度跟着往上挪到不低于新的中点。这两处比现实宽：现实里耐寒、耐热要一整套基因（冷休克蛋白、膜脂、酶），
 * 一个抗冻蛋白只让冰点降一两度，实验室进化把大肠杆菌的最高温度提几度就很难了；这里把一个基因当成一整套，
 * 不然编辑没有用处。
 *
 * @param minK         能长的最低温度（K）
 * @param optK         最适温度（K）
 * @param maxK         能长的最高温度（K）
 * @param minPH        能长的最低 pH
 * @param maxSaltMolar 能长的最高盐度（氯化钠，mol/L）
 * @param maxDoseGy    扛得住的电离辐射剂量（Gy）
 * @param uv           扛得住强紫外
 */
public record Tolerance(double minK, double optK, double maxK, double minPH, double maxSaltMolar, double maxDoseGy, boolean uv) {
    /** 外加抗冻蛋白，能长的最低温度降多少（K）。 */
    public static final double ANTIFREEZE_SHIFT = 15;
    /** 外加嗜热酶，能长的最高温度升多少（K）。 */
    public static final double THERMOSTABLE_SHIFT = 20;
    public static final double ACIDOPHILE_PH = 0;
    public static final double NEUTROPHILE_PH = 4;
    /** 氯化钠在 25 °C 的饱和浓度（mol/L）。 */
    public static final double SALT_SATURATION = 5.2;
    public static final double ORDINARY_SALT = 1;
    public static final double RADIORESISTANT_GY = 5_000;
    public static final double HYPERTHERMOPHILE_RADIORESISTANT_GY = 30_000;
    public static final double ORDINARY_GY = 300;

    /** 天生的基数温度，加上外来基因的效果。外来的抗冻蛋白、嗜热酶把范围往外推，天生就有的不再推。 */
    public static Tolerance of(MicrobeStrain strain, Set<Gene> all, Set<Gene> added) {
        double min = strain.minK();
        double max = strain.maxK();
        if (added.contains(Gene.ANTIFREEZE_PROTEIN) && !strain.has(Gene.ANTIFREEZE_PROTEIN)) {
            min = Math.max(MicrobeStrain.MIN_LIFE_K, min - ANTIFREEZE_SHIFT);
        }
        if (added.contains(Gene.THERMOSTABLE_ENZYMES) && !strain.has(Gene.THERMOSTABLE_ENZYMES)) {
            max = Math.min(MicrobeStrain.MAX_LIFE_K, max + THERMOSTABLE_SHIFT);
        }
        // 基数温度模型要最适温度高过中点
        double opt = Math.min(max - 1, Math.max(strain.optK(), (min + max) / 2 + 1));
        double dose = !all.contains(Gene.DNA_REPAIR) ? ORDINARY_GY
                : strain.optK() >= 360 && strain.morphology() == Morphology.ARCHAEON ? HYPERTHERMOPHILE_RADIORESISTANT_GY : RADIORESISTANT_GY;
        return new Tolerance(min, opt, max, all.contains(Gene.PROTON_PUMP) ? ACIDOPHILE_PH : NEUTROPHILE_PH,
                all.contains(Gene.COMPATIBLE_SOLUTES) ? SALT_SATURATION : ORDINARY_SALT, dose, all.contains(Gene.UV_SHIELD));
    }

    /** 这个温度下的生长速度是最适时的几成，0–1（基数温度模型）。 */
    public double growthFactor(double temperatureK) {
        return MicrobeStrain.cardinal(this.minK, this.optK, this.maxK, temperatureK);
    }

    public boolean grows(double temperatureK) {
        return this.growthFactor(temperatureK) > 0;
    }

    /** 热死：高过最高温度。冷过最低温度只是不长（冻住的细胞大多活着）。 */
    public boolean scalding(double temperatureK) {
        return temperatureK >= this.maxK;
    }

    /** 耐辐射（DNA 修复）：扛得住辐射星球的地表。 */
    public boolean radiation() {
        return this.maxDoseGy >= RADIORESISTANT_GY;
    }

    /** 耐酸、耐毒（质子泵）。 */
    public boolean acid() {
        return this.minPH <= ACIDOPHILE_PH;
    }

    /** 耐盐、耐旱（相容溶质）。 */
    public boolean salt() {
        return this.maxSaltMolar >= SALT_SATURATION;
    }
}
