package io.github.sweetcornna.universe.registry;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.factory.recipe.MachineRecipe;
import io.github.sweetcornna.universe.factory.recipe.ResearchShapedRecipe;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeBookCategory;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** 配方类型和序列化器：七种设备配方（冶炼、制造、精炼、化工、粒子、营养处理、便携精炼）和要研究解锁的有序合成。 */
public final class UniverseRecipeTypes {
    public static final DeferredRegister<RecipeType<?>> TYPES = DeferredRegister.create(Registries.RECIPE_TYPE, Universe.MOD_ID);
    public static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS = DeferredRegister.create(Registries.RECIPE_SERIALIZER, Universe.MOD_ID);
    public static final DeferredRegister<RecipeBookCategory> BOOK_CATEGORIES =
            DeferredRegister.create(Registries.RECIPE_BOOK_CATEGORY, Universe.MOD_ID);

    public static final DeferredHolder<RecipeType<?>, RecipeType<MachineRecipe>> SMELTING = type("smelting");
    public static final DeferredHolder<RecipeType<?>, RecipeType<MachineRecipe>> ASSEMBLING = type("assembling");
    public static final DeferredHolder<RecipeType<?>, RecipeType<MachineRecipe>> REFINING = type("refining");
    public static final DeferredHolder<RecipeType<?>, RecipeType<MachineRecipe>> CHEMICAL = type("chemical");
    public static final DeferredHolder<RecipeType<?>, RecipeType<MachineRecipe>> PARTICLE = type("particle");
    public static final DeferredHolder<RecipeType<?>, RecipeType<MachineRecipe>> NUTRIENT = type("nutrient");
    public static final DeferredHolder<RecipeType<?>, RecipeType<MachineRecipe>> PORTABLE_REFINING = type("portable_refining");

    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<MachineRecipe>> SMELTING_SERIALIZER =
            SERIALIZERS.register("smelting", () -> MachineRecipe.serializer(MachineRecipe.Kind.SMELTING));
    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<MachineRecipe>> ASSEMBLING_SERIALIZER =
            SERIALIZERS.register("assembling", () -> MachineRecipe.serializer(MachineRecipe.Kind.ASSEMBLING));
    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<MachineRecipe>> REFINING_SERIALIZER =
            SERIALIZERS.register("refining", () -> MachineRecipe.serializer(MachineRecipe.Kind.REFINING));
    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<MachineRecipe>> CHEMICAL_SERIALIZER =
            SERIALIZERS.register("chemical", () -> MachineRecipe.serializer(MachineRecipe.Kind.CHEMICAL));
    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<MachineRecipe>> PARTICLE_SERIALIZER =
            SERIALIZERS.register("particle", () -> MachineRecipe.serializer(MachineRecipe.Kind.PARTICLE));
    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<MachineRecipe>> NUTRIENT_SERIALIZER =
            SERIALIZERS.register("nutrient", () -> MachineRecipe.serializer(MachineRecipe.Kind.NUTRIENT));
    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<MachineRecipe>> PORTABLE_REFINING_SERIALIZER =
            SERIALIZERS.register("portable_refining", () -> MachineRecipe.serializer(MachineRecipe.Kind.PORTABLE_REFINING));
    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<ResearchShapedRecipe>> RESEARCH_SHAPED =
            SERIALIZERS.register("research_shaped", () -> ResearchShapedRecipe.SERIALIZER);

    /** 设备配方不进配方书（都是"特殊"配方），但每个配方都要有个类别。 */
    public static final DeferredHolder<RecipeBookCategory, RecipeBookCategory> MACHINE_CATEGORY =
            BOOK_CATEGORIES.register("machine", RecipeBookCategory::new);

    private UniverseRecipeTypes() {
    }

    private static DeferredHolder<RecipeType<?>, RecipeType<MachineRecipe>> type(String name) {
        return TYPES.register(name, () -> RecipeType.simple(Universe.id(name)));
    }

    public static void register(IEventBus modBus) {
        TYPES.register(modBus);
        SERIALIZERS.register(modBus);
        BOOK_CATEGORIES.register(modBus);
    }
}
