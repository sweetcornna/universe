package io.github.sweetcornna.universe.hazard;

import io.github.sweetcornna.universe.core.hazard.EruptionPlan;
import io.github.sweetcornna.universe.core.procgen.Rng;
import io.github.sweetcornna.universe.registry.sound.HazardSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/**
 * 一次火山喷发（ADR-0034 §六）：在玩家附近定一个喷发中心（玩家当时站的地方），远处定一个火口。先
 * {@value EruptionPlan#WARNING} 刻地面在震、HUD 上提示；然后火口冒烟喷火，熔岩弹一颗颗抛过来，落在中心
 * {@value EruptionPlan#MIN_RANGE}–{@value EruptionPlan#MAX_RANGE} 格外。不存档：服务器关了就停了。
 */
public final class VolcanicEruption {
    /** 多远内的玩家感觉得到震动、看得见提示。 */
    private static final double FELT = 72;

    private final Vec3 center;
    private final Vec3 source;
    private final Rng rng;
    private int age;

    private VolcanicEruption(Vec3 center, Vec3 source, Rng rng) {
        this.center = center;
        this.source = source;
        this.rng = rng;
    }

    /** 在 {@code near} 附近开始一次喷发；找不到火口（远处的区块没加载、地面是液体）时为 {@code null}。 */
    public static VolcanicEruption start(ServerLevel level, Vec3 near, Rng rng) {
        for (int attempt = 0; attempt < 6; attempt++) {
            double[] offset = EruptionPlan.source(rng);
            Vec3 source = Hazards.surface(level, near.x + offset[0], near.z + offset[1]);
            if (source != null) {
                VolcanicEruption eruption = new VolcanicEruption(near, source.add(0, 1, 0), rng);
                eruption.warn(level);
                return eruption;
            }
        }
        return null;
    }

    public Vec3 center() {
        return this.center;
    }

    public Vec3 source() {
        return this.source;
    }

    public int age() {
        return this.age;
    }

    /** 预警：地面在震，低沉的隆隆声。 */
    private void warn(ServerLevel level) {
        level.playSound(null, this.center.x, this.center.y, this.center.z, HazardSounds.ERUPTION_RUMBLE.get(), SoundSource.AMBIENT, 2.0F, 0.8F);
        Hazards.shake(level, this.center, FELT, 0.9F, EruptionPlan.WARNING);
        for (ServerPlayer player : this.felt(level)) {
            Hazards.hint(player, "hud.universe.hazard.eruption_warning", ChatFormatting.GOLD);
        }
    }

    /**
     * 推进一刻。
     *
     * @return 还没完
     */
    boolean tick(ServerLevel level) {
        int age = this.age++;
        EruptionPlan.Phase phase = EruptionPlan.phase(age);
        if (phase == EruptionPlan.Phase.OVER) {
            return false;
        }
        if (age == EruptionPlan.WARNING) {
            level.playSound(null, this.source.x, this.source.y, this.source.z, HazardSounds.ERUPTION_BLAST.get(), SoundSource.AMBIENT, 4.0F, 0.9F);
            Hazards.shake(level, this.center, FELT, 0.45F, EruptionPlan.DURATION);
            for (ServerPlayer player : this.felt(level)) {
                Hazards.hint(player, "hud.universe.hazard.eruption", ChatFormatting.RED);
            }
        }
        if (phase == EruptionPlan.Phase.ERUPTING) {
            this.plume(level, age);
            int bombs = EruptionPlan.bombs(age, this.rng);
            for (int i = 0; i < bombs; i++) {
                this.throwBomb(level);
            }
            if ((age - EruptionPlan.WARNING) % 100 == 99) {
                level.playSound(null, this.source.x, this.source.y, this.source.z, HazardSounds.ERUPTION_RUMBLE.get(), SoundSource.AMBIENT,
                        2.5F, 0.7F);
            }
        } else if (age % 10 == 0) {
            // 预警时火口先冒几股烟
            level.sendParticles(ParticleTypes.LARGE_SMOKE, true, true, this.source.x, this.source.y + 1, this.source.z, 6, 1.2, 0.4, 1.2, 0.04);
        }
        return true;
    }

    /** 火口的烟柱和喷出的火。 */
    private void plume(ServerLevel level, int age) {
        if (age % 2 == 0) {
            level.sendParticles(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, true, true, this.source.x, this.source.y + 1, this.source.z, 3, 1.5, 0.5, 1.5,
                    0.06);
            level.sendParticles(ParticleTypes.LARGE_SMOKE, true, true, this.source.x, this.source.y + 3, this.source.z, 6, 2, 2, 2, 0.08);
        }
        level.sendParticles(ParticleTypes.LAVA, true, true, this.source.x, this.source.y + 0.5, this.source.z, 4, 1.2, 0.3, 1.2, 0.2);
        level.sendParticles(ParticleTypes.FLAME, true, true, this.source.x, this.source.y + 1.5, this.source.z, 8, 0.8, 1.5, 0.8, 0.12);
    }

    /** 抛一颗熔岩弹：落点在中心附近，落在不该有危害的地方（基地）就不抛。 */
    private void throwBomb(ServerLevel level) {
        for (int attempt = 0; attempt < 3; attempt++) {
            double[] landing = EruptionPlan.landing(this.rng);
            Vec3 target = Hazards.surface(level, this.center.x + landing[0], this.center.z + landing[1]);
            if (target == null) {
                continue;
            }
            Vec3 from = this.source.add((this.rng.nextDouble() - 0.5) * 2, 1.5, (this.rng.nextDouble() - 0.5) * 2);
            double horizontal = Math.hypot(target.x - from.x, target.z - from.z);
            LavaBomb.launch(level, from, target.add(0, 0.2, 0), EruptionPlan.flightTicks(horizontal));
            return;
        }
    }

    private java.util.List<ServerPlayer> felt(ServerLevel level) {
        return level.getPlayers(player -> player.position().distanceTo(this.center) <= FELT);
    }
}
