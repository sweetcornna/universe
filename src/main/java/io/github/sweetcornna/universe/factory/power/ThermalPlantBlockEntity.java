package io.github.sweetcornna.universe.factory.power;

import io.github.sweetcornna.universe.core.factory.Proliferation;
import io.github.sweetcornna.universe.factory.MachineBlockEntity;
import io.github.sweetcornna.universe.factory.MachineMenu;
import io.github.sweetcornna.universe.registry.UniverseBlockEntities;
import io.github.sweetcornna.universe.registry.UniverseMenus;
import io.github.sweetcornna.universe.stats.ProductionLog;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.component.CookingFuel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.storage.loot.providers.number.ints.ResolvableInt;

/**
 * 火力发电厂（玩法设计 §9.4）：烧原版的燃料，最大 2.16 kW。只发电网需要的那么多，按负荷烧燃料，所以用电少时一块煤
 * 能撑很久。燃料的能量按原版燃烧时间算，每刻 15 J（ADR-0011）。
 */
public class ThermalPlantBlockEntity extends MachineBlockEntity {
    /** 每刻最多发的电（戴森球计划 2.16 MW）。 */
    public static final long MAX_POWER = 108;
    /** 原版燃烧时间每刻折合的能量。 */
    public static final long JOULES_PER_BURN_TICK = 15;
    public static final int FUEL_SLOT = 0;
    public static final int FUEL = BASE_DATA;
    public static final int DATA_COUNT = BASE_DATA + 1;

    /** 烧进炉膛还没发出去的能量。 */
    private long energy;
    /** 上一块燃料的总能量，界面上的火苗按它算。 */
    private long energyTotal;

    public ThermalPlantBlockEntity(BlockPos pos, BlockState state) {
        super(UniverseBlockEntities.THERMAL_PLANT.get(), pos, state, 1);
    }

    @Override
    public long generation() {
        return Math.min(MAX_POWER, this.energy);
    }

    @Override
    public void settle(double satisfaction, double load, long stored) {
        super.settle(satisfaction, load, stored);
        this.energy = Math.max(0, this.energy - Math.round(this.generation() * load));
    }

    @Override
    protected boolean tickMachine() {
        // 炉膛里的能量不够下一刻满发时添一块
        if (this.energy < MAX_POWER && this.level instanceof ServerLevel serverLevel) {
            ItemStack fuel = this.getItem(FUEL_SLOT);
            long value = this.fuelEnergy(serverLevel, fuel);
            if (value > 0) {
                ItemStackTemplate remainder = fuel.getCraftingRemainder();
                ProductionLog.consumed(serverLevel, fuel.getItem(), 1);
                // 喷过增产剂的燃料多出热量，比例和额外产出一样（ADR-0022）
                value = Math.round(value * (1 + Proliferation.extraProducts(this.consumeInput(FUEL_SLOT, 1))));
                this.energy += value;
                this.energyTotal = value;
                if (remainder != null) {
                    if (fuel.isEmpty()) {
                        this.setItem(FUEL_SLOT, remainder.create());
                    } else {
                        Containers.dropItemStack(serverLevel, this.worldPosition.getX(), this.worldPosition.getY() + 1, this.worldPosition.getZ(),
                                remainder.create());
                    }
                }
                this.setChanged();
            }
        }
        return this.load > 0 && this.energy > 0;
    }

    private long fuelEnergy(ServerLevel level, ItemStack fuel) {
        if (fuel.isEmpty()) {
            return 0;
        }
        int burnTime = ResolvableInt.getFromItem(fuel, DataComponents.COOKING_FUEL, CookingFuel::burnTime, this.getLootContext(level, fuel), 0);
        return burnTime * JOULES_PER_BURN_TICK;
    }

    @Override
    protected boolean isInput(int slot) {
        return slot == FUEL_SLOT;
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return slot == FUEL_SLOT && stack.has(DataComponents.COOKING_FUEL);
    }

    @Override
    protected boolean generator() {
        return true;
    }

    @Override
    protected int dataCount() {
        return DATA_COUNT;
    }

    @Override
    protected int dataValue(int index) {
        if (index == FUEL) {
            return this.energyTotal <= 0 ? 0 : (int) Math.min(1000, this.energy * 1000 / this.energyTotal);
        }
        return super.dataValue(index);
    }

    @Override
    protected MachineMenu.Type menuType() {
        return UniverseMenus.THERMAL_PLANT;
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.energy = input.getLongOr("Energy", 0);
        this.energyTotal = input.getLongOr("EnergyTotal", 0);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putLong("Energy", this.energy);
        output.putLong("EnergyTotal", this.energyTotal);
    }
}
