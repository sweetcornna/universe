package io.github.sweetcornna.universe.entity;

import io.github.sweetcornna.universe.core.survival.ToolWeapon;
import io.github.sweetcornna.universe.registry.UniverseEntities;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * 等离子发射器的榴弹（ADR-0026 §六）：受重力的发光弹，撞到方块弹回来，{@link ToolWeapon#GRENADE_FUSE} 刻后
 * （或者直接撞上生物时）炸开：半径 3 格内的生物挨伤害、被推开，不破坏方块。
 *
 * <p>伤害记在开火的人头上（等离子伤害），所以炸到守卫机照样算挑衅、炸死动物照样算捕杀。开火的人自己炸不到。
 */
public class PlasmaGrenade extends ThrowableProjectile {
    /** 落在地上滚不动了：速度小于这个就停下。 */
    private static final double REST_SPEED = 0.1;
    /** 炸开时把人推开的速度（格/刻），离爆心越近推得越远。 */
    private static final double BLAST_PUSH = 0.6;

    private double damageBoost = 1;
    private boolean resting;

    public PlasmaGrenade(EntityType<? extends PlasmaGrenade> type, Level level) {
        super(type, level);
    }

    /** 从 {@code from} 朝 {@code direction} 打出一颗（还没加进世界）。 */
    public static PlasmaGrenade launch(Level level, Entity owner, Vec3 from, Vec3 direction, double damageBoost) {
        PlasmaGrenade grenade = new PlasmaGrenade(UniverseEntities.PLASMA_GRENADE.get(), level);
        grenade.setOwner(owner);
        grenade.setPos(from);
        grenade.damageBoost = damageBoost;
        grenade.shoot(direction.x, direction.y, direction.z, (float) ToolWeapon.GRENADE_SPEED, 0);
        return grenade;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder entityData) {
    }

    @Override
    protected double getDefaultGravity() {
        return this.resting ? 0 : 0.05;
    }

    /** 还剩几刻炸开。 */
    public int fuseLeft() {
        return Math.max(0, ToolWeapon.GRENADE_FUSE - this.tickCount);
    }

    @Override
    public void tick() {
        // 停在地上，脚下的方块没了就接着掉
        if (this.resting && this.level().isEmptyBlock(this.blockPosition().below()) && this.level().isEmptyBlock(this.blockPosition())) {
            this.resting = false;
        }
        super.tick();
        if (this.level().isClientSide()) {
            // 越快炸，闪得越快
            int period = this.fuseLeft() > 20 ? 6 : 3;
            if (this.tickCount % period == 0) {
                this.level().addParticle(new DustParticleOptions(ToolWeapon.PLASMA_LAUNCHER.color(), 0.8F), this.getX(), this.getY() + 0.15,
                        this.getZ(), 0, 0, 0);
            }
            return;
        }
        if (this.tickCount >= ToolWeapon.GRENADE_FUSE) {
            this.burst((ServerLevel) this.level());
        }
    }

    @Override
    protected boolean canHitEntity(Entity entity) {
        Entity owner = this.getOwner();
        if (owner != null && entity.getRootVehicle() == owner.getRootVehicle()) {
            return false;
        }
        return super.canHitEntity(entity) && entity instanceof LivingEntity;
    }

    @Override
    protected void onHitEntity(EntityHitResult hitResult) {
        super.onHitEntity(hitResult);
        if (this.level() instanceof ServerLevel level) {
            this.burst(level);
        }
    }

    /** 撞到方块：垂直于那个面的速度反过来，整体剩 {@link ToolWeapon#GRENADE_BOUNCE}；落在地上慢下来就停住。 */
    @Override
    protected void onHitBlock(BlockHitResult hitResult) {
        super.onHitBlock(hitResult);
        Direction face = hitResult.getDirection();
        Vec3 velocity = this.getDeltaMovement();
        Vec3 bounced = switch (face.getAxis()) {
            case X -> new Vec3(-velocity.x, velocity.y, velocity.z);
            case Y -> new Vec3(velocity.x, -velocity.y, velocity.z);
            case Z -> new Vec3(velocity.x, velocity.y, -velocity.z);
        };
        bounced = bounced.scale(ToolWeapon.GRENADE_BOUNCE);
        // 贴着面挪开一点，下一刻不会又撞同一个面
        this.setPos(hitResult.getLocation().add(face.getUnitVec3().scale(0.02)));
        if (face == Direction.UP && bounced.lengthSqr() < REST_SPEED * REST_SPEED) {
            this.resting = true;
            this.setDeltaMovement(Vec3.ZERO);
        } else {
            this.setDeltaMovement(bounced);
        }
        this.needsSync = true;
        if (!this.level().isClientSide() && velocity.lengthSqr() > 0.04) {
            this.playSound(SoundEvents.COPPER_HIT, 0.6F, 1.6F);
        }
    }

    private void burst(ServerLevel level) {
        if (this.isRemoved()) {
            return;
        }
        Entity owner = this.getOwner();
        Vec3 center = this.position().add(0, 0.15, 0);
        double radius = ToolWeapon.PLASMA_LAUNCHER.range();
        DamageSource source = this.damageSources().source(PlasmaBolt.DAMAGE, this, owner);
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(radius + 1))) {
            if (target == owner || !target.isAlive() || target.isSpectator()) {
                continue;
            }
            // 量到身体最近的地方：大个子的守卫机边上炸也算炸到
            double distance = Math.sqrt(target.getBoundingBox().distanceToSqr(center));
            float damage = ToolWeapon.grenadeDamage(distance, this.damageBoost);
            if (damage <= 0) {
                continue;
            }
            PlasmaBolt.clearHurtCooldown(target);
            if (target.hurtServer(level, source, damage)) {
                if (owner instanceof LivingEntity shooter) {
                    shooter.setLastHurtMob(target);
                }
                double resistance = target.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
                Vec3 away = target.position().subtract(center).multiply(1, 0, 1);
                if (resistance < 1 && away.lengthSqr() > 1e-6) {
                    double strength = BLAST_PUSH * (1 - distance / radius) * (1 - resistance);
                    target.push(away.normalize().scale(strength).add(0, strength * 0.5, 0));
                }
            }
        }
        level.sendParticles(ParticleTypes.EXPLOSION, center.x, center.y, center.z, 1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, center.x, center.y, center.z, 24, 0.6, 0.6, 0.6, 0.3);
        DustParticleOptions glow = new DustParticleOptions(ToolWeapon.PLASMA_LAUNCHER.color(), 1.6F);
        for (int i = 0; i < 24; i++) {
            double angle = i * Math.PI * 2 / 24;
            level.sendParticles(glow, center.x + Math.cos(angle) * radius * 0.8, center.y + 0.2, center.z + Math.sin(angle) * radius * 0.8,
                    1, 0, 0, 0, 0);
        }
        level.playSound(null, center.x, center.y, center.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.9F, 1.5F);
        this.discard();
    }
}
