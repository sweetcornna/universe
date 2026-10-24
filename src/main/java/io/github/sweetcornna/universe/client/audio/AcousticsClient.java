package io.github.sweetcornna.universe.client.audio;

import java.util.concurrent.atomic.AtomicInteger;

import com.mojang.blaze3d.audio.Channel;
import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.core.audio.Acoustics;
import io.github.sweetcornna.universe.core.galaxy.Atmosphere;
import io.github.sweetcornna.universe.world.planet.PlanetEnvironment;
import io.github.sweetcornna.universe.world.space.SpaceDimensions;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.sound.PlaySoundSourceEvent;
import net.neoforged.neoforge.client.event.sound.PlayStreamingSourceEvent;
import net.neoforged.neoforge.client.event.sound.SoundEngineLoadEvent;
import org.jspecify.annotations.Nullable;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.ALC10;
import org.lwjgl.openal.EXTEfx;

/**
 * 真空、稀薄大气、水下的声音（ADR-0029 §四）：声源开始播放时，按 {@link Acoustics} 挂一个 OpenAL EFX 低通滤波器。
 *
 * <p>听者的状态每刻在主线程记一份；声源开始播放的事件在声音线程上发，那里只读这一份。
 * 滤波器的增益在原版每刻重设音量以后照样生效。装了 Sound Physics Remastered 就不挂：它也设直达滤波器，会互相盖掉。
 */
public final class AcousticsClient {
    private static final String SOUND_PHYSICS = "sound_physics_remastered";

    private record Listener(double pressure, Vec3 position, @Nullable AABB vehicle, boolean underwater) {
        static final Listener DEFAULT = new Listener(1, Vec3.ZERO, null, false);
    }

    private static volatile Listener listener = Listener.DEFAULT;
    private static volatile boolean enabled = true;
    private static volatile boolean reset;
    // 以下只在声音线程上动
    private static int filter;
    private static boolean unsupported;
    private static final AtomicInteger FILTERED = new AtomicInteger();
    private static volatile String lastFilter = "none";

    private AcousticsClient() {
    }

    public static void registerEvents(IEventBus modBus, IEventBus gameBus) {
        if (ModList.get().isLoaded(SOUND_PHYSICS)) {
            enabled = false;
            Universe.LOGGER.info("Sound Physics Remastered 管着声源的滤波器，真空和水下的声音不再处理");
            return;
        }
        gameBus.addListener(ClientTickEvent.Post.class, event -> tick());
        gameBus.addListener(PlaySoundSourceEvent.class, event -> apply(event.getSound(), event.getChannel()));
        gameBus.addListener(PlayStreamingSourceEvent.class, event -> apply(event.getSound(), event.getChannel()));
        // 声音系统重开（换设备、重载资源）后旧的滤波器没了，下次用时重建
        modBus.addListener(SoundEngineLoadEvent.class, event -> reset = true);
    }

    /** 听者所在的大气压：太空是真空，星球照星球的大气，其余（母星、货船、空间站里面）当一个大气压。 */
    public static double pressure(ClientLevel level) {
        if (SpaceDimensions.isSpace(level.dimension())) {
            return 0;
        }
        return PlanetEnvironment.context(level).map(context -> {
            Atmosphere atmosphere = context.planet().atmosphere();
            return atmosphere.present() ? atmosphere.pressure() : 0.0;
        }).orElse(1.0);
    }

    private static void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null || minecraft.player == null) {
            listener = Listener.DEFAULT;
            return;
        }
        Camera camera = minecraft.gameRenderer.mainCamera();
        Entity root = minecraft.player.getRootVehicle();
        AABB vehicle = root == minecraft.player ? null : root.getBoundingBox().inflate(Acoustics.CONTACT);
        listener = new Listener(pressure(level), camera.position(), vehicle, camera.getFluidInCamera() == FogType.WATER);
    }

    /** 声音线程上：算出这个声源该剩多少，挂上滤波器。 */
    private static void apply(SoundInstance sound, Channel channel) {
        if (!enabled || sound.isRelative() || sound.getSource() == SoundSource.MUSIC || sound.getSource() == SoundSource.UI) {
            return;
        }
        Listener state = listener;
        Vec3 at = new Vec3(sound.getX(), sound.getY(), sound.getZ());
        boolean contact = state.vehicle() != null && state.vehicle().contains(at);
        Acoustics.Filter f = Acoustics.filter(state.pressure(), at.distanceTo(state.position()), contact, state.underwater());
        if (f.none()) {
            return;
        }
        int id = filter();
        if (id == 0) {
            return;
        }
        EXTEfx.alFilterf(id, EXTEfx.AL_LOWPASS_GAIN, (float) f.gain());
        EXTEfx.alFilterf(id, EXTEfx.AL_LOWPASS_GAINHF, (float) f.highs());
        // 滤波器的参数在挂上时拷进声源，这一个对象可以反复用
        AL10.alSourcei(channel.source, EXTEfx.AL_DIRECT_FILTER, id);
        FILTERED.incrementAndGet();
        lastFilter = String.format("%s gain %.2f highs %.2f", sound.getIdentifier(), f.gain(), f.highs());
    }

    private static int filter() {
        if (reset) {
            reset = false;
            filter = 0;
            unsupported = false;
        }
        if (filter != 0 || unsupported) {
            return filter;
        }
        long context = ALC10.alcGetCurrentContext();
        long device = context == 0 ? 0 : ALC10.alcGetContextsDevice(context);
        if (device == 0 || !ALC10.alcIsExtensionPresent(device, "ALC_EXT_EFX")) {
            unsupported = true;
            Universe.LOGGER.info("声卡驱动不支持 OpenAL EFX，真空和水下的声音不处理");
            return 0;
        }
        filter = EXTEfx.alGenFilters();
        EXTEfx.alFilteri(filter, EXTEfx.AL_FILTER_TYPE, EXTEfx.AL_FILTER_LOWPASS);
        if (AL10.alGetError() != AL10.AL_NO_ERROR) {
            unsupported = true;
            filter = 0;
            Universe.LOGGER.warn("建不了 OpenAL 的低通滤波器，真空和水下的声音不处理");
        }
        return filter;
    }

    /** 开发脚本用：现在的状态和处理过几个声源。 */
    public static String describe() {
        Listener state = listener;
        return String.format("acoustics %s, pressure %.3f, underwater %s, vehicle %s, filtered %d, last %s",
                enabled ? (unsupported ? "unsupported" : "on") : "off", state.pressure(), state.underwater(), state.vehicle() != null,
                FILTERED.get(), lastFilter);
    }
}
