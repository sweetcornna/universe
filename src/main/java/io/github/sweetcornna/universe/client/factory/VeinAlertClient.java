package io.github.sweetcornna.universe.client.factory;

import java.util.ArrayList;
import java.util.List;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.client.HudMarker;
import io.github.sweetcornna.universe.factory.resource.VeinAlerts;
import io.github.sweetcornna.universe.network.VeinAlertPayload;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;

/**
 * 矿脉枯竭的提醒（ADR-0033 §八）：收到以后在 HUD 的提示栏写一行，再在采完的采矿机上标一个琥珀色的标记，留 30 秒。
 */
public final class VeinAlertClient {
    private static final int TICKS = 600;
    private static final int COLOR = 0xF2B544;
    private static final int MAX = 4;

    private record Alert(ResourceKey<Level> dimension, Vec3 pos, Component label, long at) {
    }

    private static final List<Alert> ALERTS = new ArrayList<>();

    private VeinAlertClient() {
    }

    public static void registerEvents(IEventBus modBus, IEventBus gameBus) {
        modBus.addListener(RegisterClientPayloadHandlersEvent.class,
                event -> event.register(VeinAlertPayload.TYPE, (payload, context) -> context.enqueueWork(() -> handle(payload))));
        modBus.addListener(RegisterGuiLayersEvent.class,
                event -> event.registerBelow(VanillaGuiLayers.CROSSHAIR, Universe.id("vein_alerts"), VeinAlertClient::render));
        gameBus.addListener(ClientPlayerNetworkEvent.LoggingOut.class, event -> ALERTS.clear());
    }

    private static void handle(VeinAlertPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        minecraft.gui.hud.setOverlayMessage(VeinAlerts.message(payload.pos(), payload.machine(), payload.item(), payload.all()), false);
        Component label = payload.all() ? Component.translatable("hud.universe.vein_alert.all")
                : Component.translatable("hud.universe.vein_alert.node", payload.item().getDefaultInstance().getHoverName());
        ALERTS.removeIf(alert -> alert.pos.equals(Vec3.atCenterOf(payload.pos()).add(0, 1, 0)));
        ALERTS.add(new Alert(minecraft.level.dimension(), Vec3.atCenterOf(payload.pos()).add(0, 1, 0), label, minecraft.level.getGameTime()));
        while (ALERTS.size() > MAX) {
            ALERTS.removeFirst();
        }
    }

    private static void render(GuiGraphicsExtractor graphics, DeltaTracker delta) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (ALERTS.isEmpty() || player == null || minecraft.level == null || minecraft.gui.hud.isHidden()) {
            return;
        }
        long now = minecraft.level.getGameTime();
        ALERTS.removeIf(alert -> now - alert.at > TICKS || now < alert.at);
        float pulse = 0.75F + 0.25F * (float) Math.sin((now + delta.getGameTimeDeltaPartialTick(false)) * 0.25);
        for (Alert alert : ALERTS) {
            if (alert.dimension != minecraft.level.dimension()) {
                continue;
            }
            int distance = (int) Math.round(alert.pos.distanceTo(player.position()));
            HudMarker.draw(graphics, minecraft, alert.pos, COLOR, Component.translatable("hud.universe.vein_alert.marker", alert.label, distance),
                    pulse);
        }
    }
}
