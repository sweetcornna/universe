package io.github.sweetcornna.universe.compat.jei;

import java.util.List;
import java.util.Locale;

import io.github.sweetcornna.universe.factory.recipe.MachineRecipe;
import io.github.sweetcornna.universe.factory.research.Research;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.recipe.types.IRecipeHolderType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.ItemLike;
import net.neoforged.neoforge.common.crafting.SizedIngredient;
import org.jspecify.annotations.Nullable;

/**
 * 一种设备配方的 JEI 分类（ADR-0019）：左边最多四种原料，箭头，右边最多两种产物；下面写速度 1 的设备做一次要几秒，
 * 要研究的配方写上科技名（还没研究的用红字）。
 */
final class MachineRecipeCategory implements IRecipeCategory<RecipeHolder<MachineRecipe>> {
    private static final int WIDTH = 160;
    private static final int HEIGHT = 46;
    private static final int SLOT = 18;
    private static final int ARROW_X = 4 * SLOT + 6;
    private static final int OUTPUT_X = ARROW_X + 24 + 8;
    private static final int TEXT_Y = 24;
    private static final String ELLIPSIS = "…";

    private final IRecipeHolderType<MachineRecipe> type;
    private final Component title;
    private final IDrawable icon;

    MachineRecipeCategory(IRecipeHolderType<MachineRecipe> type, MachineRecipe.Kind kind, ItemLike icon, IGuiHelper gui) {
        this.type = type;
        this.title = Component.translatable("jei.universe." + kind.name().toLowerCase(Locale.ROOT));
        this.icon = gui.createDrawableItemLike(icon);
    }

    @Override
    public IRecipeHolderType<MachineRecipe> getRecipeType() {
        return this.type;
    }

    @Override
    public Component getTitle() {
        return this.title;
    }

    @Override
    public int getWidth() {
        return WIDTH;
    }

    @Override
    public int getHeight() {
        return HEIGHT;
    }

    @Override
    public IDrawable getIcon() {
        return this.icon;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, RecipeHolder<MachineRecipe> holder, IFocusGroup focuses) {
        MachineRecipe recipe = holder.value();
        List<SizedIngredient> ingredients = recipe.ingredients();
        for (int i = 0; i < ingredients.size(); i++) {
            builder.addInputSlot(1 + i * SLOT, 1).setStandardSlotBackground().addItemStacks(stacks(ingredients.get(i)));
        }
        List<ItemStackTemplate> results = recipe.results();
        for (int i = 0; i < results.size(); i++) {
            builder.addOutputSlot(OUTPUT_X + i * SLOT, 1).setOutputSlotBackground().add(results.get(i));
        }
    }

    private static List<ItemStack> stacks(SizedIngredient ingredient) {
        return ingredient.ingredient().items().map(item -> new ItemStack(item, ingredient.count())).toList();
    }

    @Override
    public void createRecipeExtras(IRecipeExtrasBuilder builder, RecipeHolder<MachineRecipe> holder, IFocusGroup focuses) {
        builder.addAnimatedRecipeArrowWidget(Math.max(1, holder.value().time())).setPosition(ARROW_X, 1);
    }

    @Override
    public void draw(RecipeHolder<MachineRecipe> holder, IRecipeSlotsView slots, GuiGraphicsExtractor graphics, double mouseX, double mouseY) {
        MachineRecipe recipe = holder.value();
        Font font = Minecraft.getInstance().font;
        graphics.text(font, Component.translatable("gui.universe.recipe_machine.time", String.format(Locale.ROOT, "%.1f", recipe.time() / 20.0)), 1,
                TEXT_Y, 0xFF808080, false);
        Component research = research(recipe);
        if (research != null) {
            Minecraft minecraft = Minecraft.getInstance();
            boolean unlocked = minecraft.level != null && Research.isUnlocked(minecraft.level, recipe.research());
            graphics.text(font, Language.getInstance().getVisualOrder(fit(font, research)), 1, TEXT_Y + 11, unlocked ? 0xFF808080 : 0xFFC04040, false);
        }
    }

    /** 放不下时（英文的长科技名）截掉，末尾加省略号；鼠标指着那一行时提示里有全文。 */
    private static FormattedText fit(Font font, Component text) {
        if (font.width(text) <= WIDTH - 2) {
            return text;
        }
        return FormattedText.composite(font.substrByWidth(text, WIDTH - 2 - font.width(ELLIPSIS)), Component.literal(ELLIPSIS));
    }

    private static @Nullable Component research(MachineRecipe recipe) {
        return recipe.research().isEmpty() ? null : Component.translatable("jei.universe.requires", Research.name(recipe.research()));
    }

    @Override
    public void getTooltip(ITooltipBuilder tooltip, RecipeHolder<MachineRecipe> holder, IRecipeSlotsView slots, double mouseX, double mouseY) {
        Component research = research(holder.value());
        if (research != null && mouseY >= TEXT_Y + 10 && mouseY < TEXT_Y + 20 && Minecraft.getInstance().font.width(research) > WIDTH - 2) {
            tooltip.add(research);
        }
    }
}
