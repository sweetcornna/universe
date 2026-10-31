package io.github.sweetcornna.universe.client.terraform;

import java.util.Locale;
import java.util.Optional;

import io.github.sweetcornna.universe.client.ClientGalaxy;
import io.github.sweetcornna.universe.client.factory.MachineScreen;
import io.github.sweetcornna.universe.client.galaxy.GalaxyText;
import io.github.sweetcornna.universe.core.chem.Gas;
import io.github.sweetcornna.universe.core.galaxy.Galaxy;
import io.github.sweetcornna.universe.core.galaxy.Planet;
import io.github.sweetcornna.universe.core.terraform.EffectiveEnvironment;
import io.github.sweetcornna.universe.core.terraform.Terraform;
import io.github.sweetcornna.universe.factory.MachineMenu;
import io.github.sweetcornna.universe.terraform.ReleaseTowerBlockEntity;
import io.github.sweetcornna.universe.terraform.Terraforming;
import io.github.sweetcornna.universe.world.planet.Planets;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * 大气释放塔（ADR-0036 §六）：上面写电、在放什么、这颗星球现在的气压，下面一张门槛表（温度、毒性、大气柱、呼吸的气压、
 * 氧分压、二氧化碳），右边一列四个原料格。门槛表按客户端收到的改造状态算，最多晚几秒。
 */
public class ReleaseTowerScreen extends MachineScreen {
    private static final int TABLE_Y = 72;
    private static final int TABLE_WIDTH = 194;

    public ReleaseTowerScreen(MachineMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    protected void extractInfo(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        this.powerLine(graphics, 8, 22);
        int status = this.menu.data(ReleaseTowerBlockEntity.STATUS);
        if (status == ReleaseTowerBlockEntity.NOT_TERRAFORMABLE) {
            graphics.text(this.font, Component.translatable("gui.universe.release_tower.not_terraformable"), 8, 34, BAD, false);
            return;
        }
        Component line = switch (status) {
            case ReleaseTowerBlockEntity.NO_SKY -> Component.translatable("gui.universe.release_tower.no_sky");
            case ReleaseTowerBlockEntity.NO_GAS -> Component.translatable("gui.universe.release_tower.no_gas");
            default -> Component.translatable("gui.universe.release_tower.releasing", this.gasName());
        };
        graphics.text(this.font, line, 8, 34, status == ReleaseTowerBlockEntity.OK ? TEXT : status == ReleaseTowerBlockEntity.NO_GAS ? DIM : BAD,
                false);
        graphics.text(this.font, Component.translatable("gui.universe.release_tower.released",
                String.format(Locale.ROOT, "%,d", this.menu.wide(ReleaseTowerBlockEntity.RELEASED))), 8, 46, DIM, false);
        Optional<EffectiveEnvironment> environment = this.environment();
        if (environment.isEmpty()) {
            return;
        }
        EffectiveEnvironment env = environment.get();
        graphics.text(this.font, Component.translatable("gui.universe.terraform.planet", GalaxyText.planet(env.planet())), 8, 60, ACCENT, false);
        ThresholdRows.draw(graphics, this.font, 8, TABLE_Y, TABLE_WIDTH, Terraform.thresholds(env));
    }

    private Component gasName() {
        int index = this.menu.data(ReleaseTowerBlockEntity.GAS) - 1;
        if (index < 0 || index >= Gas.values().length) {
            return Component.empty();
        }
        return Component.translatable("item.universe." + Gas.values()[index].item());
    }

    /** 塔所在的这颗星球现用的环境（客户端收到的改造状态）。 */
    private Optional<EffectiveEnvironment> environment() {
        Minecraft minecraft = Minecraft.getInstance();
        Optional<Galaxy> galaxy = ClientGalaxy.galaxy();
        if (minecraft.level == null || galaxy.isEmpty()) {
            return Optional.empty();
        }
        Optional<Planet> base = Planets.generatedPlanet(galaxy.get(), minecraft.level.dimension()).filter(Terraform::terraformable);
        return base.map(planet -> Terraforming.clientEnvironment(galaxy.get(), planet));
    }
}
