package io.github.sweetcornna.universe.darkfog.entity;

import java.util.Optional;

import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.core.darkfog.BuildingKind;
import io.github.sweetcornna.universe.core.darkfog.DarkFogLevels;
import io.github.sweetcornna.universe.core.darkfog.Tactics;
import io.github.sweetcornna.universe.darkfog.DarkFogBases;
import io.github.sweetcornna.universe.darkfog.DarkFogEnemy;
import io.github.sweetcornna.universe.darkfog.Facilities;
import io.github.sweetcornna.universe.darkfog.Taunts;
import io.github.sweetcornna.universe.entity.PlasmaBolt;
import io.github.sweetcornna.universe.registry.UniverseEntities;
import io.github.sweetcornna.universe.registry.UniverseSounds;
import io.github.sweetcornna.universe.registry.sound.HostileSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * 黑雾地面基地的建筑（ADR-0028 §二）：不动的实体。不存档，由 {@link DarkFogBases} 按存档里的基地在玩家附近放出来；
 * 血量、等级、有没有能量都由管理那边设。
 *
 * <p>只有两种炮塔会打：高能激光塔近，一道持续的光束烧人；等离子哨塔远，一下一下地吊射炮弹，跳起来能躲。
 * 基地没能量时都不打。都先打信号塔，再打人，最后打射程里的设施。
 */
public class DarkFogBuilding extends Mob implements DarkFogEnemy {
    private static final EntityDataAccessor<Integer> DATA_KIND = SynchedEntityData.defineId(DarkFogBuilding.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_LEVEL = SynchedEntityData.defineId(DarkFogBuilding.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_POWERED = SynchedEntityData.defineId(DarkFogBuilding.class, EntityDataSerializers.BOOLEAN);
    /** 炮塔瞄着的实体（-1 是没有）、方块；激光塔按这个画光束，炮塔的炮头跟着转。 */
    private static final EntityDataAccessor<Integer> DATA_TARGET = SynchedEntityData.defineId(DarkFogBuilding.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Optional<BlockPos>> DATA_TARGET_BLOCK = SynchedEntityData.defineId(DarkFogBuilding.class,
            EntityDataSerializers.OPTIONAL_BLOCK_POS);
    private static final EntityDimensions RELAY = EntityDimensions.fixed(3.0F, 2.0F);
    private static final EntityDimensions CORE = EntityDimensions.fixed(4.0F, 5.0F);
    private static final EntityDimensions CAMP = EntityDimensions.fixed(3.0F, 3.0F);
    private static final EntityDimensions LASER = EntityDimensions.fixed(1.5F, 4.0F);
    private static final EntityDimensions SENTRY = EntityDimensions.fixed(2.0F, 3.0F);
    public static final double LASER_RANGE = 24;
    public static final double SENTRY_RANGE = 40;
    private static final double SENTRY_MIN_RANGE = 4;
    /** 激光每隔多少刻烧一下。 */
    private static final int LASER_INTERVAL = 10;
    private static final float LASER_DAMAGE = 2.0F;
    private static final int SENTRY_INTERVAL = 60;
    private static final float SENTRY_DAMAGE = 6.0F;
    /** 原版血量属性的上限。钻芯这类比它硬的，实体的血量按比例缩，挨的打也按同样的比例缩。 */
    private static final float HEALTH_CAP = 1024;

    private int baseId = -1;
    private int cooldown = 20;
    private int retarget;
    private @Nullable LivingEntity aimEntity;
    private @Nullable BlockPos aimBlock;

    public DarkFogBuilding(EntityType<? extends DarkFogBuilding> type, Level level) {
        super(type, level);
        this.setNoGravity(true);
        this.setPersistenceRequired();
        this.xpReward = 10;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, HEALTH_CAP)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.ARMOR, 6)
                .add(Attributes.MOVEMENT_SPEED, 0);
    }

    /** 造一座建筑，满血；调用的人再按存档设血量、加进世界。 */
    public static DarkFogBuilding create(ServerLevel level, BuildingKind kind, int baseId, int darkFogLevel, Vec3 pos) {
        DarkFogBuilding building = new DarkFogBuilding(UniverseEntities.DARK_FOG_BUILDING.get(), level);
        building.entityData.set(DATA_KIND, kind.ordinal());
        building.entityData.set(DATA_LEVEL, DarkFogLevels.clamp(darkFogLevel));
        building.baseId = baseId;
        building.applyMaxHealth();
        building.setHealth(building.getMaxHealth());
        building.refreshDimensions();
        building.snapTo(pos.x, pos.y, pos.z, 0, 0);
        return building;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_KIND, 0);
        builder.define(DATA_LEVEL, 0);
        builder.define(DATA_POWERED, true);
        builder.define(DATA_TARGET, -1);
        builder.define(DATA_TARGET_BLOCK, Optional.empty());
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) {
        super.onSyncedDataUpdated(accessor);
        if (DATA_KIND.equals(accessor)) {
            this.refreshDimensions();
        }
    }

    @Override
    protected EntityDimensions getDefaultDimensions(Pose pose) {
        return switch (this.kind()) {
            case RELAY -> RELAY;
            case CORE -> CORE;
            case RAIDER_CAMP, RANGER_CAMP, GUARDIAN_CAMP -> CAMP;
            case LASER_TOWER -> LASER;
            case PLASMA_SENTRY -> SENTRY;
        };
    }

    public BuildingKind kind() {
        BuildingKind[] kinds = BuildingKind.values();
        return kinds[Math.floorMod(this.entityData.get(DATA_KIND), kinds.length)];
    }

    /** 基地有没有能量：没有时炮塔不打、营地不产。 */
    public boolean powered() {
        return this.entityData.get(DATA_POWERED);
    }

    public void setPowered(boolean powered) {
        this.entityData.set(DATA_POWERED, powered);
    }

    /** 等级变了：血量上限跟着变，血量的比例不变。 */
    public void setDarkFogLevel(int level) {
        float fraction = this.getMaxHealth() > 0 ? this.getHealth() / this.getMaxHealth() : 1;
        this.entityData.set(DATA_LEVEL, DarkFogLevels.clamp(level));
        this.applyMaxHealth();
        this.setHealth(Math.max(1, this.getMaxHealth() * fraction));
    }

    /** 真实的血量上限：按种类和等级。 */
    public float maxStructure() {
        return (float) (this.kind().baseHealth() * DarkFogLevels.scale(this.darkFogLevel()));
    }

    /** 实体的血量是真实血量的几分之一。 */
    private float healthScale() {
        return this.maxStructure() / Math.max(1, this.getMaxHealth());
    }

    private void applyMaxHealth() {
        this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(Math.min(HEALTH_CAP, this.maxStructure()));
    }

    @Override
    public int darkFogLevel() {
        return this.entityData.get(DATA_LEVEL);
    }

    @Override
    public int baseId() {
        return this.baseId;
    }

    @Override
    public boolean attackingFacility() {
        return this.entityData.get(DATA_TARGET_BLOCK).isPresent();
    }

    @Override
    public boolean grounded() {
        return this.kind() != BuildingKind.RELAY;
    }

    /** 炮塔瞄着的实体，没有是 -1（客户端画光束、转炮头用）。 */
    public int aimedEntity() {
        return this.entityData.get(DATA_TARGET);
    }

    public Optional<BlockPos> aimedBlock() {
        return this.entityData.get(DATA_TARGET_BLOCK);
    }

    /** 炮口在哪：激光塔顶上的透镜，哨塔的炮管。 */
    public Vec3 muzzle() {
        return this.position().add(0, this.kind() == BuildingKind.LASER_TOWER ? 3.6 : 2.5, 0);
    }

    @Override
    protected Component getTypeName() {
        return Component.translatable("entity.universe.dark_fog_building." + this.kind().id());
    }

    // ---- 炮塔

    @Override
    public void tick() {
        super.tick();
        // 不动，也不转身
        this.yBodyRot = this.getYRot();
        this.yHeadRot = this.getYRot();
        this.setDeltaMovement(Vec3.ZERO);
        if (this.level().isClientSide()) {
            this.clientTick();
        }
    }

    private void clientTick() {
        if (!this.powered()) {
            if (this.random.nextInt(20) == 0) {
                this.level().addParticle(ParticleTypes.SMOKE, this.getRandomX(0.5), this.getY() + this.getBbHeight() * 0.8, this.getRandomZ(0.5), 0, 0.02, 0);
            }
            return;
        }
        if (this.random.nextInt(this.kind() == BuildingKind.CORE ? 2 : 6) == 0) {
            double y = this.kind() == BuildingKind.RELAY ? this.getY() : this.getY() + this.random.nextDouble() * this.getBbHeight();
            this.level().addParticle(new DustParticleOptions(0xD02040, 0.7F), this.getRandomX(0.6), y, this.getRandomZ(0.6), 0, 0.03, 0);
        }
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        BuildingKind kind = this.kind();
        if (!kind.isTurret()) {
            return;
        }
        if (!this.powered()) {
            this.aim(null, null);
            return;
        }
        if (this.cooldown > 0) {
            this.cooldown--;
        }
        double range = kind == BuildingKind.LASER_TOWER ? LASER_RANGE : SENTRY_RANGE;
        if (--this.retarget <= 0 || !this.stillValid(level, range)) {
            this.retarget = 10;
            this.pickTarget(level, range);
        }
        if (this.cooldown > 0 || this.aimEntity == null && this.aimBlock == null) {
            return;
        }
        float scale = (float) DarkFogLevels.scale(this.darkFogLevel());
        if (kind == BuildingKind.LASER_TOWER) {
            this.burn(level, LASER_DAMAGE * scale);
            this.cooldown = LASER_INTERVAL;
        } else {
            this.lob(level, SENTRY_DAMAGE * scale);
            this.cooldown = SENTRY_INTERVAL;
        }
    }

    private boolean stillValid(ServerLevel level, double range) {
        if (this.aimEntity != null) {
            return this.aimEntity.isAlive() && DarkFogDamage.targetable(this.aimEntity) && this.aimEntity.level() == level
                    && this.muzzle().distanceTo(this.aimEntity.getEyePosition()) <= range;
        }
        return this.aimBlock == null || !level.getBlockState(this.aimBlock).isAir();
    }

    /** 先信号塔，再人，再设施；激光塔要看得见，哨塔是吊射，不用。 */
    private void pickTarget(ServerLevel level, double range) {
        Vec3 muzzle = this.muzzle();
        boolean laser = this.kind() == BuildingKind.LASER_TOWER;
        Optional<BlockPos> taunt = Taunts.nearestTaunt(level, muzzle, range);
        if (taunt.isPresent()) {
            this.aim(null, taunt.get());
            return;
        }
        Player player = level.getNearestPlayer(muzzle.x, muzzle.y, muzzle.z, range, entity -> DarkFogDamage.targetable(entity)
                && (!laser || this.clear(level, muzzle, ((Player) entity).getEyePosition()))
                && (laser || entity.position().distanceTo(this.position()) >= SENTRY_MIN_RANGE));
        if (player != null) {
            this.aim(player, null);
            return;
        }
        this.aim(null, Facilities.nearest(level, this.position(), range).orElse(null));
    }

    private void aim(@Nullable LivingEntity entity, @Nullable BlockPos block) {
        this.aimEntity = entity;
        this.aimBlock = block;
        int id = entity == null ? -1 : entity.getId();
        if (this.entityData.get(DATA_TARGET) != id) {
            this.entityData.set(DATA_TARGET, id);
        }
        Optional<BlockPos> optional = Optional.ofNullable(block);
        if (!this.entityData.get(DATA_TARGET_BLOCK).equals(optional)) {
            this.entityData.set(DATA_TARGET_BLOCK, optional);
        }
    }

    /** 两点之间没有方块挡着。 */
    private boolean clear(ServerLevel level, Vec3 from, Vec3 to) {
        HitResult hit = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        return hit.getType() == HitResult.Type.MISS || hit.getLocation().distanceToSqr(to) < 1;
    }

    private void burn(ServerLevel level, float damage) {
        if (this.aimEntity != null) {
            if (!this.clear(level, this.muzzle(), this.aimEntity.getEyePosition())) {
                this.retarget = 0;
                return;
            }
            PlasmaBolt.clearHurtCooldown(this.aimEntity);
            this.aimEntity.hurtServer(level, DarkFogDamage.source(level, this, this), damage);
        } else if (this.aimBlock != null) {
            if (Facilities.damage(level, this.aimBlock, (float) (damage * Tactics.FACILITY_DAMAGE), this)) {
                this.retarget = 0;
            }
        }
        if (this.tickCount % 20 < LASER_INTERVAL) {
            level.playSound(null, this.getX(), this.getY() + 3.5, this.getZ(), HostileSounds.TOWER_LASER.get(), SoundSource.HOSTILE, 1.0F, 0.96F + this.random.nextFloat() * 0.08F);
        }
    }

    private void lob(ServerLevel level, float damage) {
        Vec3 muzzle = this.muzzle();
        Vec3 to;
        if (this.aimEntity != null) {
            to = this.aimEntity.position();
            int ticks = Tactics.flightTicks(muzzle.distanceTo(to));
            // 往前提一点，但不提满：站着不动的必中，一直跑的多半落在身后
            to = to.add(this.aimEntity.getDeltaMovement().multiply(1, 0, 1).scale(ticks * 0.5));
            DarkFogShell.lob(level, this, muzzle, to, ticks, damage);
        } else if (this.aimBlock != null) {
            to = Vec3.atCenterOf(this.aimBlock).add(0, 0.5, 0);
            DarkFogShell.lob(level, this, muzzle, to, Tactics.flightTicks(muzzle.distanceTo(to)), damage);
        } else {
            return;
        }
        level.playSound(null, muzzle.x, muzzle.y, muzzle.z, HostileSounds.SENTRY_FIRE.get(), SoundSource.HOSTILE, 1.0F, 0.96F + this.random.nextFloat() * 0.08F);
        level.sendParticles(ParticleTypes.FLAME, muzzle.x, muzzle.y, muzzle.z, 6, 0.15, 0.1, 0.15, 0.02);
    }

    // ---- 受击、死亡

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        float before = this.getHealth();
        float scale = this.healthScale();
        boolean hurt = super.hurtServer(level, source, source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) ? damage : damage / scale);
        if (hurt && !this.isRemoved()) {
            DarkFogBases.onBuildingHurt(level, this, (before - this.getHealth()) * scale, source);
        }
        return hurt;
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (this.level() instanceof ServerLevel level) {
            double y = this.getY(0.5);
            float size = this.getBbWidth();
            level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, this.getX(), y, this.getZ(), 1, 0, 0, 0, 0);
            level.sendParticles(ParticleTypes.LARGE_SMOKE, this.getX(), y, this.getZ(), 40, size * 0.4, this.getBbHeight() * 0.4, size * 0.4, 0.03);
            level.sendParticles(new DustParticleOptions(0xFF2A3A, 1.5F), this.getX(), y, this.getZ(), 30, size * 0.4, this.getBbHeight() * 0.4,
                    size * 0.4, 0);
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, this.getX(), y, this.getZ(), 40, size * 0.4, this.getBbHeight() * 0.4, size * 0.4, 0.4);
            level.playSound(null, this.getX(), y, this.getZ(), UniverseSounds.EXPLOSION_SMALL.get(), SoundSource.HOSTILE, 1.0F, 0.85F);
            DarkFogBases.onBuildingDestroyed(level, this, source);
        }
    }

    /** 不像生物那样倒下：炸完几刻就没了。 */
    @Override
    protected void tickDeath() {
        this.deathTime++;
        if (this.deathTime >= 3 && !this.level().isClientSide() && !this.isRemoved()) {
            this.remove(Entity.RemovalReason.KILLED);
        }
    }

    @Override
    protected @Nullable SoundEvent getAmbientSound() {
        return this.powered() ? HostileSounds.BUILDING_HUM.get() : null;
    }

    @Override
    public int getAmbientSoundInterval() {
        return 200;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return HostileSounds.BUILDING_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return HostileSounds.COLLAPSE.get();
    }

    @Override
    public float getVoicePitch() {
        return 0.95F + this.random.nextFloat() * 0.1F;
    }

    @Override
    public boolean canBeCollidedWith(@Nullable Entity other) {
        return this.isAlive();
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean isPushedByFluid() {
        return false;
    }

    @Override
    public void push(Entity entity) {
    }

    @Override
    protected void doPush(Entity entity) {
    }

    @Override
    public boolean canBeLeashed() {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distSqr) {
        return false;
    }

    @Override
    public void checkDespawn() {
    }
}
