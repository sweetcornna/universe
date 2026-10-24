package io.github.sweetcornna.universe.darkfog.entity;

import io.github.sweetcornna.universe.core.darkfog.Tactics;
import io.github.sweetcornna.universe.darkfog.Facilities;
import io.github.sweetcornna.universe.entity.PlasmaBolt;
import io.github.sweetcornna.universe.registry.UniverseEntities;
import io.github.sweetcornna.universe.registry.UniverseSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * 游骑兵、守卫者的激光弹（ADR-0028 §二）：直线飞、不受重力，打中玩家按黑雾伤害算，打中设施扣设施的耐久。
 * 不打黑雾自己的单位和建筑，也不打守卫机。
 */
public class DarkFogBolt extends ThrowableProjectile {
    public static final int RANGER_COLOR = 0xFF2A5A;
    public static final int GUARDIAN_COLOR = 0xD02AFF;
    private static final EntityDataAccessor<Integer> DATA_COLOR = SynchedEntityData.defineId(DarkFogBolt.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DATA_SIZE = SynchedEntityData.defineId(DarkFogBolt.class, EntityDataSerializers.FLOAT);
    private static final byte EVENT_IMPACT = EntityEvent.DEATH;
    private static final int LIFETIME = 40;

    private float damage = 3;
    private int lifetime = LIFETIME;

    public DarkFogBolt(EntityType<? extends DarkFogBolt> type, Level level) {
        super(type, level);
    }

    /** 从 {@code from} 朝 {@code direction} 打出一发，已经加进世界。 */
    public static DarkFogBolt fire(ServerLevel level, Entity owner, Vec3 from, Vec3 direction, float damage, int color, float size, double speed,
            float inaccuracy) {
        return fire(level, owner, from, direction, damage, color, size, speed, inaccuracy, LIFETIME);
    }

    /** 同上，飞 {@code lifetime} 刻才消失（太空里射程远）。 */
    public static DarkFogBolt fire(ServerLevel level, Entity owner, Vec3 from, Vec3 direction, float damage, int color, float size, double speed,
            float inaccuracy, int lifetime) {
        DarkFogBolt bolt = new DarkFogBolt(UniverseEntities.DARK_FOG_BOLT.get(), level);
        bolt.setOwner(owner);
        bolt.setPos(from);
        bolt.damage = damage;
        bolt.lifetime = lifetime;
        bolt.entityData.set(DATA_COLOR, color);
        bolt.entityData.set(DATA_SIZE, size);
        bolt.shoot(direction.x, direction.y, direction.z, (float) speed, inaccuracy);
        level.addFreshEntity(bolt);
        return bolt;
    }

    public int color() {
        return this.entityData.get(DATA_COLOR);
    }

    public float size() {
        return this.entityData.get(DATA_SIZE);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_COLOR, RANGER_COLOR);
        builder.define(DATA_SIZE, 0.6F);
    }

    @Override
    protected double getDefaultGravity() {
        return 0;
    }

    @Override
    protected float getAirDrag() {
        return 1;
    }

    @Override
    public void tick() {
        Vec3 before = this.position();
        super.tick();
        if (this.level().isClientSide()) {
            Vec3 now = this.position();
            double length = before.distanceTo(now);
            if (length > 1e-3 && this.tickCount > 1) {
                DustParticleOptions dust = new DustParticleOptions(this.color(), 0.6F);
                int steps = Math.max(1, (int) (length / 0.6));
                for (int i = 0; i < steps; i++) {
                    Vec3 p = before.lerp(now, i / (double) steps);
                    this.level().addParticle(dust, p.x, p.y, p.z, 0, 0, 0);
                }
            }
            return;
        }
        if (this.isInWater() || this.tickCount > this.lifetime) {
            this.discard();
        }
    }

    @Override
    protected boolean canHitEntity(Entity entity) {
        return super.canHitEntity(entity) && !DarkFogDamage.spared(entity);
    }

    @Override
    protected void onHitEntity(EntityHitResult hitResult) {
        super.onHitEntity(hitResult);
        if (this.level() instanceof ServerLevel level) {
            Entity target = hitResult.getEntity();
            PlasmaBolt.clearHurtCooldown(target);
            target.hurtServer(level, DarkFogDamage.source(level, this, this.getOwner()), this.damage);
            level.playSound(null, target.getX(), target.getY(0.5), target.getZ(), UniverseSounds.IMPACT_ENERGY.get(), SoundSource.HOSTILE, 0.6F, 1.0F);
        }
    }

    @Override
    protected void onHitBlock(BlockHitResult hitResult) {
        super.onHitBlock(hitResult);
        if (this.level() instanceof ServerLevel level) {
            BlockPos pos = hitResult.getBlockPos();
            if (Facilities.isFacility(level.getBlockState(pos))) {
                Facilities.damage(level, pos, (float) (this.damage * Tactics.FACILITY_DAMAGE), this.getOwner());
            }
        }
    }

    @Override
    protected void onHit(HitResult hitResult) {
        super.onHit(hitResult);
        if (!this.level().isClientSide()) {
            this.level().broadcastEntityEvent(this, EVENT_IMPACT);
            this.discard();
        }
    }

    @Override
    public void handleEntityEvent(@EntityEvent.Value byte id) {
        if (id != EVENT_IMPACT) {
            super.handleEntityEvent(id);
            return;
        }
        for (int i = 0; i < 4; i++) {
            this.level().addParticle(ParticleTypes.ELECTRIC_SPARK, this.getX(), this.getY(), this.getZ(),
                    (this.random.nextDouble() - 0.5) * 0.3, (this.random.nextDouble() - 0.5) * 0.3, (this.random.nextDouble() - 0.5) * 0.3);
        }
        this.level().addParticle(new DustParticleOptions(this.color(), 1.0F), this.getX(), this.getY(), this.getZ(), 0, 0, 0);
    }
}
