package io.github.sweetcornna.universe.client.darkfog.shield;

import io.github.sweetcornna.universe.client.TextWrap;
import io.github.sweetcornna.universe.client.factory.MachineScreen;
import io.github.sweetcornna.universe.core.darkfog.ShieldMath;
import io.github.sweetcornna.universe.darkfog.shield.PlanetaryShieldBlockEntity;
import io.github.sweetcornna.universe.factory.MachineMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * 行星护盾发生器的界面（ADR-0028 §八）：电、穹顶展开没有、存的能量（条上标着展开和罩满的两道线）、半径、
 * 这颗星球上有几台在工作。
 */
public class ShieldScreen extends MachineScreen {
    private static final int WIDTH = 184;
    /** 能量条的颜色。 */
    private static final int ENERGY = 0xFF4FC3F7;

    public ShieldScreen(MachineMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    protected void extractInfo(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        this.powerLine(graphics, 8, 22);
        int status = this.menu.data(PlanetaryShieldBlockEntity.STATUS);
        int count = this.menu.data(PlanetaryShieldBlockEntity.COUNT);
        Component state = switch (status) {
            case PlanetaryShieldBlockEntity.RAISED -> Component.translatable("gui.universe.planetary_shield.raised");
            case PlanetaryShieldBlockEntity.OVER_LIMIT -> Component.translatable("gui.universe.planetary_shield.over_limit", ShieldMath.MAX_PER_PLANET);
            default -> Component.translatable("gui.universe.planetary_shield.charging", joules((long) ShieldMath.RAISE_ENERGY));
        };
        int color = switch (status) {
            case PlanetaryShieldBlockEntity.RAISED -> GOOD;
            case PlanetaryShieldBlockEntity.OVER_LIMIT -> BAD;
            default -> WARN;
        };
        int y = TextWrap.draw(graphics, this.font, state, 8, 35, WIDTH, color, false) + 3;
        long energy = this.menu.data(PlanetaryShieldBlockEntity.ENERGY) * 1000L;
        graphics.text(this.font, Component.translatable("gui.universe.planetary_shield.energy", joules(energy), joules((long) ShieldMath.CAPACITY)),
                8, y, TEXT, false);
        y += 11;
        bar(graphics, 8, y, WIDTH, 5, (float) (energy / ShieldMath.CAPACITY), ENERGY);
        // 展开、罩满两道线
        for (double mark : new double[] {ShieldMath.RAISE_ENERGY, ShieldMath.FULL_ENERGY}) {
            int x = 8 + (int) Math.round(WIDTH * mark / ShieldMath.CAPACITY);
            graphics.fill(x, y - 2, x + 1, y + 7, energy >= mark ? 0xFFFFFFFF : DIM);
        }
        y += 10;
        int radius = this.menu.data(PlanetaryShieldBlockEntity.RADIUS);
        graphics.text(this.font, radius > 0 ? Component.translatable("gui.universe.planetary_shield.radius", radius, (int) ShieldMath.RADIUS)
                : Component.translatable("gui.universe.planetary_shield.down"), 8, y, radius > 0 ? TEXT : DIM, false);
        y += 12;
        graphics.text(this.font, Component.translatable("gui.universe.planetary_shield.count", count, ShieldMath.MAX_PER_PLANET), 8, y,
                count >= ShieldMath.MAX_PER_PLANET ? WARN : DIM, false);
        y += 13;
        TextWrap.draw(graphics, this.font, Component.translatable("gui.universe.planetary_shield.hint"), 8, y, WIDTH, DIM, false);
    }
}
