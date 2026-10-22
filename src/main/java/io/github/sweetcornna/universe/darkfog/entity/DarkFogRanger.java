package io.github.sweetcornna.universe.darkfog.entity;

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

/** 游骑兵：飞，快，绕着目标飞、远程打激光弹，参加进攻。 */
public class DarkFogRanger extends DarkFogUnit {
    private static final double SPEED = 2.2;

    public DarkFogRanger(EntityType<? extends DarkFogRanger> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, UnitKind.RANGER.baseHealth())
                .add(Attributes.ATTACK_DAMAGE, UnitKind.RANGER.baseDamage())
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.FLYING_SPEED, 0.42)
                .add(Attributes.FOLLOW_RANGE, 48)
                .add(Attributes.ARMOR, 2);
    }

    @Override
    public UnitKind kind() {
        return UnitKind.RANGER;
    }

    @Override
    public boolean grounded() {
        return false;
    }

    @Override
    protected Vec3 engagePosition(ServerLevel level, Vec3 aim) {
        this.orbit += 0.035;
        double radius = 11 + Math.sin(this.orbit * 2.3) * 2;
        return new Vec3(aim.x + Math.cos(this.orbit) * radius, aim.y + 5, aim.z + Math.sin(this.orbit) * radius);
    }

    @Override
    protected void strike(ServerLevel level, LivingEntity enemy, float damage) {
        Vec3 from = this.getEyePosition().add(this.getLookAngle().scale(0.6));
        Vec3 aim = enemy.getEyePosition().add(0, -0.4, 0);
        aim = aim.add(enemy.getDeltaMovement().multiply(1, 0, 1).scale(from.distanceTo(aim) / SPEED * 0.8));
        DarkFogBolt.fire(level, this, from, aim.subtract(from), damage, DarkFogBolt.RANGER_COLOR, 0.55F, SPEED, 3.0F);
        this.playSound(SoundEvents.BLAZE_SHOOT, 0.4F, 1.7F + this.random.nextFloat() * 0.2F);
    }

    @Override
    protected void strikeBlock(ServerLevel level, BlockPos pos, float damage) {
        Vec3 from = this.getEyePosition().add(this.getLookAngle().scale(0.6));
        Vec3 aim = Vec3.atCenterOf(pos);
        DarkFogBolt.fire(level, this, from, aim.subtract(from), damage, DarkFogBolt.RANGER_COLOR, 0.55F, SPEED, 1.0F);
        this.playSound(SoundEvents.BLAZE_SHOOT, 0.4F, 1.7F + this.random.nextFloat() * 0.2F);
    }
}
