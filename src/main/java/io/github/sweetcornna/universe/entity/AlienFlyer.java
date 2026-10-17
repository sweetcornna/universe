package io.github.sweetcornna.universe.entity;

import io.github.sweetcornna.universe.core.ecology.CreatureSpecies;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomFlyingGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.PathType;

/** 会飞的动物：像鹦鹉一样在空中乱飞，偶尔落地歇脚，不受摔落伤害。 */
public class AlienFlyer extends AlienCreature {
    public AlienFlyer(EntityType<? extends AlienFlyer> type, Level level) {
        super(type, level);
        this.moveControl = new FlyingMoveControl<>(this, 10, false);
        this.setPathfindingMalus(PathType.FIRE_IN_NEIGHBOR, -1.0F);
        this.setPathfindingMalus(PathType.FIRE, -1.0F);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return AlienCreature.createAttributes().add(Attributes.FLYING_SPEED, 0.4);
    }

    @Override
    protected void applyStats(CreatureSpecies species) {
        super.applyStats(species);
        this.setBase(Attributes.FLYING_SPEED, species.speed() * 1.6);
    }

    @Override
    protected boolean isFlyerType() {
        return true;
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        FlyingPathNavigation navigation = new FlyingPathNavigation(this, level);
        navigation.setCanOpenDoors(false);
        navigation.setCanFloat(true);
        return navigation;
    }

    @Override
    protected void registerMovementGoals() {
        this.goalSelector.addGoal(5, new WaterAvoidingRandomFlyingGoal(this, 1.0));
    }

    /** 在空中（渲染时扇翅膀、收起腿）。 */
    public boolean isFlying() {
        return !this.onGround();
    }

    @Override
    protected void checkFallDamage(double ya, boolean onGround, BlockState onState, BlockPos pos) {
    }
}
