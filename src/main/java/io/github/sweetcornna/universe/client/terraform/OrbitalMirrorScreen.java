package io.github.sweetcornna.universe.client.terraform;

import java.util.Optional;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.sweetcornna.universe.client.ClientGalaxy;
import io.github.sweetcornna.universe.client.factory.MachineScreen;
import io.github.sweetcornna.universe.client.galaxy.GalaxyText;
import io.github.sweetcornna.universe.core.galaxy.BodyId;
import io.github.sweetcornna.universe.core.galaxy.Planet;
import io.github.sweetcornna.universe.core.terraform.Terraform;
import io.github.sweetcornna.universe.factory.MachineMenu;
import io.github.sweetcornna.universe.terraform.OrbitalMirrorBlockEntity;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** 轨道反射镜（ADR-0036 §六）：对着哪颗星球、现在是反射镜还是遮阳板、这颗星球上空一共几面，下面一个切换按钮。 */
public class OrbitalMirrorScreen extends MachineScreen {
    private static final int BUTTON_Y = 84;
    private static final int BUTTON_WIDTH = 120;

    public OrbitalMirrorScreen(MachineMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    private int buttonX() {
        return (this.imageWidth - BUTTON_WIDTH) / 2;
    }

    private boolean shade() {
        return this.menu.data(OrbitalMirrorBlockEntity.SHADE) != 0;
    }

    @Override
    protected void extractInfo(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int status = this.menu.data(OrbitalMirrorBlockEntity.STATUS);
        if (status == OrbitalMirrorBlockEntity.NOT_IN_SPACE) {
            graphics.text(this.font, Component.translatable("gui.universe.orbital_mirror.not_in_space"), 8, 22, BAD, false);
            return;
        }
        if (status == OrbitalMirrorBlockEntity.NO_PLANET) {
            graphics.text(this.font, Component.translatable("gui.universe.orbital_mirror.no_planet"), 8, 22, BAD, false);
            return;
        }
        graphics.text(this.font, Component.translatable("gui.universe.orbital_mirror.target", this.planetName()), 8, 22, ACCENT, false);
        boolean shade = this.shade();
        int kelvin = (int) Terraform.MIRROR_KELVIN;
        graphics.text(this.font, Component.translatable(shade ? "gui.universe.orbital_mirror.mode_shade" : "gui.universe.orbital_mirror.mode_mirror",
                kelvin), 8, 36, shade ? WARN : GOOD, false);
        int mirrors = this.menu.data(OrbitalMirrorBlockEntity.MIRRORS);
        int shades = this.menu.data(OrbitalMirrorBlockEntity.SHADES);
        graphics.text(this.font, Component.translatable("gui.universe.orbital_mirror.count", mirrors, shades), 8, 50, TEXT, false);
        int net = Math.min(mirrors, Terraform.MAX_MIRRORS) - Math.min(shades, Terraform.MAX_MIRRORS);
        double total = Terraform.MIRROR_KELVIN * net;
        graphics.text(this.font, Component.translatable("gui.universe.orbital_mirror.net", (total > 0 ? "+" : "") + GalaxyText.number(total, 0),
                Terraform.MAX_MIRRORS), 8, 62, DIM, false);
        this.textButton(graphics, this.buttonX(), BUTTON_Y, BUTTON_WIDTH,
                Component.translatable(shade ? "gui.universe.orbital_mirror.to_mirror" : "gui.universe.orbital_mirror.to_shade"), TEXT, mouseX, mouseY);
    }

    /** 对着的星球的名字（改过名的用新名字）。 */
    private String planetName() {
        int system = this.menu.data(OrbitalMirrorBlockEntity.SYSTEM);
        int planet = this.menu.data(OrbitalMirrorBlockEntity.PLANET);
        int moon = this.menu.data(OrbitalMirrorBlockEntity.MOON) - 1;
        if (system < 0 || planet < 0 || moon < BodyId.NONE) {
            return "?";
        }
        Optional<Planet> body = ClientGalaxy.galaxy().flatMap(galaxy -> galaxy.body(new BodyId(system, planet, moon)));
        return body.map(GalaxyText::planet).orElse("?");
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && this.menu.data(OrbitalMirrorBlockEntity.STATUS) == OrbitalMirrorBlockEntity.OK
                && overMode(event.x() - this.leftPos, event.y() - this.topPos, this.buttonX(), BUTTON_Y, BUTTON_WIDTH)) {
            this.clickMode(OrbitalMirrorBlockEntity.TOGGLE);
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }
}
