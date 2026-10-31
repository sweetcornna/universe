package io.github.sweetcornna.universe.core.terraform;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.github.sweetcornna.universe.core.chem.Gas;
import io.github.sweetcornna.universe.core.microbe.Gene;

/**
 * 一颗星球的改造状态（ADR-0036 §六），存在全局存档里。星球本来的数据不动，现用的环境由
 * {@link Terraform#effective} 按"本来的 + 这里"算出来。不可变，改一样就换一份。
 *
 * <p>气体按"单位"记，一个单位就是一个气体物品（一摩尔）；多少单位合一个大气压见 {@link Terraform#capacity}。
 * 加进去为正，取走为负，但不会比星球本来的那一份取得更多（由 {@link Terraform#addGas} 管）。
 */
public final class TerraformState {
    /** 一颗星球最多记几种先锋生物（每次播种的基因组合不同就多记一种）。 */
    public static final int MAX_PIONEERS = 8;

    public static final TerraformState EMPTY = new TerraformState(new EnumMap<>(Gas.class), 0, 0, 0, List.of(), 0, 0, 0);

    private final Map<Gas, Double> gases;
    private final double biomass;
    private final int mirrors;
    private final int shades;
    private final List<Set<Gene>> pioneers;
    private final double irrigation;
    private final long lastUpdate;
    private final int announced;

    private TerraformState(EnumMap<Gas, Double> gases, double biomass, int mirrors, int shades, List<Set<Gene>> pioneers,
            double irrigation, long lastUpdate, int announced) {
        gases.values().removeIf(amount -> amount == 0);
        this.gases = Collections.unmodifiableMap(gases);
        this.biomass = Math.max(0, biomass);
        this.mirrors = Math.max(0, mirrors);
        this.shades = Math.max(0, shades);
        this.pioneers = List.copyOf(pioneers);
        this.irrigation = Math.clamp(irrigation, 0, 1);
        this.lastUpdate = lastUpdate;
        this.announced = announced;
    }

    /**
     * 从存档里读回来。气体按名字（{@link Gas#name()}），基因按名字（{@link Gene#name()}），认不得的名字丢掉。
     */
    public static TerraformState fromStored(Map<String, Double> gases, double biomass, int mirrors, int shades,
            List<List<String>> pioneers, double irrigation, long lastUpdate, int announced) {
        EnumMap<Gas, Double> map = new EnumMap<>(Gas.class);
        gases.forEach((name, amount) -> {
            Gas gas = Gas.byName(name);
            if (gas != null && amount != null && Double.isFinite(amount)) {
                map.merge(gas, amount, Double::sum);
            }
        });
        List<Set<Gene>> sets = new ArrayList<>();
        for (List<String> names : pioneers) {
            EnumSet<Gene> genes = EnumSet.noneOf(Gene.class);
            for (String name : names) {
                Gene gene = Gene.byName(name);
                if (gene != null) {
                    genes.add(gene);
                }
            }
            if (!genes.isEmpty() && !sets.contains(genes) && sets.size() < MAX_PIONEERS) {
                sets.add(Collections.unmodifiableSet(genes));
            }
        }
        return new TerraformState(map, Double.isFinite(biomass) ? biomass : 0, mirrors, shades, sets, irrigation, lastUpdate,
                announced);
    }

    /** 存盘用：气体名字到单位数，只有不为 0 的。 */
    public Map<String, Double> storedGases() {
        Map<String, Double> map = new LinkedHashMap<>();
        this.gases.forEach((gas, amount) -> map.put(gas.name(), amount));
        return map;
    }

    /** 存盘用：每种先锋生物的基因名字。 */
    public List<List<String>> storedPioneers() {
        List<List<String>> list = new ArrayList<>();
        for (Set<Gene> genes : this.pioneers) {
            list.add(genes.stream().map(Gene::name).toList());
        }
        return list;
    }

    /** 气体、反射镜、遮阳板都没动过：现用的环境就是星球本来的。生物量单独不改变环境。 */
    public boolean isEmpty() {
        return this.gases.isEmpty() && this.mirrors == 0 && this.shades == 0;
    }

    /** 这种气体加进去（正）或取走（负）的单位数。 */
    public double delta(Gas gas) {
        return this.gases.getOrDefault(gas, 0.0);
    }

    /** 只读的表，只有不为 0 的。 */
    public Map<Gas, Double> deltas() {
        return this.gases;
    }

    /** 生物量，0 到环境定的上限（最多 1，1 是整颗星球长满）。 */
    public double biomass() {
        return this.biomass;
    }

    public int mirrors() {
        return this.mirrors;
    }

    public int shades() {
        return this.shades;
    }

    /** 播下去活了的先锋生物，每种是一组基因。 */
    public List<Set<Gene>> pioneers() {
        return this.pioneers;
    }

    /** 没有天然水源的星球上，播种站浇的水能顾到多大一片，0–1（ADR-0036 §六）。 */
    public double irrigation() {
        return this.irrigation;
    }

    /** 上次推进到哪一刻（游戏时间）；由游戏那一层记，核心层不看。 */
    public long lastUpdate() {
        return this.lastUpdate;
    }

    /** 已经全服提示过的阶段（{@link Terraform.Stage} 的位），免得来回越过门槛时反复提示。 */
    public int announced() {
        return this.announced;
    }

    /** 直接改一种气体的差值，不查星球本来有多少；一般用 {@link Terraform#addGas}。 */
    public TerraformState withDelta(Gas gas, double delta) {
        EnumMap<Gas, Double> map = this.copyGases();
        map.put(gas, delta);
        return new TerraformState(map, this.biomass, this.mirrors, this.shades, this.pioneers, this.irrigation, this.lastUpdate,
                this.announced);
    }

    public TerraformState withBiomass(double biomass) {
        return new TerraformState(this.copyGases(), biomass, this.mirrors, this.shades, this.pioneers, this.irrigation,
                this.lastUpdate, this.announced);
    }

    /** 太空里那颗星球旁边的轨道反射镜有几面（游戏那一层数出来）。 */
    public TerraformState withMirrors(int mirrors) {
        return new TerraformState(this.copyGases(), this.biomass, mirrors, this.shades, this.pioneers, this.irrigation,
                this.lastUpdate, this.announced);
    }

    /** 切成遮阳板的有几面。 */
    public TerraformState withShades(int shades) {
        return new TerraformState(this.copyGases(), this.biomass, this.mirrors, shades, this.pioneers, this.irrigation,
                this.lastUpdate, this.announced);
    }

    /** 记下一种先锋生物；已经有了、或者记满了就不变。 */
    public TerraformState withPioneer(Set<Gene> genes) {
        EnumSet<Gene> copy = genes.isEmpty() ? EnumSet.noneOf(Gene.class) : EnumSet.copyOf(genes);
        if (copy.isEmpty() || this.pioneers.contains(copy) || this.pioneers.size() >= MAX_PIONEERS) {
            return this;
        }
        List<Set<Gene>> list = new ArrayList<>(this.pioneers);
        list.add(Collections.unmodifiableSet(copy));
        return new TerraformState(this.copyGases(), this.biomass, this.mirrors, this.shades, list, this.irrigation,
                this.lastUpdate, this.announced);
    }

    public TerraformState withIrrigation(double irrigation) {
        return new TerraformState(this.copyGases(), this.biomass, this.mirrors, this.shades, this.pioneers, irrigation,
                this.lastUpdate, this.announced);
    }

    public TerraformState withLastUpdate(long lastUpdate) {
        return new TerraformState(this.copyGases(), this.biomass, this.mirrors, this.shades, this.pioneers, this.irrigation,
                lastUpdate, this.announced);
    }

    public TerraformState withAnnounced(int announced) {
        return new TerraformState(this.copyGases(), this.biomass, this.mirrors, this.shades, this.pioneers, this.irrigation,
                this.lastUpdate, announced);
    }

    private EnumMap<Gas, Double> copyGases() {
        EnumMap<Gas, Double> map = new EnumMap<>(Gas.class);
        map.putAll(this.gases);
        return map;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof TerraformState state && state.gases.equals(this.gases) && state.biomass == this.biomass
                && state.mirrors == this.mirrors && state.shades == this.shades && state.pioneers.equals(this.pioneers)
                && state.irrigation == this.irrigation && state.lastUpdate == this.lastUpdate && state.announced == this.announced;
    }

    @Override
    public int hashCode() {
        return this.gases.hashCode() * 31 + Double.hashCode(this.biomass) * 17 + this.mirrors * 7 + this.shades;
    }

    @Override
    public String toString() {
        return "TerraformState[gases=" + this.gases + ", biomass=" + this.biomass + ", mirrors=" + this.mirrors + ", shades="
                + this.shades + ", pioneers=" + this.pioneers + "]";
    }
}
