package io.github.sweetcornna.universe.compat.jei;

import java.util.List;
import java.util.Locale;

import io.github.sweetcornna.universe.bio.BioRecipes;
import io.github.sweetcornna.universe.chem.reactor.ReactionBooks;
import io.github.sweetcornna.universe.chem.reactor.ReactorRecipes;
import io.github.sweetcornna.universe.core.chem.reaction.Catalyst;
import io.github.sweetcornna.universe.core.chem.reaction.Reaction;
import io.github.sweetcornna.universe.core.microbe.BioProcess;
import io.github.sweetcornna.universe.factory.recipe.MachineRecipe;
import io.github.sweetcornna.universe.factory.research.Research;
import io.github.sweetcornna.universe.microbe.MicrobeText;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.recipe.types.IRecipeHolderType;
import net.minecraft.ChatFormatting;
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
 * 一种设备配方的 JEI 分类（ADR-0019）：左边最多四种原料，箭头，右边最多三种产物（电解槽，ADR-0036）；下面写速度 1 的设备做一次要几秒，
 * 要研究的配方写上科技名（还没研究的用红字）。
 *
 * <p>反应釜（ADR-0037）：第四格放催化剂（不消耗），下面三行是反应式、教科书工况、发现了没有。配方里的科技只是把反应列进化学手册，
 * 能不能在反应釜里选看全服发现了没有。
 */
final class MachineRecipeCategory implements IRecipeCategory<RecipeHolder<MachineRecipe>> {
    private static final int WIDTH = 164;
    private static final int HEIGHT = 46;
    private static final int SLOT = 18;
    private static final int ARROW_X = 4 * SLOT + 6;
    private static final int OUTPUT_X = ARROW_X + 24 + 8;
    private static final int TEXT_Y = 24;
    private static final String ELLIPSIS = "…";

    private final IRecipeHolderType<MachineRecipe> type;
    private final Component title;
    private final IDrawable icon;
    /** 生物反应器多一行，写菌要带的基因。 */
    private final int height;

    MachineRecipeCategory(IRecipeHolderType<MachineRecipe> type, MachineRecipe.Kind kind, ItemLike icon, IGuiHelper gui) {
        this.type = type;
        this.title = Component.translatable("jei.universe." + kind.name().toLowerCase(Locale.ROOT));
        this.icon = gui.createDrawableItemLike(icon);
        this.height = kind == MachineRecipe.Kind.BIOREACTOR || kind == MachineRecipe.Kind.REACTOR ? HEIGHT + 11 : HEIGHT;
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
        return this.height;
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
        // 反应釜的催化剂放在第四格，查催化剂的用途时能找到这条反应
        Reaction reaction = recipe.kind() == MachineRecipe.Kind.REACTOR ? ReactorRecipes.reaction(holder.id().identifier()) : null;
        Catalyst catalyst = reaction == null ? null : reaction.catalyst();
        if (catalyst != null) {
            builder.addSlot(RecipeIngredientRole.INPUT, 1 + 3 * SLOT, 1).setStandardSlotBackground().add(ReactorRecipes.item(catalyst.item()))
                    .addRichTooltipCallback((view, tooltip) -> tooltip.add(
                            Component.translatable("jei.universe.reactor.catalyst", catalyst.batches()).withStyle(ChatFormatting.GRAY)));
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
        // M20 反应釜（ADR-0036 §四、ADR-0037）：配方按单程转化率 1 写，时间是上限；写反应式、教科书的工况、发现了没有
        Reaction reaction = recipe.kind() == MachineRecipe.Kind.REACTOR ? ReactorRecipes.reaction(holder.id().identifier()) : null;
        if (reaction != null) {
            reactor(graphics, font, reaction);
            return;
        }
        graphics.text(font, Component.translatable("gui.universe.recipe_machine.time", String.format(Locale.ROOT, "%.1f", recipe.time() / 20.0)), 1,
                TEXT_Y, 0xFF808080, false);
        Component research = research(recipe);
        if (research != null) {
            Minecraft minecraft = Minecraft.getInstance();
            boolean unlocked = minecraft.level != null && Research.isUnlocked(minecraft.level, recipe.research());
            graphics.text(font, Language.getInstance().getVisualOrder(fit(font, research)), 1, TEXT_Y + 11, unlocked ? 0xFF808080 : 0xFFC04040, false);
        }
        // M20 生物反应器（ADR-0036 §五）：罐里的菌要带工艺的基因，藻类还要光
        BioProcess process = recipe.kind() == MachineRecipe.Kind.BIOREACTOR ? BioRecipes.process(holder.id().identifier()) : null;
        if (process != null) {
            Component gene = Component.translatable(process.needsLight() ? "jei.universe.bioreactor.gene_light" : "jei.universe.bioreactor.gene",
                    MicrobeText.gene(process.gene()));
            graphics.text(font, Language.getInstance().getVisualOrder(fit(font, gene)), 1, TEXT_Y + 22, 0xFF808080, false);
        }
    }

    /** 反应釜配方下面三行：反应式，教科书工况，发现了没有（没发现的写怎么拿到）。 */
    private static void reactor(GuiGraphicsExtractor graphics, Font font, Reaction reaction) {
        graphics.text(font, Language.getInstance().getVisualOrder(fit(font, Component.literal(reaction.equation()))), 1, TEXT_Y, 0xFF808080, false);
        graphics.text(font, ReactorRecipes.conditions(reaction), 1, TEXT_Y + 11, 0xFF808080, false);
        boolean discovered = ReactionBooks.client().discovered(reaction);
        graphics.text(font, Language.getInstance().getVisualOrder(fit(font, reactorState(reaction))), 1, TEXT_Y + 22,
                discovered ? 0xFF808080 : 0xFFC04040, false);
    }

    private static Component reactorState(Reaction reaction) {
        Minecraft minecraft = Minecraft.getInstance();
        if (ReactionBooks.client().discovered(reaction)) {
            return Component.translatable("jei.universe.reactor.discovered");
        }
        boolean listed = minecraft.level != null && ReactionBooks.listed(minecraft.level, reaction);
        return listed ? Component.translatable("jei.universe.reactor.listed")
                : Component.translatable("jei.universe.reactor.unlisted", Research.name(reaction.research()));
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
        Reaction reaction = holder.value().kind() == MachineRecipe.Kind.REACTOR ? ReactorRecipes.reaction(holder.id().identifier()) : null;
        if (reaction != null) {
            Font font = Minecraft.getInstance().font;
            Component equation = Component.literal(reaction.equation());
            if (mouseY >= TEXT_Y - 1 && mouseY < TEXT_Y + 9 && font.width(equation) > WIDTH - 2) {
                tooltip.add(equation);
            }
            Component state = reactorState(reaction);
            if (mouseY >= TEXT_Y + 21 && mouseY < TEXT_Y + 31 && font.width(state) > WIDTH - 2) {
                tooltip.add(state);
            }
            return;
        }
        Component research = research(holder.value());
        if (research != null && mouseY >= TEXT_Y + 10 && mouseY < TEXT_Y + 20 && Minecraft.getInstance().font.width(research) > WIDTH - 2) {
            tooltip.add(research);
        }
    }
}
