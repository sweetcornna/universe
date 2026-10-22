package io.github.sweetcornna.universe.darkfog.defense;

import io.github.sweetcornna.universe.core.darkfog.Support;
import io.github.sweetcornna.universe.entity.PlasmaBolt;
import io.github.sweetcornna.universe.registry.UniverseEntities;
import io.github.sweetcornna.universe.registry.UniverseItems;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * 扔出去的燃烧单元、爆破单元、晶石爆破单元（ADR-0028 §五）：撞到方块或生物就炸开，半径里的生物挨伤害
 * （{@link Support.Charge}），燃烧单元还让它们着火。不破坏方块，扔的人自己炸不到。
 */
public class ThrownUnit extends ThrowableItemProjectile {
    public ThrownUnit(EntityType<? extends ThrownUnit> type, Level level) {
        super(type, level);
    }

    /** 从扔的人手里扔出去（还没加进世界）。 */
    public static ThrownUnit throwFrom(Level level, LivingEntity owner, ItemStack stack) {
        ThrownUnit unit = new ThrownUnit(UniverseEntities.THROWN_UNIT.get(), level);
        unit.setOwner(owner);
        unit.setPos(owner.getX(), owner.getEyeY() - 0.1, owner.getZ());
        unit.setItem(stack);
        unit.shootFromRotation(owner, owner.getXRot(), owner.getYRot(), 0, Support.THROW_SPEED, 1);
        return unit;
    }

    @Override
    protected Item getDefaultItem() {
        return UniverseItems.EXPLOSIVE_UNIT.get();
    }

    /** 这是哪种单元，看扔出来的物品。 */
    public Support.Charge charge() {
        return chargeOf(this.getItem());
    }

    public static Support.Charge chargeOf(ItemStack stack) {
        if (stack.is(UniverseItems.COMBUSTIBLE_UNIT.get())) {
            return Support.Charge.COMBUSTIBLE;
        }
        if (stack.is(UniverseItems.CRYSTAL_EXPLOSIVE_UNIT.get())) {
            return Support.Charge.CRYSTAL;
        }
        return Support.Charge.EXPLOSIVE;
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
        if (this.level() instanceof ServerLevel level) {
            this.burst(level, hitResult.getLocation());
        }
    }

    /** 炸开。 */
    public void burst(ServerLevel level, Vec3 center) {
        if (this.isRemoved()) {
            return;
        }
        Support.Charge charge = this.charge();
        Entity owner = this.getOwner();
        DamageSource source = this.damageSources().explosion(this, owner);
        double radius = charge.radius();
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(radius + 1))) {
            if (target == owner || !target.isAlive() || target.isSpectator()) {
                continue;
            }
            // 量到身体最近的地方：大个子的建筑边上炸也算炸到
            double distance = Math.sqrt(target.getBoundingBox().distanceToSqr(center));
            float damage = charge.damage(distance);
            if (damage <= 0) {
                continue;
            }
            PlasmaBolt.clearHurtCooldown(target);
            if (target.hurtServer(level, source, damage) && charge.fireTicks() > 0) {
                target.igniteForTicks(charge.fireTicks());
            }
        }
        if (charge == Support.Charge.COMBUSTIBLE) {
            level.sendParticles(ParticleTypes.FLAME, center.x, center.y + 0.2, center.z, 40, radius * 0.4, 0.3, radius * 0.4, 0.05);
            level.sendParticles(ParticleTypes.LARGE_SMOKE, center.x, center.y + 0.2, center.z, 10, 0.6, 0.3, 0.6, 0.02);
            level.playSound(null, center.x, center.y, center.z, SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 1.0F, 0.8F);
        } else {
            level.sendParticles(charge == Support.Charge.CRYSTAL ? ParticleTypes.EXPLOSION_EMITTER : ParticleTypes.EXPLOSION,
                    center.x, center.y, center.z, 1, 0, 0, 0, 0);
            if (charge == Support.Charge.CRYSTAL) {
                level.sendParticles(ParticleTypes.END_ROD, center.x, center.y, center.z, 30, radius * 0.3, radius * 0.3, radius * 0.3, 0.2);
            }
            level.playSound(null, center.x, center.y, center.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 1.2F,
                    charge == Support.Charge.CRYSTAL ? 0.8F : 1.1F);
        }
        this.discard();
    }
}
