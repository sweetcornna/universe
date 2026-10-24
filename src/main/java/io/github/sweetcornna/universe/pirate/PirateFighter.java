package io.github.sweetcornna.universe.pirate;

import java.util.UUID;

import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.core.ship.PirateFlight;
import io.github.sweetcornna.universe.core.ship.PirateRaid;
import io.github.sweetcornna.universe.core.ship.ShipCombat;
import io.github.sweetcornna.universe.core.space.Encounters;
import io.github.sweetcornna.universe.encounter.SpaceEncounters;
import io.github.sweetcornna.universe.entity.PlasmaBolt;
import io.github.sweetcornna.universe.entity.ship.ShipEntity;
import io.github.sweetcornna.universe.registry.UniverseEntities;
import io.github.sweetcornna.universe.registry.UniverseSounds;
import io.github.sweetcornna.universe.registry.sound.ShipSounds;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.InterpolationHandler;
import net.minecraft.world.entity.LinearInterpolationHandler;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * 海盗战机（ADR-0017）：太空里的小型战斗机，24 点船体、16 点护盾（3 秒没挨打后回护盾），按 {@link PirateFlight} 绕着玩家的飞船
 * 一轮一轮地俯冲、交叉射击，残血时拉开距离。打下来掉海盗残骸、给赏金（{@link Pirates#onDestroyed}）。
 *
 * <p>战机由 {@link Pirates} 按袭击生成，不存档；袭击结束、找不到目标时撤走，飞远了消失。机头朝着速度的方向，
 * 偏航和俯仰由服务端按速度算好同步，客户端按偏航的变化算出转弯时的侧倾。
 *
 * <p>遇险信号（ADR-0024）里的海盗由 {@link SpaceEncounters} 生成：先围着遇险的货船打，玩家飞近或者开火才转过来打玩家。
 * 太空守卫机的拦截机（{@link io.github.sweetcornna.universe.encounter.SentinelInterceptor}）是它的子类，飞法一样。
 */
public class PirateFighter extends Entity implements Enemy {
    private static final EntityDataAccessor<Float> DATA_HULL = SynchedEntityData.defineId(PirateFighter.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_SHIELD = SynchedEntityData.defineId(PirateFighter.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> DATA_LEAVING = SynchedEntityData.defineId(PirateFighter.class, EntityDataSerializers.BOOLEAN);
    /** 实体事件：护盾挡住了、船体挨了打、开火（客户端放粒子）。 */
    private static final byte EVENT_SHIELD_HIT = 100;
    private static final byte EVENT_HULL_HIT = 101;
    private static final byte EVENT_FIRE = 102;
    /** 海盗的炮火：品红色。 */
    public static final int BOLT_COLOR = 0xFF3B6B;
    /**
     * 每发的伤害和散布（原版弹射物的 inaccuracy，7 约合 ±7°）：远处多半打偏，贴近了才准。按离线模拟调：三架海盗围攻一艘
     * 两门炮、一台护盾的船，开发脚本那样的驾驶几乎总能全歼，四架时一成左右被击毁；真人瞄得没那么准，会更吃力一些。
     */
    private static final float BOLT_DAMAGE = 3.0F;
    private static final float BOLT_SPREAD = 7.0F;
    private static final int BOLT_LIFETIME = 40;
    /** 撤走后飞多少刻消失。 */
    private static final int LEAVE_TICKS = 80;
    /** 机头离中心多远（炮口、尾焰的位置）。 */
    private static final double NOSE = 1.4;
    private static final DustParticleOptions SHIELD_DUST = new DustParticleOptions(0xFF7FA8, 1.0F);
    private static final ColorParticleOption MUZZLE_FLASH = ColorParticleOption.create(ParticleTypes.FLASH, 0xFFFF6A8A);
    private static final DustParticleOptions ENGINE_DUST = new DustParticleOptions(0xFF5A3A, 0.8F);

    private final double maxHull;
    private final double maxShield;
    private final ShipCombat combat;
    private @Nullable PirateFlight flight;
    /** 被袭击的玩家。 */
    private @Nullable UUID targetId;
    /** 围攻的货船中心和半径（遇险信号）；被惹到以前只打它。 */
    private @Nullable Vec3 siege;
    private double siegeClearance;
    private boolean provoked;
    private int leaveTicks = -1;
    /** 服务端：这一刻速度变了多少（飞船的自动瞄准按它预判转弯）。 */
    private Vec3 acceleration = Vec3.ZERO;
    /** 客户端：平滑过的侧倾角（度）。 */
    public float bank;
    public float bankO;

    public PirateFighter(EntityType<? extends PirateFighter> type, Level level) {
        this(type, level, PirateRaid.FIGHTER_HULL, PirateRaid.FIGHTER_SHIELD);
    }

    protected PirateFighter(EntityType<? extends PirateFighter> type, Level level, double hull, double shield) {
        super(type, level);
        this.maxHull = hull;
        this.maxShield = shield;
        this.combat = new ShipCombat(hull, shield);
        this.noPhysics = false;
    }

    /** 服务端：为袭击 {@code targetId} 造一架（第 {@code slot} 架，一共 {@code count} 架）。 */
    static PirateFighter create(ServerLevel level, UUID targetId, int slot, int count, Vec3 pos, Vec3 velocity) {
        PirateFighter fighter = new PirateFighter(UniverseEntities.PIRATE_FIGHTER.get(), level);
        fighter.launch(targetId, new PirateFlight(level.getRandom().nextLong(), slot, count), pos, velocity);
        return fighter;
    }

    /** 服务端：遇险信号里围攻货船的一架（ADR-0024）。{@code targetId} 是收到信号的玩家。 */
    public static PirateFighter raider(ServerLevel level, UUID targetId, int slot, int count, Vec3 pos, Vec3 siege, double clearance) {
        PirateFighter fighter = create(level, targetId, slot, count, pos, siege.subtract(pos).normalize().scale(PirateFlight.SPEED));
        fighter.siege = siege;
        fighter.siegeClearance = clearance;
        return fighter;
    }

    /** 放到 {@code pos}，带上飞法和目标。 */
    protected void launch(UUID targetId, PirateFlight flight, Vec3 pos, Vec3 velocity) {
        this.targetId = targetId;
        this.flight = flight;
        this.setPos(pos);
        this.setDeltaMovement(velocity);
        this.face(velocity);
        this.syncCombat();
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_HULL, (float) PirateRaid.FIGHTER_HULL);
        builder.define(DATA_SHIELD, (float) PirateRaid.FIGHTER_SHIELD);
        builder.define(DATA_LEAVING, false);
    }

    public float hull() {
        return this.entityData.get(DATA_HULL);
    }

    public float shield() {
        return this.entityData.get(DATA_SHIELD);
    }

    public double maxHull() {
        return this.maxHull;
    }

    public double maxShield() {
        return this.maxShield;
    }

    /** 围攻货船的海盗还没被惹到。 */
    public boolean besieging() {
        return this.siege != null && !this.provoked;
    }

    public boolean leaving() {
        return this.entityData.get(DATA_LEAVING);
    }

    /** 这一刻的加速度（格/刻²，服务端）。 */
    public Vec3 acceleration() {
        return this.acceleration;
    }

    public @Nullable UUID targetId() {
        return this.targetId;
    }

    /** 袭击结束：撤走，飞一会儿后消失。 */
    public void leave() {
        if (this.leaveTicks < 0) {
            this.leaveTicks = 0;
            this.entityData.set(DATA_LEAVING, true);
            if (this.flight != null) {
                this.flight.leave();
            }
        }
    }

    private void syncCombat() {
        this.entityData.set(DATA_HULL, (float) this.combat.hull());
        this.entityData.set(DATA_SHIELD, (float) this.combat.shield());
    }

    // ---- 实体行为

    @Override
    public boolean isPickable() {
        return !this.isRemoved();
    }

    @Override
    public boolean isAttackable() {
        return true;
    }

    @Override
    public boolean canBeCollidedWith(@Nullable Entity other) {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected InterpolationHandler createInterpolationHandler() {
        return LinearInterpolationHandler.create(this, 2);
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        if (this.isRemoved() || this.leaving() || damage <= 0 || source.getEntity() instanceof PirateFighter) {
            return false;
        }
        // 围攻货船的海盗挨了打就转过来
        this.provoked = true;
        ShipCombat.Hit hit = this.combat.damage(damage);
        this.syncCombat();
        if (hit == ShipCombat.Hit.DESTROYED) {
            this.explode(level, source);
            return true;
        }
        level.broadcastEntityEvent(this, hit == ShipCombat.Hit.SHIELD ? EVENT_SHIELD_HIT : EVENT_HULL_HIT);
        if (hit == ShipCombat.Hit.SHIELD) {
            // 战机比玩家的船小一号，同一套受击声往高处放
            this.playSound(ShipSounds.SHIELD_HIT.get(), 0.7F, 1.2F + this.random.nextFloat() * 0.1F);
        } else {
            this.playSound(ShipSounds.HULL_HIT.get(), 0.7F, 1.2F + this.random.nextFloat() * 0.1F);
        }
        return true;
    }

    private void explode(ServerLevel level, DamageSource source) {
        Vec3 c = this.getBoundingBox().getCenter();
        level.sendParticles(ParticleTypes.EXPLOSION, c.x, c.y, c.z, 3, 0.5, 0.3, 0.5, 0);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, c.x, c.y, c.z, 16, 0.6, 0.3, 0.6, 0.04);
        level.sendParticles(ParticleTypes.LAVA, c.x, c.y, c.z, 8, 0.4, 0.2, 0.4, 0);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, c.x, c.y, c.z, 24, 0.5, 0.3, 0.5, 0.4);
        level.playSound(null, c.x, c.y, c.z, UniverseSounds.EXPLOSION_SMALL.get(), SoundSource.HOSTILE, 1.0F, 1.0F + this.random.nextFloat() * 0.15F);
        this.destroyed(level, source);
        this.discard();
    }

    /** 被打下来以后：记战绩、给赏金、掉残骸。 */
    protected void destroyed(ServerLevel level, DamageSource source) {
        Pirates.onDestroyed(this, level, source);
    }

    /** 还属于一场没结束的袭击（不属于的会撤走）。 */
    protected boolean belongs() {
        return Pirates.belongs(this) || SpaceEncounters.belongs(this);
    }

    /** 炮火的颜色。 */
    protected int boltColor() {
        return BOLT_COLOR;
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level() instanceof ServerLevel level) {
            this.serverTick(level);
        } else {
            this.clientTick();
        }
    }

    private void serverTick(ServerLevel level) {
        if (this.leaveTicks >= 0 && ++this.leaveTicks > LEAVE_TICKS) {
            this.vanish(level);
            return;
        }
        Entity target = this.targetId == null ? null : level.getPlayerByUUID(this.targetId);
        if (target == null || !target.isAlive() || target.level() != level || this.flight == null || !this.belongs()) {
            this.leave();
        }
        if (this.flight == null) {
            this.vanish(level);
            return;
        }
        this.combat.tick(this.maxShield, false, 0, false);
        if (this.tickCount % 5 == 0) {
            this.syncCombat();
        }

        Vec3 pos = this.getBoundingBox().getCenter();
        Vec3 vel = this.getDeltaMovement();
        Entity aim = target == null ? null : target.getRootVehicle();
        Vec3 targetPos;
        Vec3 targetVel;
        double clearance;
        if (this.siege != null && !this.provoked && aim != null && aim.distanceToSqr(this) < Encounters.PROVOKE_RANGE * Encounters.PROVOKE_RANGE) {
            this.provoked = true;
        }
        if (this.siege != null && this.besieging() && !this.leaving()) {
            // 围着遇险的货船打
            targetPos = this.siege;
            targetVel = Vec3.ZERO;
            clearance = this.siegeClearance;
        } else if (aim instanceof ShipEntity ship && ship.structure() != null) {
            targetPos = ship.position().add(0, ship.structure().shape().height() / 2.0, 0);
            targetVel = ship.getDeltaMovement();
            clearance = ship.structure().shape().radius() + 1;
        } else if (aim != null) {
            targetPos = aim.getBoundingBox().getCenter();
            targetVel = aim.getDeltaMovement();
            clearance = 1;
        } else {
            // 没有目标（只会是在撤走）：继续沿着来的方向飞
            targetPos = pos.subtract(vel.scale(20));
            targetVel = Vec3.ZERO;
            clearance = 0;
        }
        PirateFlight.Step step = this.flight.step(arr(pos), arr(vel), arr(targetPos), arr(targetVel), clearance,
                this.combat.hull() / this.maxHull, this.combat.shield() / this.maxShield);
        Vec3 next = vec(step.velocity());
        this.acceleration = next.subtract(vel);
        this.setDeltaMovement(next);
        this.move(MoverType.SELF, next);
        // 撞墙了就弹开一点，别贴着方块磨
        if (this.horizontalCollision || this.verticalCollision) {
            this.setDeltaMovement(next.scale(-0.5));
        }
        // 太空维度上下只有几百格：别飞出世界
        double y = Mth.clamp(this.getY(), level.getMinY() + 24, level.getMaxY() + 200);
        if (y != this.getY()) {
            this.setPos(this.getX(), y, this.getZ());
        }
        this.face(this.getDeltaMovement());
        if (step.fire() && (aim != null || this.besieging())) {
            this.fire(level, targetPos, targetVel);
        }
    }

    /** 机头朝着速度的方向。 */
    private void face(Vec3 v) {
        double horizontal = Math.sqrt(v.x * v.x + v.z * v.z);
        if (v.lengthSqr() < 1e-6) {
            return;
        }
        this.setYRot((float) Math.toDegrees(Math.atan2(-v.x, v.z)));
        this.setXRot((float) -Math.toDegrees(Math.atan2(v.y, horizontal)));
    }

    private Vec3 nose() {
        return this.getBoundingBox().getCenter().add(this.getLookAngle().scale(NOSE));
    }

    private void fire(ServerLevel level, Vec3 targetPos, Vec3 targetVel) {
        Vec3 from = this.nose();
        // 目标（玩家的船）先于战机结算：弹体下一刻飞第一步时船也刚好多走一步，按现在的位置算提前量就对
        Vec3 relative = targetVel.subtract(this.getDeltaMovement());
        Vec3 to = targetPos.subtract(from);
        Vec3 direction = vec(ShipCombat.lead(arr(to), arr(relative), PirateFlight.BOLT_SPEED));
        PlasmaBolt bolt = PlasmaBolt.shoot(level, this, from, direction, this.boltDamage(level), this.boltColor(), PirateFlight.BOLT_SPEED, BOLT_SPREAD,
                BOLT_LIFETIME);
        bolt.setDeltaMovement(bolt.getDeltaMovement().add(this.getDeltaMovement()));
        level.addFreshEntity(bolt);
        level.playSound(null, from.x, from.y, from.z, ShipSounds.PIRATE_LASER.get(), SoundSource.HOSTILE, 0.8F, 0.95F + this.random.nextFloat() * 0.1F);
        level.broadcastEntityEvent(this, EVENT_FIRE);
    }

    /** 一发等离子弹的伤害。 */
    protected float boltDamage(ServerLevel level) {
        return BOLT_DAMAGE;
    }

    /** 飞远了、撤走了：一团粒子后消失。 */
    private void vanish(ServerLevel level) {
        Vec3 c = this.getBoundingBox().getCenter();
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, c.x, c.y, c.z, 20, 0.5, 0.3, 0.5, 0.1);
        this.discard();
    }

    /** 客户端：尾焰、侧倾。 */
    private void clientTick() {
        this.bankO = this.bank;
        float turn = Mth.wrapDegrees(this.getYRot() - this.yRotO);
        float wanted = Mth.clamp(-turn * 6, -55, 55);
        this.bank += (wanted - this.bank) * 0.25F;
        Vec3 back = this.getBoundingBox().getCenter().subtract(this.getLookAngle().scale(NOSE));
        Vec3 v = this.getDeltaMovement();
        for (int side = -1; side <= 1; side += 2) {
            Vec3 right = this.getLookAngle().cross(new Vec3(0, 1, 0));
            if (right.lengthSqr() < 1e-6) {
                right = new Vec3(1, 0, 0);
            }
            Vec3 p = back.add(right.normalize().scale(side * 0.35));
            this.level().addParticle(ENGINE_DUST, p.x, p.y, p.z, v.x * 0.3, v.y * 0.3, v.z * 0.3);
            if (this.random.nextInt(2) == 0) {
                this.level().addParticle(ParticleTypes.FLAME, p.x, p.y, p.z, v.x * 0.2, v.y * 0.2, v.z * 0.2);
            }
        }
    }

    @Override
    public void handleEntityEvent(byte id) {
        Vec3 c = this.getBoundingBox().getCenter();
        switch (id) {
            case EVENT_SHIELD_HIT -> {
                for (int i = 0; i < 28; i++) {
                    double theta = this.random.nextDouble() * Math.PI * 2;
                    double cos = this.random.nextDouble() * 2 - 1;
                    double sin = Math.sqrt(1 - cos * cos);
                    this.level().addParticle(SHIELD_DUST, c.x + 1.5 * sin * Math.cos(theta), c.y + 0.9 * cos, c.z + 1.5 * sin * Math.sin(theta),
                            0, 0, 0);
                }
            }
            case EVENT_HULL_HIT -> {
                for (int i = 0; i < 8; i++) {
                    this.level().addParticle(i % 3 == 0 ? ParticleTypes.LARGE_SMOKE : ParticleTypes.ELECTRIC_SPARK, c.x, c.y, c.z,
                            (this.random.nextDouble() - 0.5) * 0.4, this.random.nextDouble() * 0.2, (this.random.nextDouble() - 0.5) * 0.4);
                }
            }
            case EVENT_FIRE -> {
                Vec3 p = this.nose();
                this.level().addParticle(MUZZLE_FLASH, p.x, p.y, p.z, 0, 0, 0);
            }
            default -> super.handleEntityEvent(id);
        }
    }

    // ---- 不存档

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    static double[] arr(Vec3 v) {
        return new double[] {v.x, v.y, v.z};
    }

    static Vec3 vec(double[] v) {
        return new Vec3(v[0], v[1], v[2]);
    }
}
