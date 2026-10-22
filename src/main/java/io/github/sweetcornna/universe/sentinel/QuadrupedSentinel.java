package io.github.sweetcornna.universe.sentinel;

import java.util.EnumSet;

import io.github.sweetcornna.universe.core.sentinel.SentinelKind;
import io.github.sweetcornna.universe.entity.PlasmaBolt;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * 四足机（ADR-0026）：地面单位，跑得快。离得近了跃起扑过去；隔一阵停下来蓄力 2 秒，射一道光束。蓄力时头上的灯越来越亮、
 * 嗡嗡作响，最后半秒不再跟着目标瞄、地上画出一条虚线，这时横着跑开就躲得过去。
 */
public class QuadrupedSentinel extends GroundSentinel {
    private static final EntityDataAccessor<Integer> DATA_CHARGE = SynchedEntityData.defineId(QuadrupedSentinel.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_POUNCING = SynchedEntityData.defineId(QuadrupedSentinel.class,
            EntityDataSerializers.BOOLEAN);
    public static final float MAX_HEALTH = 80;
    /** 光束蓄力多少刻。 */
    public static final int CHARGE_TICKS = 40;
    /** 蓄力的最后这么多刻不再跟着目标瞄。 */
    static final int AIM_LOCK = 10;
    public static final float BEAM_DAMAGE = 8;
    static final double BEAM_RANGE = 28;
    public static final float POUNCE_DAMAGE = 6;
    static final int BEAM_COLOR = 0xFF3B30;
    private static final int AIM_COLOR = 0xFF9A8A;

    private int beamCooldown = 60;
    private int pounceCooldown = 30;
    /** 起跳后过了几刻；-1 表示没在扑。 */
    private int pounceTicks = -1;
    private boolean pounceHit;
    private Vec3 aim = Vec3.ZERO;

    public QuadrupedSentinel(EntityType<? extends QuadrupedSentinel> type, Level level) {
        super(type, level);
        this.xpReward = 20;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, MAX_HEALTH)
                .add(Attributes.MOVEMENT_SPEED, 0.34)
                .add(Attributes.FOLLOW_RANGE, 48)
                .add(Attributes.ARMOR, 6)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.6)
                .add(Attributes.STEP_HEIGHT, 1.0)
                .add(Attributes.ATTACK_DAMAGE, POUNCE_DAMAGE);
    }

    @Override
    public SentinelKind kind() {
        return SentinelKind.QUADRUPED;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder entityData) {
        super.defineSynchedData(entityData);
        entityData.define(DATA_CHARGE, 0);
        entityData.define(DATA_POUNCING, false);
    }

    /** 光束蓄力还剩几刻；0 表示没在蓄力。 */
    public int charge() {
        return this.entityData.get(DATA_CHARGE);
    }

    public boolean isPouncing() {
        return this.entityData.get(DATA_POUNCING);
    }

    @Override
    protected Goal engageGoal() {
        return new EngageGoal();
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide() || this.pounceTicks < 0) {
            return;
        }
        // 扑：在空中撞上目标就咬一口，落地就结束
        this.pounceTicks++;
        LivingEntity target = this.getTarget();
        if (!this.pounceHit && target != null && this.level() instanceof ServerLevel level
                && this.getBoundingBox().inflate(0.4).intersects(target.getBoundingBox())) {
            this.pounceHit = true;
            DamageSource source = this.damageSources().mobAttack(this);
            if (target.hurtServer(level, source, POUNCE_DAMAGE)) {
                target.knockback(0.8, this.getX() - target.getX(), this.getZ() - target.getZ(), source, POUNCE_DAMAGE);
            }
            this.playSound(SoundEvents.IRON_GOLEM_ATTACK, 1.0F, 1.4F);
        }
        if (this.pounceTicks > 4 && this.onGround() || this.pounceTicks > 40) {
            this.pounceTicks = -1;
            this.entityData.set(DATA_POUNCING, false);
            this.pounceCooldown = 70 + this.random.nextInt(40);
        }
    }

    /** 光束从头前面的发射口出来。 */
    Vec3 muzzle() {
        return this.position().add(forward(this.getYHeadRot()).scale(1.05)).add(0, 1.0, 0);
    }

    private void startCharge() {
        this.entityData.set(DATA_CHARGE, CHARGE_TICKS);
        this.getNavigation().stop();
        this.playSound(SoundEvents.GUARDIAN_ATTACK, 1.6F, 1.9F);
    }

    /** 蓄力的预警：发射口聚起越来越大的红光，最后半秒沿着锁定的方向画一条虚线。 */
    private void chargeEffects(ServerLevel level, int left) {
        Vec3 muzzle = this.muzzle();
        float grown = 1 - (float) left / CHARGE_TICKS;
        if (left % 2 == 0) {
            level.sendParticles(new DustParticleOptions(BEAM_COLOR, 0.6F + grown * 1.4F), muzzle.x, muzzle.y, muzzle.z, 2 + (int) (grown * 4),
                    0.08, 0.08, 0.08, 0);
        }
        if (left % 8 == 0) {
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, muzzle.x, muzzle.y, muzzle.z, 3, 0.15, 0.15, 0.15, 0.05);
        }
        if (left <= AIM_LOCK && left % 2 == 0) {
            Vec3 dir = this.aim.normalize();
            DustParticleOptions dust = new DustParticleOptions(AIM_COLOR, 0.5F);
            for (double d = 1; d < BEAM_RANGE; d += 1.5) {
                Vec3 p = muzzle.add(dir.scale(d));
                level.sendParticles(dust, p.x, p.y, p.z, 1, 0, 0, 0, 0);
            }
        }
    }

    /** 沿着锁定的方向射出光束：打中第一个挡在路上的生物（守卫机除外），被方块挡住就停在方块上。 */
    private void fireBeam(ServerLevel level) {
        Vec3 from = this.muzzle();
        Vec3 dir = this.aim.lengthSqr() < 1e-6 ? forward(this.getYHeadRot()) : this.aim.normalize();
        Vec3 to = from.add(dir.scale(BEAM_RANGE));
        BlockHitResult block = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        if (block.getType() != HitResult.Type.MISS) {
            to = block.getLocation();
        }
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(level, this, from, to, new AABB(from, to).inflate(1),
                QuadrupedSentinel::beamTarget, 0.3F);
        if (hit != null) {
            to = hit.getLocation();
            hit.getEntity().hurtServer(level, this.damageSources().source(PlasmaBolt.DAMAGE, this, this), BEAM_DAMAGE);
        }
        DustParticleOptions dust = new DustParticleOptions(BEAM_COLOR, 1.3F);
        double length = from.distanceTo(to);
        for (double d = 0; d < length; d += 0.3) {
            Vec3 p = from.add(dir.scale(d));
            level.sendParticles(dust, p.x, p.y, p.z, 1, 0.02, 0.02, 0.02, 0);
        }
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, to.x, to.y, to.z, 12, 0.2, 0.2, 0.2, 0.3);
        level.sendParticles(ParticleTypes.SMOKE, to.x, to.y, to.z, 6, 0.1, 0.1, 0.1, 0.02);
        level.playSound(null, from.x, from.y, from.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 0.5F, 1.9F);
        level.playSound(null, from.x, from.y, from.z, SoundEvents.BEACON_DEACTIVATE, SoundSource.HOSTILE, 0.8F, 2.0F);
    }

    private static boolean beamTarget(Entity entity) {
        return entity instanceof LivingEntity && !(entity instanceof Sentinel) && entity.isPickable() && !entity.isSpectator()
                && !(entity instanceof Player player && player.isCreative());
    }

    private void pounce(LivingEntity target) {
        Vec3 offset = target.position().subtract(this.position()).multiply(1, 0, 1);
        double distance = offset.length();
        Vec3 dir = distance < 1e-4 ? forward(this.yBodyRot) : offset.scale(1 / distance);
        double push = Math.min(1.3, 0.35 + distance * 0.11);
        this.getNavigation().stop();
        this.setDeltaMovement(dir.x * push, 0.42 + distance * 0.02, dir.z * push);
        this.pounceTicks = 0;
        this.pounceHit = false;
        this.entityData.set(DATA_POUNCING, true);
        this.playSound(SoundEvents.PISTON_EXTEND, 0.9F, 1.4F);
    }

    /**
     * 交战：远了、看不见就追；8 格左右绕着目标跑，太近了往后退。冷却好了就扑或者蓄力射光束，蓄力时站着不动。
     */
    private final class EngageGoal extends Goal {
        private int repath;
        private int strafeTime;
        private boolean clockwise;

        EngageGoal() {
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK, Goal.Flag.JUMP));
        }

        @Override
        public boolean canUse() {
            return QuadrupedSentinel.this.engaging();
        }

        @Override
        public void stop() {
            QuadrupedSentinel self = QuadrupedSentinel.this;
            self.getNavigation().stop();
            self.entityData.set(DATA_CHARGE, 0);
            self.setZza(0);
            self.setXxa(0);
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            QuadrupedSentinel self = QuadrupedSentinel.this;
            LivingEntity target = self.getTarget();
            if (target == null || !(self.level() instanceof ServerLevel level)) {
                return;
            }
            int charge = self.charge();
            if (charge > 0) {
                self.getNavigation().stop();
                self.getMoveControl().strafe(0, 0);
                if (charge > AIM_LOCK) {
                    self.lookAt(target, 12, 30);
                    self.getLookControl().setLookAt(target, 30, 30);
                    self.aim = target.getEyePosition().add(0, -0.4, 0).subtract(self.muzzle());
                }
                self.chargeEffects(level, charge);
                self.entityData.set(DATA_CHARGE, charge - 1);
                if (charge == 1) {
                    self.fireBeam(level);
                    self.beamCooldown = 90 + self.random.nextInt(50);
                }
                return;
            }
            self.lookAt(target, 8, 30);
            self.getLookControl().setLookAt(target, 30, 30);
            if (self.pounceTicks >= 0) {
                return;
            }
            self.beamCooldown--;
            self.pounceCooldown--;
            double distance = self.distanceTo(target);
            boolean sees = self.hasLineOfSight(target);
            if (self.onGround() && sees) {
                if (self.beamCooldown <= 0 && distance >= 3 && distance <= BEAM_RANGE && self.offAim(target) < 40) {
                    self.startCharge();
                    return;
                }
                if (self.pounceCooldown <= 0 && distance >= 2.5 && distance <= 9 && (distance < 4.5 || self.random.nextInt(15) == 0)) {
                    self.pounce(target);
                    return;
                }
            }
            if (distance > 12 || !sees) {
                if (--this.repath <= 0) {
                    this.repath = REPATH;
                    self.getNavigation().moveTo(target, 1.3);
                }
                return;
            }
            // 绕着目标跑：隔一阵换方向，撞墙就马上换
            self.getNavigation().stop();
            if (--this.strafeTime <= 0 || self.horizontalCollision) {
                this.strafeTime = 40 + self.random.nextInt(40);
                this.clockwise = !this.clockwise;
            }
            float forward = distance < 6 ? -0.5F : distance > 10 ? 0.5F : 0;
            self.getMoveControl().strafe(forward, this.clockwise ? 0.7F : -0.7F);
        }
    }
}
