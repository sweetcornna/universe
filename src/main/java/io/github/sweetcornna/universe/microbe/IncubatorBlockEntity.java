package io.github.sweetcornna.universe.microbe;

import java.util.List;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.core.microbe.Isolation;
import io.github.sweetcornna.universe.core.microbe.MicrobeStrain;
import io.github.sweetcornna.universe.core.microbe.Organism;
import io.github.sweetcornna.universe.core.procgen.Rng;
import io.github.sweetcornna.universe.factory.MachineMenu;
import io.github.sweetcornna.universe.registry.UniverseBlockEntities;
import io.github.sweetcornna.universe.registry.UniverseItems;
import io.github.sweetcornna.universe.registry.UniverseMenus;
import io.github.sweetcornna.universe.stats.ProductionLog;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * 培养箱（ADR-0036 §五）：原料格放一管样本或菌种，耗材格放培养基，设一个温度。
 *
 * <ul>
 *   <li>样本：分离（{@link Isolation#isolate}），一天（2.4 分钟）后长出一管菌种；这个温度下样本里一种菌都长不了，
 *       就是一个空平板，还回一支空采样管。</li>
 *   <li>菌种：扩增成两管（{@link Isolation#expand}），在菌的温度范围边上扩增顺便驯化。这个温度长不了的不开工。</li>
 * </ul>
 * 组织样本不能培养，放不进来。
 */
public class IncubatorBlockEntity extends BioMachineBlockEntity {
    /** 恒温加热、风扇：2 J/t（40 kW）。 */
    public static final long POWER = 2;
    public static final int DEFAULT_K = 310;
    /** 界面数据：设定温度（K）、上一次分离的结果。 */
    public static final int TEMPERATURE = BIO_DATA;
    public static final int LAST = BIO_DATA + 1;
    public static final int DATA_COUNT = BIO_DATA + 2;
    /** 按钮：-10、-1、+1、+10 K；{@link #SET_BUTTON} 加 0–120 直接设到 273 加那么多。 */
    public static final int SET_BUTTON = 100;

    /** 界面上的状态。 */
    public enum Status {
        EMPTY,
        TISSUE,
        NO_MEDIUM,
        /** 菌种在这个温度下长不了。 */
        NO_GROWTH,
        OUTPUT_FULL,
        WORKING
    }

    /** 上一次分离的结果。 */
    public enum Last {
        NONE,
        GREW,
        NOTHING
    }

    private int temperature = DEFAULT_K;
    private Last last = Last.NONE;

    public IncubatorBlockEntity(BlockPos pos, BlockState state) {
        super(UniverseBlockEntities.INCUBATOR.get(), pos, state);
    }

    public int temperature() {
        return this.temperature;
    }

    @Override
    protected long power() {
        return POWER;
    }

    @Override
    protected int total() {
        ItemStack input = this.getItem(INPUT);
        if (SampleItem.sample(input) != null) {
            return Isolation.ISOLATION_TICKS;
        }
        return StrainTubeItem.organism(input) != null ? Isolation.EXPAND_TICKS : 0;
    }

    @Override
    protected int status() {
        return this.check().ordinal();
    }

    private Status check() {
        ItemStack input = this.getItem(INPUT);
        Sample sample = SampleItem.sample(input);
        Organism organism = StrainTubeItem.organism(input);
        if (sample == null && organism == null) {
            return Status.EMPTY;
        }
        if (sample != null && sample.tissue()) {
            return Status.TISSUE;
        }
        if (organism != null && !organism.tolerance().grows(this.temperature)) {
            return Status.NO_GROWTH;
        }
        if (!this.getItem(CONSUMABLE).is(UniverseItems.CULTURE_MEDIUM.get())) {
            return Status.NO_MEDIUM;
        }
        if (sample != null ? !this.getItem(OUTPUT).isEmpty() : !this.outputHasRoom(OUTPUT, product(organism))) {
            return Status.OUTPUT_FULL;
        }
        return Status.WORKING;
    }

    /** 扩增出来的两管。 */
    private ItemStack product(Organism organism) {
        return StrainTubeItem.stack(Isolation.expand(organism, this.temperature), Isolation.EXPAND_COPIES);
    }

    @Override
    protected boolean tickMachine() {
        if (!this.advance(this.check() == Status.WORKING)) {
            return this.moving();
        }
        ItemStack input = this.getItem(INPUT);
        Sample sample = SampleItem.sample(input);
        Organism organism = StrainTubeItem.organism(input);
        ItemStack out;
        if (sample != null) {
            Optional<MicrobeStrain> grown = this.isolate(sample);
            this.last = grown.isPresent() ? Last.GREW : Last.NOTHING;
            out = grown.map(strain -> StrainTubeItem.stack(Organism.wild(strain), 1)).orElseGet(() -> new ItemStack(UniverseItems.SAMPLE_TUBE.get()));
        } else if (organism != null) {
            out = this.product(organism);
        } else {
            return false;
        }
        this.consumeInput(INPUT, 1);
        this.consumeInput(CONSUMABLE, 1);
        this.addOutput(OUTPUT, out);
        if (out.is(UniverseItems.STRAIN_TUBE.get())) {
            ProductionLog.produced(this.level, out.getItem(), out.getCount());
        }
        this.play(SoundEvents.BREWING_STAND_BREW, 0.6F, 1.2F);
        this.setChanged();
        return true;
    }

    private Optional<MicrobeStrain> isolate(Sample sample) {
        if (!(this.level instanceof ServerLevel server)) {
            return Optional.empty();
        }
        List<MicrobeStrain> candidates = Microbiomes.candidates(server.getServer(), sample);
        return Isolation.isolate(candidates, this.temperature, Rng.of(server.getRandom().nextLong()));
    }

    @Override
    public boolean clickButton(ServerPlayer player, int button) {
        int next = switch (button) {
            case 0 -> this.temperature - 10;
            case 1 -> this.temperature - 1;
            case 2 -> this.temperature + 1;
            case 3 -> this.temperature + 10;
            default -> button >= SET_BUTTON && button <= SET_BUTTON + 120 ? (int) Isolation.MIN_SETTING_K + button - SET_BUTTON : this.temperature;
        };
        next = Mth.clamp(next, (int) Isolation.MIN_SETTING_K, (int) Isolation.MAX_SETTING_K);
        if (next == this.temperature) {
            return false;
        }
        this.temperature = next;
        // 换了温度从头培养
        this.progress = 0;
        this.setChanged();
        return true;
    }

    // ---- 物品栏

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return switch (slot) {
            case INPUT -> {
                Sample sample = SampleItem.sample(stack);
                yield sample != null && !sample.tissue() || StrainTubeItem.organism(stack) != null;
            }
            case CONSUMABLE -> stack.is(UniverseItems.CULTURE_MEDIUM.get());
            default -> false;
        };
    }

    // ---- 界面

    @Override
    protected int dataCount() {
        return DATA_COUNT;
    }

    @Override
    protected int dataValue(int index) {
        return switch (index) {
            case TEMPERATURE -> this.temperature;
            case LAST -> this.last.ordinal();
            default -> super.dataValue(index);
        };
    }

    @Override
    protected MachineMenu.Type menuType() {
        return UniverseMenus.INCUBATOR;
    }

    // ---- 存档

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.temperature = Mth.clamp(input.getIntOr("Temperature", DEFAULT_K), (int) Isolation.MIN_SETTING_K, (int) Isolation.MAX_SETTING_K);
        int last = input.getIntOr("Last", 0);
        this.last = last >= 0 && last < Last.values().length ? Last.values()[last] : Last.NONE;
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("Temperature", this.temperature);
        output.putInt("Last", this.last.ordinal());
    }

    /** 给 GameTest 用：设定温度。 */
    public void setTemperature(int kelvin) {
        this.temperature = Mth.clamp(kelvin, (int) Isolation.MIN_SETTING_K, (int) Isolation.MAX_SETTING_K);
        this.setChanged();
    }

    /** 给 GameTest 用：产物格里的菌。 */
    public @Nullable Organism output() {
        return StrainTubeItem.organism(this.getItem(OUTPUT));
    }
}
