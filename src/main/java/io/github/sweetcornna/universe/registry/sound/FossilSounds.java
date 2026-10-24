package io.github.sweetcornna.universe.registry.sound;

import static io.github.sweetcornna.universe.registry.UniverseSounds.sound;

import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;

/** 化石和石哨兵（ADR-0034 §三）的声音，配方在 {@code tools/sounds/fossil.py}。 */
public final class FossilSounds {
    public static final DeferredHolder<SoundEvent, SoundEvent> BONE_PLACE = sound("fossil.bone_place", 3, "骨头：放下", "Bone placed");
    public static final DeferredHolder<SoundEvent, SoundEvent> BONE_TAKE = sound("fossil.bone_take", 2, "骨头：拿起", "Bone taken");
    public static final DeferredHolder<SoundEvent, SoundEvent> PACK = sound("fossil.pack", 1, "骨架：封装", "Skeleton packed");
    public static final DeferredHolder<SoundEvent, SoundEvent> UNPACK = sound("fossil.unpack", 1, "骨架：摆上展台", "Skeleton unpacked");
    public static final DeferredHolder<SoundEvent, SoundEvent> SKELETON_COMPLETE = sound("fossil.skeleton_complete", 1, "骨架：拼齐", "Skeleton completed");
    public static final DeferredHolder<SoundEvent, SoundEvent> SELL = sound("fossil.sell", 1, "收藏家：收下展品", "Collector buys exhibit");
    public static final DeferredHolder<SoundEvent, SoundEvent> SWAP = sound("fossil.swap", 1, "收藏家：换骨头", "Collector swaps bones");
    /** 挖化石土时，睡着的石哨兵身上掉碎石。 */
    public static final DeferredHolder<SoundEvent, SoundEvent> UNREST = sound("fossil.unrest", 2, "石哨兵：躁动", "Stone sentry stirs");
    public static final DeferredHolder<SoundEvent, SoundEvent> SENTRY_WAKE = sound("fossil.sentry_wake", 1, 24, "石哨兵：苏醒", "Stone sentry awakens");
    public static final DeferredHolder<SoundEvent, SoundEvent> SENTRY_AMBIENT = sound("fossil.sentry_ambient", 3, "石哨兵：磨响", "Stone sentry grinds");
    public static final DeferredHolder<SoundEvent, SoundEvent> SENTRY_HURT = sound("fossil.sentry_hurt", 3, "石哨兵：受伤", "Stone sentry hurts");
    public static final DeferredHolder<SoundEvent, SoundEvent> SENTRY_DEATH = sound("fossil.sentry_death", 1, "石哨兵：碎裂", "Stone sentry shatters");
    public static final DeferredHolder<SoundEvent, SoundEvent> SENTRY_THROW = sound("fossil.sentry_throw", 2, "石哨兵：掷石", "Stone sentry hurls rock");
    public static final DeferredHolder<SoundEvent, SoundEvent> ROCK_IMPACT = sound("fossil.rock_impact", 3, "石块：砸中", "Rock hits");
    /** 石哨兵残血时自爆前的嘶响。 */
    public static final DeferredHolder<SoundEvent, SoundEvent> SENTRY_FUSE = sound("fossil.sentry_fuse", 1, "石哨兵：过热", "Stone sentry overheats");

    private FossilSounds() {
    }

    /** 让静态字段登记进声音表。 */
    public static void init() {
    }
}
