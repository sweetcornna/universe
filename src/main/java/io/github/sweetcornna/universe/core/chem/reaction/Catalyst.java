package io.github.sweetcornna.universe.core.chem.reaction;

/**
 * 反应釜的催化剂（ADR-0036 §四、ADR-0037）。放在催化剂格里，每做一批磨损一点，做满 {@link #batches()} 批报废。
 * 太热会烧结：超过 {@link #sinterTemperature()} 活性按 {@link #SINTER_WIDTH} 指数下降，所以吸热的重整反应也有一个产量最高的温度。
 * 顺序和名字是存档格式（反应釜按名字记磨损的是哪种），只往后加。
 *
 * @param item              物品的注册名
 * @param sinterTemperature 开始烧结的温度（K）
 * @param batches           一份催化剂能做几批
 */
public enum Catalyst {
    /** 铁：磁铁矿用氢还原成的多孔铁，合成氨、费托合成、水煤气变换、布杜阿尔反应用。现实的熔铁催化剂超过 550 °C 很快失活。 */
    IRON("universe:iron_catalyst", "铁催化剂", "Iron Catalyst", 823, 256),
    /** 五氧化二钒：接触法用，现实里 600 °C 往上开始烧结。 */
    VANADIUM_PENTOXIDE("universe:vanadium_catalyst", "五氧化二钒催化剂", "Vanadium Pentoxide Catalyst", 893, 256),
    /**
     * 镍：萨巴蒂尔反应、甲烷化、蒸汽重整、干重整、氨分解用。现实的转化炉管出口在 850–900 °C，镍在更高的温度下烧结；
     * 这里定在 860 °C，让重整的最高产量落在现实的工况里。
     */
    NICKEL("universe:nickel_catalyst", "镍催化剂", "Nickel Catalyst", 1133, 256),
    /** 铜锌（Cu/ZnO/Al₂O₃）：甲醇合成用。铜在 300 °C 往上很快烧结，现实的甲醇塔在 220–280 °C。 */
    COPPER_ZINC("universe:copper_zinc_catalyst", "铜锌催化剂", "Copper-Zinc Catalyst", 573, 256),
    /** 铂铑网：氨氧化、安德卢梭法、BMA 法用。铂在 1300 °C 往上挥发得快。 */
    PLATINUM_RHODIUM("universe:platinum_rhodium_gauze", "铂铑网", "Platinum-Rhodium Gauze", 1573, 512),
    /** ZSM-5 沸石：甲醇制烯烃用，500 °C 往上脱铝、积碳。 */
    ZEOLITE("universe:zeolite_catalyst", "ZSM-5 沸石", "ZSM-5 Zeolite", 793, 256),
    /** 齐格勒–纳塔（TiCl₄/MgCl₂）：乙烯低压聚合用，120 °C 往上失活。 */
    ZIEGLER_NATTA("universe:ziegler_natta_catalyst", "齐格勒–纳塔催化剂", "Ziegler–Natta Catalyst", 393, 256),
    /** 氯化铜：迪肯法用，450 °C 往上挥发。 */
    COPPER_CHLORIDE("universe:copper_chloride_catalyst", "氯化铜催化剂", "Copper Chloride Catalyst", 723, 256);

    /** 没放催化剂时反应快慢的倍数：现实里这些反应不加催化剂几乎不走。 */
    public static final double UNCATALYZED = 0.02;
    /** 超过烧结温度后，每高这么多 K 活性降到 1/e。 */
    public static final double SINTER_WIDTH = 25;

    private final String item;
    private final String nameZh;
    private final String nameEn;
    private final double sinterTemperature;
    private final int batches;

    Catalyst(String item, String nameZh, String nameEn, double sinterTemperature, int batches) {
        this.item = item;
        this.nameZh = nameZh;
        this.nameEn = nameEn;
        this.sinterTemperature = sinterTemperature;
        this.batches = batches;
    }

    public String item() {
        return this.item;
    }

    public String nameZh() {
        return this.nameZh;
    }

    public String nameEn() {
        return this.nameEn;
    }

    public double sinterTemperature() {
        return this.sinterTemperature;
    }

    public int batches() {
        return this.batches;
    }

    /** 能不能催化这条反应。 */
    public boolean accepts(Reaction reaction) {
        return reaction.catalyst() == this;
    }

    /** 这个温度下的活性，0–1：烧结温度以下为 1。 */
    public double activity(double temperature) {
        return temperature <= this.sinterTemperature ? 1 : Math.exp(-(temperature - this.sinterTemperature) / SINTER_WIDTH);
    }

    /** 按物品找，不是催化剂为 {@code null}。 */
    public static Catalyst byItem(String item) {
        for (Catalyst catalyst : values()) {
            if (catalyst.item.equals(item)) {
                return catalyst;
            }
        }
        return null;
    }
}
