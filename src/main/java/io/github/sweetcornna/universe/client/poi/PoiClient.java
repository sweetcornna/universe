package io.github.sweetcornna.universe.client.poi;

import org.joml.Matrix3x2fStack;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.client.MultitoolClient;
import io.github.sweetcornna.universe.client.ScreenProjection;
import io.github.sweetcornna.universe.core.poi.PoiType;
import io.github.sweetcornna.universe.network.OpenSignalBoosterPayload;
import io.github.sweetcornna.universe.network.PoiTextPayload;
import io.github.sweetcornna.universe.poi.Waypoint;
import io.github.sweetcornna.universe.registry.UniverseAttachments;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
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
    /** 航点在屏幕外时，箭头离屏幕边多远。 */
    private static final int EDGE = 18;

    private PoiClient() {
    }

    public static void registerEvents(IEventBus modBus) {
        modBus.addListener(RegisterClientPayloadHandlersEvent.class, event -> {
            event.register(PoiTextPayload.TYPE, (payload, context) -> context.enqueueWork(() -> Minecraft.getInstance().gui.setScreen(
                    payload.kind() == PoiTextPayload.Kind.TERMINAL ? PoiTextScreen.terminal(payload.seed(), payload.rewarded())
                            : PoiTextScreen.monolith(payload.lore(), payload.rewarded()))));
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
        if (!waypoint.active() || waypoint.dimension() != minecraft.level.dimension() || waypoint.poiType().isEmpty()) {
            return;
        }
        PoiType type = waypoint.poiType().get();
        Vec3 target = Vec3.atBottomCenterOf(waypoint.pos()).add(0, 2, 0);
        ScreenProjection projection = ScreenProjection.of(minecraft, graphics.guiWidth(), graphics.guiHeight());
        double dx = target.x - player.getX();
        double dz = target.z - player.getZ();
        int distance = (int) Math.round(Math.sqrt(dx * dx + dz * dz));
        Font font = minecraft.font;
        float pulse = 0.75F + 0.25F * (float) Math.sin((minecraft.level.getGameTime() + delta.getGameTimeDeltaPartialTick(false)) * 0.2);
        int alpha = (int) (255 * pulse);
        int color = (alpha << 24) | type.color();
        Component label = Component.translatable("hud.universe.waypoint", Component.translatable("poi.universe." + type.serializedName()), distance);
        int[] point = projection.project(target);
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        if (point != null && point[0] >= EDGE && point[0] <= width - EDGE && point[1] >= EDGE && point[1] <= height - EDGE) {
            MultitoolClient.diamond(graphics, point[0], point[1], color, 0xA0000000);
            MultitoolClient.diamond(graphics, point[0], point[1] - 2, color & 0x60FFFFFF, 0);
            int w = font.width(label);
            graphics.text(font, label, point[0] - w / 2, point[1] + 8, 0xFF000000 | type.color(), true);
            return;
        }
        int[] edge = projection.edge(target, EDGE);
        double angle = Math.atan2(edge[1] - height / 2.0, edge[0] - width / 2.0);
        Matrix3x2fStack pose = graphics.pose();
        pose.pushMatrix();
        pose.translate(edge[0], edge[1]);
        pose.rotate((float) angle);
        // 指向外面的三角形
        for (int x = -5; x <= 5; x++) {
            int h = (5 - x) / 2;
            graphics.fill(x - 1, -h - 1, x + 1, h + 2, 0xA0000000);
        }
        for (int x = -4; x <= 4; x++) {
            int h = (4 - x) / 2;
            graphics.fill(x, -h, x + 1, h + 1, color);
        }
        pose.popMatrix();
        int w = font.width(label);
        int tx = Math.clamp(edge[0] - w / 2, 4, width - w - 4);
        int ty = edge[1] < height / 2 ? edge[1] + 10 : edge[1] - 18;
        graphics.text(font, label, tx, ty, 0xFF000000 | type.color(), true);
    }
}
