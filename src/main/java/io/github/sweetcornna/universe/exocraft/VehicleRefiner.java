package io.github.sweetcornna.universe.exocraft;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.factory.FuelRecipeMachineBlockEntity;
import io.github.sweetcornna.universe.factory.MachineMenu;
import io.github.sweetcornna.universe.factory.RecipeMachineBlockEntity;
import io.github.sweetcornna.universe.factory.RecipeMachines;
import io.github.sweetcornna.universe.factory.recipe.MachineRecipe;
import io.github.sweetcornna.universe.factory.recipe.MachineRecipes;
import io.github.sweetcornna.universe.factory.research.Research;
import io.github.sweetcornna.universe.stats.ProductionLog;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Prediction;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.component.CookingFuel;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.providers.number.ints.ResolvableInt;
import net.neoforged.neoforge.common.crafting.SizedIngredient;

/**
 * 重型车自带的便携精炼机（ADR-0026）：和放在地上的便携精炼机一样，一个原料格、一个产物格、一个燃料格，用便携精炼的配方，
 * 界面也是同一个（{@link RecipeMachines#PORTABLE_REFINER}）。只是装在车上：车走着也在炼，车收成物品时里面的东西和选好的配方跟着走。
 * <p>
 * 界面数据的格子和 {@link FuelRecipeMachineBlockEntity} 一一对应，所以能直接用便携精炼机的界面；不接电网，没有增产剂。
 */
public class VehicleRefiner extends SimpleContainer implements MachineMenu.ButtonTarget {
    static final int INPUT = 0;
    static final int OUTPUT = 1;
    static final int FUEL = 2;
    private static final RecipeMachineBlockEntity.Spec SPEC = RecipeMachines.PORTABLE_REFINER;

    private final UniverseVehicle vehicle;
    private @Nullable ResourceKey<Recipe<?>> selected;
    private double progress;
    private int status = RecipeMachineBlockEntity.NO_RECIPE;
    private int burnLeft;
    private int burnTotal;
    private boolean working;

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return VehicleRefiner.this.dataValue(index);
        }

        @Override
        public void set(int index, int value) {
        }

        @Override
        public int getCount() {
            return FuelRecipeMachineBlockEntity.DATA_COUNT;
        }
    };

    VehicleRefiner(UniverseVehicle vehicle) {
        super(3);
        this.vehicle = vehicle;
    }

    public ContainerData data() {
        return this.data;
    }

    /** 这一刻在炼（车上的灯亮）。 */
    public boolean working() {
        return this.working;
    }

    // ---- 配方

    private @Nullable RecipeHolder<MachineRecipe> recipe() {
        if (this.selected == null || !(this.vehicle.level() instanceof ServerLevel level)) {
            return null;
        }
        RecipeHolder<?> holder = level.getServer().getRecipeManager().recipeMap().byKey(this.selected);
        if (holder != null && holder.value() instanceof MachineRecipe recipe && recipe.kind() == SPEC.kind()) {
            @SuppressWarnings("unchecked")
            RecipeHolder<MachineRecipe> machineRecipe = (RecipeHolder<MachineRecipe>) holder;
            return machineRecipe;
        }
        return null;
    }

    /** 界面上点了配方清单里的第几个。换配方时把原料格里的东西还给玩家。 */
    @Override
    public boolean clickButton(ServerPlayer player, int button) {
        List<RecipeHolder<MachineRecipe>> recipes = MachineRecipes.list(player.level(), SPEC.kind());
        if (button < 0 || button >= recipes.size()) {
            return false;
        }
        RecipeHolder<MachineRecipe> holder = recipes.get(button);
        if (!Research.isUnlocked(player.level(), holder.value().research()) || Objects.equals(holder.id(), this.selected)) {
            return false;
        }
        ItemStack input = this.removeItemNoUpdate(INPUT);
        if (!input.isEmpty()) {
            player.getInventory().placeItemBackInInventory(input, Prediction.SERVER_ONLY);
        }
        this.selected = holder.id();
        this.progress = 0;
        this.setChanged();
        return true;
    }

    // ---- 每刻（服务端）

    void tick(ServerLevel level) {
        this.working = this.work(level);
    }

    private boolean work(ServerLevel level) {
        RecipeHolder<MachineRecipe> holder = this.recipe();
        if (holder == null) {
            return this.idle(RecipeMachineBlockEntity.NO_RECIPE);
        }
        MachineRecipe recipe = holder.value();
        if (!Research.isUnlocked(level, recipe.research())) {
            return this.idle(RecipeMachineBlockEntity.LOCKED);
        }
        if (!this.hasInputs(recipe)) {
            return this.idle(RecipeMachineBlockEntity.NO_INPUT);
        }
        if (!this.outputFits(recipe)) {
            return this.idle(RecipeMachineBlockEntity.OUTPUT_FULL);
        }
        if (this.burnLeft <= 0 && !this.ignite(level)) {
            return this.idle(RecipeMachineBlockEntity.NO_FUEL);
        }
        this.burnLeft--;
        this.status = RecipeMachineBlockEntity.RUNNING;
        this.progress += SPEC.speed();
        if (this.progress >= recipe.time()) {
            this.progress = 0;
            this.craft(level, recipe);
        }
        return true;
    }

    private boolean idle(int status) {
        this.status = status;
        return false;
    }

    private boolean hasInputs(MachineRecipe recipe) {
        List<SizedIngredient> ingredients = recipe.ingredients();
        return ingredients.size() <= SPEC.inputs() && (ingredients.isEmpty() || ingredients.getFirst().test(this.getItem(INPUT)));
    }

    private boolean outputFits(MachineRecipe recipe) {
        List<ItemStackTemplate> results = recipe.results();
        if (results.size() > SPEC.outputs()) {
            return false;
        }
        if (results.isEmpty()) {
            return true;
        }
        ItemStack current = this.getItem(OUTPUT);
        ItemStack result = results.getFirst().create();
        return current.isEmpty() || ItemStack.isSameItemSameComponents(current, result)
                && current.getCount() + result.getCount() <= current.getMaxStackSize();
    }

    private void craft(ServerLevel level, MachineRecipe recipe) {
        if (!recipe.ingredients().isEmpty()) {
            ItemStack input = this.getItem(INPUT);
            ItemStackTemplate remainder = input.getCraftingRemainder();
            int count = recipe.ingredients().getFirst().count();
            ProductionLog.consumed(level, input.getItem(), count);
            input.shrink(count);
            if (remainder != null && this.getItem(INPUT).isEmpty()) {
                this.setItem(INPUT, remainder.create().copyWithCount(remainder.count() * count));
            } else if (remainder != null) {
                this.vehicle.spawnAtLocation(level, remainder.create().copyWithCount(remainder.count() * count));
            }
        }
        if (!recipe.results().isEmpty()) {
            ItemStack result = recipe.results().getFirst().create();
            ProductionLog.produced(level, result);
            ItemStack current = this.getItem(OUTPUT);
            if (current.isEmpty()) {
                this.setItem(OUTPUT, result);
            } else {
                current.grow(result.getCount());
            }
        }
        this.setChanged();
    }

    /** 添一块燃料，烧完剩下的东西（岩浆桶的空桶）放回燃料格，放不下就掉在车旁边。 */
    private boolean ignite(ServerLevel level) {
        ItemStack fuel = this.getItem(FUEL);
        int burn = fuel.isEmpty() ? 0 : this.burnTime(level, fuel);
        if (burn <= 0) {
            return false;
        }
        ItemStackTemplate remainder = fuel.getCraftingRemainder();
        ProductionLog.consumed(level, fuel.getItem(), 1);
        fuel.shrink(1);
        if (remainder != null) {
            if (this.getItem(FUEL).isEmpty()) {
                this.setItem(FUEL, remainder.create());
            } else {
                this.vehicle.spawnAtLocation(level, remainder.create());
            }
        }
        this.burnLeft = burn;
        this.burnTotal = burn;
        this.setChanged();
        return true;
    }

    /** 燃料能烧几刻：和熔炉一样按物品上的烹饪燃料组件算。 */
    private int burnTime(ServerLevel level, ItemStack fuel) {
        LootContext context = new LootContext.Builder(new LootParams.Builder(level)
                .withParameter(LootContextParams.ORIGIN, this.vehicle.position())
                .withOptionalParameter(LootContextParams.THIS_ENTITY, this.vehicle)
                .create(LootContextParamSets.COMMAND)).create(Optional.empty());
        return ResolvableInt.getFromItem(fuel, DataComponents.COOKING_FUEL, CookingFuel::burnTime, context, 0);
    }

    // ---- 格子

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return switch (slot) {
            case INPUT -> {
                RecipeHolder<MachineRecipe> holder = this.recipe();
                yield holder != null && !holder.value().ingredients().isEmpty()
                        && holder.value().ingredients().getFirst().ingredient().test(stack);
            }
            case FUEL -> stack.has(DataComponents.COOKING_FUEL);
            default -> false;
        };
    }

    @Override
    public boolean stillValid(Player player) {
        return !this.vehicle.isRemoved() && player.distanceToSqr(this.vehicle) < 64;
    }

    // ---- 界面数据（和烧燃料的配方设备一样排）

    private int dataValue(int index) {
        return switch (index) {
            case 0 -> -1;
            case 3 -> this.working ? 1 : 0;
            case RecipeMachineBlockEntity.SELECTED -> {
                RecipeHolder<MachineRecipe> holder = this.recipe();
                yield holder == null ? 0 : MachineRecipes.indexOf(this.vehicle.level(), SPEC.kind(), holder) + 1;
            }
            case RecipeMachineBlockEntity.PROGRESS -> {
                RecipeHolder<MachineRecipe> holder = this.recipe();
                yield holder == null ? 0 : (int) Math.min(1000, Math.round(this.progress * 1000 / holder.value().time()));
            }
            case RecipeMachineBlockEntity.STATUS -> this.status;
            case FuelRecipeMachineBlockEntity.FUEL -> this.burnTotal <= 0 ? 0 : (int) Math.min(1000, (long) this.burnLeft * 1000 / this.burnTotal);
            default -> 0;
        };
    }

    // ---- 存档和物品

    void save(ValueOutput output) {
        ValueOutput child = output.child("refiner");
        ContainerHelper.saveAllItems(child, this.getItems());
        child.putString("recipe", this.selected == null ? "" : this.selected.identifier().toString());
        child.putDouble("progress", this.progress);
        child.putInt("burn", this.burnLeft);
        child.putInt("burn_total", this.burnTotal);
    }

    void load(ValueInput input) {
        input.child("refiner").ifPresent(child -> {
            ContainerHelper.loadAllItems(child, this.getItems());
            this.selected = key(child.getStringOr("recipe", ""));
            this.progress = child.getDoubleOr("progress", 0);
            this.burnLeft = child.getIntOr("burn", 0);
            this.burnTotal = child.getIntOr("burn_total", 0);
        });
    }

    /** 收成物品时存下的样子；空着、也没选配方时是 null。 */
    @Nullable Contents contents() {
        if (this.isEmpty() && this.selected == null && this.burnLeft <= 0) {
            return null;
        }
        return new Contents(ItemContainerContents.fromItems(this.getItems()), this.selected == null ? "" : this.selected.identifier().toString(),
                this.burnLeft, this.burnTotal);
    }

    public void restore(Contents contents) {
        NonNullList<ItemStack> items = NonNullList.withSize(this.getContainerSize(), ItemStack.EMPTY);
        contents.items().copyInto(items);
        for (int i = 0; i < items.size(); i++) {
            this.setItem(i, items.get(i));
        }
        this.selected = key(contents.recipe());
        this.burnLeft = contents.burn();
        this.burnTotal = contents.burnTotal();
    }

    private static @Nullable ResourceKey<Recipe<?>> key(String recipe) {
        Identifier id = recipe.isEmpty() ? null : Identifier.tryParse(recipe);
        return id == null ? null : ResourceKey.create(Registries.RECIPE, id);
    }

    /** 物品上记的精炼机：三个格子、选好的配方、炉膛里剩的火。 */
    public record Contents(ItemContainerContents items, String recipe, int burn, int burnTotal) {
        public static final Codec<Contents> CODEC = RecordCodecBuilder.create(i -> i.group(
                ItemContainerContents.CODEC.optionalFieldOf("items", ItemContainerContents.EMPTY).forGetter(Contents::items),
                Codec.STRING.optionalFieldOf("recipe", "").forGetter(Contents::recipe),
                Codec.INT.optionalFieldOf("burn", 0).forGetter(Contents::burn),
                Codec.INT.optionalFieldOf("burn_total", 0).forGetter(Contents::burnTotal)
        ).apply(i, Contents::new));
        public static final StreamCodec<RegistryFriendlyByteBuf, Contents> STREAM_CODEC = StreamCodec.composite(
                ItemContainerContents.STREAM_CODEC, Contents::items,
                ByteBufCodecs.STRING_UTF8, Contents::recipe,
                ByteBufCodecs.VAR_INT, Contents::burn,
                ByteBufCodecs.VAR_INT, Contents::burnTotal,
                Contents::new);
    }
}
