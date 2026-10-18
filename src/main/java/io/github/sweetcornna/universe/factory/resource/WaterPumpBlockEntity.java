package io.github.sweetcornna.universe.factory.resource;

import io.github.sweetcornna.universe.core.galaxy.Liquid;
import io.github.sweetcornna.universe.factory.MachineBlock;
import io.github.sweetcornna.universe.factory.MachineBlockEntity;
import io.github.sweetcornna.universe.factory.MachineMenu;
import io.github.sweetcornna.universe.registry.UniverseBlockEntities;
import io.github.sweetcornna.universe.registry.UniverseItems;
import io.github.sweetcornna.universe.registry.UniverseMenus;
import io.github.sweetcornna.universe.world.planet.Planets;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * 抽水站（ADR-0011）：放在水源方块上，每分钟抽 50 个水，从正面推出去。酸海星球的"水"是酸液，抽不了。
 */
public class WaterPumpBlockEntity extends MachineBlockEntity {
    /** 戴森球计划的 300 kW。 */
    public static final long POWER = 15;
    /** 每分钟 50 个。 */
    public static final int TICKS_PER_ITEM = 24;
    public static final int OUTPUT = 0;

    /** 下面的液体：0 没有水，1 有水，2 是酸液。 */
    public static final int SOURCE = BASE_DATA;
    public static final int DATA_COUNT = BASE_DATA + 1;
    public static final int NO_WATER = 0;
    public static final int WATER = 1;
    public static final int ACID = 2;

    private int source = NO_WATER;
    private int checkTimer;
    private double progress;
    private boolean active;
    private double supplied;

    public WaterPumpBlockEntity(BlockPos pos, BlockState state) {
        super(UniverseBlockEntities.WATER_PUMP.get(), pos, state, 1);
    }

    @Override
    public long demand() {
        return this.active ? POWER : 0;
    }

    @Override
    public void settle(double satisfaction, double load, long stored) {
        super.settle(satisfaction, load, stored);
        this.supplied = this.active ? satisfaction : 0;
    }

    @Override
    protected boolean tickMachine() {
        double rate = this.supplied;
        this.supplied = 0;
        if (--this.checkTimer <= 0) {
            this.checkTimer = 40;
            this.source = this.checkSource();
        }
        this.pushOutput(OUTPUT, this.getBlockState().getValue(MachineBlock.FACING), 4);
        ItemStack water = new ItemStack(UniverseItems.WATER.get());
        this.active = this.source == WATER && this.outputHasRoom(OUTPUT, water);
        if (!this.active || rate <= 0) {
            return false;
        }
        this.progress += rate;
        if (this.progress >= TICKS_PER_ITEM) {
            this.progress -= TICKS_PER_ITEM;
            this.addOutput(OUTPUT, water);
        }
        this.setChanged();
        return true;
    }

    private int checkSource() {
        if (!(this.level instanceof ServerLevel level)) {
            return NO_WATER;
        }
        FluidState fluid = level.getFluidState(this.worldPosition.below());
        if (!fluid.isSource() || !fluid.is(FluidTags.WATER)) {
            return NO_WATER;
        }
        return Planets.planet(level).map(planet -> planet.liquid() == Liquid.ACID).orElse(false) ? ACID : WATER;
    }

    @Override
    protected boolean isOutput(int slot) {
        return slot == OUTPUT;
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return false;
    }

    @Override
    protected MachineMenu.Type menuType() {
        return UniverseMenus.WATER_PUMP;
    }

    @Override
    protected int dataCount() {
        return DATA_COUNT;
    }

    @Override
    protected int dataValue(int index) {
        return index == SOURCE ? this.source : super.dataValue(index);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.progress = input.getDoubleOr("Progress", 0);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putDouble("Progress", this.progress);
    }
}
