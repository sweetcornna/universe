package io.github.sweetcornna.universe.entity;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.core.survival.PlasmaWeapon;
import io.github.sweetcornna.universe.encounter.SpaceEncounters;
import io.github.sweetcornna.universe.pirate.PirateFighter;
import io.github.sweetcornna.universe.registry.UniverseEntities;
import io.github.sweetcornna.universe.sentinel.Sentinel;
import io.github.sweetcornna.universe.registry.UniverseSounds;
import io.github.sweetcornna.universe.registry.sound.GearSounds;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageScaling;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * 等离子弹（ADR-0014）：多功能工具战斗模式打出的发光弹体，直线飞行、不受重力，飞 {@link PlasmaWeapon#LIFETIME} 刻后消失。
 * 打中生物造成等离子伤害（无视受伤后的无敌时间，否则每秒 4 发大半会被吞掉），打中方块溅火花，进水熄灭。
 *
 * <p>守卫机的弹体也是这个实体：颜色（同步给客户端）、速度、射程不同，守卫机之间不会互相打中。
 */
public class PlasmaBolt extends ThrowableProjectile {
    public static final ResourceKey<DamageType> DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE, Universe.id("plasma"));
    /** 多功能工具的等离子弹：青白色。 */
    public static final int PLAYER_COLOR = 0x6FE3FF;
    private static final EntityDataAccessor<Integer> DATA_COLOR = SynchedEntityData.defineId(PlasmaBolt.class, EntityDataSerializers.INT);
    private static final byte EVENT_IMPACT = EntityEvent.DEATH;
    /** 离开火者的眼睛这么近时不撒拖尾、少撒落点火花。 */
    private static final double TRAIL_CLEARANCE = 3.0;
    /** 命中时把目标水平推开的速度（格/刻）。 */
    private static final double KNOCKBACK = 0.15;

    private float damage = (float) PlasmaWeapon.BASE_DAMAGE;
    private int lifetime = PlasmaWeapon.LIFETIME;

    public PlasmaBolt(EntityType<? extends PlasmaBolt> type, Level level) {
        super(type, level);
    }

    /** 多功能工具：从 {@code from} 朝 {@code direction} 打出一发（还没加进世界）。 */
    public static PlasmaBolt shoot(Level level, LivingEntity owner, Vec3 from, Vec3 direction, float damage, int color) {
        return shoot(level, owner, from, direction, damage, color, PlasmaWeapon.SPEED, 0, PlasmaWeapon.LIFETIME);
    }

    /**
     * 打出一发（还没加进世界）。
     *
     * @param speed      每刻飞几格
     * @param inaccuracy 散布，和原版弹射物的含义一样
     * @param lifetime   飞多少刻后消失
     */
    public static PlasmaBolt shoot(Level level, Entity owner, Vec3 from, Vec3 direction, float damage, int color, double speed,
            float inaccuracy, int lifetime) {
        PlasmaBolt bolt = new PlasmaBolt(UniverseEntities.PLASMA_BOLT.get(), level);
        bolt.setOwner(owner);
        bolt.setPos(from);
        bolt.damage = damage;
        bolt.lifetime = lifetime;
        bolt.entityData.set(DATA_COLOR, color);
        bolt.shoot(direction.x, direction.y, direction.z, (float) speed, inaccuracy);
        return bolt;
    }

    public static void bootstrapDamage(BootstrapContext<DamageType> context) {
        context.register(DAMAGE, new DamageType("universe.plasma", DamageScaling.WHEN_CAUSED_BY_LIVING_NON_PLAYER, 0.1F));
    }

    public int color() {
        return this.entityData.get(DATA_COLOR);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder entityData) {
        entityData.define(DATA_COLOR, PLAYER_COLOR);
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
            this.trail(before);
            return;
        }
        if (this.isInWater()) {
            this.playSound(GearSounds.BOLT_FIZZLE.get(), 0.6F, 0.95F + this.random.nextFloat() * 0.1F);
            this.level().broadcastEntityEvent(this, EVENT_IMPACT);
            this.discard();
        } else if (this.tickCount > this.lifetime) {
            this.discard();
        }
    }

    /** 客户端：沿着这一刻飞过的路径撒一串光点，看起来像一道拖尾。 */
    private void trail(Vec3 before) {
        Vec3 now = this.position();
        double length = before.distanceTo(now);
        if (length < 1e-3 || this.tickCount < 2) {
            return;
        }
        DustParticleOptions dust = new DustParticleOptions(this.color(), 0.7F);
        // 开火的人眼前几格不撒：离镜头太近的光点会糊成一大片
        Entity owner = this.getOwner();
        Vec3 eye = owner == null ? null : owner.getEyePosition();
        int steps = Math.max(1, (int) (length / 0.5));
        for (int i = 0; i < steps; i++) {
            Vec3 p = before.lerp(now, i / (double) steps);
            if (eye == null || p.distanceToSqr(eye) > TRAIL_CLEARANCE * TRAIL_CLEARANCE) {
                this.level().addParticle(dust, p.x, p.y, p.z, 0, 0, 0);
            }
        }
    }

    @Override
    protected boolean canHitEntity(Entity entity) {
        Entity owner = this.getOwner();
        // 飞船的炮打不中自己的船和船上的人（ADR-0017）
        if (owner != null && owner.isPassenger() && entity.getRootVehicle() == owner.getRootVehicle()) {
            return false;
        }
        return super.canHitEntity(entity) && !Sentinel.friendly(owner, entity) && !(owner instanceof PirateFighter && entity instanceof PirateFighter);
    }

    @Override
    protected void onHitEntity(EntityHitResult hitResult) {
        super.onHitEntity(hitResult);
        if (!(this.level() instanceof ServerLevel level)) {
            return;
        }
        Entity target = hitResult.getEntity();
        Entity owner = this.getOwner();
        DamageSource source = this.damageSources().source(DAMAGE, this, owner);
        clearHurtCooldown(target);
        if (target.hurtServer(level, source, this.damage)) {
            if (owner instanceof LivingEntity shooter) {
                shooter.setLastHurtMob(target);
            }
            // 顺着弹道水平推一小下，不往上抛（伤害类型带 no_knockback，原版击退不生效）
            double resistance = target instanceof LivingEntity living ? living.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE) : 0;
            Vec3 push = this.getDeltaMovement().multiply(1, 0, 1);
            if (resistance < 1 && push.lengthSqr() > 1e-6) {
                target.push(push.normalize().scale(KNOCKBACK * (1 - resistance)));
            }
        }
        level.playSound(null, target.getX(), target.getY(0.5), target.getZ(), UniverseSounds.IMPACT_ENERGY,
                owner instanceof Sentinel || owner instanceof PirateFighter ? SoundSource.HOSTILE : SoundSource.PLAYERS, 0.8F,
                0.9F + this.random.nextFloat() * 0.2F);
    }

    /**
     * 连发的弹丸互不挡：打之前清掉目标的受伤冷却。26.x 的生物看的是 {@code damageCooldownTime}，
     * 只清 {@code invulnerableTime} 的话，冷却里的下一发伤害不比上一发高就被吞掉。
     */
    public static void clearHurtCooldown(Entity target) {
        target.setInvulnerableTime(0);
        if (target instanceof LivingEntity living) {
            living.damageCooldownTime = 0;
        }
    }

    @Override
    protected void onHit(HitResult hitResult) {
        super.onHit(hitResult);
        if (!this.level().isClientSide()) {
            if (hitResult instanceof BlockHitResult block && hitResult.getType() == HitResult.Type.BLOCK) {
                this.playSound(UniverseSounds.IMPACT_ENERGY.get(), 0.5F, 1.05F + this.random.nextFloat() * 0.15F);
                // 打中遇险的货船（ADR-0024）
                SpaceEncounters.onBoltHit((ServerLevel) this.level(), this.getOwner(), block.getBlockPos());
            }
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
        DustParticleOptions dust = new DustParticleOptions(this.color(), 0.7F);
        // 贴着墙开火时落点就在眼前，粒子少撒一些，免得糊满屏幕
        Entity owner = this.getOwner();
        boolean close = owner != null && owner.getEyePosition().distanceToSqr(this.position()) < TRAIL_CLEARANCE * TRAIL_CLEARANCE;
        int count = close ? 2 : 5;
        for (int i = 0; i < count; i++) {
            this.level().addParticle(ParticleTypes.ELECTRIC_SPARK, this.getX(), this.getY(), this.getZ(),
                    (this.random.nextDouble() - 0.5) * 0.3, (this.random.nextDouble() - 0.5) * 0.3, (this.random.nextDouble() - 0.5) * 0.3);
            if (i % 2 == 0) {
                this.level().addParticle(dust, this.getX() + (this.random.nextDouble() - 0.5) * 0.3, this.getY() + (this.random.nextDouble() - 0.5) * 0.3,
                        this.getZ() + (this.random.nextDouble() - 0.5) * 0.3, 0, 0, 0);
            }
        }
    }
}
