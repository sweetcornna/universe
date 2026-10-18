package io.github.sweetcornna.universe.factory.power;

import io.github.sweetcornna.universe.factory.MachineBlockEntity;
import io.github.sweetcornna.universe.factory.MachineMenu;
import io.github.sweetcornna.universe.registry.UniverseBlockEntities;
import io.github.sweetcornna.universe.registry.UniverseDataComponents;
import io.github.sweetcornna.universe.registry.UniverseMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * 蓄电器（玩法设计 §9.4）：发电有富余时充电，不够时放电，削峰填谷。容量 180 kJ，充放电最快 2.7 kW。
 * 拆下来时电量跟着物品走。
 */
public class AccumulatorBlockEntity extends MachineBlockEntity {
    /** 容量（戴森球计划 180 MJ）。 */
    public static final long CAPACITY = 180_000;
    /** 每刻最多充进、放出多少（戴森球计划 2.7 MW）。 */
    public static final long RATE = 135;
    public static final int ENERGY = BASE_DATA;
    /** 1 充电，-1 放电，0 不动。 */
    public static final int DIRECTION = BASE_DATA + 2;
    public static final int DATA_COUNT = BASE_DATA + 3;

    private long energy;

    public AccumulatorBlockEntity(BlockPos pos, BlockState state) {
        super(UniverseBlockEntities.ACCUMULATOR.get(), pos, state, 0);
    }

    public long energy() {
        return this.energy;
    }

    @Override
    public long chargeLimit() {
        return Math.min(RATE, CAPACITY - this.energy);
    }

    @Override
    public long dischargeLimit() {
        return Math.min(RATE, this.energy);
    }

    @Override
    public void settle(double satisfaction, double load, long stored) {
        super.settle(satisfaction, load, stored);
        if (stored != 0) {
            this.energy = Math.max(0, Math.min(CAPACITY, this.energy + stored));
            this.setChanged();
        }
    }

    @Override
    protected long powerFlow() {
        return Math.abs(this.stored);
    }

    @Override
    protected boolean tickMachine() {
        // 侧面的电量指示跟着电量走
        BlockState state = this.getBlockState();
        int charge = (int) Math.min(AccumulatorBlock.MAX_CHARGE, (this.energy * AccumulatorBlock.MAX_CHARGE + CAPACITY / 2) / CAPACITY);
        if (this.level != null && state.hasProperty(AccumulatorBlock.CHARGE) && state.getValue(AccumulatorBlock.CHARGE) != charge) {
            this.level.setBlock(this.worldPosition, state.setValue(AccumulatorBlock.CHARGE, charge), 3);
        }
        return this.stored != 0;
    }

    @Override
    protected int dataCount() {
        return DATA_COUNT;
    }

    @Override
    protected int dataValue(int index) {
        return switch (index) {
            case 0 -> this.connected() ? 1000 : -1;
            case ENERGY -> MachineMenu.low(this.energy);
            case ENERGY + 1 -> MachineMenu.high(this.energy);
            case DIRECTION -> Long.signum(this.stored);
            default -> super.dataValue(index);
        };
    }

    @Override
    protected MachineMenu.Type menuType() {
        return UniverseMenus.ACCUMULATOR;
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.energy = Math.max(0, Math.min(CAPACITY, input.getLongOr("Energy", 0)));
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putLong("Energy", this.energy);
    }

    @Override
    protected void applyImplicitComponents(DataComponentGetter components) {
        super.applyImplicitComponents(components);
        Long stored = components.get(UniverseDataComponents.STORED_ENERGY.get());
        this.energy = stored == null ? 0 : Math.max(0, Math.min(CAPACITY, stored));
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        if (this.energy > 0) {
            components.set(UniverseDataComponents.STORED_ENERGY.get(), this.energy);
        }
    }

    @Override
    public void removeComponentsFromTag(ValueOutput output) {
        super.removeComponentsFromTag(output);
        output.discard("Energy");
    }
}
