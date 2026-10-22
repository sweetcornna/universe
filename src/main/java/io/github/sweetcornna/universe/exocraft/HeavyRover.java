package io.github.sweetcornna.universe.exocraft;

import io.github.sweetcornna.universe.core.vehicle.VehicleKind;
import io.github.sweetcornna.universe.core.vehicle.WheelDrive;
import io.github.sweetcornna.universe.factory.MachineMenu;
import io.github.sweetcornna.universe.factory.RecipeMachines;
import io.github.sweetcornna.universe.registry.UniverseDataComponents;
import io.github.sweetcornna.universe.registry.UniverseItems;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * 重型车（ADR-0026）：照无人深空的巨像号做的八轮运输车，慢（探索车的 0.7 倍），货舱 54 格，爬得上一格半的坎。
 * 车尾背着一台便携精炼机：潜行右键车尾打开，车走着也在炼，燃料和原料自己放（{@link VehicleRefiner}）。
 * 潜行右键车身其他地方还是开货舱。采矿激光、车载扫描和探索车一样。
 */
public class HeavyRover extends Exocraft {
    /** 踩着油门每刻烧多少：车重，比探索车费油。 */
    public static final double FUEL_PER_TICK = 0.07;
    /** 车头往后多远算车尾（精炼机那一截），格。 */
    private static final double REFINER_FROM = -0.7;
    private static final EntityDataAccessor<Boolean> DATA_REFINING = SynchedEntityData.defineId(HeavyRover.class,
            EntityDataSerializers.BOOLEAN);

    private final VehicleRefiner refiner = new VehicleRefiner(this);

    public HeavyRover(EntityType<? extends HeavyRover> type, Level level) {
        super(type, level, VehicleKind.HEAVY_ROVER);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_REFINING, false);
    }

    /** 车尾的精炼机正在炼（两端都能读，模型上的观察窗亮）。 */
    public boolean refining() {
        return this.entityData.get(DATA_REFINING);
    }

    public VehicleRefiner refiner() {
        return this.refiner;
    }

    @Override
    protected WheelDrive wheels() {
        return WheelDrive.HEAVY;
    }

    @Override
    protected double fuelPerTick() {
        return FUEL_PER_TICK;
    }

    @Override
    public float cameraDistance() {
        return 8.0F;
    }

    @Override
    protected Vec3 muzzleOffset() {
        return new Vec3(0, 2.53, 1.4);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level() instanceof ServerLevel level) {
            this.refiner.tick(level);
            this.entityData.set(DATA_REFINING, this.refiner.working());
        }
    }

    // ---- 交互

    @Override
    public InteractionResult interact(Player player, InteractionHand hand, Vec3 location) {
        if (player.isSecondaryUseActive() && this.atRefiner(location)) {
            if (this.level() instanceof ServerLevel) {
                this.openRefiner(player);
            }
            return InteractionResult.SUCCESS;
        }
        return super.interact(player, hand, location);
    }

    /** 点的地方（相对车的位置）在不在车尾的精炼机那一截。 */
    boolean atRefiner(Vec3 location) {
        float r = this.getYRot() * Mth.DEG_TO_RAD;
        double along = -location.x * Mth.sin(r) + location.z * Mth.cos(r);
        return along < REFINER_FROM;
    }

    /** 服务端：打开车尾的精炼机，界面和便携精炼机一样。 */
    void openRefiner(Player player) {
        MachineMenu.Type type = RecipeMachines.PORTABLE_REFINER.menu();
        player.openMenu(new SimpleMenuProvider((id, inventory, p) -> new MachineMenu(type, id, inventory, this.refiner, this.refiner.data(),
                this.blockPosition()), Component.translatable("container.universe.heavy_rover_refiner")), this.blockPosition());
    }

    // ---- 存档和物品

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        this.refiner.save(output);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.refiner.load(input);
    }

    @Override
    protected void saveToItem(ItemStack stack) {
        VehicleRefiner.Contents contents = this.refiner.contents();
        if (contents != null) {
            stack.set(UniverseDataComponents.VEHICLE_REFINER.get(), contents);
        }
        this.refiner.clearContent();
    }

    @Override
    protected void loadFromItem(ItemStack stack) {
        VehicleRefiner.Contents contents = stack.get(UniverseDataComponents.VEHICLE_REFINER.get());
        if (contents != null) {
            this.refiner.restore(contents);
        }
    }

    @Override
    protected Item getDropItem() {
        return UniverseItems.HEAVY_ROVER.get();
    }

    // ---- 乘坐和物理

    @Override
    protected Vec3 getPassengerAttachmentPoint(Entity passenger, EntityDimensions dimensions, float scale) {
        // 驾驶室在车头，座椅面离地 1 格（和模型对齐）
        return new Vec3(0, 1.0, 0.85).yRot(-this.getYRot() * Mth.DEG_TO_RAD);
    }

    @Override
    public float maxUpStep() {
        return 1.55F;
    }
}
