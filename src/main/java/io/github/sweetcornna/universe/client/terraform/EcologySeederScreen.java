package io.github.sweetcornna.universe.client.terraform;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import io.github.sweetcornna.universe.client.ClientGalaxy;
import io.github.sweetcornna.universe.client.factory.MachineScreen;
import io.github.sweetcornna.universe.core.galaxy.Galaxy;
import io.github.sweetcornna.universe.core.galaxy.Planet;
import io.github.sweetcornna.universe.core.microbe.Gene;
import io.github.sweetcornna.universe.core.terraform.EffectiveEnvironment;
import io.github.sweetcornna.universe.core.terraform.Pioneer;
import io.github.sweetcornna.universe.core.terraform.Terraform;
import io.github.sweetcornna.universe.core.terraform.TerraformState;
import io.github.sweetcornna.universe.factory.MachineMenu;
import io.github.sweetcornna.universe.terraform.EcologySeederBlockEntity;
import io.github.sweetcornna.universe.terraform.TerraformRules;
import io.github.sweetcornna.universe.terraform.Terraforming;
import io.github.sweetcornna.universe.world.planet.Planets;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * 生态播种站（ADR-0036 §六）：上面写电、在不在播、浇水和这颗星球的生物量，下面列出放进来的菌或种苗要面对的条件
 * （光合、温度、辐射、酸、紫外、干旱），每一条要哪个基因、有没有，扛不住的标红。右边上面一格放菌种管或种苗，下面一格放水。
 * 条件按客户端收到的改造状态算，最多晚几秒。
 */
public class EcologySeederScreen extends MachineScreen {
    private static final int TABLE_Y = 96;
    private static final int TABLE_WIDTH = 194;
    private static final int ROW_HEIGHT = 11;

    public EcologySeederScreen(MachineMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    protected void extractInfo(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        this.powerLine(graphics, 8, 22);
        graphics.text(this.font, Component.translatable("gui.universe.ecology_seeder.seed_slot"), 208 - this.font.width(
                Component.translatable("gui.universe.ecology_seeder.seed_slot")), 39, DIM, false);
        graphics.text(this.font, Component.translatable("gui.universe.ecology_seeder.water_slot"), 208 - this.font.width(
                Component.translatable("gui.universe.ecology_seeder.water_slot")), 81, DIM, false);
        int status = this.menu.data(EcologySeederBlockEntity.STATUS);
        if (status == EcologySeederBlockEntity.NOT_TERRAFORMABLE) {
            graphics.text(this.font, Component.translatable("gui.universe.ecology_seeder.not_terraformable"), 8, 34, BAD, false);
            return;
        }
        Component line = switch (status) {
            case EcologySeederBlockEntity.NO_SKY -> Component.translatable("gui.universe.ecology_seeder.no_sky");
            case EcologySeederBlockEntity.NO_SEED -> Component.translatable("gui.universe.ecology_seeder.no_seed");
            case EcologySeederBlockEntity.CANNOT_SURVIVE -> Component.translatable("gui.universe.ecology_seeder.cannot_survive");
            default -> Component.translatable("gui.universe.ecology_seeder.seeding", this.menu.data(EcologySeederBlockEntity.PROGRESS) / 10);
        };
        int color = status == EcologySeederBlockEntity.OK ? TEXT : status == EcologySeederBlockEntity.NO_SEED ? DIM : BAD;
        graphics.text(this.font, line, 8, 34, color, false);
        graphics.text(this.font, Component.translatable("gui.universe.ecology_seeder.seeded",
                String.format(Locale.ROOT, "%,d", this.menu.wide(EcologySeederBlockEntity.SEEDED))), 8, 46, DIM, false);
        graphics.text(this.font, this.waterLine(), 8, 58, this.menu.data(EcologySeederBlockEntity.WATER_LEFT) > 0 ? GOOD : DIM, false);
        Optional<Planet> base = this.base();
        Optional<Galaxy> galaxy = ClientGalaxy.galaxy();
        if (base.isEmpty() || galaxy.isEmpty()) {
            return;
        }
        TerraformState state = Terraforming.clientState(base.get().id());
        double biomass = state.biomass();
        graphics.text(this.font, Component.translatable("gui.universe.ecology_seeder.biomass", biomass(biomass),
                TerraformRules.greeningRadius(biomass)), 8, 70, ACCENT, false);
        ItemStack seed = this.menu.getSlot(EcologySeederBlockEntity.SEED_SLOT).getItem();
        Optional<Set<Gene>> genes = EcologySeederBlockEntity.genes(seed);
        if (genes.isEmpty()) {
            return;
        }
        EffectiveEnvironment environment = Terraforming.clientEnvironment(galaxy.get(), base.get());
        graphics.text(this.font, Component.translatable("gui.universe.ecology_seeder.conditions", seed.getHoverName()), 8, TABLE_Y - 12, TEXT,
                false);
        this.rows(graphics, Pioneer.requirements(genes.get(), environment, state.irrigation()));
    }

    /** 一行一个条件：名字靠左，要的基因在中间，有没有靠右；扛不住的条件（太冷、太热、没水）中间写原因。 */
    private void rows(GuiGraphicsExtractor graphics, List<Pioneer.Requirement> requirements) {
        int geneX = 8 + TABLE_WIDTH * 2 / 5;
        for (int i = 0; i < requirements.size(); i++) {
            Pioneer.Requirement requirement = requirements.get(i);
            Pioneer.Condition condition = requirement.condition();
            int y = TABLE_Y + i * ROW_HEIGHT;
            String key = condition.name().toLowerCase(Locale.ROOT);
            graphics.text(this.font, Component.translatable("gui.universe.ecology_seeder.condition." + key), 8, y, TEXT, false);
            Component middle = condition.gene() == null
                    ? Component.translatable("gui.universe.ecology_seeder.fatal." + key)
                    : Component.translatable("gui.universe.ecology_seeder.needs", Component.translatable("gene.universe." + condition.gene().id()));
            graphics.text(this.font, middle, geneX, y, DIM, false);
            Component verdict = Component.translatable(requirement.met() ? "gui.universe.ecology_seeder.has"
                    : condition.gene() == null ? "gui.universe.ecology_seeder.dies" : "gui.universe.ecology_seeder.lacks");
            graphics.text(this.font, verdict, 8 + TABLE_WIDTH - this.font.width(verdict), y, requirement.met() ? GOOD : BAD, false);
        }
    }

    private Component waterLine() {
        if (this.menu.data(EcologySeederBlockEntity.NATURAL_WATER) == 1) {
            return Component.translatable("gui.universe.ecology_seeder.natural_water");
        }
        int left = this.menu.data(EcologySeederBlockEntity.WATER_LEFT);
        if (left <= 0) {
            return Component.translatable("gui.universe.ecology_seeder.no_water");
        }
        return Component.translatable("gui.universe.ecology_seeder.watering", left, this.menu.data(EcologySeederBlockEntity.STATIONS));
    }

    /** 生物量写成百分数：整颗星球长满是 100%。 */
    private static String biomass(double biomass) {
        double percent = biomass * 100;
        return String.format(Locale.ROOT, percent >= 10 ? "%.0f%%" : percent >= 0.1 ? "%.1f%%" : "%.2f%%", percent);
    }

    /** 站所在的这颗星球（不看改造状态），不能改造的为空。 */
    private Optional<Planet> base() {
        Minecraft minecraft = Minecraft.getInstance();
        Optional<Galaxy> galaxy = ClientGalaxy.galaxy();
        if (minecraft.level == null || galaxy.isEmpty()) {
            return Optional.empty();
        }
        return Planets.generatedPlanet(galaxy.get(), minecraft.level.dimension()).filter(Terraform::terraformable);
    }
}
