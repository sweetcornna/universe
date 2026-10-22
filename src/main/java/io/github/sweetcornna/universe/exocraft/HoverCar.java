package io.github.sweetcornna.universe.exocraft;

import io.github.sweetcornna.universe.core.vehicle.HoverDrive;
import io.github.sweetcornna.universe.core.vehicle.VehicleKind;
import io.github.sweetcornna.universe.core.vehicle.WheelDrive;
import io.github.sweetcornna.universe.registry.UniverseItems;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * 悬浮车（ADR-0026）：照无人深空的游牧者号做的单座悬浮车，最快的载具，探索车的 1.4 倍。车底四个推力垫托着，悬在地面或水面上一格，
 * 开得过湖和海。没有轮子，地面抓不住：松了油门滑得远，急转弯会甩。18 格货舱，采矿激光和车载扫描和探索车一样。
 * <p>
 * 离地多高由 {@link HoverDrive} 算：每刻往下探一次（车中间和车头前一格，取近的那个，上坡时提前抬起来），方块和水面都算地面。
 */
public class HoverCar extends Exocraft {
    /** 往下探多远：比推力垫够得着的再多一格，探不到就当悬空。 */
    private static final double PROBE = HoverDrive.SENSE + 1;
    /** 探的起点在车底往上多少：车陷进水里时从水面以下探，离地是负数，推力垫使劲往上托。 */
    private static final double PROBE_START = 0.9;

    /** 这一刻车底离地多高（算驾驶的那一端）。 */
    private double clearance;

    public HoverCar(EntityType<? extends HoverCar> type, Level level) {
        super(type, level, VehicleKind.HOVER_CAR);
    }

    @Override
    protected WheelDrive wheels() {
        return WheelDrive.HOVER;
    }

    @Override
    protected boolean traction() {
        Vec3 ahead = new Vec3(-Mth.sin(this.getYRot() * Mth.DEG_TO_RAD), 0, Mth.cos(this.getYRot() * Mth.DEG_TO_RAD));
        this.clearance = Math.min(this.probe(this.position()), this.probe(this.position().add(ahead)));
        return HoverDrive.hovering(this.clearance);
    }

    @Override
    protected double verticalSpeed(double vy, boolean water) {
        return HoverDrive.lift(this.clearance, vy, this.gravityScale());
    }

    /** 从 {@code at} 往下探，车底到下面的方块或水面有多高；探不到是一个大于推力垫够得着的数。 */
    private double probe(Vec3 at) {
        Vec3 from = new Vec3(at.x, this.getY() + PROBE_START, at.z);
        Vec3 to = new Vec3(at.x, this.getY() - PROBE, at.z);
        BlockHitResult hit = this.level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, this));
        return hit.getType() == HitResult.Type.MISS ? PROBE + 1 : this.getY() - hit.getLocation().y;
    }

    /** 车底离地多高（算驾驶的那一端才准）。 */
    public double clearance() {
        return this.clearance;
    }

    @Override
    public void tick() {
        super.tick();
        // 推力垫往下喷一点气：悬在水面上时溅起水花
        if (this.level().isClientSide() && this.random.nextInt(3) == 0) {
            Vec3 pad = this.position().add((this.random.nextDouble() - 0.5) * 1.6, -0.2, (this.random.nextDouble() - 0.5) * 2.0);
            boolean overWater = this.level().getFluidState(net.minecraft.core.BlockPos.containing(pad.x, pad.y - 1.0, pad.z)).isSource();
            this.level().addParticle(overWater ? ParticleTypes.SPLASH : ParticleTypes.CLOUD, pad.x, pad.y, pad.z, 0, -0.05, 0);
        }
    }

    @Override
    protected double fuelPerTick() {
        return FUEL_PER_TICK;
    }

    @Override
    protected Vec3 muzzleOffset() {
        return new Vec3(0, 0.66, 1.55);
    }

    @Override
    protected Item getDropItem() {
        return UniverseItems.HOVER_CAR.get();
    }

    @Override
    protected Vec3 getPassengerAttachmentPoint(Entity passenger, EntityDimensions dimensions, float scale) {
        // 座舱在车身中间偏后，座椅面离车底 0.75 格（和模型对齐）
        return new Vec3(0, 0.75, -0.15).yRot(-this.getYRot() * Mth.DEG_TO_RAD);
    }

    @Override
    public boolean causeFallDamage(double fallDistance, float damageModifier, DamageSource damageSource) {
        // 推力垫接得住，车和人都不摔
        return false;
    }

    @Override
    public boolean isPushedByFluid() {
        return false;
    }
}
