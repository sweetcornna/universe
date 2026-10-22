package io.github.sweetcornna.universe.client.creature;

import java.util.Optional;

import io.github.sweetcornna.universe.block.CreatureEggItem;
import io.github.sweetcornna.universe.block.EggSequencerBlockEntity;
import io.github.sweetcornna.universe.client.factory.MachineScreen;
import io.github.sweetcornna.universe.core.ecology.CreatureTraits;
import io.github.sweetcornna.universe.core.ecology.GeneEdit;
import io.github.sweetcornna.universe.entity.CreatureEggs;
import io.github.sweetcornna.universe.entity.CreatureEggs.Egg;
import io.github.sweetcornna.universe.factory.MachineMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * 卵序列仪的界面（ADR-0026 §九）：中间写这份材料改什么、进度、改前改后；下面一行是卵现在的性状。
 * 都按格子里的东西在客户端算，只有进度从服务端来。
 */
public class EggSequencerScreen extends MachineScreen {
    private static final int CENTER = 100;
    private static final int BAR_X = 36;
    private static final int BAR_WIDTH = 128;

    public EggSequencerScreen(MachineMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    protected void extractInfo(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        ItemStack eggStack = this.menu.getSlot(EggSequencerBlockEntity.EGG).getItem();
        ItemStack material = this.menu.getSlot(EggSequencerBlockEntity.MATERIAL).getItem();
        ItemStack output = this.menu.getSlot(EggSequencerBlockEntity.OUTPUT).getItem();
        Egg egg = CreatureEggs.egg(eggStack);
        Optional<GeneEdit> edit = CreatureEggs.edit(material);
        edit.ifPresent(e -> graphics.centeredText(this.font, describe(e), CENTER, 24, ACCENT));
        float share = this.menu.data(EggSequencerBlockEntity.PROGRESS) / (float) GeneEdit.TICKS;
        bar(graphics, BAR_X, 38, BAR_WIDTH, 4, share, GOOD);

        Component status;
        int color = WARN;
        if (eggStack.isEmpty()) {
            status = Component.translatable("gui.universe.egg_sequencer.no_egg");
            color = DIM;
        } else if (egg == null) {
            status = Component.translatable("gui.universe.egg_sequencer.infertile");
            color = BAD;
        } else if (edit.isEmpty()) {
            status = Component.translatable("gui.universe.egg_sequencer.no_material");
            color = DIM;
        } else {
            Optional<CreatureTraits> result = edit.get().apply(egg.traits());
            ItemStack product = EggSequencerBlockEntity.product(eggStack, material);
            if (result.isEmpty()) {
                status = Component.translatable("gui.universe.egg_sequencer.no_change");
            } else if (!output.isEmpty() && (!ItemStack.isSameItemSameComponents(output, product) || output.getCount() >= output.getMaxStackSize())) {
                status = Component.translatable("gui.universe.egg_sequencer.output_full");
            } else {
                status = change(egg.traits(), result.get());
                color = TEXT;
            }
        }
        graphics.centeredText(this.font, status, CENTER, 48, color);

        if (egg != null) {
            CreatureTraits traits = egg.traits();
            Component summary = Component.translatable("gui.universe.egg_sequencer.egg", egg.speciesName(), CreatureEggItem.size(traits.size()),
                    CreatureEggItem.temperament(traits.temperament()));
            if (traits.tint() != CreatureTraits.NO_TINT) {
                summary = Component.empty().append(summary).append(" ").append(CreatureEggItem.swatch(traits.tint()));
            }
            graphics.centeredText(this.font, summary, this.imageWidth / 2, 70, DIM);
        }
    }

    /** 这份材料改什么。 */
    private static Component describe(GeneEdit edit) {
        return switch (edit) {
            case GeneEdit.Resize resize -> Component.translatable(resize.steps() > 0 ? "gui.universe.egg_sequencer.grow"
                    : "gui.universe.egg_sequencer.shrink");
            case GeneEdit.Recolor recolor -> Component.translatable("gui.universe.egg_sequencer.recolor", CreatureEggItem.swatch(recolor.tint()));
            case GeneEdit.Temper temper -> Component.translatable("gui.universe.egg_sequencer.temper",
                    CreatureEggItem.temperament(temper.temperament()));
        };
    }

    /** 改前改后：只写变了的那一项。 */
    private static Component change(CreatureTraits before, CreatureTraits after) {
        if (before.size() != after.size()) {
            return Component.translatable("gui.universe.egg_sequencer.change", CreatureEggItem.size(before.size()),
                    CreatureEggItem.size(after.size()));
        }
        if (before.tint() != after.tint()) {
            return Component.translatable("gui.universe.egg_sequencer.change", tint(before.tint()), tint(after.tint()));
        }
        return Component.translatable("gui.universe.egg_sequencer.change", CreatureEggItem.temperament(before.temperament()),
                CreatureEggItem.temperament(after.temperament()));
    }

    private static Component tint(int tint) {
        return tint == CreatureTraits.NO_TINT ? Component.translatable("gui.universe.egg_sequencer.natural") : CreatureEggItem.swatch(tint);
    }
}
