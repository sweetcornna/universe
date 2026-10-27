package io.github.sweetcornna.universe.chem.reactor;

import java.util.List;

import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.blueprint.BlueprintSettings;
import io.github.sweetcornna.universe.core.chem.reaction.Catalyst;
import io.github.sweetcornna.universe.core.chem.reaction.ItemCount;
import io.github.sweetcornna.universe.core.chem.reaction.Reaction;
import io.github.sweetcornna.universe.core.chem.reaction.ReactorModel;
import io.github.sweetcornna.universe.core.chem.reaction.ReactorResult;
import io.github.sweetcornna.universe.core.chem.reaction.ReactorSettings;
import io.github.sweetcornna.universe.core.chem.reaction.YieldGrid;
import io.github.sweetcornna.universe.factory.MachineBlockEntity;
import io.github.sweetcornna.universe.factory.MachineMenu;
import io.github.sweetcornna.universe.factory.recipe.MachineRecipe;
import io.github.sweetcornna.universe.factory.research.Research;
import io.github.sweetcornna.universe.registry.UniverseBlockEntities;
import io.github.sweetcornna.universe.registry.UniverseMenus;
import io.github.sweetcornna.universe.stats.ProductionLog;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Prediction;
import net.minecraft.world.Containers;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * 化学反应釜（ADR-0036 §四）：一台做一条反应。玩家在界面上选反应、调温度和压强，核心层的 {@link ReactorModel}
 * 按工况、催化剂和星球气温算出每分钟做几批、用多少电；电不够时按满足率放慢。攒满一批、原料够、产物放得下时扣一批原料、
 * 出一批产物。没反应的原料留在釜里接着反应，所以转化率只管快慢。费托合成的油和甲烷按选择性分，零头攒到下一批。
 *
 * <p>格子：0–2 是原料（第 i 格放反应的第 i 种原料），3–5 是产物（第 3 + i 格放第 i 种产物，副产物排在最后），6 是催化剂。
 * 催化剂每做一批磨损一点，做满 {@link Catalyst#batches()} 批用掉一个；没放或放错了按 {@link Catalyst#UNCATALYZED} 算，慢几十倍。
 *
 * <p>做成一批的工况记进全服共用的 {@link ReactorKnowledge}，界面上的产量图读它。工况在服务端拉回范围、取整到 1 K 和 0.01 bar，
 * 和界面数据传的精度一样，界面上算的读数和服务端对得上。
 */
public class ChemicalReactorBlockEntity extends MachineBlockEntity implements MachineMenu.ButtonTarget, BlueprintSettings {
    public static final int INPUTS = 3;
    public static final int OUTPUTS = 3;
    public static final int CATALYST_SLOT = INPUTS + OUTPUTS;
    public static final int SLOTS = CATALYST_SLOT + 1;

    /** 选的反应：序号 + 1，没选是 0。 */
    public static final int REACTION = BASE_DATA;
    /** 设定温度（K）。 */
    public static final int TEMPERATURE = BASE_DATA + 1;
    /** 设定压强 × {@link #PRESSURE_SCALE}。 */
    public static final int PRESSURE = BASE_DATA + 2;
    public static final int STATUS = BASE_DATA + 3;
    /** 这一批做了多少，千分数。 */
    public static final int PROGRESS = BASE_DATA + 4;
    /** 星球气温（K）。 */
    public static final int AMBIENT = BASE_DATA + 5;
    /** 第 0 位：放了对的催化剂；第 1 位：放了催化剂但不对这条反应。 */
    public static final int CATALYST = BASE_DATA + 6;
    /** 现在这个催化剂还能做几批。 */
    public static final int WEAR = BASE_DATA + 7;
    /** 从这里起 {@link ReactorKnowledge#WORDS} 格：选的反应在产量图上跑过的格子。 */
    public static final int MEASURED = BASE_DATA + 8;
    public static final int DATA_COUNT = MEASURED + ReactorKnowledge.WORDS;

    /** 界面按钮：0 起是选反应（反应的序号），其余见下。 */
    public static final int DEFAULTS_BUTTON = 99;
    /** 设温度：加上开尔文数。 */
    public static final int TEMPERATURE_BUTTON = 1000;
    /** 设压强：加上 bar × {@link #PRESSURE_SCALE}。 */
    public static final int PRESSURE_BUTTON = 10_000;
    public static final int PRESSURE_SCALE = 100;

    public static final int RUNNING = 0;
    public static final int NO_REACTION = 1;
    public static final int NO_INPUT = 2;
    public static final int OUTPUT_FULL = 3;
    public static final int NO_POWER = 4;
    public static final int LOCKED = 5;

    /** 自动化最多往原料格里塞几批的量，催化剂最多塞几个。 */
    private static final int INPUT_BUFFER = 4;
    private static final int CATALYST_BUFFER = 4;
    /** 隔多久重新读一次星球气温（刻），星球改造了能跟上。 */
    private static final int CLIMATE_INTERVAL = 200;

    private @Nullable Reaction reaction;
    private ReactorSettings settings = new ReactorSettings(ReactorSettings.MIN_TEMPERATURE, ReactorSettings.MIN_PRESSURE);
    /** 这一批做了多少（0–1）。 */
    private double batches;
    /** 费托合成的油和甲烷没出满一个的零头。 */
    private double productFraction;
    private double byproductFraction;
    /** 催化剂格里那种催化剂已经做了几批。 */
    private int wear;
    private @Nullable Catalyst wearCatalyst;
    private boolean active;
    private double supplied;
    private long power;
    private int status = NO_REACTION;
    private double ambient = Double.NaN;

    public ChemicalReactorBlockEntity(BlockPos pos, BlockState state) {
        super(UniverseBlockEntities.CHEMICAL_REACTOR.get(), pos, state, SLOTS);
    }

    // ---- 反应和工况

    public @Nullable Reaction reaction() {
        return this.reaction;
    }

    public ReactorSettings settings() {
        return this.settings;
    }

    /** 选一条反应：工况回到教科书的数，做到一半的批数清零。 */
    public void setReaction(@Nullable Reaction reaction) {
        this.reaction = reaction;
        this.settings = reaction == null ? this.settings : reaction.defaults();
        this.batches = 0;
        this.productFraction = 0;
        this.byproductFraction = 0;
        this.active = false;
        this.setChanged();
    }

    public void setSettings(ReactorSettings settings) {
        this.settings = quantize(settings);
        this.setChanged();
    }

    /** 取整到界面数据传的精度：1 K、0.01 bar。 */
    static ReactorSettings quantize(ReactorSettings settings) {
        return new ReactorSettings(Math.round(settings.temperature()),
                (double) Math.round(settings.pressure() * PRESSURE_SCALE) / PRESSURE_SCALE);
    }

    /** 催化剂格里的催化剂；没放或放的不是催化剂为 {@code null}。 */
    public @Nullable Catalyst installed() {
        return ReactorRecipes.catalyst(this.getItem(CATALYST_SLOT));
    }

    /** 放了对这条反应的催化剂。 */
    public boolean catalyzed() {
        Catalyst catalyst = this.installed();
        return this.reaction != null && catalyst != null && catalyst.accepts(this.reaction);
    }

    /** 现在的催化剂已经做了几批。 */
    public int wear() {
        return this.installed() == this.wearCatalyst ? this.wear : 0;
    }

    /** 测试用：直接设现在这个催化剂做过的批数。 */
    public void setWear(int wear) {
        this.wearCatalyst = this.installed();
        this.wear = wear;
        this.setChanged();
    }

    /** 当前工况下的读数；没选反应为 {@code null}。 */
    public @Nullable ReactorResult result() {
        return this.reaction == null ? null : ReactorModel.evaluate(this.reaction, this.settings, this.catalyzed(), this.ambient());
    }

    private double ambient() {
        if (Double.isNaN(this.ambient) && this.level instanceof ServerLevel serverLevel) {
            this.ambient = Math.round(ReactorClimate.ambient(serverLevel));
        }
        return Double.isNaN(this.ambient) ? ReactorClimate.DEFAULT_AMBIENT : this.ambient;
    }

    /** 这条反应的配方；数据包里没有时为 {@code null}，反应釜就不做这条。 */
    private static @Nullable RecipeHolder<MachineRecipe> recipe(ServerLevel level, Reaction reaction) {
        RecipeHolder<?> holder = level.getServer().getRecipeManager().recipeMap().byKey(ReactorRecipes.key(reaction));
        if (holder != null && holder.value() instanceof MachineRecipe recipe && recipe.kind() == MachineRecipe.Kind.REACTOR) {
            @SuppressWarnings("unchecked")
            RecipeHolder<MachineRecipe> machineRecipe = (RecipeHolder<MachineRecipe>) holder;
            return machineRecipe;
        }
        return null;
    }

    private static boolean unlocked(ServerLevel level, Reaction reaction) {
        RecipeHolder<MachineRecipe> recipe = recipe(level, reaction);
        return recipe != null && Research.isUnlocked(level, recipe.value().research());
    }

    @Override
    public boolean clickButton(ServerPlayer player, int button) {
        if (!(this.level instanceof ServerLevel serverLevel)) {
            return false;
        }
        Reaction[] reactions = Reaction.values();
        if (button >= 0 && button < reactions.length) {
            Reaction picked = reactions[button];
            if (picked == this.reaction || !unlocked(serverLevel, picked)) {
                return false;
            }
            // 换反应时把原料格里的东西还给玩家
            for (int i = 0; i < INPUTS; i++) {
                ItemStack stack = this.removeItemNoUpdate(i);
                if (!stack.isEmpty()) {
                    player.getInventory().placeItemBackInInventory(stack, Prediction.SERVER_ONLY);
                }
            }
            this.setReaction(picked);
            return true;
        }
        if (this.reaction == null) {
            return false;
        }
        if (button == DEFAULTS_BUTTON) {
            this.setSettings(this.reaction.defaults());
        } else if (button >= PRESSURE_BUTTON) {
            this.setSettings(this.settings.withPressure((double) (button - PRESSURE_BUTTON) / PRESSURE_SCALE));
        } else if (button >= TEMPERATURE_BUTTON) {
            this.setSettings(this.settings.withTemperature(button - TEMPERATURE_BUTTON));
        } else {
            return false;
        }
        return true;
    }

    @Override
    public @Nullable CompoundTag blueprintSettings() {
        if (this.reaction == null) {
            return null;
        }
        CompoundTag tag = new CompoundTag();
        tag.putString("Reaction", this.reaction.name());
        tag.putDouble("Temperature", this.settings.temperature());
        tag.putDouble("Pressure", this.settings.pressure());
        return tag;
    }

    @Override
    public void applyBlueprintSettings(CompoundTag settings) {
        Reaction reaction = Reaction.byName(settings.getStringOr("Reaction", ""));
        this.setReaction(reaction);
        if (reaction != null) {
            this.setSettings(new ReactorSettings(settings.getDoubleOr("Temperature", reaction.defaults().temperature()),
                    settings.getDoubleOr("Pressure", reaction.defaults().pressure())));
        }
    }

    // ---- 电

    @Override
    public long demand() {
        return this.active ? this.power : 0;
    }

    @Override
    public void settle(double satisfaction, double load, long stored) {
        super.settle(satisfaction, load, stored);
        this.supplied = this.active ? satisfaction : 0;
    }

    // ---- 反应

    @Override
    protected boolean tickMachine() {
        double supplied = this.supplied;
        this.supplied = 0;
        if (!(this.level instanceof ServerLevel serverLevel)) {
            return false;
        }
        if (serverLevel.getGameTime() % CLIMATE_INTERVAL == 0) {
            this.ambient = Double.NaN;
        }
        Reaction reaction = this.reaction;
        RecipeHolder<MachineRecipe> recipe = reaction == null ? null : recipe(serverLevel, reaction);
        if (reaction == null || recipe == null) {
            return this.idle(NO_REACTION);
        }
        if (!Research.isUnlocked(serverLevel, recipe.value().research())) {
            return this.idle(LOCKED);
        }
        if (!this.hasInputs(reaction)) {
            return this.idle(NO_INPUT);
        }
        if (!this.outputsFit(reaction)) {
            return this.idle(OUTPUT_FULL);
        }
        ReactorResult result = ReactorModel.evaluate(reaction, this.settings, this.catalyzed(), this.ambient());
        this.power = Math.max(1, Math.round(result.power()));
        this.active = true;
        if (supplied <= 0) {
            this.status = NO_POWER;
            return false;
        }
        this.status = RUNNING;
        // 参考产量（ADR-0033 §四）：配方按单程转化率 1 写，满电时的速度是单程转化率倍
        ProductionLog.reference(serverLevel, this.worldPosition, recipe, result.singlePass(), 1);
        this.batches += result.batchesPerMinute() / ReactorRecipes.TICKS_PER_MINUTE * supplied;
        if (this.batches >= 1) {
            this.batches -= 1;
            this.react(serverLevel, reaction, result);
        }
        this.setChanged();
        return true;
    }

    /** 停工：做到一半的留着。 */
    private boolean idle(int status) {
        this.status = status;
        this.active = false;
        return false;
    }

    private boolean hasInputs(Reaction reaction) {
        List<ItemCount> inputs = reaction.inputs();
        for (int i = 0; i < inputs.size(); i++) {
            ItemStack stack = this.getItem(i);
            if (!stack.is(ReactorRecipes.item(inputs.get(i))) || stack.getCount() < inputs.get(i).count()) {
                return false;
            }
        }
        return true;
    }

    /** 每样产物按一批的整数量留出地方；油和甲烷的零头不会让一批多出整数量。 */
    private boolean outputsFit(Reaction reaction) {
        List<ItemCount> outputs = reaction.outputs();
        for (int i = 0; i < outputs.size(); i++) {
            if (!this.room(INPUTS + i, outputs.get(i))) {
                return false;
            }
        }
        return reaction.byproduct() == null || this.room(INPUTS + outputs.size(), reaction.byproduct());
    }

    private boolean room(int slot, ItemCount output) {
        ItemStack current = this.getItem(slot);
        ItemStack result = new ItemStack(ReactorRecipes.item(output));
        return current.isEmpty() || ItemStack.isSameItemSameComponents(current, result)
                && current.getCount() + output.count() <= current.getMaxStackSize();
    }

    private void react(ServerLevel level, Reaction reaction, ReactorResult result) {
        List<ItemCount> inputs = reaction.inputs();
        for (int i = 0; i < inputs.size(); i++) {
            int count = inputs.get(i).count();
            ProductionLog.consumed(level, this.getItem(i).getItem(), count);
            this.consumeInput(i, count);
        }
        List<ItemCount> outputs = reaction.outputs();
        ItemCount byproduct = reaction.byproduct();
        for (int i = 0; i < outputs.size(); i++) {
            int count = outputs.get(i).count();
            if (i == 0 && byproduct != null) {
                this.productFraction += count * (1 - result.methaneSelectivity());
                count = (int) this.productFraction;
                this.productFraction -= count;
            }
            this.produce(level, INPUTS + i, ReactorRecipes.item(outputs.get(i)), count);
        }
        if (byproduct != null) {
            this.byproductFraction += byproduct.count() * result.methaneSelectivity();
            int count = (int) this.byproductFraction;
            this.byproductFraction -= count;
            this.produce(level, INPUTS + outputs.size(), ReactorRecipes.item(byproduct), count);
        }
        if (result.catalyzed()) {
            this.wearCatalyst();
        }
        ReactorKnowledge.get(level.getServer()).mark(reaction, YieldGrid.cell(this.settings).index());
    }

    private void produce(ServerLevel level, int slot, Item item, int count) {
        if (count <= 0) {
            return;
        }
        ItemStack stack = new ItemStack(item, count);
        ProductionLog.produced(level, stack);
        int left = this.addOutput(slot, stack);
        if (left > 0) {
            Containers.dropItemStack(level, this.worldPosition.getX(), this.worldPosition.getY() + 1, this.worldPosition.getZ(),
                    stack.copyWithCount(left));
        }
    }

    private void wearCatalyst() {
        Catalyst catalyst = this.installed();
        if (catalyst == null) {
            return;
        }
        if (catalyst != this.wearCatalyst) {
            this.wearCatalyst = catalyst;
            this.wear = 0;
        }
        this.wear++;
        if (this.wear >= catalyst.batches()) {
            this.getItem(CATALYST_SLOT).shrink(1);
            this.wear = 0;
        }
    }

    // ---- 物品栏

    @Override
    protected boolean isInput(int slot) {
        return slot < INPUTS || slot == CATALYST_SLOT;
    }

    @Override
    protected boolean isOutput(int slot) {
        return slot >= INPUTS && slot < INPUTS + OUTPUTS;
    }

    private @Nullable ItemCount input(int slot) {
        if (this.reaction == null || slot >= INPUTS || slot >= this.reaction.inputs().size()) {
            return null;
        }
        return this.reaction.inputs().get(slot);
    }

    /** 原料格只收这条反应对应的那种原料；催化剂格收哪种催化剂都行（放错了界面上会说）。 */
    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        if (slot == CATALYST_SLOT) {
            return ReactorRecipes.catalyst(stack) != null;
        }
        ItemCount input = this.input(slot);
        return input != null && stack.is(ReactorRecipes.item(input));
    }

    /** 自动化只塞到几批的量，催化剂只塞对的那种。 */
    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction side) {
        if (slot == CATALYST_SLOT) {
            Catalyst catalyst = ReactorRecipes.catalyst(stack);
            return catalyst != null && this.reaction != null && catalyst.accepts(this.reaction)
                    && this.getItem(slot).getCount() < CATALYST_BUFFER;
        }
        ItemCount input = this.input(slot);
        return input != null && stack.is(ReactorRecipes.item(input))
                && this.getItem(slot).getCount() < Math.min(stack.getMaxStackSize(), input.count() * INPUT_BUFFER);
    }

    /** 产物能抽走；换反应后原料格里剩下的、催化剂格里放错的也能抽走。 */
    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        if (this.isOutput(slot)) {
            return true;
        }
        if (slot == CATALYST_SLOT) {
            Catalyst catalyst = ReactorRecipes.catalyst(stack);
            return catalyst == null || this.reaction == null || !catalyst.accepts(this.reaction);
        }
        ItemCount input = this.input(slot);
        return input == null || !stack.is(ReactorRecipes.item(input));
    }

    // ---- 界面

    @Override
    protected MachineMenu.Type menuType() {
        return UniverseMenus.CHEMICAL_REACTOR;
    }

    @Override
    protected int dataCount() {
        return DATA_COUNT;
    }

    @Override
    protected int dataValue(int index) {
        if (index >= MEASURED && index < MEASURED + ReactorKnowledge.WORDS) {
            if (this.reaction == null || !(this.level instanceof ServerLevel serverLevel)) {
                return 0;
            }
            return ReactorKnowledge.get(serverLevel.getServer()).word(this.reaction, index - MEASURED);
        }
        return switch (index) {
            case REACTION -> this.reaction == null ? 0 : this.reaction.ordinal() + 1;
            case TEMPERATURE -> (int) Math.round(this.settings.temperature());
            case PRESSURE -> (int) Math.round(this.settings.pressure() * PRESSURE_SCALE);
            case STATUS -> this.status;
            case PROGRESS -> (int) Math.min(1000, Math.round(this.batches * 1000));
            case AMBIENT -> (int) Math.round(this.ambient());
            case CATALYST -> {
                Catalyst catalyst = this.installed();
                yield catalyst == null ? 0 : this.catalyzed() ? 1 : 2;
            }
            case WEAR -> {
                Catalyst catalyst = this.installed();
                yield catalyst == null ? 0 : catalyst.batches() - this.wear();
            }
            default -> super.dataValue(index);
        };
    }

    // ---- 存档

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.reaction = Reaction.byName(input.getStringOr("Reaction", ""));
        ReactorSettings defaults = this.reaction == null ? this.settings : this.reaction.defaults();
        this.settings = quantize(new ReactorSettings(input.getDoubleOr("Temperature", defaults.temperature()),
                input.getDoubleOr("Pressure", defaults.pressure())));
        this.batches = input.getDoubleOr("Batches", 0);
        this.productFraction = input.getDoubleOr("ProductFraction", 0);
        this.byproductFraction = input.getDoubleOr("ByproductFraction", 0);
        this.wear = input.getIntOr("Wear", 0);
        String worn = input.getStringOr("WearCatalyst", "");
        this.wearCatalyst = null;
        for (Catalyst catalyst : Catalyst.values()) {
            if (catalyst.name().equals(worn)) {
                this.wearCatalyst = catalyst;
            }
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (this.reaction != null) {
            output.putString("Reaction", this.reaction.name());
        }
        output.putDouble("Temperature", this.settings.temperature());
        output.putDouble("Pressure", this.settings.pressure());
        output.putDouble("Batches", this.batches);
        output.putDouble("ProductFraction", this.productFraction);
        output.putDouble("ByproductFraction", this.byproductFraction);
        output.putInt("Wear", this.wear);
        if (this.wearCatalyst != null) {
            output.putString("WearCatalyst", this.wearCatalyst.name());
        }
    }
}
