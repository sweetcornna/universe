package io.github.sweetcornna.universe.registry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.registry.sound.GearSounds;
import io.github.sweetcornna.universe.registry.sound.HostileSounds;
import io.github.sweetcornna.universe.registry.sound.MachineSounds;
import io.github.sweetcornna.universe.registry.sound.ShipSounds;
import io.github.sweetcornna.universe.registry.sound.WorldSounds;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 本模组的声音（ADR-0029）。文件由 {@code tools/sounds} 合成，{@code sounds.json} 和字幕由数据生成。
 *
 * <p>各类声音的持有者在 {@code registry.sound} 下的几个类里，都经 {@link #sound} 登记。
 */
public final class UniverseSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, Universe.MOD_ID);
    /** 默认的传播距离（格），和原版一样。 */
    public static final int DEFAULT_RANGE = 16;
    private static final List<Spec> SPECS = new ArrayList<>();

    // 几类共用的
    public static final DeferredHolder<SoundEvent, SoundEvent> EXPLOSION_SMALL = sound("explosion.small", 3, 48, "爆炸", "Explosion");
    public static final DeferredHolder<SoundEvent, SoundEvent> EXPLOSION_LARGE = sound("explosion.large", 2, 96, "剧烈爆炸", "Large explosion");
    public static final DeferredHolder<SoundEvent, SoundEvent> IMPACT_ENERGY = sound("impact.energy", 4, "能量弹：命中", "Energy bolt hits");

    private UniverseSounds() {
    }

    /**
     * 一个声音。
     *
     * @param name     点分隔的名字（{@code machine.assembler}），文件是 {@code sounds/machine/assembler_1.ogg} 到 {@code _variants.ogg}
     * @param variants 变体数，每次随机挑一个
     * @param zh       中文字幕，照原版写成"主体：动作"
     * @param en       英文字幕，照原版写成"Subject verbs"
     */
    public record Spec(String name, int variants, int range, String zh, String en, DeferredHolder<SoundEvent, SoundEvent> holder) {
        public String path() {
            return this.name.replace('.', '/');
        }

        public String subtitle() {
            return "subtitles." + Universe.MOD_ID + "." + this.name;
        }
    }

    public static DeferredHolder<SoundEvent, SoundEvent> sound(String name, int variants, String zh, String en) {
        return sound(name, variants, DEFAULT_RANGE, zh, en);
    }

    /** range 超过 16 格时注册成固定范围的事件：服务端照这个距离发给玩家，客户端在 sounds.json 里照同样的距离衰减。 */
    public static DeferredHolder<SoundEvent, SoundEvent> sound(String name, int variants, int range, String zh, String en) {
        Identifier id = Universe.id(name);
        DeferredHolder<SoundEvent, SoundEvent> holder = SOUNDS.register(name,
                () -> range > DEFAULT_RANGE ? SoundEvent.createFixedRangeEvent(id, range) : SoundEvent.createVariableRangeEvent(id));
        SPECS.add(new Spec(name, variants, range, zh, en, holder));
        return holder;
    }

    public static List<Spec> specs() {
        return Collections.unmodifiableList(SPECS);
    }

    public static void register(IEventBus modBus) {
        // 先把各类的持有者类加载进来，它们的静态字段才会登记
        MachineSounds.init();
        ShipSounds.init();
        GearSounds.init();
        HostileSounds.init();
        WorldSounds.init();
        SOUNDS.register(modBus);
    }
}
