package io.github.sweetcornna.universe.client.mission;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.core.mission.Mission;
import io.github.sweetcornna.universe.mission.MissionState;
import io.github.sweetcornna.universe.network.OpenMissionBoardPayload;
import io.github.sweetcornna.universe.registry.UniverseAttachments;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;

/** 空间站任务的客户端部分（ADR-0017）：任务板界面，右上角一块写着手上的任务和进度。 */
public final class MissionClient {
    private MissionClient() {
    }

    public static void registerEvents(IEventBus modBus) {
        modBus.addListener(RegisterClientPayloadHandlersEvent.class, event -> event.register(OpenMissionBoardPayload.TYPE,
                (payload, context) -> context.enqueueWork(() -> Minecraft.getInstance().gui.setScreen(new MissionBoardScreen(payload)))));
        modBus.addListener(RegisterGuiLayersEvent.class,
                event -> event.registerBelow(VanillaGuiLayers.CROSSHAIR, Universe.id("mission"), MissionClient::renderHud));
    }

    private static void renderHud(GuiGraphicsExtractor graphics, DeltaTracker delta) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.gui.hud.isHidden() || minecraft.gui.screen() != null) {
            return;
        }
        MissionState state = player.getData(UniverseAttachments.MISSIONS);
        if (state.active().isEmpty()) {
            return;
        }
        MissionState.Active active = state.active().get();
        Mission mission = active.mission();
        Component goal = MissionText.goal(mission);
        Component status = active.complete()
                ? Component.translatable("hud.universe.mission.ready", MissionText.station(mission.system()))
                : Component.translatable("gui.universe.mission.progress", active.progress(), mission.count());
        int width = Math.max(minecraft.font.width(goal), minecraft.font.width(status)) + 12;
        int right = graphics.guiWidth() - 4;
        // 右上角：站内的信用点下面；有状态效果图标时再往下让开
        int top = player.getActiveEffects().isEmpty() ? 21 : 54;
        graphics.fill(right - width, top, right, top + 26, 0x90000000);
        graphics.fill(right - width, top, right - width + 2, top + 26, MissionText.color(mission.kind()));
        graphics.text(minecraft.font, goal, right - width + 7, top + 4, 0xFFE6EEF2, true);
        graphics.text(minecraft.font, status, right - width + 7, top + 15, active.complete() ? 0xFF8AE08A : 0xFFB8C4C8, true);
    }
}
