package io.github.sweetcornna.universe.client.ship;

import io.github.sweetcornna.universe.client.UniverseKeys;
import io.github.sweetcornna.universe.entity.ship.ShipEntity;
import io.github.sweetcornna.universe.network.DealerPayloads;
import io.github.sweetcornna.universe.network.ShipPayloads;
import io.github.sweetcornna.universe.registry.UniverseMenus;
import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;

/** 飞船面板、召唤键、自动驾驶和飞船交易终端的客户端部分（ADR-0024）。召唤键做什么由服务端按玩家在哪、在不在开船决定。 */
public final class ShipNavClient {
    private ShipNavClient() {
    }

    public static void registerEvents(IEventBus modBus, IEventBus gameBus) {
        modBus.addListener(RegisterMenuScreensEvent.class, event -> event.register(UniverseMenus.SHIP_PANEL.get(), ShipPanelScreen::new));
        modBus.addListener(RegisterClientPayloadHandlersEvent.class, event -> {
            event.register(ShipPayloads.Helm.TYPE, (payload, context) -> context.enqueueWork(() -> {
                Minecraft minecraft = Minecraft.getInstance();
                if (minecraft.level != null && minecraft.level.getEntity(payload.entity()) instanceof ShipEntity ship) {
                    ship.clientHelm(payload.data());
                }
            }));
            event.register(ShipPayloads.Destinations.TYPE, (payload, context) -> context.enqueueWork(
                    () -> Minecraft.getInstance().gui.setScreen(new AutopilotScreen(payload))));
            event.register(DealerPayloads.State.TYPE, (payload, context) -> context.enqueueWork(() -> {
                Minecraft minecraft = Minecraft.getInstance();
                if (minecraft.gui.screen() instanceof ShipDealerScreen screen) {
                    screen.update(payload);
                } else if (payload.open()) {
                    minecraft.gui.setScreen(new ShipDealerScreen(payload));
                }
            }));
        });
        gameBus.addListener(ClientTickEvent.Post.class, event -> {
            Minecraft minecraft = Minecraft.getInstance();
            while (UniverseKeys.SUMMON.consumeClick()) {
                if (minecraft.gui.screen() == null && minecraft.player != null) {
                    summon();
                }
            }
        });
    }

    /** 按了召唤键（开发脚本也调）。 */
    public static void summon() {
        ClientPacketDistributor.sendToServer(ShipPayloads.Summon.INSTANCE);
    }
}
