package io.github.sweetcornna.universe.client.microbe;

import io.github.sweetcornna.universe.client.ClientGalaxy;
import io.github.sweetcornna.universe.microbe.GeneLibraries;
import io.github.sweetcornna.universe.microbe.MicrobePayloads;
import io.github.sweetcornna.universe.microbe.MicrobeText;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;

/** 微生物和基因在客户端的部分（ADR-0036 §五）：收基因库，物品说明里的星球名读客户端的星团。 */
public final class MicrobeClient {
    private MicrobeClient() {
    }

    public static void registerEvents(IEventBus modBus, IEventBus gameBus) {
        MicrobeText.clientGalaxy = ClientGalaxy::galaxy;
        modBus.addListener(RegisterClientPayloadHandlersEvent.class, event -> event.register(MicrobePayloads.Library.TYPE,
                (payload, context) -> context.enqueueWork(() -> GeneLibraries.receive(payload))));
        gameBus.addListener(ClientPlayerNetworkEvent.LoggingOut.class, event -> GeneLibraries.clearClient());
    }
}
