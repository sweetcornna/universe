package io.github.sweetcornna.universe.sentinel;

import java.util.EnumSet;

import io.github.sweetcornna.universe.core.sentinel.Flank;
import io.github.sweetcornna.universe.registry.sound.HostileSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * 地面守卫机（ADR-0026）：四足机和步行机共用的部分。走地面寻路、有重力、能上台阶；不敌对时在附近的玩家周围走动，
 * 撤退时原地传送走（{@link Sentinel#tick}）。背上的散热口是弱点，从后面打伤害翻倍（{@link Flank}）。
 */
public abstract class GroundSentinel extends Sentinel {
    /** 交战时多少刻重新寻一次路。 */
    static final int REPATH = 10;

    protected GroundSentinel(EntityType<? extends GroundSentinel> type, Level level) {
        super(type, level);
        this.moveControl = new MoveControl<>(this);
        this.setNoGravity(false);
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        GroundPathNavigation navigation = new GroundPathNavigation(this, level);
        navigation.setCanOpenDoors(false);
        navigation.setCanFloat(true);
        return navigation;
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, this.engageGoal());
        this.goalSelector.addGoal(5, new PatrolGoal());
        this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 12));
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
    }

    /** 交战的 goal：只在 {@link #engaging()} 时用。 */
    protected abstract Goal engageGoal();

    /** 正在追捕目标：敌对、目标还活着、没在撤退、不是和平难度。 */
    protected boolean engaging() {
        LivingEntity target = this.getTarget();
        return this.isHostile() && target != null && target.isAlive() && !this.retreating()
                && this.level().getDifficulty() != Difficulty.PEACEFUL;
    }

    /** 水平方向上的朝向（偏航角 {@code yaw}，度）。 */
    static Vec3 forward(float yaw) {
        double rad = Math.toRadians(yaw);
        return new Vec3(-Math.sin(rad), 0, Math.cos(rad));
    }

    /** 目标偏离座舱（头）朝向多少度；炮和发射口只能朝前打。 */
    float offAim(LivingEntity target) {
        float yaw = (float) Math.toDegrees(Math.atan2(-(target.getX() - this.getX()), target.getZ() - this.getZ()));
        return Math.abs(Mth.wrapDegrees(yaw - this.getYHeadRot()));
    }

    /** 背上散热口的位置，挨打时冒火花的地方。 */
    Vec3 vent() {
        return this.position().add(forward(this.yBodyRot).scale(-this.getBbWidth() * 0.45)).add(0, this.getBbHeight() * 0.7, 0);
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        Vec3 from = source.getSourcePosition();
        boolean back = from != null && Flank.fromBehind(this.yBodyRot, from.x - this.getX(), from.z - this.getZ());
        boolean hurt = super.hurtServer(level, source, back ? damage * Flank.BACK_MULTIPLIER : damage);
        if (hurt && back) {
            Vec3 vent = this.vent();
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, vent.x, vent.y, vent.z, 14, 0.25, 0.25, 0.25, 0.4);
            level.sendParticles(ParticleTypes.CRIT, vent.x, vent.y, vent.z, 8, 0.2, 0.2, 0.2, 0.3);
            level.playSound(null, vent.x, vent.y, vent.z, HostileSounds.SENTINEL_VENT_HIT.get(), SoundSource.HOSTILE, 1.0F, 1.0F);
        }
        return hurt;
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (this.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.EXPLOSION, this.getX(), this.getY(0.5), this.getZ(), 3, this.getBbWidth() * 0.4,
                    this.getBbHeight() * 0.3, this.getBbWidth() * 0.4, 0);
        }
    }

    /** 脚步声：金属的重踏，体型越大越沉。 */
    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        float size = this.getBbHeight();
        this.playSound(HostileSounds.SENTINEL_STEP.get(), 0.35F + size * 0.15F, 1.2F - size * 0.1F);
    }

    /** 巡逻：在附近的玩家周围（没有玩家就在原地附近）找一块地面走过去。 */
    private final class PatrolGoal extends Goal {
        PatrolGoal() {
            this.setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            GroundSentinel self = GroundSentinel.this;
            return !self.isHostile() && !self.retreating() && self.getNavigation().isDone() && self.getRandom().nextInt(40) == 0;
        }

        @Override
        public boolean canContinueToUse() {
            return !GroundSentinel.this.isHostile() && !GroundSentinel.this.getNavigation().isDone();
        }

        @Override
        public void start() {
            GroundSentinel self = GroundSentinel.this;
            Player player = self.level().getNearestPlayer(self, 48);
            double cx = player != null ? player.getX() : self.getX();
            double cz = player != null ? player.getZ() : self.getZ();
            double angle = self.getRandom().nextDouble() * Math.PI * 2;
            double distance = player != null ? 8 + self.getRandom().nextDouble() * 10 : 3 + self.getRandom().nextDouble() * 10;
            int x = (int) Math.floor(cx + Math.cos(angle) * distance);
            int z = (int) Math.floor(cz + Math.sin(angle) * distance);
            self.getNavigation().moveTo(x + 0.5, self.groundY(x, z), z + 0.5, 0.7);
        }
    }
}
