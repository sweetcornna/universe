package io.github.sweetcornna.universe.core.balance;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import io.github.sweetcornna.universe.core.dyson.DysonGrid;
import io.github.sweetcornna.universe.core.dyson.DysonLayer;
import io.github.sweetcornna.universe.core.dyson.DysonSphere;
import io.github.sweetcornna.universe.core.dyson.RayReceiver;
import io.github.sweetcornna.universe.core.factory.Throughput;
import io.github.sweetcornna.universe.core.factory.Veins;
import io.github.sweetcornna.universe.core.research.Tech;
import io.github.sweetcornna.universe.core.research.TechTree;
import io.github.sweetcornna.universe.core.trade.ValueGraph;

/**
 * 数值平衡的进度模型（ADR-0018）。
 *
 * <p>本模组的产能卡在采矿上：一个矿脉节点 40 秒才出一个矿，设备和研究站多造几台就够，矿不够只能多开矿。所以模型把每样
 * 东西折算成"几个矿"，每档矩阵假定一座参考工厂（{@link Stage}），研究一个科技的时间 = 它要的矿 ÷ 工厂每分钟挖的矿，
 * 研究站不够时按研究站算。再按 ADR-0018 定的里程碑分段，看每段游戏时间里研究占多少：占得太少，研究形同虚设；
 * 占得太多，玩家只能干等。
 */
public final class Progression {
    private static final String NS = "universe:";
    /** 折算的基准：一台圈住 8 个节点的采矿机，一分钟 12 个矿。 */
    public static final double MINER_ORE_PER_MINUTE = 8 * 1200.0 / Throughput.MINER_TICKS_PER_ITEM;
    /** 研究站满电时每分钟几点。 */
    public static final double LAB_POINTS_PER_MINUTE = 1200.0 / Throughput.LAB_TICKS_PER_POINT;
    /** 分馏塔的工时记成一样虚拟的原料，每个重氢配一份。 */
    static final String FRACTIONATOR = NS + "fractionator_time";
    /** 射线接收站的工时，每个临界光子配一份。 */
    static final String RECEIVER = NS + "receiver_time";
    /** 一个临界光子的电要几张帆发：一张帆在寿命里发的电都给接收站（恒星光度系数按 1）。 */
    public static final double SAILS_PER_PHOTON = RayReceiver.PHOTON_ENERGY / (DysonSphere.SAIL_POWER * DysonSphere.SAIL_LIFETIME);

    /**
     * 不是采矿机挖的原料折合几个矿：一台设备一分钟的产量和一台采矿机比。原油按涌泉的平均产量，接收站按光子模式满功率、
     * 不带透镜。别的没有设备配方的东西都按 1 个矿算。
     */
    public static final Map<String, Double> EXTRACTED = Map.of(
            NS + "crude_oil", MINER_ORE_PER_MINUTE / ((Veins.MIN_SEEP_RATE + Veins.MAX_SEEP_RATE) / 2 * 60),
            NS + "water", MINER_ORE_PER_MINUTE / (1200.0 / Throughput.PUMP_TICKS_PER_ITEM),
            RECEIVER, MINER_ORE_PER_MINUTE / (RayReceiver.PHOTON_DRAW_MAX * 1200 / RayReceiver.PHOTON_ENERGY),
            FRACTIONATOR, MINER_ORE_PER_MINUTE / (Throughput.FRACTIONATOR_PER_TICK * Throughput.FRACTIONATOR_CHANCE * 1200));

    private static final String BLUE = TechTree.MATRICES.get(0);
    private static final String RED = TechTree.MATRICES.get(1);
    private static final String YELLOW = TechTree.MATRICES.get(2);
    private static final String PURPLE = TechTree.MATRICES.get(3);
    private static final String GREEN = TechTree.MATRICES.get(4);
    private static final String WHITE = TechTree.MATRICES.get(5);

    /**
     * 一档矩阵的参考工厂。
     *
     * @param matrices     这一档的科技每点要的矩阵
     * @param orePerMinute 每分钟挖的矿，按 {@link #MINER_ORE_PER_MINUTE} 折算
     * @param labs         研究站台数
     */
    public record Stage(List<String> matrices, double orePerMinute, int labs) {
        public Stage {
            matrices = List.copyOf(matrices);
        }

        /** 每分钟最多研究几点：矿和研究站哪个先不够。 */
        double pointsPerMinute(double orePerPoint) {
            return Math.min(this.labs * LAB_POINTS_PER_MINUTE, this.orePerMinute / orePerPoint);
        }
    }

    /** 参考工厂，越往后矿场越大。大型采矿机半径多一圈、速度翻倍，一台按四台采矿机算。 */
    public static final List<Stage> STAGES = List.of(
            // 蓝：4 台采矿机
            new Stage(List.of(BLUE), 4 * MINER_ORE_PER_MINUTE, 4),
            // 蓝红：8 台，原油另算
            new Stage(List.of(BLUE, RED), 8 * MINER_ORE_PER_MINUTE, 6),
            // 蓝红黄：钛要去别的星球挖、开飞船运回来，矿场还是那么大
            new Stage(List.of(BLUE, RED, YELLOW), 8 * MINER_ORE_PER_MINUTE, 8),
            // 蓝红黄紫：有了行星物流，16 台采矿机加 3 台大型采矿机
            new Stage(List.of(BLUE, RED, YELLOW, PURPLE), 28 * MINER_ORE_PER_MINUTE, 12),
            // 五种：8 台加 10 台大型
            new Stage(List.of(BLUE, RED, YELLOW, PURPLE, GREEN), 48 * MINER_ORE_PER_MINUTE, 16),
            // 白：8 台加 18 台大型
            new Stage(List.of(WHITE), 80 * MINER_ORE_PER_MINUTE, 20));

    /**
     * 里程碑：研究完这个科技时玩了多少小时（ADR-0018 的目标时长）。
     *
     * @param firstShell 研究完以后还要铺满第一片球壳才算到了这一步
     */
    public record Milestone(String tech, double hours, boolean firstShell) {
    }

    /** 蓝矩阵自动化 4 小时，第一次跃迁 10 小时，第一片球壳 25 小时，任务完成 40 小时。第一艘飞船不用研究，不在这里。 */
    public static final List<Milestone> MILESTONES = List.of(
            new Milestone("electromagnetic_matrix", 4, false),
            new Milestone("warp_drive", 10, false),
            new Milestone("vertical_launching_silo", 25, true),
            new Milestone("mission_complete", 40, false));

    /** 两个里程碑之间的一段：多长、要研究多久、还要造多久（小时）。 */
    public record Window(Milestone end, double length, double research, double build) {
        /** 研究和建造占这段时间的比例。 */
        public double share() {
            return (this.research + this.build) / this.length;
        }
    }

    /** 铺满一片球壳要几枚火箭（节点和框架的结构点）、几张帆（球壳的单元）。 */
    public record Shell(int rockets, int sails) {
    }

    /** 第一层的参考半径（天文单位）：最内侧的行星在 0.4 天文单位时，层的半径上限是 0.36，编辑器把第一层放在四成处。 */
    public static final double FIRST_LAYER_RADIUS = DysonSphere.maxRadius(0.4) * 0.4;

    private final TechTree tree;
    private final Map<String, Double> ore;
    private final List<Stage> stages;
    private final Shell shell;

    public Progression(TechTree tree, List<ValueGraph.Recipe> recipes, List<Stage> stages, Shell shell) {
        this.tree = tree;
        this.stages = List.copyOf(stages);
        this.shell = shell;
        // 没有配方、由设备转出来的两样：分馏塔把氢转成重氢，射线接收站把戴森球的电转成临界光子（按 100 个算，免得帆的张数取整）
        List<ValueGraph.Recipe> all = new ArrayList<>(recipes);
        all.add(new ValueGraph.Recipe(List.of(new ValueGraph.Input(List.of(NS + "hydrogen"), 1), new ValueGraph.Input(List.of(FRACTIONATOR), 1)),
                List.of(new ValueGraph.Output(NS + "deuterium", 1)), 0));
        all.add(new ValueGraph.Recipe(List.of(new ValueGraph.Input(List.of(NS + "solar_sail"), (int) Math.round(SAILS_PER_PHOTON * 100)),
                new ValueGraph.Input(List.of(RECEIVER), 100)), List.of(new ValueGraph.Output(NS + "critical_photon", 100)), 0));
        this.ore = ValueGraph.compute(rawOre(all), all, 0, 0);
    }

    /** 用默认的参考工厂，第一片球壳按编辑器默认的网格、参考半径和应力系统之前的纬度限制算。 */
    public static Progression of(TechTree tree, List<ValueGraph.Recipe> recipes) {
        return new Progression(tree, recipes, STAGES,
                firstShell(DysonGrid.of(DysonGrid.Template.GEODESIC, 1), FIRST_LAYER_RADIUS, DysonSphere.LATITUDE_BASE));
    }

    /** 配方用到、却没有配方产出的东西都是原料：设备抽出来的按 {@link #EXTRACTED}，别的按 1 个矿。 */
    private static Map<String, Double> rawOre(List<ValueGraph.Recipe> recipes) {
        Set<String> produced = new HashSet<>();
        for (ValueGraph.Recipe recipe : recipes) {
            recipe.outputs().forEach(output -> produced.add(output.item()));
        }
        Map<String, Double> raw = new LinkedHashMap<>(EXTRACTED);
        for (ValueGraph.Recipe recipe : recipes) {
            for (ValueGraph.Input input : recipe.inputs()) {
                for (String choice : input.choices()) {
                    if (!produced.contains(choice)) {
                        raw.putIfAbsent(choice, 1.0);
                    }
                }
            }
        }
        return raw;
    }

    /** 纬度限制以内最便宜的一片球壳。 */
    public static Shell firstShell(DysonGrid grid, double radius, double latitude) {
        Shell best = null;
        for (int s = 0; s < grid.shellCount(); s++) {
            if (!grid.shellWithin(s, latitude)) {
                continue;
            }
            int rockets = grid.shell(s).length * DysonLayer.NODE_SP;
            for (int frame : grid.shellFrames(s)) {
                rockets += DysonLayer.frameCost(grid.frameArc(frame), radius);
            }
            int sails = DysonLayer.shellCost(grid.shellArea(s), radius);
            if (best == null || rockets < best.rockets() || rockets == best.rockets() && sails < best.sails()) {
                best = new Shell(rockets, sails);
            }
        }
        if (best == null) {
            throw new IllegalArgumentException("no shell within " + latitude + " degrees");
        }
        return best;
    }

    /** 一样东西折合几个矿。 */
    public double ore(String item) {
        Double value = this.ore.get(item);
        if (value == null) {
            throw new IllegalArgumentException("no recipe chain for " + item);
        }
        return value;
    }

    public Shell shell() {
        return this.shell;
    }

    /** 每点要的矩阵对应的参考工厂。 */
    public Stage stage(List<String> matrices) {
        for (Stage stage : this.stages) {
            if (stage.matrices().equals(matrices)) {
                return stage;
            }
        }
        throw new IllegalArgumentException("no stage for " + matrices);
    }

    private int stageIndex(Tech tech) {
        return tech.isHandIn() ? -1 : this.stages.indexOf(this.stage(tech.matrices()));
    }

    /** 研究一点要的矩阵折合几个矿。 */
    public double orePerPoint(List<String> matrices) {
        double total = 0;
        for (String matrix : matrices) {
            total += this.ore(matrix);
        }
        return total;
    }

    /** 一个科技要的矿；上交物品的科技按上交的东西算。 */
    public double ore(Tech tech) {
        if (tech.isHandIn()) {
            double total = 0;
            for (Tech.ItemCost cost : tech.handIn()) {
                Double value = this.ore.get(cost.item());
                total += (value == null ? 1 : value) * cost.count();
            }
            return total;
        }
        return tech.points() * this.orePerPoint(tech.matrices());
    }

    /** 在参考工厂里研究一个科技要几小时；上交物品的科技是 0。 */
    public double hours(Tech tech) {
        if (tech.isHandIn()) {
            return 0;
        }
        double perPoint = this.orePerPoint(tech.matrices());
        return tech.points() / this.stage(tech.matrices()).pointsPerMinute(perPoint) / 60;
    }

    /** 每档的科技（不算升级科技）一共要研究几小时，按参考工厂的顺序。 */
    public Map<Stage, Double> stageHours() {
        Map<Stage, Double> hours = new LinkedHashMap<>();
        for (Stage stage : this.stages) {
            hours.put(stage, 0.0);
        }
        for (Tech tech : this.tree.techs()) {
            if (!tech.isHandIn()) {
                hours.merge(this.stage(tech.matrices()), this.hours(tech), Double::sum);
            }
        }
        return hours;
    }

    /** 每档科技要的矿的中位数。 */
    public Map<Stage, Double> medianOre() {
        Map<Stage, Double> medians = new LinkedHashMap<>();
        for (Stage stage : this.stages) {
            List<Double> costs = new ArrayList<>();
            for (Tech tech : this.tree.techs()) {
                if (!tech.isHandIn() && tech.matrices().equals(stage.matrices())) {
                    costs.add(this.ore(tech));
                }
            }
            if (!costs.isEmpty()) {
                costs.sort(null);
                int n = costs.size();
                medians.put(stage, n % 2 == 1 ? costs.get(n / 2) : (costs.get(n / 2 - 1) + costs.get(n / 2)) / 2);
            }
        }
        return medians;
    }

    /** 火箭和帆折合的矿，在研究完那一档的工厂里造要几小时。 */
    private double shellHours(Tech tech) {
        double ore = this.shell.rockets() * this.ore(NS + "small_carrier_rocket") + this.shell.sails() * this.ore(NS + "solar_sail");
        return ore / this.stage(tech.matrices()).orePerMinute() / 60;
    }

    /**
     * 按里程碑分段。假定玩家到一个里程碑之前，研究完了比它低的各档的全部科技，再加上它自己这一档里它的前置。
     */
    public List<Window> windows(List<Milestone> milestones) {
        List<Window> windows = new ArrayList<>();
        Set<String> done = new HashSet<>();
        double previous = 0;
        for (Milestone milestone : milestones) {
            Tech target = this.tree.get(milestone.tech()).orElseThrow(() -> new IllegalArgumentException("unknown tech " + milestone.tech()));
            Set<String> need = this.prerequisites(target);
            int level = this.stageIndex(target);
            for (Tech tech : this.tree.techs()) {
                if (this.stageIndex(tech) < level) {
                    need.add(tech.id());
                }
            }
            double research = 0;
            for (String id : need) {
                if (done.add(id)) {
                    research += this.hours(this.tree.get(id).orElseThrow());
                }
            }
            double build = milestone.firstShell() ? this.shellHours(target) : 0;
            windows.add(new Window(milestone, milestone.hours() - previous, research, build));
            previous = milestone.hours();
        }
        return windows;
    }

    /** 这个科技和它的全部前置。 */
    private Set<String> prerequisites(Tech tech) {
        Set<String> out = new HashSet<>();
        Deque<String> queue = new ArrayDeque<>(List.of(tech.id()));
        while (!queue.isEmpty()) {
            String id = queue.pop();
            if (out.add(id)) {
                queue.addAll(this.tree.get(id).orElseThrow().requires());
            }
        }
        return out;
    }

    /** 给人看的报告：每种矩阵折合几个矿，每档要研究多久，每段研究占多少。 */
    public List<String> report() {
        List<String> lines = new ArrayList<>();
        StringBuilder matrices = new StringBuilder("每个矩阵折合矿：");
        for (String matrix : TechTree.MATRICES) {
            matrices.append(String.format(Locale.ROOT, " %s %.1f", name(matrix), this.ore(matrix)));
        }
        lines.add(matrices.toString());
        Map<Stage, Double> medians = this.medianOre();
        for (Map.Entry<Stage, Double> entry : this.stageHours().entrySet()) {
            Stage stage = entry.getKey();
            lines.add(String.format(Locale.ROOT, "%-6s 每分钟 %4.0f 个矿，研究 %5.2f 小时，每个科技中位 %6.0f 个矿",
                    String.join("", stage.matrices().stream().map(Progression::name).toList()), stage.orePerMinute(), entry.getValue(),
                    medians.getOrDefault(stage, 0.0)));
        }
        lines.add(String.format(Locale.ROOT, "第一片球壳：%d 枚火箭、%d 张帆", this.shell.rockets(), this.shell.sails()));
        for (Window window : this.windows(MILESTONES)) {
            lines.add(String.format(Locale.ROOT, "到 %-24s %4.1f 小时里研究 %5.2f、建造 %5.2f，占 %3.0f%%", window.end().tech(), window.length(),
                    window.research(), window.build(), window.share() * 100));
        }
        return lines;
    }

    /** 矩阵的简称：蓝、红、黄、紫、绿、白。 */
    private static String name(String matrix) {
        int index = TechTree.MATRICES.indexOf(matrix);
        return index < 0 ? matrix : "蓝红黄紫绿白".substring(index, index + 1);
    }
}
