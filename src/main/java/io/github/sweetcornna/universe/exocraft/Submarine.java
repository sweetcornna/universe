package io.github.sweetcornna.universe.exocraft;

import java.util.List;

import io.github.sweetcornna.universe.core.vehicle.SubmarineDrive;
import io.github.sweetcornna.universe.core.vehicle.VehicleKind;
import io.github.sweetcornna.universe.fluid.AcidFluid;
import io.github.sweetcornna.universe.network.ScanResultPayload;
import io.github.sweetcornna.universe.registry.UniverseItems;
import io.github.sweetcornna.universe.survival.Multitool;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 潜艇（ADR-0026）：照无人深空的鹦鹉螺号做的单人潜艇，18 格货舱，烧和飞船一样的燃料。
 * <p>
 * 只在水里推得动：W、S 是推进器，艇头慢慢转向驾驶员看的方向（抬头上浮、低头下潜），A、D 侧推；松开推进器就悬停在水里。
 * 浮到水面就上不去了，离了水只会往下掉。艇里有空气：驾驶员不耗外骨骼的氧气、不受水压，看得清水下（隐藏的潮涌能量）。
 * 按住攻击键打鱼雷，坐在艇里用多功能工具右键是声呐，标出 {@link #SONAR_RADIUS} 格内的地标和兴趣点。
 * 声呐不标矿脉：海底一扫上百个矿点，会把沉船和遗迹淹掉；找矿下艇用多功能工具。
 */
public class Submarine extends UniverseVehicle {
    public static final int CARGO_SLOTS = VehicleKind.SUBMARINE.cargoSlots();
    /** 推进器开着每刻烧多少（比探索车省一点：水里没有坡要爬）。 */
    public static final double FUEL_PER_TICK = 0.04;
    /** 一发鱼雷烧多少燃料。 */
    public static final double TORPEDO_FUEL = 4;
    /** 两发鱼雷之间隔多少刻。 */
    public static final int TORPEDO_RELOAD = 24;
    public static final int SONAR_RADIUS = 600;
    public static final int SONAR_COOLDOWN = 100;
    /** 第三人称镜头至少拉开多远。 */
    public static final float CAMERA_DISTANCE = 7.0F;
    /** 每刻的重力（乘上星球的表面重力），只在离了水时有用。 */
    private static final double GRAVITY = 0.08;

    private static final EntityDataAccessor<Integer> DATA_RELOAD = SynchedEntityData.defineId(Submarine.class, EntityDataSerializers.INT);

    /** 沿艇头方向的速度（格/刻）：在算驾驶的那一端才有意义。 */
    private double speed;

    // 服务端
    private boolean firing;
    private int sonarCooldown;

    // 客户端：螺旋桨转过的角度（弧度）
    public float propellerSpin;
    public float propellerSpinO;

    public Submarine(EntityType<? extends Submarine> type, Level level) {
        super(type, level, CARGO_SLOTS);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_RELOAD, 0);
    }

    @Override
    public VehicleKind kind() {
        return VehicleKind.SUBMARINE;
    }

    /** 鱼雷还要多少刻装好（两端都能读）。 */
    public int reload() {
        return this.entityData.get(DATA_RELOAD);
    }

    /** 艇身大半在水里（两端都能算）。 */
    public boolean submerged() {
        // 酸海里也开得动（ADR-0030）
        return AcidFluid.sea(this.level().getFluidState(BlockPos.containing(this.getX(), this.getY() + this.getBbHeight() * 0.6, this.getZ())));
    }

    // ---- 每刻

    @Override
    public void tick() {
        super.tick();
        if (this.isLocalInstanceAuthoritative()) {
            this.drive();
            Vec3 before = this.position();
            this.move(MoverType.SELF, this.getDeltaMovement());
            if (this.horizontalCollision || this.verticalCollision) {
                // 撞上了：速度只剩真的走出去的那点
                Vec3 heading = heading(this.getYRot(), this.getXRot());
                double moved = this.position().subtract(before).dot(heading);
                if (Math.abs(moved) < Math.abs(this.speed)) {
                    this.speed = moved;
                }
            }
        } else {
            this.speed = 0;
        }
        if (this.level().isClientSide()) {
            Vec3 heading = heading(this.getYRot(), this.getXRot());
            double moved = this.position().subtract(this.xo, this.yo, this.zo).dot(heading);
            this.propellerSpinO = this.propellerSpin;
            // 停着也慢慢转，表示推进器还开着
            this.propellerSpin += (float) (moved * 2.5) + (this.isVehicle() ? 0.05F : 0);
            if (this.isInWater() && Math.abs(moved) > 0.2 && this.random.nextInt(2) == 0) {
                Vec3 tail = this.position().add(heading.scale(-1.9)).add(0, 0.6, 0);
                this.level().addParticle(ParticleTypes.BUBBLE, tail.x, tail.y, tail.z, 0, 0.05, 0);
            }
        }
        if (this.level() instanceof ServerLevel level) {
            this.serverTick(level);
        }
    }

    /** 艇头方向（偏航 0 朝 +z，俯仰正数朝下）。 */
    static Vec3 heading(float yaw, float pitch) {
        double[] v = SubmarineDrive.velocity(yaw, pitch, 1, 0);
        return new Vec3(v[0], v[1], v[2]);
    }

    /** 算驾驶的那一端：艇头转向驾驶员看的方向，按推进器加减速；浮在水面上不去，离了水往下掉。 */
    private void drive() {
        Player driver = this.getControllingPassenger() instanceof Player player ? player : null;
        boolean water = this.isInWater();
        boolean submerged = water && this.submerged();
        boolean powered = this.powered(driver);
        double throttle = driver == null ? 0 : driver.zza;
        double strafe = driver == null || !water || !powered ? 0 : driver.xxa;
        float rate = SubmarineDrive.turnRate(this.speed);
        if (driver != null && water) {
            this.setYRot(SubmarineDrive.turnToward(this.getYRot(), driver.getYRot(), rate));
            float pitch = SubmarineDrive.clampPitch(driver.getXRot());
            // 浮在水面时艇头抬不起来
            this.setXRot(SubmarineDrive.turnToward(this.getXRot(), submerged ? pitch : Math.max(0, pitch), rate));
        } else {
            this.setXRot(SubmarineDrive.turnToward(this.getXRot(), 0, 1.5F));
        }
        this.speed = SubmarineDrive.speed(this.speed, throttle, powered, water);
        double[] v = SubmarineDrive.velocity(this.getYRot(), this.getXRot(), this.speed, strafe);
        double vy = this.getDeltaMovement().y;
        if (!water) {
            vy = (vy - GRAVITY * this.gravityScale()) * 0.98;
        } else if (!submerged) {
            // 浮在水面：上不去，慢慢沉回水线
            vy = Math.min(Mth.lerp(0.25, vy, v[1]), 0) - 0.01;
        } else {
            // 水里中性浮力：只剩推进器给的上下速度，掉进水里的速度很快被水吃掉
            vy = Mth.lerp(0.25, vy, v[1]);
        }
        this.setDeltaMovement(v[0], vy, v[2]);
    }

    private void serverTick(ServerLevel level) {
        ServerPlayer driver = this.getControllingPassenger() instanceof ServerPlayer player ? player : null;
        boolean infinite = driver != null && driver.hasInfiniteMaterials();
        if (this.isInWater()) {
            this.burnDriveFuel(driver, FUEL_PER_TICK);
        }
        if (this.sonarCooldown > 0) {
            this.sonarCooldown--;
        }
        int reload = this.reload();
        if (reload > 0) {
            this.entityData.set(DATA_RELOAD, reload - 1);
        }
        // 艇里有空气：潮涌能量让艇里的人在水下能呼吸、看得清（隐藏，不显示图标）
        for (Entity passenger : this.getPassengers()) {
            if (passenger instanceof Player player) {
                player.addEffect(new MobEffectInstance(MobEffects.CONDUIT_POWER, 60, 0, true, false, false));
                player.setAirSupply(player.getMaxAirSupply());
            }
        }
        if (this.firing && driver != null && reload == 0) {
            this.fireTorpedo(level, driver, infinite);
        }
        if (driver == null) {
            this.firing = false;
        }
    }

    // ---- 鱼雷

    @Override
    void setControls(boolean firing, boolean thirdPerson) {
        this.firing = firing;
    }

    /** 鱼雷从艇头打出去，朝驾驶员看的方向。 */
    private void fireTorpedo(ServerLevel level, ServerPlayer driver, boolean infinite) {
        if (!this.isInWater()) {
            return;
        }
        if (!infinite && this.fuelLeft() < TORPEDO_FUEL) {
            if (this.tickCount % 40 == 0) {
                driver.sendOverlayMessage(Component.translatable("hud.universe.exocraft.no_fuel"));
            }
            return;
        }
        Vec3 nose = this.position().add(heading(this.getYRot(), this.getXRot()).scale(2.0)).add(0, this.getBbHeight() * 0.45, 0);
        level.addFreshEntity(Torpedo.launch(level, driver, nose, driver.getLookAngle()));
        this.burnFuel(driver, TORPEDO_FUEL);
        this.entityData.set(DATA_RELOAD, TORPEDO_RELOAD);
        level.playSound(null, nose.x, nose.y, nose.z, SoundEvents.TRIDENT_RIPTIDE_1.value(), SoundSource.PLAYERS, 0.8F, 0.7F);
        level.sendParticles(ParticleTypes.BUBBLE, nose.x, nose.y, nose.z, 12, 0.2, 0.2, 0.2, 0.1);
    }

    // ---- 声呐

    @Override
    void scan(ServerPlayer player) {
        if (this.sonarCooldown > 0) {
            player.sendOverlayMessage(Component.translatable("message.universe.multitool.charging"));
            return;
        }
        this.sonarCooldown = SONAR_COOLDOWN;
        ServerLevel level = player.level();
        List<ScanResultPayload.PoiMarker> pois = Multitool.scanPois(player, SONAR_RADIUS);
        List<ScanResultPayload.LandmarkMarker> landmarks = Multitool.scanLandmarks(player, SONAR_RADIUS);
        PacketDistributor.sendToPlayer(player, new ScanResultPayload(List.of(), pois, landmarks));
        level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.NEUTRAL, 1.0F, 0.5F);
        for (int i = 0; i < 60; i++) {
            double angle = i * Math.PI * 2 / 60;
            level.sendParticles(ParticleTypes.NAUTILUS, this.getX() + Math.cos(angle) * 3, this.getY() + 0.7, this.getZ() + Math.sin(angle) * 3,
                    1, 0, 0, 0, 0);
        }
        player.sendOverlayMessage(Component.translatable("message.universe.submarine.sonar", landmarks.size(), pois.size()));
    }

    @Override
    protected Item getDropItem() {
        return UniverseItems.SUBMARINE.get();
    }

    // ---- 乘坐

    @Override
    protected Vec3 getPassengerAttachmentPoint(Entity passenger, EntityDimensions dimensions, float scale) {
        // 驾驶舱在艇身前半截的玻璃罩里（和模型对齐）
        return new Vec3(0, 0.35, 0.45).yRot(-this.getYRot() * Mth.DEG_TO_RAD);
    }

    @Override
    protected void removePassenger(Entity passenger) {
        super.removePassenger(passenger);
        this.firing = false;
        if (passenger instanceof Player player) {
            player.removeEffect(MobEffects.CONDUIT_POWER);
        }
    }

    @Override
    public boolean isPushedByFluid() {
        return false;
    }

    @Override
    public boolean canBeRiddenUnderFluidType(net.neoforged.neoforge.fluids.FluidType type, Entity rider) {
        return true;
    }
}
