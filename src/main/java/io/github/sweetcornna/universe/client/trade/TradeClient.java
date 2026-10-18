package io.github.sweetcornna.universe.client.trade;

import io.github.sweetcornna.universe.network.TradeStatePayload;
import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** 交易终端的客户端部分（ADR-0017）：收到界面内容时打开或刷新交易界面，记住上次看的是卖出还是购买。 */
public final class TradeClient {
    private static boolean buyTab;

    private TradeClient() {
    }

    public static void registerEvents(IEventBus modBus) {
        modBus.addListener(RegisterClientPayloadHandlersEvent.class, event -> event.register(TradeStatePayload.TYPE, TradeClient::handle));
    }

    private static void handle(TradeStatePayload payload, IPayloadContext context) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.gui.screen() instanceof TradeScreen screen) {
            screen.update(payload);
        } else if (payload.open()) {
            minecraft.gui.setScreen(new TradeScreen(payload, buyTab));
        }
    }

    static void rememberTab(boolean buy) {
        buyTab = buy;
    }
}
