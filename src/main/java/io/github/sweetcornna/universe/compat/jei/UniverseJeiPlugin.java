package io.github.sweetcornna.universe.compat.jei;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.client.TextWrap;
import io.github.sweetcornna.universe.client.dev.DevScript;
import io.github.sweetcornna.universe.client.factory.RecipeMachineScreen;
import io.github.sweetcornna.universe.factory.recipe.MachineRecipe;
import io.github.sweetcornna.universe.factory.recipe.MachineRecipes;
import io.github.sweetcornna.universe.registry.UniverseBlocks;
import io.github.sweetcornna.universe.registry.UniverseItems;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.handlers.IGuiClickableArea;
import mezz.jei.api.gui.handlers.IGuiContainerHandler;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.types.IRecipeHolderType;
import mezz.jei.api.recipe.types.IRecipeType;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;

/**
 * JEI 插件（ADR-0019）。JEI 装着时由它按注解找到并加载，没装时这个包里的类不会被碰到。每种设备配方一个分类，
 * 配方读客户端收到的那份（和设备界面左边的清单同一份、同一顺序）；设备界面里的进度箭头点了打开对应分类；
 * 不走配方的几种东西（水、原油、重氢、临界光子）给一页说明。
 */
@JeiPlugin
public final class UniverseJeiPlugin implements IModPlugin {
    private static final Map<MachineRecipe.Kind, IRecipeHolderType<MachineRecipe>> TYPES = new EnumMap<>(MachineRecipe.Kind.class);
    /** 说明页的文字宽度，比 JEI 说明页的可用宽度（约 150）留一点余量。 */
    private static final int INFO_WIDTH = 140;

    @Override
    public Identifier getPluginUid() {
        return Universe.id("jei");
    }

    private static IRecipeHolderType<MachineRecipe> type(MachineRecipe.Kind kind) {
        return TYPES.computeIfAbsent(kind, k -> IRecipeType.create(k.type()));
    }

    /** 每种配方能在哪些设备里做，第一个当分类的图标。 */
    private static List<ItemLike> machines(MachineRecipe.Kind kind) {
        return switch (kind) {
            case SMELTING -> List.of(UniverseBlocks.ARC_SMELTER.get(), UniverseBlocks.PLANE_SMELTER.get(), UniverseBlocks.NEGENTROPY_SMELTER.get());
            case ASSEMBLING -> List.of(UniverseBlocks.ASSEMBLER.get(), UniverseBlocks.ASSEMBLER_MK2.get(), UniverseBlocks.ASSEMBLER_MK3.get(),
                    UniverseBlocks.RECOMPOSING_ASSEMBLER.get());
            case REFINING -> List.of(UniverseBlocks.OIL_REFINERY.get());
            case CHEMICAL -> List.of(UniverseBlocks.CHEMICAL_PLANT.get(), UniverseBlocks.QUANTUM_CHEMICAL_PLANT.get());
            case PARTICLE -> List.of(UniverseBlocks.MINIATURE_PARTICLE_COLLIDER.get());
            case NUTRIENT -> List.of(UniverseBlocks.NUTRIENT_PROCESSOR.get());
            case PORTABLE_REFINING -> List.of(UniverseBlocks.PORTABLE_REFINER.get());
        };
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        IGuiHelper gui = registration.getJeiHelpers().getGuiHelper();
        for (MachineRecipe.Kind kind : MachineRecipe.Kind.values()) {
            registration.addRecipeCategories(new MachineRecipeCategory(type(kind), kind, machines(kind).getFirst(), gui));
        }
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        for (MachineRecipe.Kind kind : MachineRecipe.Kind.values()) {
            registration.addRecipes(type(kind), MachineRecipes.clientList(kind));
        }
        info(registration, UniverseItems.WATER.get(), "water");
        info(registration, UniverseItems.CRUDE_OIL.get(), "crude_oil");
        info(registration, UniverseItems.DEUTERIUM.get(), "deuterium");
        info(registration, UniverseItems.CRITICAL_PHOTON.get(), "critical_photon");
    }

    /** JEI 换行只认空格，中文会断在数字两边、词中间加连字符，所以先按本模组的规则断好行，一行一个组件。 */
    private static void info(IRecipeRegistration registration, ItemLike item, String key) {
        Font font = Minecraft.getInstance().font;
        List<Component> lines = new ArrayList<>();
        for (FormattedCharSequence line : TextWrap.split(font, Component.translatable("jei.universe.info." + key), INFO_WIDTH)) {
            StringBuilder text = new StringBuilder();
            line.accept((index, style, codepoint) -> {
                text.appendCodePoint(codepoint);
                return true;
            });
            lines.add(Component.literal(text.toString()));
        }
        registration.addIngredientInfo(item, lines.toArray(Component[]::new));
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        for (MachineRecipe.Kind kind : MachineRecipe.Kind.values()) {
            registration.addCraftingStation(type(kind), machines(kind).toArray(ItemLike[]::new));
        }
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime runtime) {
        // 无头验收用：开发脚本里 jei <物品> 打开它的配方，jei <物品> uses 打开它的用途
        if (DevScript.enabled()) {
            DevScript.EXTRA_VERBS.put("jeicat", args -> runtime.getRecipesGui().showTypes(
                    List.of(type(MachineRecipe.Kind.valueOf(args[0].toUpperCase(java.util.Locale.ROOT))))));
            DevScript.EXTRA_VERBS.put("jei", args -> {
                Item item = BuiltInRegistries.ITEM.getValue(Identifier.parse(args[0]));
                RecipeIngredientRole role = args.length > 1 && args[1].equals("uses") ? RecipeIngredientRole.INPUT : RecipeIngredientRole.OUTPUT;
                runtime.getRecipesGui().show(runtime.getJeiHelpers().getFocusFactory().createFocus(role, VanillaTypes.ITEM_STACK, new ItemStack(item)));
            });
        }
    }

    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        // 所有配方设备共用一个界面类，按界面上的设备种类给箭头配上分类
        registration.addGuiContainerHandler(RecipeMachineScreen.class, new IGuiContainerHandler<RecipeMachineScreen>() {
            @Override
            public Collection<IGuiClickableArea> getGuiClickableAreas(RecipeMachineScreen screen, double mouseX, double mouseY) {
                Collection<IGuiClickableArea> areas = new ArrayList<>();
                int[] arrow = RecipeMachineScreen.ARROW_AREA;
                areas.add(IGuiClickableArea.createBasic(arrow[0], arrow[1], arrow[2], arrow[3], type(screen.spec().kind())));
                return areas;
            }
        });
    }
}
