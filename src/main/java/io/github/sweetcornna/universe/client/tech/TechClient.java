package io.github.sweetcornna.universe.client.tech;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.client.UniverseKeys;
import io.github.sweetcornna.universe.network.OpenTechPayload;
import io.github.sweetcornna.universe.registry.UniverseMenus;
import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/** 技术模块的客户端部分（ADR-0014）：配置界面、按键、图标的品级颜色。 */
public final class TechClient {
    private TechClient() {
    }

    public static void registerEvents(IEventBus modBus, IEventBus gameBus) {
        modBus.addListener(RegisterMenuScreensEvent.class, event -> event.register(UniverseMenus.TECH.get(), TechScreen::new));
        modBus.addListener(RegisterColorHandlersEvent.ItemTintSources.class,
                event -> event.register(Universe.id("module_grade"), ModuleGradeTint.MAP_CODEC));
        gameBus.addListener(ClientTickEvent.Post.class, event -> {
            Minecraft minecraft = Minecraft.getInstance();
            while (UniverseKeys.TECH.consumeClick()) {
                if (minecraft.gui.screen() == null && minecraft.player != null) {
                    ClientPacketDistributor.sendToServer(OpenTechPayload.INSTANCE);
                }
            }
        });
    }
}
