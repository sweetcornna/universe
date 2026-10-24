package io.github.sweetcornna.universe.sentinel;

import io.github.sweetcornna.universe.core.sentinel.SentinelKind;
import io.github.sweetcornna.universe.registry.sound.HostileSounds;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * 召唤无人机（ADR-0026）：24 点生命，不开火，远远地绕着目标飞；活着时每 15 秒传送来 2 架无人机（附近最多 6 架），
 * 叫来的也算进这一波。召唤前 2 秒身边聚起紫光，先打掉它就不会越打越多。
 */
public class SummonerSentinel extends Sentinel {
    /** 两次召唤隔多少刻。 */
    public static final int SUMMON_INTERVAL = 300;
    public static final int SUMMON_COUNT = 2;
    /** 附近已经有这么多无人机就不再叫。 */
    public static final int MAX_DRONES = 6;
    static final double DRONE_RADIUS = 32;
    /** 召唤前聚光多少刻。 */
    static final int CHARGE = 40;
    static final int PORTAL_COLOR = 0xB06CFF;
    private static final EntityDataAccessor<Boolean> DATA_CHARGING = SynchedEntityData.defineId(SummonerSentinel.class,
            EntityDataSerializers.BOOLEAN);

    private int summonCooldown = 100;

    public SummonerSentinel(EntityType<? extends SummonerSentinel> type, Level level) {
        super(type, level);
        this.xpReward = 10;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Sentinel.createAttributes(false)
                .add(Attributes.MAX_HEALTH, 24)
                .add(Attributes.FLYING_SPEED, 0.45)
                .add(Attributes.ARMOR, 4)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.6);
    }

    @Override
    public SentinelKind kind() {
        return SentinelKind.SUMMONER;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder entityData) {
        super.defineSynchedData(entityData);
        entityData.define(DATA_CHARGING, false);
    }

    /** 正在聚光，马上要召唤（环转得快）。 */
    public boolean charging() {
        return this.entityData.get(DATA_CHARGING);
    }

    @Override
    protected boolean armed() {
        return false;
    }

    @Override
    protected double standOff() {
        return 13;
    }

    @Override
    public void tick() {
        super.tick();
        if (!(this.level() instanceof ServerLevel level)) {
            return;
        }
        if (!this.isAlive() || this.retreating() || !this.isHostile() || !(this.getTarget() instanceof ServerPlayer player)
                || !player.isAlive()) {
            this.entityData.set(DATA_CHARGING, false);
            return;
        }
        this.summonCooldown--;
        this.entityData.set(DATA_CHARGING, this.summonCooldown <= CHARGE);
        if (this.summonCooldown <= CHARGE && this.summonCooldown > 0 && this.summonCooldown % 4 == 0) {
            level.sendParticles(ParticleTypes.PORTAL, this.getX(), this.getY(0.5), this.getZ(), 10, 0.8, 0.6, 0.8, 0.4);
            if (this.summonCooldown == CHARGE) {
                this.playSound(HostileSounds.SUMMON_CHARGE.get(), 0.9F, 1.0F);
            }
        }
        if (this.summonCooldown <= 0) {
            this.summonCooldown = SUMMON_INTERVAL;
            this.summon(level, player);
        }
    }

    /** 在身边放几架无人机，算进目标玩家的这一波。 */
    void summon(ServerLevel level, ServerPlayer player) {
        int drones = 0;
        for (Sentinel other : Sentinel.near(level, this.position(), DRONE_RADIUS)) {
            if (other.kind() == SentinelKind.DRONE && other.isAlive() && !other.retreating()) {
                drones++;
            }
        }
        int count = Math.min(SUMMON_COUNT, MAX_DRONES - drones);
        int placed = 0;
        for (int attempt = 0; attempt < count * 4 && placed < count; attempt++) {
            double angle = this.random.nextDouble() * Math.PI * 2;
            double ring = 2.5 + this.random.nextDouble() * 2;
            Vec3 pos = new Vec3(this.getX() + Math.cos(angle) * ring, this.getY() + this.random.nextDouble() * 2 - 1,
                    this.getZ() + Math.sin(angle) * ring);
            Sentinel drone = Sentinels.spawnAt(level, pos, SentinelKind.DRONE, player);
            if (drone == null) {
                continue;
            }
            placed++;
            Sentinels.reinforce(player, drone);
            Vec3 from = this.position().add(0, this.getBbHeight() * 0.5, 0);
            Vec3 to = drone.position().add(0, drone.getBbHeight() * 0.5, 0);
            DustParticleOptions dust = new DustParticleOptions(PORTAL_COLOR, 0.9F);
            int steps = (int) (from.distanceTo(to) / 0.35);
            for (int i = 1; i < steps; i++) {
                Vec3 p = from.lerp(to, i / (double) steps);
                level.sendParticles(dust, p.x, p.y, p.z, 1, 0.03, 0.03, 0.03, 0);
            }
        }
        if (placed > 0) {
            level.playSound(null, this.getX(), this.getY(), this.getZ(), HostileSounds.SUMMON.get(), SoundSource.HOSTILE, 1.0F, 1.0F);
        }
    }
}
