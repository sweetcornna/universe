package io.github.sweetcornna.universe.entity.ship;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.block.ship.ShipHelmBlock;
import io.github.sweetcornna.universe.block.ship.ShipSeatBlock;
import io.github.sweetcornna.universe.core.ship.ShipFlight;
import io.github.sweetcornna.universe.core.ship.ShipFlight.Controls;
import io.github.sweetcornna.universe.core.ship.ShipFlight.Environment;
import io.github.sweetcornna.universe.core.ship.ShipShape;
import io.github.sweetcornna.universe.core.ship.ShipStats;
import io.github.sweetcornna.universe.registry.UniverseEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.InterpolationHandler;
import net.minecraft.world.entity.LinearInterpolationHandler;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.DismountHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.entity.IEntityWithComplexSpawn;

/**
 * 组装好的飞船（ADR-0008）。
 *
 * <p>船身是一组方块，飞行时从世界里搬到实体上，停船时原样放回去。飞行由服务端计算（{@link ShipFlight}），
 * 驾驶员的按键来自原版每刻上报的输入：WASD 推进和转向、空格上升、Shift 下降（着陆后按住停船）、疾跑键加力。
 * 客户端只做插值，所以多人游戏里各方看到的是同一艘船。
 */
public class ShipEntity extends Entity implements IEntityWithComplexSpawn {
    /** 着陆后按住 Shift 这么多刻就停船（把船放回方块）。 */
    public static final int PARK_TICKS = 20;
    /** 船飞出星球维度顶部这么高就进入太空。 */
    public static final int SPACE_ALTITUDE = 48;
    /** 太空里离维度底部最近能飞到多少格。 */
    private static final int SPACE_MARGIN = 32;
    /** 提示晚这么多刻再发：上船、穿过大气层时，客户端收到乘坐数据包会用原版的"按 Shift 下船"盖掉之前的提示。 */
    private static final int NOTICE_DELAY = 5;

    private static final EntityDataAccessor<Float> DATA_THROTTLE = SynchedEntityData.defineId(ShipEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_FUEL = SynchedEntityData.defineId(ShipEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_GRAVITY = SynchedEntityData.defineId(ShipEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> DATA_IMPULSE = SynchedEntityData.defineId(ShipEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_LANDED = SynchedEntityData.defineId(ShipEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> DATA_PARKING = SynchedEntityData.defineId(ShipEntity.class, EntityDataSerializers.INT);

    private @Nullable ShipStructure structure;
    /** 组装时驾驶台朝向对应的偏航；方块按这个方向存，画的时候转 {@code getYRot() - baseYaw}。 */
    private float baseYaw;
    private double fuel;
    private final ShipFlight flight = new ShipFlight(0);
    private boolean landed;
    private int parkingTicks;
    /** 这一刻船转了多少度，乘客跟着转。 */
    private float yawDelta;
    private float lastYaw;
    /** 每颗星球上最后一次起飞的位置（维度 ID → 打包坐标），回来时降落在附近。 */
    private final Map<Identifier, Long> departures = new HashMap<>();
    private @Nullable Component notice;
    private int noticeDelay;
    /** 刚穿过大气层：驾驶员松开升降键之前不理会它们。 */
    private boolean verticalLatched;
    private @Nullable ShipCollider collider;
    private @Nullable Environment environment;
    private @Nullable Level environmentLevel;
    /** 客户端：渲染缓存（由渲染器创建和使用，服务端始终为空）。 */
    public @Nullable Object renderCache;
    /** 客户端：上一刻的位置，用来算 HUD 上的速度。 */
    public Vec3 clientLastPos = Vec3.ZERO;
    public double clientSpeed;

    public ShipEntity(EntityType<? extends ShipEntity> type, Level level) {
        super(type, level);
    }

    /** 服务端：用组装好的方块造一艘船。 */
    static ShipEntity create(ServerLevel level, ShipStructure structure, double x, double y, double z, float yaw, double fuel) {
        ShipEntity ship = new ShipEntity(UniverseEntities.SHIP.get(), level);
        ship.structure = structure;
        ship.baseYaw = yaw;
        ship.fuel = Math.min(fuel, structure.stats().fuelCapacity());
        ship.snapTo(x, y, z, yaw, 0);
        ship.flight.restore(0, 0, 0, yaw);
        ship.lastYaw = yaw;
        ship.refreshDimensions();
        ship.entityData.set(DATA_FUEL, (float) ship.fuel);
        return ship;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_THROTTLE, 0F);
        builder.define(DATA_FUEL, 0F);
        builder.define(DATA_GRAVITY, 1F);
        builder.define(DATA_IMPULSE, false);
        builder.define(DATA_LANDED, true);
        builder.define(DATA_PARKING, 0);
    }

    // ---- 查询

    public @Nullable ShipStructure structure() {
        return this.structure;
    }

    public float baseYaw() {
        return this.baseYaw;
    }

    /** 船相对组装时转过的角度。 */
    public float relativeYaw(float partialTick) {
        return Mth.rotLerp(partialTick, this.yRotO, this.getYRot()) - this.baseYaw;
    }

    public float throttle() {
        return this.entityData.get(DATA_THROTTLE);
    }

    public float fuel() {
        return this.entityData.get(DATA_FUEL);
    }

    public float gravity() {
        return this.entityData.get(DATA_GRAVITY);
    }

    public boolean impulseActive() {
        return this.entityData.get(DATA_IMPULSE);
    }

    public boolean isLanded() {
        return this.entityData.get(DATA_LANDED);
    }

    public int parkingTicks() {
        return this.entityData.get(DATA_PARKING);
    }

    double fuelServer() {
        return this.fuel;
    }

    void setFuelServer(double fuel) {
        this.fuel = fuel;
    }

    Map<Identifier, Long> departures() {
        return this.departures;
    }

    ShipFlight flight() {
        return this.flight;
    }

    /** 驾驶员：第一个乘客。 */
    public @Nullable Player pilot() {
        return this.getFirstPassenger() instanceof Player player ? player : null;
    }

    // ---- 实体行为

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        if (this.structure == null) {
            return super.getDimensions(pose);
        }
        ShipShape shape = this.structure.shape();
        // 碰撞箱只用于视锥剔除、右键登船和追踪距离，取能罩住转动的船身的方柱
        return EntityDimensions.fixed((float) (shape.radius() * 2), shape.height());
    }

    @Override
    public Vec3 getLightProbePosition(float partialTickTime) {
        // 整艘船用一个光照，取顶层的高度：露天的船看起来是亮的
        double up = this.structure == null ? 0.5 : this.structure.shape().height() - 0.5;
        return this.getPosition(partialTickTime).add(0, up, 0);
    }

    @Override
    public boolean isPickable() {
        return !this.isRemoved();
    }

    @Override
    public boolean canBeCollidedWith(@Nullable Entity other) {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        return false;
    }

    @Override
    public boolean isClientAuthoritative() {
        // 由服务端计算飞行，客户端只插值
        return false;
    }

    @Override
    protected boolean isLocalClientAuthoritative() {
        return false;
    }

    @Override
    protected InterpolationHandler createInterpolationHandler() {
        return LinearInterpolationHandler.create(this, 2);
    }

    @Override
    public @Nullable LivingEntity getControllingPassenger() {
        return this.pilot();
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return this.structure != null && this.getPassengers().size() < 1 + this.structure.seatCount();
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand, Vec3 location) {
        if (player.isSecondaryUseActive()) {
            // 没人驾驶、停在地上的船：潜行右键直接停船
            if (this.level() instanceof ServerLevel && this.pilot() == null && this.landed) {
                if (!ShipAssembly.disassemble(this)) {
                    player.sendOverlayMessage(Component.translatable("message.universe.ship.blocked"));
                }
            }
            return InteractionResult.SUCCESS;
        }
        if (this.canAddPassenger(player)) {
            if (!this.level().isClientSide() && player.startRiding(this)) {
                this.noticeLater(Component.translatable(this.pilot() == player
                        ? "message.universe.ship.boarded_pilot" : "message.universe.ship.boarded_passenger"));
            }
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    /** 第 {@code index} 个乘客的座位：0 是驾驶台，其余依次是座椅。返回 {局部 x, 座面高度, 局部 z}。 */
    private double[] seat(int index) {
        ShipStructure.ShipBlock block;
        double height;
        if (index <= 0 || this.structure.seatCount() == 0) {
            block = this.structure.helm();
            height = ShipHelmBlock.SEAT_HEIGHT;
        } else {
            block = this.structure.seat(Math.min(index - 1, this.structure.seatCount() - 1));
            height = ShipSeatBlock.SEAT_HEIGHT;
        }
        return new double[] {block.x(), block.y() + height, block.z()};
    }

    /** 局部坐标（方块中心为整数）在世界里的位置。 */
    public Vec3 toWorld(double lx, double ly, double lz, float relativeYaw) {
        double[] r = ShipShape.rotate(lx, lz, relativeYaw);
        return new Vec3(this.getX() + r[0], this.getY() + ly, this.getZ() + r[1]);
    }

    @Override
    protected void positionRider(Entity passenger, MoveFunction moveFunction) {
        if (this.structure == null) {
            super.positionRider(passenger, moveFunction);
            return;
        }
        double[] seat = this.seat(this.getPassengers().indexOf(passenger));
        Vec3 pos = this.toWorld(seat[0], seat[1], seat[2], this.getYRot() - this.baseYaw);
        Vec3 offset = passenger.getVehicleAttachmentPoint(this);
        moveFunction.accept(passenger, pos.x - offset.x, pos.y - offset.y, pos.z - offset.z);
        if (this.yawDelta != 0) {
            passenger.setYRot(passenger.getYRot() + this.yawDelta);
            passenger.setYHeadRot(passenger.getYHeadRot() + this.yawDelta);
        }
    }

    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        if (this.structure == null) {
            return super.getDismountLocationForPassenger(passenger);
        }
        // 在船边找个能站人的地方
        double r = this.structure.shape().radius() + 1;
        float facing = passenger.getYRot();
        for (int i = 0; i < 8; i++) {
            double angle = Math.toRadians(facing + 90 + i * 45);
            Vec3 candidate = new Vec3(this.getX() - Math.sin(angle) * r, this.getY(), this.getZ() + Math.cos(angle) * r);
            BlockPos pos = BlockPos.containing(candidate);
            for (int dy = 0; dy <= 3; dy++) {
                Vec3 spot = DismountHelper.findSafeDismountLocation(passenger.getType(), this.level(), pos.above(dy), true);
                if (spot != null) {
                    return spot;
                }
            }
        }
        return super.getDismountLocationForPassenger(passenger);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.structure == null) {
            if (!this.level().isClientSide()) {
                this.discard();
            }
            return;
        }
        if (this.level() instanceof ServerLevel level) {
            this.serverTick(level);
        } else {
            this.clientSpeed = this.position().distanceTo(this.clientLastPos);
            this.clientLastPos = this.position();
            this.exhaust();
        }
        this.yawDelta = Mth.wrapDegrees(this.getYRot() - this.lastYaw);
        this.lastYaw = this.getYRot();
    }

    /** 客户端：推进器喷火、冒烟，脉冲引擎喷出蓝色的焰流。 */
    private void exhaust() {
        float throttle = this.throttle();
        boolean impulse = this.impulseActive();
        if (throttle < 0.03F && !impulse) {
            return;
        }
        float relative = this.getYRot() - this.baseYaw;
        int[] thrusters = this.structure.thrusters();
        // 推进器很多时每刻只挑一部分喷，粒子总数有个上限
        int stride = Math.max(1, thrusters.length / 24);
        for (int k = this.tickCount % stride; k < thrusters.length; k += stride) {
            ShipStructure.ShipBlock block = this.structure.block(thrusters[k]);
            Direction facing = block.state().hasProperty(BlockStateProperties.FACING) ? block.state().getValue(BlockStateProperties.FACING) : Direction.DOWN;
            if (this.random.nextFloat() < 0.35F + 0.5F * Math.min(1, throttle)) {
                this.emit(block, facing, relative, 0.12 + 0.25 * throttle, ParticleTypes.FLAME);
            }
            if (this.random.nextFloat() < 0.15F * throttle) {
                this.emit(block, facing, relative, 0.08, ParticleTypes.SMOKE);
            }
        }
        if (impulse) {
            for (int index : this.structure.engines()) {
                ShipStructure.ShipBlock block = this.structure.block(index);
                Direction facing = block.state().hasProperty(BlockStateProperties.HORIZONTAL_FACING)
                        ? block.state().getValue(BlockStateProperties.HORIZONTAL_FACING) : Direction.SOUTH;
                for (int i = 0; i < 3; i++) {
                    this.emit(block, facing, relative, 0.6, ParticleTypes.SOUL_FIRE_FLAME);
                }
                this.emit(block, facing, relative, 0.3, ParticleTypes.END_ROD);
            }
        }
    }

    private void emit(ShipStructure.ShipBlock block, Direction facing, float relativeYaw, double speed, ParticleOptions particle) {
        double nx = block.x() + facing.getStepX() * 0.6 + (this.random.nextDouble() - 0.5) * 0.4;
        double ny = block.y() + 0.5 + facing.getStepY() * 0.6 + (this.random.nextDouble() - 0.5) * 0.4;
        double nz = block.z() + facing.getStepZ() * 0.6 + (this.random.nextDouble() - 0.5) * 0.4;
        Vec3 pos = this.toWorld(nx, ny, nz, relativeYaw);
        double[] dir = ShipShape.rotate(facing.getStepX(), facing.getStepZ(), relativeYaw);
        Vec3 motion = this.getDeltaMovement();
        this.level().addParticle(particle, pos.x, pos.y, pos.z,
                dir[0] * speed + motion.x * 0.5, facing.getStepY() * speed + motion.y * 0.5, dir[1] * speed + motion.z * 0.5);
    }

    /** 穿过大气层后调用：驾驶员还按着的升降键要松开一次才生效。 */
    public void latchVertical() {
        this.verticalLatched = true;
    }

    /** 过几刻把提示发给船上的玩家（见 {@link #NOTICE_DELAY}）。 */
    public void noticeLater(Component message) {
        this.notice = message;
        this.noticeDelay = NOTICE_DELAY;
    }

    private void serverTick(ServerLevel level) {
        if (this.notice != null && --this.noticeDelay <= 0) {
            for (Entity passenger : this.getPassengers()) {
                if (passenger instanceof Player player) {
                    player.sendOverlayMessage(this.notice);
                }
            }
            this.notice = null;
        }
        ShipStats stats = this.structure.stats();
        if (this.environmentLevel != level) {
            this.environment = ShipEnvironment.of(level);
            this.environmentLevel = level;
            this.collider = new ShipCollider(level);
            this.entityData.set(DATA_GRAVITY, (float) (this.environment.space() ? 0 : this.environment.gravity()));
        }
        Environment env = this.environment;
        ShipCollider collider = this.collider;
        collider.reset();

        Player pilot = this.pilot();
        Controls controls;
        if (pilot instanceof ServerPlayer player) {
            Input input = player.getLastClientInput();
            // 刚穿过大气层时还按着的升降键先不算，松开再按才生效：不然一路冲进太空几百格，从星球上方飞过去
            if (this.verticalLatched && !input.jump() && !input.shift()) {
                this.verticalLatched = false;
            }
            boolean up = input.jump() && !this.verticalLatched;
            boolean down = input.shift() && !this.verticalLatched;
            controls = new Controls(input.forward(), input.backward(), input.left(), input.right(), up, down, input.sprint());
        } else if (this.landed || env.space()) {
            controls = Controls.NONE;
        } else {
            // 没人驾驶的船自己慢慢降落
            controls = new Controls(false, false, false, false, false, true, false);
        }
        boolean infinite = pilot != null && pilot.hasInfiniteMaterials();
        double used = this.flight.step(stats, controls, env, infinite || this.fuel > 0, this.landed);
        if (!infinite) {
            this.fuel = Math.max(0, this.fuel - used);
        }

        // 转向：转过去会嵌进地形就不转
        float oldYaw = this.getYRot();
        float newYaw = this.flight.yaw();
        if (Mth.wrapDegrees(newYaw - oldYaw) != 0
                && !collider.fits(ShipCollider.boxes(this.structure, this.getX(), this.getY(), this.getZ(), newYaw - this.baseYaw))) {
            this.flight.undoYaw();
            newYaw = this.flight.yaw();
        }
        this.setYRot(newYaw);

        // 移动
        float relative = newYaw - this.baseYaw;
        List<AABB> boxes = ShipCollider.boxes(this.structure, this.getX(), this.getY(), this.getZ(), relative);
        Vec3 wanted = new Vec3(this.flight.vx(), this.flight.vy(), this.flight.vz());
        Vec3 moved = collider.collide(boxes, wanted);
        this.flight.collided(Math.abs(moved.x - wanted.x) > 1e-6, Math.abs(moved.y - wanted.y) > 1e-6, Math.abs(moved.z - wanted.z) > 1e-6);
        this.setPos(this.getX() + moved.x, this.getY() + moved.y, this.getZ() + moved.z);
        this.setDeltaMovement(moved);
        if (env.space()) {
            // 太空维度上下只有几百格：飞到边上就停住，免得掉出世界（船会被删掉，乘客受伤）
            double y = Mth.clamp(this.getY(), level.getMinY() + SPACE_MARGIN, level.getMaxY() + SPACE_MARGIN * 8);
            if (y != this.getY()) {
                this.setPos(this.getX(), y, this.getZ());
                this.flight.restore(this.flight.vx(), 0, this.flight.vz(), this.flight.yaw());
            }
        }
        boolean wasLanded = this.landed;
        this.landed = !env.space() && collider.onGround(ShipCollider.boxes(this.structure, this.getX(), this.getY(), this.getZ(), relative));
        if (this.landed && !wasLanded && wanted.y < -0.6) {
            this.playSound(net.minecraft.sounds.SoundEvents.ANVIL_LAND, 0.6F, 0.7F);
        }

        // 停船：着陆后驾驶员按住 Shift
        if (pilot != null && controls.down() && this.landed && this.flight.speed() < 0.1) {
            this.parkingTicks++;
            if (this.parkingTicks >= PARK_TICKS) {
                this.parkingTicks = 0;
                if (!ShipAssembly.disassemble(this)) {
                    pilot.sendOverlayMessage(Component.translatable("message.universe.ship.blocked"));
                }
                return;
            }
        } else {
            this.parkingTicks = 0;
        }
        // 乘客（不是驾驶员）着陆后按 Shift 下船
        if (this.landed) {
            for (Entity passenger : List.copyOf(this.getPassengers())) {
                if (passenger != pilot && passenger.isShiftKeyDown()) {
                    passenger.stopRiding();
                }
            }
        }

        this.entityData.set(DATA_THROTTLE, (float) this.flight.throttle());
        this.entityData.set(DATA_FUEL, (float) this.fuel);
        this.entityData.set(DATA_IMPULSE, env.space() && controls.boost() && controls.forward() && stats.impulse() > 0 && this.fuel > 0);
        this.entityData.set(DATA_LANDED, this.landed);
        this.entityData.set(DATA_PARKING, this.parkingTicks);

        ShipTransit.check(this, level);
    }

    // ---- 存档和同步

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        if (this.structure != null) {
            this.structure.save(output);
        }
        output.putFloat("BaseYaw", this.baseYaw);
        output.putDouble("Fuel", this.fuel);
        output.putBoolean("Landed", this.landed);
        ValueOutput.ValueOutputList list = output.childrenList("Departures");
        this.departures.forEach((dimension, pos) -> {
            ValueOutput child = list.addChild();
            child.putString("Dimension", dimension.toString());
            child.putLong("Pos", pos);
        });
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        this.structure = ShipStructure.load(input);
        this.baseYaw = input.getFloatOr("BaseYaw", 0);
        this.fuel = input.getDoubleOr("Fuel", 0);
        this.landed = input.getBooleanOr("Landed", false);
        this.departures.clear();
        for (ValueInput child : input.childrenListOrEmpty("Departures")) {
            Identifier dimension = Identifier.tryParse(child.getStringOr("Dimension", ""));
            if (dimension != null) {
                this.departures.put(dimension, child.getLongOr("Pos", 0));
            }
        }
        Vec3 motion = this.getDeltaMovement();
        this.flight.restore(motion.x, motion.y, motion.z, this.getYRot());
        this.lastYaw = this.getYRot();
        this.entityData.set(DATA_FUEL, (float) this.fuel);
        this.refreshDimensions();
    }

    @Override
    public void writeSpawnData(RegistryFriendlyByteBuf buffer) {
        buffer.writeFloat(this.baseYaw);
        ShipStructure.write(this.structure, buffer);
    }

    @Override
    public void readSpawnData(RegistryFriendlyByteBuf buffer) {
        this.baseYaw = buffer.readFloat();
        this.structure = ShipStructure.read(buffer);
        this.lastYaw = this.getYRot();
        this.clientLastPos = this.position();
        this.refreshDimensions();
    }
}
