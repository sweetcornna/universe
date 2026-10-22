package io.github.sweetcornna.universe.exocraft;

import io.github.sweetcornna.universe.registry.UniverseEntities;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * 潜艇的鱼雷（ADR-0026）：水里直线跑，碰到东西或者跑满 {@link #LIFETIME} 刻就炸。只伤生物，不炸方块；伤不到自己的潜艇和艇里的人。
 * 出了水推进器就没用了，往下掉。
 */
public class Torpedo extends Projectile {
    /** 每刻跑几格（22 格/秒）。 */
    public static final double SPEED = 1.1;
    /** 跑多少刻（射程 66 格）。 */
    public static final int LIFETIME = 60;
    /** 炸点中心的伤害，往外线性减到一半。 */
    public static final float DAMAGE = 12;
    /** 爆炸半径（格）。 */
    public static final double RADIUS = 3;

    public Torpedo(EntityType<? extends Torpedo> type, Level level) {
        super(type, level);
    }

    /** 从 {@code from} 朝 {@code direction} 打出一发（还没加进世界）。 */
    public static Torpedo launch(Level level, Entity owner, Vec3 from, Vec3 direction) {
        Torpedo torpedo = new Torpedo(UniverseEntities.TORPEDO.get(), level);
        torpedo.setOwner(owner);
        torpedo.setPos(from);
        torpedo.shoot(direction.x, direction.y, direction.z, (float) SPEED, 0);
        return torpedo;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
    }

    @Override
    public void tick() {
        if (!this.isInWater()) {
            // 出了水：推进器空转，往下掉
            this.setDeltaMovement(this.getDeltaMovement().scale(0.96).add(0, -0.06, 0));
        }
        HitResult result = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
        boolean hit = result.getType() != HitResult.Type.MISS && !net.neoforged.neoforge.event.EventHooks.onProjectileImpact(this, result);
        Vec3 before = this.position();
        this.setPos(hit ? result.getLocation() : before.add(this.getDeltaMovement()));
        this.updateRotation();
        super.tick();
        if (this.level().isClientSide()) {
            this.trail(before);
            return;
        }
        if (hit && this.isAlive()) {
            this.hitTargetOrDeflectSelf(result);
        } else if (this.tickCount > LIFETIME) {
            this.detonate();
        }
    }

    /** 客户端：身后一串气泡。 */
    private void trail(Vec3 before) {
        Vec3 now = this.position();
        if (!this.isInWater() || this.tickCount < 2) {
            return;
        }
        for (int i = 0; i < 3; i++) {
            Vec3 p = before.lerp(now, i / 3.0);
            this.level().addParticle(ParticleTypes.BUBBLE, p.x, p.y, p.z, 0, 0.02, 0);
        }
    }

    @Override
    protected boolean canHitEntity(Entity entity) {
        Entity owner = this.getOwner();
        if (owner != null && entity.getRootVehicle() == owner.getRootVehicle()) {
            return false;
        }
        return super.canHitEntity(entity);
    }

    @Override
    protected void onHit(HitResult hitResult) {
        super.onHit(hitResult);
        if (!this.level().isClientSide()) {
            this.detonate();
        }
    }

    /** 服务端：在这里炸开，伤到半径里的生物（自己的潜艇和艇里的人除外）。 */
    private void detonate() {
        if (!(this.level() instanceof ServerLevel level) || this.isRemoved()) {
            return;
        }
        Vec3 at = this.position();
        Entity owner = this.getOwner();
        DamageSource source = this.damageSources().explosion(this, owner);
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(RADIUS))) {
            if (owner != null && target.getRootVehicle() == owner.getRootVehicle()) {
                continue;
            }
            double distance = target.getBoundingBox().getCenter().distanceTo(at);
            if (distance > RADIUS + target.getBbWidth() / 2) {
                continue;
            }
            float share = (float) (1 - 0.5 * Math.min(1, distance / RADIUS));
            if (target.hurtServer(level, source, DAMAGE * share) && owner instanceof LivingEntity shooter) {
                shooter.setLastHurtMob(target);
            }
        }
        level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y, at.z, 2, 0.4, 0.4, 0.4, 0);
        level.sendParticles(this.isInWater() ? ParticleTypes.BUBBLE : ParticleTypes.SMOKE, at.x, at.y, at.z, 40, 1.0, 1.0, 1.0, 0.15);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 1.2F, this.isInWater() ? 0.6F : 1.0F);
        this.discard();
    }

    @Override
    public boolean isPickable() {
        return false;
    }
}
