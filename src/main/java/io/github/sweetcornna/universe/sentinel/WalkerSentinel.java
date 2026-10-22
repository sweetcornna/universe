package io.github.sweetcornna.universe.sentinel;

import java.util.EnumSet;

import io.github.sweetcornna.universe.core.sentinel.SentinelKind;
import io.github.sweetcornna.universe.entity.PlasmaBolt;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * 步行机（ADR-0026）：4 格高、两条腿的大型守卫机，走得慢、转身也慢。座舱两侧的炮连射重型光弹；有人贴近了就抬起一条腿，
 * 1 秒多以后踩下去，震伤、震退周围 5 格内站在地上的人（这时跳起来就躲开了）。绕到背后打散热口伤害翻倍。
 */
public class WalkerSentinel extends GroundSentinel {
    private static final EntityDataAccessor<Integer> DATA_STOMP = SynchedEntityData.defineId(WalkerSentinel.class, EntityDataSerializers.INT);
    public static final float MAX_HEALTH = 240;
    /** 抬腿到踩下去多少刻。 */
    public static final int STOMP_WINDUP = 24;
    static final double STOMP_RADIUS = 5;
    /** 离得这么近就会踩。 */
    static final double STOMP_TRIGGER = 4.5;
    public static final float STOMP_DAMAGE = 7;
    static final float BOLT_DAMAGE = 4;
    static final int BURST = 6;
    private static final int WARNING_COLOR = 0xFF5A3A;

    private int stompCooldown = 40;
    private int burstLeft;
    private int burstDelay;
    private int fireCooldown = 40;
    /** 两门炮轮流打。 */
    private boolean leftCannon;

    public WalkerSentinel(EntityType<? extends WalkerSentinel> type, Level level) {
        super(type, level);
        this.xpReward = 40;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, MAX_HEALTH)
                .add(Attributes.MOVEMENT_SPEED, 0.2)
                .add(Attributes.FOLLOW_RANGE, 48)
                .add(Attributes.ARMOR, 10)
                .add(Attributes.ARMOR_TOUGHNESS, 4)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.STEP_HEIGHT, 1.5)
                .add(Attributes.ATTACK_DAMAGE, STOMP_DAMAGE);
    }

    @Override
    public SentinelKind kind() {
        return SentinelKind.WALKER;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder entityData) {
        super.defineSynchedData(entityData);
        entityData.define(DATA_STOMP, 0);
    }

    /** 踩地还剩几刻；0 表示没在抬腿。 */
    public int stomp() {
        return this.entityData.get(DATA_STOMP);
    }

    @Override
    protected Goal engageGoal() {
        return new EngageGoal();
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    /** 一侧的炮口（座舱跟着视线转）。 */
    Vec3 muzzle(boolean left) {
        float yaw = this.getYHeadRot();
        Vec3 forward = forward(yaw);
        Vec3 side = new Vec3(forward.z, 0, -forward.x).scale(left ? 1 : -1);
        return this.position().add(forward.scale(1.3)).add(side.scale(1.05)).add(0, 3.15, 0);
    }

    private void fireAt(LivingEntity target) {
        double speed = 1.7;
        Vec3 from = this.muzzle(this.leftCannon);
        this.leftCannon = !this.leftCannon;
        Vec3 aim = target.getEyePosition().add(0, -0.4, 0);
        aim = aim.add(target.getDeltaMovement().multiply(1, 0, 1).scale(from.distanceTo(aim) / speed * 0.8));
        PlasmaBolt bolt = PlasmaBolt.shoot(this.level(), this, from, aim.subtract(from), BOLT_DAMAGE, HEAVY_BOLT_COLOR, speed, 4.0F, 40);
        this.level().addFreshEntity(bolt);
        this.playSound(SoundEvents.BLAZE_SHOOT, 0.9F, 0.6F + this.random.nextFloat() * 0.1F);
    }

    /** 谁站在踩地的范围里：守卫机、创造和旁观模式的人除外。 */
    private boolean stompable(LivingEntity entity) {
        return entity != this && entity.isAlive() && !(entity instanceof Sentinel)
                && !(entity instanceof Player player && (player.isCreative() || player.isSpectator()));
    }

    private boolean someoneClose() {
        return !this.level().getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(STOMP_TRIGGER, 1, STOMP_TRIGGER),
                e -> this.stompable(e) && this.distanceToSqr(e.getX(), this.getY(), e.getZ()) < STOMP_TRIGGER * STOMP_TRIGGER).isEmpty();
    }

    private void startStomp() {
        this.entityData.set(DATA_STOMP, STOMP_WINDUP);
        this.getNavigation().stop();
        this.burstLeft = 0;
        this.playSound(SoundEvents.RAVAGER_ROAR, 1.3F, 0.6F);
    }

    /** 抬腿时地上一圈红光，标出震到的范围。 */
    private void warn(ServerLevel level) {
        DustParticleOptions dust = new DustParticleOptions(WARNING_COLOR, 1.2F);
        for (int i = 0; i < 24; i++) {
            double angle = i * Math.PI * 2 / 24;
            level.sendParticles(dust, this.getX() + Math.cos(angle) * STOMP_RADIUS, this.getY() + 0.15, this.getZ() + Math.sin(angle) * STOMP_RADIUS,
                    1, 0.05, 0, 0.05, 0);
        }
    }

    /** 踩下去：5 格内站在地上的人挨震、被推开，离得越近越疼；跳在空中的不算。 */
    private void stompDown(ServerLevel level) {
        DamageSource source = this.damageSources().mobAttack(this);
        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(STOMP_RADIUS, 1.5, STOMP_RADIUS),
                this::stompable)) {
            double dx = entity.getX() - this.getX();
            double dz = entity.getZ() - this.getZ();
            double distance = Math.sqrt(dx * dx + dz * dz);
            if (distance > STOMP_RADIUS || !entity.onGround() && entity.getY() > this.getY() + 0.6) {
                continue;
            }
            float damage = (float) (STOMP_DAMAGE * (1 - 0.5 * distance / STOMP_RADIUS));
            if (entity.hurtServer(level, source, damage)) {
                entity.knockback(1.6 * (1 - distance / (STOMP_RADIUS * 1.6)), -dx, -dz, source, damage);
            }
        }
        BlockPos below = this.blockPosition().below();
        BlockState ground = level.getBlockState(below);
        if (!ground.isAir()) {
            BlockParticleOption dust = new BlockParticleOption(ParticleTypes.BLOCK, ground);
            for (int i = 0; i < 32; i++) {
                double angle = i * Math.PI * 2 / 32;
                double r = 1.5 + this.random.nextDouble() * (STOMP_RADIUS - 1.5);
                level.sendParticles(dust, this.getX() + Math.cos(angle) * r, this.getY() + 0.1, this.getZ() + Math.sin(angle) * r, 3, 0.2, 0.1, 0.2, 0.15);
            }
        }
        level.sendParticles(ParticleTypes.EXPLOSION, this.getX(), this.getY() + 0.3, this.getZ(), 2, 1.0, 0.1, 1.0, 0);
        level.sendParticles(ParticleTypes.POOF, this.getX(), this.getY() + 0.2, this.getZ(), 30, STOMP_RADIUS * 0.4, 0.1, STOMP_RADIUS * 0.4, 0.05);
        level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.MACE_SMASH_GROUND_HEAVY, SoundSource.HOSTILE, 1.6F, 0.6F);
        level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 0.9F, 0.5F);
    }

    /**
     * 交战：保持 8–16 格开火，远了、看不见就走过去，太近了往后退；有人贴到 4.5 格以内就踩地。转身慢，绕着它跑能绕到背后。
     */
    private final class EngageGoal extends Goal {
        private int repath;

        EngageGoal() {
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK, Goal.Flag.JUMP));
        }

        @Override
        public boolean canUse() {
            return WalkerSentinel.this.engaging();
        }

        @Override
        public void stop() {
            WalkerSentinel self = WalkerSentinel.this;
            self.getNavigation().stop();
            self.entityData.set(DATA_STOMP, 0);
            self.burstLeft = 0;
            self.setZza(0);
            self.setXxa(0);
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            WalkerSentinel self = WalkerSentinel.this;
            LivingEntity target = self.getTarget();
            if (target == null || !(self.level() instanceof ServerLevel level)) {
                return;
            }
            int stomp = self.stomp();
            if (stomp > 0) {
                self.getNavigation().stop();
                self.getMoveControl().strafe(0, 0);
                if (stomp % 4 == 0) {
                    self.warn(level);
                }
                self.entityData.set(DATA_STOMP, stomp - 1);
                if (stomp == 1) {
                    self.stompDown(level);
                    self.stompCooldown = 90 + self.random.nextInt(40);
                }
                return;
            }
            // 转身慢：座舱和机身每刻最多转 4°
            self.lookAt(target, 4, 20);
            self.getLookControl().setLookAt(target, 4, 20);
            self.stompCooldown--;
            if (self.stompCooldown <= 0 && self.onGround() && self.someoneClose()) {
                self.startStomp();
                return;
            }
            double distance = self.distanceTo(target);
            boolean sees = self.hasLineOfSight(target);
            if (distance > 16 || !sees) {
                if (--this.repath <= 0) {
                    this.repath = REPATH;
                    self.getNavigation().moveTo(target, 1.0);
                }
            } else {
                self.getNavigation().stop();
                self.getMoveControl().strafe(distance < 7 ? -0.6F : 0, 0);
            }
            if (self.fireCooldown > 0) {
                self.fireCooldown--;
                return;
            }
            if (self.burstLeft == 0) {
                if (distance < 32 && sees) {
                    self.burstLeft = BURST;
                    self.burstDelay = 0;
                } else {
                    return;
                }
            }
            if (--self.burstDelay <= 0) {
                // 炮只能朝前打：绕到侧面、背后的人打不着
                if (self.hasLineOfSight(target) && self.offAim(target) < 50) {
                    self.fireAt(target);
                }
                self.burstLeft--;
                self.burstDelay = 5;
                if (self.burstLeft == 0) {
                    self.fireCooldown = 60 + self.random.nextInt(30);
                }
            }
        }
    }
}
