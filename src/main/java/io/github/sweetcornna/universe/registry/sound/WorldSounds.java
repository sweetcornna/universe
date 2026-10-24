package io.github.sweetcornna.universe.registry.sound;

import static io.github.sweetcornna.universe.registry.UniverseSounds.sound;

import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;

/** 世界里的交互：传送门、终端、前哨站、遭遇、任务和奖励的声音（ADR-0029），配方在 {@code tools/sounds/world.py}。 */
public final class WorldSounds {
    // 传送门
    public static final DeferredHolder<SoundEvent, SoundEvent> PORTAL_GLYPH = sound("world.portal_glyph", 3, "符文：记下", "Glyph learned");
    public static final DeferredHolder<SoundEvent, SoundEvent> PORTAL_OPEN = sound("world.portal_open", 1, 48, "传送门：开启", "Portal opens");
    public static final DeferredHolder<SoundEvent, SoundEvent> PORTAL_CLOSE = sound("world.portal_close", 1, 48, "传送门：关闭", "Portal closes");
    public static final DeferredHolder<SoundEvent, SoundEvent> PORTAL_TRAVEL = sound("world.portal_travel", 1, "传送门：穿过", "Portal whooshes");
    public static final DeferredHolder<SoundEvent, SoundEvent> PORTAL_DENY = sound("world.portal_deny", 1, "传送门：拨号失败", "Portal refuses address");

    // 终端、前哨站、补给
    public static final DeferredHolder<SoundEvent, SoundEvent> TERMINAL_ACCESS = sound("world.terminal_access", 3, "终端：读取", "Terminal reads");
    public static final DeferredHolder<SoundEvent, SoundEvent> TERMINAL_CONFIRM = sound("world.terminal_confirm", 2, "终端：确认", "Terminal confirms");
    public static final DeferredHolder<SoundEvent, SoundEvent> TERMINAL_DENY = sound("world.terminal_deny", 2, "终端：拒绝", "Terminal denies");
    public static final DeferredHolder<SoundEvent, SoundEvent> DOOR_BREACH = sound("world.door_breach", 1, 32, "加固门：切开", "Reinforced door gives way");
    public static final DeferredHolder<SoundEvent, SoundEvent> CAPSULE_OPEN = sound("world.capsule_open", 2, "打捞舱：打开", "Salvage capsule opens");
    public static final DeferredHolder<SoundEvent, SoundEvent> CRATE_OPEN = sound("world.crate_open", 2, "补给箱：打开", "Supply crate opens");

    // 知识石、石碑、守卫塔
    public static final DeferredHolder<SoundEvent, SoundEvent> STONE_TOUCH = sound("world.stone_touch", 2, "石头：回响", "Stone hums");
    public static final DeferredHolder<SoundEvent, SoundEvent> STONE_LEARN = sound("world.stone_learn", 2, "知识石：低语", "Knowledge stone whispers");
    public static final DeferredHolder<SoundEvent, SoundEvent> MONOLITH = sound("world.monolith", 1, 32, "先驱者石碑：共鸣", "Monolith resonates");
    public static final DeferredHolder<SoundEvent, SoundEvent> PILLAR_FOUND = sound("world.pillar_found", 1, "守卫塔：嗡鸣", "Sentinel pillar hums");
    public static final DeferredHolder<SoundEvent, SoundEvent> PILLAR_GIVE = sound("world.pillar_give", 1, "守卫塔终端：吐出工具", "Pillar terminal dispenses");

    // 信号和遭遇
    public static final DeferredHolder<SoundEvent, SoundEvent> SIGNAL = sound("world.signal", 2, "扫描：找到信号", "Signal located");
    public static final DeferredHolder<SoundEvent, SoundEvent> POI_FOUND = sound("world.poi_found", 1, "地点：发现", "Location discovered");
    public static final DeferredHolder<SoundEvent, SoundEvent> DISTRESS = sound("world.distress", 1, "求救信号：传来", "Distress call received");
    public static final DeferredHolder<SoundEvent, SoundEvent> FREIGHTER_ALARM = sound("world.freighter_alarm", 1, 96, "货船：警报", "Freighter alarm blares");
    public static final DeferredHolder<SoundEvent, SoundEvent> LOCK_ON = sound("world.lock_on", 1, "守卫机：锁定", "Sentinels lock on");
    public static final DeferredHolder<SoundEvent, SoundEvent> CONDUIT_HISS = sound("world.conduit_hiss", 3, "漏电的线管：噼啪", "Live conduit crackles");
    public static final DeferredHolder<SoundEvent, SoundEvent> CONDUIT_SHOCK = sound("world.conduit_shock", 2, "漏电的线管：电击", "Live conduit shocks");

    // 奖励和进度
    public static final DeferredHolder<SoundEvent, SoundEvent> CREDITS = sound("world.credits", 2, "信用点：到账", "Credits received");
    public static final DeferredHolder<SoundEvent, SoundEvent> PURCHASE = sound("world.purchase", 1, "交易：成交", "Purchase made");
    public static final DeferredHolder<SoundEvent, SoundEvent> REWARD = sound("world.reward", 1, "奖励：到手", "Reward earned");
    public static final DeferredHolder<SoundEvent, SoundEvent> ACHIEVEMENT = sound("world.achievement", 1, "货船：归你了", "Freighter acquired");
    public static final DeferredHolder<SoundEvent, SoundEvent> MISSION_ACCEPT = sound("world.mission_accept", 1, "任务：接下", "Mission accepted");
    public static final DeferredHolder<SoundEvent, SoundEvent> MISSION_UPDATE = sound("world.mission_update", 1, "任务：有进展", "Mission progresses");
    public static final DeferredHolder<SoundEvent, SoundEvent> MISSION_COMPLETE = sound("world.mission_complete", 1, "任务：完成", "Mission complete");
    public static final DeferredHolder<SoundEvent, SoundEvent> RESEARCH_DONE = sound("world.research_done", 1, "研究：完成", "Research complete");
    public static final DeferredHolder<SoundEvent, SoundEvent> REPAIR = sound("world.repair", 2, "维修：完成", "Repairs done");
    public static final DeferredHolder<SoundEvent, SoundEvent> GUILD_GIFT = sound("world.guild_gift", 1, "公会：送来礼物", "Guild sends a gift");
    public static final DeferredHolder<SoundEvent, SoundEvent> DIALOGUE = sound("world.dialogue", 2, "外星人：回赠", "Alien rewards you");

    private WorldSounds() {
    }

    public static void init() {
    }
}
