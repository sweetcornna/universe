package io.github.sweetcornna.universe.client.darkfog;

import io.github.sweetcornna.universe.client.TextWrap;
import io.github.sweetcornna.universe.client.factory.MachineScreen;
import io.github.sweetcornna.universe.darkfog.defense.JammerTowerBlockEntity;
import io.github.sweetcornna.universe.darkfog.defense.SignalTowerBlockEntity;
import io.github.sweetcornna.universe.factory.MachineMenu;
import io.github.sweetcornna.universe.factory.power.TeslaTowerBlockEntity;
import io.github.sweetcornna.universe.registry.UniverseEntities;
import io.github.sweetcornna.universe.registry.UniverseMenus;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/** 信号塔、干扰塔的界面和扔出去的单元（ADR-0028 §五）。 */
public final class DarkFogSupportClient {
    private DarkFogSupportClient() {
    }

    public static void registerEvents(IEventBus modBus) {
        modBus.addListener(RegisterMenuScreensEvent.class, event -> {
            event.register(UniverseMenus.SIGNAL_TOWER.menuType(), SignalTowerScreen::new);
            event.register(UniverseMenus.JAMMER_TOWER.menuType(), JammerTowerScreen::new);
        });
        modBus.addListener(EntityRenderersEvent.RegisterRenderers.class,
                event -> event.registerEntityRenderer(UniverseEntities.THROWN_UNIT.get(), ThrownItemRenderer::new));
    }

    /** 信号塔：电网的情况、嘲讽范围里有几个黑雾。 */
    public static final class SignalTowerScreen extends MachineScreen {
        public SignalTowerScreen(MachineMenu menu, Inventory inventory, Component title) {
            super(menu, inventory, title);
        }

        @Override
        protected void extractInfo(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
            this.powerLine(graphics, 8, 22);
            graphics.text(this.font, Component.translatable("gui.universe.tesla_tower.grid",
                    this.menu.data(TeslaTowerBlockEntity.TOWERS), this.menu.data(TeslaTowerBlockEntity.DEVICES)), 8, 34, TEXT, false);
            if (this.menu.data(SignalTowerBlockEntity.POWERED) == 0) {
                graphics.text(this.font, Component.translatable("gui.universe.signal_tower.unpowered"), 8, 46, BAD, false);
            } else {
                int taunted = this.menu.data(SignalTowerBlockEntity.TAUNTED);
                graphics.text(this.font, Component.translatable("gui.universe.signal_tower.taunting", taunted), 8, 46,
                        taunted > 0 ? WARN : GOOD, false);
            }
            int range = this.menu.data(TeslaTowerBlockEntity.RANGE);
            int y = TextWrap.draw(graphics, this.font, Component.translatable("gui.universe.tesla_tower.range", range & 0xFF, range >> 8 & 0xFF),
                    8, 58, 160, DIM, false);
            TextWrap.draw(graphics, this.font, Component.translatable("gui.universe.signal_tower.hint"), 8, y + 4, 160, DIM, false);
        }
    }

    /** 干扰塔：在不在干扰、半径和减速、胶囊还能管几次。 */
    public static final class JammerTowerScreen extends MachineScreen {
        /** 读数折行的宽度：第一行和"胶囊"两个字同高，要在它左边停下。 */
        private static final int INFO_WIDTH = 146;

        public JammerTowerScreen(MachineMenu menu, Inventory inventory, Component title) {
            super(menu, inventory, title);
        }

        @Override
        protected void extractInfo(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
            this.powerLine(graphics, 8, 22);
            Component slot = Component.translatable("gui.universe.jammer_tower.capsule_slot");
            graphics.text(this.font, slot, 180 - this.font.width(slot) / 2, 34, DIM, false);
            int status = this.menu.data(JammerTowerBlockEntity.STATUS);
            Component line = switch (status) {
                case JammerTowerBlockEntity.JAMMING -> Component.translatable("gui.universe.jammer_tower.jamming",
                        this.menu.data(JammerTowerBlockEntity.TARGETS));
                case JammerTowerBlockEntity.NO_POWER -> Component.translatable("gui.universe.jammer_tower.no_power");
                default -> Component.translatable("gui.universe.jammer_tower.idle");
            };
            int color = switch (status) {
                case JammerTowerBlockEntity.JAMMING -> GOOD;
                case JammerTowerBlockEntity.NO_POWER -> BAD;
                default -> DIM;
            };
            int y = TextWrap.draw(graphics, this.font, line, 8, 36, INFO_WIDTH, color, false) + 3;
            y = TextWrap.draw(graphics, this.font, Component.translatable("gui.universe.jammer_tower.effect",
                    this.menu.data(JammerTowerBlockEntity.RADIUS), this.menu.data(JammerTowerBlockEntity.FACTOR)), 8, y, INFO_WIDTH, TEXT, false) + 3;
            int charge = this.menu.data(JammerTowerBlockEntity.CHARGE);
            boolean capsule = charge > 0 || !this.menu.getSlot(JammerTowerBlockEntity.CAPSULE_SLOT).getItem().isEmpty();
            y = TextWrap.draw(graphics, this.font, capsule ? Component.translatable("gui.universe.jammer_tower.capsule", charge)
                    : Component.translatable("gui.universe.jammer_tower.power_only"), 8, y, INFO_WIDTH, capsule ? TEXT : DIM, false) + 3;
            TextWrap.draw(graphics, this.font, Component.translatable("gui.universe.jammer_tower.hint"), 8, y, INFO_WIDTH, DIM, false);
        }
    }
}
