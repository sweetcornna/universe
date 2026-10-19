package io.github.sweetcornna.universe.exocraft;

import java.util.List;

import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.block.ship.ShipFuel;
import io.github.sweetcornna.universe.core.sentinel.Offense;
import io.github.sweetcornna.universe.core.ship.ShipFlight;
import io.github.sweetcornna.universe.core.survival.LaserHeat;
import io.github.sweetcornna.universe.core.vehicle.RoverDrive;
import io.github.sweetcornna.universe.entity.ship.ShipEnvironment;
import io.github.sweetcornna.universe.network.ScanResultPayload;
import io.github.sweetcornna.universe.registry.UniverseDataComponents;
import io.github.sweetcornna.universe.registry.UniverseItems;
import io.github.sweetcornna.universe.sentinel.Sentinels;
import io.github.sweetcornna.universe.survival.Multitool;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.InterpolationHandler;
import net.minecraft.world.entity.LinearInterpolationHandler;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.DismountHelper;
import net.minecraft.world.entity.vehicle.VehicleEntity;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 探索车（ADR-0017）：四轮车，一个座位，27 格货箱，烧和飞船一样的燃料。
 * <p>
 * 驾驶像原版的船一样由驾驶员的客户端算（地形上开起来才顺），服务端收车的位置，按驾驶员这一刻按着的键扣燃料。车载采矿激光按住攻击键开，
 * 打 {@link #LASER_RANGE} 格内准星对着的方块，挖下的东西进货箱；坐在车上用多功能工具右键是车载扫描（矿脉 {@link #SCAN_RADIUS} 格、
 * 兴趣点 {@link #POI_RADIUS} 格）。右键上车，潜行右键开货箱，拿燃料右键加油，拿镐敲三下变回物品（货物和燃料存在物品里）。
 */
public class Exocraft extends VehicleEntity {
    /** 油箱多大（和飞船燃料同一个单位：一块煤 200）。 */
    public static final double MAX_FUEL = 1000;
    /** 踩着油门每刻烧多少（和一台飞船推进器全开一样）。 */
    public static final double FUEL_PER_TICK = 0.05;
    /** 开着激光每刻烧多少。 */
    public static final double LASER_FUEL_PER_TICK = 0.03;
    /** 激光打多远（格）。 */
    public static final double LASER_RANGE = 12;
    /** 激光的挖掘力：钻石镐的速度 8 乘上 2.25，比多功能工具（铁镐 6 × 1.5）快一倍。 */
    public static final float LASER_POWER = 18;
    /** 激光升温的倍数：比多功能工具慢一半，连续开 8 秒过热。 */
    public static final float LASER_HEAT = 0.5F;
    public static final int SCAN_RADIUS = 64;
    public static final int POI_RADIUS = 400;
    public static final int SCAN_COOLDOWN = 100;
    public static final int CARGO_SLOTS = 27;
    /** 第三人称开车时镜头抬高多少格：车和驾驶员落在准星下面，挡不住激光要打的地方（和无人深空的探索车视角一样）。 */
    public static final float CAMERA_LIFT = 1.0F;
    /** 第三人称开车时镜头至少拉开多远：整辆车都在画面里。 */
    public static final float CAMERA_DISTANCE = 6.0F;
    /** 每刻的重力（乘上星球的表面重力）。 */
    private static final double GRAVITY = 0.08;
    /** 悬挂能吃掉多高的摔落（格）。 */
    private static final double SUSPENSION = 6;
    /** 拿镐敲一下掉多少：过了 40 就收起（敲三下）。 */
    private static final float HIT_DAMAGE = 20;
    /** 每刻回多少：按镐的攻击冷却一下一下敲，三下就收起；两下之间隔了两秒以上，前面的就白敲了。 */
    private static final float DAMAGE_DECAY = 0.25F;
    private static final int BEAM_COLOR = 0x5AD2FF;
    /** 激光按方块的挖掘规则算掉落，掉落和钻石镐挖的一样。 */
    private static final ItemStack LASER_TOOL = new ItemStack(Items.DIAMOND_PICKAXE);

    private static final EntityDataAccessor<Float> DATA_FUEL = SynchedEntityData.defineId(Exocraft.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_GRAVITY = SynchedEntityData.defineId(Exocraft.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_HEAT = SynchedEntityData.defineId(Exocraft.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> DATA_LASER = SynchedEntityData.defineId(Exocraft.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_OVERHEATED = SynchedEntityData.defineId(Exocraft.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> DATA_CARGO = SynchedEntityData.defineId(Exocraft.class, EntityDataSerializers.INT);

    private final SimpleContainer cargo = new SimpleContainer(CARGO_SLOTS) {
        @Override
        public boolean stillValid(Player player) {
            return !Exocraft.this.isRemoved() && player.distanceToSqr(Exocraft.this) < 64;
        }

        @Override
        public void setChanged() {
            super.setChanged();
            // 货箱的东西只在服务端，客户端的仪表盘靠同步的格数
            if (!Exocraft.this.level().isClientSide()) {
                Exocraft.this.entityData.set(DATA_CARGO, Exocraft.this.countUsed());
            }
        }
    };
    private double fuel;
    /** 沿车头方向的速度（格/刻）：在算驾驶的那一端（驾驶员的客户端，没人开时的服务端）才有意义。 */
    private double speed;
    /** 这一刻转了多少度，乘客跟着转。 */
    private float deltaRotation;

    // 服务端：激光
    private boolean firing;
    /** 驾驶员用的是背后的第三人称：瞄准线跟着镜头抬高。 */
    private boolean thirdPersonAim;
    private final LaserHeat heat = new LaserHeat();
    private @Nullable BlockPos target;
    private float progress;
    private int scanCooldown;

    // 客户端：车轮转过的角度（弧度）
    public float wheelSpin;
    public float wheelSpinO;

    public Exocraft(EntityType<? extends Exocraft> type, Level level) {
        super(type, level);
        this.blocksBuilding = true;
    }

    // ---- 同步的数据

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_FUEL, 0.0F);
        builder.define(DATA_GRAVITY, 1.0F);
        builder.define(DATA_HEAT, 0.0F);
        builder.define(DATA_LASER, false);
        builder.define(DATA_OVERHEATED, false);
        builder.define(DATA_CARGO, 0);
    }

    /** 燃料（两端都能读）。 */
    public float fuel() {
        return this.entityData.get(DATA_FUEL);
    }

    /** 激光的热量，0 到 {@link LaserHeat#MAX}（两端都能读）。 */
    public float heat() {
        return this.entityData.get(DATA_HEAT);
    }

    /** 激光正开着（两端都能读）。 */
    public boolean laserOn() {
        return this.entityData.get(DATA_LASER);
    }

    /** 激光过热了，正在强制冷却（两端都能读）。 */
    public boolean overheated() {
        return this.entityData.get(DATA_OVERHEATED);
    }

    public SimpleContainer cargo() {
        return this.cargo;
    }

    /** 货箱里用了几格（两端都能读）。 */
    public int usedSlots() {
        return this.entityData.get(DATA_CARGO);
    }

    private int countUsed() {
        int used = 0;
        for (int i = 0; i < this.cargo.getContainerSize(); i++) {
            if (!this.cargo.getItem(i).isEmpty()) {
                used++;
            }
        }
        return used;
    }

    void setFuel(double fuel) {
        this.fuel = Mth.clamp(fuel, 0, MAX_FUEL);
        this.entityData.set(DATA_FUEL, (float) this.fuel);
    }

    // ---- 每刻

    @Override
    public void tick() {
        if (this.getHurtTime() > 0) {
            this.setHurtTime(this.getHurtTime() - 1);
        }
        if (this.getDamage() > 0) {
            this.setDamage(Math.max(0, this.getDamage() - DAMAGE_DECAY));
        }
        super.tick();
        if (this.isLocalInstanceAuthoritative()) {
            this.drive();
            Vec3 wanted = this.getDeltaMovement();
            this.move(MoverType.SELF, wanted);
            if (this.horizontalCollision) {
                // 撞上爬不过去的墙：速度只剩真的走出去的那点
                Vec3 heading = heading(this.getYRot());
                double moved = (this.getX() - this.xo) * heading.x + (this.getZ() - this.zo) * heading.z;
                if (Math.abs(moved) < Math.abs(this.speed)) {
                    this.speed = moved;
                }
            }
        } else {
            this.speed = 0;
            this.deltaRotation = 0;
        }
        if (this.level().isClientSide()) {
            Vec3 heading = heading(this.getYRot());
            double moved = (this.getX() - this.xo) * heading.x + (this.getZ() - this.zo) * heading.z;
            this.wheelSpinO = this.wheelSpin;
            this.wheelSpin += (float) (moved / 0.45);
        }
        if (this.level() instanceof ServerLevel level) {
            this.serverTick(level);
        }
    }

    private static Vec3 heading(float yaw) {
        float r = yaw * Mth.DEG_TO_RAD;
        return new Vec3(-Mth.sin(r), 0, Mth.cos(r));
    }

    /** 算驾驶的那一端：按驾驶员的方向键转向、加减速，加上重力和水的阻力。 */
    private void drive() {
        Player driver = this.getControllingPassenger() instanceof Player player ? player : null;
        boolean grounded = this.onGround();
        boolean water = this.isInWater();
        double throttle = driver == null ? 0 : driver.zza;
        // 玩家的 xxa 向左为正，方向盘向右为正
        double steer = driver == null ? 0 : -driver.xxa;
        boolean powered = driver != null && (this.fuel() > 0 || driver.hasInfiniteMaterials());
        this.deltaRotation = RoverDrive.turn(this.speed, steer, grounded || water);
        this.setYRot(this.getYRot() + this.deltaRotation);
        this.speed = RoverDrive.speed(this.speed, throttle, powered, grounded || water);
        if (water) {
            this.speed *= 0.92;
        }
        Vec3 heading = heading(this.getYRot());
        double g = GRAVITY * this.entityData.get(DATA_GRAVITY);
        double vy = this.getDeltaMovement().y;
        vy = water ? Math.max(vy * 0.8 - g * 0.15, -0.12) : (vy - g) * 0.98;
        this.setDeltaMovement(heading.x * this.speed, vy, heading.z * this.speed);
    }

    private void serverTick(ServerLevel level) {
        if (this.tickCount % 20 == 1) {
            ShipFlight.Environment env = ShipEnvironment.of(level);
            this.entityData.set(DATA_GRAVITY, (float) (env.space() ? 1 : env.gravity()));
        }
        ServerPlayer driver = this.getControllingPassenger() instanceof ServerPlayer player ? player : null;
        boolean infinite = driver != null && driver.hasInfiniteMaterials();
        if (driver != null && !infinite) {
            Input input = driver.getLastClientInput();
            if ((input.forward() || input.backward()) && this.fuel > 0) {
                this.setFuel(this.fuel - FUEL_PER_TICK);
            }
        }
        if (this.scanCooldown > 0) {
            this.scanCooldown--;
        }
        boolean trigger = this.firing && driver != null && (infinite || this.fuel > 0);
        boolean on = this.heat.tick(trigger, LASER_HEAT);
        this.entityData.set(DATA_HEAT, this.heat.heat());
        this.entityData.set(DATA_LASER, on);
        this.entityData.set(DATA_OVERHEATED, this.heat.overheated());
        if (on) {
            if (!infinite) {
                this.setFuel(this.fuel - LASER_FUEL_PER_TICK);
            }
            this.fireLaser(level, driver);
        } else {
            this.clearTarget(level);
        }
        if (driver == null) {
            this.firing = false;
            this.thirdPersonAim = false;
        }
    }

    // ---- 车载采矿激光

    void setControls(boolean firing, boolean thirdPerson) {
        this.firing = firing;
        this.thirdPersonAim = thirdPerson;
    }

    /**
     * 激光瞄准线的起点：第一人称就是眼睛；第三人称从眼睛沿镜头的上方抬高 {@link #CAMERA_LIFT}，和抬高、拉远的镜头在同一条视线上，
     * 准星对着哪儿就打哪儿。头顶有方块时只抬到方块下面。镜头（{@code CameraMixin}）、仪表盘和服务端的激光都用它，三边对得上。
     */
    public static Vec3 aimOrigin(Level level, Entity viewer, Vec3 eye, Vec3 up, boolean thirdPerson) {
        if (!thirdPerson) {
            return eye;
        }
        Vec3 top = eye.add(up.scale(CAMERA_LIFT));
        BlockHitResult hit = level.clip(new ClipContext(eye, top, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, viewer));
        if (hit.getType() == HitResult.Type.MISS) {
            return top;
        }
        return eye.add(up.scale(Math.max(0, hit.getLocation().distanceTo(eye) - 0.2)));
    }

    /** 激光打到哪儿：从瞄准线的起点沿视线打 {@link #LASER_RANGE} 格。 */
    public static BlockHitResult aim(Level level, Entity viewer, Vec3 eye, Vec3 look, Vec3 up, boolean thirdPerson) {
        Vec3 origin = aimOrigin(level, viewer, eye, up, thirdPerson);
        return level.clip(new ClipContext(origin, origin.add(look.scale(LASER_RANGE)), ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE,
                viewer));
    }

    /** 激光从发动机罩上炮管的口打出来（和模型对齐）。 */
    public Vec3 muzzle() {
        Vec3 heading = heading(this.getYRot());
        return this.position().add(heading.scale(1.25)).add(0, 1.22, 0);
    }

    private void fireLaser(ServerLevel level, ServerPlayer driver) {
        BlockHitResult hit = aim(level, driver, driver.getEyePosition(), driver.getLookAngle(), driver.getUpVector(1.0F), this.thirdPersonAim);
        Vec3 to = hit.getLocation();
        beam(level, this.muzzle(), to);
        if (this.tickCount % 10 == 0) {
            level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.BEACON_AMBIENT, SoundSource.NEUTRAL, 0.6F, 1.6F);
        }
        if (hit.getType() != HitResult.Type.BLOCK) {
            this.clearTarget(level);
            return;
        }
        BlockPos pos = hit.getBlockPos();
        if (!pos.equals(this.target)) {
            this.clearTarget(level);
            this.target = pos.immutable();
        }
        BlockState block = level.getBlockState(pos);
        float hardness = block.getDestroySpeed(level, pos);
        boolean harvestable = !block.requiresCorrectToolForDrops() || LASER_TOOL.isCorrectToolForDrops(block);
        if (hardness < 0 || !harvestable || !level.mayInteract(driver, pos)) {
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, to.x, to.y, to.z, 2, 0.05, 0.05, 0.05, 0.02);
            return;
        }
        this.progress += hardness == 0 ? 1 : LASER_POWER / hardness / 30F;
        level.sendParticles(ParticleTypes.SMOKE, to.x, to.y, to.z, 1, 0.05, 0.05, 0.05, 0.01);
        if (this.progress < 1) {
            level.destroyBlockProgress(this.crackId(), pos, Mth.clamp((int) (this.progress * 10), 0, 9));
            return;
        }
        this.clearTarget(level);
        if (CommonHooks.fireBlockBreak(level, driver.gameMode.getGameModeForPlayer(), driver, pos, block).isCanceled()) {
            return;
        }
        BlockEntity entity = level.getBlockEntity(pos);
        List<ItemStack> drops = Block.getDrops(block, level, pos, entity, driver, LASER_TOOL);
        if (!level.destroyBlock(pos, false, driver)) {
            return;
        }
        block.spawnAfterBreak(level, pos, LASER_TOOL, true);
        driver.awardStat(Stats.BLOCK_MINED.get(block.getBlock()));
        boolean full = false;
        for (ItemStack drop : drops) {
            ItemStack left = this.cargo.addItem(drop);
            if (!left.isEmpty()) {
                // 货箱满了：掉在车旁边
                full = true;
                this.spawnAtLocation(level, left);
            }
        }
        if (full && this.tickCount % 40 < 3) {
            driver.sendOverlayMessage(Component.translatable("message.universe.exocraft.cargo_full"));
        }
        // 守卫机盯着用激光大量采矿的人（ADR-0014），车载的也一样
        Sentinels.offend(driver, block.is(Tags.BlockItems.ORES.block()) ? Offense.LASER_MINING_ORE : Offense.LASER_MINING);
    }

    private static void beam(ServerLevel level, Vec3 from, Vec3 to) {
        DustParticleOptions dust = new DustParticleOptions(BEAM_COLOR, 0.7F);
        double length = from.distanceTo(to);
        int steps = Math.max(1, (int) (length / 0.35));
        for (int i = 0; i <= steps; i++) {
            Vec3 p = from.lerp(to, i / (double) steps);
            level.sendParticles(dust, p.x, p.y, p.z, 1, 0, 0, 0, 0);
        }
    }

    private void clearTarget(ServerLevel level) {
        if (this.target != null) {
            level.destroyBlockProgress(this.crackId(), this.target, -1);
            this.target = null;
        }
        this.progress = 0;
    }

    /** 裂纹的编号：和玩家自己挖的、多功能工具的裂纹分开。 */
    private int crackId() {
        return -1_000_000 - this.getId();
    }

    // ---- 车载扫描

    void scan(ServerPlayer player) {
        if (this.scanCooldown > 0) {
            player.sendOverlayMessage(Component.translatable("message.universe.multitool.charging"));
            return;
        }
        this.scanCooldown = SCAN_COOLDOWN;
        ServerLevel level = player.level();
        List<ScanResultPayload.Marker> markers = Multitool.scanOres(level, this.blockPosition(), SCAN_RADIUS);
        List<ScanResultPayload.PoiMarker> pois = Multitool.scanPois(player, POI_RADIUS);
        PacketDistributor.sendToPlayer(player, new ScanResultPayload(markers, pois));
        level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.NEUTRAL, 1.0F, 1.4F);
        for (int i = 0; i < 72; i++) {
            double angle = i * Math.PI * 2 / 72;
            level.sendParticles(ParticleTypes.END_ROD, this.getX() + Math.cos(angle) * 3, this.getY() + 0.3, this.getZ() + Math.sin(angle) * 3,
                    1, 0, 0, 0, 0);
        }
        int total = markers.stream().mapToInt(ScanResultPayload.Marker::count).sum();
        player.sendOverlayMessage(pois.isEmpty()
                ? Component.translatable("message.universe.multitool.scanned", markers.size(), total)
                : Component.translatable("message.universe.multitool.scanned_poi", markers.size(), total, pois.size()));
    }

    // ---- 交互

    @Override
    public InteractionResult interact(Player player, InteractionHand hand, Vec3 location) {
        if (player.isSecondaryUseActive()) {
            if (this.level() instanceof ServerLevel) {
                player.openMenu(new SimpleMenuProvider((id, inventory, p) -> ChestMenu.threeRows(id, inventory, this.cargo), this.getDisplayName()));
            }
            return InteractionResult.SUCCESS;
        }
        ItemStack held = player.getItemInHand(hand);
        if (ShipFuel.value(held) > 0) {
            if (this.level() instanceof ServerLevel) {
                this.refuel(player, hand);
            }
            return InteractionResult.SUCCESS;
        }
        if (!this.isVehicle()) {
            if (this.level() instanceof ServerLevel) {
                player.startRiding(this);
            }
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    /** 服务端：用手里的燃料加油，油箱放不下一整份就不加。 */
    void refuel(Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        double value = ShipFuel.value(held);
        if (this.fuel + value > MAX_FUEL + 1e-6) {
            player.sendOverlayMessage(Component.translatable("message.universe.exocraft.tank_full", (int) this.fuel, (int) MAX_FUEL));
            return;
        }
        this.setFuel(this.fuel + value);
        if (!player.hasInfiniteMaterials()) {
            ShipFuel.consume(held, player, hand);
        }
        this.playSound(SoundEvents.BUCKET_EMPTY_LAVA, 0.6F, 1.2F);
        player.sendOverlayMessage(Component.translatable("message.universe.exocraft.fuel", (int) this.fuel, (int) MAX_FUEL));
    }

    // ---- 拆车

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        if (this.isRemoved() || !(source.getEntity() instanceof Player player) || source.getDirectEntity() != player) {
            // 怪物、爆炸、火都伤不了它
            return false;
        }
        boolean creative = player.getAbilities().instabuild;
        if (!creative && !player.getMainHandItem().is(ItemTags.PICKAXES)) {
            player.sendOverlayMessage(Component.translatable("message.universe.exocraft.need_pickaxe"));
            return false;
        }
        this.setHurtDir(-this.getHurtDir());
        this.setHurtTime(10);
        this.markHurt();
        this.setDamage(this.getDamage() + HIT_DAMAGE);
        this.playSound(SoundEvents.ANVIL_PLACE, 0.4F, 1.4F);
        if (creative || this.getDamage() > 40) {
            this.destroy(level, source);
        }
        return true;
    }

    @Override
    protected void destroy(ServerLevel level, DamageSource source) {
        // 货物和燃料存进物品里，再放下还是原样；拿镐收车的人直接收进背包，背包满了才掉在地上
        this.ejectPassengers();
        ItemStack item = this.toItem();
        if (source.getEntity() instanceof Player player) {
            player.getInventory().add(item);
        }
        if (!item.isEmpty()) {
            this.spawnAtLocation(level, item);
        }
        this.cargo.clearContent();
        this.discard();
    }

    ItemStack toItem() {
        ItemStack stack = new ItemStack(this.getDropItem());
        stack.set(DataComponents.CUSTOM_NAME, this.getCustomName());
        if (this.fuel > 0) {
            stack.set(UniverseDataComponents.EXOCRAFT_FUEL.get(), (int) Math.round(this.fuel));
        }
        if (!this.cargo.isEmpty()) {
            stack.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(this.cargo.getItems()));
        }
        return stack;
    }

    /** 从物品放下时把物品里存的燃料和货物搬进车里。 */
    void loadFrom(ItemStack stack) {
        this.setFuel(stack.getOrDefault(UniverseDataComponents.EXOCRAFT_FUEL.get(), 0));
        ItemContainerContents contents = stack.get(DataComponents.CONTAINER);
        if (contents != null) {
            NonNullList<ItemStack> items = NonNullList.withSize(CARGO_SLOTS, ItemStack.EMPTY);
            contents.copyInto(items);
            for (int i = 0; i < CARGO_SLOTS; i++) {
                this.cargo.setItem(i, items.get(i));
            }
        }
        this.setCustomName(stack.get(DataComponents.CUSTOM_NAME));
    }

    @Override
    protected Item getDropItem() {
        return UniverseItems.EXOCRAFT.get();
    }

    @Override
    public ItemStack getPickResult() {
        return new ItemStack(this.getDropItem());
    }

    // ---- 乘坐

    @Override
    public @Nullable LivingEntity getControllingPassenger() {
        return this.getFirstPassenger() instanceof Player player ? player : null;
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return this.getPassengers().isEmpty() && passenger instanceof Player;
    }

    @Override
    protected Vec3 getPassengerAttachmentPoint(Entity passenger, EntityDimensions dimensions, float scale) {
        // 座椅面离地 1.2 格、在车中间往后一点（和模型对齐）
        return new Vec3(0, 1.2, -0.125).yRot(-this.getYRot() * Mth.DEG_TO_RAD);
    }

    @Override
    protected void positionRider(Entity passenger, Entity.MoveFunction moveFunction) {
        super.positionRider(passenger, moveFunction);
        if (passenger.isLocalInstanceAuthoritative()) {
            passenger.setYRot(passenger.getYRot() + this.deltaRotation);
            passenger.setYHeadRot(passenger.getYHeadRot() + this.deltaRotation);
        }
    }

    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        // 先试驾驶员左手边（车门那一侧），再试右边、后面、前面
        for (float side : new float[] {-90, 90, 180, 0}) {
            Vec3 direction = getCollisionHorizontalEscapeVector(this.getBbWidth() * Mth.SQRT_OF_TWO, passenger.getBbWidth(),
                    this.getYRot() + side);
            double x = this.getX() + direction.x;
            double z = this.getZ() + direction.z;
            BlockPos pos = BlockPos.containing(x, this.getBoundingBox().maxY, z);
            for (BlockPos at : new BlockPos[] {pos, pos.below(), pos.below(2)}) {
                double floor = this.level().getBlockFloorHeight(at);
                if (!DismountHelper.isBlockFloorValid(floor)) {
                    continue;
                }
                Vec3 target = new Vec3(x, at.getY() + floor, z);
                for (Pose pose : passenger.getDismountPoses()) {
                    if (DismountHelper.canDismountTo(this.level(), target, passenger, pose)) {
                        passenger.setPose(pose);
                        return target;
                    }
                }
            }
        }
        return super.getDismountLocationForPassenger(passenger);
    }

    @Override
    protected void removePassenger(Entity passenger) {
        super.removePassenger(passenger);
        this.firing = false;
    }

    // ---- 物理

    @Override
    public float maxUpStep() {
        return 1.05F;
    }

    @Override
    public boolean causeFallDamage(double fallDistance, float damageModifier, DamageSource damageSource) {
        // 悬挂吃掉一截，车本身不坏
        this.propagateFallToPassengers(Math.max(0, fallDistance - SUSPENSION), damageModifier, damageSource);
        return false;
    }

    @Override
    public boolean canBeCollidedWith(@Nullable Entity other) {
        return true;
    }

    @Override
    public boolean isPickable() {
        return !this.isRemoved();
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected InterpolationHandler createInterpolationHandler() {
        return LinearInterpolationHandler.create(this, 3);
    }

    // ---- 存档

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        output.putDouble("fuel", this.fuel);
        ContainerHelper.saveAllItems(output, this.cargo.getItems());
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        this.setFuel(input.getDoubleOr("fuel", 0));
        ContainerHelper.loadAllItems(input, this.cargo.getItems());
        this.entityData.set(DATA_CARGO, this.countUsed());
    }
}
