package io.github.sweetcornna.universe.registry.sound;

import static io.github.sweetcornna.universe.registry.UniverseSounds.sound;

import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;

/** 钓鱼（ADR-0034 §一）的声音，配方在 {@code tools/sounds/fishing.py}。 */
public final class FishingSounds {
    public static final DeferredHolder<SoundEvent, SoundEvent> CAST = sound("fishing.cast", 2, "钓鱼器：甩竿", "Fishing rig casts");
    public static final DeferredHolder<SoundEvent, SoundEvent> LAND = sound("fishing.land", 3, "浮标：落水", "Bobber lands");
    public static final DeferredHolder<SoundEvent, SoundEvent> BITE = sound("fishing.bite", 2, "鱼：咬钩", "Fish bites");
    public static final DeferredHolder<SoundEvent, SoundEvent> HOOK = sound("fishing.hook", 2, "钓鱼器：钩住", "Fish hooked");
    /** 按住收线时一下一下放的棘轮声，音高跟着张力走。 */
    public static final DeferredHolder<SoundEvent, SoundEvent> REEL = sound("fishing.reel", 3, "钓鱼器：收线", "Line reels in");
    public static final DeferredHolder<SoundEvent, SoundEvent> STRAIN = sound("fishing.strain", 2, "钓线：绷紧", "Line strains");
    public static final DeferredHolder<SoundEvent, SoundEvent> DASH = sound("fishing.dash", 3, "鱼：猛冲", "Fish thrashes");
    public static final DeferredHolder<SoundEvent, SoundEvent> SNAP = sound("fishing.snap", 2, "钓线：断了", "Line snaps");
    public static final DeferredHolder<SoundEvent, SoundEvent> ESCAPE = sound("fishing.escape", 2, "鱼：脱钩", "Fish slips free");
    public static final DeferredHolder<SoundEvent, SoundEvent> CATCH = sound("fishing.catch", 2, "鱼：钓上来", "Fish landed");
    public static final DeferredHolder<SoundEvent, SoundEvent> TROPHY = sound("fishing.trophy", 1, "鱼：钓上稀有的", "Rare fish landed");
    public static final DeferredHolder<SoundEvent, SoundEvent> RELEASE = sound("fishing.release", 2, "鱼：放生", "Fish released");
    public static final DeferredHolder<SoundEvent, SoundEvent> TRAP = sound("fishing.trap", 2, "捕鱼笼：进鱼", "Fish trap catches");

    private FishingSounds() {
    }

    /** 让静态字段登记进声音表。 */
    public static void init() {
    }
}
