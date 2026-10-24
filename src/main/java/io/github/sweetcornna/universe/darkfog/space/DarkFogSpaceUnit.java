package io.github.sweetcornna.universe.darkfog.space;

import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.core.darkfog.DarkFogLevels;
import io.github.sweetcornna.universe.core.darkfog.SpaceFlight;
import io.github.sweetcornna.universe.core.darkfog.SpaceUnitKind;
import io.github.sweetcornna.universe.core.ship.ShipCombat;
import io.github.sweetcornna.universe.darkfog.DarkFogEnemy;
import io.github.sweetcornna.universe.darkfog.entity.DarkFogBolt;
import io.github.sweetcornna.universe.darkfog.entity.DarkFogDamage;
import io.github.sweetcornna.universe.darkfog.hive.DarkFogHives;
import io.github.sweetcornna.universe.entity.ship.ShipEntity;
import io.github.sweetcornna.universe.registry.UniverseEntities;
import io.github.sweetcornna.universe.registry.UniverseSounds;
import io.github.sweetcornna.universe.registry.sound.HostileSounds;
import io.github.sweetcornna.universe.survival.SurvivalDamage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
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
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * 太空巢穴的单位（ADR-0028 §八）：枪骑、座头鲸。三维自由飞，高度不设限，不存档。实现 {@link Enemy}，飞船的自动瞄准认得。
 *
 * <p>接口（签名别改）：{@link #spawn}、{@link #kind}、{@link #system}、{@link #mode}。挨打、被打死时回调
 * {@code DarkFogHives.onUnitHurt}、{@code DarkFogHives.onUnitKilled}。
 *
 * <ul>
 *   <li>不走原版的移动：速度由子类每刻按 {@link SpaceFlight} 算好，{@link #travel} 原样走，没有空气阻力。</li>
 *   <li>守巢：在家（巢穴的中心）附近绕圈；有人进了 {@link SpaceUnitKind#sight} 格、或者被人打了，就去打；
 *       离家超过 {@link SpaceUnitKind#leash} 格就不追了，回去。坐在飞船里的人按飞船打。</li>
 *   <li>只吃玩家这一边的伤害：黑雾自己的、守卫机的、卡墙、缺氧这类都不算。</li>
 * </ul>
 */
public abstract class DarkFogSpaceUnit extends Mob implements DarkFogEnemy, Enemy {
    public enum Mode {
        /** 守巢：在巢穴附近巡逻，打靠近的飞船和玩家，追远了回去。 */
        DEFEND,
        /** 轰炸星球（只有枪骑）：从高空往下冲，炸设施，炸完走。 */
        RAID
    }

    private static final EntityDataAccessor<Integer> DATA_LEVEL = SynchedEntityData.defineId(DarkFogSpaceUnit.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_ANGRY = SynchedEntityData.defineId(DarkFogSpaceUnit.class, EntityDataSerializers.BOOLEAN);
    /** 几刻想一次打谁。 */
    private static final int THINK_INTERVAL = 10;
    /** 被打以后多少刻内还记着仇。 */
    private static final int GRUDGE = 300;
    /** 多远还画：太空里隔着上百格打。 */
    private static final double RENDER_DISTANCE = 320;
    /** 死了以后几刻炸没。 */
    private static final int DEATH_TICKS = 8;

    private int system = -1;
    private Mode mode = Mode.DEFEND;
    private Vec3 home = Vec3.ZERO;
    private boolean homeSet;
    private int jamTicks;
    private double jamFactor = 1;
    private @Nullable LivingEntity focus;
    private int thinkCooldown;
    private boolean returning;
    /** 巡逻的圈：半径、现在转到的角度。 */
    protected double orbitRadius;
    protected double orbitAngle;
    /** 转弯时侧倾的角度（客户端算，只用来画）。 */
    private float roll;
    private float rollO;

    protected DarkFogSpaceUnit(EntityType<? extends DarkFogSpaceUnit> type, Level level) {
        super(type, level);
        this.setNoGravity(true);
        this.setPersistenceRequired();
        this.xpReward = 0;
        this.orbitAngle = this.random.nextDouble() * Math.PI * 2;
    }

    /** 放一个出来，已经加进世界。{@code home} 是守巢时绕着的点（巢穴的中心）。 */
    public static DarkFogSpaceUnit spawn(ServerLevel level, SpaceUnitKind kind, int system, int darkFogLevel, Vec3 at, Vec3 home, Mode mode) {
        DarkFogSpaceUnit unit = create(level, kind, system, darkFogLevel, at, home, mode);
        level.addFreshEntity(unit);
        return unit;
    }

    /** 造一个（还没加进世界）。 */
    static DarkFogSpaceUnit create(ServerLevel level, SpaceUnitKind kind, int system, int darkFogLevel, Vec3 at, Vec3 home, Mode mode) {
        EntityType<? extends DarkFogSpaceUnit> type = switch (kind) {
            case LANCER -> UniverseEntities.DARK_FOG_LANCER.get();
            case HUMPBACK -> UniverseEntities.DARK_FOG_HUMPBACK.get();
        };
        DarkFogSpaceUnit unit = type.create(level, EntitySpawnReason.EVENT);
        if (unit == null) {
            throw new IllegalStateException("can't create " + kind);
        }
        double scale = DarkFogLevels.scale(darkFogLevel);
        unit.entityData.set(DATA_LEVEL, DarkFogLevels.clamp(darkFogLevel));
        unit.system = system;
        unit.mode = mode;
        unit.home = home;
        unit.homeSet = true;
        unit.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(kind.baseHealth() * scale);
        unit.setHealth(unit.getMaxHealth());
        unit.snapTo(at.x, at.y, at.z, level.getRandom().nextFloat() * 360, 0);
        return unit;
    }

    public abstract SpaceUnitKind kind();

    /** 属于哪个恒星系的巢穴。 */
    public int system() {
        return this.system;
    }

    public Mode mode() {
        return this.mode;
    }

    /** 守巢时绕着的点。 */
    public Vec3 home() {
        return this.home;
    }

    /** 现在盯着打的人（服务端，GameTest 和调试用）。 */
    public @Nullable LivingEntity focus() {
        return this.focus;
    }

    /** 正在往家飞。 */
    public boolean returning() {
        return this.returning;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_LEVEL, 0);
        builder.define(DATA_ANGRY, false);
    }

    @Override
    public int darkFogLevel() {
        return this.entityData.get(DATA_LEVEL);
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

    /** 在打人（发光层亮一些）。 */
    public boolean angry() {
        return this.entityData.get(DATA_ANGRY);
    }

    protected void setAngry(boolean angry) {
        if (this.entityData.get(DATA_ANGRY) != angry) {
            this.entityData.set(DATA_ANGRY, angry);
        }
    }

    @Override
    public void jam(int ticks, double factor) {
        this.jamTicks = Math.max(this.jamTicks, ticks);
        this.jamFactor = Math.max(0, Math.min(1, factor));
    }

    /** 现在的减速倍率：没被干扰是 1。 */
    public double jamFactor() {
        return this.jamTicks > 0 ? this.jamFactor : 1;
    }

    /** 一发的伤害（按等级加过）。 */
    protected float shotDamage() {
        return (float) (this.kind().baseDamage() * DarkFogLevels.scale(this.darkFogLevel()));
    }

    // ---- 飞

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide()) {
            // 往哪边转就往哪边侧倾，座头鲸笨重，侧得少
            float max = this.kind() == SpaceUnitKind.HUMPBACK ? 10 : 50;
            float wanted = Mth.clamp(-Mth.wrapDegrees(this.getYRot() - this.yRotO) * 4, -max, max);
            this.rollO = this.roll;
            this.roll += (wanted - this.roll) * 0.2F;
        }
    }

    /** 画的时候侧倾多少度。 */
    public float roll(float partialTicks) {
        return Mth.lerp(partialTicks, this.rollO, this.roll);
    }

    /** 速度由 AI 每刻算好，这里原样走：不受重力，没有空气阻力。撞了方块弹回来一点。 */
    @Override
    public void travel(Vec3 input) {
        this.move(MoverType.SELF, this.getDeltaMovement());
        if (this.horizontalCollision || this.verticalCollision) {
            this.setDeltaMovement(this.getDeltaMovement().scale(-0.4));
        }
    }

    /** 按 {@link SpaceFlight#approach} 把速度朝 {@code wanted} 改一步。 */
    protected void steer(double[] wanted, double accel) {
        double[] next = SpaceFlight.approach(arr(this.getDeltaMovement()), wanted, accel);
        this.setDeltaMovement(vec(next));
    }

    /** 机头朝着速度的方向（慢得几乎停住时不转）。 */
    protected void faceVelocity() {
        Vec3 v = this.getDeltaMovement();
        if (v.lengthSqr() < 1e-4) {
            return;
        }
        double[] a = arr(v);
        float yaw = SpaceFlight.yaw(a);
        this.setYRot(yaw);
        this.setXRot(SpaceFlight.pitch(a));
        this.yBodyRot = yaw;
        this.yHeadRot = yaw;
    }

    // ---- 行为

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        if (!this.homeSet) {
            // 用命令、刷怪蛋放出来的把出生的地方当家
            this.home = this.position();
            this.homeSet = true;
        }
        if (this.jamTicks > 0) {
            this.jamTicks--;
        }
        if (this.mode == Mode.RAID) {
            this.raid(level);
            this.faceVelocity();
            return;
        }
        if (--this.thinkCooldown <= 0) {
            this.think(level);
            this.thinkCooldown = THINK_INTERVAL + this.random.nextInt(3);
        }
        LivingEntity target = this.focus;
        if (target != null && (!target.isAlive() || target.level() != level || !DarkFogDamage.engageable(target))) {
            target = this.focus = null;
        }
        double jam = this.jamFactor();
        if (this.returning) {
            double[] wanted = SpaceFlight.seek(arr(this.position()), arr(this.home), this.kind().speed() * jam, 32);
            this.steer(wanted, this.kind().accel());
            if (this.position().distanceTo(this.home) < this.kind().leash() * 0.5) {
                this.returning = false;
            }
        } else if (target != null) {
            Entity body = target.getRootVehicle();
            this.engage(level, target, aimPoint(target), body.getDeltaMovement(), clearance(target));
        } else {
            this.patrol(level, jam);
        }
        this.setAngry(target != null && !this.returning);
        this.faceVelocity();
    }

    /** 想一下打谁：离家太远就回去；记仇的先打，再打附近的人。 */
    private void think(ServerLevel level) {
        Vec3 pos = this.position();
        SpaceUnitKind kind = this.kind();
        if (pos.distanceTo(this.home) > kind.leash()) {
            this.returning = true;
            this.focus = null;
            return;
        }
        LivingEntity attacker = this.getLastHurtByMob();
        boolean grudge = attacker != null && attacker.isAlive() && DarkFogDamage.engageable(attacker) && attacker.level() == level
                && this.tickCount - this.getLastHurtByMobTimestamp() < GRUDGE && aimPoint(attacker).distanceTo(pos) < kind.sight() * 1.5;
        if (grudge) {
            this.focus = attacker;
            return;
        }
        LivingEntity best = null;
        double bestDistance = kind.sight();
        for (Player player : level.players()) {
            if (!DarkFogDamage.targetable(player)) {
                continue;
            }
            Vec3 aim = aimPoint(player);
            double distance = aim.distanceTo(pos);
            if (distance < bestDistance && aim.distanceTo(this.home) < kind.leash()) {
                bestDistance = distance;
                best = player;
            }
        }
        // 玩家的太空舰队也打
        for (LivingEntity unit : DarkFogDamage.fleetUnits(level, pos, kind.sight())) {
            Vec3 aim = aimPoint(unit);
            double distance = aim.distanceTo(pos);
            if (distance < bestDistance && aim.distanceTo(this.home) < kind.leash()) {
                bestDistance = distance;
                best = unit;
            }
        }
        this.focus = best;
    }

    /** 没人可打：绕着家转圈。 */
    protected void patrol(ServerLevel level, double jam) {
        double speed = this.kind().speed() * 0.5 * jam;
        if (this.orbitRadius <= 0) {
            this.orbitRadius = this.patrolRadius();
        }
        this.orbitAngle += Math.max(0.002, speed / this.orbitRadius);
        double[] goal = SpaceFlight.orbitPoint(arr(this.home), this.orbitRadius, this.orbitAngle, this.orbitRadius * 0.15);
        this.steer(SpaceFlight.seek(arr(this.position()), goal, speed, 16), this.kind().accel());
    }

    /** 巡逻圈的半径。 */
    protected abstract double patrolRadius();

    /** 打 {@code target}：改速度、开火。{@code aim} 是瞄准的点（飞船的中间），{@code clearance} 是目标的半径。 */
    protected abstract void engage(ServerLevel level, LivingEntity target, Vec3 aim, Vec3 targetVelocity, double clearance);

    /** 轰炸星球时每刻做什么（只有枪骑会轰炸）。 */
    protected void raid(ServerLevel level) {
        this.vanish(level);
    }

    /** 瞄哪里：坐在飞船里的按飞船的中间，不然按碰撞箱的中间。 */
    static Vec3 aimPoint(Entity target) {
        Entity root = target.getRootVehicle();
        if (root instanceof ShipEntity ship && ship.structure() != null) {
            return ship.position().add(0, ship.structure().shape().height() / 2.0, 0);
        }
        return target.getBoundingBox().getCenter();
    }

    /** 目标有多大：飞船按船身的半径，人算 1 格。 */
    static double clearance(Entity target) {
        Entity root = target.getRootVehicle();
        if (root instanceof ShipEntity ship && ship.structure() != null) {
            return ship.structure().shape().radius() + 1;
        }
        return 1;
    }

    /**
     * 打一发激光弹：带提前量，弹速是相对自己的（加上自己的速度），已经加进世界。
     */
    protected DarkFogBolt shoot(ServerLevel level, Vec3 from, Vec3 aim, Vec3 targetVelocity, int color, float size, double speed, float spread,
            int lifetime) {
        Vec3 own = this.getDeltaMovement();
        double[] direction = ShipCombat.lead(arr(aim.subtract(from)), arr(targetVelocity.subtract(own)), speed);
        Vec3 shot = vec(direction).scale(speed).add(own);
        return DarkFogBolt.fire(level, this, from, shot, this.shotDamage(), color, size, shot.length(), spread, lifetime);
    }

    /** 不打了、飞走了：一团粒子后消失（不算打死）。 */
    protected void vanish(ServerLevel level) {
        Vec3 c = this.getBoundingBox().getCenter();
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, c.x, c.y, c.z, 16, 0.5, 0.3, 0.5, 0.1);
        this.discard();
    }

    // ---- 受击、死亡

    /** 这下伤害算不算：只认玩家这一边的（{@code /kill} 和掉出世界照算）。 */
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
        float before = this.getHealth();
        boolean hurt = super.hurtServer(level, source, damage);
        if (hurt && !this.isRemoved() && before > this.getHealth()) {
            DarkFogHives.onUnitHurt(level, this.system, before - this.getHealth(), this.mode == Mode.DEFEND);
        }
        return hurt;
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (this.level() instanceof ServerLevel level) {
            Vec3 c = this.getBoundingBox().getCenter();
            double spread = this.getBbWidth() * 0.4;
            level.sendParticles(ParticleTypes.EXPLOSION, c.x, c.y, c.z, this.kind() == SpaceUnitKind.HUMPBACK ? 6 : 1, spread, spread, spread, 0);
            level.sendParticles(ParticleTypes.LARGE_SMOKE, c.x, c.y, c.z, 16, spread, spread, spread, 0.03);
            level.sendParticles(new DustParticleOptions(0xFF2A3A, 1.2F), c.x, c.y, c.z, 14, spread, spread, spread, 0);
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, c.x, c.y, c.z, 20, spread, spread, spread, 0.4);
            level.playSound(null, c.x, c.y, c.z, (this.kind() == SpaceUnitKind.HUMPBACK ? UniverseSounds.EXPLOSION_LARGE : UniverseSounds.EXPLOSION_SMALL).get(),
                    SoundSource.HOSTILE, this.kind() == SpaceUnitKind.HUMPBACK ? 1.0F : 0.7F, this.kind() == SpaceUnitKind.HUMPBACK ? 0.9F : 1.2F);
            DarkFogHives.onUnitKilled(level, this.system, this.kind(), this.mode == Mode.DEFEND, this.position(), this.darkFogLevel());
        }
    }

    /** 炸得快，不像原版的怪那样倒地一秒。 */
    @Override
    protected void tickDeath() {
        this.deathTime++;
        if (this.deathTime >= DEATH_TICKS && !this.level().isClientSide() && !this.isRemoved()) {
            this.remove(RemovalReason.KILLED);
        }
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < RENDER_DISTANCE * RENDER_DISTANCE;
    }

    @Override
    protected @Nullable SoundEvent getAmbientSound() {
        return this.kind() == SpaceUnitKind.HUMPBACK ? HostileSounds.CRAFT_HUM.get() : HostileSounds.UNIT_AMBIENT.get();
    }

    @Override
    public int getAmbientSoundInterval() {
        return 200;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return this.kind() == SpaceUnitKind.HUMPBACK ? HostileSounds.BUILDING_HURT.get() : HostileSounds.UNIT_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return HostileSounds.UNIT_DEATH.get();
    }

    @Override
    protected float getSoundVolume() {
        return this.kind() == SpaceUnitKind.HUMPBACK ? 1.0F : 0.6F;
    }

    @Override
    public float getVoicePitch() {
        return (this.kind() == SpaceUnitKind.HUMPBACK ? 0.8F : 1.0F) + (this.random.nextFloat() - 0.5F) * 0.1F;
    }

    @Override
    protected void checkFallDamage(double ya, boolean onGround, BlockState onState, BlockPos pos) {
    }

    @Override
    public boolean causeFallDamage(double fallDistance, float damageModifier, DamageSource source) {
        return false;
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

    static double[] arr(Vec3 v) {
        return new double[] {v.x, v.y, v.z};
    }

    static Vec3 vec(double[] a) {
        return new Vec3(a[0], a[1], a[2]);
    }
}
