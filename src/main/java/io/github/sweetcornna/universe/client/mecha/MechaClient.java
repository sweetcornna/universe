package io.github.sweetcornna.universe.client.mecha;

import io.github.sweetcornna.universe.client.UniverseKeys;
import io.github.sweetcornna.universe.network.MechaPayloads;
import io.github.sweetcornna.universe.registry.UniverseMenus;
import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/** 机甲的客户端部分（ADR-0023）：机甲面板和它的按键、无人机和机器人的画法。 */
public final class MechaClient {
    private MechaClient() {
    }

    public static void registerEvents(IEventBus modBus, IEventBus gameBus) {
        modBus.addListener(RegisterMenuScreensEvent.class, event -> event.register(UniverseMenus.MECHA.get(), MechaScreen::new));
        gameBus.addListener(ClientTickEvent.Post.class, event -> {
            Minecraft minecraft = Minecraft.getInstance();
            while (UniverseKeys.MECHA.consumeClick()) {
                if (minecraft.gui.screen() == null && minecraft.player != null) {
                    ClientPacketDistributor.sendToServer(MechaPayloads.Open.INSTANCE);
                }
            }
        });
        FlyersClient.registerEvents(modBus, gameBus);
    }
}
