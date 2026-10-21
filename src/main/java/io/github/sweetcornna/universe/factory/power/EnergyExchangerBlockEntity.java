package io.github.sweetcornna.universe.factory.power;

import io.github.sweetcornna.universe.factory.MachineBlockEntity;
import io.github.sweetcornna.universe.factory.MachineMenu;
import io.github.sweetcornna.universe.registry.UniverseBlockEntities;
import io.github.sweetcornna.universe.registry.UniverseDataComponents;
import io.github.sweetcornna.universe.registry.UniverseItems;
import io.github.sweetcornna.universe.registry.UniverseMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * 能量枢纽（ADR-0022）：充电模式下从电网取电，把空的蓄电器（物品）充满；放电模式下把满的蓄电器里的电放回电网。
 * 每次处理一个，处理完放进产物格；和模式不对的蓄电器直接挪过去。速度是戴森球计划的 45 MW。
 */
public class EnergyExchangerBlockEntity extends MachineBlockEntity implements MachineMenu.ButtonTarget {
    /** 每刻最多充进、放出多少：戴森球计划的 45 MW。 */
    public static final long RATE = 2250;
    public static final int INPUT = 0;
    public static final int OUTPUT = 1;
    /** 界面数据：模式（0 充电，1 放电）、正在处理的那个的电量（两格）、有没有在处理。 */
    public static final int MODE = BASE_DATA;
    public static final int ENERGY = BASE_DATA + 1;
    public static final int LOADED = BASE_DATA + 3;
    public static final int DATA_COUNT = BASE_DATA + 4;
    public static final int MODE_BUTTON = 0;

    private boolean discharging;
    /** 正在处理的蓄电器；没有时是 false。 */
    private boolean loaded;
    private long energy;

    public EnergyExchangerBlockEntity(BlockPos pos, BlockState state) {
        super(UniverseBlockEntities.ENERGY_EXCHANGER.get(), pos, state, 2);
    }

    public boolean discharging() {
        return this.discharging;
    }

    @Override
    public boolean clickButton(ServerPlayer player, int button) {
        if (button != MODE_BUTTON) {
            return false;
        }
        this.discharging = !this.discharging;
        this.setChanged();
        return true;
    }

    // ---- 电

    @Override
    public long demand() {
        return this.loaded && !this.discharging ? Math.min(RATE, AccumulatorBlockEntity.CAPACITY - this.energy) : 0;
    }

    @Override
    public long generation() {
        return this.loaded && this.discharging ? Math.min(RATE, this.energy) : 0;
    }

    @Override
    protected boolean generator() {
        return this.discharging;
    }

    @Override
    public void settle(double satisfaction, double load, long stored) {
        long demand = this.demand();
        long generation = this.generation();
        super.settle(satisfaction, load, stored);
        if (demand > 0) {
            this.energy = Math.min(AccumulatorBlockEntity.CAPACITY, this.energy + Math.round(demand * satisfaction));
        } else if (generation > 0) {
            this.energy = Math.max(0, this.energy - Math.round(generation * load));
        }
    }

    // ---- 每刻

    @Override
    protected boolean tickMachine() {
        if (this.loaded && this.finished() && this.unload()) {
            this.setChanged();
        }
        if (!this.loaded) {
            this.load();
        }
        return this.loaded && (this.demand() > 0 && this.satisfaction > 0 || this.generation() > 0 && this.load > 0);
    }

    private boolean finished() {
        return this.discharging ? this.energy <= 0 : this.energy >= AccumulatorBlockEntity.CAPACITY;
    }

    /** 处理完的蓄电器放进产物格；放不下返回 false，接着等。 */
    private boolean unload() {
        ItemStack done = accumulator(this.energy);
        if (!this.outputHasRoom(OUTPUT, done)) {
            return false;
        }
        this.addOutput(OUTPUT, done);
        this.loaded = false;
        this.energy = 0;
        return true;
    }

    /** 从原料格拿一个：和模式对得上的开始处理，对不上的直接挪到产物格。 */
    private void load() {
        ItemStack input = this.getItem(INPUT);
        if (input.isEmpty()) {
            return;
        }
        long stored = input.getOrDefault(UniverseDataComponents.STORED_ENERGY.get(), 0L);
        boolean useful = this.discharging ? stored > 0 : stored < AccumulatorBlockEntity.CAPACITY;
        if (!useful) {
            ItemStack one = input.copyWithCount(1);
            if (this.outputHasRoom(OUTPUT, one)) {
                this.addOutput(OUTPUT, one);
                input.shrink(1);
                this.setChanged();
            }
            return;
        }
        input.shrink(1);
        this.loaded = true;
        this.energy = stored;
        this.setChanged();
    }

    /** 一个电量是 {@code energy} 的蓄电器物品。 */
    public static ItemStack accumulator(long energy) {
        ItemStack stack = new ItemStack(UniverseItems.ACCUMULATOR.get());
        if (energy > 0) {
            stack.set(UniverseDataComponents.STORED_ENERGY.get(), energy);
        }
        return stack;
    }

    // ---- 物品栏

    @Override
    protected boolean isInput(int slot) {
        return slot == INPUT;
    }

    @Override
    protected boolean isOutput(int slot) {
        return slot == OUTPUT;
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return slot == INPUT && stack.is(UniverseItems.ACCUMULATOR.get());
    }

    /** 拆掉时正在处理的那个也掉出来。 */
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        if (this.loaded) {
            this.loaded = false;
            ItemStack current = accumulator(this.energy);
            if (this.level != null) {
                net.minecraft.world.Containers.dropItemStack(this.level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, current);
            }
        }
        super.preRemoveSideEffects(pos, state);
    }

    // ---- 界面和存档

    @Override
    protected MachineMenu.Type menuType() {
        return UniverseMenus.ENERGY_EXCHANGER;
    }

    @Override
    protected int dataCount() {
        return DATA_COUNT;
    }

    @Override
    protected int dataValue(int index) {
        return switch (index) {
            case MODE -> this.discharging ? 1 : 0;
            case ENERGY -> MachineMenu.low(this.energy);
            case ENERGY + 1 -> MachineMenu.high(this.energy);
            case LOADED -> this.loaded ? 1 : 0;
            default -> super.dataValue(index);
        };
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.discharging = input.getBooleanOr("Discharging", false);
        this.loaded = input.getBooleanOr("Loaded", false);
        this.energy = Math.max(0, Math.min(AccumulatorBlockEntity.CAPACITY, input.getLongOr("Energy", 0)));
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putBoolean("Discharging", this.discharging);
        output.putBoolean("Loaded", this.loaded);
        output.putLong("Energy", this.energy);
    }
}
