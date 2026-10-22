package io.github.sweetcornna.universe.darkfog.entity;

import io.github.sweetcornna.universe.core.darkfog.Tactics;
import io.github.sweetcornna.universe.core.darkfog.UnitKind;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** 守卫者：飞，在基地上空绕大圈巡逻，有人靠近才打，只守家。打的是重一点的激光弹。 */
public class DarkFogGuardian extends DarkFogUnit {
    private static final double SPEED = 1.8;

    public DarkFogGuardian(EntityType<? extends DarkFogGuardian> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, UnitKind.GUARDIAN.baseHealth())
                .add(Attributes.ATTACK_DAMAGE, UnitKind.GUARDIAN.baseDamage())
                .add(Attributes.MOVEMENT_SPEED, 0.25)
                .add(Attributes.FLYING_SPEED, 0.21)
                .add(Attributes.FOLLOW_RANGE, 48)
                .add(Attributes.ARMOR, 8)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.6);
    }

    @Override
    public UnitKind kind() {
        return UnitKind.GUARDIAN;
    }

    @Override
    public boolean grounded() {
        return false;
    }

    @Override
    protected Vec3 pickPatrolPoint(ServerLevel level) {
        // 绕家转大圈：每次往前转 40° 左右
        this.orbit += 0.6 + this.random.nextDouble() * 0.3;
        Vec3 home = center(this.home());
        double radius = 12 + this.random.nextDouble() * 6;
        double x = home.x + Math.cos(this.orbit) * radius;
        double z = home.z + Math.sin(this.orbit) * radius;
        return new Vec3(x, this.groundY(level, x, z) + Tactics.hover(UnitKind.GUARDIAN) + this.random.nextDouble() * 2, z);
    }

    @Override
    protected Vec3 engagePosition(ServerLevel level, Vec3 aim) {
        this.orbit += 0.02;
        return new Vec3(aim.x + Math.cos(this.orbit) * 9, aim.y + 7, aim.z + Math.sin(this.orbit) * 9);
    }

    @Override
    protected void strike(ServerLevel level, LivingEntity enemy, float damage) {
        Vec3 from = this.getEyePosition().add(0, -0.3, 0);
        Vec3 aim = enemy.getEyePosition().add(0, -0.4, 0);
        aim = aim.add(enemy.getDeltaMovement().multiply(1, 0, 1).scale(from.distanceTo(aim) / SPEED * 0.8));
        DarkFogBolt.fire(level, this, from, aim.subtract(from), damage, DarkFogBolt.GUARDIAN_COLOR, 0.9F, SPEED, 2.5F);
        this.playSound(SoundEvents.BLAZE_SHOOT, 0.6F, 1.0F);
    }

    @Override
    protected void strikeBlock(ServerLevel level, BlockPos pos, float damage) {
        Vec3 from = this.getEyePosition().add(0, -0.3, 0);
        Vec3 aim = Vec3.atCenterOf(pos);
        DarkFogBolt.fire(level, this, from, aim.subtract(from), damage, DarkFogBolt.GUARDIAN_COLOR, 0.9F, SPEED, 1.0F);
        this.playSound(SoundEvents.BLAZE_SHOOT, 0.6F, 1.0F);
    }
}
