package io.github.sweetcornna.universe.exocraft;

import java.util.UUID;

import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.block.ship.ShipFuel;
import io.github.sweetcornna.universe.core.vehicle.VehicleKind;
import io.github.sweetcornna.universe.core.ship.ShipFlight;
import io.github.sweetcornna.universe.entity.ship.ShipEnvironment;
import io.github.sweetcornna.universe.registry.UniverseDataComponents;
import io.github.sweetcornna.universe.registry.sound.ShipSounds;
import net.minecraft.core.NonNullList;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.InterpolationHandler;
import net.minecraft.world.entity.LinearInterpolationHandler;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.VehicleEntity;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * 载具的共同部分（ADR-0017、ADR-0026）：一个驾驶座，烧和飞船一样的燃料，带一个货舱。右键上车，潜行右键开货舱，拿燃料右键加油，
 * 拿镐敲三下变回物品（货物和燃料存在物品里）。怪物、爆炸、火都伤不了它。
 * <p>
 * 驾驶由驾驶员的客户端算（像原版的船），服务端收位置，按驾驶员这一刻按着的键扣燃料。
 * <p>
 * 载具记主人（放下它的人；老存档里的车第一次有人上车时记在那人名下），在 {@link VehicleRoster} 里登记位置，召唤台按它找车。
 */
public abstract class UniverseVehicle extends VehicleEntity {
    /** 油箱多大（和飞船燃料同一个单位：一块煤 200）。 */
    public static final double MAX_FUEL = 1000;
    /** 拿镐敲一下掉多少：过了 40 就收起（敲三下）。 */
    private static final float HIT_DAMAGE = 20;
    /** 每刻回多少：按镐的攻击冷却一下一下敲，三下就收起；两下之间隔了两秒以上，前面的就白敲了。 */
    private static final float DAMAGE_DECAY = 0.25F;
    /** 隔多少刻在名册上更新一次位置。 */
    private static final int ROSTER_INTERVAL = 100;

    private static final EntityDataAccessor<Float> DATA_FUEL = SynchedEntityData.defineId(UniverseVehicle.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_GRAVITY = SynchedEntityData.defineId(UniverseVehicle.class,
            EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DATA_CARGO = SynchedEntityData.defineId(UniverseVehicle.class, EntityDataSerializers.INT);

    private final SimpleContainer cargo;
    private double fuel;
    private @Nullable UUID owner;

    protected UniverseVehicle(EntityType<? extends UniverseVehicle> type, Level level, int cargoSlots) {
        super(type, level);
        this.blocksBuilding = true;
        this.cargo = new SimpleContainer(cargoSlots) {
            @Override
            public boolean stillValid(Player player) {
                return !UniverseVehicle.this.isRemoved() && player.distanceToSqr(UniverseVehicle.this) < 64;
            }

            @Override
            public void setChanged() {
                super.setChanged();
                // 货舱的东西只在服务端，客户端的仪表盘靠同步的格数
                if (!UniverseVehicle.this.level().isClientSide()) {
                    UniverseVehicle.this.entityData.set(DATA_CARGO, UniverseVehicle.this.countUsed());
                }
            }
        };
    }

    // ---- 同步的数据

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_FUEL, 0.0F);
        builder.define(DATA_GRAVITY, 1.0F);
        builder.define(DATA_CARGO, 0);
    }

    /** 燃料（两端都能读）。 */
    public float fuel() {
        return this.entityData.get(DATA_FUEL);
    }

    /** 星球的表面重力，太空里是 1（两端都能读）。 */
    protected float gravityScale() {
        return this.entityData.get(DATA_GRAVITY);
    }

    public SimpleContainer cargo() {
        return this.cargo;
    }

    public int cargoSlots() {
        return this.cargo.getContainerSize();
    }

    /** 货舱里用了几格（两端都能读）。 */
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

    /** 放下这辆车的人；老存档里没人上过的车是 null。 */
    public @Nullable UUID owner() {
        return this.owner;
    }

    public void setOwner(@Nullable UUID owner) {
        this.owner = owner;
    }

    /** 这是哪种载具（召唤台的列表、名册用）。 */
    public abstract VehicleKind kind();

    /** 服务端的燃料。 */
    protected double fuelLeft() {
        return this.fuel;
    }

    void setFuel(double fuel) {
        this.fuel = Mth.clamp(fuel, 0, MAX_FUEL);
        this.entityData.set(DATA_FUEL, (float) this.fuel);
    }

    /** 有燃料或者驾驶员是创造模式：推进器推得动。 */
    protected boolean powered(@Nullable Player driver) {
        return driver != null && (this.fuel() > 0 || driver.hasInfiniteMaterials());
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
        if (this.level() instanceof ServerLevel level && this.tickCount % 20 == 1) {
            ShipFlight.Environment env = ShipEnvironment.of(level);
            this.entityData.set(DATA_GRAVITY, (float) (env.space() ? 1 : env.gravity()));
        }
        if (this.level() instanceof ServerLevel level && this.owner != null && this.tickCount % ROSTER_INTERVAL == 2) {
            VehicleRoster.get(level.getServer()).update(this.owner, this);
        }
    }

    @Override
    public void remove(RemovalReason reason) {
        // 收起来、被毁了：从名册上划掉。区块卸载、换维度不算（召唤台搬车时先拆再放，新的那辆会重新登记）
        if (reason.shouldDestroy() && this.owner != null && this.level() instanceof ServerLevel level) {
            VehicleRoster.get(level.getServer()).remove(this.owner, this.getUUID());
        }
        super.remove(reason);
    }

    /** 服务端：驾驶员这一刻踩着油门就扣这么多燃料。 */
    protected void burnDriveFuel(@Nullable ServerPlayer driver, double perTick) {
        if (driver == null || driver.hasInfiniteMaterials()) {
            return;
        }
        Input input = driver.getLastClientInput();
        if ((input.forward() || input.backward()) && this.fuel > 0) {
            this.setFuel(this.fuel - perTick);
        }
    }

    /** 服务端：武器、激光这些另外烧的燃料（创造模式不扣）。 */
    protected void burnFuel(@Nullable ServerPlayer driver, double amount) {
        if (driver != null && !driver.hasInfiniteMaterials()) {
            this.setFuel(this.fuel - amount);
        }
    }

    // ---- 驾驶员的操作

    /** 驾驶员按着攻击键没有、用的是不是背后的第三人称（驾驶员的客户端发来的）。 */
    void setControls(boolean firing, boolean thirdPerson) {
    }

    /** 坐在车上用多功能工具右键：车载扫描。 */
    abstract void scan(ServerPlayer player);

    // ---- 交互

    @Override
    public InteractionResult interact(Player player, InteractionHand hand, Vec3 location) {
        if (player.isSecondaryUseActive()) {
            if (this.level() instanceof ServerLevel) {
                this.openCargo(player);
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
            if (this.level() instanceof ServerLevel level) {
                if (this.owner == null) {
                    // 老存档里的车：第一个上车的人当主人
                    this.owner = player.getUUID();
                    VehicleRoster.get(level.getServer()).update(this.owner, this);
                }
                player.startRiding(this);
            }
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    /** 服务端：打开货舱。 */
    protected void openCargo(Player player) {
        int rows = Math.max(1, Math.min(6, this.cargo.getContainerSize() / 9));
        MenuType<?> type = switch (rows) {
            case 1 -> MenuType.GENERIC_9x1;
            case 2 -> MenuType.GENERIC_9x2;
            case 4 -> MenuType.GENERIC_9x4;
            case 5 -> MenuType.GENERIC_9x5;
            case 6 -> MenuType.GENERIC_9x6;
            default -> MenuType.GENERIC_9x3;
        };
        player.openMenu(new SimpleMenuProvider((id, inventory, p) -> new ChestMenu(type, id, inventory, this.cargo, rows),
                this.getDisplayName()));
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

    // ---- 收起来

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
        this.playSound(ShipSounds.HULL_HIT.get(), 0.5F, 1.5F);
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
        this.saveToItem(stack);
        return stack;
    }

    /** 子类另外存进物品的东西（重型车的精炼机）。 */
    protected void saveToItem(ItemStack stack) {
    }

    /** 子类从物品里取回 {@link #saveToItem} 存的东西。 */
    protected void loadFromItem(ItemStack stack) {
    }

    /** 从物品放下时把物品里存的燃料和货物搬进来。 */
    void loadFrom(ItemStack stack) {
        this.setFuel(stack.getOrDefault(UniverseDataComponents.EXOCRAFT_FUEL.get(), 0));
        ItemContainerContents contents = stack.get(DataComponents.CONTAINER);
        if (contents != null) {
            int slots = this.cargo.getContainerSize();
            NonNullList<ItemStack> items = NonNullList.withSize(slots, ItemStack.EMPTY);
            contents.copyInto(items);
            for (int i = 0; i < slots; i++) {
                this.cargo.setItem(i, items.get(i));
            }
        }
        this.setCustomName(stack.get(DataComponents.CUSTOM_NAME));
        this.loadFromItem(stack);
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

    // ---- 物理

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
        output.storeNullable("owner", UUIDUtil.CODEC, this.owner);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        this.setFuel(input.getDoubleOr("fuel", 0));
        ContainerHelper.loadAllItems(input, this.cargo.getItems());
        this.entityData.set(DATA_CARGO, this.countUsed());
        this.owner = input.read("owner", UUIDUtil.CODEC).orElse(null);
    }
}
