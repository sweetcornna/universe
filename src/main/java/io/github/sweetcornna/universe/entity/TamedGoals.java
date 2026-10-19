package io.github.sweetcornna.universe.entity;

import java.util.EnumSet;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;

/**
 * 驯服的异星动物的行为（ADR-0017）：原地待命、跟着主人（离得太远就传送到主人身边）、会打架的帮主人打。
 * 原版的这几个目标只认 {@code TamableAnimal}，异星动物不是原版的动物，照着原版的写法另写一份。
 */
final class TamedGoals {
    /** 主人离开多远开始跟（格）。 */
    private static final float FOLLOW_START = 10;
    /** 跟到多近停下。 */
    private static final float FOLLOW_STOP = 3;
    /** 主人离开多远直接传送过去（和原版的狼一样 12 格）。 */
    private static final double TELEPORT_DISTANCE = 12;

    private TamedGoals() {
    }

    /** 待命的时候站着不动：占着移动和跳跃，优先级排在最前面，闲逛、跟随、攻击都动不了。 */
    static final class Stay extends Goal {
        private final AlienCreature creature;

        Stay(AlienCreature creature) {
            this.creature = creature;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.JUMP));
        }

        @Override
        public boolean canUse() {
            return this.creature.isStaying() && !this.creature.isInWater() && this.creature.onGround() && !this.creature.isVehicle();
        }

        @Override
        public boolean canContinueToUse() {
            return this.creature.isStaying() && !this.creature.isVehicle();
        }

        @Override
        public void start() {
            this.creature.getNavigation().stop();
        }
    }

    /** 跟着主人：离开 {@link #FOLLOW_START} 格就走过去，离开 {@link #TELEPORT_DISTANCE} 格以上传送到主人身边。 */
    static final class FollowOwner extends Goal {
        private final AlienCreature creature;
        private final double speed;
        private final PathNavigation navigation;
        private @Nullable LivingEntity owner;
        private int recalc;
        private float oldWaterCost;

        FollowOwner(AlienCreature creature, double speed) {
            this.creature = creature;
            this.speed = speed;
            this.navigation = creature.getNavigation();
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity owner = this.creature.getOwner();
            if (owner == null || this.unableToMove(owner) || this.creature.distanceToSqr(owner) < FOLLOW_START * FOLLOW_START) {
                return false;
            }
            this.owner = owner;
            return true;
        }

        @Override
        public boolean canContinueToUse() {
            return this.owner != null && !this.navigation.isDone() && !this.unableToMove(this.owner)
                    && this.creature.distanceToSqr(this.owner) > FOLLOW_STOP * FOLLOW_STOP;
        }

        private boolean unableToMove(LivingEntity owner) {
            return this.creature.isStaying() || this.creature.isPassenger() || this.creature.isVehicle() || this.creature.mayBeLeashed()
                    || owner.isSpectator() || owner.level() != this.creature.level();
        }

        @Override
        public void start() {
            this.recalc = 0;
            this.oldWaterCost = this.creature.getPathfindingMalus(PathType.WATER);
            this.creature.setPathfindingMalus(PathType.WATER, 0);
        }

        @Override
        public void stop() {
            this.owner = null;
            this.navigation.stop();
            this.creature.setPathfindingMalus(PathType.WATER, this.oldWaterCost);
        }

        @Override
        public void tick() {
            if (this.owner == null) {
                return;
            }
            boolean far = this.creature.distanceToSqr(this.owner) >= TELEPORT_DISTANCE * TELEPORT_DISTANCE;
            if (!far) {
                this.creature.getLookControl().setLookAt(this.owner, 10, this.creature.getMaxHeadXRot());
            }
            if (--this.recalc <= 0) {
                this.recalc = this.adjustedTickDelay(10);
                if (far) {
                    this.teleportNear(this.owner.blockPosition());
                } else {
                    this.navigation.moveTo(this.owner, this.speed);
                }
            }
        }

        /** 在主人周围 2–3 格找一块能站的地方传过去，试十次。 */
        private void teleportNear(BlockPos target) {
            for (int attempt = 0; attempt < 10; attempt++) {
                int dx = this.creature.getRandom().nextIntBetweenInclusive(-3, 3);
                int dz = this.creature.getRandom().nextIntBetweenInclusive(-3, 3);
                if (Math.abs(dx) < 2 && Math.abs(dz) < 2) {
                    continue;
                }
                int dy = this.creature.getRandom().nextIntBetweenInclusive(-1, 1);
                BlockPos pos = target.offset(dx, dy, dz);
                if (this.canStandAt(pos)) {
                    this.creature.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, this.creature.getYRot(), this.creature.getXRot());
                    this.navigation.stop();
                    return;
                }
            }
        }

        private boolean canStandAt(BlockPos pos) {
            boolean flyer = this.creature instanceof AlienFlyer;
            if (!flyer && WalkNodeEvaluator.getPathTypeStatic(this.creature, pos) != PathType.WALKABLE) {
                return false;
            }
            if (!flyer && this.creature.level().getBlockState(pos.below()).getBlock() instanceof LeavesBlock) {
                return false;
            }
            BlockPos delta = pos.subtract(this.creature.blockPosition());
            return this.creature.level().noCollision(this.creature, this.creature.getBoundingBox().move(delta));
        }
    }

    /** 主人挨打：会打架的驯服动物去打凶手。 */
    static final class DefendOwner extends TargetGoal {
        private final AlienCreature creature;
        private @Nullable LivingEntity attacker;
        private int timestamp;

        DefendOwner(AlienCreature creature) {
            super(creature, false);
            this.creature = creature;
            this.setFlags(EnumSet.of(Goal.Flag.TARGET));
        }

        @Override
        public boolean canUse() {
            LivingEntity owner = this.creature.getOwner();
            if (!this.creature.fights() || this.creature.isStaying() || owner == null) {
                return false;
            }
            this.attacker = owner.getLastHurtByMob();
            return owner.getLastHurtByMobTimestamp() != this.timestamp && this.canAttack(this.attacker, TargetingConditions.DEFAULT);
        }

        @Override
        public void start() {
            this.mob.setTarget(this.attacker);
            LivingEntity owner = this.creature.getOwner();
            if (owner != null) {
                this.timestamp = owner.getLastHurtByMobTimestamp();
            }
            super.start();
        }
    }

    /** 主人打谁：会打架的驯服动物跟着打。 */
    static final class AssistOwner extends TargetGoal {
        private final AlienCreature creature;
        private @Nullable LivingEntity victim;
        private int timestamp;

        AssistOwner(AlienCreature creature) {
            super(creature, false);
            this.creature = creature;
            this.setFlags(EnumSet.of(Goal.Flag.TARGET));
        }

        @Override
        public boolean canUse() {
            LivingEntity owner = this.creature.getOwner();
            if (!this.creature.fights() || this.creature.isStaying() || owner == null) {
                return false;
            }
            this.victim = owner.getLastHurtMob();
            return owner.getLastHurtMobTimestamp() != this.timestamp && this.canAttack(this.victim, TargetingConditions.DEFAULT);
        }

        @Override
        public void start() {
            this.mob.setTarget(this.victim);
            LivingEntity owner = this.creature.getOwner();
            if (owner != null) {
                this.timestamp = owner.getLastHurtMobTimestamp();
            }
            super.start();
        }
    }
}
