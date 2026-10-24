package io.github.sweetcornna.universe.fossil;

import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.core.darkfog.Tactics;
import io.github.sweetcornna.universe.core.fossil.StoneSentryRules;
import io.github.sweetcornna.universe.registry.UniverseBlocks;
import io.github.sweetcornna.universe.registry.sound.FossilSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * 石哨兵（ADR-0034 §三）：发掘场边上睡着的石头醒过来，浮在离地两三格的地方，身边绕着几块碎石。
 *
 * <ul>
 *   <li>醒来的头一秒半慢慢浮起来，不动手。</li>
 *   <li>隔着七到十格绕着人飘，看得见就抬手吊射一块石头（{@link SentryRock}）。</li>
 *   <li>血量掉到三成以下，石缝烧得发白，直冲过去，{@value StoneSentryRules#FUSE_TICKS} 刻后或者贴到身边就炸，炸了什么也不留。</li>
 *   <li>打下来掉一两块骨头，偏向稀有的类群（战利品表）。</li>
 *   <li>附近没人了就飘回原来那块地方，落下去重新变成一块睡着的石头。</li>
 * </ul>
 */
public class StoneSentry extends PathfinderMob implements Enemy {
    private static final EntityDataAccessor<Integer> DATA_RISE = SynchedEntityData.defineId(StoneSentry.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_WINDUP = SynchedEntityData.defineId(StoneSentry.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_FUSE = SynchedEntityData.defineId(StoneSentry.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_ANGRY = SynchedEntityData.defineId(StoneSentry.class, EntityDataSerializers.BOOLEAN);
    /** 醒来时浮起来要多久。 */
    public static final int RISE_TICKS = 30;
    /** 抬手到扔出去。 */
    public static final int WINDUP_TICKS = 12;
    /** 没有目标多久以后回去睡。 */
    private static final int SETTLE_AFTER = 200;
    /** 找新目标的范围。 */
    private static final double AGGRO_RANGE = 16;
    private static final double LEASH = 40;

    private @Nullable BlockPos home;
    private int calm;
    private int cooldown = StoneSentryRules.THROW_INTERVAL;
    private int reposition;

    public StoneSentry(EntityType<? extends StoneSentry> type, Level level) {
        super(type, level);
        this.moveControl = new FlyingMoveControl<>(this, 10, true);
        this.setNoGravity(true);
        this.setPathfindingMalus(PathType.WATER, -1.0F);
        this.setPathfindingMalus(PathType.LAVA, -1.0F);
        this.xpReward = 10;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, StoneSentryRules.MAX_HEALTH)
                .add(Attributes.ARMOR, StoneSentryRules.ARMOR)
                .add(Attributes.FLYING_SPEED, 0.45)
                .add(Attributes.MOVEMENT_SPEED, 0.25)
                .add(Attributes.FOLLOW_RANGE, 32)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.8);
    }

    /** 刚醒过来：记下睡的地方，冲着 {@code player} 去。 */
    public void awaken(BlockPos home, @Nullable Player player) {
        this.home = home.immutable();
        this.entityData.set(DATA_RISE, RISE_TICKS);
        this.setPersistenceRequired();
        if (player != null && valid(player)) {
            this.setTarget(player);
        }
    }

    public @Nullable BlockPos home() {
        return this.home;
    }

    /** 还在浮起来（0 到 1，1 是刚醒）。 */
    public float rising() {
        return this.entityData.get(DATA_RISE) / (float) RISE_TICKS;
    }

    /** 抬手的进度（0 到 1，1 是正要扔）。 */
    public float windup() {
        int left = this.entityData.get(DATA_WINDUP);
        return left <= 0 ? 0 : 1 - (left - 1) / (float) WINDUP_TICKS;
    }

    /** 点着了没有。 */
    public boolean fused() {
        return this.entityData.get(DATA_FUSE) >= 0;
    }

    /** 盯上人了（客户端按这个把石缝烧红）。 */
    public boolean angry() {
        return this.entityData.get(DATA_ANGRY);
    }

    /** 点着以后过了多少刻。 */
    public int fuseTicks() {
        return Math.max(0, this.entityData.get(DATA_FUSE));
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder entityData) {
        super.defineSynchedData(entityData);
        entityData.define(DATA_RISE, 0);
        entityData.define(DATA_WINDUP, 0);
        entityData.define(DATA_FUSE, -1);
        entityData.define(DATA_ANGRY, false);
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        FlyingPathNavigation navigation = new FlyingPathNavigation(this, level);
        navigation.setCanOpenDoors(false);
        navigation.setCanFloat(true);
        return navigation;
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide()) {
            if (this.fused() && this.random.nextInt(2) == 0) {
                this.level().addParticle(ParticleTypes.SMOKE, this.getRandomX(0.5), this.getRandomY(), this.getRandomZ(0.5), 0, 0.03, 0);
                this.level().addParticle(ParticleTypes.FLAME, this.getRandomX(0.4), this.getY(0.5), this.getRandomZ(0.4), 0, 0.02, 0);
            } else if (this.random.nextInt(8) == 0) {
                // 身上往下掉的石屑
                this.level().addParticle(new BlockParticleOption(ParticleTypes.FALLING_DUST, UniverseBlocks.PLANET_COBBLESTONE.get().defaultBlockState()),
                        this.getRandomX(0.5), this.getY(0.2), this.getRandomZ(0.5), 0, 0, 0);
            }
        }
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        int rise = this.entityData.get(DATA_RISE);
        if (rise > 0) {
            // 浮起来：头一秒半只往上飘
            this.entityData.set(DATA_RISE, rise - 1);
            this.setDeltaMovement(this.getDeltaMovement().scale(0.6).add(0, 0.05, 0));
            return;
        }
        if (this.fused()) {
            this.burn(level);
            return;
        }
        LivingEntity target = this.getTarget();
        if (target != null && !(valid(target) && target.level() == level && this.distanceToSqr(target) < LEASH * LEASH)) {
            this.setTarget(null);
            target = null;
        }
        if (target == null && level.getDifficulty() != Difficulty.PEACEFUL && this.tickCount % 10 == 0) {
            Player nearest = level.getNearestPlayer(this.getX(), this.getY(), this.getZ(), AGGRO_RANGE,
                    p -> p instanceof Player player && valid(player) && this.hasLineOfSight(player));
            if (nearest != null) {
                this.setTarget(nearest);
                target = nearest;
            }
        }
        this.entityData.set(DATA_ANGRY, target != null);
        if (target == null) {
            this.idle(level);
            return;
        }
        this.calm = 0;
        if (StoneSentryRules.fuse(this.getHealth(), this.getMaxHealth())) {
            this.entityData.set(DATA_FUSE, 0);
            this.entityData.set(DATA_WINDUP, 0);
            this.playSound(FossilSounds.SENTRY_FUSE.get(), 1.2F, 1.0F);
            return;
        }
        this.engage(level, target);
    }

    /** 交战：绕着人飘，隔一阵扔一块石头。 */
    private void engage(ServerLevel level, LivingEntity target) {
        this.getLookControl().setLookAt(target, 30, 30);
        double distance = this.distanceTo(target);
        if (--this.reposition <= 0 || this.getNavigation().isDone()) {
            this.reposition = 30 + this.random.nextInt(30);
            double ring = 7 + this.random.nextDouble() * 3;
            double angle = Math.atan2(this.getZ() - target.getZ(), this.getX() - target.getX()) + (this.random.nextDouble() - 0.5) * 1.4;
            int x = (int) Math.floor(target.getX() + Math.cos(angle) * ring);
            int z = (int) Math.floor(target.getZ() + Math.sin(angle) * ring);
            double y = Math.max(target.getY() + 1.5, this.groundY(x, z) + 2) + this.random.nextDouble() * 1.5;
            this.getNavigation().moveTo(x + 0.5, y, z + 0.5, 1.0);
        }
        int windup = this.entityData.get(DATA_WINDUP);
        if (windup > 0) {
            this.entityData.set(DATA_WINDUP, windup - 1);
            if (windup == 1) {
                this.hurl(level, target);
            }
            return;
        }
        if (--this.cooldown <= 0 && distance <= StoneSentryRules.THROW_RANGE && this.hasLineOfSight(target)) {
            this.entityData.set(DATA_WINDUP, WINDUP_TICKS);
            this.cooldown = StoneSentryRules.THROW_INTERVAL + this.random.nextInt(20);
        }
    }

    /** 扔一块石头：按人走的速度稍微提前量，吊着扔过去。 */
    private void hurl(ServerLevel level, LivingEntity target) {
        Vec3 from = this.position().add(0, this.getBbHeight() + 0.3, 0);
        Vec3 aim = target.position().add(0, target.getBbHeight() * 0.5, 0);
        int ticks = StoneSentryRules.flightTicks(from.distanceTo(aim));
        aim = aim.add(target.getDeltaMovement().multiply(1, 0, 1).scale(ticks * 0.7));
        double[] v = Tactics.ballistic(aim.x - from.x, aim.y - from.y, aim.z - from.z, SentryRock.GRAVITY, ticks);
        SentryRock rock = SentryRock.launch(level, this, from, new Vec3(v[0], v[1], v[2]));
        if (rock != null) {
            this.playSound(FossilSounds.SENTRY_THROW.get(), 1.0F, 0.9F + this.random.nextFloat() * 0.2F);
        }
    }

    /** 点着了：直冲过去，到时候或者贴到身边就炸。 */
    private void burn(ServerLevel level) {
        int fuse = this.entityData.get(DATA_FUSE) + 1;
        this.entityData.set(DATA_FUSE, fuse);
        LivingEntity target = this.getTarget();
        if (target != null && target.isAlive()) {
            this.getMoveControl().setWantedPosition(target.getX(), target.getY(0.5), target.getZ(), 2.2);
            this.getLookControl().setLookAt(target, 60, 60);
        }
        boolean reached = target != null && this.distanceTo(target) <= StoneSentryRules.FUSE_REACH;
        if (fuse >= StoneSentryRules.FUSE_TICKS || reached) {
            this.explode(level);
        }
    }

    /** 自爆：不炸方块，只伤人，什么也不留。 */
    private void explode(ServerLevel level) {
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, UniverseBlocks.PLANET_COBBLESTONE.get().defaultBlockState()),
                this.getX(), this.getY(0.5), this.getZ(), 40, 0.5, 0.5, 0.5, 0.2);
        level.explode(this, this.getX(), this.getY(0.5), this.getZ(), StoneSentryRules.BLAST_RADIUS, Level.ExplosionInteraction.NONE);
        this.discard();
    }

    /** 没有目标：飘回睡的地方，待够了就落下去变回石头。 */
    private void idle(ServerLevel level) {
        this.calm++;
        BlockPos rest = this.home != null ? this.home : this.blockPosition();
        double hx = rest.getX() + 0.5;
        double hz = rest.getZ() + 0.5;
        double horizontal = Math.hypot(hx - this.getX(), hz - this.getZ());
        if (horizontal > 1.0) {
            if (this.getNavigation().isDone() || this.tickCount % 40 == 0) {
                this.getNavigation().moveTo(hx, rest.getY() + 1.5, hz, 0.8);
            }
            return;
        }
        if (this.calm < SETTLE_AFTER) {
            this.getMoveControl().setWantedPosition(hx, rest.getY() + 0.6, hz, 0.4);
            return;
        }
        BlockPos at = this.home != null ? this.home : BlockPos.containing(hx, this.groundY(rest.getX(), rest.getZ()), hz);
        BlockState there = level.getBlockState(at);
        if ((there.isAir() || there.canBeReplaced()) && !level.getBlockState(at.below()).getCollisionShape(level, at.below()).isEmpty()) {
            level.setBlock(at, UniverseBlocks.DORMANT_STONE_SENTRY.get().defaultBlockState(), Block.UPDATE_ALL);
            level.playSound(null, at, FossilSounds.UNREST.get(), SoundSource.HOSTILE, 0.8F, 0.7F);
            this.discard();
        } else {
            // 原来的地方被占了：就地找块地方落
            this.home = BlockPos.containing(this.getX(), this.groundY(this.getBlockX(), this.getBlockZ()), this.getZ());
            this.calm = SETTLE_AFTER / 2;
        }
    }

    private int groundY(int x, int z) {
        return this.level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
    }

    private static boolean valid(LivingEntity entity) {
        return entity.isAlive() && !(entity instanceof Player player && (player.isCreative() || player.isSpectator()));
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        boolean hurt = super.hurtServer(level, source, damage);
        if (hurt && source.getEntity() instanceof Player player && valid(player) && !this.fused()) {
            this.setTarget(player);
        }
        return hurt;
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (this.level() instanceof ServerLevel level) {
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, UniverseBlocks.PLANET_COBBLESTONE.get().defaultBlockState()),
                    this.getX(), this.getY(0.5), this.getZ(), 40, 0.45, 0.45, 0.45, 0.15);
        }
    }

    // ---- 存档

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.storeNullable("Home", BlockPos.CODEC, this.home);
        output.putInt("Calm", this.calm);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.home = input.read("Home", BlockPos.CODEC).orElse(null);
        this.calm = input.getIntOr("Calm", 0);
    }

    // ---- 声音、杂项

    @Override
    protected @Nullable SoundEvent getAmbientSound() {
        return FossilSounds.SENTRY_AMBIENT.get();
    }

    @Override
    public int getAmbientSoundInterval() {
        return 140;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return FossilSounds.SENTRY_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return FossilSounds.SENTRY_DEATH.get();
    }

    @Override
    public float getVoicePitch() {
        return 0.9F + (this.random.nextFloat() - 0.5F) * 0.15F;
    }

    @Override
    protected void checkFallDamage(double ya, boolean onGround, BlockState onState, BlockPos pos) {
    }

    @Override
    public boolean causeFallDamage(double fallDistance, float damageModifier, DamageSource source) {
        return false;
    }

    @Override
    public boolean canBreatheUnderwater() {
        return true;
    }

    @Override
    public boolean removeWhenFarAway(double distSqr) {
        return false;
    }
}
