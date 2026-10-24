package io.github.sweetcornna.universe.darkfog.entity;

import java.util.Optional;

import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.core.darkfog.DarkFogLevels;
import io.github.sweetcornna.universe.core.darkfog.Tactics;
import io.github.sweetcornna.universe.core.darkfog.UnitKind;
import io.github.sweetcornna.universe.darkfog.DarkFogBases;
import io.github.sweetcornna.universe.darkfog.DarkFogEnemy;
import io.github.sweetcornna.universe.darkfog.Facilities;
import io.github.sweetcornna.universe.darkfog.Taunts;
import io.github.sweetcornna.universe.registry.UniverseEntities;
import io.github.sweetcornna.universe.registry.UniverseSounds;
import io.github.sweetcornna.universe.registry.sound.HostileSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.phys.Vec3;

/**
 * 黑雾的地面单位（ADR-0028 §二）：突袭者、游骑兵、守卫者。不存档，由 {@link DarkFogBases} 按营地里的数量放出来，
 * 或者作为一波进攻在目标附近出现。
 *
 * <p>都飞或者贴地悬浮，不走地面寻路：每刻朝要去的地方直飞一小段，高度按脚下的地形抬起来。打谁按 {@link Tactics#decide}：
 * 先打信号塔，再打附近的人，进攻时打设施；守家的不离家太远。
 */
public abstract class DarkFogUnit extends PathfinderMob implements DarkFogEnemy {
    private static final EntityDataAccessor<Integer> DATA_LEVEL = SynchedEntityData.defineId(DarkFogUnit.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_ATTACKING = SynchedEntityData.defineId(DarkFogUnit.class, EntityDataSerializers.BOOLEAN);
    /** 电弧连着的实体（-1 是没有）、方块；客户端按这个画电弧。 */
    private static final EntityDataAccessor<Integer> DATA_ARC_ENTITY = SynchedEntityData.defineId(DarkFogUnit.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Optional<BlockPos>> DATA_ARC_BLOCK = SynchedEntityData.defineId(DarkFogUnit.class,
            EntityDataSerializers.OPTIONAL_BLOCK_POS);
    /** 找人的范围。 */
    private static final double SIGHT = 48;
    /** 一次飞多远的小段。 */
    private static final double STEP = 8;
    /** 被打以后多少刻内还记着仇。 */
    private static final int GRUDGE = 200;

    /** 守家：在基地附近转，有人靠近才打；进攻：往目标的设施冲。 */
    public enum Mode {
        DEFEND,
        ATTACK
    }

    private int baseId = -1;
    private BlockPos home = BlockPos.ZERO;
    private boolean homeSet;
    private Mode mode = Mode.DEFEND;
    private @Nullable BlockPos target;
    private int jamTicks;
    private double jamFactor = 1;

    private Tactics.Order order = Tactics.Order.PATROL;
    private @Nullable LivingEntity focus;
    private @Nullable BlockPos focusBlock;
    private @Nullable Vec3 patrolPoint;
    private int thinkCooldown;
    private int attackCooldown = 20;
    private int arcTicks;
    /** 绕着目标飞的角度。 */
    protected double orbit;
    /** 卡住的判定：从哪儿开始没挪动、多少刻了。 */
    private @Nullable Vec3 stuckFrom;
    private int stuckTicks;
    /** 穿地形还剩多少刻。 */
    private int phaseTicks;
    private static final int STUCK_TICKS = 100;
    private static final int PHASE_TICKS = 60;
    /**
     * 在空中按输入加速的系数。原版是 0.02（{@link LivingEntity#getFlyingSpeed}），突袭者那样一秒才飞一格多，导弹一推就过不来。
     * 换成这个，FLYING_SPEED 每 0.1 大约是每秒 1.4 格。
     */
    private static final float FLIGHT_ACCELERATION = 0.064F;

    protected DarkFogUnit(EntityType<? extends DarkFogUnit> type, Level level) {
        super(type, level);
        this.setPersistenceRequired();
        this.moveControl = new FlyingMoveControl<>(this, 20, true);
        this.setNoGravity(true);
        this.setPathfindingMalus(PathType.WATER, -1.0F);
        this.setPathfindingMalus(PathType.FIRE, -1.0F);
        this.orbit = this.random.nextDouble() * Math.PI * 2;
        this.xpReward = 3;
    }

    public abstract UnitKind kind();

    /** 放一个单位进世界，血量和伤害按等级加。 */
    public static DarkFogUnit spawn(ServerLevel level, UnitKind kind, int baseId, int darkFogLevel, Vec3 pos, BlockPos home, Mode mode,
            @Nullable BlockPos target) {
        EntityType<? extends DarkFogUnit> type = switch (kind) {
            case RAIDER -> UniverseEntities.DARK_FOG_RAIDER.get();
            case RANGER -> UniverseEntities.DARK_FOG_RANGER.get();
            case GUARDIAN -> UniverseEntities.DARK_FOG_GUARDIAN.get();
        };
        DarkFogUnit unit = type.create(level, EntitySpawnReason.EVENT);
        if (unit == null) {
            throw new IllegalStateException("can't create " + kind);
        }
        double scale = DarkFogLevels.scale(darkFogLevel);
        unit.entityData.set(DATA_LEVEL, DarkFogLevels.clamp(darkFogLevel));
        unit.baseId = baseId;
        unit.home = home.immutable();
        unit.homeSet = true;
        unit.mode = mode;
        unit.target = target == null ? null : target.immutable();
        unit.getAttribute(Attributes.MAX_HEALTH).setBaseValue(kind.baseHealth() * scale);
        unit.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(kind.baseDamage() * scale);
        unit.setHealth(unit.getMaxHealth());
        unit.snapTo(pos.x, pos.y, pos.z, level.getRandom().nextFloat() * 360, 0);
        level.addFreshEntity(unit);
        return unit;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_LEVEL, 0);
        builder.define(DATA_ATTACKING, false);
        builder.define(DATA_ARC_ENTITY, -1);
        builder.define(DATA_ARC_BLOCK, Optional.empty());
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        FlyingPathNavigation navigation = new FlyingPathNavigation(this, level);
        navigation.setCanOpenDoors(false);
        navigation.setCanFloat(true);
        return navigation;
    }

    @Override
    public int darkFogLevel() {
        return this.entityData.get(DATA_LEVEL);
    }

    @Override
    public int baseId() {
        return this.baseId;
    }

    /** 基地的位置（钻芯）。 */
    public BlockPos home() {
        return this.home;
    }

    public Mode mode() {
        return this.mode;
    }

    /** 进攻的目标（那片设施的中心）；守家时是空的。 */
    public @Nullable BlockPos attackTarget() {
        return this.target;
    }

    public void setMode(Mode mode, @Nullable BlockPos target) {
        this.mode = mode;
        this.target = target == null ? null : target.immutable();
        this.thinkCooldown = 0;
    }

    @Override
    public boolean attackingFacility() {
        return this.entityData.get(DATA_ATTACKING);
    }

    /** AI 开始、停止打设施时设。 */
    public void setAttackingFacility(boolean attacking) {
        if (this.entityData.get(DATA_ATTACKING) != attacking) {
            this.entityData.set(DATA_ATTACKING, attacking);
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

    /** 现在的打法（GameTest 和调试用）。 */
    public Tactics.Order order() {
        return this.order;
    }

    /** 电弧连着的实体（客户端画电弧用），没有是 -1。 */
    public int arcEntity() {
        return this.entityData.get(DATA_ARC_ENTITY);
    }

    public Optional<BlockPos> arcBlock() {
        return this.entityData.get(DATA_ARC_BLOCK);
    }

    /** 亮一下电弧，连到实体或者方块上。 */
    protected void showArc(@Nullable Entity entity, @Nullable BlockPos block, int ticks) {
        this.entityData.set(DATA_ARC_ENTITY, entity == null ? -1 : entity.getId());
        this.entityData.set(DATA_ARC_BLOCK, Optional.ofNullable(block));
        this.arcTicks = ticks;
    }

    @Override
    protected float getFlyingSpeed() {
        return FLIGHT_ACCELERATION;
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (this.jamTicks > 0) {
            this.jamTicks--;
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide()) {
            this.clientTick();
        }
    }

    /** 客户端：推进器下面撒一点暗红的光。 */
    protected void clientTick() {
        if (this.random.nextInt(3) == 0) {
            this.level().addParticle(new DustParticleOptions(0xC02040, 0.5F), this.getX() + (this.random.nextDouble() - 0.5) * this.getBbWidth() * 0.6,
                    this.getY() + 0.05, this.getZ() + (this.random.nextDouble() - 0.5) * this.getBbWidth() * 0.6, 0, -0.05, 0);
        }
    }

    // ---- 行为

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        if (!this.homeSet) {
            // 用命令放出来的单位把出生的地方当家
            this.home = this.blockPosition();
            this.homeSet = true;
        }
        if (this.arcTicks > 0 && --this.arcTicks == 0) {
            this.entityData.set(DATA_ARC_ENTITY, -1);
            this.entityData.set(DATA_ARC_BLOCK, Optional.empty());
        }
        if (this.attackCooldown > 0) {
            this.attackCooldown--;
        }
        if (--this.thinkCooldown <= 0) {
            this.think(level);
            this.thinkCooldown = 10 + this.random.nextInt(5);
        }
        this.act(level);
        if (this.horizontalCollision) {
            this.setDeltaMovement(this.getDeltaMovement().add(0, 0.12, 0));
        }
        this.unstick();
    }

    /**
     * 赶路时卡在悬崖下、洞里出不来（{@link #STUCK_TICKS} 刻没挪出 1 格），就穿过地形飞一会儿。
     * 单位不走寻路，直飞总会碰到这种地方。
     */
    private void unstick() {
        if (this.phaseTicks > 0) {
            if (--this.phaseTicks == 0) {
                this.noPhysics = false;
            }
            return;
        }
        boolean travelling = this.order == Tactics.Order.ADVANCE || this.order == Tactics.Order.RETURN || this.order == Tactics.Order.FACILITY
                || this.order == Tactics.Order.TAUNT;
        if (!travelling || this.stuckFrom == null || this.position().distanceToSqr(this.stuckFrom) > 1) {
            this.stuckFrom = this.position();
            this.stuckTicks = 0;
            return;
        }
        if (++this.stuckTicks >= STUCK_TICKS) {
            this.noPhysics = true;
            this.phaseTicks = PHASE_TICKS;
            this.stuckTicks = 0;
            this.stuckFrom = null;
        }
    }

    private void think(ServerLevel level) {
        Vec3 pos = this.position();
        double homeDistance = horizontal(pos, center(this.home));
        Optional<BlockPos> taunt = Taunts.nearestTaunt(level, pos, Tactics.TAUNT_RANGE);
        Player player = level.getNearestPlayer(pos.x, pos.y, pos.z, SIGHT, DarkFogDamage::targetable);
        double playerDistance = player == null ? Double.POSITIVE_INFINITY : this.distanceTo(player);
        double playerHome = player == null ? Double.POSITIVE_INFINITY : horizontal(player.position(), center(this.home));
        LivingEntity attacker = this.getLastHurtByMob();
        boolean grudge = attacker != null && DarkFogDamage.targetable(attacker) && attacker.level() == level
                && this.tickCount - this.getLastHurtByMobTimestamp() < GRUDGE;
        double revenge = grudge ? this.distanceTo(attacker) : Double.POSITIVE_INFINITY;
        double targetDistance = this.target == null ? Double.POSITIVE_INFINITY : horizontal(pos, center(this.target));
        Optional<BlockPos> facility = Optional.empty();
        if (this.mode == Mode.ATTACK) {
            double radius = targetDistance <= Tactics.ARRIVE ? Tactics.ARRIVE + 8 : Tactics.PASSING_FACILITY;
            facility = Facilities.nearest(level, pos, radius);
        }
        double facilityDistance = facility.map(f -> pos.distanceTo(center(f))).orElse(Double.POSITIVE_INFINITY);
        Tactics.Situation situation = new Tactics.Situation(this.mode == Mode.ATTACK, homeDistance,
                taunt.map(t -> pos.distanceTo(center(t))).orElse(Double.POSITIVE_INFINITY), playerDistance, playerHome, revenge,
                facilityDistance, targetDistance);
        Tactics.Order next = Tactics.decide(situation);
        this.focus = null;
        this.focusBlock = null;
        switch (next) {
            case TAUNT -> this.focusBlock = taunt.orElse(null);
            case PLAYER -> this.focus = revenge <= Tactics.REVENGE_RANGE ? attacker : player;
            case FACILITY -> this.focusBlock = facility.orElse(null);
            case PATROL -> {
                if (this.order != Tactics.Order.PATROL || this.patrolPoint == null || this.position().distanceTo(this.patrolPoint) < 2
                        || this.random.nextInt(8) == 0) {
                    this.patrolPoint = this.pickPatrolPoint(level);
                }
            }
            default -> {
            }
        }
        this.order = next;
        this.setAttackingFacility(this.focusBlock != null);
        if (this.focus != null) {
            this.setTarget(this.focus);
        } else {
            this.setTarget(null);
        }
    }

    private void act(ServerLevel level) {
        double jam = this.jamFactor();
        switch (this.order) {
            case PLAYER -> {
                LivingEntity enemy = this.focus;
                if (enemy == null || !enemy.isAlive() || enemy.level() != level || !DarkFogDamage.targetable(enemy)) {
                    this.thinkCooldown = 0;
                    return;
                }
                Vec3 aim = enemy.position().add(0, enemy.getBbHeight() * 0.5, 0);
                this.getLookControl().setLookAt(enemy, 40, 40);
                this.steer(level, this.engagePosition(level, aim), 1.2 * jam);
                if (this.attackCooldown <= 0 && this.getEyePosition().distanceTo(aim) <= Tactics.reach(this.kind()) && this.hasLineOfSight(enemy)) {
                    this.strike(level, enemy, (float) this.getAttributeValue(Attributes.ATTACK_DAMAGE));
                    this.attackCooldown = Tactics.cooldown(Tactics.attackInterval(this.kind()), jam);
                }
            }
            case TAUNT, FACILITY -> {
                BlockPos block = this.focusBlock;
                // 信号塔不一定在设施标签里，只看还在不在
                if (block == null || (this.order == Tactics.Order.TAUNT ? level.getBlockState(block).isAir()
                        : !Facilities.isFacility(level.getBlockState(block)))) {
                    this.thinkCooldown = 0;
                    return;
                }
                Vec3 aim = center(block);
                this.getLookControl().setLookAt(aim.x, aim.y, aim.z, 40, 40);
                this.steer(level, this.engagePosition(level, aim), 1.2 * jam);
                if (this.attackCooldown <= 0 && this.getEyePosition().distanceTo(aim) <= Tactics.reach(this.kind()) + 0.8) {
                    this.strikeBlock(level, block, (float) this.getAttributeValue(Attributes.ATTACK_DAMAGE));
                    this.attackCooldown = Tactics.cooldown(Tactics.attackInterval(this.kind()), jam);
                }
            }
            case ADVANCE -> {
                if (this.target == null) {
                    this.thinkCooldown = 0;
                    return;
                }
                this.steer(level, this.cruise(level, center(this.target)), jam);
            }
            case RETURN -> this.steer(level, this.cruise(level, center(this.home)), (this.mode == Mode.ATTACK ? 0.7 : 1.0) * jam);
            case PATROL -> {
                if (this.patrolPoint != null) {
                    this.steer(level, this.patrolPoint, 0.5 * jam);
                }
            }
        }
    }

    /** 巡逻点：家附近（守卫者在家上空绕大圈）。 */
    protected Vec3 pickPatrolPoint(ServerLevel level) {
        Vec3 home = center(this.home);
        double angle = this.random.nextDouble() * Math.PI * 2;
        double distance = 4 + this.random.nextDouble() * 12;
        double x = home.x + Math.cos(angle) * distance;
        double z = home.z + Math.sin(angle) * distance;
        return new Vec3(x, this.groundY(level, x, z) + Tactics.hover(this.kind()) + this.random.nextDouble(), z);
    }

    /** 赶路时的高度：按目标那里的地面（目标在高台上就是台面）加上悬浮高度。 */
    private Vec3 cruise(ServerLevel level, Vec3 goal) {
        return new Vec3(goal.x, this.groundY(level, goal.x, goal.z, Math.max(this.getY(), goal.y)) + Tactics.hover(this.kind()), goal.z);
    }

    /** 交战时要飞到哪：子类按打法定（突袭者贴上去，游骑兵绕着飞）。 */
    protected abstract Vec3 engagePosition(ServerLevel level, Vec3 aim);

    /** 打人。 */
    protected abstract void strike(ServerLevel level, LivingEntity enemy, float damage);

    /** 打设施（信号塔也是设施）。 */
    protected abstract void strikeBlock(ServerLevel level, BlockPos pos, float damage);

    /**
     * 朝 {@code goal} 直飞一小段：远了每次只往前 {@link #STEP} 格，高度不低于脚下和前面几格的地面加上离地间隙。
     */
    protected void steer(ServerLevel level, Vec3 goal, double speed) {
        Vec3 pos = this.position();
        double dx = goal.x - pos.x;
        double dz = goal.z - pos.z;
        double distance = Math.sqrt(dx * dx + dz * dz);
        double nx = goal.x;
        double nz = goal.z;
        if (distance > STEP) {
            nx = pos.x + dx / distance * STEP;
            nz = pos.z + dz / distance * STEP;
        }
        double ahead = distance > 1e-3 ? Math.min(3, distance) : 0;
        double reference = Math.max(pos.y, goal.y);
        double ground = Math.max(this.groundY(level, nx, nz, reference),
                this.groundY(level, pos.x + (distance > 1e-3 ? dx / distance * ahead : 0), pos.z + (distance > 1e-3 ? dz / distance * ahead : 0),
                        reference));
        double clearance = this.kind() == UnitKind.RAIDER ? 0.8 : 2;
        double y = Math.max(goal.y, ground + clearance);
        this.getMoveControl().setWantedPosition(nx, y, nz, Math.max(0.05, speed));
    }

    protected double groundY(ServerLevel level, double x, double z) {
        return this.groundY(level, x, z, this.getY());
    }

    /**
     * 这一列在 {@code reference} 这个高度下面的地面。上面空着就按高度图；上面有东西（屋顶、悬崖、洞顶）就从这个高度往下找
     * 第一块实心的方块，不然进了有顶的厂房会贴着屋顶飞、够不着里面的设备。{@code reference} 取自己和要去的地方里高的那个：
     * 目标在高台上时，从台面往下找，找到的就是台面，单位会往上飞。
     */
    protected double groundY(ServerLevel level, double x, double z, double reference) {
        int bx = (int) Math.floor(x);
        int bz = (int) Math.floor(z);
        // 没加载的区块不去读（会就地加载、甚至生成区块，卡服务端）：按自己现在的高度算
        if (!level.hasChunk(net.minecraft.core.SectionPos.blockToSectionCoord(bx), net.minecraft.core.SectionPos.blockToSectionCoord(bz))) {
            return this.getY() - Tactics.hover(this.kind());
        }
        // 26.x 的 MOTION_BLOCKING 只认带 blocks_motion 标签的方块，玩家的地基、设备都不算；飞着的单位按"不是空气"算
        int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, bx, bz);
        int from = (int) Math.floor(reference) + 2;
        if (top <= from) {
            return top;
        }
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(bx, from, bz);
        for (int i = 0; i < 24 && pos.getY() > level.getMinY(); i++) {
            pos.move(0, -1, 0);
            if (!level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()) {
                return pos.getY() + 1;
            }
        }
        return top;
    }

    protected static Vec3 center(BlockPos pos) {
        return Vec3.atCenterOf(pos);
    }

    private static double horizontal(Vec3 a, Vec3 b) {
        return Math.hypot(a.x - b.x, a.z - b.z);
    }

    // ---- 受击、死亡

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        float before = this.getHealth();
        boolean hurt = super.hurtServer(level, source, damage);
        if (hurt && !this.isRemoved()) {
            DarkFogBases.onUnitHurt(level, this, before - this.getHealth(), source);
        }
        return hurt;
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (this.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.EXPLOSION, this.getX(), this.getY(0.5), this.getZ(), 1, 0.2, 0.2, 0.2, 0);
            level.sendParticles(ParticleTypes.LARGE_SMOKE, this.getX(), this.getY(0.5), this.getZ(), 14, 0.3, 0.3, 0.3, 0.02);
            level.sendParticles(new DustParticleOptions(0xFF2A3A, 1.0F), this.getX(), this.getY(0.5), this.getZ(), 12, 0.4, 0.4, 0.4, 0);
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, this.getX(), this.getY(0.5), this.getZ(), 16, 0.3, 0.3, 0.3, 0.3);
            level.playSound(null, this.getX(), this.getY(), this.getZ(), UniverseSounds.EXPLOSION_SMALL.get(), SoundSource.HOSTILE, 0.5F, 1.25F);
            DarkFogBases.onUnitKilled(level, this, source);
        }
    }

    @Override
    protected @Nullable SoundEvent getAmbientSound() {
        return HostileSounds.UNIT_AMBIENT.get();
    }

    @Override
    public int getAmbientSoundInterval() {
        return 160;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return HostileSounds.UNIT_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return HostileSounds.UNIT_DEATH.get();
    }

    @Override
    protected float getSoundVolume() {
        return 0.6F;
    }

    @Override
    public float getVoicePitch() {
        return 1.0F + (this.random.nextFloat() - 0.5F) * 0.1F;
    }

    @Override
    protected void checkFallDamage(double ya, boolean onGround, BlockState onState, BlockPos pos) {
    }

    @Override
    public boolean causeFallDamage(double fallDistance, float damageModifier, DamageSource source) {
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
}
