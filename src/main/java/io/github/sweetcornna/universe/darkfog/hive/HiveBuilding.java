package io.github.sweetcornna.universe.darkfog.hive;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.core.darkfog.DarkFogLevels;
import io.github.sweetcornna.universe.core.darkfog.HiveBuildingKind;
import io.github.sweetcornna.universe.core.darkfog.HiveWeapons;
import io.github.sweetcornna.universe.darkfog.DarkFogEnemy;
import io.github.sweetcornna.universe.darkfog.entity.DarkFogBolt;
import io.github.sweetcornna.universe.darkfog.entity.DarkFogDamage;
import io.github.sweetcornna.universe.entity.ship.ShipEntity;
import io.github.sweetcornna.universe.registry.UniverseEntities;
import io.github.sweetcornna.universe.registry.UniverseSounds;
import io.github.sweetcornna.universe.registry.sound.HostileSounds;
import io.github.sweetcornna.universe.survival.SurvivalDamage;
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
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;

/**
 * 太空巢穴的一座建筑（ADR-0028 §八）：不动、不存档，玩家走近时由 {@link DarkFogHives} 按存档放出来。
 * 实现 {@link Enemy}，飞船的自动瞄准认得。
 *
 * <p>接口（签名别改）：{@link #create}、{@link #hiveKind}、{@link #system}、{@link #slot}、{@link #setPowered}、
 * {@link #setDarkFogLevel}。挨打、被打掉时回调 {@link DarkFogHives#onBuildingHurt}、{@link DarkFogHives#onBuildingDestroyed}。
 *
 * <ul>
 *   <li>血量：原版的血量上限是 1024，巢穴的建筑比这硬得多（核心 6000 起），所以实体的血量是按比例缩过的：
 *       上限取 {@code min(1024, 真实血量)}，挨的打按同样的比例缩小。回调里给的是真实的伤害，存档按血量比例记。</li>
 *   <li>只吃玩家这一边的伤害：黑雾自己的、守卫机的、卡墙、摔落、缺氧这类环境伤害都不算；{@code /kill} 照样有效。</li>
 *   <li>两种塔有能量时自己找目标：附近的玩家（不打创造、旁观）和载着这样的玩家的飞船，坐在飞船里的人按飞船算。
 *       每 10 刻找一次，要看得见；等离子塔打不受重力的等离子炮弹（{@link HivePlasmaShell}），相位激光塔打激光弹。</li>
 * </ul>
 */
public class HiveBuilding extends Mob implements DarkFogEnemy, Enemy {
    private static final EntityDataAccessor<Integer> DATA_KIND = SynchedEntityData.defineId(HiveBuilding.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_LEVEL = SynchedEntityData.defineId(HiveBuilding.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_POWERED = SynchedEntityData.defineId(HiveBuilding.class, EntityDataSerializers.BOOLEAN);
    /** 塔瞄着的实体（-1 是没有）：客户端按这个转炮头。 */
    private static final EntityDataAccessor<Integer> DATA_TARGET = SynchedEntityData.defineId(HiveBuilding.class, EntityDataSerializers.INT);
    /** 原版血量属性的上限。 */
    private static final float HEALTH_CAP = 1024;
    private static final int RETARGET = 10;
    public static final int PLASMA_COLOR = 0xFF6A3A;
    public static final int LASER_COLOR = 0xD040FF;

    private int system = -1;
    private int slot = -1;
    private int cooldown = 20;
    private int retarget;
    private @Nullable Entity aim;
    /** 游戏内测试用：把塔的射程收小，免得打到隔壁的测试。 */
    private double rangeLimit = Double.MAX_VALUE;
    /** 游戏内测试用：假玩家不在世界的玩家列表里，挑目标、近炸、溅射时把它们也算上。 */
    private final List<Player> testPlayers = new ArrayList<>();

    public HiveBuilding(EntityType<? extends HiveBuilding> type, Level level) {
        super(type, level);
        this.setNoGravity(true);
        this.setPersistenceRequired();
        this.xpReward = 0;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, HEALTH_CAP)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                // 不加护甲：伤害先按血量缩过再进原版的结算，护甲减掉的比例会跟着缩放变
                .add(Attributes.MOVEMENT_SPEED, 0)
                .add(Attributes.NAME_TAG_DISTANCE, 128);
    }

    public static void registerEvents(IEventBus modBus) {
        modBus.addListener(EntityAttributeCreationEvent.class, event -> event.put(UniverseEntities.HIVE_BUILDING.get(), createAttributes().build()));
    }

    /** 造一座（还没加进世界）：{@code slot} 是巢穴布局里的槽位，{@code pos} 是它的位置。血量是满的，朝着恒星（太空维度的原点）。 */
    public static HiveBuilding create(ServerLevel level, HiveBuildingKind kind, int system, int slot, int darkFogLevel, Vec3 pos) {
        HiveBuilding building = new HiveBuilding(UniverseEntities.HIVE_BUILDING.get(), level);
        building.entityData.set(DATA_KIND, kind.ordinal());
        building.entityData.set(DATA_LEVEL, DarkFogLevels.clamp(darkFogLevel));
        building.system = system;
        building.slot = slot;
        building.applyMaxHealth();
        building.setHealth(building.getMaxHealth());
        building.refreshDimensions();
        building.snapTo(pos.x, pos.y, pos.z, facingStar(pos), 0);
        building.yBodyRot = building.getYRot();
        building.yHeadRot = building.getYRot();
        return building;
    }

    /** 从 {@code pos} 朝原点的朝向（度）。 */
    static float facingStar(Vec3 pos) {
        if (pos.x * pos.x + pos.z * pos.z < 1e-6) {
            return 0;
        }
        return (float) (Mth.atan2(pos.x, -pos.z) * Mth.RAD_TO_DEG);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_KIND, 0);
        builder.define(DATA_LEVEL, 0);
        builder.define(DATA_POWERED, true);
        builder.define(DATA_TARGET, -1);
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
        return dimensions(this.hiveKind());
    }

    /**
     * 各种建筑的碰撞箱。模型比这大一圈（核心外面的环、接收器的盘），碰撞箱罩住主体就够：实体按 16 格的区段查，
     * 碰撞箱宽过 6 格、高过 5 格，边上的弹体会查不到它。
     */
    public static EntityDimensions dimensions(HiveBuildingKind kind) {
        return switch (kind) {
            case CORE -> EntityDimensions.fixed(6.0F, 5.0F);
            case PHOTON_RECEIVER -> EntityDimensions.fixed(5.0F, 5.0F);
            case PLASMA_TOWER -> EntityDimensions.fixed(4.0F, 5.0F);
            case PHASE_LASER -> EntityDimensions.fixed(3.0F, 5.0F);
            case LANCER_PORT, HUMPBACK_PORT -> EntityDimensions.fixed(6.0F, 5.0F);
        };
    }

    public HiveBuildingKind hiveKind() {
        HiveBuildingKind[] kinds = HiveBuildingKind.values();
        return kinds[Math.floorMod(this.entityData.get(DATA_KIND), kinds.length)];
    }

    /** 属于哪个恒星系的巢穴。 */
    public int system() {
        return this.system;
    }

    /** 巢穴布局里的槽位。 */
    public int slot() {
        return this.slot;
    }

    /** 巢穴有没有能量：没有时炮塔不打、港口不出单位。 */
    public boolean powered() {
        return this.entityData.get(DATA_POWERED);
    }

    /** 巢穴有没有能量：没有时炮塔不打、港口不出单位。 */
    public void setPowered(boolean powered) {
        this.entityData.set(DATA_POWERED, powered);
        if (!powered) {
            this.aim(null);
        }
    }

    /** 等级变了：血量上限跟着变，血量的比例不变。 */
    public void setDarkFogLevel(int level) {
        int clamped = DarkFogLevels.clamp(level);
        if (clamped == this.darkFogLevel()) {
            return;
        }
        float fraction = this.getMaxHealth() > 0 ? this.getHealth() / this.getMaxHealth() : 1;
        this.entityData.set(DATA_LEVEL, clamped);
        this.applyMaxHealth();
        this.setHealth(Math.max(Float.MIN_VALUE, this.getMaxHealth() * fraction));
    }

    @Override
    public int darkFogLevel() {
        return this.entityData.get(DATA_LEVEL);
    }

    /** 真实的血量上限：0 级的血量乘等级的倍率。 */
    public float maxStructure() {
        return (float) (this.hiveKind().baseHealth() * DarkFogLevels.scale(this.darkFogLevel()));
    }

    /** 真实的剩余血量。 */
    public float structure() {
        return this.getMaxHealth() > 0 ? this.getHealth() / this.getMaxHealth() * this.maxStructure() : 0;
    }

    /** 实体的血量是真实血量的几分之一。 */
    private float healthScale() {
        return this.maxStructure() / Math.max(1, this.getMaxHealth());
    }

    private void applyMaxHealth() {
        this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(Math.min(HEALTH_CAP, this.maxStructure()));
    }

    @Override
    public int baseId() {
        return -1;
    }

    @Override
    public boolean attackingFacility() {
        return false;
    }

    @Override
    public boolean grounded() {
        return false;
    }

    /** 塔瞄着的实体，没有是 -1（客户端转炮头用）。 */
    public int aimedEntity() {
        return this.entityData.get(DATA_TARGET);
    }

    /** 炮口在哪：等离子塔的炮管、相位激光塔的透镜；别的建筑是中间。 */
    public Vec3 muzzle() {
        double up = switch (this.hiveKind()) {
            case PLASMA_TOWER -> 4.4;
            case PHASE_LASER -> 4.6;
            default -> this.getBbHeight() * 0.5;
        };
        return this.position().add(0, up, 0);
    }

    /** 游戏内测试用：塔最远打多远。 */
    public void restrictRange(double range) {
        this.rangeLimit = range;
    }

    /** 游戏内测试用：把假玩家也当成世界里的玩家。 */
    public void watchForTest(Player player) {
        this.testPlayers.add(player);
    }

    /** 塔要考虑的玩家：世界里的，加上测试的假玩家。 */
    List<? extends Player> players(ServerLevel level) {
        if (this.testPlayers.isEmpty()) {
            return level.players();
        }
        List<Player> all = new ArrayList<>(level.players());
        all.addAll(this.testPlayers);
        return all;
    }

    @Override
    protected Component getTypeName() {
        return Component.translatable("entity.universe.hive_building." + this.hiveKind().id());
    }

    // ---- 塔

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
            if (this.random.nextInt(14) == 0) {
                this.level().addParticle(ParticleTypes.SMOKE, this.getRandomX(0.6), this.getY() + this.getBbHeight() * this.random.nextDouble(),
                        this.getRandomZ(0.6), 0, 0.01, 0);
            }
            return;
        }
        HiveBuildingKind kind = this.hiveKind();
        if (this.random.nextInt(kind == HiveBuildingKind.CORE ? 1 : 5) == 0) {
            int color = kind == HiveBuildingKind.PHOTON_RECEIVER ? 0xB060FF : 0xD02040;
            this.level().addParticle(new DustParticleOptions(color, 0.9F), this.getRandomX(0.8), this.getY() + this.random.nextDouble() * this.getBbHeight(),
                    this.getRandomZ(0.8), 0, 0, 0);
        }
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        HiveWeapons.Weapon weapon = HiveWeapons.of(this.hiveKind()).orElse(null);
        if (weapon == null) {
            return;
        }
        if (!this.powered()) {
            this.aim(null);
            return;
        }
        if (this.cooldown > 0) {
            this.cooldown--;
        }
        double range = Math.min(weapon.range(), this.rangeLimit);
        if (--this.retarget <= 0 || !this.stillValid(level, range)) {
            this.retarget = RETARGET;
            this.aim(this.pickTarget(level, range));
        }
        if (this.cooldown > 0 || this.aim == null) {
            return;
        }
        this.fire(level, weapon, this.aim);
        this.cooldown = weapon.interval(this.darkFogLevel());
    }

    /** 打得打不了这个飞船：在太空里飞着、上面坐着黑雾会打的人。 */
    static boolean shootableShip(ShipEntity ship) {
        if (!ship.isAlive() || ship.structure() == null) {
            return false;
        }
        for (Entity passenger : ship.getPassengers()) {
            if (DarkFogDamage.targetable(passenger)) {
                return true;
            }
        }
        return false;
    }

    /** 能当目标：飞船看上面的人，人不能坐在飞船里（按飞船算）。 */
    private static boolean shootable(Entity entity) {
        if (entity instanceof ShipEntity ship) {
            return shootableShip(ship);
        }
        return DarkFogDamage.targetable(entity) && !(entity.getVehicle() instanceof ShipEntity);
    }

    /** 瞄哪里：碰撞箱的中心（飞船的碰撞箱罩着整个船身）。 */
    static Vec3 aimPoint(Entity entity) {
        return entity.getBoundingBox().getCenter();
    }

    /** 目标这一刻的速度（格/刻）：玩家的位置是客户端报上来的，按它报的走法算。 */
    static Vec3 velocity(Entity entity) {
        return entity instanceof Player player ? player.getKnownMovement() : entity.getDeltaMovement();
    }

    private boolean stillValid(ServerLevel level, double range) {
        Entity target = this.aim;
        return target != null && !target.isRemoved() && target.level() == level && shootable(target)
                && aimPoint(target).distanceToSqr(this.muzzle()) <= range * range * 1.1;
    }

    /** 射程里最近的、看得见的目标。 */
    private @Nullable Entity pickTarget(ServerLevel level, double range) {
        Vec3 muzzle = this.muzzle();
        Entity best = null;
        double bestDistance = range * range;
        for (ShipEntity ship : level.getEntitiesOfClass(ShipEntity.class, new AABB(muzzle, muzzle).inflate(range), HiveBuilding::shootableShip)) {
            double d = aimPoint(ship).distanceToSqr(muzzle);
            if (d < bestDistance && this.clear(level, muzzle, aimPoint(ship))) {
                bestDistance = d;
                best = ship;
            }
        }
        for (Player player : this.players(level)) {
            if (!shootable(player)) {
                continue;
            }
            double d = aimPoint(player).distanceToSqr(muzzle);
            if (d < bestDistance && this.clear(level, muzzle, aimPoint(player))) {
                bestDistance = d;
                best = player;
            }
        }
        return best;
    }

    private void aim(@Nullable Entity target) {
        this.aim = target;
        int id = target == null ? -1 : target.getId();
        if (this.entityData.get(DATA_TARGET) != id) {
            this.entityData.set(DATA_TARGET, id);
        }
    }

    /** 两点之间没有方块挡着（太空里是小行星）。 */
    private boolean clear(ServerLevel level, Vec3 from, Vec3 to) {
        HitResult hit = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        return hit.getType() == HitResult.Type.MISS || hit.getLocation().distanceToSqr(to) < 4;
    }

    private void fire(ServerLevel level, HiveWeapons.Weapon weapon, Entity target) {
        Vec3 muzzle = this.muzzle();
        Vec3 at = aimPoint(target);
        Vec3 v = velocity(target);
        // 炮弹慢，提前量打满了反倒好躲：只打八成五
        double lead = weapon == HiveWeapons.Weapon.PLASMA ? 0.85 : 1;
        double[] d = HiveWeapons.aim(at.x - muzzle.x, at.y - muzzle.y, at.z - muzzle.z, v.x, v.y, v.z, weapon.speed(), weapon.lifetime(), lead);
        Vec3 direction = new Vec3(d[0], d[1], d[2]);
        if (direction.lengthSqr() < 1e-6) {
            return;
        }
        direction = direction.normalize();
        // 从碰撞箱外面打出去
        Vec3 from = muzzle.add(direction.scale(weapon == HiveWeapons.Weapon.PLASMA ? 2.5 : 1.8));
        float damage = weapon.damage(this.darkFogLevel());
        if (weapon == HiveWeapons.Weapon.PLASMA) {
            HivePlasmaShell.fire(level, this, from, direction, damage, weapon.speed(), weapon.inaccuracy(), weapon.lifetime());
            level.playSound(null, from.x, from.y, from.z, HostileSounds.HIVE_PLASMA.get(), SoundSource.HOSTILE, 1.0F,
                    0.96F + this.random.nextFloat() * 0.08F);
            level.sendParticles(new DustParticleOptions(PLASMA_COLOR, 1.6F), from.x, from.y, from.z, 6, 0.3, 0.3, 0.3, 0);
        } else {
            DarkFogBolt.fire(level, this, from, direction, damage, LASER_COLOR, 0.7F, weapon.speed(), weapon.inaccuracy(), weapon.lifetime());
            level.playSound(null, from.x, from.y, from.z, HostileSounds.HIVE_LASER.get(), SoundSource.HOSTILE, 1.0F,
                    0.96F + this.random.nextFloat() * 0.08F);
        }
    }

    // ---- 受击、死亡

    /** 这下伤害算不算：只认玩家这一边的。 */
    static boolean harmedBy(DamageSource source) {
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return true;
        }
        if (source.is(DarkFogDamage.KEY)) {
            return false;
        }
        Entity attacker = source.getEntity();
        Entity direct = source.getDirectEntity();
        if (attacker != null && DarkFogDamage.spared(attacker) || direct != null && DarkFogDamage.spared(direct)) {
            return false;
        }
        return !(source.is(DamageTypes.IN_WALL) || source.is(DamageTypes.CRAMMING) || source.is(DamageTypes.FLY_INTO_WALL)
                || source.is(DamageTypeTags.IS_FALL) || source.is(DamageTypeTags.IS_DROWNING) || source.is(DamageTypeTags.IS_FREEZING)
                || source.is(SurvivalDamage.VACUUM) || source.is(SurvivalDamage.COLD) || source.is(SurvivalDamage.HEAT)
                || source.is(SurvivalDamage.TOXIC) || source.is(SurvivalDamage.RADIATION));
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        if (!harmedBy(source)) {
            return false;
        }
        float scale = this.healthScale();
        float before = this.getHealth();
        boolean hurt = super.hurtServer(level, source, source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) ? damage : damage / scale);
        float taken = (before - this.getHealth()) * scale;
        if (hurt && taken > 0 && !this.isRemoved()) {
            DarkFogHives.onBuildingHurt(level, this.system, this.slot, taken, this.getMaxHealth() > 0 ? this.getHealth() / this.getMaxHealth() : 0);
        }
        return hurt;
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (this.level() instanceof ServerLevel level) {
            double y = this.getY(0.5);
            float size = this.getBbWidth();
            level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, this.getX(), y, this.getZ(), 2, size * 0.3, this.getBbHeight() * 0.3, size * 0.3, 0);
            level.sendParticles(ParticleTypes.LARGE_SMOKE, this.getX(), y, this.getZ(), 60, size * 0.5, this.getBbHeight() * 0.5, size * 0.5, 0.05);
            level.sendParticles(new DustParticleOptions(0xFF2A3A, 2.0F), this.getX(), y, this.getZ(), 50, size * 0.5, this.getBbHeight() * 0.5,
                    size * 0.5, 0);
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, this.getX(), y, this.getZ(), 60, size * 0.5, this.getBbHeight() * 0.5, size * 0.5, 0.6);
            level.playSound(null, this.getX(), y, this.getZ(), UniverseSounds.EXPLOSION_LARGE.get(), SoundSource.HOSTILE, 1.0F, 0.9F);
            DarkFogHives.onBuildingDestroyed(level, this.system, this.slot, this.position(), this.darkFogLevel());
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
        if (!this.powered()) {
            return null;
        }
        return this.hiveKind() == HiveBuildingKind.CORE ? HostileSounds.HIVE_CORE.get() : HostileSounds.BUILDING_HUM.get();
    }

    @Override
    public int getAmbientSoundInterval() {
        return 240;
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
        return 0.85F + this.random.nextFloat() * 0.1F;
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
