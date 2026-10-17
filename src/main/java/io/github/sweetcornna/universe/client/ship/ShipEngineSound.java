package io.github.sweetcornna.universe.client.ship;

import io.github.sweetcornna.universe.entity.ship.ShipEntity;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/** 驾驶时的引擎声：推进器越用力越响，飞得越快音调越高；开脉冲引擎时更尖。 */
final class ShipEngineSound extends AbstractTickableSoundInstance {
    private final ShipEntity ship;

    ShipEngineSound(ShipEntity ship) {
        super(SoundEvents.ELYTRA_FLYING, SoundSource.NEUTRAL, RandomSource.create());
        this.ship = ship;
        this.looping = true;
        this.delay = 0;
        this.volume = 0.01F;
        this.relative = true;
        this.attenuation = SoundInstance.Attenuation.NONE;
    }

    @Override
    public void tick() {
        if (this.ship.isRemoved() || this.ship.getPassengers().isEmpty()) {
            this.stop();
            return;
        }
        float throttle = this.ship.throttle();
        float speed = (float) this.ship.clientSpeed;
        float target = this.ship.isLanded() && throttle < 0.05F ? 0.0F : 0.15F + 0.35F * Mth.clamp(throttle, 0, 1.6F);
        this.volume = Mth.lerp(0.15F, this.volume, target);
        float pitch = 0.55F + 0.12F * Mth.clamp(speed, 0, 3) + (this.ship.impulseActive() ? 0.5F : 0);
        this.pitch = Mth.lerp(0.1F, this.pitch, pitch);
    }

    @Override
    public boolean canStartSilent() {
        return true;
    }
}
