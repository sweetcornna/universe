package io.github.sweetcornna.universe.client.teleport;

import io.github.sweetcornna.universe.network.OpenBaseBeaconPayload;
import io.github.sweetcornna.universe.network.OpenTeleporterPayload;
import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;

/** 基地和传送的客户端部分（ADR-0017）：传送器和基地信标的界面。 */
public final class TeleportClient {
    private TeleportClient() {
    }

    public static void registerEvents(IEventBus modBus) {
        modBus.addListener(RegisterClientPayloadHandlersEvent.class, event -> {
            event.register(OpenTeleporterPayload.TYPE, (payload, context) -> context.enqueueWork(
                    () -> Minecraft.getInstance().gui.setScreen(new TeleporterScreen(payload))));
            event.register(OpenBaseBeaconPayload.TYPE, (payload, context) -> context.enqueueWork(
                    () -> Minecraft.getInstance().gui.setScreen(new BaseBeaconScreen(payload))));
        });
    }
}
