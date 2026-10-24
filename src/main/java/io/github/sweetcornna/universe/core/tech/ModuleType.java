package io.github.sweetcornna.universe.core.tech;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 技术模块的种类（ADR-0014）。每种装在一个地方，提供一到两项加成。
 */
public enum ModuleType {
    LIFE_SUPPORT(ModuleHost.EXOSUIT, "生命维持扩容", "Life Support Expansion", Stat.OXYGEN),
    COLD_LINING(ModuleHost.EXOSUIT, "防寒层", "Thermal Lining", Stat.COLD),
    HEAT_LINING(ModuleHost.EXOSUIT, "隔热层", "Heat Shielding", Stat.HEAT),
    TOXIN_FILTER(ModuleHost.EXOSUIT, "毒素过滤", "Toxin Filter", Stat.TOXIC),
    RADIATION_SHIELD(ModuleHost.EXOSUIT, "辐射屏蔽", "Radiation Shield", Stat.RADIATION),
    JET_BOOSTER(ModuleHost.EXOSUIT, "喷气推进器", "Jet Booster", Stat.JET_TIME, Stat.JET_THRUST),
    SERVO(ModuleHost.EXOSUIT, "动力伺服", "Power Servo", Stat.SPEED),
    SHIELD(ModuleHost.EXOSUIT, "个人护盾", "Personal Shield", Stat.SHIELD),
    BEAM_FOCUS(ModuleHost.MULTITOOL, "光束聚焦", "Beam Focus", Stat.MINING),
    HEAT_SINK(ModuleHost.MULTITOOL, "散热片", "Heat Sink", Stat.COOLING),
    SCANNER_ARRAY(ModuleHost.MULTITOOL, "扫描阵列", "Scanner Array", Stat.SCAN_RADIUS, Stat.SCAN_COOLDOWN),
    ANALYZER(ModuleHost.MULTITOOL, "分析处理器", "Analysis Processor", Stat.ANALYSIS),
    WEAPON_CALIBRATOR(ModuleHost.MULTITOOL, "武器校准", "Weapon Calibrator", Stat.DAMAGE, Stat.FIRE_RATE),
    // ---- 飞船（ADR-0024）：只加在后面，网络和存档按序号认
    THRUST_EFFICIENCY(ModuleHost.SHIP, "推进效率", "Thrust Efficiency", Stat.FUEL_USE),
    PULSE_TUNING(ModuleHost.SHIP, "脉冲调校", "Pulse Tuning", Stat.PULSE_SPEED),
    HYPERDRIVE_BOOSTER(ModuleHost.SHIP, "超空间增幅", "Hyperdrive Booster", Stat.WARP_RANGE),
    DEFLECTOR(ModuleHost.SHIP, "偏导护盾", "Deflector Shield", Stat.SHIP_SHIELD),
    HULL_REINFORCEMENT(ModuleHost.SHIP, "船体加固", "Hull Reinforcement", Stat.HULL),
    FIRE_CONTROL(ModuleHost.SHIP, "火控校准", "Fire Control", Stat.DAMAGE, Stat.FIRE_RATE),
    // ---- 深空（ADR-0034 §四）：装在驾驶台，开着时把附近的掉落物和碎片吸进货舱
    TRACTOR_BEAM(ModuleHost.SHIP, "牵引光束", "Tractor Beam", Stat.TRACTOR_RANGE);

    /** 同一种模块最多几个叠加生效。 */
    public static final int MAX_STACK = 3;

    private final ModuleHost host;
    private final String nameZh;
    private final String nameEn;
    private final List<Stat> stats;

    ModuleType(ModuleHost host, String nameZh, String nameEn, Stat... stats) {
        this.host = host;
        this.nameZh = nameZh;
        this.nameEn = nameEn;
        this.stats = List.of(stats);
    }

    public ModuleHost host() {
        return this.host;
    }

    public String nameZh() {
        return this.nameZh;
    }

    public String nameEn() {
        return this.nameEn;
    }

    public List<Stat> stats() {
        return this.stats;
    }

    /** 物品 ID 里用的名字，比如 {@code module_life_support}。 */
    public String itemName() {
        return "module_" + this.name().toLowerCase(Locale.ROOT);
    }

    /**
     * 外骨骼和多功能工具的模块，按原来的顺序。任务、兴趣点、交易终端从这里抽，飞船模块加进来以后老存档抽到的东西不变。
     */
    public static List<ModuleType> personal() {
        List<ModuleType> types = new ArrayList<>();
        for (ModuleType type : values()) {
            if (type.host != ModuleHost.SHIP) {
                types.add(type);
            }
        }
        return types;
    }

    public static List<ModuleType> of(ModuleHost host) {
        List<ModuleType> types = new ArrayList<>();
        for (ModuleType type : values()) {
            if (type.host == host) {
                types.add(type);
            }
        }
        return types;
    }
}
