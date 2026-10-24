package io.github.sweetcornna.universe.sentinel;

import java.util.EnumSet;

import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.core.sentinel.SentinelKind;
import io.github.sweetcornna.universe.registry.sound.HostileSounds;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * 维修无人机（ADR-0026）：16 点生命，不开火，用绿色的光束给 16 格内受伤的守卫机每秒回 4 点；没人要修时远远地绕着目标飞。
 */
public class RepairSentinel extends Sentinel {
    /** 给多远以内的守卫机回血。 */
    public static final double RANGE = 16;
    /** 每秒回多少血（每 5 刻回 1 点）。 */
    public static final float HEAL_PER_SECOND = 4;
    static final int BEAM_COLOR = 0x6CFFB0;

    public RepairSentinel(EntityType<? extends RepairSentinel> type, Level level) {
        super(type, level);
        this.xpReward = 6;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Sentinel.createAttributes(false)
                .add(Attributes.MAX_HEALTH, 16)
                .add(Attributes.FLYING_SPEED, 0.65)
                .add(Attributes.ARMOR, 1);
    }

    @Override
    public SentinelKind kind() {
        return SentinelKind.REPAIR;
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        this.goalSelector.addGoal(0, new RepairGoal());
    }

    @Override
    protected boolean armed() {
        return false;
    }

    @Override
    protected double standOff() {
        return 11;
    }

    /** 16 格内看得见的、伤得最重的守卫机（不算自己、不算在撤退的）。 */
    private @Nullable Sentinel patient() {
        if (!(this.level() instanceof ServerLevel level)) {
            return null;
        }
        Sentinel best = null;
        float worst = 1;
        for (Sentinel other : Sentinel.near(level, this.position(), RANGE)) {
            if (other == this || !other.isAlive() || other.retreating() || !this.hasLineOfSight(other)) {
                continue;
            }
            float share = other.getHealth() / other.getMaxHealth();
            if (share < worst) {
                worst = share;
                best = other;
            }
        }
        return best;
    }

    /** 修理：飞到伤员上方几格，看得见就用光束回血。 */
    private final class RepairGoal extends Goal {
        private @Nullable Sentinel patient;
        private int search;
        private int reposition;

        RepairGoal() {
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            RepairSentinel self = RepairSentinel.this;
            if (self.retreating() || --this.search > 0) {
                return false;
            }
            this.search = 10;
            this.patient = self.patient();
            return this.patient != null;
        }

        @Override
        public boolean canContinueToUse() {
            Sentinel patient = this.patient;
            return patient != null && patient.isAlive() && !patient.retreating() && patient.getHealth() < patient.getMaxHealth()
                    && !RepairSentinel.this.retreating() && RepairSentinel.this.distanceTo(patient) < RANGE + 6;
        }

        @Override
        public void start() {
            this.reposition = 0;
        }

        @Override
        public void stop() {
            this.patient = null;
            RepairSentinel.this.getNavigation().stop();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            RepairSentinel self = RepairSentinel.this;
            Sentinel patient = this.patient;
            if (patient == null) {
                return;
            }
            self.getLookControl().setLookAt(patient, 40, 40);
            double distance = self.distanceTo(patient);
            if (--this.reposition <= 0 || self.getNavigation().isDone()) {
                this.reposition = 20;
                double angle = self.random.nextDouble() * Math.PI * 2;
                double ring = 3 + self.random.nextDouble() * 2;
                self.getNavigation().moveTo(patient.getX() + Math.cos(angle) * ring, patient.getY() + patient.getBbHeight() + 2.5,
                        patient.getZ() + Math.sin(angle) * ring, 1.2);
            }
            if (distance > RANGE || !self.hasLineOfSight(patient) || !(self.level() instanceof ServerLevel level)) {
                return;
            }
            if (self.tickCount % 5 == 0) {
                patient.heal(HEAL_PER_SECOND / 4);
                Vec3 from = self.getEyePosition();
                Vec3 to = patient.position().add(0, patient.getBbHeight() * 0.5, 0);
                DustParticleOptions dust = new DustParticleOptions(BEAM_COLOR, 0.7F);
                int steps = (int) (from.distanceTo(to) / 0.4);
                for (int i = 1; i < steps; i++) {
                    Vec3 p = from.lerp(to, i / (double) steps);
                    level.sendParticles(dust, p.x, p.y, p.z, 1, 0.02, 0.02, 0.02, 0);
                }
                level.sendParticles(ParticleTypes.HAPPY_VILLAGER, to.x, to.y, to.z, 2, 0.3, 0.3, 0.3, 0);
            }
            if (self.tickCount % 30 == 0) {
                self.playSound(HostileSounds.REPAIR_WELD.get(), 0.6F, 1.0F);
            }
        }
    }
}
