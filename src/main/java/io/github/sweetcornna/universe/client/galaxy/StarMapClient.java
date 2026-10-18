package io.github.sweetcornna.universe.client.galaxy;

import io.github.sweetcornna.universe.client.UniverseKeys;
import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** 按星图键（默认 M）打开星图。 */
public final class StarMapClient {
    private StarMapClient() {
    }

    public static void registerEvents(IEventBus gameBus) {
        gameBus.addListener(ClientTickEvent.Post.class, event -> {
            Minecraft minecraft = Minecraft.getInstance();
            while (UniverseKeys.STAR_MAP.consumeClick()) {
                if (minecraft.gui.screen() == null && minecraft.player != null) {
                    minecraft.gui.setScreen(new StarMapScreen());
                }
            }
        });
    }
}
