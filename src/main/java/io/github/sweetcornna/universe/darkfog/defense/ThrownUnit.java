package io.github.sweetcornna.universe.darkfog.defense;

import io.github.sweetcornna.universe.core.darkfog.Support;
import io.github.sweetcornna.universe.darkfog.DarkFogEnemy;
import io.github.sweetcornna.universe.entity.PlasmaBolt;
import io.github.sweetcornna.universe.mecha.combat.MechaCombat;
import io.github.sweetcornna.universe.registry.UniverseEntities;
import io.github.sweetcornna.universe.registry.UniverseItems;
import io.github.sweetcornna.universe.registry.UniverseSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * 扔出去的燃烧单元、爆破单元、晶石爆破单元（ADR-0028 §五）：撞到方块或生物就炸开，半径里的生物挨伤害
 * （{@link Support.Charge}），燃烧单元还让它们着火。不破坏方块，扔的人自己炸不到。
 *
 * <p>机甲扔的干扰胶囊和水瓶（ADR-0033 §二）也是它：干扰胶囊让附近的黑雾像在干扰塔边上一样变慢，水瓶灭附近的火。
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
        ItemStack item = this.getItem();
        if (item.is(UniverseItems.JAMMING_CAPSULE.get())) {
            this.jam(level, center);
            this.discard();
            return;
        }
        if (MechaCombat.water(item)) {
            this.splash(level, center, item);
            this.discard();
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
            level.playSound(null, center.x, center.y, center.z, UniverseSounds.EXPLOSION_SMALL.get(), SoundSource.PLAYERS, 1.0F,
                    charge == Support.Charge.CRYSTAL ? 0.85F : 1.05F);
        }
        this.discard();
    }

    /** 干扰胶囊：半径里的黑雾按干扰塔的胶囊减速一阵子。 */
    private void jam(ServerLevel level, Vec3 center) {
        double radius = MechaCombat.JAM_RADIUS;
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(radius + 1),
                entity -> entity instanceof DarkFogEnemy && entity.isAlive())) {
            if (target.getBoundingBox().distanceToSqr(center) <= radius * radius) {
                ((DarkFogEnemy) target).jam(MechaCombat.JAM_TICKS, Support.CAPSULE.factor());
            }
        }
        DustParticleOptions violet = new DustParticleOptions(0xB48CFF, 1.2F);
        for (int i = 0; i < 48; i++) {
            double a = Math.PI * 2 * i / 48;
            level.sendParticles(violet, center.x + Math.cos(a) * radius, center.y + 0.3, center.z + Math.sin(a) * radius, 1, 0, 0.2, 0, 0);
        }
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, center.x, center.y + 0.3, center.z, 40, radius * 0.4, 0.5, radius * 0.4, 0.3);
        level.playSound(null, center.x, center.y, center.z, SoundEvents.SPLASH_POTION_BREAK, SoundSource.PLAYERS, 0.8F, 1.4F);
        level.playSound(null, center.x, center.y, center.z, SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.7F, 1.8F);
    }

    /** 水瓶：灭掉半径里的火和身上的火，像喷溅型水瓶那样溅开。 */
    private void splash(ServerLevel level, Vec3 center, ItemStack item) {
        double radius = MechaCombat.WATER_RADIUS;
        int r = (int) Math.ceil(radius);
        BlockPos origin = BlockPos.containing(center);
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-r, -r, -r), origin.offset(r, r, r))) {
            if (pos.distToCenterSqr(center) <= radius * radius && level.getBlockState(pos).is(BlockTags.FIRE)) {
                level.destroyBlock(pos, false, this);
            }
        }
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(radius + 1),
                LivingEntity::isOnFire)) {
            if (target.getBoundingBox().distanceToSqr(center) <= radius * radius) {
                target.extinguishFire();
            }
        }
        int color = item.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY).getColor();
        level.levelEvent(2002, BlockPos.containing(center), color);
        level.levelEvent(1053, BlockPos.containing(center), 0);
    }
}
