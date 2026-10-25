package io.github.sweetcornna.universe.fishing;

import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.core.fishing.Bait;
import io.github.sweetcornna.universe.core.fishing.BiteTimer;
import io.github.sweetcornna.universe.core.fishing.Catch;
import io.github.sweetcornna.universe.core.fishing.CatchRoll;
import io.github.sweetcornna.universe.core.fishing.FishSpecies;
import io.github.sweetcornna.universe.core.fishing.Reeling;
import io.github.sweetcornna.universe.core.procgen.Rng;
import io.github.sweetcornna.universe.core.survival.ToolWeapon;
import io.github.sweetcornna.universe.registry.UniverseEntities;
import io.github.sweetcornna.universe.registry.sound.FishingSounds;
import io.github.sweetcornna.universe.survival.MultitoolItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.InterpolationHandler;
import net.minecraft.world.entity.LinearInterpolationHandler;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * 钓鱼器的浮标（ADR-0034 §一）。飞出去落在水或酸液上才开始等鱼；等到鱼影游过来、咬钩，右键钩住后进入收线。
 *
 * <p>判定都在服务端：咬钩的是什么在鱼影出现时就定下（{@link CatchRoll}），收线按 {@link Reeling} 每刻推进，
 * 按着还是松开看主人是不是正按着使用键（多功能工具的 {@code startUsingItem}）。张力和进度每 5 刻同步一次，
 * 客户端在中间插值（{@link #displayTension}）。不存档：退出时浮标就没了。
 */
public class FishingBobber extends Projectile {
    public enum Phase {
        FLYING,
        /** 落在水上，等鱼。 */
        FLOATING,
        /** 鱼影正朝浮标游过来。 */
        APPROACH,
        /** 咬钩了，{@value BiteTimer#BITE_WINDOW} 刻内右键钩住。 */
        BITE,
        /** 钩住了，正在收线。 */
        HOOKED,
        /** 没落在水里，搁在地上。 */
        GROUNDED
    }

    /** 收线时每几刻同步一次张力和进度。 */
    public static final int SYNC_INTERVAL = 5;
    /** 鱼影的编号：不是鱼（杂物、漂流瓶……）时画一团模糊的影子。 */
    public static final int SHADOW_JUNK = -2;
    private static final int NO_SHADOW = -1;
    /** 搁在地上多久自己收回。 */
    private static final int GROUNDED_LIFE = 1200;
    private static final double GRAVITY = 0.05;
    private static final double LAUNCH_SPEED = 1.15;

    private static final EntityDataAccessor<Byte> PHASE = SynchedEntityData.defineId(FishingBobber.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Integer> SHADOW = SynchedEntityData.defineId(FishingBobber.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> APPROACH_TICKS = SynchedEntityData.defineId(FishingBobber.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> SHADOW_ANGLE = SynchedEntityData.defineId(FishingBobber.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> TENSION = SynchedEntityData.defineId(FishingBobber.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> PROGRESS = SynchedEntityData.defineId(FishingBobber.class, EntityDataSerializers.FLOAT);
    /** 第 0 位正在猛冲，第 1 位马上要冲，第 2 位线松了快脱钩。 */
    private static final EntityDataAccessor<Byte> CUES = SynchedEntityData.defineId(FishingBobber.class, EntityDataSerializers.BYTE);

    // 服务端
    private int timer;
    /** 等鱼的时间里最后几刻是鱼影游过来。 */
    private int approachPlanned;
    /** 甩竿时主手的那一把工具：主手换成别的（哪怕是另一把钓鱼模式的工具）浮标就收回，饵也只从这一把扣。 */
    private @Nullable ItemStack tool;
    private Bait bait = Bait.NONE;
    private boolean blessed;
    private @Nullable Catch pending;
    private @Nullable Reeling reeling;
    private Rng rng = Rng.of(0);
    private boolean warnedGround;

    // 两端
    private int phaseAge;
    private float tensionFrom;
    private float progressFrom;
    private int syncAge;

    public FishingBobber(EntityType<? extends FishingBobber> type, Level level) {
        super(type, level);
    }

    /** 服务端甩竿：从眼睛前面朝视线方向抛出去，带一点往上的弧线。 */
    FishingBobber(ServerLevel level, ServerPlayer owner, ItemStack tool, Bait bait, boolean blessed) {
        this(UniverseEntities.FISHING_BOBBER.get(), level);
        this.setOwner(owner);
        this.tool = tool;
        this.bait = bait;
        this.blessed = blessed;
        this.rng = Rng.of(level.getRandom().nextLong());
        Vec3 look = owner.getLookAngle();
        Vec3 right = look.cross(new Vec3(0, 1, 0)).normalize();
        Vec3 from = owner.getEyePosition().add(look.scale(0.4)).add(right.scale(0.3)).add(0, -0.15, 0);
        this.snapTo(from.x, from.y, from.z, owner.getYRot(), owner.getXRot());
        this.setDeltaMovement(look.scale(LAUNCH_SPEED).add(0, 0.22, 0));
        this.yRotO = this.getYRot();
        this.xRotO = this.getXRot();
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(PHASE, (byte) Phase.FLYING.ordinal());
        builder.define(SHADOW, NO_SHADOW);
        builder.define(APPROACH_TICKS, 0);
        builder.define(SHADOW_ANGLE, 0F);
        builder.define(TENSION, 0F);
        builder.define(PROGRESS, 0F);
        builder.define(CUES, (byte) 0);
    }

    @Override
    protected InterpolationHandler createInterpolationHandler() {
        return LinearInterpolationHandler.create(this);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) {
        super.onSyncedDataUpdated(accessor);
        if (PHASE.equals(accessor)) {
            this.phaseAge = 0;
            if (this.level().isClientSide() && this.phase() == Phase.BITE) {
                // 咬钩：浮标往下一沉
                this.setDeltaMovement(this.getDeltaMovement().x, -0.35, this.getDeltaMovement().z);
            }
        } else if (TENSION.equals(accessor) || PROGRESS.equals(accessor)) {
            this.syncAge = 0;
        }
    }

    public Phase phase() {
        return Phase.values()[this.entityData.get(PHASE)];
    }

    private void setPhase(Phase phase) {
        this.entityData.set(PHASE, (byte) phase.ordinal());
        this.phaseAge = 0;
    }

    /** 这一阶段过了几刻（两端都在数）。 */
    public int phaseAge() {
        return this.phaseAge;
    }

    /** 鱼影画哪种鱼；没有鱼影是空，不是鱼时是 {@link #SHADOW_JUNK}。 */
    public int shadow() {
        return this.entityData.get(SHADOW);
    }

    /** 鱼影一共游几刻。 */
    public int approachTicks() {
        return this.entityData.get(APPROACH_TICKS);
    }

    /** 鱼影从哪个方向游过来（弧度）。 */
    public float shadowAngle() {
        return this.entityData.get(SHADOW_ANGLE);
    }

    public boolean dashing() {
        return (this.entityData.get(CUES) & 1) != 0;
    }

    public boolean warning() {
        return (this.entityData.get(CUES) & 2) != 0;
    }

    public boolean slack() {
        return (this.entityData.get(CUES) & 4) != 0;
    }

    /** 客户端画的张力：在上一次和这一次同步的值之间插。 */
    public float displayTension(float partialTick) {
        return Mth.lerp(this.lerp(partialTick), this.tensionFrom, this.entityData.get(TENSION));
    }

    public float displayProgress(float partialTick) {
        return Mth.lerp(this.lerp(partialTick), this.progressFrom, this.entityData.get(PROGRESS));
    }

    private float lerp(float partialTick) {
        return Math.min(1, (this.syncAge + partialTick) / SYNC_INTERVAL);
    }

    public @Nullable Player playerOwner() {
        return this.getOwner() instanceof Player player ? player : null;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 4096;
    }

    @Override
    public boolean canUsePortal(boolean allowPassengers) {
        return false;
    }

    @Override
    protected boolean canHitEntity(Entity entity) {
        return false;
    }

    @Override
    protected double getDefaultGravity() {
        return GRAVITY;
    }

    // ---- 每刻

    @Override
    public void tick() {
        super.tick();
        this.phaseAge++;
        if (this.syncAge < SYNC_INTERVAL) {
            this.syncAge++;
        } else {
            this.tensionFrom = this.entityData.get(TENSION);
            this.progressFrom = this.entityData.get(PROGRESS);
        }
        Player owner = this.playerOwner();
        if (owner == null) {
            if (!this.level().isClientSide()) {
                this.discard();
            }
            return;
        }
        if (!this.level().isClientSide() && this.shouldStop(owner)) {
            this.discard();
            return;
        }
        BlockPos pos = this.blockPosition();
        FluidState fluid = this.level().getFluidState(pos);
        float height = Fishing.fishable(fluid) ? fluid.getHeight(this.level(), pos) : 0;
        boolean floating = height > 0;
        Phase phase = this.phase();
        if (phase == Phase.FLYING || phase == Phase.GROUNDED) {
            if (fluid.is(FluidTags.LAVA)) {
                if (owner instanceof ServerPlayer player) {
                    Fishing.tell(player, Component.translatable("message.universe.fishing.lava"));
                    this.playSound(net.minecraft.sounds.SoundEvents.FIRE_EXTINGUISH, 0.5F, 1.4F);
                    this.discard();
                }
                return;
            }
            if (floating) {
                this.setDeltaMovement(this.getDeltaMovement().multiply(0.3, 0.2, 0.3));
                if (!this.level().isClientSide()) {
                    this.landInWater();
                }
            } else if (phase == Phase.FLYING) {
                this.limitRange(owner);
            }
        } else if (floating) {
            this.bob(pos, height, phase);
        } else if (!this.level().isClientSide()) {
            // 水没了（结冰、被抽干），重新掉下去
            this.reset(Phase.FLYING);
        }
        if (!floating) {
            this.setDeltaMovement(this.getDeltaMovement().add(0, -this.getGravity(), 0));
        }
        this.move(MoverType.SELF, this.getDeltaMovement());
        this.updateRotation();
        if (this.phase() == Phase.FLYING && !floating && (this.onGround() || this.horizontalCollision)) {
            this.setDeltaMovement(Vec3.ZERO);
            if (!this.level().isClientSide() && this.onGround()) {
                this.setPhase(Phase.GROUNDED);
                if (!this.warnedGround && owner instanceof ServerPlayer player) {
                    this.warnedGround = true;
                    Fishing.tell(player, Component.translatable("message.universe.fishing.not_water"));
                }
            }
        }
        this.setDeltaMovement(this.getDeltaMovement().scale(this.phase() == Phase.FLYING ? 0.985 : 0.92));
        this.reapplyPosition();
        if (this.level() instanceof ServerLevel level && owner instanceof ServerPlayer player) {
            this.serverTick(level, player);
        }
    }

    /** 主人不拿着甩竿的那一把钓鱼模式的工具、走远了、换了维度，浮标就收回。 */
    private boolean shouldStop(Player owner) {
        ItemStack held = owner.getMainHandItem();
        return !owner.isAlive() || owner.level() != this.level() || held != this.tool || !Fishing.fishing(held)
                || this.distanceToSqr(owner) > Fishing.LINE_LENGTH * Fishing.LINE_LENGTH;
    }

    /** 最远甩 24 格：飞出这么远就不再往前，直直落下。 */
    private void limitRange(Player owner) {
        double range = ToolWeapon.FISHING_RIG.range();
        double dx = this.getX() - owner.getX();
        double dz = this.getZ() - owner.getZ();
        if (dx * dx + dz * dz > range * range) {
            Vec3 motion = this.getDeltaMovement();
            this.setDeltaMovement(0, Math.min(motion.y, 0), 0);
        }
    }

    /** 浮在水面上一上一下（照原版浮标）；咬钩的时候往下拽。 */
    private void bob(BlockPos pos, float height, Phase phase) {
        Vec3 motion = this.getDeltaMovement();
        double force = this.getY() + motion.y - pos.getY() - height;
        if (Math.abs(force) < 0.01) {
            force += Math.signum(force) * 0.1;
        }
        double pull = 0;
        if (phase == Phase.BITE) {
            pull = -0.06 * this.random.nextFloat();
        } else if (phase == Phase.HOOKED) {
            pull = this.dashing() ? -0.08 * this.random.nextFloat() : -0.02 * this.random.nextFloat();
        }
        this.setDeltaMovement(motion.x * 0.9, motion.y - force * this.random.nextFloat() * 0.2 + pull, motion.z * 0.9);
        if (phase == Phase.HOOKED && !this.level().isClientSide()) {
            // 鱼拖着浮标在水里打转
            float angle = this.shadowAngle() + this.phaseAge * 0.07F;
            double strength = this.dashing() ? 0.05 : 0.012;
            this.setDeltaMovement(this.getDeltaMovement().add(Mth.sin(angle) * strength, 0, Mth.cos(angle) * strength));
        }
    }

    // ---- 服务端：等鱼、咬钩、收线

    private void serverTick(ServerLevel level, ServerPlayer owner) {
        switch (this.phase()) {
            case FLOATING -> {
                if (--this.timer <= 0) {
                    this.approach(level);
                }
            }
            case APPROACH -> {
                this.approachRipples(level);
                if (--this.timer <= 0) {
                    this.bite(level, owner);
                }
            }
            case BITE -> {
                if (--this.timer <= 0) {
                    Fishing.tell(owner, Component.translatable("message.universe.fishing.missed"));
                    this.reset(Phase.FLOATING);
                    this.planWait();
                }
            }
            case HOOKED -> this.reel(level, owner);
            case GROUNDED -> {
                if (this.phaseAge > GROUNDED_LIFE) {
                    this.discard();
                }
            }
            case FLYING -> {
            }
        }
    }

    private void landInWater() {
        this.setPhase(Phase.FLOATING);
        this.planWait();
        this.playSound(FishingSounds.LAND.get(), 0.5F, 0.9F + this.random.nextFloat() * 0.3F);
    }

    /** 定下等多久：一共 8–20 秒（按饵和放生缩短），最后 2–4 秒是鱼影游过来。 */
    private void planWait() {
        int wait = BiteTimer.waitTicks(this.bait, this.blessed, this.rng);
        this.approachPlanned = BiteTimer.approachTicks(wait, this.rng);
        this.timer = wait - this.approachPlanned;
    }

    /** 回到等鱼（或者飞着）的状态：鱼影、张力都清掉。 */
    private void reset(Phase phase) {
        this.setPhase(phase);
        this.pending = null;
        this.reeling = null;
        this.entityData.set(SHADOW, NO_SHADOW);
        this.entityData.set(CUES, (byte) 0);
    }

    /** 鱼影出现：这时就定下咬钩的是什么。 */
    private void approach(ServerLevel level) {
        BlockPos pos = this.blockPosition();
        Fishing.Water water = Fishing.water(level, pos).orElse(null);
        if (water == null) {
            this.reset(Phase.FLYING);
            return;
        }
        CatchRoll.Situation situation = Fishing.situation(level, pos, water.depth());
        this.pending = CatchRoll.roll(Fishing.pool(level, water.habitat()), situation, this.bait, this.blessed, CatchRoll.Odds.ROD, this.rng);
        int approach = Math.max(1, this.approachPlanned);
        this.timer = approach;
        this.entityData.set(SHADOW, this.pending.isFish() ? this.pending.species().index() : SHADOW_JUNK);
        this.entityData.set(APPROACH_TICKS, approach);
        this.entityData.set(SHADOW_ANGLE, (float) (this.rng.nextDouble() * Math.PI * 2));
        this.setPhase(Phase.APPROACH);
    }

    /** 鱼影游过来的那几秒，水面上冒一点细小的波纹（照原版的鱼）。 */
    private void approachRipples(ServerLevel level) {
        int total = Math.max(1, this.approachTicks());
        double distance = 3.0 * this.timer / total;
        float angle = this.shadowAngle();
        double x = this.getX() + Mth.sin(angle) * distance;
        double z = this.getZ() + Mth.cos(angle) * distance;
        double y = Mth.floor(this.getY()) + 1.0;
        if (this.random.nextFloat() < 0.35F) {
            level.sendParticles(ParticleTypes.FISHING, x, y, z, 0, Mth.cos(angle) * 0.04, 0.01, -Mth.sin(angle) * 0.04, 1.0);
        }
        if (this.random.nextFloat() < 0.1F) {
            level.sendParticles(ParticleTypes.BUBBLE, x, y - 0.2, z, 1, 0.05, 0.05, 0.05, 0);
        }
    }

    private void bite(ServerLevel level, ServerPlayer owner) {
        this.playSound(FishingSounds.BITE.get(), 0.7F, 0.9F + this.random.nextFloat() * 0.2F);
        double y = this.getY() + 0.5;
        level.sendParticles(ParticleTypes.BUBBLE, this.getX(), y, this.getZ(), 6, 0.2, 0, 0.2, 0.2);
        level.sendParticles(ParticleTypes.FISHING, this.getX(), y, this.getZ(), 6, 0.2, 0, 0.2, 0.2);
        this.setPhase(Phase.BITE);
        this.timer = BiteTimer.BITE_WINDOW;
        if (level.getDifficulty() == Difficulty.PEACEFUL) {
            // 和平难度不玩收线：咬钩就钓上来
            this.hook(owner);
            if (this.reeling != null) {
                this.finish(owner, Reeling.Outcome.CAUGHT);
            }
        }
    }

    /** 主人右键：咬钩时钩住，收线时不管（按住就是收线），别的时候把浮标收回来。 */
    void click(ServerPlayer owner) {
        switch (this.phase()) {
            case BITE -> this.hook(owner);
            case HOOKED -> {
            }
            default -> this.discard();
        }
    }

    /** 钩住：饵用掉一个；是鱼就开始收线，别的东西直接拉上来。 */
    private void hook(ServerPlayer owner) {
        Catch caught = this.pending;
        if (caught == null) {
            this.discard();
            return;
        }
        // 同一刻里先换到另一把工具再右键，浮标还没来得及收回：不扣换上来那一把的饵，这一竿作废
        if (!Fishing.consumeBait(owner, this.tool)) {
            this.discard();
            return;
        }
        this.playSound(FishingSounds.HOOK.get(), 0.8F, 1.0F);
        if (!caught.isFish()) {
            Fishing.land(owner, caught, this.position());
            this.discard();
            return;
        }
        FishSpecies species = caught.species();
        this.reeling = new Reeling(species, this.rng);
        this.setPhase(Phase.HOOKED);
        this.syncReeling();
    }

    private void reel(ServerLevel level, ServerPlayer owner) {
        Reeling reeling = this.reeling;
        if (reeling == null) {
            this.reset(Phase.FLOATING);
            return;
        }
        boolean holding = owner.isUsingItem() && owner.getUseItem().getItem() instanceof MultitoolItem;
        boolean wasDashing = reeling.dashing();
        Reeling.Outcome outcome = reeling.tick(holding, this.rng);
        if (reeling.dashing() && !wasDashing) {
            this.playSound(FishingSounds.DASH.get(), 0.8F, 0.8F + this.random.nextFloat() * 0.3F);
            level.sendParticles(ParticleTypes.SPLASH, this.getX(), this.getY() + 0.3, this.getZ(), 10, 0.3, 0.05, 0.3, 0.2);
        }
        if (holding && reeling.ticks() % 4 == 0) {
            level.playSound(null, owner.getX(), owner.getY(), owner.getZ(), FishingSounds.REEL.get(), SoundSource.PLAYERS, 0.35F,
                    0.8F + (float) reeling.tension() * 0.6F);
        }
        if (reeling.tension() > 0.85 && reeling.ticks() % 6 == 0) {
            level.playSound(null, owner.getX(), owner.getY(), owner.getZ(), FishingSounds.STRAIN.get(), SoundSource.PLAYERS, 0.5F,
                    0.9F + (float) reeling.tension() * 0.3F);
        }
        if (outcome != Reeling.Outcome.REELING || reeling.ticks() % SYNC_INTERVAL == 0) {
            this.syncReeling();
        }
        if (outcome != Reeling.Outcome.REELING) {
            this.finish(owner, outcome);
        }
    }

    private void syncReeling() {
        Reeling reeling = this.reeling;
        if (reeling == null) {
            return;
        }
        this.entityData.set(TENSION, (float) reeling.tension());
        this.entityData.set(PROGRESS, (float) reeling.progress());
        int cues = (reeling.dashing() ? 1 : 0) | (reeling.warning() ? 2 : 0) | (reeling.slack() > Reeling.SLACK_TICKS / 2 ? 4 : 0);
        this.entityData.set(CUES, (byte) cues);
    }

    private void finish(ServerPlayer owner, Reeling.Outcome outcome) {
        Catch caught = this.pending;
        switch (outcome) {
            case CAUGHT -> {
                if (caught != null) {
                    Fishing.land(owner, caught, this.position());
                }
            }
            case SNAPPED -> {
                this.playSound(FishingSounds.SNAP.get(), 0.9F, 1.0F);
                Fishing.tell(owner, Component.translatable("message.universe.fishing.snapped"));
            }
            case ESCAPED -> {
                this.playSound(FishingSounds.ESCAPE.get(), 0.7F, 1.0F);
                Fishing.tell(owner, Component.translatable("message.universe.fishing.escaped"));
            }
            case REELING -> {
                return;
            }
        }
        this.discard();
    }

    @Override
    public void onRemovedFromLevel() {
        super.onRemovedFromLevel();
        if (!this.level().isClientSide()) {
            Fishing.forget(this);
        }
    }

    // ---- 给测试用

    /** 立刻进入咬钩（跳过等鱼和鱼影），钓上来的是 {@code caught}。 */
    public void forceBite(Catch caught) {
        if (this.level() instanceof ServerLevel level && this.getOwner() instanceof ServerPlayer owner) {
            this.pending = caught;
            this.entityData.set(SHADOW, caught.isFish() ? caught.species().index() : SHADOW_JUNK);
            this.bite(level, owner);
        }
    }

    /** 现在的收线状态（测试看张力和进度）。 */
    public @Nullable Reeling reeling() {
        return this.reeling;
    }

    /** 这一竿用的饵。 */
    public Bait bait() {
        return this.bait;
    }

    /** 这一竿有没有放生的加成。 */
    public boolean blessed() {
        return this.blessed;
    }

    /** 浮标落水后一共要等几刻才咬钩（测试看鱼饵、放生缩短了等鱼的时间）。 */
    public int waitLeft() {
        return this.phase() == Phase.FLOATING ? this.timer + this.approachPlanned : this.timer;
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
    }
}
