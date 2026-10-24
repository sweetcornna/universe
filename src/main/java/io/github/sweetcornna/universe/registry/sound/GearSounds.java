package io.github.sweetcornna.universe.registry.sound;

import static io.github.sweetcornna.universe.registry.UniverseSounds.sound;

import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;

/** 玩家的装备：多功能工具、武器、宇航服、分析镜的声音（ADR-0029），配方在 {@code tools/sounds/gear.py}。 */
public final class GearSounds {
    // 多功能工具：采矿激光是客户端按"正在用、采矿模式"放的循环
    public static final DeferredHolder<SoundEvent, SoundEvent> MINING_BEAM = sound("tool.mining_beam", 1, "采矿激光：运转", "Mining beam hums");
    public static final DeferredHolder<SoundEvent, SoundEvent> OVERHEAT = sound("tool.overheat", 2, "多功能工具：过热", "Multitool overheats");
    public static final DeferredHolder<SoundEvent, SoundEvent> MODE = sound("tool.mode", 2, "多功能工具：切换", "Multitool switches mode");
    public static final DeferredHolder<SoundEvent, SoundEvent> SCAN = sound("tool.scan", 1, 32, "多功能工具：扫描", "Multitool scans");

    // 武器
    public static final DeferredHolder<SoundEvent, SoundEvent> BLASTER = sound("weapon.blaster", 4, 32, "爆能枪：开火", "Blaster fires");
    public static final DeferredHolder<SoundEvent, SoundEvent> SCATTER = sound("weapon.scatter", 3, 32, "散射爆能枪：开火", "Scatter blaster fires");
    public static final DeferredHolder<SoundEvent, SoundEvent> PULSE = sound("weapon.pulse", 4, 24, "脉冲喷射器：开火", "Pulse spitter fires");
    /** 客户端的循环，音高随蓄力升。 */
    public static final DeferredHolder<SoundEvent, SoundEvent> JAVELIN_CHARGE = sound("weapon.javelin_charge", 1, "烈焰标枪：蓄力", "Blaze javelin charges");
    public static final DeferredHolder<SoundEvent, SoundEvent> JAVELIN_READY = sound("weapon.javelin_ready", 1, "烈焰标枪：蓄满", "Blaze javelin charged");
    public static final DeferredHolder<SoundEvent, SoundEvent> JAVELIN_FIRE = sound("weapon.javelin_fire", 2, 48, "烈焰标枪：发射", "Blaze javelin fires");
    public static final DeferredHolder<SoundEvent, SoundEvent> LAUNCHER = sound("weapon.launcher", 2, 32, "等离子发射器：发射", "Plasma launcher fires");
    public static final DeferredHolder<SoundEvent, SoundEvent> GRENADE_BOUNCE = sound("weapon.grenade_bounce", 3, "等离子榴弹：弹跳", "Plasma grenade bounces");
    public static final DeferredHolder<SoundEvent, SoundEvent> BOLT_FIZZLE = sound("weapon.bolt_fizzle", 2, "等离子弹：熄灭", "Plasma bolt fizzles");

    // 宇航服：生命维持、喷气背包、告警都是客户端只放给自己的
    public static final DeferredHolder<SoundEvent, SoundEvent> LIFE_SUPPORT = sound("suit.life_support", 1, "宇航服：生命维持", "Life support hums");
    public static final DeferredHolder<SoundEvent, SoundEvent> JETPACK = sound("suit.jetpack", 1, "喷气背包：推进", "Jetpack thrusts");
    public static final DeferredHolder<SoundEvent, SoundEvent> WARNING_OXYGEN = sound("suit.warning_oxygen", 1, "宇航服：氧气告急", "Oxygen alarm beeps");
    public static final DeferredHolder<SoundEvent, SoundEvent> WARNING_HAZARD = sound("suit.warning_hazard", 1, "宇航服：防护告急", "Hazard alarm beeps");
    public static final DeferredHolder<SoundEvent, SoundEvent> EQUIP = sound("suit.equip", 2, "外骨骼：穿上", "Exosuit fastens");
    public static final DeferredHolder<SoundEvent, SoundEvent> REFILL_OXYGEN = sound("suit.refill_oxygen", 1, "氧气：补充", "Oxygen refills");
    public static final DeferredHolder<SoundEvent, SoundEvent> REFILL = sound("suit.refill", 1, "宇航服：充能", "Suit recharges");
    public static final DeferredHolder<SoundEvent, SoundEvent> SHIELD_HIT = sound("suit.shield_hit", 3, "个人护盾：挡下", "Personal shield absorbs");
    public static final DeferredHolder<SoundEvent, SoundEvent> SHIELD_BREAK = sound("suit.shield_break", 1, "个人护盾：击破", "Personal shield breaks");

    // 分析镜
    public static final DeferredHolder<SoundEvent, SoundEvent> VISOR_OPEN = sound("visor.open", 1, "分析镜：打开", "Visor opens");
    public static final DeferredHolder<SoundEvent, SoundEvent> VISOR_CLOSE = sound("visor.close", 1, "分析镜：关闭", "Visor closes");
    public static final DeferredHolder<SoundEvent, SoundEvent> VISOR_TICK = sound("visor.tick", 1, "分析镜：分析中", "Visor analyzes");
    public static final DeferredHolder<SoundEvent, SoundEvent> VISOR_COMPLETE = sound("visor.complete", 1, "分析镜：分析完", "Visor finishes");
    /** 新发现入册（discovery.Discoveries 用）。 */
    public static final DeferredHolder<SoundEvent, SoundEvent> VISOR_DISCOVERY = sound("visor.discovery", 1, "新发现", "Discovery logged");

    private GearSounds() {
    }

    public static void init() {
    }
}
