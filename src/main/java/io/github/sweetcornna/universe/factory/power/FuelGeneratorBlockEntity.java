package io.github.sweetcornna.universe.factory.power;

import java.util.function.Supplier;

import io.github.sweetcornna.universe.factory.MachineBlockEntity;
import io.github.sweetcornna.universe.stats.ProductionLog;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * 烧一种燃料棒的发电设备（ADR-0018）：微型聚变发电站烧氘核燃料棒，人造恒星烧反物质燃料棒。和火力发电厂一样只发电网要的那么多，
 * 按负荷烧燃料；燃料格只收自己那种燃料棒。
 */
public abstract class FuelGeneratorBlockEntity extends MachineBlockEntity {
    public static final int FUEL_SLOT = 0;
    /** 炉膛里剩下的能量，千分数。 */
    public static final int FUEL = BASE_DATA;
    public static final int DATA_COUNT = BASE_DATA + 1;

    private final long maxPower;
    private final Supplier<? extends Item> fuel;
    private final long fuelEnergy;
    /** 烧进去还没发出去的能量。 */
    private long energy;

    /**
     * @param maxPower   每刻最多发的电（焦耳）
     * @param fuel       燃料棒
     * @param fuelEnergy 一根燃料棒的能量（焦耳）
     */
    protected FuelGeneratorBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, long maxPower, Supplier<? extends Item> fuel,
            long fuelEnergy) {
        super(type, pos, state, 1);
        this.maxPower = maxPower;
        this.fuel = fuel;
        this.fuelEnergy = fuelEnergy;
    }

    @Override
    public long generation() {
        return Math.min(this.maxPower, this.energy);
    }

    @Override
    public void settle(double satisfaction, double load, long stored) {
        super.settle(satisfaction, load, stored);
        this.energy = Math.max(0, this.energy - Math.round(this.generation() * load));
    }

    @Override
    protected boolean tickMachine() {
        // 剩下的能量不够下一刻满发时添一根
        if (this.energy < this.maxPower && this.level instanceof ServerLevel serverLevel) {
            ItemStack stack = this.getItem(FUEL_SLOT);
            if (stack.is(this.fuel.get())) {
                this.energy += this.fuelEnergy;
                ProductionLog.consumed(serverLevel, stack.getItem(), 1);
                stack.shrink(1);
                this.setChanged();
            }
        }
        return this.load > 0 && this.energy > 0;
    }

    @Override
    protected boolean isInput(int slot) {
        return slot == FUEL_SLOT;
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return slot == FUEL_SLOT && stack.is(this.fuel.get());
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
            return (int) Math.min(1000, this.energy * 1000 / this.fuelEnergy);
        }
        return super.dataValue(index);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.energy = input.getLongOr("Energy", 0);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putLong("Energy", this.energy);
    }
}
