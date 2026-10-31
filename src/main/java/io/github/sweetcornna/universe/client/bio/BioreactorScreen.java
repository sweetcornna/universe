package io.github.sweetcornna.universe.client.bio;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.platform.InputConstants;

import io.github.sweetcornna.universe.bio.BioRecipes;
import io.github.sweetcornna.universe.bio.BioreactorBlockEntity;
import io.github.sweetcornna.universe.chem.reactor.ReactorRecipes;
import io.github.sweetcornna.universe.client.factory.RecipeMachineScreen;
import io.github.sweetcornna.universe.core.microbe.BioProcess;
import io.github.sweetcornna.universe.core.microbe.BioTime;
import io.github.sweetcornna.universe.core.microbe.Culture;
import io.github.sweetcornna.universe.core.microbe.Isolation;
import io.github.sweetcornna.universe.core.microbe.Organism;
import io.github.sweetcornna.universe.core.microbe.Tolerance;
import io.github.sweetcornna.universe.factory.MachineMenu;
import io.github.sweetcornna.universe.factory.RecipeMachineBlockEntity;
import io.github.sweetcornna.universe.factory.RecipeMachines;
import io.github.sweetcornna.universe.factory.recipe.MachineRecipe;
import io.github.sweetcornna.universe.factory.recipe.MachineRecipes;
import io.github.sweetcornna.universe.microbe.MicrobeText;
import io.github.sweetcornna.universe.registry.UniverseItems;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;

/**
 * 生物反应器的界面（ADR-0036 §五）：上半截是配方设备的界面（选工艺、原料、产物），下面一块是罐里的培养物：
 * 菌种格、菌名和状态、能做哪些工艺、最近两分钟的密度曲线，右边设定温度。菌名从客户端的方块实体读（接种时同步过来），
 * 别的读数走界面数据。
 */
public class BioreactorScreen extends RecipeMachineScreen {
    private static final int INFO_X = 114;
    private static final int STATUS_Y = 76;
    private static final int TIME_Y = 88;
    private static final int MODE_Y = 98;
    private static final int MODE_WIDTH = 96;
    /** 培养物那一块。 */
    private static final int PANEL_Y = 112;
    private static final int NAME_X = 28;
    private static final int NAME_Y = 116;
    private static final int STATE_Y = 127;
    private static final int PROCESS_Y = 138;
    private static final int CURVE_X = 8;
    private static final int CURVE_Y = 150;
    private static final int CURVE_WIDTH = BioreactorBlockEntity.HISTORY_POINTS * 2 + 2;
    private static final int CURVE_HEIGHT = 24;
    private static final int TEMP_X = 100;
    private static final int TEMP_Y = 150;
    private static final int BUTTON_Y = 160;
    private static final int BUTTON_WIDTH = 26;
    private static final int[] STEPS = {-10, -1, 1, 10};
    private static final int FLUSH_X = 172;
    private static final int FLUSH_Y = 125;
    private static final int FLUSH_WIDTH = 38;
    private static final int CULTURE_COLOR = 0xFF6CC46A;

    public BioreactorScreen(MachineMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, RecipeMachines.BIOREACTOR);
    }

    private @Nullable BioreactorBlockEntity blockEntity() {
        if (this.minecraft == null || this.minecraft.level == null) {
            return null;
        }
        return this.minecraft.level.getBlockEntity(this.menu.pos()) instanceof BioreactorBlockEntity reactor ? reactor : null;
    }

    /** 罐里（或者上一罐）的菌；空罐为 {@code null}。 */
    private @Nullable Organism organism() {
        BioreactorBlockEntity reactor = this.blockEntity();
        return reactor == null || this.menu.data(BioreactorBlockEntity.CULTURE) == 0 ? null : reactor.lastOrganism();
    }

    private Culture.@Nullable State state() {
        int value = this.menu.data(BioreactorBlockEntity.CULTURE);
        return value <= 0 || value > Culture.State.values().length ? null : Culture.State.values()[value - 1];
    }

    private boolean alive() {
        Culture.State state = this.state();
        return state != null && state != Culture.State.LOST;
    }

    private @Nullable RecipeHolder<MachineRecipe> selected() {
        if (this.minecraft == null || this.minecraft.level == null) {
            return null;
        }
        List<RecipeHolder<MachineRecipe>> recipes = MachineRecipes.list(this.minecraft.level, MachineRecipe.Kind.BIOREACTOR);
        int index = this.menu.data(RecipeMachineBlockEntity.SELECTED) - 1;
        return index >= 0 && index < recipes.size() ? recipes.get(index) : null;
    }

    private @Nullable BioProcess process() {
        RecipeHolder<MachineRecipe> recipe = this.selected();
        return recipe == null ? null : BioRecipes.process(recipe.id().identifier());
    }

    // ---- 画

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractBackground(graphics, mouseX, mouseY, a);
        int x = this.leftPos;
        int y = this.topPos;
        graphics.fill(x + 6, y + PANEL_Y, x + this.imageWidth - 6, y + PANEL_Y + 1, 0x602E7A92);

        // 密度曲线：一个点两像素宽，从下往上填
        int left = x + CURVE_X;
        int top = y + CURVE_Y;
        graphics.fill(left, top, left + CURVE_WIDTH, top + CURVE_HEIGHT, 0xFF24404D);
        graphics.fill(left + 1, top + 1, left + CURVE_WIDTH - 1, top + CURVE_HEIGHT - 1, INSET);
        // 满罐和一半两道虚线
        for (int i = left + 1; i < left + CURVE_WIDTH - 1; i += 3) {
            graphics.fill(i, top + 2, i + 1, top + 3, 0xFF24404D);
            graphics.fill(i, top + CURVE_HEIGHT / 2, i + 1, top + CURVE_HEIGHT / 2 + 1, 0xFF1A2E37);
        }
        int inner = CURVE_HEIGHT - 3;
        for (int i = 0; i < BioreactorBlockEntity.HISTORY_POINTS; i++) {
            int density = this.menu.data(BioreactorBlockEntity.HISTORY + i);
            int height = Math.round(inner * Math.min(1000, density) / 1000F);
            if (height <= 0) {
                continue;
            }
            int columnX = left + 1 + i * 2;
            int bottom = top + CURVE_HEIGHT - 1;
            graphics.fill(columnX, bottom - height, columnX + 2, bottom, 0xFF2F6B3A);
            graphics.fill(columnX, bottom - height, columnX + 2, bottom - height + 1, CULTURE_COLOR);
        }

        // 菌种格空着、罐也空着时画一管菌种的虚影
        if (this.menu.getSlot(BioreactorBlockEntity.STRAIN_SLOT).getItem().isEmpty() && !this.alive()) {
            graphics.fakeItem(new ItemStack(UniverseItems.STRAIN_TUBE.get()), x + 8, y + 116);
            graphics.nextStratum();
            graphics.fill(x + 8, y + 116, x + 24, y + 132, 0x900A1015);
        }
    }

    @Override
    protected void extractInfo(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        this.powerLine(graphics, 8, 20);
        RecipeHolder<MachineRecipe> selected = this.selected();
        int status = this.menu.data(RecipeMachineBlockEntity.STATUS);
        String key = switch (status) {
            case RecipeMachineBlockEntity.RUNNING -> "recipe_machine.running";
            case RecipeMachineBlockEntity.NO_INPUT -> "recipe_machine.no_input";
            case RecipeMachineBlockEntity.OUTPUT_FULL -> "recipe_machine.output_full";
            case RecipeMachineBlockEntity.NO_POWER -> "recipe_machine.no_power";
            case RecipeMachineBlockEntity.LOCKED -> "recipe_machine.locked";
            case BioreactorBlockEntity.NO_CULTURE -> "bioreactor.status.no_culture";
            case BioreactorBlockEntity.WRONG_STRAIN -> "bioreactor.status.wrong_strain";
            case BioreactorBlockEntity.BAD_TEMPERATURE -> "bioreactor.status.bad_temperature";
            default -> "recipe_machine.no_recipe";
        };
        int color = switch (status) {
            case RecipeMachineBlockEntity.RUNNING -> GOOD;
            case RecipeMachineBlockEntity.NO_INPUT, RecipeMachineBlockEntity.OUTPUT_FULL, BioreactorBlockEntity.NO_CULTURE,
                    BioreactorBlockEntity.BAD_TEMPERATURE -> WARN;
            case RecipeMachineBlockEntity.NO_POWER, RecipeMachineBlockEntity.LOCKED, BioreactorBlockEntity.WRONG_STRAIN -> BAD;
            default -> DIM;
        };
        if (selected == null) {
            key = "recipe_machine.no_recipe";
            color = DIM;
        }
        graphics.text(this.font, Component.translatable("gui.universe." + key), INFO_X, STATUS_Y, color, false);
        if (selected != null) {
            graphics.text(this.font, Component.translatable("gui.universe.bioreactor.time",
                    String.format(Locale.ROOT, "%.1f", selected.value().time() / 20.0)), INFO_X, TIME_Y, DIM, false);
            int value = this.menu.data(RecipeMachineBlockEntity.PROLIFERATION);
            this.modeButton(graphics, INFO_X, MODE_Y, MODE_WIDTH, value, (value & 16) != 0, mouseX, mouseY);
        }
        this.culturePanel(graphics, mouseX, mouseY);
    }

    private void culturePanel(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        Organism organism = this.organism();
        Culture.State state = this.state();
        int density = this.menu.data(BioreactorBlockEntity.DENSITY);
        if (organism == null || state == null) {
            graphics.text(this.font, Component.translatable("gui.universe.bioreactor.empty"), NAME_X, NAME_Y, DIM, false);
            graphics.text(this.font, Component.translatable("gui.universe.bioreactor.empty_hint"), NAME_X, STATE_Y, DIM, false);
        } else {
            graphics.text(this.font, this.fit(MicrobeText.strain(organism.strain()), 182), NAME_X, NAME_Y,
                    state == Culture.State.LOST ? DIM : TEXT, false);
            int stateColor = switch (state) {
                case GROWING -> GOOD;
                case STARVING -> WARN;
                case DYING, LOST -> BAD;
            };
            Component line = state == Culture.State.LOST
                    ? Component.translatable("gui.universe.bioreactor.state.lost")
                    : Component.translatable("gui.universe.bioreactor.state_density",
                            Component.translatable("gui.universe.bioreactor.state." + state.name().toLowerCase(Locale.ROOT)),
                            percent(density / 1000.0));
            graphics.text(this.font, line, NAME_X, STATE_Y, stateColor, false);
            this.textButton(graphics, FLUSH_X, FLUSH_Y, FLUSH_WIDTH, Component.translatable("gui.universe.bioreactor.flush"), TEXT, mouseX, mouseY);
        }

        // 能做哪些工艺；选的那种做不了时写缺哪个基因
        BioProcess process = this.process();
        boolean capable = (this.menu.data(BioreactorBlockEntity.FLAGS) & 4) != 0;
        if (this.alive() && process != null && !capable) {
            graphics.text(this.font, this.fit(Component.translatable("gui.universe.bioreactor.needs_gene", MicrobeText.gene(process.gene())), 202),
                    CURVE_X, PROCESS_Y, BAD, false);
        } else if (this.alive()) {
            graphics.text(this.font, this.fit(Component.translatable("gui.universe.bioreactor.can_do", this.processes()), 202),
                    CURVE_X, PROCESS_Y, DIM, false);
        }

        // 温度：设定值和生长速度，旁边写光从哪来
        int setpoint = this.menu.data(BioreactorBlockEntity.SETPOINT);
        Component temperature = this.alive()
                ? Component.translatable("gui.universe.bioreactor.setpoint_growth", ReactorRecipes.celsius(setpoint),
                        percent(this.menu.data(BioreactorBlockEntity.GROWTH) / 1000.0))
                : Component.translatable("gui.universe.bioreactor.setpoint", ReactorRecipes.celsius(setpoint));
        graphics.text(this.font, temperature, TEMP_X, TEMP_Y, TEXT, false);
        if (process != null && process.needsLight() && this.alive()) {
            int flags = this.menu.data(BioreactorBlockEntity.FLAGS);
            Component light = Component.translatable((flags & 1) != 0 ? "gui.universe.bioreactor.sunlight" : "gui.universe.bioreactor.grow_lights");
            graphics.text(this.font, light, this.imageWidth - 8 - this.font.width(light), PROCESS_Y, (flags & 1) != 0 ? WARN : ACCENT, false);
        }
        for (int i = 0; i < STEPS.length; i++) {
            int step = STEPS[i];
            this.textButton(graphics, TEMP_X + i * (BUTTON_WIDTH + 1), BUTTON_Y, BUTTON_WIDTH,
                    Component.literal((step > 0 ? "+" : "−") + Math.abs(step)), TEXT, mouseX, mouseY);
        }
    }

    /** 罐里的菌能跑的工艺，用顿号连起来；一种也没有写"无"。 */
    private Component processes() {
        int bits = this.menu.data(BioreactorBlockEntity.PROCESSES);
        MutableComponent list = Component.empty();
        boolean first = true;
        for (BioProcess process : BioProcess.values()) {
            if ((bits >> process.ordinal() & 1) == 0) {
                continue;
            }
            if (!first) {
                list.append(Component.translatable("microbe.universe.separator"));
            }
            first = false;
            list.append(Component.translatable("bioprocess.universe." + process.id()));
        }
        return first ? Component.translatable("microbe.universe.none") : list;
    }

    /** 放不下时截掉，末尾加省略号；鼠标指着时提示里有全文。 */
    private Component fit(Component text, int width) {
        if (this.font.width(text) <= width) {
            return text;
        }
        String ellipsis = "…";
        String plain = this.font.substrByWidth(text, width - this.font.width(ellipsis)).getString();
        return Component.literal(plain + ellipsis).withStyle(text.getStyle());
    }

    // ---- 提示

    @Override
    protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractTooltip(graphics, mouseX, mouseY);
        if (!this.menu.getCarried().isEmpty()) {
            return;
        }
        int localX = mouseX - this.leftPos;
        int localY = mouseY - this.topPos;
        List<Component> lines = new ArrayList<>();
        Organism organism = this.organism();
        if (organism != null && localX >= NAME_X && localX < FLUSH_X && localY >= NAME_Y && localY < NAME_Y + 9) {
            lines.add(MicrobeText.strain(organism.strain()).copy().withStyle(ChatFormatting.AQUA));
            lines.add(Component.translatable("gui.universe.bioreactor.genes", MicrobeText.genes(organism.genes())).withStyle(ChatFormatting.GRAY));
            double hours = BioTime.hours(organism.doublingTicks());
            lines.add(Component.translatable("gui.universe.bioreactor.doubling", MicrobeText.hours(hours),
                    String.format(Locale.ROOT, "%.0f", organism.doublingTicks() / 20)).withStyle(ChatFormatting.GRAY));
            if (organism.productivity() != 1) {
                lines.add(Component.translatable("gui.universe.bioreactor.productivity", percent(organism.productivity()))
                        .withStyle(ChatFormatting.GRAY));
            }
        } else if (localX >= CURVE_X && localX < CURVE_X + CURVE_WIDTH && localY >= CURVE_Y && localY < CURVE_Y + CURVE_HEIGHT) {
            lines.add(Component.translatable("gui.universe.bioreactor.curve"));
            lines.add(Component.translatable("gui.universe.bioreactor.curve_hint").withStyle(ChatFormatting.GRAY));
        } else if (localX >= TEMP_X && localX < this.imageWidth - 6 && localY >= TEMP_Y && localY < TEMP_Y + 9) {
            int tank = this.menu.data(BioreactorBlockEntity.TANK);
            int ambient = this.menu.data(BioreactorBlockEntity.AMBIENT);
            lines.add(Component.translatable("gui.universe.bioreactor.tank", ReactorRecipes.celsius(tank)));
            lines.add(Component.translatable("gui.universe.bioreactor.ambient", ReactorRecipes.celsius(ambient)).withStyle(ChatFormatting.GRAY));
            if (organism != null) {
                Tolerance tolerance = organism.tolerance();
                lines.add(Component.translatable("gui.universe.bioreactor.range", ReactorRecipes.celsius(tolerance.minK()),
                        ReactorRecipes.celsius(tolerance.maxK()), ReactorRecipes.celsius(tolerance.optK())).withStyle(ChatFormatting.GRAY));
            }
            lines.add(Component.translatable("gui.universe.bioreactor.temperature_hint").withStyle(ChatFormatting.DARK_GRAY));
        } else if (organism != null && overMode(localX, localY, FLUSH_X, FLUSH_Y, FLUSH_WIDTH)) {
            lines.add(Component.translatable("gui.universe.bioreactor.flush_hint"));
        } else if (localX >= 8 && localX < 24 && localY >= 116 && localY < 132
                && this.menu.getSlot(BioreactorBlockEntity.STRAIN_SLOT).getItem().isEmpty()) {
            lines.add(Component.translatable("gui.universe.bioreactor.strain_slot"));
            lines.add(Component.translatable("gui.universe.bioreactor.strain_slot_hint").withStyle(ChatFormatting.GRAY));
        }
        if (!lines.isEmpty()) {
            graphics.setTooltipForNextFrame(this.font, lines, Optional.empty(), mouseX, mouseY);
        }
    }

    // ---- 操作

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            double localX = event.x() - this.leftPos;
            double localY = event.y() - this.topPos;
            for (int i = 0; i < STEPS.length; i++) {
                if (overMode(localX, localY, TEMP_X + i * (BUTTON_WIDTH + 1), BUTTON_Y, BUTTON_WIDTH)) {
                    int step = STEPS[i];
                    int setpoint = this.menu.data(BioreactorBlockEntity.SETPOINT);
                    int target = (int) Math.clamp(setpoint + step, Isolation.MIN_SETTING_K, Isolation.MAX_SETTING_K);
                    if (target != setpoint) {
                        this.clickMode(BioreactorBlockEntity.TEMPERATURE_BUTTON + target);
                    }
                    return true;
                }
            }
            if (this.organism() != null && overMode(localX, localY, FLUSH_X, FLUSH_Y, FLUSH_WIDTH)) {
                this.clickMode(BioreactorBlockEntity.FLUSH_BUTTON);
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }
}
