package io.github.sweetcornna.universe.client.darkfog;

import java.util.Locale;

import io.github.sweetcornna.universe.client.TextWrap;
import io.github.sweetcornna.universe.client.factory.MachineScreen;
import io.github.sweetcornna.universe.core.darkfog.AmmoKind;
import io.github.sweetcornna.universe.core.darkfog.TurretKind;
import io.github.sweetcornna.universe.darkfog.turret.TurretBlock;
import io.github.sweetcornna.universe.darkfog.turret.TurretBlockEntity;
import io.github.sweetcornna.universe.factory.MachineMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * 炮塔的界面（ADR-0028 §五）：电、在干什么（找目标、交战、没弹药）、装着的弹药还剩几发、射程、击毁数；右边一格弹药。
 */
public class TurretScreen extends MachineScreen {
    /** 左边读数的宽度：右边是弹药格，长的弹药名折到下一行。 */
    private static final int INFO_WIDTH = 158;

    public TurretScreen(MachineMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    private TurretKind kind() {
        if (this.minecraft != null && this.minecraft.level != null
                && this.minecraft.level.getBlockState(this.menu.pos()).getBlock() instanceof TurretBlock turret) {
            return turret.kind();
        }
        return TurretKind.GAUSS;
    }

    @Override
    protected void extractInfo(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        TurretKind kind = this.kind();
        this.powerLine(graphics, 8, 22);
        int status = this.menu.data(TurretBlockEntity.STATUS);
        boolean unpowered = this.menu.data(0) < 0;
        Component state = switch (status) {
            case TurretBlockEntity.ENGAGING -> Component.translatable("gui.universe.turret.engaging");
            case TurretBlockEntity.NO_AMMO -> Component.translatable("gui.universe.turret.no_ammo");
            default -> Component.translatable("gui.universe.turret.searching");
        };
        int color = unpowered ? DIM : status == TurretBlockEntity.ENGAGING ? GOOD : status == TurretBlockEntity.NO_AMMO ? BAD : TEXT;
        graphics.text(this.font, state, 8, 36, color, false);
        int y = 48;
        if (kind.usesAmmo()) {
            Component slot = Component.translatable("gui.universe.turret.ammo_slot");
            graphics.text(this.font, slot, 180 - this.font.width(slot) / 2, 34, DIM, false);
            int loaded = this.menu.data(TurretBlockEntity.LOADED);
            if (loaded > 0 && loaded <= AmmoKind.values().length) {
                AmmoKind ammo = AmmoKind.values()[loaded - 1];
                y = TextWrap.draw(graphics, this.font, Component.translatable("gui.universe.turret.loaded",
                        Component.translatable("item.universe." + ammo.id()), this.menu.data(TurretBlockEntity.ROUNDS), ammo.rounds()), 8, y, INFO_WIDTH,
                        TEXT, false) + 3;
            } else {
                graphics.text(this.font, Component.translatable("gui.universe.turret.empty"), 8, y, DIM, false);
                y += 12;
            }
        }
        graphics.text(this.font, Component.translatable("gui.universe.turret.range", (int) kind.range(),
                String.format(Locale.ROOT, "%.1f", kind.shotsPerSecond())), 8, y, DIM, false);
        y += 12;
        graphics.text(this.font, Component.translatable("gui.universe.turret.kills",
                String.format(Locale.ROOT, "%,d", this.menu.wide(TurretBlockEntity.KILLS))), 8, y, DIM, false);
        if (kind.groundOnly()) {
            y += 12;
            graphics.text(this.font, Component.translatable("gui.universe.turret.ground_only"), 8, y, DIM, false);
        }
    }
}
