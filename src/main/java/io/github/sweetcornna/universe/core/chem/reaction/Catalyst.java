package io.github.sweetcornna.universe.core.chem.reaction;

/**
 * 反应釜的催化剂（ADR-0036 §四）。放在催化剂格里，每做一批磨损一点，做满 {@link #batches()} 批报废。
 * 太热会烧结：超过 {@link #sinterTemperature()} 活性按 {@link #SINTER_WIDTH} 指数下降，所以吸热的重整反应也有一个产量最高的温度。
 *
 * @param item              物品的注册名
 * @param sinterTemperature 开始烧结的温度（K）
 * @param batches           一份催化剂能做几批
 */
public enum Catalyst {
    /** 铁：磁铁矿用氢还原成的多孔铁，合成氨和费托合成用。现实的熔铁催化剂超过 550 °C 很快失活。 */
    IRON("universe:iron_catalyst", "铁催化剂", "Iron Catalyst", 823, 256),
    /** 五氧化二钒：接触法用，现实里 600 °C 往上开始烧结。 */
    VANADIUM_PENTOXIDE("universe:vanadium_catalyst", "五氧化二钒催化剂", "Vanadium Pentoxide Catalyst", 893, 256),
    /**
     * 镍：萨巴蒂尔反应和甲烷蒸汽重整用。现实的转化炉管出口在 850–900 °C，镍在更高的温度下烧结；
     * 这里定在 860 °C，让重整的最高产量落在现实的工况里。
     */
    NICKEL("universe:nickel_catalyst", "镍催化剂", "Nickel Catalyst", 1133, 256);

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
}
