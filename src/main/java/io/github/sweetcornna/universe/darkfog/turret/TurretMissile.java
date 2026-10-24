package io.github.sweetcornna.universe.darkfog.turret;

import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.core.darkfog.AmmoKind;
import io.github.sweetcornna.universe.darkfog.DarkFogEnemy;
import io.github.sweetcornna.universe.registry.UniverseEntities;
import io.github.sweetcornna.universe.registry.UniverseSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * 导弹（ADR-0028 §五）：先往上蹿一小段，再拐弯追目标，离目标一格以内或撞上东西就炸。目标死了就找附近别的黑雾，
 * 找不到就直飞，飞够时间自爆。范围伤害只伤黑雾、不破坏方块。不存档。
 */
public class TurretMissile extends ThrowableProjectile {
    private static final EntityDataAccessor<Integer> DATA_AMMO = SynchedEntityData.defineId(TurretMissile.class, EntityDataSerializers.INT);
    /** 发射后先直着往上飞几刻。 */
    private static final int BOOST = 6;
    private static final int LIFETIME = 140;
    /** 每刻最多把速度方向往目标拧多少（0–1）。 */
    private static final double TURN = 0.3;
    /** 离目标碰撞箱多近就炸。 */
    private static final double FUSE = 1.0;
    /** 目标没了，在这么远内找别的黑雾。 */
    private static final double RETARGET = 16;

    private @Nullable BlockPos turret;
    private int targetId = -1;

    public TurretMissile(EntityType<? extends TurretMissile> type, Level level) {
        super(type, level);
    }

    /** 从 {@code from} 打向 {@code target}（还没加进世界）。 */
    public static TurretMissile launch(ServerLevel level, BlockPos turret, Vec3 from, Entity target, AmmoKind ammo) {
        TurretMissile missile = new TurretMissile(UniverseEntities.TURRET_MISSILE.get(), level);
        missile.setPos(from);
        missile.turret = turret.immutable();
        missile.targetId = target.getId();
        missile.entityData.set(DATA_AMMO, ammo.ordinal());
        Vec3 toward = TurretBlockEntity.aimPoint(target).subtract(from).multiply(1, 0, 1);
        Vec3 sideways = toward.lengthSqr() < 1e-6 ? Vec3.ZERO : toward.normalize().scale(0.25);
        missile.setDeltaMovement(new Vec3(sideways.x, 1, sideways.z).normalize().scale(ammo.speed() * 0.7));
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
        if (this.tickCount > LIFETIME) {
            this.burst((ServerLevel) this.level());
        }
    }

    /** 拐向目标；目标没了就找附近别的。 */
    private void steer(ServerLevel level) {
        if (this.tickCount < BOOST) {
            return;
        }
        Entity target = this.targetId < 0 ? null : level.getEntity(this.targetId);
        if (target == null || !target.isAlive()) {
            target = null;
            this.targetId = -1;
            double best = Double.MAX_VALUE;
            for (Mob mob : TurretBlockEntity.enemies(level, this.getBoundingBox().inflate(RETARGET))) {
                double distance = mob.distanceToSqr(this);
                if (distance < best) {
                    best = distance;
                    target = mob;
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
        Vec3 want = TurretBlockEntity.aimPoint(target).subtract(this.position()).normalize().scale(speed);
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
        return entity instanceof DarkFogEnemy && super.canHitEntity(entity);
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
        int killed = Turrets.splash(level, center, ammo, this);
        Turrets.creditKills(level, this.turret, killed);
        level.sendParticles(ParticleTypes.EXPLOSION, center.x, center.y, center.z, 1, 0, 0, 0, 0);
        if (ammo == AmmoKind.GRAVITY_MISSILE) {
            level.sendParticles(ParticleTypes.REVERSE_PORTAL, center.x, center.y, center.z, 60, ammo.radius() * 0.5, 0.6, ammo.radius() * 0.5, 0.6);
        } else {
            level.sendParticles(ParticleTypes.FLAME, center.x, center.y, center.z, 16, 0.5, 0.4, 0.5, 0.08);
        }
        level.sendParticles(ParticleTypes.LARGE_SMOKE, center.x, center.y, center.z, 8, 0.6, 0.4, 0.6, 0.02);
        level.playSound(null, center.x, center.y, center.z, UniverseSounds.EXPLOSION_SMALL.get(), SoundSource.BLOCKS, 1.0F, 1.1F);
        this.discard();
    }
}
