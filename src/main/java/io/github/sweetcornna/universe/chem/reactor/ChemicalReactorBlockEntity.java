package io.github.sweetcornna.universe.chem.reactor;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.blueprint.BlueprintSettings;
import io.github.sweetcornna.universe.core.chem.reaction.Catalyst;
import io.github.sweetcornna.universe.core.chem.reaction.ItemCount;
import io.github.sweetcornna.universe.core.chem.reaction.Reaction;
import io.github.sweetcornna.universe.core.chem.reaction.ReactionMatcher;
import io.github.sweetcornna.universe.core.chem.reaction.ReactorModel;
import io.github.sweetcornna.universe.core.chem.reaction.ReactorResult;
import io.github.sweetcornna.universe.core.chem.reaction.ReactorSettings;
import io.github.sweetcornna.universe.core.chem.reaction.Species;
import io.github.sweetcornna.universe.core.chem.reaction.YieldGrid;
import io.github.sweetcornna.universe.factory.MachineBlockEntity;
import io.github.sweetcornna.universe.factory.MachineMenu;
import io.github.sweetcornna.universe.factory.recipe.MachineRecipe;
import io.github.sweetcornna.universe.registry.UniverseBlockEntities;
import io.github.sweetcornna.universe.registry.UniverseMenus;
import io.github.sweetcornna.universe.stats.ProductionLog;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
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
 * 化学反应釜（ADR-0036 §四、ADR-0037）。两种用法：
 *
 * <ul>
 *   <li><b>自由投料</b>（没选反应）：原料格放反应物，随便哪格，釜按原料的种类和催化剂认反应（{@link ReactionMatcher}），
 *       认出来就照玩家调的工况做。第一次做成一批，这条反应就发现了（{@link ReactionBooks}）。自动化不往里塞也不往外抽原料。</li>
 *   <li><b>选定配方</b>：选一条发现了的反应，工况回到教科书的数，第 i 格只收第 i 种反应物，漏斗、分拣器能往里送。</li>
 * </ul>
 *
 * <p>核心层的 {@link ReactorModel} 按工况、催化剂和星球气温算出每分钟做几批、用多少电；电不够时按满足率放慢。攒满一批、
 * 原料够、产物放得下时扣一批原料、出一批产物，物品数就是反应式的系数。没反应的原料留在釜里接着反应，所以转化率只管快慢。
 *
 * <p>格子：0–2 是原料，3–5 是产物（第 3 + i 格放第 i 种产物），6 是催化剂。催化剂每做一批磨损一点，做满
 * {@link Catalyst#batches()} 批用掉一个；没放或放错了按 {@link Catalyst#UNCATALYZED} 算，慢几十倍。不用催化剂的反应不看催化剂格。
 *
 * <p>做成一批的工况记进全服共用的 {@link ReactorKnowledge}，界面上的产量图读它。工况在服务端拉回范围、取整到 1 K 和 0.01 bar，
 * 和界面数据传的精度一样，界面上算的读数和服务端对得上。
 */
public class ChemicalReactorBlockEntity extends MachineBlockEntity implements MachineMenu.ButtonTarget, BlueprintSettings {
    public static final int INPUTS = 3;
    public static final int OUTPUTS = 3;
    public static final int CATALYST_SLOT = INPUTS + OUTPUTS;
    public static final int SLOTS = CATALYST_SLOT + 1;

    /** 选的反应：序号 + 1，自由投料是 0。 */
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
    /** 催化剂格：{@link #CATALYST_EMPTY} 等。 */
    public static final int CATALYST = BASE_DATA + 6;
    /** 现在这个催化剂还能做几批。 */
    public static final int WEAR = BASE_DATA + 7;
    /** 正在做的反应：选定的，或者自由投料认出来的；序号 + 1，没有是 0。 */
    public static final int ACTIVE = BASE_DATA + 8;
    /** 自由投料时反应物种类对得上的有几条。 */
    public static final int CANDIDATES = BASE_DATA + 9;
    /** 从这里起 {@link ReactorKnowledge#WORDS} 格：正在做的反应在产量图上跑过的格子。 */
    public static final int MEASURED = BASE_DATA + 10;
    public static final int DATA_COUNT = MEASURED + ReactorKnowledge.WORDS;

    /** 界面按钮：0 起是选反应（反应的序号），其余见下。 */
    public static final int FREE_BUTTON = 98;
    /** 正在做的反应回到教科书工况。 */
    public static final int DEFAULTS_BUTTON = 99;
    /** 设温度：加上开尔文数。 */
    public static final int TEMPERATURE_BUTTON = 1000;
    /** 设压强：加上 bar × {@link #PRESSURE_SCALE}。 */
    public static final int PRESSURE_BUTTON = 10_000;
    public static final int PRESSURE_SCALE = 100;

    public static final int RUNNING = 0;
    /** 数据包里没有这条反应的配方。 */
    public static final int NO_REACTION = 1;
    public static final int NO_INPUT = 2;
    public static final int OUTPUT_FULL = 3;
    public static final int NO_POWER = 4;
    /** 选的反应还没发现（蓝图带过来的）。 */
    public static final int LOCKED = 5;
    /** 自由投料：认不出这组原料。 */
    public static final int UNKNOWN = 6;
    /** 自由投料：对得上好几条，催化剂挑不出来。 */
    public static final int AMBIGUOUS = 7;
    /** 自由投料：原料格是空的。 */
    public static final int EMPTY = 8;

    public static final int CATALYST_EMPTY = 0;
    public static final int CATALYST_RIGHT = 1;
    public static final int CATALYST_WRONG = 2;
    /** 这条反应不用催化剂。 */
    public static final int CATALYST_UNNEEDED = 3;
    /** 自由投料还没认出反应。 */
    public static final int CATALYST_IDLE = 4;

    /** 存档格式：2 起分自由投料和选定配方（ADR-0037）。 */
    private static final int FORMAT = 2;

    /** 自动化最多往原料格里塞几批的量，催化剂最多塞几个。 */
    private static final int INPUT_BUFFER = 4;
    private static final int CATALYST_BUFFER = 4;
    /** 隔多久重新读一次星球气温（刻），星球改造了能跟上。 */
    private static final int CLIMATE_INTERVAL = 200;

    /** 选的反应；自由投料为 {@code null}。 */
    private @Nullable Reaction reaction;
    /** 自由投料认出来的反应，每刻重新认。 */
    private @Nullable Reaction matched;
    private int candidates;
    /** 老存档里选着反应的釜，第一刻把它记成发现了。 */
    private boolean migrate;
    private ReactorSettings settings = new ReactorSettings(ReactorSettings.MIN_TEMPERATURE, ReactorSettings.MIN_PRESSURE);
    /** 这一批做了多少（0–1）。 */
    private double batches;
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

    /** 选的反应；自由投料为 {@code null}。 */
    public @Nullable Reaction reaction() {
        return this.reaction;
    }

    /** 正在做的反应：选定的，或者自由投料认出来的。 */
    public @Nullable Reaction active() {
        return this.reaction != null ? this.reaction : this.matched;
    }

    public boolean freeMode() {
        return this.reaction == null;
    }

    public ReactorSettings settings() {
        return this.settings;
    }

    /** 选一条反应（{@code null} 是自由投料）：工况回到教科书的数，做到一半的批数清零。 */
    public void setReaction(@Nullable Reaction reaction) {
        this.reaction = reaction;
        this.matched = null;
        this.candidates = 0;
        this.settings = reaction == null ? this.settings : reaction.defaults();
        this.batches = 0;
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

    /** 正在做的反应放了对的催化剂，或者它不用催化剂。 */
    public boolean catalyzed() {
        Reaction active = this.active();
        return active != null && ReactionMatcher.catalyzed(active, this.installed());
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

    /** 当前工况下的读数；没有正在做的反应为 {@code null}。 */
    public @Nullable ReactorResult result() {
        Reaction active = this.active();
        return active == null ? null : ReactorModel.evaluate(active, this.settings, this.catalyzed(), this.ambient());
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

    @Override
    public boolean clickButton(ServerPlayer player, int button) {
        if (!(this.level instanceof ServerLevel serverLevel)) {
            return false;
        }
        Reaction[] reactions = Reaction.values();
        if (button >= 0 && button < reactions.length) {
            Reaction picked = reactions[button];
            if (picked == this.reaction || !ReactionBooks.book(serverLevel.getServer()).discovered(picked)) {
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
        if (button == FREE_BUTTON) {
            // 原料留着：自由投料什么都收
            if (this.reaction == null) {
                return false;
            }
            this.setReaction(null);
            return true;
        }
        if (button == DEFAULTS_BUTTON) {
            Reaction active = this.active();
            if (active == null) {
                return false;
            }
            this.setSettings(active.defaults());
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
        CompoundTag tag = new CompoundTag();
        if (this.reaction != null) {
            tag.putString("Reaction", this.reaction.name());
        }
        tag.putDouble("Temperature", this.settings.temperature());
        tag.putDouble("Pressure", this.settings.pressure());
        return tag;
    }

    /** 蓝图带过来的反应在这个世界还没发现时照样选上，界面上写未发现，不做。 */
    @Override
    public void applyBlueprintSettings(CompoundTag settings) {
        Reaction reaction = Reaction.byName(settings.getStringOr("Reaction", ""));
        this.setReaction(reaction);
        this.setSettings(new ReactorSettings(settings.getDoubleOr("Temperature", this.settings.temperature()),
                settings.getDoubleOr("Pressure", this.settings.pressure())));
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
        if (this.migrate) {
            this.migrate = false;
            if (this.reaction != null) {
                ReactionBooks.discover(serverLevel.getServer(), this.reaction, false);
            }
        }
        Reaction reaction = this.reaction;
        if (reaction == null) {
            reaction = this.match();
            if (reaction == null) {
                return this.idle(this.candidates > 1 ? AMBIGUOUS : this.inputKinds().isEmpty() ? EMPTY : UNKNOWN);
            }
        } else if (!ReactionBooks.book(serverLevel.getServer()).discovered(reaction)) {
            return this.idle(LOCKED);
        }
        RecipeHolder<MachineRecipe> recipe = recipe(serverLevel, reaction);
        if (recipe == null) {
            return this.idle(NO_REACTION);
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

    /** 自由投料：按原料和催化剂认反应。认出的反应变了，做到一半的批数清零。 */
    private @Nullable Reaction match() {
        ReactionMatcher.Match match = ReactionMatcher.match(this.inputKinds(), this.installed());
        if (match.reaction() != this.matched) {
            this.matched = match.reaction();
            this.batches = 0;
            this.setChanged();
        }
        this.candidates = match.candidates();
        return this.matched;
    }

    /** 原料格里有哪几种物品（注册名）。 */
    private List<String> inputKinds() {
        List<String> kinds = new ArrayList<>(INPUTS);
        for (int i = 0; i < INPUTS; i++) {
            ItemStack stack = this.getItem(i);
            if (!stack.isEmpty()) {
                kinds.add(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
            }
        }
        return kinds;
    }

    /** 选定配方时第 i 格放第 i 种反应物；自由投料时同一种可以分在几格里，加起来够就行。 */
    private boolean hasInputs(Reaction reaction) {
        List<ItemCount> inputs = reaction.inputs();
        for (int i = 0; i < inputs.size(); i++) {
            ItemCount input = inputs.get(i);
            if (this.reaction == null) {
                if (this.count(ReactorRecipes.item(input)) < input.count()) {
                    return false;
                }
                continue;
            }
            ItemStack stack = this.getItem(i);
            if (!stack.is(ReactorRecipes.item(input)) || stack.getCount() < input.count()) {
                return false;
            }
        }
        return true;
    }

    /** 原料格里这种物品一共几个。 */
    private int count(Item item) {
        int count = 0;
        for (int i = 0; i < INPUTS; i++) {
            ItemStack stack = this.getItem(i);
            if (stack.is(item)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    /** 每样产物按一批的量留出地方。 */
    private boolean outputsFit(Reaction reaction) {
        List<ItemCount> outputs = reaction.outputs();
        for (int i = 0; i < outputs.size(); i++) {
            if (!this.room(INPUTS + i, outputs.get(i))) {
                return false;
            }
        }
        return true;
    }

    private boolean room(int slot, ItemCount output) {
        ItemStack current = this.getItem(slot);
        ItemStack result = new ItemStack(ReactorRecipes.item(output));
        return current.isEmpty() || ItemStack.isSameItemSameComponents(current, result)
                && current.getCount() + output.count() <= current.getMaxStackSize();
    }

    private void react(ServerLevel level, Reaction reaction, ReactorResult result) {
        for (ItemCount input : reaction.inputs()) {
            Item item = ReactorRecipes.item(input);
            ProductionLog.consumed(level, item, input.count());
            int left = input.count();
            for (int i = 0; i < INPUTS && left > 0; i++) {
                ItemStack stack = this.getItem(i);
                if (stack.is(item)) {
                    int n = Math.min(left, stack.getCount());
                    this.consumeInput(i, n);
                    left -= n;
                }
            }
        }
        List<ItemCount> outputs = reaction.outputs();
        for (int i = 0; i < outputs.size(); i++) {
            this.produce(level, INPUTS + i, ReactorRecipes.item(outputs.get(i)), outputs.get(i).count());
        }
        if (reaction.catalyst() != null && result.catalyzed()) {
            this.wearCatalyst();
        }
        ReactorKnowledge.get(level.getServer()).mark(reaction, YieldGrid.cell(this.settings).index());
        if (this.reaction == null) {
            ReactionBooks.discover(level.getServer(), reaction, true);
        }
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

    /** 做一批磨损一点，做满用掉一个。 */
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

    /**
     * 选定配方时原料格只收这条反应对应的那种原料；自由投料时收哪种反应物都行（{@link Species} 里有的）。
     * 催化剂格收哪种催化剂都行（放错了界面上会说）。
     */
    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        if (slot == CATALYST_SLOT) {
            return ReactorRecipes.catalyst(stack) != null;
        }
        if (slot >= INPUTS) {
            return false;
        }
        if (this.reaction == null) {
            return Species.byItem(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString()) != null;
        }
        ItemCount input = this.input(slot);
        return input != null && stack.is(ReactorRecipes.item(input));
    }

    /** 自动化只在选定配方时往里送：原料只塞到几批的量，催化剂只塞对的那种。 */
    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction side) {
        if (this.reaction == null) {
            return false;
        }
        if (slot == CATALYST_SLOT) {
            Catalyst catalyst = ReactorRecipes.catalyst(stack);
            return catalyst != null && catalyst.accepts(this.reaction) && this.getItem(slot).getCount() < CATALYST_BUFFER;
        }
        ItemCount input = this.input(slot);
        return input != null && stack.is(ReactorRecipes.item(input))
                && this.getItem(slot).getCount() < Math.min(stack.getMaxStackSize(), input.count() * INPUT_BUFFER);
    }

    /** 产物能抽走；选定配方时，换反应后原料格里剩下的、催化剂格里放错的也能抽走。自由投料时原料和催化剂不往外抽。 */
    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        if (this.isOutput(slot)) {
            return true;
        }
        if (this.reaction == null) {
            return false;
        }
        if (slot == CATALYST_SLOT) {
            Catalyst catalyst = ReactorRecipes.catalyst(stack);
            return catalyst == null || !catalyst.accepts(this.reaction);
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
            Reaction active = this.active();
            if (active == null || !(this.level instanceof ServerLevel serverLevel)) {
                return 0;
            }
            return ReactorKnowledge.get(serverLevel.getServer()).word(active, index - MEASURED);
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
                Reaction active = this.active();
                yield catalyst == null ? CATALYST_EMPTY : active == null ? CATALYST_IDLE : active.catalyst() == null ? CATALYST_UNNEEDED
                        : catalyst.accepts(active) ? CATALYST_RIGHT : CATALYST_WRONG;
            }
            case WEAR -> {
                Catalyst catalyst = this.installed();
                yield catalyst == null ? 0 : catalyst.batches() - this.wear();
            }
            case ACTIVE -> this.active() == null ? 0 : this.active().ordinal() + 1;
            case CANDIDATES -> this.candidates;
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
        this.migrate = input.getIntOr("Format", 1) < FORMAT;
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
        output.putInt("Format", FORMAT);
        output.putInt("Wear", this.wear);
        if (this.wearCatalyst != null) {
            output.putString("WearCatalyst", this.wearCatalyst.name());
        }
    }
}
