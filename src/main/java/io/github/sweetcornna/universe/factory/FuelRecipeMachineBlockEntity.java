package io.github.sweetcornna.universe.factory;

import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.core.factory.Proliferation;
import io.github.sweetcornna.universe.factory.power.LevelPower;
import io.github.sweetcornna.universe.stats.ProductionLog;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.component.CookingFuel;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.storage.loot.providers.number.ints.ResolvableInt;

/**
 * 烧燃料的配方设备（ADR-0026）：营养处理器、便携精炼机。和电网的设备一样在界面上选配方，但不接电网，
 * 产物格后面多一个燃料格，烧原版熔炉认的燃料。只在干活的刻里烧，停工时炉膛里剩的火留着。
 * 燃料的燃烧时间就是能干活的刻数：一块煤 80 秒。
 */
public class FuelRecipeMachineBlockEntity extends RecipeMachineBlockEntity {
    /** 炉膛里剩下的火（千分数）。 */
    public static final int FUEL = RecipeMachineBlockEntity.DATA_COUNT;
    public static final int DATA_COUNT = FUEL + 1;

    private final int fuelSlot;
    /** 拆掉时里面的东西跟着方块走（便携精炼机），不撒出来。 */
    private final boolean keepsContents;
    /** 这块燃料还能烧几刻。 */
    private int burnLeft;
    /** 这块燃料一共能烧几刻，界面上的火苗按它算。 */
    private int burnTotal;

    public FuelRecipeMachineBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, Spec spec, boolean keepsContents) {
        super(type, pos, state, spec, 1);
        this.fuelSlot = spec.inputs() + spec.outputs();
        this.keepsContents = keepsContents;
    }

    public int fuelSlot() {
        return this.fuelSlot;
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        if (!this.keepsContents) {
            super.preRemoveSideEffects(pos, state);
        }
    }

    /** 带着东西走的设备连选好的配方一起记在物品上（自定义数据），放下来接着做，不用重选。 */
    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        CompoundTag settings = this.keepsContents ? this.blueprintSettings() : null;
        if (settings != null) {
            components.set(DataComponents.CUSTOM_DATA, CustomData.of(settings));
        }
    }

    @Override
    protected void applyImplicitComponents(DataComponentGetter components) {
        super.applyImplicitComponents(components);
        CustomData settings = components.get(DataComponents.CUSTOM_DATA);
        if (this.keepsContents && settings != null) {
            this.applyBlueprintSettings(settings.copyTag());
        }
    }

    // ---- 不接电网

    @Override
    protected void joinGrid(LevelPower power) {
    }

    @Override
    protected void leaveGrid(LevelPower power) {
    }

    @Override
    public long demand() {
        return 0;
    }

    // ---- 燃料

    @Override
    protected double workRate(double supplied) {
        if (this.burnLeft <= 0 && !this.ignite()) {
            return 0;
        }
        this.burnLeft--;
        return 1;
    }

    @Override
    protected int starvedStatus() {
        return NO_FUEL;
    }

    /** 添一块燃料。燃料用完留下的东西（岩浆桶的空桶）放回燃料格，放不下就掉出来。 */
    private boolean ignite() {
        if (!(this.level instanceof ServerLevel serverLevel)) {
            return false;
        }
        ItemStack fuel = this.getItem(this.fuelSlot);
        int burn = burnTime(serverLevel, fuel);
        if (burn <= 0) {
            return false;
        }
        ItemStackTemplate remainder = fuel.getCraftingRemainder();
        ProductionLog.consumed(serverLevel, fuel.getItem(), 1);
        // 喷过增产剂的燃料烧得久，比例和额外产出一样（ADR-0022）
        burn = (int) Math.round(burn * (1 + Proliferation.extraProducts(this.consumeInput(this.fuelSlot, 1))));
        if (remainder != null) {
            if (this.getItem(this.fuelSlot).isEmpty()) {
                this.setItem(this.fuelSlot, remainder.create());
            } else {
                Containers.dropItemStack(serverLevel, this.worldPosition.getX(), this.worldPosition.getY() + 1, this.worldPosition.getZ(),
                        remainder.create());
            }
        }
        this.burnLeft = burn;
        this.burnTotal = burn;
        this.setChanged();
        return true;
    }

    private int burnTime(ServerLevel level, ItemStack fuel) {
        if (fuel.isEmpty()) {
            return 0;
        }
        return ResolvableInt.getFromItem(fuel, DataComponents.COOKING_FUEL, CookingFuel::burnTime, this.getLootContext(level, fuel), 0);
    }

    // ---- 物品栏

    @Override
    protected boolean isInput(int slot) {
        return slot == this.fuelSlot || super.isInput(slot);
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return slot == this.fuelSlot ? stack.has(DataComponents.COOKING_FUEL) : super.canPlaceItem(slot, stack);
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction side) {
        return slot == this.fuelSlot ? stack.has(DataComponents.COOKING_FUEL) : super.canPlaceItemThroughFace(slot, stack, side);
    }

    /** 燃料格里烧剩的空桶可以抽走。 */
    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        return slot == this.fuelSlot ? !stack.has(DataComponents.COOKING_FUEL) : super.canTakeItemThroughFace(slot, stack, side);
    }

    // ---- 界面

    @Override
    protected int dataCount() {
        return DATA_COUNT;
    }

    @Override
    protected int dataValue(int index) {
        if (index == FUEL) {
            return this.burnTotal <= 0 ? 0 : (int) Math.min(1000, (long) this.burnLeft * 1000 / this.burnTotal);
        }
        return super.dataValue(index);
    }

    // ---- 存档

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.burnLeft = input.getIntOr("Burn", 0);
        this.burnTotal = input.getIntOr("BurnTotal", 0);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("Burn", this.burnLeft);
        output.putInt("BurnTotal", this.burnTotal);
    }
}
