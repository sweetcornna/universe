package io.github.sweetcornna.universe.client.microbe;

import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.sweetcornna.universe.client.factory.MachineScreen;
import io.github.sweetcornna.universe.core.microbe.Gene;
import io.github.sweetcornna.universe.core.microbe.GeneEditing;
import io.github.sweetcornna.universe.core.microbe.GeneLibrary;
import io.github.sweetcornna.universe.factory.MachineMenu;
import io.github.sweetcornna.universe.microbe.BioMachineBlockEntity;
import io.github.sweetcornna.universe.microbe.GeneEditorBlockEntity;
import io.github.sweetcornna.universe.microbe.GeneLibraries;
import io.github.sweetcornna.universe.microbe.MicrobeText;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * 基因编辑台的界面（ADR-0036 §五）：上面写在改什么、进度；下面四列基因按钮，基因库里有的才点得动，
 * 选中的绿色，要改的东西已经有的标暗。鼠标停在按钮上写基因的全名和用处。
 */
public class GeneEditorScreen extends MachineScreen {
    private static final int LEFT = 30;
    private static final int WIDTH = 196;
    private static final int GRID_X = 8;
    private static final int GRID_Y = 80;
    private static final int COLUMNS = 4;
    private static final int CELL_WIDTH = 58;
    private static final int STEP_X = 60;
    private static final int STEP_Y = 14;

    public GeneEditorScreen(MachineMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    private Optional<Gene> selected() {
        int index = this.menu.data(GeneEditorBlockEntity.SELECTED);
        return index >= 0 && index < Gene.values().length ? Optional.of(Gene.values()[index]) : Optional.empty();
    }

    @Override
    protected void extractInfo(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int center = LEFT + WIDTH / 2;
        ItemStack input = this.menu.getSlot(BioMachineBlockEntity.INPUT).getItem();
        Optional<GeneEditorBlockEntity.Target> target = GeneEditorBlockEntity.target(input);
        int statusIndex = this.menu.data(BioMachineBlockEntity.STATUS);
        GeneEditorBlockEntity.Status[] statuses = GeneEditorBlockEntity.Status.values();
        GeneEditorBlockEntity.Status status = statusIndex >= 0 && statusIndex < statuses.length ? statuses[statusIndex]
                : GeneEditorBlockEntity.Status.EMPTY;
        Component gene = this.selected().map(MicrobeText::gene).orElse(Component.empty());
        Component line = switch (status) {
            case EMPTY -> Component.translatable("gui.universe.gene_editor.empty");
            case NOT_EDITABLE -> Component.translatable("gui.universe.gene_editor.not_editable");
            case NO_GENE -> Component.translatable("gui.universe.gene_editor.no_gene");
            case NOT_IN_LIBRARY -> Component.translatable("gui.universe.gene_editor.not_in_library", gene);
            case ALREADY_PRESENT -> Component.translatable("gui.universe.gene_editor.already", gene);
            case FULL -> Component.translatable("gui.universe.gene_editor.full", Gene.MAX_ADDED);
            case NO_KIT -> Component.translatable("gui.universe.gene_editor.no_kit");
            case OUTPUT_FULL -> Component.translatable("gui.universe.gene_editor.output_full");
            case WORKING -> Component.translatable("gui.universe.gene_editor.working", gene, input.getHoverName());
        };
        int color = status == GeneEditorBlockEntity.Status.WORKING ? TEXT : status == GeneEditorBlockEntity.Status.EMPTY
                || status == GeneEditorBlockEntity.Status.NO_GENE ? DIM : WARN;
        graphics.centeredText(this.font, this.font.substrByWidth(line, WIDTH).getString(), center, 22, color);
        int total = this.menu.data(BioMachineBlockEntity.TOTAL);
        bar(graphics, LEFT, 34, WIDTH, 4, total <= 0 ? 0 : this.menu.data(BioMachineBlockEntity.PROGRESS) / (float) total, GOOD);

        Set<Gene> present = EnumSet.noneOf(Gene.class);
        for (Gene g : Gene.values()) {
            if (target.isPresent() && target.get().canAdd(g) == GeneEditing.Result.ALREADY_PRESENT) {
                present.add(g);
            }
        }
        if (target.isPresent()) {
            Component genes = Component.translatable("gui.universe.gene_editor.has", MicrobeText.genes(present));
            int y = 44;
            for (var part : this.font.split(genes, WIDTH)) {
                graphics.text(this.font, part, LEFT, y, DIM, false);
                y += 10;
                if (y > 64) {
                    break;
                }
            }
        }

        GeneLibrary library = GeneLibraries.client();
        Gene chosen = this.selected().orElse(null);
        for (Gene g : Gene.values()) {
            int x = GRID_X + g.ordinal() % COLUMNS * STEP_X;
            int y = GRID_Y + g.ordinal() / COLUMNS * STEP_Y;
            boolean known = library.has(g);
            int textColor = g == chosen ? GOOD : !known ? 0xFF4A5A62 : present.contains(g) ? DIM : TEXT;
            Component label = known ? MicrobeText.gene(g) : Component.literal("？？？");
            this.textButton(graphics, x, y, CELL_WIDTH, Component.literal(this.font.plainSubstrByWidth(label.getString(), CELL_WIDTH - 4)), textColor,
                    mouseX, mouseY);
        }
        this.powerLine(graphics, GRID_X, GRID_Y + 4 * STEP_Y + 2);
    }

    @Override
    protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractTooltip(graphics, mouseX, mouseY);
        Gene gene = this.geneAt(mouseX - this.leftPos, mouseY - this.topPos);
        if (gene != null) {
            Component text = GeneLibraries.client().has(gene)
                    ? Component.translatable("gui.universe.gene_editor.gene_tooltip", MicrobeText.gene(gene),
                            Component.translatable("gene.universe." + gene.id() + ".use"))
                    : Component.translatable("gui.universe.gene_editor.unknown_gene");
            graphics.setTooltipForNextFrame(text, mouseX, mouseY);
        }
    }

    private Gene geneAt(double x, double y) {
        for (Gene g : Gene.values()) {
            if (overMode(x, y, GRID_X + g.ordinal() % COLUMNS * STEP_X, GRID_Y + g.ordinal() / COLUMNS * STEP_Y, CELL_WIDTH)) {
                return g;
            }
        }
        return null;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            Gene gene = this.geneAt(event.x() - this.leftPos, event.y() - this.topPos);
            if (gene != null) {
                if (GeneLibraries.client().has(gene)) {
                    this.clickMode(gene.ordinal());
                }
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }
}
