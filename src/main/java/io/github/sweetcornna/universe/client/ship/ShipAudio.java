package io.github.sweetcornna.universe.client.ship;

import java.util.HashMap;
import java.util.Map;

import io.github.sweetcornna.universe.client.audio.AcousticsClient;
import io.github.sweetcornna.universe.client.audio.Loops;
import io.github.sweetcornna.universe.core.ship.ShipStats;
import io.github.sweetcornna.universe.entity.ship.ShipEntity;
import io.github.sweetcornna.universe.registry.sound.ShipSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.jspecify.annotations.Nullable;

/**
 * 飞船在舱内振动以外的几层声音（ADR-0029 §三）：自己坐的船外面的气流（有大气才有，越快越响）、脉冲引擎，
 * 还有附近别人开着的船的推进器。舱内振动是 {@link ShipEngineSound}，由 {@link ShipClient} 起停。
 *
 * <p>每层要出声时才起一条循环，静下来就停，免得真空里也挂着字幕、占着声道。
 */
public final class ShipAudio {
    /** 别人的船在这个距离以内才起推进器的声音，出了 {@link #FAR} 就停；声音本身 48 格衰减完。 */
    private static final double NEAR = 48;
    private static final double FAR = 56;
    /** 气流在这个速度（格/刻）以上最响。 */
    private static final double AIR_FULL = 2.5;
    /** 大气里飞不到这么快：超过的是刚进视野或刚传送，那一刻的速度不算。 */
    private static final double SPEED_JUMP = 10;

    private static final Map<Integer, Exterior> EXTERIOR = new HashMap<>();
    private static @Nullable Air air;
    private static @Nullable Pulse pulse;
    private static @Nullable ClientLevel pressureLevel;
    private static double pressure = 1;
    private static int ticks;

    private ShipAudio() {
    }

    public static void registerEvents(IEventBus gameBus) {
        gameBus.addListener(ClientTickEvent.Post.class, event -> tick());
        gameBus.addListener(ClientPlayerNetworkEvent.LoggingOut.class, event -> clear());
    }

    private static void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer self = minecraft.player;
        ClientLevel level = minecraft.level;
        if (self == null || level == null) {
            clear();
            return;
        }
        if (minecraft.isPaused()) {
            return;
        }
        ticks++;
        // 换了维度立刻重算，平时两秒一次
        if (level != pressureLevel || ticks % 40 == 0) {
            pressure = AcousticsClient.pressure(level);
            pressureLevel = level;
        }
        if (self.getVehicle() instanceof ShipEntity own) {
            if (air != null && !Loops.playing(air)) {
                air = null;
            }
            if (air == null && airVolume(own) > 0.02F) {
                air = new Air(own);
                minecraft.getSoundManager().play(air);
            }
            if (pulse != null && !Loops.playing(pulse)) {
                pulse = null;
            }
            if (pulse == null && own.impulseActive()) {
                pulse = new Pulse(own);
                minecraft.getSoundManager().play(pulse);
            }
        }
        if (ticks % 10 == 0) {
            scan(minecraft, level, self);
        }
        EXTERIOR.values().removeIf(sound -> !Loops.playing(sound));
    }

    /** 找附近有人在开、推进器开着的别人的船；真空里传不过来，不找。 */
    private static void scan(Minecraft minecraft, ClientLevel level, LocalPlayer self) {
        if (pressure < 0.01) {
            return;
        }
        for (ShipEntity ship : level.getEntitiesOfClass(ShipEntity.class, self.getBoundingBox().inflate(NEAR))) {
            Exterior playing = EXTERIOR.get(ship.getId());
            if (Loops.playing(playing) || riding(ship) || ship.pilot() == null || exteriorVolume(ship) <= 0.02F
                    || ship.getBoundingBox().getCenter().distanceTo(self.position()) > NEAR) {
                continue;
            }
            Exterior sound = new Exterior(ship);
            EXTERIOR.put(ship.getId(), sound);
            minecraft.getSoundManager().play(sound);
        }
    }

    private static void clear() {
        air = null;
        pulse = null;
        EXTERIOR.clear();
        pressureLevel = null;
        pressure = 1;
    }

    private static boolean riding(ShipEntity ship) {
        LocalPlayer player = Minecraft.getInstance().player;
        return !ship.isRemoved() && player != null && player.getVehicle() == ship;
    }

    private static float speedShare(ShipEntity ship) {
        return ship.clientSpeed > SPEED_JUMP ? 0 : (float) Mth.clamp(ship.clientSpeed / AIR_FULL, 0, 1);
    }

    /** 气流随速度的 1.5 次方变响，稀薄的大气里按压强的平方根变小，真空里没有。 */
    private static float airVolume(ShipEntity ship) {
        return (float) (0.85 * Math.pow(speedShare(ship), 1.5) * Math.min(1, Math.sqrt(pressure)));
    }

    /** 停在地上、推进器没开时不响。 */
    private static float exteriorVolume(ShipEntity ship) {
        float throttle = ship.throttle();
        return ship.isLanded() && throttle < 0.05F ? 0 : 0.35F + 0.5F * Mth.clamp(throttle, 0, 1);
    }

    /** 一层循环：每刻往目标音量和音高靠，不该再响或者目标是零就淡出，静了就停。 */
    private abstract static class Layer extends AbstractTickableSoundInstance {
        protected final ShipEntity ship;

        Layer(SoundEvent event, ShipEntity ship) {
            super(event, SoundSource.NEUTRAL, RandomSource.create());
            this.ship = ship;
            this.looping = true;
            this.delay = 0;
            this.volume = 0.01F;
            this.pitch = this.targetPitch();
        }

        abstract boolean alive();

        abstract float targetVolume();

        abstract float targetPitch();

        void place() {
        }

        @Override
        public void tick() {
            float target = this.alive() ? this.targetVolume() : 0;
            this.volume += Mth.clamp(target - this.volume, -0.06F, 0.05F);
            if (target <= 0 && this.volume <= 0.01F) {
                this.stop();
                return;
            }
            this.pitch = Mth.lerp(0.1F, this.pitch, this.targetPitch());
            this.place();
        }

        @Override
        public boolean canStartSilent() {
            return true;
        }
    }

    /** 船外的气流，贴着耳朵放。 */
    private static final class Air extends Layer {
        Air(ShipEntity ship) {
            super(ShipSounds.AIR.get(), ship);
            this.relative = true;
            this.attenuation = SoundInstance.Attenuation.NONE;
        }

        @Override
        boolean alive() {
            return riding(this.ship);
        }

        @Override
        float targetVolume() {
            return airVolume(this.ship);
        }

        @Override
        float targetPitch() {
            return 0.75F + 0.4F * speedShare(this.ship);
        }
    }

    /** 脉冲引擎开着时的嗡鸣，越快音调越高。 */
    private static final class Pulse extends Layer {
        Pulse(ShipEntity ship) {
            super(ShipSounds.PULSE.get(), ship);
            this.relative = true;
            this.attenuation = SoundInstance.Attenuation.NONE;
        }

        @Override
        boolean alive() {
            return riding(this.ship);
        }

        @Override
        float targetVolume() {
            return this.ship.impulseActive() ? 0.6F : 0;
        }

        @Override
        float targetPitch() {
            return 0.9F + 0.15F * (float) Mth.clamp(this.ship.clientSpeed / ShipStats.IMPULSE_SPEED, 0, 1);
        }
    }

    /** 别人开着的船的推进器：跟着船走，推力越大越响，飞得越快音调越高。 */
    private static final class Exterior extends Layer {
        Exterior(ShipEntity ship) {
            super(ShipSounds.EXTERIOR.get(), ship);
            this.place();
        }

        @Override
        boolean alive() {
            Minecraft minecraft = Minecraft.getInstance();
            return !this.ship.isRemoved() && this.ship.level() == minecraft.level && minecraft.player != null && !riding(this.ship)
                    && this.ship.pilot() != null && this.ship.getBoundingBox().getCenter().distanceTo(minecraft.player.position()) <= FAR;
        }

        @Override
        float targetVolume() {
            return exteriorVolume(this.ship);
        }

        @Override
        float targetPitch() {
            return 0.85F + 0.1F * (float) Mth.clamp(this.ship.clientSpeed, 0, 3) + 0.1F * Mth.clamp(this.ship.throttle(), 0, 1);
        }

        @Override
        void place() {
            Vec3 center = this.ship.getBoundingBox().getCenter();
            this.x = center.x;
            this.y = center.y;
            this.z = center.z;
        }
    }
}
