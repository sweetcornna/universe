package io.github.sweetcornna.universe.registry.sound;

import static io.github.sweetcornna.universe.registry.UniverseSounds.sound;

import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;

/** 聚落（ADR-0034 §二）：接管、施工、开张、决定、海盗来袭和炮台的声音，配方在 {@code tools/sounds/settlement.py}。 */
public final class SettlementSounds {
    public static final DeferredHolder<SoundEvent, SoundEvent> CLAIM = sound("settlement.claim", 1, 48, "聚落：换了总督", "Settlement changes hands");
    public static final DeferredHolder<SoundEvent, SoundEvent> BUILD = sound("settlement.build", 4, "施工：敲打", "Construction clatters");
    public static final DeferredHolder<SoundEvent, SoundEvent> DELIVER = sound("settlement.deliver", 2, "施工终端：收下材料", "Materials delivered");
    public static final DeferredHolder<SoundEvent, SoundEvent> STAGE = sound("settlement.stage", 2, "施工：完成一段", "Construction stage done");
    public static final DeferredHolder<SoundEvent, SoundEvent> OPEN = sound("settlement.open", 1, 64, "聚落：新建筑开张", "Building opens");
    public static final DeferredHolder<SoundEvent, SoundEvent> DECISION = sound("settlement.decision", 2, "总督办公室：有事要定", "Governor's bell rings");
    public static final DeferredHolder<SoundEvent, SoundEvent> RAID_ALARM = sound("settlement.raid_alarm", 1, 128, "聚落：海盗警报", "Raid siren wails");
    public static final DeferredHolder<SoundEvent, SoundEvent> RAID_WON = sound("settlement.raid_won", 1, 64, "聚落：守住了", "Settlement holds");
    public static final DeferredHolder<SoundEvent, SoundEvent> RAID_LOST = sound("settlement.raid_lost", 1, 64, "聚落：失守", "Settlement falls");
    public static final DeferredHolder<SoundEvent, SoundEvent> TURRET_FIRE = sound("settlement.turret_fire", 3, 48, "聚落炮台：开火", "Settlement turret fires");

    private SettlementSounds() {
    }

    public static void init() {
    }
}
