package io.github.sweetcornna.universe.client.factory;

import io.github.sweetcornna.universe.factory.belt.BeltNetwork;
import io.github.sweetcornna.universe.network.BeltLinesPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** 客户端的传送带：收服务端的线路快照，校正本地模拟。 */
public final class BeltClient {
    private BeltClient() {
    }

    public static void registerEvents(IEventBus modBus) {
        modBus.addListener(RegisterClientPayloadHandlersEvent.class, event -> event.register(BeltLinesPayload.TYPE, BeltClient::handle));
    }

    private static void handle(BeltLinesPayload payload, IPayloadContext context) {
        Level level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        BeltNetwork network = BeltNetwork.of(level);
        for (BeltLinesPayload.Line line : payload.lines()) {
            network.apply(line);
        }
    }
}
