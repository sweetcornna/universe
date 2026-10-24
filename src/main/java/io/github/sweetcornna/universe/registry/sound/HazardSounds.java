package io.github.sweetcornna.universe.registry.sound;

import static io.github.sweetcornna.universe.registry.UniverseSounds.sound;

import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;

/** 局部危害的声音（ADR-0034 §六）：间歇泉、火山喷发、孢子云、尘卷风；配方在 {@code tools/sounds/hazard.py}。 */
public final class HazardSounds {
    public static final DeferredHolder<SoundEvent, SoundEvent> GEYSER_RUMBLE = sound("hazard.geyser_rumble", 2, 24, "间歇泉：咕嘟作响",
            "Geyser gurgles");
    public static final DeferredHolder<SoundEvent, SoundEvent> GEYSER_ERUPT = sound("hazard.geyser_erupt", 2, 48, "间歇泉：喷发", "Geyser erupts");
    public static final DeferredHolder<SoundEvent, SoundEvent> ERUPTION_RUMBLE = sound("hazard.eruption_rumble", 2, 96, "地面：震动",
            "Ground trembles");
    public static final DeferredHolder<SoundEvent, SoundEvent> ERUPTION_BLAST = sound("hazard.eruption_blast", 2, 160, "火山：喷发",
            "Volcano erupts");
    public static final DeferredHolder<SoundEvent, SoundEvent> LAVA_BOMB_FLY = sound("hazard.lava_bomb_fly", 3, 32, "熔岩弹：呼啸",
            "Lava bomb whistles");
    public static final DeferredHolder<SoundEvent, SoundEvent> LAVA_BOMB_IMPACT = sound("hazard.lava_bomb_impact", 3, 48, "熔岩弹：砸落",
            "Lava bomb lands");
    public static final DeferredHolder<SoundEvent, SoundEvent> SPORE_CLOUD = sound("hazard.spore_cloud", 2, 24, "孢子云：沙沙作响",
            "Spores rustle");
    public static final DeferredHolder<SoundEvent, SoundEvent> DUST_DEVIL = sound("hazard.dust_devil", 2, 40, "尘卷风：呼啸", "Dust devil howls");

    private HazardSounds() {
    }

    public static void init() {
    }
}
