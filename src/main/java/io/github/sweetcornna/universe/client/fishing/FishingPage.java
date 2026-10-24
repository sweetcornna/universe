package io.github.sweetcornna.universe.client.fishing;

import static io.github.sweetcornna.universe.client.factory.MachineScreen.ACCENT;
import static io.github.sweetcornna.universe.client.factory.MachineScreen.DIM;
import static io.github.sweetcornna.universe.client.factory.MachineScreen.TEXT;

import java.util.List;
import java.util.Optional;

import org.joml.Matrix3x2fStack;
import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.client.TextWrap;
import io.github.sweetcornna.universe.core.fishing.FishSpecies;
import io.github.sweetcornna.universe.core.fishing.FishTable;
import io.github.sweetcornna.universe.core.fishing.FishingLog;
import io.github.sweetcornna.universe.core.fishing.Habitat;
import io.github.sweetcornna.universe.fishing.FishText;
import io.github.sweetcornna.universe.registry.UniverseAttachments;
import io.github.sweetcornna.universe.registry.UniverseItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import net.minecraft.world.item.ItemStack;

/**
 * 发现日志的"钓鱼"页（ADR-0034 §一）：左边 8 种水域，各写钓到了几种；右边是选中水域的 12 格，钓到过的画图标，
 * 没钓到过的是剪影。下面写选中（或者鼠标指着）的那一种：钓到过的写名字、最重的一条多重、在哪颗星球钓的、钓了几条；
 * 没钓到过的写水域、档次和条件。
 */
public final class FishingPage {
    static final int LEFT_WIDTH = 150;
    private static final int ROW = 22;
    private static final int PADDING = 6;
    private static final int CELL = 42;
    private static final int COLUMNS = 6;
    private static final int SILHOUETTE = 0xFF0C1418;

    private final Font font;
    private Habitat selected = Habitat.FRESH;
    private @Nullable FishSpecies picked;
    private int listTop;
    private int gridLeft;
    private int gridTop;

    public FishingPage(Font font) {
        this.font = font;
    }

    private static FishingLog log() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.player == null ? FishingLog.EMPTY : minecraft.player.getData(UniverseAttachments.FISHING_LOG);
    }

    public void draw(GuiGraphicsExtractor graphics, int top, int bottom, int width, int mouseX, int mouseY) {
        FishingLog log = log();
        graphics.fill(0, top, LEFT_WIDTH, bottom, 0xC00A1218);
        graphics.fill(LEFT_WIDTH - 1, top, LEFT_WIDTH, bottom, 0x604FB4D0);
        graphics.fill(0, top, LEFT_WIDTH - 1, top + 14, 0xD0081016);
        graphics.text(this.font, Component.translatable("gui.universe.fishing.habitats"), PADDING, top + 3, TEXT, false);
        this.listTop = top + 14;
        int y = this.listTop;
        for (Habitat habitat : Habitat.values()) {
            boolean active = habitat == this.selected;
            boolean hover = !active && mouseX < LEFT_WIDTH && mouseY >= y && mouseY < y + ROW;
            if (active || hover) {
                graphics.fill(0, y, LEFT_WIDTH - 1, y + ROW, active ? 0x502A6A80 : 0x30FFFFFF);
            }
            if (active) {
                graphics.fill(0, y, 2, y + ROW, ACCENT);
            }
            graphics.fill(PADDING + 2, y + 7, PADDING + 10, y + 15, ARGB.opaque(habitat.color()));
            graphics.text(this.font, Component.translatable("fishing.universe.habitat." + habitat.key()), PADDING + 14, y + 7, TEXT, false);
            Component count = Component.literal(log.species(habitat) + "/" + FishTable.of(habitat).size());
            graphics.text(this.font, count, LEFT_WIDTH - PADDING - this.font.width(count), y + 7, log.species(habitat) == 12 ? ACCENT : DIM, false);
            y += ROW;
        }
        // 底下的总数：图鉴一行，钓上和放生一行（挤在一行里中文会从词中间折开）
        Component known = Component.translatable("gui.universe.fishing.species", log.species(), FishTable.ALL.size());
        Component landed = Component.translatable("gui.universe.fishing.caught", log.total(), log.released());
        int totalsY = TextWrap.draw(graphics, this.font, known, PADDING, Math.max(y + 8, bottom - 30), LEFT_WIDTH - PADDING * 2, DIM, false);
        TextWrap.draw(graphics, this.font, landed, PADDING, totalsY, LEFT_WIDTH - PADDING * 2, DIM, false);

        // 右边的格子
        int right = width;
        int gridWidth = COLUMNS * CELL;
        this.gridLeft = LEFT_WIDTH + Math.max(PADDING, (right - LEFT_WIDTH - gridWidth) / 2);
        this.gridTop = top + 24;
        graphics.text(this.font, Component.translatable("fishing.universe.habitat." + this.selected.key()), this.gridLeft, top + 8,
                ARGB.opaque(this.selected.color()) | 0xFF404040, false);
        Component where = Component.translatable("gui.universe.fishing.where." + this.selected.key());
        graphics.text(this.font, where, this.gridLeft + this.font.width(Component.translatable("fishing.universe.habitat." + this.selected.key())) + 8,
                top + 8, DIM, false);
        List<FishSpecies> species = FishTable.of(this.selected);
        FishSpecies hovered = null;
        for (int i = 0; i < species.size(); i++) {
            FishSpecies s = species.get(i);
            int cx = this.gridLeft + i % COLUMNS * CELL;
            int cy = this.gridTop + i / COLUMNS * CELL;
            boolean caught = log.caught(s);
            boolean over = mouseX >= cx && mouseX < cx + CELL - 4 && mouseY >= cy && mouseY < cy + CELL - 4;
            if (over) {
                hovered = s;
            }
            int frame = s == this.picked ? ACCENT : caught ? s.rarity().color() : 0xFF26343C;
            graphics.fill(cx, cy, cx + CELL - 4, cy + CELL - 4, ARGB.color(0xC0, ARGB.scaleRGB(this.selected.color(), 0.35F)));
            graphics.outline(cx, cy, CELL - 4, CELL - 4, over ? 0xFFFFFFFF : frame);
            Matrix3x2fStack pose = graphics.pose();
            pose.pushMatrix();
            pose.translate(cx + 3, cy + 3);
            pose.scale(2);
            if (caught) {
                graphics.item(new ItemStack(UniverseItems.FISH.get(s).get()), 0, 0);
            } else {
                graphics.blit(RenderPipelines.GUI_TEXTURED, Universe.id("textures/item/" + s.itemId() + ".png"), 0, 0, 0, 0, 16, 16, 16, 16,
                        SILHOUETTE);
            }
            pose.popMatrix();
        }
        FishSpecies shown = hovered != null ? hovered : this.picked;
        int detailTop = this.gridTop + 2 * CELL + 6;
        if (shown != null) {
            this.detail(graphics, shown, log.entry(shown), this.gridLeft, detailTop, Math.max(gridWidth, 200));
        } else {
            graphics.text(this.font, Component.translatable("gui.universe.fishing.pick"), this.gridLeft, detailTop, DIM, false);
        }
    }

    private void detail(GuiGraphicsExtractor graphics, FishSpecies species, Optional<FishingLog.Entry> entry, int x, int y, int width) {
        Component kind = Component.translatable("tooltip.universe.fish.kind", Component.translatable("fishing.universe.habitat." + species.habitat().key()),
                Component.translatable("fishing.universe.rarity." + species.rarity().key()),
                Component.translatable("fishing.universe.size." + species.size().key()));
        if (entry.isPresent()) {
            FishingLog.Entry e = entry.get();
            graphics.text(this.font, FishText.name(species), x, y, species.rarity().color(), false);
            graphics.text(this.font, kind, x, y + 12, DIM, false);
            graphics.text(this.font, Component.translatable("gui.universe.fishing.conditions", FishText.conditions(species)), x, y + 24, TEXT, false);
            boolean chinese = Minecraft.getInstance().getLanguageManager().getSelected().startsWith("zh");
            String planet = chinese ? e.planetZh() : e.planetEn();
            Component record = Component.translatable("gui.universe.fishing.record", FishText.weight(e.heaviest()), planet);
            graphics.text(this.font, record, x, y + 36, TEXT, false);
            graphics.text(this.font, Component.translatable("gui.universe.fishing.count", e.count()), x, y + 48, DIM, false);
        } else {
            graphics.text(this.font, Component.translatable("gui.universe.fishing.unknown"), x, y, DIM, false);
            graphics.text(this.font, kind, x, y + 12, DIM, false);
            graphics.text(this.font, Component.translatable("gui.universe.fishing.conditions", FishText.conditions(species)), x, y + 24, TEXT, false);
            TextWrap.draw(graphics, this.font, Component.translatable("gui.universe.fishing.hint"), x, y + 38, width, DIM, false);
        }
    }

    // ---- 输入

    /** 点了水域那一行或者一格鱼；返回点没点中。 */
    public boolean click(double x, double y) {
        if (x < LEFT_WIDTH) {
            int row = (int) ((y - this.listTop) / ROW);
            if (y >= this.listTop && row >= 0 && row < Habitat.values().length) {
                this.selected = Habitat.values()[row];
                this.picked = null;
                return true;
            }
            return false;
        }
        int column = (int) Math.floor((x - this.gridLeft) / CELL);
        int row = (int) Math.floor((y - this.gridTop) / CELL);
        List<FishSpecies> species = FishTable.of(this.selected);
        int index = row * COLUMNS + column;
        if (column >= 0 && column < COLUMNS && row >= 0 && index < species.size()) {
            this.picked = species.get(index);
            return true;
        }
        return false;
    }

    /** 滚轮在左边换水域。 */
    public void scroll(double x, double amount) {
        if (x < LEFT_WIDTH && amount != 0) {
            int next = Math.floorMod(this.selected.ordinal() - (int) Math.signum(amount), Habitat.values().length);
            this.selected = Habitat.values()[next];
            this.picked = null;
        }
    }
}
