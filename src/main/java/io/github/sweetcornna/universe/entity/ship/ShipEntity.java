package io.github.sweetcornna.universe.entity.ship;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.block.ship.ShipHelmBlock;
import io.github.sweetcornna.universe.block.ship.ShipSeatBlock;
import io.github.sweetcornna.universe.core.ship.ShipCombat;
import io.github.sweetcornna.universe.core.ship.ShipFlight;
import io.github.sweetcornna.universe.core.ship.ShipFlight.Controls;
import io.github.sweetcornna.universe.core.ship.ShipFlight.Environment;
import io.github.sweetcornna.universe.core.ship.ShipShape;
import io.github.sweetcornna.universe.core.ship.ShipStats;
import io.github.sweetcornna.universe.core.space.Warp;
import io.github.sweetcornna.universe.pirate.Pirates;
import io.github.sweetcornna.universe.registry.UniverseEntities;
import io.github.sweetcornna.universe.station.Stations;
import io.github.sweetcornna.universe.world.space.SpaceDimensions;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
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
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
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
    private static final EntityDataAccessor<Float> DATA_HULL = SynchedEntityData.defineId(ShipEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_SHIELD = SynchedEntityData.defineId(ShipEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_HEAT = SynchedEntityData.defineId(ShipEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Byte> DATA_WEAPON = SynchedEntityData.defineId(ShipEntity.class, EntityDataSerializers.BYTE);
    /** {@link #DATA_WEAPON} 的位：采矿模式、过热、光束开着、驾驶员按着开火键。 */
    private static final int WEAPON_BEAM_MODE = 1;
    private static final int WEAPON_OVERHEATED = 2;
    private static final int WEAPON_BEAM_ON = 4;
    private static final int WEAPON_FIRING = 8;
    /** 实体事件：护盾挡住了、船体挨了打（客户端放粒子）。 */
    private static final byte EVENT_SHIELD_HIT = 100;
    private static final byte EVENT_HULL_HIT = 101;
    /** 被击毁后这么多刻内不再挨打，等拖船传送。 */
    private static final int WRECK_TICKS = 40;
    private static final DustParticleOptions SHIELD_DUST = new DustParticleOptions(0x7FDBFF, 1.1F);

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
    /** 跃迁的目标恒星系；没在跃迁时是 -1（ADR-0012）。 */
    private int warpTarget = -1;
    private int warpTicks;
    private @Nullable ShipCollider collider;
    private @Nullable Environment environment;
    private @Nullable Level environmentLevel;
    /** 在空间站里（按有重力、有空气算）。 */
    private boolean docked;
    /** 船体、护盾和武器的热量（ADR-0017）。 */
    private final ShipCombat combat = new ShipCombat(0, 0);
    /** 驾驶员按着开火键。 */
    private boolean firing;
    /** 采矿模式：开火键开采矿光束而不是光子炮。 */
    private boolean beamMode;
    /** 下一发轮到第几门炮。 */
    private int nextCannon;
    private int wreckTicks;
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
        // 组装出来的船船体和护盾都是满的：停船再组装就修好了
        ship.combat.set(structure.stats().hull(), structure.stats().shield());
        ship.syncCombat();
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
        builder.define(DATA_HULL, 0F);
        builder.define(DATA_SHIELD, 0F);
        builder.define(DATA_HEAT, 0F);
        builder.define(DATA_WEAPON, (byte) 0);
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

    /** 船体（客户端用同步来的值）。 */
    public float hull() {
        return this.entityData.get(DATA_HULL);
    }

    public float shield() {
        return this.entityData.get(DATA_SHIELD);
    }

    public double maxHull() {
        return this.structure == null ? 0 : this.structure.stats().hull();
    }

    public double maxShield() {
        return this.structure == null ? 0 : this.structure.stats().shield();
    }

    /** 武器的热量（0–100）。 */
    public float weaponHeat() {
        return this.entityData.get(DATA_HEAT);
    }

    public boolean beamMode() {
        return (this.entityData.get(DATA_WEAPON) & WEAPON_BEAM_MODE) != 0;
    }

    public boolean weaponOverheated() {
        return (this.entityData.get(DATA_WEAPON) & WEAPON_OVERHEATED) != 0;
    }

    public boolean beamActive() {
        return (this.entityData.get(DATA_WEAPON) & WEAPON_BEAM_ON) != 0;
    }

    /** 驾驶员按着开火键（客户端画准星用）。 */
    public boolean weaponFiring() {
        return (this.entityData.get(DATA_WEAPON) & WEAPON_FIRING) != 0;
    }

    /** 服务端：还差多少船体、多少护盾（维修台报价用）。 */
    public double missingHull() {
        return Math.max(0, this.maxHull() - this.combat.hull());
    }

    public double missingShield() {
        return Math.max(0, this.maxShield() - this.combat.shield());
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

    /** 算在飞船上的伤害：弹射物（光子弹、等离子弹、箭……）和爆炸；拳头、摔落、岩浆之类的不算。 */
    public static boolean isShipDamage(DamageSource source) {
        return source.is(DamageTypeTags.IS_PROJECTILE) || source.is(DamageTypeTags.IS_EXPLOSION);
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        Entity attacker = source.getEntity();
        // 自己船上的人打不坏自己的船；空间站里安全
        if (this.structure == null || this.docked || !isShipDamage(source) || attacker != null && attacker.getRootVehicle() == this) {
            return false;
        }
        return this.damageShip(level, damage);
    }

    /** 服务端：飞船挨一下打（先扣护盾再扣船体），船体打光就被击毁、拖回空间站。 */
    public boolean damageShip(ServerLevel level, float damage) {
        if (this.structure == null || this.wreckTicks > 0 || damage <= 0) {
            return false;
        }
        ShipCombat.Hit hit = this.combat.damage(damage);
        level.broadcastEntityEvent(this, hit == ShipCombat.Hit.SHIELD ? EVENT_SHIELD_HIT : EVENT_HULL_HIT);
        if (hit == ShipCombat.Hit.SHIELD) {
            this.playSound(SoundEvents.SHIELD_BLOCK.value(), 0.7F, 1.5F + this.random.nextFloat() * 0.2F);
        } else {
            this.playSound(SoundEvents.ZOMBIE_ATTACK_IRON_DOOR, 0.6F, 1.3F + this.random.nextFloat() * 0.2F);
        }
        this.syncCombat();
        if (hit == ShipCombat.Hit.DESTROYED) {
            ShipWreck.destroy(this, level);
        }
        return true;
    }

    /** 被击毁之后：船体剩两成五，护盾清空，停火，跃迁取消。 */
    void wrecked() {
        this.combat.set(this.maxHull() * ShipCombat.TOW_HULL, 0);
        this.wreckTicks = WRECK_TICKS;
        this.firing = false;
        this.warpTarget = -1;
        this.flight.restore(0, 0, 0, this.flight.yaw());
        this.syncCombat();
    }

    /** 维修台修好：船体、护盾都充满。 */
    public void repair() {
        this.combat.set(this.maxHull(), this.maxShield());
        this.syncCombat();
    }

    /** 货舱里的东西变了（被击毁丢了货）：换成新的方块数据，方块的位置和种类不变。 */
    void replaceStructure(ShipStructure structure) {
        this.structure = structure;
    }

    public void setFiring(boolean firing) {
        this.firing = firing;
    }

    /** 驾驶员按了武器模式键：在光子炮和采矿光束之间切换。 */
    public void toggleWeaponMode(Player pilot) {
        if (this.structure == null) {
            return;
        }
        ShipStats stats = this.structure.stats();
        if (!this.beamMode && stats.beams() == 0) {
            pilot.sendOverlayMessage(Component.translatable("message.universe.ship.no_beam"));
            return;
        }
        if (this.beamMode && stats.cannons() == 0) {
            pilot.sendOverlayMessage(Component.translatable("message.universe.ship.no_cannon"));
            return;
        }
        this.beamMode = !this.beamMode;
        pilot.sendOverlayMessage(Component.translatable(this.beamMode ? "message.universe.ship.mode_beam" : "message.universe.ship.mode_cannon"));
        this.syncCombat();
    }

    /** 轮到第几门炮开火（服务端）。 */
    int nextCannon(int cannons) {
        int index = Math.floorMod(this.nextCannon, cannons);
        this.nextCannon = index + 1;
        return index;
    }

    private void syncCombat() {
        this.entityData.set(DATA_HULL, (float) this.combat.hull());
        this.entityData.set(DATA_SHIELD, (float) this.combat.shield());
        this.entityData.set(DATA_HEAT, this.combat.heat());
        int flags = (this.beamMode ? WEAPON_BEAM_MODE : 0) | (this.combat.overheated() ? WEAPON_OVERHEATED : 0)
                | (this.firing ? WEAPON_FIRING : 0) | (this.entityData.get(DATA_WEAPON) & WEAPON_BEAM_ON);
        this.entityData.set(DATA_WEAPON, (byte) flags);
    }

    /** 每刻：回护盾、开火、照射。空间站里、跃迁充能时、刚被击毁时不开火。 */
    private void tickCombat(ServerLevel level, ShipStats stats, @Nullable Player pilot, boolean warping) {
        if (this.wreckTicks > 0) {
            this.wreckTicks--;
        }
        if (pilot == null) {
            this.firing = false;
        }
        if (this.beamMode && stats.beams() == 0) {
            this.beamMode = false;
        }
        boolean trigger = this.firing && pilot != null && !warping && !this.docked && this.wreckTicks == 0;
        int shots = this.combat.tick(stats.shield(), trigger, stats.cannons(), this.beamMode);
        if (shots > 0) {
            ShipWeapons.fireCannons(this, level, pilot, shots);
        } else if (shots < 0) {
            ShipWeapons.fireBeam(this, level, pilot);
        }
        this.syncCombat();
        byte weapon = this.entityData.get(DATA_WEAPON);
        this.entityData.set(DATA_WEAPON, (byte) (shots < 0 ? weapon | WEAPON_BEAM_ON : weapon & ~WEAPON_BEAM_ON));
    }

    @Override
    public void handleEntityEvent(byte id) {
        if (id == EVENT_SHIELD_HIT || id == EVENT_HULL_HIT) {
            this.hitEffect(id == EVENT_SHIELD_HIT);
        } else {
            super.handleEntityEvent(id);
        }
    }

    /** 客户端：护盾挡住时船外闪一圈淡蓝色的护盾泡；船体挨打时冒火花和烟。 */
    private void hitEffect(boolean shield) {
        if (this.structure == null) {
            return;
        }
        double r = this.structure.shape().radius() + 0.8;
        double h = this.structure.shape().height() / 2.0;
        if (shield) {
            for (int i = 0; i < 48; i++) {
                double theta = this.random.nextDouble() * Math.PI * 2;
                double cos = this.random.nextDouble() * 2 - 1;
                double sin = Math.sqrt(1 - cos * cos);
                this.level().addParticle(SHIELD_DUST, this.getX() + r * sin * Math.cos(theta), this.getY() + h + (h + 0.8) * cos,
                        this.getZ() + r * sin * Math.sin(theta), 0, 0, 0);
            }
            return;
        }
        for (int i = 0; i < 10; i++) {
            ShipStructure.ShipBlock block = this.structure.block(this.random.nextInt(this.structure.blocks().size()));
            Vec3 p = this.toWorld(block.x(), block.y() + 0.5, block.z(), this.getYRot() - this.baseYaw);
            this.level().addParticle(i % 3 == 0 ? ParticleTypes.LARGE_SMOKE : ParticleTypes.ELECTRIC_SPARK, p.x, p.y, p.z,
                    (this.random.nextDouble() - 0.5) * 0.3, this.random.nextDouble() * 0.2, (this.random.nextDouble() - 0.5) * 0.3);
        }
        this.level().addParticle(ParticleTypes.LAVA, this.getX(), this.getY() + h, this.getZ(), 0, 0, 0);
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
            if (this.beamActive()) {
                ShipWeapons.drawBeams(this);
            }
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

    public boolean isWarping() {
        return this.warpTarget >= 0;
    }

    /** 开始跃迁充能（服务端已经检查过射程、引力井和翘曲器）。 */
    public void startWarp(int system) {
        this.warpTarget = system;
        this.warpTicks = Warp.SPOOL_TICKS;
    }

    /** 跃迁充能：驾驶员还在、还在太空里就倒数，船慢慢停下；数到头了起跳。返回这一刻是不是还在充能。 */
    private boolean tickWarp(ServerLevel level) {
        if (this.warpTarget < 0) {
            return false;
        }
        if (this.pilot() == null || !SpaceDimensions.isSpace(level.dimension())) {
            this.warpTarget = -1;
            ShipWarp.cancel(this, Component.translatable("message.universe.warp.cancelled"));
            return false;
        }
        if (--this.warpTicks <= 0) {
            int target = this.warpTarget;
            this.warpTarget = -1;
            ShipWarp.jump(this, level, target);
            return false;
        }
        return true;
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
        boolean warping = this.tickWarp(level);
        ShipStats stats = this.structure.stats();
        boolean docked = Stations.inside(level, this.position());
        if (this.environmentLevel != level || docked != this.docked) {
            this.environment = ShipEnvironment.of(level);
            if (docked) {
                // 空间站里有重力和空气（ADR-0017）：能降落在停机坪上停船、下船
                this.environment = Environment.HOMEWORLD;
            }
            this.docked = docked;
            this.environmentLevel = level;
            this.collider = new ShipCollider(level);
            this.entityData.set(DATA_GRAVITY, (float) (this.environment.space() ? 0 : this.environment.gravity()));
        }
        Environment env = this.environment;
        ShipCollider collider = this.collider;
        collider.reset();

        Player pilot = this.pilot();
        Controls controls;
        if (warping) {
            // 充能时不听操纵
            controls = Controls.NONE;
        } else if (pilot instanceof ServerPlayer player) {
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
        if (controls.boost() && env.space() && stats.impulse() > 0 && Pirates.jammed(this)) {
            // 海盗干扰：脉冲引擎开不起来，只能用推进器周旋（ADR-0017）
            controls = new Controls(controls.forward(), controls.back(), controls.left(), controls.right(), controls.up(), controls.down(), false);
            if (pilot != null && this.tickCount % 40 == 0) {
                pilot.sendOverlayMessage(Component.translatable("message.universe.pirate.jammed"));
            }
        }
        boolean infinite = pilot != null && pilot.hasInfiniteMaterials();
        double used = this.flight.step(stats, controls, env, infinite || this.fuel > 0, this.landed);
        if (!infinite) {
            this.fuel = Math.max(0, this.fuel - used);
        }
        if (warping) {
            // 充能时把船稳住，免得一路滑进哪颗星球的大气
            this.flight.restore(this.flight.vx() * 0.9, this.flight.vy() * 0.9, this.flight.vz() * 0.9, this.flight.yaw());
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

        this.tickCombat(level, stats, pilot, warping);

        if (!warping) {
            ShipTransit.check(this, level);
        }
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
        output.putDouble("Hull", this.combat.hull());
        output.putDouble("Shield", this.combat.shield());
        output.putBoolean("BeamMode", this.beamMode);
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
        if (this.structure != null) {
            ShipStats stats = this.structure.stats();
            this.combat.set(Math.min(stats.hull(), input.getDoubleOr("Hull", stats.hull())),
                    Math.min(stats.shield(), input.getDoubleOr("Shield", stats.shield())));
            this.beamMode = input.getBooleanOr("BeamMode", false) && stats.beams() > 0;
            this.syncCombat();
        }
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
