package io.github.sweetcornna.universe.chem;

import java.util.List;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.chem.reactor.ReactorRecipes;
import io.github.sweetcornna.universe.core.chem.AtmosphereMix;
import io.github.sweetcornna.universe.core.chem.Extraction;
import io.github.sweetcornna.universe.core.chem.Gas;
import io.github.sweetcornna.universe.core.chem.Substances;
import io.github.sweetcornna.universe.core.terraform.EffectiveEnvironment;
import io.github.sweetcornna.universe.factory.MachineBlock;
import io.github.sweetcornna.universe.factory.MachineBlockEntity;
import io.github.sweetcornna.universe.factory.MachineMenu;
import io.github.sweetcornna.universe.registry.UniverseBlockEntities;
import io.github.sweetcornna.universe.registry.UniverseMenus;
import io.github.sweetcornna.universe.stats.ProductionLog;
import io.github.sweetcornna.universe.terraform.Terraforming;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * 大气提取器（ADR-0036 §三）：放在有大气的星球地表，头顶要看得到天。界面里选一种气体，按气压和这种气体的分数出货，
 * 从正面推出去；分拣器、漏斗也能从产物格拿。越稀的大气越费电。
 */
public class AtmosphericExtractorBlockEntity extends MachineBlockEntity implements MachineMenu.ButtonTarget {
    public static final int OUTPUT = 0;
    /** 多久重新看一次头顶和大气（刻）。 */
    private static final int CHECK_INTERVAL = 40;

    /** 界面数据：状态（{@link #OK} 等）。 */
    public static final int STATUS = BASE_DATA;
    /** 选的气体的序号 + 1，没选为 0。 */
    public static final int SELECTED = BASE_DATA + 1;
    /** 能选的气体，按 {@link Gas#ordinal()} 的位。 */
    public static final int COLLECTABLE = BASE_DATA + 2;
    /** 选的气体每分钟出几个 × 10。 */
    public static final int RATE = BASE_DATA + 3;
    /** 气压（标准大气压）× 1000，两格。 */
    public static final int PRESSURE = BASE_DATA + 4;
    public static final int DATA_COUNT = BASE_DATA + 6;

    public static final int OK = 0;
    /** 不在星球上，或者星球没有大气。 */
    public static final int NO_ATMOSPHERE = 1;
    /** 头顶有遮挡。 */
    public static final int NO_SKY = 2;
    /** 产物格满了。 */
    public static final int FULL = 3;

    private Gas selected;
    private AtmosphereMix mix = AtmosphereMix.EMPTY;
    private double pressure;
    private boolean sky;
    private int checkTimer;
    private double progress;
    private boolean active;
    private double supplied;
    /** {@link #mix} 里能取的气体（按分数排好），大气换了才重新排：设备每刻都要挑气体。 */
    private List<Gas> collectable = List.of();
    private @Nullable AtmosphereMix collectableMix;

    public AtmosphericExtractorBlockEntity(BlockPos pos, BlockState state) {
        super(UniverseBlockEntities.ATMOSPHERIC_EXTRACTOR.get(), pos, state, 1);
    }

    /** 选的气体；还没选、或者选的这里取不到时，取分数最大的那种。 */
    public Optional<Gas> gas() {
        List<Gas> collectable = this.collectable();
        if (this.selected != null && collectable.contains(this.selected)) {
            return Optional.of(this.selected);
        }
        return collectable.isEmpty() ? Optional.empty() : Optional.of(collectable.getFirst());
    }

    /** 这里的大气里能取的气体，按分数排好；大气换了才重新排。 */
    private List<Gas> collectable() {
        if (this.collectableMix != this.mix) {
            this.collectableMix = this.mix;
            this.collectable = Extraction.collectable(this.mix);
        }
        return this.collectable;
    }

    @Override
    public long demand() {
        return this.active ? Math.round(Extraction.power(this.pressure)) : 0;
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
            this.checkTimer = CHECK_INTERVAL;
            this.check();
        }
        this.pushOutput(OUTPUT, this.getBlockState().getValue(MachineBlock.FACING), 4);
        Optional<Gas> gas = this.gas();
        ItemStack product = gas.map(AtmosphericExtractorBlockEntity::item).map(ItemStack::new).orElse(ItemStack.EMPTY);
        this.active = this.sky && gas.isPresent() && !product.isEmpty() && this.outputHasRoom(OUTPUT, product);
        if (!this.active || rate <= 0) {
            return false;
        }
        this.progress += rate;
        double ticks = Extraction.ticksPerItem(this.mix, gas.get(), this.pressure);
        if (this.progress >= ticks) {
            this.progress -= ticks;
            this.extract(gas.get(), product);
            this.setChanged();
        } else {
            this.markUnsaved();
        }
        return true;
    }

    /** 取出一个气体：从改造中的星球的大气里扣掉一个单位（ADR-0036 §六），进产物格、记进生产统计。 */
    protected void extract(Gas gas, ItemStack product) {
        if (this.level instanceof ServerLevel level && Terraforming.withdraw(level, gas, 1) < 1) {
            // 这种气体抽光了，下一刻重新看一次大气
            this.checkTimer = 0;
            return;
        }
        ProductionLog.produced(this.level, product.getItem(), 1);
        this.addOutput(OUTPUT, product);
    }

    /** 重新看一次所在的星球和头顶。 */
    private void check() {
        if (!(this.level instanceof ServerLevel level)) {
            return;
        }
        Optional<EffectiveEnvironment> environment = Terraforming.environment(level);
        this.mix = environment.map(PlanetAir::mix).orElse(AtmosphereMix.EMPTY);
        this.pressure = environment.map(PlanetAir::pressure).orElse(0.0);
        this.sky = level.dimensionType().hasSkyLight() && level.canSeeSky(this.worldPosition.above());
    }

    /** 气体对应的物品。 */
    public static Item item(Gas gas) {
        return ReactorRecipes.item(Substances.item(gas));
    }

    @Override
    public boolean clickButton(ServerPlayer player, int button) {
        Gas[] gases = Gas.values();
        if (button < 0 || button >= gases.length || !this.collectable().contains(gases[button])) {
            return false;
        }
        if (gases[button] != this.selected) {
            this.selected = gases[button];
            this.progress = 0;
            this.setChanged();
        }
        return true;
    }

    private int status() {
        if (this.mix.isEmpty() || this.pressure <= 0) {
            return NO_ATMOSPHERE;
        }
        if (!this.sky) {
            return NO_SKY;
        }
        Optional<Gas> gas = this.gas();
        if (gas.isPresent() && !this.outputHasRoom(OUTPUT, new ItemStack(item(gas.get())))) {
            return FULL;
        }
        return OK;
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
        return UniverseMenus.ATMOSPHERIC_EXTRACTOR;
    }

    @Override
    protected int dataCount() {
        return DATA_COUNT;
    }

    @Override
    protected int dataValue(int index) {
        if (index == STATUS) {
            return this.status();
        }
        if (index == SELECTED) {
            return this.gas().map(gas -> gas.ordinal() + 1).orElse(0);
        }
        if (index == COLLECTABLE) {
            int bits = 0;
            for (Gas gas : this.collectable()) {
                bits |= 1 << gas.ordinal();
            }
            return bits;
        }
        if (index == RATE) {
            return this.gas().map(gas -> (int) Math.round(Extraction.perMinute(this.mix, gas, this.pressure) * 10)).orElse(0);
        }
        if (index == PRESSURE || index == PRESSURE + 1) {
            long milli = Math.round(this.pressure * 1000);
            return index == PRESSURE ? MachineMenu.low(milli) : MachineMenu.high(milli);
        }
        return super.dataValue(index);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.progress = input.getDoubleOr("Progress", 0);
        this.selected = Gas.byName(input.getStringOr("Gas", ""));
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putDouble("Progress", this.progress);
        if (this.selected != null) {
            output.putString("Gas", this.selected.name());
        }
    }
}
