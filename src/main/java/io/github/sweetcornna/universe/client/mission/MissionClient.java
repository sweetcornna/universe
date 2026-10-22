package io.github.sweetcornna.universe.client.mission;

import java.util.Optional;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.client.HudMarker;
import io.github.sweetcornna.universe.core.mission.LandmarkTarget;
import io.github.sweetcornna.universe.core.mission.Mission;
import io.github.sweetcornna.universe.core.mission.MissionKind;
import io.github.sweetcornna.universe.mission.MissionState;
import io.github.sweetcornna.universe.network.GuildEnvoyPayloads;
import io.github.sweetcornna.universe.network.OpenMissionBoardPayload;
import io.github.sweetcornna.universe.registry.UniverseAttachments;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;

/**
 * 空间站任务的客户端部分（ADR-0017）：任务板界面，右上角一块写着手上的任务和进度。公会使者的界面（ADR-0027 §六）也在这里，
 * 地标任务在那颗星球上时 HUD 标出地标。
 */
public final class MissionClient {
    /** 地标任务的路标颜色。 */
    private static final int LANDMARK_COLOR = 0x9AD8FF;

    private MissionClient() {
    }

    public static void registerEvents(IEventBus modBus) {
        modBus.addListener(RegisterClientPayloadHandlersEvent.class, event -> event.register(OpenMissionBoardPayload.TYPE,
                (payload, context) -> context.enqueueWork(() -> Minecraft.getInstance().gui.setScreen(new MissionBoardScreen(payload)))));
        modBus.addListener(RegisterClientPayloadHandlersEvent.class, event -> event.register(GuildEnvoyPayloads.Open.TYPE,
                (payload, context) -> context.enqueueWork(() -> {
                    Minecraft minecraft = Minecraft.getInstance();
                    if (minecraft.gui.screen() instanceof GuildEnvoyScreen screen && screen.entity() == payload.entity()) {
                        screen.update(payload);
                    } else if (payload.open()) {
                        minecraft.gui.setScreen(new GuildEnvoyScreen(payload));
                    }
                })));
        modBus.addListener(RegisterGuiLayersEvent.class,
                event -> event.registerBelow(VanillaGuiLayers.CROSSHAIR, Universe.id("mission"), MissionClient::renderHud));
        modBus.addListener(RegisterGuiLayersEvent.class,
                event -> event.registerBelow(VanillaGuiLayers.CROSSHAIR, Universe.id("mission_landmark"), MissionClient::renderLandmark));
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

    /** 手上是还没到的地标任务、人在那颗星球上：画出地标的方向和距离。 */
    private static void renderLandmark(GuiGraphicsExtractor graphics, DeltaTracker delta) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null || minecraft.gui.hud.isHidden()) {
            return;
        }
        MissionState state = player.getData(UniverseAttachments.MISSIONS);
        if (state.active().isEmpty() || state.active().get().complete() || state.active().get().mission().kind() != MissionKind.LANDMARK) {
            return;
        }
        Optional<LandmarkTarget> found = LandmarkTarget.parse(state.active().get().mission().item());
        if (found.isEmpty() || !minecraft.level.dimension().identifier().equals(Universe.id(found.get().body()))) {
            return;
        }
        LandmarkTarget target = found.get();
        // 区块加载了用真实地面，没加载就画在和人一样高的地方
        double y = minecraft.level.hasChunk(target.x() >> 4, target.z() >> 4)
                ? minecraft.level.getHeight(Heightmap.Types.MOTION_BLOCKING, target.x(), target.z()) + 2 : player.getY() + 2;
        Vec3 position = new Vec3(target.x() + 0.5, y, target.z() + 0.5);
        double dx = position.x - player.getX();
        double dz = position.z - player.getZ();
        int distance = (int) Math.round(Math.sqrt(dx * dx + dz * dz));
        float pulse = 0.75F + 0.25F * (float) Math.sin((minecraft.level.getGameTime() + delta.getGameTimeDeltaPartialTick(false)) * 0.2);
        Component label = Component.translatable("hud.universe.waypoint", Component.translatable("landmark.universe." + target.type()), distance);
        HudMarker.draw(graphics, minecraft, position, LANDMARK_COLOR, label, pulse);
    }
}
