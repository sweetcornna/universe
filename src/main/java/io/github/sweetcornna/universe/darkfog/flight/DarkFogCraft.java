package io.github.sweetcornna.universe.darkfog.flight;

import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.core.darkfog.DarkFogLevels;
import io.github.sweetcornna.universe.core.darkfog.FlightPlan;
import io.github.sweetcornna.universe.darkfog.DarkFogEnemy;
import io.github.sweetcornna.universe.darkfog.entity.DarkFogDamage;
import io.github.sweetcornna.universe.registry.UniverseEntities;
import io.github.sweetcornna.universe.registry.UniverseSounds;
import io.github.sweetcornna.universe.registry.sound.HostileSounds;
import io.github.sweetcornna.universe.survival.SurvivalDamage;
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
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * 飞着的中继站和种子（ADR-0028 §八）：只在起飞、到达的那一段有，由 {@link DarkFogFlights} 在有人看得到的时候放出来，
 * 按时间沿着定好的路线走（不受重力、不撞地形），路上打得掉。不存档，飞行的进度存在 {@link DarkFogFlightData} 里。
 *
 * <p>往星球上降落的中继站算高空目标（{@link #highAltitude()}），导弹炮塔、等离子炮塔打得到。黑雾自己的伤害不吃。
 */
public class DarkFogCraft extends Mob implements DarkFogEnemy, Enemy {
    private static final EntityDataAccessor<Integer> DATA_LEVEL = SynchedEntityData.defineId(DarkFogCraft.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_DESCENDING = SynchedEntityData.defineId(DarkFogCraft.class, EntityDataSerializers.BOOLEAN);
    /** 0 级时的血量：中继站比地上的那座薄，种子硬一些（30 级的种子刚好在原版血量上限 1024 以内）。 */
    public static final float RELAY_HEALTH = 240;
    public static final float SEED_HEALTH = 460;
    /** 多远以内都画（格）：在太空里要远远就看得见。 */
    private static final double RENDER_RANGE = 640;
    /** 降落声提前多少刻放：声音里落地的那一下在 1.05 秒左右。 */
    private static final int LANDING_LEAD = 21;

    /** 怎么走：直线（起飞、种子进入恒星系），或者从高空落到落点（中继站降落）。 */
    enum Path {
        LINE,
        DESCENT
    }

    private int flightId = -1;
    private Path path = Path.LINE;
    private Vec3 origin = Vec3.ZERO;
    private Vec3 direction = new Vec3(1, 0, 0);
    private double speed;
    private long start;
    private long stop;
    /** 降落声放过了（不存档：重新放出来时已经过了那一段就不再放）。 */
    private boolean landingSounded;

    public DarkFogCraft(EntityType<? extends DarkFogCraft> type, Level level) {
        super(type, level);
        this.setNoGravity(true);
        this.setPersistenceRequired();
        this.xpReward = 20;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, RELAY_HEALTH)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.ARMOR, 6)
                .add(Attributes.MOVEMENT_SPEED, 0);
    }

    /**
     * 造一个（还没加进世界）：沿直线从 {@code origin} 朝 {@code direction} 每刻走 {@code speed} 格，{@code start} 刻时在起点。
     */
    static DarkFogCraft line(ServerLevel level, boolean seed, int flightId, int darkFogLevel, float health, Vec3 origin, Vec3 direction, double speed,
            long start, long stop) {
        DarkFogCraft craft = create(level, seed, flightId, darkFogLevel, health);
        craft.path = Path.LINE;
        craft.origin = origin;
        craft.direction = direction.lengthSqr() < 1e-9 ? new Vec3(1, 0, 0) : direction.normalize();
        craft.speed = speed;
        craft.start = start;
        craft.stop = stop;
        craft.moveAlong(now(level));
        return craft;
    }

    /** 造一个往 {@code ground} 降落的中继站：{@code start} 刻时在它上空 {@link FlightPlan#DESCENT_HEIGHT} 格，{@code stop} 刻落地。 */
    static DarkFogCraft descent(ServerLevel level, int flightId, int darkFogLevel, float health, Vec3 ground, long start, long stop) {
        DarkFogCraft craft = create(level, false, flightId, darkFogLevel, health);
        craft.path = Path.DESCENT;
        craft.origin = ground;
        craft.start = start;
        craft.stop = stop;
        craft.entityData.set(DATA_DESCENDING, true);
        craft.moveAlong(now(level));
        return craft;
    }

    private static DarkFogCraft create(ServerLevel level, boolean seed, int flightId, int darkFogLevel, float health) {
        DarkFogCraft craft = new DarkFogCraft(seed ? UniverseEntities.DARK_FOG_SEED.get() : UniverseEntities.DARK_FOG_FLYING_RELAY.get(), level);
        craft.flightId = flightId;
        craft.entityData.set(DATA_LEVEL, DarkFogLevels.clamp(darkFogLevel));
        craft.getAttribute(Attributes.MAX_HEALTH).setBaseValue((seed ? SEED_HEALTH : RELAY_HEALTH) * DarkFogLevels.scale(darkFogLevel));
        craft.setHealth(Math.max(1, craft.getMaxHealth() * health));
        return craft;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_LEVEL, 0);
        builder.define(DATA_DESCENDING, false);
    }

    /** 是种子（不是中继站）。 */
    public boolean seed() {
        return this.getType() == UniverseEntities.DARK_FOG_SEED.get();
    }

    /** 属于哪一次飞行（{@link DarkFogFlightData} 里的编号）。 */
    public int flightId() {
        return this.flightId;
    }

    /** 飞行按主世界的时间算（和存档里的时刻一致）。 */
    static long now(ServerLevel level) {
        return level.getServer().overworld().getGameTime();
    }

    /** 这一刻在路线上的哪里。 */
    Vec3 pathAt(long now) {
        double t = this.stop <= this.start ? 1 : Mth.clamp((now - this.start) / (double) (this.stop - this.start), 0, 1);
        if (this.path == Path.DESCENT) {
            return this.origin.add(0, FlightPlan.descentHeight(t), 0);
        }
        double ticks = Mth.clamp(now - this.start, 0, Math.max(0, this.stop - this.start));
        return this.origin.add(this.direction.scale(this.speed * ticks));
    }

    private void moveAlong(long now) {
        Vec3 at = this.pathAt(now);
        Vec3 next = this.pathAt(now + 1);
        Vec3 heading = this.path == Path.DESCENT ? this.direction : next.subtract(at);
        float yaw = heading.horizontalDistanceSqr() < 1e-6 ? this.getYRot() : (float) (Mth.atan2(heading.z, heading.x) * Mth.RAD_TO_DEG) - 90;
        this.snapTo(at.x, at.y, at.z, yaw, 0);
        this.yBodyRot = yaw;
        this.yHeadRot = yaw;
    }

    @Override
    public void tick() {
        super.tick();
        this.setDeltaMovement(Vec3.ZERO);
        if (this.level() instanceof ServerLevel level) {
            if (this.flightId >= 0 && this.isAlive()) {
                long now = now(level);
                this.moveAlong(now);
                this.soundLanding(level, now);
            }
        } else {
            this.trail();
        }
    }

    /** 中继站快落地时在落点放一次降落声，落地的那一下对上 {@code stop}。 */
    private void soundLanding(ServerLevel level, long now) {
        if (this.path != Path.DESCENT || this.landingSounded || now < this.stop - LANDING_LEAD || now > this.stop) {
            return;
        }
        this.landingSounded = true;
        level.playSound(null, this.origin.x, this.origin.y, this.origin.z, HostileSounds.RELAY_LAND.get(), SoundSource.HOSTILE, 1.0F, 1.0F);
    }

    /** 尾迹：中继站紫色，种子红色；降落的中继站底下喷着。 */
    private void trail() {
        int color = this.seed() ? 0xFF2A48 : 0xB040FF;
        int count = this.seed() ? 2 : 1;
        for (int i = 0; i < count; i++) {
            double y = this.descending() ? this.getY() - 0.2 : this.getY(0.5);
            this.level().addParticle(new DustParticleOptions(color, 1.4F), this.getRandomX(0.6), y, this.getRandomZ(0.6), 0,
                    this.descending() ? -0.05 : 0, 0);
        }
        if (this.random.nextInt(this.seed() ? 3 : 6) == 0) {
            this.level().addParticle(ParticleTypes.ELECTRIC_SPARK, this.getRandomX(0.8), this.getRandomY(), this.getRandomZ(0.8), 0, 0, 0);
        }
    }

    @Override
    public void travel(Vec3 input) {
        // 位置按路线算，不走物理
    }

    public boolean descending() {
        return this.entityData.get(DATA_DESCENDING);
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

    @Override
    public boolean highAltitude() {
        return this.descending();
    }

    // ---- 受击、死亡

    /** 只吃玩家这一边的伤害：黑雾自己的、守卫机的、卡墙、缺氧这类环境伤害都不算；{@code /kill} 照样有效。 */
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
        if (hurt && !this.isRemoved()) {
            DarkFogFlights.onCraftHurt(level, this, before - this.getHealth());
        }
        return hurt;
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (this.level() instanceof ServerLevel level) {
            double y = this.getY(0.5);
            level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, this.getX(), y, this.getZ(), 1, 0, 0, 0, 0);
            level.sendParticles(new DustParticleOptions(this.seed() ? 0xFF2A48 : 0xB040FF, 1.5F), this.getX(), y, this.getZ(), 40, 1, 1, 1, 0);
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, this.getX(), y, this.getZ(), 40, 1, 1, 1, 0.4);
            level.playSound(null, this.getX(), y, this.getZ(), UniverseSounds.EXPLOSION_LARGE.get(), SoundSource.HOSTILE, 1.0F, 1.0F);
            DarkFogFlights.onCraftDestroyed(level, this, source);
        }
    }

    @Override
    protected void tickDeath() {
        this.deathTime++;
        if (this.deathTime >= 3 && !this.level().isClientSide() && !this.isRemoved()) {
            this.remove(Entity.RemovalReason.KILLED);
        }
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < RENDER_RANGE * RENDER_RANGE;
    }

    @Override
    protected @Nullable SoundEvent getAmbientSound() {
        return HostileSounds.CRAFT_HUM.get();
    }

    @Override
    public int getAmbientSoundInterval() {
        return 160;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return HostileSounds.BUILDING_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return HostileSounds.UNIT_DEATH.get();
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
