package io.github.sweetcornna.universe.hazard;

import io.github.sweetcornna.universe.core.galaxy.Hazard;
import io.github.sweetcornna.universe.core.hazard.EruptionPlan;
import io.github.sweetcornna.universe.registry.UniverseEntities;
import io.github.sweetcornna.universe.registry.sound.HazardSounds;
import io.github.sweetcornna.universe.survival.SurvivalDamage;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * 火山喷发抛出来的熔岩弹（ADR-0034 §六）：一团冒烟的熔岩，按抛物线飞（没有空气阻力，重力
 * {@value EruptionPlan#GRAVITY}），落地或者砸到东西就碎开，{@value EruptionPlan#BURN_RADIUS} 格内的生物着火、烫伤，不炸方块。
 * 泡进水里就熄了。
 */
public class LavaBomb extends ThrowableProjectile {
    private static final byte EVENT_SPLASH = EntityEvent.DEATH;
    private static final byte EVENT_QUENCH = EntityEvent.POOF;
    /** 飞了这么久还没落地（掉进深坑、飞出了加载的区块）就散掉。 */
    private static final int LIFETIME = 20 * 12;

    public LavaBomb(EntityType<? extends LavaBomb> type, Level level) {
        super(type, level);
    }

    /** 从 {@code from} 抛向 {@code target}，{@code ticks} 刻以后落到，已经加进世界。 */
    public static LavaBomb launch(ServerLevel level, Vec3 from, Vec3 target, int ticks) {
        LavaBomb bomb = new LavaBomb(UniverseEntities.LAVA_BOMB.get(), level);
        bomb.setPos(from);
        double[] v = EruptionPlan.launch(target.x - from.x, target.y - from.y, target.z - from.z, ticks, EruptionPlan.GRAVITY);
        bomb.setDeltaMovement(v[0], v[1], v[2]);
        level.addFreshEntity(bomb);
        return bomb;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
    }

    @Override
    protected double getDefaultGravity() {
        return EruptionPlan.GRAVITY;
    }

    @Override
    protected float getAirDrag() {
        return 1;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 192 * 192;
    }

    @Override
    public boolean isOnFire() {
        return false;
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide()) {
            this.trail();
            return;
        }
        if (this.isRemoved()) {
            return;
        }
        if (this.isInWater()) {
            this.level().broadcastEntityEvent(this, EVENT_QUENCH);
            this.playSound(net.minecraft.sounds.SoundEvents.FIRE_EXTINGUISH, 1.0F, 0.8F);
            this.discard();
            return;
        }
        if (this.tickCount > LIFETIME) {
            this.discard();
        } else if (this.tickCount % 12 == 1 && this.level() instanceof ServerLevel level) {
            level.playSound(null, this.getX(), this.getY(), this.getZ(), HazardSounds.LAVA_BOMB_FLY.get(), SoundSource.AMBIENT, 0.7F,
                    0.9F + this.random.nextFloat() * 0.2F);
        }
    }

    /** 拖着的烟和火星：烟往后飘、慢慢散，偶尔甩下一滴熔岩。 */
    private void trail() {
        if (this.tickCount < 2) {
            return;
        }
        Vec3 v = this.getDeltaMovement();
        for (int i = 0; i < 2; i++) {
            double t = this.random.nextDouble();
            this.level().addParticle(ParticleTypes.LARGE_SMOKE, this.getX() - v.x * t, this.getY() - v.y * t + 0.15, this.getZ() - v.z * t,
                    (this.random.nextDouble() - 0.5) * 0.04, 0.03, (this.random.nextDouble() - 0.5) * 0.04);
        }
        this.level().addParticle(ParticleTypes.FLAME, this.getX(), this.getY() + 0.15, this.getZ(), 0, 0, 0);
        if (this.random.nextInt(4) == 0) {
            this.level().addParticle(ParticleTypes.LAVA, this.getX(), this.getY(), this.getZ(), 0, 0, 0);
        }
    }

    @Override
    protected boolean canHitEntity(Entity entity) {
        return !(entity instanceof LavaBomb) && super.canHitEntity(entity);
    }

    @Override
    protected void onHit(HitResult hitResult) {
        super.onHit(hitResult);
        if (this.level() instanceof ServerLevel level && !this.isRemoved()) {
            this.splash(level, hitResult.getLocation());
        }
    }

    /** 碎开：附近的生物着火、烫伤，离得越近越重。 */
    void splash(ServerLevel level, Vec3 at) {
        double radius = EruptionPlan.BURN_RADIUS;
        AABB box = new AABB(at, at).inflate(radius + 1);
        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, box, LivingEntity::isAlive)) {
            if (entity instanceof Player player && (player.isSpectator() || player.isCreative())) {
                continue;
            }
            double distance = Math.sqrt(entity.getBoundingBox().distanceToSqr(at));
            float damage = EruptionPlan.burn(distance);
            if (damage <= 0) {
                continue;
            }
            entity.hurtServer(level, level.damageSources().source(SurvivalDamage.BY_HAZARD.get(Hazard.HEAT)), damage);
            entity.igniteForTicks(EruptionPlan.fireTicks(distance));
        }
        level.playSound(null, at.x, at.y, at.z, HazardSounds.LAVA_BOMB_IMPACT.get(), SoundSource.AMBIENT, 1.2F,
                0.85F + this.random.nextFloat() * 0.3F);
        this.setPos(at);
        level.broadcastEntityEvent(this, EVENT_SPLASH);
        this.discard();
    }

    @Override
    public void handleEntityEvent(@EntityEvent.Value byte id) {
        if (id == EVENT_QUENCH) {
            for (int i = 0; i < 12; i++) {
                this.level().addParticle(ParticleTypes.CLOUD, this.getX(), this.getY() + 0.3, this.getZ(),
                        (this.random.nextDouble() - 0.5) * 0.2, 0.15, (this.random.nextDouble() - 0.5) * 0.2);
            }
            return;
        }
        if (id != EVENT_SPLASH) {
            super.handleEntityEvent(id);
            return;
        }
        // 熔岩溅开：几滴往外飞的熔岩、一圈火苗、一大团烟
        for (int i = 0; i < 14; i++) {
            double yaw = this.random.nextDouble() * Math.PI * 2;
            double speed = 0.15 + this.random.nextDouble() * 0.25;
            this.level().addParticle(ParticleTypes.FLAME, this.getX(), this.getY() + 0.2, this.getZ(),
                    Math.cos(yaw) * speed, 0.05 + this.random.nextDouble() * 0.2, Math.sin(yaw) * speed);
        }
        for (int i = 0; i < 8; i++) {
            this.level().addParticle(ParticleTypes.LAVA, this.getX(), this.getY() + 0.2, this.getZ(), 0, 0, 0);
        }
        for (int i = 0; i < 10; i++) {
            this.level().addParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, this.getX() + (this.random.nextDouble() - 0.5) * 2,
                    this.getY() + 0.3, this.getZ() + (this.random.nextDouble() - 0.5) * 2, 0, 0.04 + this.random.nextDouble() * 0.03, 0);
        }
        this.level().addParticle(ParticleTypes.EXPLOSION, this.getX(), this.getY() + 0.5, this.getZ(), 0, 0, 0);
    }
}
