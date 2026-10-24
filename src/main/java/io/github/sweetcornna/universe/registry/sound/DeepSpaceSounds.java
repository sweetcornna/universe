package io.github.sweetcornna.universe.registry.sound;

import static io.github.sweetcornna.universe.registry.UniverseSounds.sound;

import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;

/**
 * 深空和引力抓取器的声音（ADR-0034 §四、§七）：残骸的警报和熔毁、碎片碎裂、牵引光束、出舱、抛货、引力抓取器，
 * 配方在 {@code tools/sounds/deepspace.py}。
 */
public final class DeepSpaceSounds {
    // 残骸
    public static final DeferredHolder<SoundEvent, SoundEvent> SIREN = sound("deepspace.siren", 1, 96, "残骸：警报", "Wreck alarm blares");
    public static final DeferredHolder<SoundEvent, SoundEvent> GROAN = sound("deepspace.groan", 3, 48, "残骸：金属呻吟", "Wreck groans");
    public static final DeferredHolder<SoundEvent, SoundEvent> MELTDOWN = sound("deepspace.meltdown", 1, 160, "反应堆：熔毁", "Reactor melts down");
    public static final DeferredHolder<SoundEvent, SoundEvent> REACTOR_HUM = sound("deepspace.reactor_hum", 2, "反应堆芯：嗡鸣", "Reactor core hums");
    public static final DeferredHolder<SoundEvent, SoundEvent> DEBRIS_BREAK = sound("deepspace.debris_break", 3, 48, "残骸碎片：碎裂", "Debris shatters");

    // 飞船
    public static final DeferredHolder<SoundEvent, SoundEvent> TRACTOR_ON = sound("deepspace.tractor_on", 1, "牵引光束：开启", "Tractor beam engages");
    public static final DeferredHolder<SoundEvent, SoundEvent> TRACTOR_OFF = sound("deepspace.tractor_off", 1, "牵引光束：关闭", "Tractor beam disengages");
    public static final DeferredHolder<SoundEvent, SoundEvent> TRACTOR_STOW = sound("deepspace.tractor_stow", 3, "牵引光束：收进货舱", "Tractor beam stows cargo");
    public static final DeferredHolder<SoundEvent, SoundEvent> HATCH = sound("deepspace.hatch", 2, "舱门：泄压", "Hatch cycles");
    public static final DeferredHolder<SoundEvent, SoundEvent> JETTISON = sound("deepspace.jettison", 2, "货物：抛出", "Cargo jettisoned");

    // 引力抓取器
    public static final DeferredHolder<SoundEvent, SoundEvent> GRAVITY_GRAB = sound("deepspace.gravity_grab", 2, "引力抓取器：抓住", "Gravity coil grabs");
    public static final DeferredHolder<SoundEvent, SoundEvent> GRAVITY_HOLD = sound("deepspace.gravity_hold", 1, "引力抓取器：嗡嗡", "Gravity coil hums");
    public static final DeferredHolder<SoundEvent, SoundEvent> GRAVITY_DROP = sound("deepspace.gravity_drop", 2, "引力抓取器：放下", "Gravity coil releases");
    public static final DeferredHolder<SoundEvent, SoundEvent> GRAVITY_THROW = sound("deepspace.gravity_throw", 2, 32, "引力抓取器：抛出", "Gravity coil launches");
    public static final DeferredHolder<SoundEvent, SoundEvent> GRAVITY_PUSH = sound("deepspace.gravity_push", 2, 32, "引力抓取器：冲击波", "Gravity coil pulses");
    public static final DeferredHolder<SoundEvent, SoundEvent> GRAVITY_IMPACT = sound("deepspace.gravity_impact", 3, "抛出的东西：撞上", "Thrown object hits");

    private DeepSpaceSounds() {
    }

    /** 加载这个类，静态字段登记声音。 */
    public static void init() {
    }
}
