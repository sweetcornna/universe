package io.github.sweetcornna.universe.darkfog.space;

import io.github.sweetcornna.universe.core.darkfog.SpaceFlight;
import io.github.sweetcornna.universe.core.darkfog.SpaceUnitKind;
import io.github.sweetcornna.universe.darkfog.entity.DarkFogBolt;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * 座头鲸（ADR-0028 §八）：慢、耐打、炮重，只守巢。不往上冲，隔着 {@link #NEAR}–{@link #FAR} 格绕着目标转，
 * 背上的炮塔朝哪都能打，一梭三发紫色的重炮弹。
 */
public class DarkFogHumpback extends DarkFogSpaceUnit {
    /** 和目标保持的距离（离目标的外沿算）。 */
    static final double NEAR = 24;
    static final double FAR = 44;
    private static final double SHELL_SPEED = 2.4;
    private static final int SHELL_LIFETIME = 70;
    private static final double FIRE_RANGE = 120;
    private static final int BURST = 3;
    private static final int BURST_GAP = 6;
    private static final int COOLDOWN = 50;

    /** 绕着目标转的方向。 */
    private final double side;
    private int gunCooldown = 20;
    private int burstLeft;
    private int burstGap;

    public DarkFogHumpback(EntityType<? extends DarkFogHumpback> type, Level level) {
        super(type, level);
        this.side = this.random.nextBoolean() ? 1 : -1;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, SpaceUnitKind.HUMPBACK.baseHealth())
                .add(Attributes.MOVEMENT_SPEED, 0.2)
                .add(Attributes.FLYING_SPEED, 0.2)
                .add(Attributes.FOLLOW_RANGE, SpaceUnitKind.HUMPBACK.sight())
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.ARMOR, 10);
    }

    @Override
    public SpaceUnitKind kind() {
        return SpaceUnitKind.HUMPBACK;
    }

    @Override
    protected double patrolRadius() {
        return 18 + this.random.nextDouble() * 24;
    }

    @Override
    protected void engage(ServerLevel level, LivingEntity target, Vec3 aim, Vec3 targetVelocity, double clearance) {
        Vec3 pos = this.getBoundingBox().getCenter();
        double[] wanted = SpaceFlight.standoff(arr(pos), arr(aim), NEAR + clearance, FAR + clearance, this.kind().speed() * this.jamFactor(),
                this.side);
        this.steer(wanted, this.kind().accel());
        if (this.gunCooldown > 0) {
            this.gunCooldown--;
        }
        if (this.burstGap > 0) {
            this.burstGap--;
        }
        if (this.burstLeft == 0 && this.gunCooldown == 0 && aim.distanceTo(pos) < FIRE_RANGE && this.hasLineOfSight(target.getRootVehicle())) {
            this.burstLeft = BURST;
            this.burstGap = 0;
        }
        if (this.burstLeft > 0 && this.burstGap == 0) {
            this.burstLeft--;
            this.burstGap = BURST_GAP;
            if (this.burstLeft == 0) {
                this.gunCooldown = (int) Math.ceil((COOLDOWN + this.random.nextInt(20)) / Math.max(0.2, this.jamFactor()));
            }
            Vec3 muzzle = this.position().add(0, this.getBbHeight() * 0.95, 0);
            this.shoot(level, muzzle, aim, targetVelocity, DarkFogBolt.GUARDIAN_COLOR, 1.4F, SHELL_SPEED, 1.5F, SHELL_LIFETIME);
            this.playSound(SoundEvents.BLAZE_SHOOT, 1.2F, 0.5F + this.random.nextFloat() * 0.1F);
        }
    }
}
