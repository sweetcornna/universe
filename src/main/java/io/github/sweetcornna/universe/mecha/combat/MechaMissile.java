package io.github.sweetcornna.universe.mecha.combat;

import io.github.sweetcornna.universe.core.darkfog.AmmoKind;
import io.github.sweetcornna.universe.darkfog.DarkFogEnemy;
import io.github.sweetcornna.universe.registry.UniverseEntities;
import io.github.sweetcornna.universe.registry.UniverseSounds;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * 机甲导弹舱打出的导弹（ADR-0033 §二）：先往上蹿一小段，再拐弯追目标，离目标一格以内或撞上东西就炸；目标死了找附近别的
 * 敌人，找不到就直飞，飞够时间自爆。范围伤害只伤黑雾，直接命中的怪物也挨一下。来源记在机甲的主人头上。不存档。
 */
public class MechaMissile extends ThrowableProjectile {
    private static final EntityDataAccessor<Integer> DATA_AMMO = SynchedEntityData.defineId(MechaMissile.class, EntityDataSerializers.INT);
    private static final int BOOST = 5;
    private static final int LIFETIME = 120;
    private static final double TURN = 0.35;
    private static final double FUSE = 1.0;
    private static final double RETARGET = 16;

    private int targetId = -1;
    private float damage;

    public MechaMissile(EntityType<? extends MechaMissile> type, Level level) {
        super(type, level);
    }

    /** 从 {@code from} 打向 {@code target}（已经加进世界）。 */
    public static MechaMissile fire(ServerLevel level, Player owner, Vec3 from, LivingEntity target, AmmoKind ammo, float damage) {
        MechaMissile missile = new MechaMissile(UniverseEntities.MECHA_MISSILE.get(), level);
        missile.setPos(from);
        missile.setOwner(owner);
        missile.targetId = target.getId();
        missile.damage = damage;
        missile.entityData.set(DATA_AMMO, ammo.ordinal());
        Vec3 toward = Strikes.aimPoint(target).subtract(from).multiply(1, 0, 1);
        Vec3 sideways = toward.lengthSqr() < 1e-6 ? Vec3.ZERO : toward.normalize().scale(0.35);
        missile.setDeltaMovement(new Vec3(sideways.x, 1, sideways.z).normalize().scale(ammo.speed() * 0.7));
        level.addFreshEntity(missile);
        return missile;
    }

    public AmmoKind ammo() {
        AmmoKind[] kinds = AmmoKind.values();
        return kinds[Math.floorMod(this.entityData.get(DATA_AMMO), kinds.length)];
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_AMMO, AmmoKind.MISSILE.ordinal());
    }

    @Override
    protected double getDefaultGravity() {
        return 0;
    }

    @Override
    protected float getAirDrag() {
        return 1.0F;
    }

    @Override
    public void tick() {
        if (this.level() instanceof ServerLevel level) {
            this.steer(level);
        }
        super.tick();
        if (this.level().isClientSide()) {
            Vec3 back = this.position().subtract(this.getDeltaMovement().scale(0.6));
            this.level().addParticle(ParticleTypes.FLAME, back.x, back.y, back.z, 0, 0, 0);
            this.level().addParticle(ParticleTypes.SMOKE, back.x, back.y, back.z, 0, 0.01, 0);
            if (this.ammo() == AmmoKind.GRAVITY_MISSILE) {
                this.level().addParticle(ParticleTypes.REVERSE_PORTAL, back.x, back.y, back.z, 0, 0, 0);
            }
            return;
        }
        if (this.tickCount > LIFETIME && this.level() instanceof ServerLevel level) {
            this.burst(level);
        }
    }

    private void steer(ServerLevel level) {
        if (this.tickCount < BOOST || this.isRemoved()) {
            return;
        }
        Entity target = this.targetId < 0 ? null : level.getEntity(this.targetId);
        Player owner = this.getOwner() instanceof Player player ? player : null;
        if (target == null || !target.isAlive()) {
            target = null;
            this.targetId = -1;
            double best = Double.MAX_VALUE;
            for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(RETARGET),
                    entity -> Strikes.hostile(entity, owner))) {
                double distance = living.distanceToSqr(this);
                if (distance < best) {
                    best = distance;
                    target = living;
                }
            }
            if (target != null) {
                this.targetId = target.getId();
            }
        }
        if (target == null) {
            return;
        }
        if (Math.sqrt(target.getBoundingBox().distanceToSqr(this.position())) <= FUSE) {
            this.burst(level);
            return;
        }
        double speed = this.ammo().speed();
        Vec3 want = Strikes.aimPoint(target).subtract(this.position()).normalize().scale(speed);
        Vec3 velocity = this.getDeltaMovement();
        Vec3 steered = velocity.add(want.subtract(velocity).scale(TURN));
        if (steered.lengthSqr() < 1e-6) {
            steered = want;
        }
        this.setDeltaMovement(steered.normalize().scale(speed));
        this.needsSync = true;
    }

    @Override
    protected boolean canHitEntity(Entity entity) {
        return Strikes.hostile(entity, this.getOwner() instanceof Player player ? player : null) && super.canHitEntity(entity);
    }

    @Override
    protected void onHitEntity(EntityHitResult hitResult) {
        super.onHitEntity(hitResult);
        if (this.level() instanceof ServerLevel level) {
            this.burst(level);
        }
    }

    @Override
    protected void onHitBlock(BlockHitResult hitResult) {
        super.onHitBlock(hitResult);
        if (this.level() instanceof ServerLevel level) {
            this.setPos(hitResult.getLocation().add(hitResult.getDirection().getUnitVec3().scale(0.2)));
            this.burst(level);
        }
    }

    private void burst(ServerLevel level) {
        if (this.isRemoved()) {
            return;
        }
        AmmoKind ammo = this.ammo();
        Vec3 center = this.position();
        Strikes.splash(level, center, this.damage, ammo.radius(), ammo.push(), this, this.getOwner());
        // 不是黑雾的怪物不吃范围伤害，正中的那个挨一下
        Player owner = this.getOwner() instanceof Player player ? player : null;
        for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(1.5),
                entity -> !(entity instanceof DarkFogEnemy) && Strikes.hostile(entity, owner))) {
            Strikes.hit(level, living, this.damage, this, owner);
        }
        level.sendParticles(ParticleTypes.EXPLOSION, center.x, center.y, center.z, 1, 0, 0, 0, 0);
        if (ammo == AmmoKind.GRAVITY_MISSILE) {
            level.sendParticles(ParticleTypes.REVERSE_PORTAL, center.x, center.y, center.z, 50, ammo.radius() * 0.5, 0.6, ammo.radius() * 0.5, 0.6);
        } else {
            level.sendParticles(ParticleTypes.FLAME, center.x, center.y, center.z, 12, 0.5, 0.4, 0.5, 0.08);
        }
        level.sendParticles(ParticleTypes.LARGE_SMOKE, center.x, center.y, center.z, 6, 0.6, 0.4, 0.6, 0.02);
        level.playSound(null, center.x, center.y, center.z, UniverseSounds.EXPLOSION_SMALL.get(), SoundSource.PLAYERS, 0.9F, 1.15F);
        this.discard();
    }
}
