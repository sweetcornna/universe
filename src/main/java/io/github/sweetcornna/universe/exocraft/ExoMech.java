package io.github.sweetcornna.universe.exocraft;

import io.github.sweetcornna.universe.core.vehicle.MechDrive;
import io.github.sweetcornna.universe.core.vehicle.VehicleKind;
import io.github.sweetcornna.universe.entity.PlasmaBolt;
import io.github.sweetcornna.universe.registry.UniverseItems;
import io.github.sweetcornna.universe.registry.sound.ShipSounds;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * 机甲（ADR-0026）：照无人深空的牛头人做的两足机甲。坐在胸口的驾驶舱里：舱里有空气、挡得住星球的危害（{@code PlayerSurvival}）。
 * 走得不快（探索车的 0.6 倍），但跨得上两格的坎；按住跳跃开背上的喷气，往上推、往前冲（探索车的 1.2 倍），喷 3 秒要落地歇一会儿。
 * 右臂一门爆能炮，按住攻击键朝准星连发等离子弹；左臂是车载扫描（坐在里面拿多功能工具右键）。27 格货舱。
 * <p>
 * 驾驶和探索车一样由驾驶员的客户端算：身子慢慢转向驾驶员看的方向，W、S 前后走，A、D 横着挪（{@link MechDrive}）。
 */
public class ExoMech extends UniverseVehicle {
    /** 走着每刻烧多少燃料。 */
    public static final double WALK_FUEL = 0.04;
    /** 喷气每刻烧多少燃料。 */
    public static final double JET_FUEL = 0.12;
    /** 爆能炮一发多少伤害、烧多少燃料、隔几刻。 */
    public static final float CANNON_DAMAGE = 8;
    public static final double CANNON_FUEL = 0.5;
    public static final int CANNON_INTERVAL = 6;
    /** 等离子弹的颜色：比多功能工具的偏橙。 */
    private static final int CANNON_COLOR = 0xFFB04A;
    public static final int SCAN_COOLDOWN = 100;
    /** 第三人称镜头至少拉开多远。 */
    public static final float CAMERA_DISTANCE = 7.5F;
    /** 瞄多远：炮弹从右臂打出，朝准星对着的点飞。 */
    private static final double AIM_RANGE = 64;

    private static final EntityDataAccessor<Float> DATA_JET = SynchedEntityData.defineId(ExoMech.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> DATA_THRUST = SynchedEntityData.defineId(ExoMech.class, EntityDataSerializers.BOOLEAN);

    /** 喷气的储量：算驾驶的那一端用它推；服务端在别人开的时候另算一份，用来扣燃料、告诉旁人喷口有没有火。 */
    private final MechDrive.Jet jet = new MechDrive.Jet();
    /** 这一刻喷气推着（算驾驶的那一端）。 */
    private boolean thrust;

    // 服务端
    private boolean firing;
    private int cannonCooldown;
    private int scanCooldown;

    // 客户端：腿迈过的相位（弧度）和这一刻迈得多大
    public float stride;
    public float strideO;
    public float strideAmount;

    public ExoMech(EntityType<? extends ExoMech> type, Level level) {
        super(type, level, VehicleKind.EXO_MECH.cargoSlots());
    }

    @Override
    public VehicleKind kind() {
        return VehicleKind.EXO_MECH;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_JET, 1.0F);
        builder.define(DATA_THRUST, false);
    }

    /** 喷气还剩多少，0 到 1（仪表盘用）：驾驶员自己那一端算得最准，别的地方读同步的数。 */
    public float jetShare() {
        return this.isLocalInstanceAuthoritative() ? (float) this.jet.share() : this.entityData.get(DATA_JET);
    }

    /** 喷气这一刻开着（喷口的火）。 */
    public boolean thrusting() {
        return this.isLocalInstanceAuthoritative() ? this.thrust : this.entityData.get(DATA_THRUST);
    }

    /** 炮还要多少刻才能再打（服务端）。 */
    int cannonCooldown() {
        return this.cannonCooldown;
    }

    // ---- 每刻

    @Override
    public void tick() {
        super.tick();
        if (this.isLocalInstanceAuthoritative()) {
            this.drive();
            this.move(MoverType.SELF, this.getDeltaMovement());
        }
        if (this.level().isClientSide()) {
            double moved = Math.sqrt(Math.pow(this.getX() - this.xo, 2) + Math.pow(this.getZ() - this.zo, 2));
            this.strideO = this.stride;
            this.stride += (float) (moved * 1.6);
            this.strideAmount = Mth.lerp(0.3F, this.strideAmount, (float) Math.min(1, moved / MechDrive.WALK_SPEED));
            // 每迈过半个周期落一只脚，正是身子压到最低的那一下；各个客户端自己算，不走网络
            if (this.onGround() && this.strideAmount > 0.2F && Mth.floor(this.strideO / Mth.PI) != Mth.floor(this.stride / Mth.PI)) {
                this.level().playLocalSound(this.getX(), this.getY(), this.getZ(), ShipSounds.MECH_STEP.get(), SoundSource.NEUTRAL,
                        0.4F + 0.5F * this.strideAmount, 0.9F + this.random.nextFloat() * 0.15F, false);
            }
            if (this.thrusting()) {
                for (int side = -1; side <= 1; side += 2) {
                    Vec3 nozzle = this.position().add(new Vec3(side * 0.375, 1.85, -0.66).yRot(-this.getYRot() * Mth.DEG_TO_RAD));
                    this.level().addParticle(ParticleTypes.FLAME, nozzle.x, nozzle.y, nozzle.z, 0, -0.25, 0);
                    this.level().addParticle(ParticleTypes.SMOKE, nozzle.x, nozzle.y - 0.3, nozzle.z, 0, -0.1, 0);
                }
            }
        }
        if (this.level() instanceof ServerLevel level) {
            this.serverTick(level);
        }
    }

    /** 算驾驶的那一端：身子转向驾驶员看的方向，迈步、喷气、重力。 */
    private void drive() {
        Player driver = this.getControllingPassenger() instanceof Player player ? player : null;
        boolean powered = this.powered(driver);
        boolean grounded = this.onGround();
        boolean jetting = this.jet.tick(driver != null && powered && driver.isJumping(), grounded);
        this.thrust = jetting;
        if (driver != null) {
            this.setYRot(MechDrive.turn(this.getYRot(), driver.getYRot()));
        }
        Vec3 v = this.getDeltaMovement();
        double forward = driver == null ? 0 : driver.zza;
        double strafe = driver == null ? 0 : driver.xxa;
        double[] walk = MechDrive.walk(v.x, v.z, this.getYRot(), forward, strafe, powered, grounded, jetting);
        double vy = MechDrive.climb(v.y, jetting, this.gravityScale());
        this.setDeltaMovement(walk[0], grounded && !jetting && vy < 0 ? Math.max(vy, -0.2) : vy, walk[1]);
    }

    private void serverTick(ServerLevel level) {
        ServerPlayer driver = this.getControllingPassenger() instanceof ServerPlayer player ? player : null;
        if (!this.isLocalInstanceAuthoritative()) {
            // 玩家开着：驾驶在他的客户端算，这边照着他按的键另算一份喷气
            this.thrust = this.jet.tick(driver != null && this.powered(driver) && driver.getLastClientInput().jump(), this.onGround());
        }
        if (driver != null && !driver.hasInfiniteMaterials()) {
            if (this.thrust) {
                this.burnFuel(driver, JET_FUEL);
            } else {
                this.burnDriveFuel(driver, WALK_FUEL);
            }
        }
        this.entityData.set(DATA_JET, (float) this.jet.share());
        this.entityData.set(DATA_THRUST, this.thrust);
        if (this.scanCooldown > 0) {
            this.scanCooldown--;
        }
        if (this.cannonCooldown > 0) {
            this.cannonCooldown--;
        }
        if (this.firing && driver != null && this.cannonCooldown == 0) {
            this.fireCannon(level, driver);
        }
        if (driver == null) {
            this.firing = false;
        }
    }

    // ---- 爆能炮

    @Override
    void setControls(boolean firing, boolean thirdPerson) {
        this.firing = firing;
    }

    /** 右臂的炮口（和模型对齐）。 */
    public Vec3 muzzle() {
        return this.position().add(new Vec3(-0.81, 2.03, 1.15).yRot(-this.getYRot() * Mth.DEG_TO_RAD));
    }

    private void fireCannon(ServerLevel level, ServerPlayer driver) {
        if (!driver.hasInfiniteMaterials() && this.fuelLeft() < CANNON_FUEL) {
            if (this.tickCount % 40 == 0) {
                driver.sendOverlayMessage(Component.translatable("hud.universe.exocraft.no_fuel"));
            }
            return;
        }
        Vec3 eye = driver.getEyePosition();
        Vec3 look = driver.getLookAngle();
        HitResult hit = level.clip(new ClipContext(eye, eye.add(look.scale(AIM_RANGE)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE,
                driver));
        Vec3 from = this.muzzle();
        Vec3 direction = hit.getLocation().subtract(from);
        if (direction.lengthSqr() < 1) {
            direction = look;
        }
        level.addFreshEntity(PlasmaBolt.shoot(level, driver, from, direction, CANNON_DAMAGE, CANNON_COLOR));
        this.burnFuel(driver, CANNON_FUEL);
        this.cannonCooldown = CANNON_INTERVAL;
        level.playSound(null, from.x, from.y, from.z, ShipSounds.CANNON.get(), SoundSource.PLAYERS, 0.8F, 0.95F + this.random.nextFloat() * 0.1F);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, from.x, from.y, from.z, 3, 0.05, 0.05, 0.05, 0.05);
    }

    // ---- 扫描

    @Override
    void scan(ServerPlayer player) {
        if (this.scanCooldown > 0) {
            player.sendOverlayMessage(Component.translatable("message.universe.multitool.charging"));
            return;
        }
        this.scanCooldown = SCAN_COOLDOWN;
        Exocraft.scanAround(this, player);
    }

    @Override
    protected Item getDropItem() {
        return UniverseItems.EXO_MECH.get();
    }

    // ---- 乘坐

    @Override
    protected Vec3 getPassengerAttachmentPoint(Entity passenger, EntityDimensions dimensions, float scale) {
        // 驾驶舱在胸口，座椅面离地 1.75 格（和模型对齐）
        return new Vec3(0, 1.75, 0).yRot(-this.getYRot() * Mth.DEG_TO_RAD);
    }

    @Override
    protected void removePassenger(Entity passenger) {
        super.removePassenger(passenger);
        this.firing = false;
    }

    @Override
    public Vec3 getDismountLocationForPassenger(net.minecraft.world.entity.LivingEntity passenger) {
        // 从机甲脚边下来：先试左边、右边、后面
        for (float side : new float[] {-90, 90, 180, 0}) {
            Vec3 direction = getCollisionHorizontalEscapeVector(this.getBbWidth() * Mth.SQRT_OF_TWO, passenger.getBbWidth(),
                    this.getYRot() + side);
            Vec3 target = new Vec3(this.getX() + direction.x, this.getY(), this.getZ() + direction.z);
            if (this.level().noCollision(passenger, passenger.getDimensions(passenger.getPose()).makeBoundingBox(target))) {
                return target;
            }
        }
        return super.getDismountLocationForPassenger(passenger);
    }

    // ---- 物理

    @Override
    public float maxUpStep() {
        return MechDrive.STEP;
    }

    @Override
    public boolean causeFallDamage(double fallDistance, float damageModifier, DamageSource damageSource) {
        // 腿吃住冲击，人不摔
        return false;
    }
}
