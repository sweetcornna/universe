package io.github.sweetcornna.universe.client.poi;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.client.HudMarker;
import io.github.sweetcornna.universe.network.OpenSignalBoosterPayload;
import io.github.sweetcornna.universe.network.PoiTextPayload;
import io.github.sweetcornna.universe.poi.Waypoint;
import io.github.sweetcornna.universe.registry.UniverseAttachments;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;

/**
 * 兴趣点的客户端部分（ADR-0014）：数据终端和石碑的文字界面、信号增强器界面，以及 HUD 上的航点。
 */
public final class PoiClient {
    private PoiClient() {
    }

    public static void registerEvents(IEventBus modBus) {
        modBus.addListener(RegisterClientPayloadHandlersEvent.class, event -> {
            event.register(PoiTextPayload.TYPE, (payload, context) -> context.enqueueWork(() -> Minecraft.getInstance().gui.setScreen(
                    switch (payload.kind()) {
                        case TERMINAL -> PoiTextScreen.terminal(payload.seed(), payload.rewarded());
                        case MONOLITH -> PoiTextScreen.monolith(payload.lore(), payload.rewarded());
                        case RESPONSE -> PoiTextScreen.response(payload.lore(), payload.rewarded());
                    })));
            event.register(OpenSignalBoosterPayload.TYPE, (payload, context) -> context.enqueueWork(
                    () -> Minecraft.getInstance().gui.setScreen(new SignalBoosterScreen(payload.pos()))));
        });
        modBus.addListener(RegisterGuiLayersEvent.class,
                event -> event.registerBelow(VanillaGuiLayers.CROSSHAIR, Universe.id("waypoint"), PoiClient::renderWaypoint));
    }

    /** 航点：在视野里画一个跳动的菱形和名字、距离；在视野外时屏幕边上画一个指过去的箭头。 */
    private static void renderWaypoint(GuiGraphicsExtractor graphics, DeltaTracker delta) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null || minecraft.gui.hud.isHidden()) {
            return;
        }
        Waypoint waypoint = player.getData(UniverseAttachments.WAYPOINT);
        if (!waypoint.active() || waypoint.dimension() != minecraft.level.dimension()) {
            return;
        }
        Vec3 target = Vec3.atBottomCenterOf(waypoint.pos()).add(0, 2, 0);
        double dx = target.x - player.getX();
        double dz = target.z - player.getZ();
        int distance = (int) Math.round(Math.sqrt(dx * dx + dz * dz));
        float pulse = 0.75F + 0.25F * (float) Math.sin((minecraft.level.getGameTime() + delta.getGameTimeDeltaPartialTick(false)) * 0.2);
        Component label = Component.translatable("hud.universe.waypoint", waypoint.name(), distance);
        HudMarker.draw(graphics, minecraft, target, waypoint.color(), label, pulse);
    }
}
