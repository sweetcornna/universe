package io.github.sweetcornna.universe.client.portal;

import io.github.sweetcornna.universe.network.PortalPayloads;
import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;

/** 先驱者传送门的客户端部分（ADR-0024）：拨号盘。 */
public final class PortalClient {
    private PortalClient() {
    }

    public static void registerEvents(IEventBus modBus) {
        modBus.addListener(RegisterClientPayloadHandlersEvent.class, event -> event.register(PortalPayloads.Open.TYPE,
                (payload, context) -> context.enqueueWork(() -> Minecraft.getInstance().gui.setScreen(new PortalDialScreen(payload)))));
    }
}
