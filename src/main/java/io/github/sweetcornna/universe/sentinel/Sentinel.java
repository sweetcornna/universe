package io.github.sweetcornna.universe.sentinel;

import java.util.EnumSet;
import java.util.List;

import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.core.sentinel.SentinelKind;
import io.github.sweetcornna.universe.entity.PlasmaBolt;
import io.github.sweetcornna.universe.registry.UniverseEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 守卫机（ADR-0014）：飞在离地 3–6 格的高度巡逻，偶尔停下来用黄光扫描附近的玩家。被 {@link Sentinels} 设为敌对后，
 * 绕着目标飞、保持距离，连发红色的等离子弹。重型守卫机血厚、护甲高，一轮打得更多。
 *
 * <p>守卫机不存档、离玩家太远就消失，都由 {@link Sentinels} 按玩家附近的情况补充。
 */
public class Sentinel extends PathfinderMob {
    private static final EntityDataAccessor<Boolean> DATA_HOSTILE = SynchedEntityData.defineId(Sentinel.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_SCANNING = SynchedEntityData.defineId(Sentinel.class, EntityDataSerializers.BOOLEAN);
    static final int DRONE_BOLT_COLOR = 0xFF4A3A;
    static final int HEAVY_BOLT_COLOR = 0xFF8A2A;
    private static final int SCAN_COLOR = 0xFFD34A;
    /** 没有敌意时，离最近的玩家超过这个距离就消失。 */
    private static final double LEASH = 80;

    private int burstLeft;
    private int burstDelay;
    private int cooldown = 20;
    private int scanCooldown = 100;
    /** 撤退（脱离通缉后）：往上飞一段时间后消失；-1 表示没在撤退。 */
    private int retreat = -1;

    public Sentinel(EntityType<? extends Sentinel> type, Level level) {
        super(type, level);
        this.moveControl = new FlyingMoveControl<>(this, 20, true);
        this.setNoGravity(true);
        this.setPathfindingMalus(PathType.WATER, -1.0F);
        this.setPathfindingMalus(PathType.FIRE, -1.0F);
        this.xpReward = this.isHeavy() ? 15 : 5;
    }

    public static AttributeSupplier.Builder createAttributes(boolean heavy) {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, heavy ? 60 : 20)
                .add(Attributes.FLYING_SPEED, heavy ? 0.4 : 0.6)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.FOLLOW_RANGE, 48)
                .add(Attributes.ARMOR, heavy ? 8 : 2)
                .add(Attributes.KNOCKBACK_RESISTANCE, heavy ? 1.0 : 0.4);
    }

    public boolean isHeavy() {
        return this.getType() == UniverseEntities.HEAVY_SENTINEL.get();
    }

    /** 哪一种守卫机；子类各自返回自己的种类。 */
    public SentinelKind kind() {
        return this.isHeavy() ? SentinelKind.HEAVY : SentinelKind.DRONE;
    }

    /** 交战时开不开火（维修、召唤无人机不开火）。 */
    protected boolean armed() {
        return true;
    }

    /** 交战时绕着目标飞的半径（再随机加 0–6 格）。 */
    protected double standOff() {
        return this.isHeavy() ? 9 : 7;
    }

    public boolean isHostile() {
        return this.entityData.get(DATA_HOSTILE);
    }

    public boolean isScanning() {
        return this.entityData.get(DATA_SCANNING);
    }

    /** 转为敌对并追捕 {@code target}，或者（{@code target} 为空）恢复巡逻。 */
    public void setHostile(@Nullable LivingEntity target) {
        this.entityData.set(DATA_HOSTILE, target != null);
        this.entityData.set(DATA_SCANNING, false);
        this.setTarget(target);
        if (target != null) {
            this.retreat = -1;
        }
    }

    /** 脱离通缉后撤退：往上飞走，几秒后消失。 */
    public void retreat() {
        this.setHostile(null);
        this.retreat = 0;
        this.getNavigation().stop();
    }

    /** 正在撤退（地面守卫机的 goal 用）。 */
    protected boolean retreating() {
        return this.retreat >= 0;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder entityData) {
        super.defineSynchedData(entityData);
        entityData.define(DATA_HOSTILE, false);
        entityData.define(DATA_SCANNING, false);
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        FlyingPathNavigation navigation = new FlyingPathNavigation(this, level);
        navigation.setCanOpenDoors(false);
        navigation.setCanFloat(true);
        return navigation;
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new EngageGoal());
        this.goalSelector.addGoal(3, new ScanGoal());
        this.goalSelector.addGoal(5, new PatrolGoal());
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide()) {
            // 机身下方推进器的微光
            if (this.kind().flying() && this.random.nextInt(3) == 0) {
                double spread = this.isHeavy() ? 0.6 : 0.3;
                this.level().addParticle(new DustParticleOptions(this.isHostile() ? 0xFF6A4A : 0x9AD8FF, 0.5F),
                        this.getX() + (this.random.nextDouble() - 0.5) * spread, this.getY() + 0.05,
                        this.getZ() + (this.random.nextDouble() - 0.5) * spread, 0, -0.05, 0);
            }
            return;
        }
        if (this.retreat >= 0) {
            // 飞的往上飞走，走路的原地传送走
            this.retreat++;
            if (this.kind().flying()) {
                this.setDeltaMovement(this.getDeltaMovement().scale(0.8).add(0, 0.08, 0));
            }
            if (this.retreat > (this.kind().flying() ? 60 : 20)) {
                this.vanish();
            }
            return;
        }
        if (this.isHostile()) {
            LivingEntity target = this.getTarget();
            if (target == null || !target.isAlive() || target.level() != this.level()
                    || target instanceof Player player && (player.isCreative() || player.isSpectator())
                    || this.level().getDifficulty() == Difficulty.PEACEFUL) {
                this.retreat();
            }
        } else if (this.tickCount % 100 == 0 && this.level().getNearestPlayer(this, LEASH) == null) {
            this.discard();
        }
        // 不要贴地飞
        if (this.kind().flying() && this.tickCount % 10 == 0 && this.getY() - this.groundY(this.getBlockX(), this.getBlockZ()) < 1.5) {
            this.setDeltaMovement(this.getDeltaMovement().add(0, 0.12, 0));
        }
    }

    /** 传送走的效果：一团粒子，然后消失。 */
    void vanish() {
        if (this.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.REVERSE_PORTAL, this.getX(), this.getY(0.5), this.getZ(), 20, 0.3, 0.3, 0.3, 0.05);
            level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.BEACON_DEACTIVATE, SoundSource.HOSTILE, 0.5F, 1.8F);
        }
        this.discard();
    }

    int groundY(int x, int z) {
        return this.level().getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        boolean hurt = super.hurtServer(level, source, damage);
        if (hurt && source.getEntity() instanceof ServerPlayer player) {
            Sentinels.onAttacked(player, this);
        }
        return hurt;
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (this.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.EXPLOSION, this.getX(), this.getY(0.5), this.getZ(), this.isHeavy() ? 3 : 1, 0.2, 0.2, 0.2, 0);
            level.sendParticles(ParticleTypes.LARGE_SMOKE, this.getX(), this.getY(0.5), this.getZ(), 12, 0.3, 0.3, 0.3, 0.02);
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, this.getX(), this.getY(0.5), this.getZ(), 16, 0.3, 0.3, 0.3, 0.3);
            level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE,
                    this.isHeavy() ? 1.2F : 0.6F, 1.4F);
            Sentinels.onDestroyed(this, source);
        }
    }

    // ---- 开火

    private void fireAt(LivingEntity target) {
        boolean heavy = this.isHeavy();
        double speed = heavy ? 1.9 : 1.6;
        Vec3 from = this.getEyePosition().add(this.getLookAngle().scale(heavy ? 0.9 : 0.55));
        Vec3 aim = target.getEyePosition().add(0, -0.35, 0);
        // 按目标的速度稍微提前量
        aim = aim.add(target.getDeltaMovement().multiply(1, 0, 1).scale(from.distanceTo(aim) / speed * 0.8));
        // 伤害和散布按骷髅弓箭的量级定：小型守卫机一梭三发有一半左右打偏，玩家有余地躲和还手
        PlasmaBolt bolt = PlasmaBolt.shoot(this.level(), this, from, aim.subtract(from), heavy ? 3.0F : 1.5F,
                heavy ? HEAVY_BOLT_COLOR : DRONE_BOLT_COLOR, speed, heavy ? 3.5F : 5.0F, 30);
        this.level().addFreshEntity(bolt);
        this.playSound(SoundEvents.BLAZE_SHOOT, heavy ? 0.6F : 0.4F, heavy ? 1.0F : 1.4F + this.random.nextFloat() * 0.2F);
    }

    // ---- 声音、杂项

    @Override
    protected @Nullable SoundEvent getAmbientSound() {
        return SoundEvents.BEACON_AMBIENT;
    }

    @Override
    public int getAmbientSoundInterval() {
        return 120;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.IRON_GOLEM_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.IRON_GOLEM_DEATH;
    }

    @Override
    protected float getSoundVolume() {
        return 0.5F;
    }

    @Override
    public float getVoicePitch() {
        return (this.isHeavy() ? 1.0F : 1.6F) + (this.random.nextFloat() - 0.5F) * 0.2F;
    }

    @Override
    protected void checkFallDamage(double ya, boolean onGround, BlockState onState, BlockPos pos) {
    }

    @Override
    public boolean causeFallDamage(double fallDistance, float damageModifier, DamageSource source) {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distSqr) {
        return true;
    }

    // ---- 行为

    /** 巡逻：在附近的玩家周围（没有玩家就在原地附近）找一个离地 3–6 格的点飞过去。 */
    private final class PatrolGoal extends Goal {
        PatrolGoal() {
            this.setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return !Sentinel.this.isHostile() && Sentinel.this.retreat < 0 && Sentinel.this.getNavigation().isDone()
                    && Sentinel.this.random.nextInt(20) == 0;
        }

        @Override
        public boolean canContinueToUse() {
            return !Sentinel.this.isHostile() && !Sentinel.this.getNavigation().isDone();
        }

        @Override
        public void start() {
            Sentinel self = Sentinel.this;
            Player player = self.level().getNearestPlayer(self, 48);
            double cx = player != null ? player.getX() : self.getX();
            double cz = player != null ? player.getZ() : self.getZ();
            double angle = self.random.nextDouble() * Math.PI * 2;
            double distance = player != null ? 8 + self.random.nextDouble() * 10 : self.random.nextDouble() * 12;
            int x = (int) Math.floor(cx + Math.cos(angle) * distance);
            int z = (int) Math.floor(cz + Math.sin(angle) * distance);
            double y = self.groundY(x, z) + 3 + self.random.nextDouble() * 3;
            self.getNavigation().moveTo(x + 0.5, y, z + 0.5, 1.0);
        }
    }

    /** 扫描：附近有玩家时停下来，对着玩家照几秒黄光。 */
    private final class ScanGoal extends Goal {
        private @Nullable Player player;
        private int time;

        ScanGoal() {
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            Sentinel self = Sentinel.this;
            if (self.isHostile() || self.retreat >= 0 || --self.scanCooldown > 0) {
                return false;
            }
            self.scanCooldown = 20;
            Player nearest = self.level().getNearestPlayer(self.getX(), self.getY(), self.getZ(), 14,
                    p -> !p.isSpectator() && p instanceof LivingEntity living && self.hasLineOfSight(living));
            if (nearest == null) {
                return false;
            }
            this.player = nearest;
            return true;
        }

        @Override
        public boolean canContinueToUse() {
            return this.player != null && this.player.isAlive() && !Sentinel.this.isHostile() && this.time > 0
                    && Sentinel.this.distanceToSqr(this.player) < 20 * 20;
        }

        @Override
        public void start() {
            Sentinel self = Sentinel.this;
            this.time = 60 + self.random.nextInt(40);
            self.getNavigation().stop();
            self.entityData.set(DATA_SCANNING, true);
            self.playSound(SoundEvents.BEACON_POWER_SELECT, 0.6F, 1.8F);
        }

        @Override
        public void stop() {
            Sentinel self = Sentinel.this;
            self.entityData.set(DATA_SCANNING, false);
            self.scanCooldown = 400 + self.random.nextInt(400);
            this.player = null;
        }

        @Override
        public void tick() {
            Sentinel self = Sentinel.this;
            this.time--;
            if (this.player == null) {
                return;
            }
            self.getLookControl().setLookAt(this.player, 30, 30);
            if (this.time % 10 == 0 && self.level() instanceof ServerLevel level) {
                Vec3 from = self.getEyePosition();
                Vec3 to = this.player.position().add(0, this.player.getBbHeight() * 0.5, 0);
                DustParticleOptions dust = new DustParticleOptions(SCAN_COLOR, 0.6F);
                int steps = (int) (from.distanceTo(to) / 0.5);
                for (int i = 1; i < steps; i++) {
                    Vec3 p = from.lerp(to, i / (double) steps);
                    level.sendParticles(dust, p.x, p.y, p.z, 1, 0.02, 0.02, 0.02, 0);
                }
            }
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }
    }

    /** 交战：绕着目标飞，保持距离；看得见目标时一轮一轮地打。 */
    private final class EngageGoal extends Goal {
        private int reposition;

        EngageGoal() {
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity target = Sentinel.this.getTarget();
            return Sentinel.this.isHostile() && target != null && target.isAlive() && Sentinel.this.retreat < 0
                    && Sentinel.this.level().getDifficulty() != Difficulty.PEACEFUL;
        }

        @Override
        public void stop() {
            Sentinel.this.getNavigation().stop();
            Sentinel.this.burstLeft = 0;
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            Sentinel self = Sentinel.this;
            LivingEntity target = self.getTarget();
            if (target == null) {
                return;
            }
            boolean heavy = self.isHeavy();
            self.getLookControl().setLookAt(target, 40, 40);
            double distance = self.distanceTo(target);
            if (--this.reposition <= 0 || self.getNavigation().isDone()) {
                this.reposition = 30 + self.random.nextInt(30);
                double ring = distance > 24 ? 6 : self.standOff() + self.random.nextDouble() * 6;
                double angle = Math.atan2(self.getZ() - target.getZ(), self.getX() - target.getX()) + (self.random.nextDouble() - 0.5) * 1.6;
                int x = (int) Math.floor(target.getX() + Math.cos(angle) * ring);
                int z = (int) Math.floor(target.getZ() + Math.sin(angle) * ring);
                double y = Math.max(target.getY() + 2.5, self.groundY(x, z) + 3) + self.random.nextDouble() * 2;
                self.getNavigation().moveTo(x + 0.5, y, z + 0.5, heavy ? 1.0 : 1.3);
            }
            if (!self.armed()) {
                return;
            }
            if (self.cooldown > 0) {
                self.cooldown--;
                return;
            }
            if (self.burstLeft == 0) {
                if (distance < 30 && self.hasLineOfSight(target)) {
                    self.burstLeft = heavy ? 6 : 3;
                    self.burstDelay = 0;
                } else {
                    return;
                }
            }
            if (--self.burstDelay <= 0) {
                if (self.hasLineOfSight(target)) {
                    self.fireAt(target);
                }
                self.burstLeft--;
                self.burstDelay = heavy ? 3 : 4;
                if (self.burstLeft == 0) {
                    self.cooldown = 50 + self.random.nextInt(30);
                }
            }
        }
    }

    /** 守卫机之间不互相打（{@link PlasmaBolt} 用）。 */
    public static boolean friendly(@Nullable Entity owner, Entity target) {
        return owner instanceof Sentinel && target instanceof Sentinel;
    }

    /** 附近的守卫机。 */
    static List<Sentinel> near(ServerLevel level, Vec3 center, double radius) {
        return level.getEntitiesOfClass(Sentinel.class, new AABB(center, center).inflate(radius));
    }
}
