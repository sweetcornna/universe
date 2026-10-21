package io.github.sweetcornna.universe.factory;

import io.github.sweetcornna.universe.core.factory.Cargo;
import io.github.sweetcornna.universe.core.factory.Proliferation;
import io.github.sweetcornna.universe.core.factory.Throughput;
import io.github.sweetcornna.universe.factory.belt.BeltNetwork;
import io.github.sweetcornna.universe.factory.belt.BeltReceiver;
import io.github.sweetcornna.universe.registry.UniverseBlockEntities;
import io.github.sweetcornna.universe.registry.UniverseItems;
import io.github.sweetcornna.universe.registry.UniverseMenus;
import io.github.sweetcornna.universe.stats.ProductionLog;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * 分馏塔（ADR-0015）：氢从传送带（或分拣器、漏斗）进来，每个有 1% 变成重氢，满电每秒最多处理 30 个。没变的氢从
 * 正面推出去，接着往下一座分馏塔送；重氢往上推（上面放储液罐、储物仓），也可以用分拣器从产物格拿。
 */
public class FractionatorBlockEntity extends MachineBlockEntity implements BeltReceiver {
    /** 戴森球计划的 720 kW。 */
    public static final long POWER = 36;
    /** 每刻最多处理几个（每秒 30 个）。 */
    public static final double PER_TICK = Throughput.FRACTIONATOR_PER_TICK;
    /** 每个氢变成重氢的概率。 */
    public static final double CHANCE = Throughput.FRACTIONATOR_CHANCE;
    /** 原料格最多攒多少氢。 */
    private static final int BUFFER = 40;

    public static final int INPUT = 0;
    public static final int HYDROGEN = 1;
    public static final int DEUTERIUM = 2;

    /** 最近一秒处理了几个、一共出了多少重氢（拆两格）。 */
    public static final int RATE = BASE_DATA;
    public static final int PRODUCED = BASE_DATA + 1;
    public static final int DATA_COUNT = BASE_DATA + 3;

    private double progress;
    private boolean active;
    private double supplied;
    private int processedThisSecond;
    private int processedLastSecond;
    private int secondTimer;
    private long produced;

    public FractionatorBlockEntity(BlockPos pos, BlockState state) {
        super(UniverseBlockEntities.FRACTIONATOR.get(), pos, state, 3);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (this.level != null) {
            BeltNetwork.of(this.level).outputChanged(this.worldPosition);
        }
    }

    @Override
    public void setRemoved() {
        if (this.level != null) {
            BeltNetwork network = BeltNetwork.existing(this.level);
            if (network != null) {
                network.outputChanged(this.worldPosition);
            }
        }
        super.setRemoved();
    }

    private static Item hydrogen() {
        return UniverseItems.HYDROGEN.get();
    }

    /** 喷过增产剂的氢变成重氢的概率。 */
    public static double chance(int proliferation) {
        return CHANCE * (1 + Proliferation.speedup(proliferation));
    }

    /** 下一个流出去的氢：带着原料格里的平均点数。 */
    private ItemStack passThrough() {
        return BeltNetwork.stack(hydrogen(), 1, this.inputLevel(INPUT));
    }

    @Override
    public boolean acceptFromBelt(int cargo) {
        ItemStack input = this.getItem(INPUT);
        int count = Cargo.count(cargo);
        if (BeltNetwork.item(cargo) != hydrogen() || input.getCount() + count > BUFFER) {
            return false;
        }
        this.pool(INPUT);
        if (input.isEmpty()) {
            this.getItems().set(INPUT, new ItemStack(hydrogen(), count));
        } else {
            input.grow(count);
        }
        this.receiveInput(INPUT, hydrogen(), count, Cargo.points(cargo));
        this.setChanged();
        return true;
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
        if (++this.secondTimer >= 20) {
            this.secondTimer = 0;
            this.processedLastSecond = this.processedThisSecond;
            this.processedThisSecond = 0;
        }
        Direction front = this.getBlockState().getValue(MachineBlock.FACING);
        this.pushOutput(HYDROGEN, front, 2);
        this.pushOutput(DEUTERIUM, Direction.UP, 2);
        ItemStack d = new ItemStack(UniverseItems.DEUTERIUM.get());
        this.active = !this.getItem(INPUT).isEmpty() && this.outputHasRoom(HYDROGEN, this.passThrough()) && this.outputHasRoom(DEUTERIUM, d);
        if (!this.active || rate <= 0 || this.level == null) {
            return false;
        }
        this.progress += PER_TICK * rate;
        while (this.progress >= 1 && !this.getItem(INPUT).isEmpty() && this.outputHasRoom(HYDROGEN, this.passThrough())
                && this.outputHasRoom(DEUTERIUM, d)) {
            this.progress -= 1;
            // 喷过增产剂的氢变成重氢的概率按加速的比例提高，没变的氢带着点数流出去，下一座分馏塔接着用（ADR-0022）
            int proliferation = this.consumeInput(INPUT, 1);
            ItemStack h = BeltNetwork.stack(hydrogen(), 1, proliferation);
            if (this.level.getRandom().nextDouble() < chance(proliferation)) {
                // 统计里只算变成重氢的那一个，流过去的氢不算
                ProductionLog.consumed(this.level, h.getItem(), 1);
                ProductionLog.produced(this.level, d.getItem(), 1);
                this.addOutput(DEUTERIUM, d.copy());
                this.produced++;
            } else {
                this.addOutput(HYDROGEN, h.copy());
            }
            this.processedThisSecond++;
        }
        this.progress = Math.min(this.progress, 2);
        this.setChanged();
        return true;
    }

    // ---- 物品栏

    @Override
    protected boolean isInput(int slot) {
        return slot == INPUT;
    }

    @Override
    protected boolean isOutput(int slot) {
        return slot == HYDROGEN || slot == DEUTERIUM;
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return slot == INPUT && stack.is(hydrogen());
    }

    // ---- 界面

    @Override
    protected MachineMenu.Type menuType() {
        return UniverseMenus.FRACTIONATOR;
    }

    @Override
    protected int dataCount() {
        return DATA_COUNT;
    }

    @Override
    protected int dataValue(int index) {
        return switch (index) {
            case RATE -> this.processedLastSecond;
            case PRODUCED -> MachineMenu.low(this.produced);
            case PRODUCED + 1 -> MachineMenu.high(this.produced);
            default -> super.dataValue(index);
        };
    }

    // ---- 存档

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.progress = input.getDoubleOr("Progress", 0);
        this.produced = input.getLongOr("Produced", 0);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putDouble("Progress", this.progress);
        output.putLong("Produced", this.produced);
    }
}
