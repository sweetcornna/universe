package io.github.sweetcornna.universe.client.microbe;

import java.util.Set;

import io.github.sweetcornna.universe.client.factory.MachineScreen;
import io.github.sweetcornna.universe.core.microbe.Gene;
import io.github.sweetcornna.universe.core.microbe.GeneLibrary;
import io.github.sweetcornna.universe.factory.MachineMenu;
import io.github.sweetcornna.universe.microbe.BioMachineBlockEntity;
import io.github.sweetcornna.universe.microbe.GeneLibraries;
import io.github.sweetcornna.universe.microbe.MicrobeText;
import io.github.sweetcornna.universe.microbe.SequencerBlockEntity;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/** 测序仪的界面（ADR-0036 §五）：在测什么、进度、上一次测出了什么、基因库里有多少。 */
public class SequencerScreen extends MachineScreen {
    private static final int LEFT = 30;
    private static final int WIDTH = 148;

    public SequencerScreen(MachineMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    protected void extractInfo(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int center = LEFT + WIDTH / 2;
        ItemStack input = this.menu.getSlot(BioMachineBlockEntity.INPUT).getItem();
        int statusIndex = this.menu.data(BioMachineBlockEntity.STATUS);
        SequencerBlockEntity.Status[] statuses = SequencerBlockEntity.Status.values();
        SequencerBlockEntity.Status status = statusIndex >= 0 && statusIndex < statuses.length ? statuses[statusIndex] : SequencerBlockEntity.Status.EMPTY;
        Component line = switch (status) {
            case EMPTY -> Component.translatable("gui.universe.sequencer.empty");
            case NO_CHIP -> Component.translatable("gui.universe.sequencer.no_chip");
            case OUTPUT_FULL -> Component.translatable("gui.universe.sequencer.output_full");
            case WORKING -> Component.translatable("gui.universe.sequencer.working", input.getHoverName());
        };
        graphics.centeredText(this.font, this.font.substrByWidth(line, WIDTH + 20).getString(), center, 22,
                status == SequencerBlockEntity.Status.WORKING ? TEXT : status == SequencerBlockEntity.Status.EMPTY ? DIM : WARN);
        int total = this.menu.data(BioMachineBlockEntity.TOTAL);
        bar(graphics, LEFT, 34, WIDTH, 4, total <= 0 ? 0 : this.menu.data(BioMachineBlockEntity.PROGRESS) / (float) total, GOOD);

        int last = this.menu.data(SequencerBlockEntity.LAST);
        Set<Gene> fresh = SequencerBlockEntity.unmask(this.menu.data(SequencerBlockEntity.LAST_GENES));
        Component result = null;
        int color = DIM;
        if (last == SequencerBlockEntity.Last.KNOWN.ordinal()) {
            result = Component.translatable("gui.universe.sequencer.last_known");
        } else if (last == SequencerBlockEntity.Last.NEW_GENES.ordinal()) {
            result = Component.translatable("gui.universe.sequencer.last_genes", MicrobeText.genes(fresh));
            color = GOOD;
        } else if (last == SequencerBlockEntity.Last.NEW_STRAIN.ordinal()) {
            result = fresh.isEmpty() ? Component.translatable("gui.universe.sequencer.last_strain")
                    : Component.translatable("gui.universe.sequencer.last_strain_genes", MicrobeText.genes(fresh));
            color = GOOD;
        }
        int y = 46;
        if (result != null) {
            for (var part : this.font.split(result, WIDTH + 20)) {
                graphics.text(this.font, part, LEFT - 10, y, color, false);
                y += 10;
            }
        }
        GeneLibrary library = GeneLibraries.client();
        graphics.text(this.font, Component.translatable("gui.universe.sequencer.library", library.genes().size(), Gene.values().length,
                library.strains().size()), LEFT - 10, Math.max(y + 2, 60), DIM, false);
        this.powerLine(graphics, LEFT - 10, 74);
    }
}
