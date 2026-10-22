package io.github.sweetcornna.universe.factory;

import java.util.List;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.blueprint.BlueprintSettings;
import io.github.sweetcornna.universe.core.factory.Proliferation;
import io.github.sweetcornna.universe.factory.recipe.MachineRecipe;
import io.github.sweetcornna.universe.factory.recipe.MachineRecipes;
import io.github.sweetcornna.universe.factory.research.Research;
import io.github.sweetcornna.universe.stats.ProductionLog;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Prediction;
import net.minecraft.world.Containers;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.common.crafting.SizedIngredient;

/**
 * 按选定配方加工的设备（ADR-0011）：电弧熔炉、制造台、精炼厂共用。玩家在界面上选配方，第 i 个原料格只放第 i 种原料；
 * 原料够、产物放得下时向电网要电，按 速度 × 满足率 推进，做完一次扣原料、出产物。
 *
 * <p>格子：前 {@link Spec#inputs()} 个是原料，后面是产物。存档里记下原料格数；原料格变多了（M8 把熔炉改成三格），
 * 读档时把产物挪到新的位置。
 *
 * <p>增产剂（ADR-0022）：这一批原料的等级取各原料平均点数的最低值。界面上选“额外产出”时每做一次按等级攒额外进度，
 * 攒满多出一份产物；选“加速”时做得更快。两种都按等级多耗电。建筑和设备的配方不能额外产出，只能加速。
 */
public class RecipeMachineBlockEntity extends MachineBlockEntity implements MachineMenu.ButtonTarget, BlueprintSettings {
    public static final int SELECTED = BASE_DATA;
    public static final int PROGRESS = BASE_DATA + 1;
    public static final int STATUS = BASE_DATA + 2;
    /** 增产：第 0 位是加速模式，第 1–3 位是这一批原料的等级，第 4 位表示这个配方能额外产出。 */
    public static final int PROLIFERATION = BASE_DATA + 3;
    public static final int DATA_COUNT = BASE_DATA + 4;
    /** 界面上切换“额外产出”和“加速”的按钮（配方清单的按钮是 0 开始的序号）。 */
    public static final int MODE_BUTTON = 1000;

    /** 状态（界面上显示）。 */
    public static final int RUNNING = 0;
    public static final int NO_RECIPE = 1;
    public static final int NO_INPUT = 2;
    public static final int OUTPUT_FULL = 3;
    public static final int NO_POWER = 4;
    public static final int LOCKED = 5;
    /** 烧燃料的设备（ADR-0026）没有燃料了。 */
    public static final int NO_FUEL = 6;

    /** 自动化最多往原料格里塞几次配方的量。 */
    private static final int INPUT_BUFFER = 4;

    private final Spec spec;
    private @Nullable ResourceKey<Recipe<?>> selected;
    /** 已经做了多少刻（按标准时间算）。 */
    private double progress;
    /** 正在做（原料够、产物放得下），向电网要电。 */
    private boolean active;
    /** 这一刻电网给的满足率；没要电或没接上时是 0。 */
    private double supplied;
    private int status = NO_RECIPE;
    private Proliferation.Mode mode = Proliferation.Mode.EXTRA_PRODUCTS;
    private final Proliferation.ExtraProgress extra = new Proliferation.ExtraProgress();
    /** 这一批原料的增产等级（界面和耗电用）。 */
    private int batchLevel;

    /**
     * 一种配方设备。
     *
     * @param kind    用哪类配方
     * @param inputs  原料格数
     * @param outputs 产物格数
     * @param power   工作时的功率（焦耳/刻）
     * @param speed   速度倍率
     * @param menu    界面
     */
    public record Spec(MachineRecipe.Kind kind, int inputs, int outputs, long power, double speed, MachineMenu.Type menu) {
    }

    public RecipeMachineBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, Spec spec) {
        this(type, pos, state, spec, 0);
    }

    /** 子类在产物格后面另加 {@code extraSlots} 个格子（比如燃料格）。 */
    protected RecipeMachineBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, Spec spec, int extraSlots) {
        super(type, pos, state, spec.inputs() + spec.outputs() + extraSlots);
        this.spec = spec;
    }

    public Spec spec() {
        return this.spec;
    }

    // ---- 配方

    /** 选定的配方；没选、配方已不存在或类型不对时为空。只在服务端可用。 */
    @Override
    public @Nullable CompoundTag blueprintSettings() {
        if (this.selected == null) {
            return null;
        }
        CompoundTag tag = new CompoundTag();
        tag.putString("Recipe", this.selected.identifier().toString());
        tag.putBoolean("Speedup", this.mode == Proliferation.Mode.SPEEDUP);
        return tag;
    }

    @Override
    public void applyBlueprintSettings(CompoundTag settings) {
        Identifier id = Identifier.tryParse(settings.getStringOr("Recipe", ""));
        this.selected = id == null ? null : ResourceKey.create(Registries.RECIPE, id);
        this.mode = settings.getBooleanOr("Speedup", false) ? Proliferation.Mode.SPEEDUP : Proliferation.Mode.EXTRA_PRODUCTS;
        this.setChanged();
    }

    public @Nullable RecipeHolder<MachineRecipe> recipe() {
        if (this.selected == null || !(this.level instanceof ServerLevel serverLevel)) {
            return null;
        }
        RecipeHolder<?> holder = serverLevel.getServer().getRecipeManager().recipeMap().byKey(this.selected);
        if (holder != null && holder.value() instanceof MachineRecipe recipe && recipe.kind() == this.spec.kind()) {
            @SuppressWarnings("unchecked")
            RecipeHolder<MachineRecipe> machineRecipe = (RecipeHolder<MachineRecipe>) holder;
            return machineRecipe;
        }
        return null;
    }

    /** 界面上点了配方清单里的第几个。换配方时把原料格里的东西还给玩家。 */
    @Override
    public boolean clickButton(ServerPlayer player, int button) {
        if (this.level == null) {
            return false;
        }
        if (button == MODE_BUTTON) {
            this.mode = this.mode.next();
            this.setChanged();
            return true;
        }
        List<RecipeHolder<MachineRecipe>> recipes = MachineRecipes.list(this.level, this.spec.kind());
        if (button < 0 || button >= recipes.size()) {
            return false;
        }
        RecipeHolder<MachineRecipe> holder = recipes.get(button);
        if (!Research.isUnlocked(this.level, holder.value().research()) || Objects.equals(holder.id(), this.selected)) {
            return false;
        }
        for (int i = 0; i < this.spec.inputs(); i++) {
            ItemStack stack = this.removeItemNoUpdate(i);
            if (!stack.isEmpty()) {
                player.getInventory().placeItemBackInInventory(stack, Prediction.SERVER_ONLY);
            }
        }
        this.selected = holder.id();
        this.progress = 0;
        this.active = false;
        this.setChanged();
        return true;
    }

    // ---- 电

    @Override
    public long demand() {
        return this.active ? Math.round(this.spec.power() * Proliferation.powerFactor(this.batchLevel)) : 0;
    }

    @Override
    public void settle(double satisfaction, double load, long stored) {
        super.settle(satisfaction, load, stored);
        // 没要电的那一刻满足率也可能是 1（电网没有负载），不能算数
        this.supplied = this.active ? satisfaction : 0;
    }

    // ---- 加工

    @Override
    protected boolean tickMachine() {
        double supplied = this.supplied;
        this.supplied = 0;
        RecipeHolder<MachineRecipe> holder = this.recipe();
        if (holder == null) {
            return this.idle(NO_RECIPE);
        }
        MachineRecipe recipe = holder.value();
        if (!Research.isUnlocked(this.level, recipe.research())) {
            return this.idle(LOCKED);
        }
        if (!this.hasInputs(recipe)) {
            return this.idle(NO_INPUT);
        }
        this.batchLevel = this.batchLevel(recipe);
        Proliferation.Mode mode = this.effectiveMode(recipe);
        if (!this.outputsFit(recipe, mode == Proliferation.Mode.EXTRA_PRODUCTS && this.batchLevel > 0)) {
            return this.idle(OUTPUT_FULL);
        }
        this.active = true;
        double rate = this.workRate(supplied);
        if (rate <= 0) {
            this.status = this.starvedStatus();
            return false;
        }
        this.status = RUNNING;
        this.progress += this.spec.speed() * rate * Proliferation.speedFactor(mode, this.batchLevel);
        if (this.progress >= recipe.time()) {
            this.progress = 0;
            this.craft(recipe, mode);
        }
        this.setChanged();
        return true;
    }

    /**
     * 这一刻按多快干活（0–1）。原料齐了、产物放得下才会问。默认是电网的满足率；烧燃料的设备改成看炉膛。
     *
     * @param supplied 电网这一刻给的满足率
     */
    protected double workRate(double supplied) {
        return supplied;
    }

    /** {@link #workRate} 是 0 时显示的状态。 */
    protected int starvedStatus() {
        return NO_POWER;
    }

    /** 停工：做到一半的进度留着，原料补上了接着做。 */
    private boolean idle(int status) {
        this.status = status;
        this.active = false;
        return false;
    }

    /** 这一批原料的等级：每种原料的平均点数，取最低的。 */
    private int batchLevel(MachineRecipe recipe) {
        int level = Proliferation.MAX_LEVEL;
        for (int i = 0; i < recipe.ingredients().size(); i++) {
            level = Math.min(level, this.inputLevel(i));
        }
        return recipe.ingredients().isEmpty() ? 0 : level;
    }

    /** 能额外产出的配方按界面上选的；建筑和设备只能加速。 */
    private Proliferation.Mode effectiveMode(MachineRecipe recipe) {
        return productive(recipe) ? this.mode : Proliferation.Mode.SPEEDUP;
    }

    /** 配方能不能额外产出：产物是本模组的方块（建筑、设备）时不能，和戴森球计划一样。 */
    public static boolean productive(MachineRecipe recipe) {
        for (ItemStackTemplate result : recipe.results()) {
            ItemStack stack = result.create();
            if (stack.getItem() instanceof BlockItem && BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace().equals(Universe.MOD_ID)) {
                return false;
            }
        }
        return true;
    }

    public Proliferation.Mode mode() {
        return this.mode;
    }

    private boolean hasInputs(MachineRecipe recipe) {
        List<SizedIngredient> ingredients = recipe.ingredients();
        if (ingredients.size() > this.spec.inputs()) {
            return false;
        }
        for (int i = 0; i < ingredients.size(); i++) {
            if (!ingredients.get(i).test(this.getItem(i))) {
                return false;
            }
        }
        return true;
    }

    /** 产物放不放得下；可能额外产出时按两份算。 */
    private boolean outputsFit(MachineRecipe recipe, boolean mayDouble) {
        List<ItemStackTemplate> results = recipe.results();
        if (results.size() > this.spec.outputs()) {
            return false;
        }
        for (int i = 0; i < results.size(); i++) {
            ItemStack current = this.getItem(this.spec.inputs() + i);
            ItemStack result = results.get(i).create();
            int count = result.getCount() * (mayDouble ? 2 : 1);
            if (!current.isEmpty() && (!ItemStack.isSameItemSameComponents(current, result)
                    || current.getCount() + count > current.getMaxStackSize())) {
                return false;
            }
        }
        return true;
    }

    private void craft(MachineRecipe recipe, Proliferation.Mode mode) {
        List<SizedIngredient> ingredients = recipe.ingredients();
        int level = ingredients.isEmpty() ? 0 : Proliferation.MAX_LEVEL;
        for (int i = 0; i < ingredients.size(); i++) {
            ItemStack input = this.getItem(i);
            ItemStackTemplate remainder = input.getCraftingRemainder();
            int count = ingredients.get(i).count();
            ProductionLog.consumed(this.level, input.getItem(), count);
            level = Math.min(level, this.consumeInput(i, count));
            if (remainder != null) {
                this.leaveRemainder(i, remainder.create().copyWithCount(remainder.count() * count));
            }
        }
        int copies = 1 + (mode == Proliferation.Mode.EXTRA_PRODUCTS ? this.extra.craft(level) : 0);
        List<ItemStackTemplate> results = recipe.results();
        for (int i = 0; i < results.size(); i++) {
            int slot = this.spec.inputs() + i;
            ItemStack current = this.getItem(slot);
            ItemStack result = results.get(i).create();
            result.setCount(result.getCount() * copies);
            ProductionLog.produced(this.level, result);
            if (current.isEmpty()) {
                this.setItem(slot, result);
            } else {
                current.grow(result.getCount());
            }
        }
    }

    /**
     * 原料用完留下的东西（牛奶桶的空桶，ADR-0026）：原料格空了就放回原料格，等玩家或漏斗拿走；没空就掉在设备上面。
     */
    private void leaveRemainder(int slot, ItemStack remainder) {
        if (this.getItem(slot).isEmpty()) {
            this.setItem(slot, remainder);
        } else if (this.level != null) {
            Containers.dropItemStack(this.level, this.worldPosition.getX(), this.worldPosition.getY() + 1, this.worldPosition.getZ(), remainder);
        }
    }

    // ---- 物品栏

    @Override
    protected boolean isInput(int slot) {
        return slot < this.spec.inputs();
    }

    @Override
    protected boolean isOutput(int slot) {
        return slot >= this.spec.inputs() && slot < this.spec.inputs() + this.spec.outputs();
    }

    /** 原料格只收选定配方对应的那种原料。 */
    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        SizedIngredient ingredient = this.ingredient(slot);
        return ingredient != null && ingredient.ingredient().test(stack);
    }

    /** 产物能抽走；原料格里留下的空桶这类不是原料的东西也能抽走。 */
    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        if (this.isOutput(slot)) {
            return true;
        }
        if (slot >= this.spec.inputs()) {
            return false;
        }
        SizedIngredient ingredient = this.ingredient(slot);
        return ingredient == null || !ingredient.ingredient().test(stack);
    }

    /** 自动化只塞到几次配方的量，免得一台设备把传送带上的原料全吞了。 */
    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction side) {
        SizedIngredient ingredient = this.ingredient(slot);
        return ingredient != null && ingredient.ingredient().test(stack)
                && this.getItem(slot).getCount() < Math.min(stack.getMaxStackSize(), ingredient.count() * INPUT_BUFFER);
    }

    private @Nullable SizedIngredient ingredient(int slot) {
        if (!this.isInput(slot)) {
            return null;
        }
        RecipeHolder<MachineRecipe> holder = this.recipe();
        if (holder == null || slot >= holder.value().ingredients().size()) {
            return null;
        }
        return holder.value().ingredients().get(slot);
    }

    // ---- 界面

    @Override
    protected MachineMenu.Type menuType() {
        return this.spec.menu();
    }

    @Override
    protected int dataCount() {
        return DATA_COUNT;
    }

    /**
     * 本类的界面数据：选定的配方在清单里的序号 + 1（没选是 0）、进度千分数、状态。
     */
    @Override
    protected int dataValue(int index) {
        return switch (index) {
            case SELECTED -> this.level == null ? 0 : MachineRecipes.indexOf(this.level, this.spec.kind(), this.recipe()) + 1;
            case PROGRESS -> {
                RecipeHolder<MachineRecipe> holder = this.recipe();
                yield holder == null ? 0 : (int) Math.min(1000, Math.round(this.progress * 1000 / holder.value().time()));
            }
            case STATUS -> this.status;
            case PROLIFERATION -> {
                RecipeHolder<MachineRecipe> holder = this.recipe();
                boolean productive = holder != null && productive(holder.value());
                yield (this.mode == Proliferation.Mode.SPEEDUP ? 1 : 0) | this.batchLevel << 1 | (productive ? 16 : 0);
            }
            default -> super.dataValue(index);
        };
    }

    // ---- 存档

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        String recipe = input.getStringOr("Recipe", "");
        Identifier id = recipe.isEmpty() ? null : Identifier.tryParse(recipe);
        this.selected = id == null ? null : ResourceKey.create(Registries.RECIPE, id);
        this.progress = input.getDoubleOr("Progress", 0);
        this.mode = input.getBooleanOr("Speedup", false) ? Proliferation.Mode.SPEEDUP : Proliferation.Mode.EXTRA_PRODUCTS;
        this.extra.set(input.getIntOr("ExtraProgress", 0));
        // M8 以前没记格数，那时熔炉是两个原料格，别的设备和现在一样
        int saved = input.getIntOr("InputSlots", this.spec.kind() == MachineRecipe.Kind.SMELTING ? 2 : this.spec.inputs());
        if (saved < this.spec.inputs()) {
            this.shiftOutputs(saved);
        }
    }

    /** 原料格从 {@code saved} 个变多了：老位置上的产物往后挪，空出来的原料格清空。 */
    private void shiftOutputs(int saved) {
        int shift = this.spec.inputs() - saved;
        var items = this.getItems();
        for (int slot = this.spec.inputs() + this.spec.outputs() - 1; slot >= saved + shift; slot--) {
            items.set(slot, items.get(slot - shift));
        }
        for (int slot = saved; slot < saved + shift; slot++) {
            items.set(slot, ItemStack.EMPTY);
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (this.selected != null) {
            output.putString("Recipe", this.selected.identifier().toString());
        }
        output.putDouble("Progress", this.progress);
        output.putInt("InputSlots", this.spec.inputs());
        output.putBoolean("Speedup", this.mode == Proliferation.Mode.SPEEDUP);
        output.putInt("ExtraProgress", this.extra.milli());
    }
}
