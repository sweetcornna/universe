package io.github.sweetcornna.universe.client.ship;

import io.github.sweetcornna.universe.entity.ship.ShipEntity;
import io.github.sweetcornna.universe.registry.sound.ShipSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/**
 * 驾驶时的舱内振动（ADR-0029 §三）：推进器和涡轮泵的振动顺着船体传进来，推力越大越响，飞得越快音调越高。
 * 真空里只剩这一层；外面的气流和脉冲引擎由 {@link ShipAudio} 另放。
 */
final class ShipEngineSound extends AbstractTickableSoundInstance {
    private final ShipEntity ship;

    ShipEngineSound(ShipEntity ship) {
        super(ShipSounds.CABIN.get(), SoundSource.NEUTRAL, RandomSource.create());
        this.ship = ship;
        this.looping = true;
        this.delay = 0;
        this.volume = 0.01F;
        this.pitch = 0.8F;
        this.relative = true;
        this.attenuation = SoundInstance.Attenuation.NONE;
    }

    @Override
    public void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        if (this.ship.isRemoved() || minecraft.player == null || minecraft.player.getVehicle() != this.ship) {
            this.stop();
            return;
        }
        float throttle = this.ship.throttle();
        float speed = (float) this.ship.clientSpeed;
        // 停在地上、推进器没开时只剩设备的低声
        float target = this.ship.isLanded() && throttle < 0.05F ? 0.12F : 0.3F + 0.4F * Mth.clamp(throttle, 0, 1.6F);
        this.volume = Mth.lerp(0.15F, this.volume, target);
        float pitch = 0.8F + 0.1F * Mth.clamp(speed, 0, 3) + 0.15F * Mth.clamp(throttle, 0, 1) + (this.ship.impulseActive() ? 0.15F : 0);
        this.pitch = Mth.lerp(0.1F, this.pitch, pitch);
    }

    @Override
    public boolean canStartSilent() {
        return true;
    }
}
