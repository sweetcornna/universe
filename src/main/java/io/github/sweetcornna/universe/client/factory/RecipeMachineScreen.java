package io.github.sweetcornna.universe.client.factory;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import com.mojang.blaze3d.platform.InputConstants;

import io.github.sweetcornna.universe.factory.MachineMenu;
import io.github.sweetcornna.universe.factory.MachineMenu.Role;
import io.github.sweetcornna.universe.factory.MachineMenu.SlotSpec;
import io.github.sweetcornna.universe.factory.RecipeMachineBlockEntity;
import io.github.sweetcornna.universe.factory.recipe.MachineRecipe;
import io.github.sweetcornna.universe.factory.recipe.MachineRecipes;
import io.github.sweetcornna.universe.factory.research.Research;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.common.crafting.SizedIngredient;

/**
 * 配方设备的界面（ADR-0011）：左边是这类设备的全部配方，点一下选定；右边是原料格、进度箭头和产物格。
 * 原料格空着时画出该放的原料（半透明）和数量；没解锁的配方变暗，提示要研究什么。
 */
public class RecipeMachineScreen extends MachineScreen {
    private static final int GRID_X = 8;
    private static final int GRID_Y = 32;
    private static final int COLUMNS = 5;
    private static final int ROWS = 4;
    private static final int CELL = 18;
    private static final int ARROW_X = 158;
    private static final int ARROW_Y = 50;
    private static final int INFO_X = 114;

    private final RecipeMachineBlockEntity.Spec spec;
    private int scrollRow;
    private int hoveredRecipe = -1;

    public RecipeMachineScreen(MachineMenu menu, Inventory inventory, Component title, RecipeMachineBlockEntity.Spec spec) {
        super(menu, inventory, title);
        this.spec = spec;
    }

    private List<RecipeHolder<MachineRecipe>> recipes() {
        return this.minecraft == null || this.minecraft.level == null ? List.of() : MachineRecipes.list(this.minecraft.level, this.spec.kind());
    }

    private RecipeHolder<MachineRecipe> selected(List<RecipeHolder<MachineRecipe>> recipes) {
        int index = this.menu.data(RecipeMachineBlockEntity.SELECTED) - 1;
        return index >= 0 && index < recipes.size() ? recipes.get(index) : null;
    }

    private boolean unlocked(MachineRecipe recipe) {
        return this.minecraft != null && this.minecraft.level != null && Research.isUnlocked(this.minecraft.level, recipe.research());
    }

    private int maxScroll(int count) {
        return Math.max(0, (count + COLUMNS - 1) / COLUMNS - ROWS);
    }

    // ---- 画

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractBackground(graphics, mouseX, mouseY, a);
        int x = this.leftPos;
        int y = this.topPos;
        List<RecipeHolder<MachineRecipe>> recipes = this.recipes();
        this.scrollRow = Math.min(this.scrollRow, this.maxScroll(recipes.size()));
        RecipeHolder<MachineRecipe> selected = this.selected(recipes);

        // 配方清单
        this.hoveredRecipe = -1;
        List<int[]> dimmed = new ArrayList<>();
        int[] highlight = null;
        for (int row = 0; row < ROWS; row++) {
            for (int column = 0; column < COLUMNS; column++) {
                int cx = x + GRID_X + column * CELL;
                int cy = y + GRID_Y + row * CELL;
                slotFrame(graphics, cx + 1, cy + 1);
                int index = (this.scrollRow + row) * COLUMNS + column;
                if (index >= recipes.size()) {
                    continue;
                }
                MachineRecipe recipe = recipes.get(index).value();
                graphics.fakeItem(recipe.results().getFirst().create(), cx + 1, cy + 1);
                boolean hovered = mouseX >= cx && mouseX < cx + CELL && mouseY >= cy && mouseY < cy + CELL;
                if (hovered) {
                    this.hoveredRecipe = index;
                }
                if (!this.unlocked(recipe)) {
                    dimmed.add(new int[] {cx + 1, cy + 1});
                } else if (recipes.get(index) == selected || hovered) {
                    highlight = recipes.get(index) == selected ? new int[] {cx, cy, ACCENT} : highlight == null ? new int[] {cx, cy, 0x807FE0F7} : highlight;
                }
            }
        }
        // 滚动条
        int maxScroll = this.maxScroll(recipes.size());
        if (maxScroll > 0) {
            int trackX = x + GRID_X + COLUMNS * CELL + 2;
            int trackTop = y + GRID_Y;
            int trackHeight = ROWS * CELL;
            graphics.fill(trackX, trackTop, trackX + 3, trackTop + trackHeight, INSET);
            int thumb = Math.max(8, trackHeight * ROWS / (ROWS + maxScroll));
            int thumbY = trackTop + (trackHeight - thumb) * this.scrollRow / maxScroll;
            graphics.fill(trackX, thumbY, trackX + 3, thumbY + thumb, BORDER);
        }

        // 原料格：空着时画出该放的原料
        List<SlotSpec> slots = this.menu.type().slots();
        List<int[]> ghosts = new ArrayList<>();
        int input = 0;
        for (int i = 0; i < slots.size(); i++) {
            SlotSpec slot = slots.get(i);
            if (slot.role() != Role.INPUT) {
                continue;
            }
            int ingredientIndex = input++;
            if (selected == null || !this.menu.getSlot(i).getItem().isEmpty()) {
                continue;
            }
            List<SizedIngredient> ingredients = selected.value().ingredients();
            if (ingredientIndex < ingredients.size()) {
                SizedIngredient ingredient = ingredients.get(ingredientIndex);
                ItemStack ghost = cycle(ingredient).copyWithCount(ingredient.count());
                graphics.fakeItem(ghost, x + slot.x(), y + slot.y());
                graphics.itemDecorations(this.font, ghost, x + slot.x(), y + slot.y());
                ghosts.add(new int[] {x + slot.x(), y + slot.y()});
            } else {
                // 这个配方用不到的格子
                ghosts.add(new int[] {x + slot.x(), y + slot.y()});
            }
        }

        // 盖在物品上面的一层：没解锁的变暗、原料虚影、选中框
        graphics.nextStratum();
        for (int[] cell : dimmed) {
            graphics.fill(cell[0], cell[1], cell[0] + 16, cell[1] + 16, 0xB0101A21);
        }
        for (int[] ghost : ghosts) {
            graphics.fill(ghost[0], ghost[1], ghost[0] + 16, ghost[1] + 16, 0x900A1015);
        }
        if (highlight != null) {
            outline(graphics, highlight[0], highlight[1], CELL, CELL, highlight[2]);
        }

        // 进度箭头
        this.arrow(graphics, x + ARROW_X, y + ARROW_Y, this.menu.data(RecipeMachineBlockEntity.PROGRESS) / 1000F);
    }

    /** 原料可以是好几种物品时轮流显示。 */
    private static ItemStack cycle(SizedIngredient ingredient) {
        List<Holder<Item>> items = ingredient.ingredient().items().toList();
        if (items.isEmpty()) {
            return ItemStack.EMPTY;
        }
        int index = (int) (System.currentTimeMillis() / 1000 % items.size());
        return new ItemStack(items.get(index));
    }

    /** 往右的箭头：杆 20 像素，箭头 6 像素，按进度从左往右填。 */
    private void arrow(GuiGraphicsExtractor graphics, int x, int centerY, float progress) {
        int length = 26;
        int filled = Math.round(length * progress);
        for (int column = 0; column < length; column++) {
            int half = column < 20 ? 2 : 2 * (length - column) - 1;
            int color = column < filled ? ACCENT : 0xFF24404D;
            graphics.fill(x + column, centerY - half, x + column + 1, centerY + half + 1, color);
        }
    }

    private static void outline(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int color) {
        graphics.fill(x, y, x + width, y + 1, color);
        graphics.fill(x, y + height - 1, x + width, y + height, color);
        graphics.fill(x, y, x + 1, y + height, color);
        graphics.fill(x + width - 1, y, x + width, y + height, color);
    }

    @Override
    protected void extractInfo(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        this.powerLine(graphics, 8, 20);
        List<RecipeHolder<MachineRecipe>> recipes = this.recipes();
        RecipeHolder<MachineRecipe> selected = this.selected(recipes);
        int status = this.menu.data(RecipeMachineBlockEntity.STATUS);
        String key = switch (status) {
            case RecipeMachineBlockEntity.RUNNING -> "running";
            case RecipeMachineBlockEntity.NO_INPUT -> "no_input";
            case RecipeMachineBlockEntity.OUTPUT_FULL -> "output_full";
            case RecipeMachineBlockEntity.NO_POWER -> "no_power";
            case RecipeMachineBlockEntity.LOCKED -> "locked";
            default -> "no_recipe";
        };
        int color = switch (status) {
            case RecipeMachineBlockEntity.RUNNING -> GOOD;
            case RecipeMachineBlockEntity.NO_INPUT, RecipeMachineBlockEntity.OUTPUT_FULL -> WARN;
            case RecipeMachineBlockEntity.NO_POWER, RecipeMachineBlockEntity.LOCKED -> BAD;
            default -> DIM;
        };
        if (selected == null) {
            key = "no_recipe";
            color = DIM;
        }
        graphics.text(this.font, Component.translatable("gui.universe.recipe_machine." + key), INFO_X, 76, color, false);
        if (selected != null) {
            graphics.text(this.font, Component.translatable("gui.universe.recipe_machine.time",
                    seconds(selected.value().time() / this.spec.speed())), INFO_X, 88, DIM, false);
        }
    }

    private static String seconds(double ticks) {
        return String.format(Locale.ROOT, "%.1f", ticks / 20);
    }

    @Override
    protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractTooltip(graphics, mouseX, mouseY);
        List<RecipeHolder<MachineRecipe>> recipes = this.recipes();
        if (this.hoveredRecipe < 0 || this.hoveredRecipe >= recipes.size() || !this.menu.getCarried().isEmpty()) {
            return;
        }
        MachineRecipe recipe = recipes.get(this.hoveredRecipe).value();
        List<Component> lines = new ArrayList<>();
        ItemStack main = recipe.results().getFirst().create();
        lines.add(main.getHoverName().copy().withStyle(ChatFormatting.AQUA));
        lines.add(Component.translatable("gui.universe.recipe_machine.ingredients").withStyle(ChatFormatting.GRAY));
        for (SizedIngredient ingredient : recipe.ingredients()) {
            lines.add(Component.literal("  " + ingredient.count() + " × ").append(cycle(ingredient).getHoverName()));
        }
        lines.add(Component.translatable("gui.universe.recipe_machine.results").withStyle(ChatFormatting.GRAY));
        for (ItemStackTemplate result : recipe.results()) {
            ItemStack stack = result.create();
            lines.add(Component.literal("  " + stack.getCount() + " × ").append(stack.getHoverName()));
        }
        lines.add(Component.translatable("gui.universe.recipe_machine.time", seconds(recipe.time() / this.spec.speed()))
                .withStyle(ChatFormatting.GRAY));
        if (!this.unlocked(recipe)) {
            lines.add(Component.translatable("gui.universe.recipe_machine.requires", Research.name(recipe.research()))
                    .withStyle(ChatFormatting.RED));
        }
        graphics.setTooltipForNextFrame(this.font, lines, Optional.empty(), mouseX, mouseY);
    }

    // ---- 操作

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        List<RecipeHolder<MachineRecipe>> recipes = this.recipes();
        int index = this.recipeAt(event.x(), event.y(), recipes.size());
        // 26.x 的输入是 SDL：左键是 1，不是 GLFW 的 0
        if (index >= 0 && event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            if (this.unlocked(recipes.get(index).value()) && this.minecraft != null && this.minecraft.gameMode != null) {
                this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, index);
                this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            }
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        double localX = mouseX - this.leftPos - GRID_X;
        double localY = mouseY - this.topPos - GRID_Y;
        if (localX >= 0 && localX < COLUMNS * CELL + 6 && localY >= 0 && localY < ROWS * CELL) {
            int max = this.maxScroll(this.recipes().size());
            this.scrollRow = Math.max(0, Math.min(max, this.scrollRow - (int) Math.signum(scrollY)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private int recipeAt(double mouseX, double mouseY, int count) {
        double localX = mouseX - this.leftPos - GRID_X;
        double localY = mouseY - this.topPos - GRID_Y;
        if (localX < 0 || localY < 0 || localX >= COLUMNS * CELL || localY >= ROWS * CELL) {
            return -1;
        }
        int index = (this.scrollRow + (int) (localY / CELL)) * COLUMNS + (int) (localX / CELL);
        return index < count ? index : -1;
    }
}
