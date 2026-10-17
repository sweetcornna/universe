package io.github.sweetcornna.universe.client;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.core.survival.LaserHeat;
import io.github.sweetcornna.universe.network.ScanResultPayload;
import io.github.sweetcornna.universe.registry.UniverseAttachments;
import io.github.sweetcornna.universe.survival.MultitoolItem;
import io.github.sweetcornna.universe.survival.SuitHud;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 多功能工具的客户端部分：准星下方的激光热量条，以及扫描到的矿脉标记（投影到屏幕上，30 秒后淡出）。
 */
public final class MultitoolClient {
    /** 标记显示多久（刻）。 */
    private static final int MARKER_TICKS = 600;
    /** 最多给几条最近的矿脉写字，其余只画点。 */
    private static final int LABELS = 12;

    private record Marker(Vec3 pos, Block block, int count, int color) {
    }

    private static List<Marker> markers = List.of();
    private static long scannedAt;

    private MultitoolClient() {
    }

    public static void registerEvents(IEventBus modBus) {
        modBus.addListener(RegisterClientPayloadHandlersEvent.class,
                event -> event.register(ScanResultPayload.TYPE, MultitoolClient::handle));
        modBus.addListener(RegisterGuiLayersEvent.class, event -> {
            event.registerBelow(VanillaGuiLayers.CROSSHAIR, Universe.id("scan_markers"), MultitoolClient::renderMarkers);
            event.registerAbove(VanillaGuiLayers.CROSSHAIR, Universe.id("laser_heat"), MultitoolClient::renderHeat);
        });
    }

    private static void handle(ScanResultPayload payload, IPayloadContext context) {
        List<Marker> list = new ArrayList<>();
        for (ScanResultPayload.Marker marker : payload.markers()) {
            Block block = BuiltInRegistries.BLOCK.getValue(marker.block());
            list.add(new Marker(Vec3.atCenterOf(marker.pos()), block, marker.count(), color(marker.block())));
        }
        markers = List.copyOf(list);
        Minecraft minecraft = Minecraft.getInstance();
        scannedAt = minecraft.level == null ? 0 : minecraft.level.getGameTime();
    }

    // ---- 激光热量

    private static void renderHeat(GuiGraphicsExtractor graphics, DeltaTracker delta) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.gui.hud.isHidden() || !(player.getMainHandItem().getItem() instanceof MultitoolItem)) {
            return;
        }
        SuitHud hud = player.getData(UniverseAttachments.SUIT_HUD);
        if (hud.laser() <= 0) {
            return;
        }
        boolean overheated = hud.has(SuitHud.OVERHEATED);
        int width = 40;
        int x = graphics.guiWidth() / 2 - width / 2;
        int y = graphics.guiHeight() / 2 + 10;
        float share = Mth.clamp(hud.laser() / LaserHeat.MAX, 0, 1);
        int color = overheated ? 0xFFE5533D : share > 0.7F ? 0xFFFF9F43 : 0xFFFFE08A;
        graphics.fill(x - 1, y - 1, x + width + 1, y + 3, 0xA0000000);
        graphics.fill(x, y, x + Math.round(width * share), y + 2, color);
        if (overheated) {
            Font font = minecraft.font;
            Component text = Component.translatable("hud.universe.multitool.overheated");
            graphics.text(font, text, graphics.guiWidth() / 2 - font.width(text) / 2, y + 5, 0xFFFF6A5A, true);
        }
    }

    // ---- 矿脉标记

    private static void renderMarkers(GuiGraphicsExtractor graphics, DeltaTracker delta) {
        Minecraft minecraft = Minecraft.getInstance();
        if (markers.isEmpty() || minecraft.level == null || minecraft.player == null || minecraft.gui.hud.isHidden()) {
            return;
        }
        long age = minecraft.level.getGameTime() - scannedAt;
        if (age > MARKER_TICKS || age < 0) {
            markers = List.of();
            return;
        }
        int alpha = age > MARKER_TICKS - 100 ? (int) (255 * (MARKER_TICKS - age) / 100F) : 255;
        Camera camera = minecraft.gameRenderer.mainCamera();
        Vec3 eye = camera.position();
        double yaw = Math.toRadians(camera.yRot());
        double pitch = Math.toRadians(camera.xRot());
        double sinYaw = Math.sin(yaw);
        double cosYaw = Math.cos(yaw);
        double sinPitch = Math.sin(pitch);
        double cosPitch = Math.cos(pitch);
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        double focal = (height / 2.0) / Math.tan(Math.toRadians(minecraft.options.fov().get()) / 2);
        // 先投影到屏幕上，只留看得见的
        List<OnScreen> visible = new ArrayList<>();
        for (Marker marker : markers) {
            Vec3 d = marker.pos().subtract(eye);
            // 绕 Y 轴转到"前方是 +Z"，再绕 X 轴按俯仰转
            double x1 = d.x * cosYaw + d.z * sinYaw;
            double z1 = -d.x * sinYaw + d.z * cosYaw;
            double y2 = d.y * cosPitch + z1 * sinPitch;
            double z2 = -d.y * sinPitch + z1 * cosPitch;
            if (z2 < 0.5) {
                continue;
            }
            int sx = (int) Math.round(width / 2.0 - x1 / z2 * focal);
            int sy = (int) Math.round(height / 2.0 - y2 / z2 * focal);
            if (sx >= -20 && sx <= width + 20 && sy >= -20 && sy <= height + 20) {
                visible.add(new OnScreen(marker, sx, sy, Math.sqrt(d.lengthSqr())));
            }
        }
        visible.sort(Comparator.comparingDouble(OnScreen::distance));
        Font font = minecraft.font;
        // 看得见的里面由近到远挑几条写字，和已经写了的字叠在一起就跳过
        List<Component> texts = new ArrayList<>();
        List<int[]> boxes = new ArrayList<>();
        for (OnScreen on : visible) {
            Component text = null;
            if (boxes.size() < LABELS) {
                Component candidate = Component.translatable("hud.universe.multitool.marker",
                        Component.translatable(on.marker().block().getDescriptionId()), on.marker().count(),
                        (int) Math.round(on.distance()));
                int[] box = {on.x() + 6, on.y() - 5, on.x() + 6 + font.width(candidate), on.y() + 5};
                if (boxes.stream().noneMatch(other -> box[0] < other[2] && other[0] < box[2] && box[1] < other[3] && other[1] < box[3])) {
                    boxes.add(box);
                    text = candidate;
                }
            }
            texts.add(text);
        }
        // 由远到近画，近的盖在上面
        for (int i = visible.size() - 1; i >= 0; i--) {
            OnScreen on = visible.get(i);
            int sx = on.x();
            int sy = on.y();
            int color = (alpha << 24) | (on.marker().color() & 0xFFFFFF);
            int shadow = ((alpha * 3 / 4) << 24);
            graphics.fill(sx - 3, sy - 1, sx + 4, sy + 2, shadow);
            graphics.fill(sx - 1, sy - 3, sx + 2, sy + 4, shadow);
            graphics.fill(sx - 2, sy, sx + 3, sy + 1, color);
            graphics.fill(sx, sy - 2, sx + 1, sy + 3, color);
            Component text = texts.get(i);
            if (text != null) {
                graphics.text(font, text, sx + 6, sy - 4, (alpha << 24) | lighten(on.marker().color()), true);
            }
        }
    }

    private record OnScreen(Marker marker, int x, int y, double distance) {
    }

    /** 文字用标记颜色往白色靠一半，深色的矿也看得清。 */
    private static int lighten(int rgb) {
        int r = ((rgb >> 16) & 0xFF) + 0xFF >> 1;
        int g = ((rgb >> 8) & 0xFF) + 0xFF >> 1;
        int b = (rgb & 0xFF) + 0xFF >> 1;
        return r << 16 | g << 8 | b;
    }

    /** 标记的颜色：本模组的矿按矿点的颜色，原版的矿按名字猜。 */
    private static int color(Identifier id) {
        String path = id.getPath();
        if (path.contains("titanium")) {
            return 0xCAD6E6;
        } else if (path.contains("uranium")) {
            return 0x7CFF4A;
        } else if (path.contains("optical")) {
            return 0xB070FF;
        } else if (path.contains("organic")) {
            return 0x7CE07C;
        } else if (path.contains("strange")) {
            return 0xD08CFF;
        } else if (path.contains("fire_ice")) {
            return 0x9FE8FF;
        } else if (path.contains("silicon")) {
            return 0x8FA3C0;
        } else if (path.contains("sulfur")) {
            return 0xF2E05A;
        } else if (path.contains("salt") || path.contains("quartz")) {
            return 0xF6EEF0;
        } else if (path.contains("magnetic")) {
            return 0x9C9CB8;
        } else if (path.contains("copper")) {
            return 0xEE9A5E;
        } else if (path.contains("iron")) {
            return 0xE8C7A8;
        } else if (path.contains("gold")) {
            return 0xFCEE4B;
        } else if (path.contains("diamond")) {
            return 0x5DECF5;
        } else if (path.contains("emerald")) {
            return 0x17DD62;
        } else if (path.contains("redstone")) {
            return 0xFF3B3B;
        } else if (path.contains("lapis")) {
            return 0x3B66FF;
        } else if (path.contains("coal")) {
            return 0x707070;
        }
        return 0xFFFFFF;
    }
}
