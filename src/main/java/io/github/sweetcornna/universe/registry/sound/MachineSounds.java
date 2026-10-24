package io.github.sweetcornna.universe.registry.sound;

import static io.github.sweetcornna.universe.registry.UniverseSounds.sound;

import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;

/** 工厂、发电、物流、戴森球的设备的声音（ADR-0029），配方在 {@code tools/sounds/machines.py}。 */
public final class MachineSounds {
    // 运转声：客户端的环境声按"运转"放的循环（client.audio.MachineSoundTable 登记哪种机器放哪条）
    public static final DeferredHolder<SoundEvent, SoundEvent> ASSEMBLER = sound("machine.assembler", 1, "装配机：运转", "Assembler whirs");
    public static final DeferredHolder<SoundEvent, SoundEvent> ARC_SMELTER = sound("machine.arc_smelter", 1, "电弧熔炉：噼啪作响", "Arc smelter crackles");
    public static final DeferredHolder<SoundEvent, SoundEvent> PLANE_SMELTER = sound("machine.plane_smelter", 1, "位面熔炉：嗡鸣", "Plane smelter hums");
    public static final DeferredHolder<SoundEvent, SoundEvent> CHEMICAL = sound("machine.chemical", 1, "化工设备：冒泡", "Chemical plant bubbles");
    public static final DeferredHolder<SoundEvent, SoundEvent> FRACTIONATOR = sound("machine.fractionator", 1, "分馏塔：旋转", "Fractionator spins");
    public static final DeferredHolder<SoundEvent, SoundEvent> LAB = sound("machine.lab", 1, "研究站：运算", "Lab computes");
    public static final DeferredHolder<SoundEvent, SoundEvent> MINER = sound("machine.miner", 1, "采矿机：钻探", "Mining machine drills");
    public static final DeferredHolder<SoundEvent, SoundEvent> WATER_PUMP = sound("machine.water_pump", 1, "抽水站：抽水", "Water pump runs");
    public static final DeferredHolder<SoundEvent, SoundEvent> OIL_EXTRACTOR = sound("machine.oil_extractor", 1, "原油萃取站：抽油", "Oil extractor pumps");
    public static final DeferredHolder<SoundEvent, SoundEvent> THERMAL = sound("machine.thermal", 1, "火力发电厂：轰鸣", "Thermal plant roars");
    public static final DeferredHolder<SoundEvent, SoundEvent> FUSION = sound("machine.fusion", 1, "聚变装置：脉动", "Fusion core pulses");
    public static final DeferredHolder<SoundEvent, SoundEvent> GEOTHERMAL = sound("machine.geothermal", 1, "地热电站：隆隆作响", "Geothermal station rumbles");
    public static final DeferredHolder<SoundEvent, SoundEvent> WIND = sound("machine.wind", 1, "风力涡轮机：转动", "Wind turbine turns");
    public static final DeferredHolder<SoundEvent, SoundEvent> COLLIDER = sound("machine.collider", 1, "粒子对撞机：加速", "Particle collider ramps up");
    public static final DeferredHolder<SoundEvent, SoundEvent> RECEIVER = sound("machine.receiver", 1, "射线接收站：嗡鸣", "Ray receiver hums");
    public static final DeferredHolder<SoundEvent, SoundEvent> TRANSFORMER = sound("machine.transformer", 1, "电气设备：嗡嗡作响", "Transformer hums");
    public static final DeferredHolder<SoundEvent, SoundEvent> BIO = sound("machine.bio", 1, "生物设备：冒泡", "Bio tank bubbles");
    public static final DeferredHolder<SoundEvent, SoundEvent> STATION = sound("machine.station", 1, "物流塔：运转", "Logistics station runs");
    public static final DeferredHolder<SoundEvent, SoundEvent> SHIELD = sound("machine.shield", 1, "行星护盾：嗡鸣", "Planetary shield hums");
    public static final DeferredHolder<SoundEvent, SoundEvent> JAMMER = sound("machine.jammer", 1, "干扰塔：干扰", "Jammer warbles");

    // 一次性的
    public static final DeferredHolder<SoundEvent, SoundEvent> EJECTOR_FIRE = sound("machine.ejector_fire", 3, 32, "电磁弹射器：发射", "EM rail ejector fires");
    public static final DeferredHolder<SoundEvent, SoundEvent> SILO_LAUNCH = sound("machine.silo_launch", 2, 96, "发射井：火箭升空", "Rocket launches");
    public static final DeferredHolder<SoundEvent, SoundEvent> SHIELD_RAISE = sound("machine.shield_raise", 1, 64, "行星护盾：展开", "Planetary shield rises");
    public static final DeferredHolder<SoundEvent, SoundEvent> SHIELD_HIT = sound("machine.shield_hit", 3, 64, "行星护盾：挡下", "Planetary shield absorbs hit");
    public static final DeferredHolder<SoundEvent, SoundEvent> SHIELD_COLLAPSE = sound("machine.shield_collapse", 1, 96, "行星护盾：崩溃", "Planetary shield collapses");
    public static final DeferredHolder<SoundEvent, SoundEvent> TELEPORT = sound("machine.teleport", 2, "传送器：传送", "Teleporter warps");
    public static final DeferredHolder<SoundEvent, SoundEvent> JAMMER_PULSE = sound("machine.jammer_pulse", 2, 32, "干扰塔：发出干扰", "Jammer pulses");
    public static final DeferredHolder<SoundEvent, SoundEvent> STATION_LOAD = sound("machine.station_load", 2, "物流塔：装入", "Logistics station loads");
    public static final DeferredHolder<SoundEvent, SoundEvent> SEQUENCER_DONE = sound("machine.sequencer_done", 1, "蛋序列仪：完成", "Egg sequencer finishes");

    private MachineSounds() {
    }

    public static void init() {
    }
}
