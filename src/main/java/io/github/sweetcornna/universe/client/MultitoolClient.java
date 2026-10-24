package io.github.sweetcornna.universe.client;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import io.github.sweetcornna.universe.core.survival.ToolWeapon;
import io.github.sweetcornna.universe.core.tech.Stat;
import io.github.sweetcornna.universe.tech.SuitTech;
import net.minecraft.world.item.ItemStack;
import io.github.sweetcornna.universe.entity.ship.ShipEntity;
import io.github.sweetcornna.universe.survival.Multitool;
import io.github.sweetcornna.universe.network.ToolModePayload;
import io.github.sweetcornna.universe.core.survival.ToolMode;
import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.core.poi.LandmarkType;
import io.github.sweetcornna.universe.core.poi.PoiType;
import io.github.sweetcornna.universe.core.survival.LaserHeat;
import io.github.sweetcornna.universe.network.ScanResultPayload;
import io.github.sweetcornna.universe.registry.UniverseAttachments;
import io.github.sweetcornna.universe.survival.MultitoolItem;
import io.github.sweetcornna.universe.survival.SuitHud;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.client.event.ClientTickEvent;
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
 * 多功能工具的客户端部分：模式切换键、开火模式的准星框、装着的武器名和烈焰标枪的蓄力条、准星下方的热量条，以及扫描到的矿脉和兴趣点标记
 * （投影到屏幕上，30 秒后淡出）。
 */
public final class MultitoolClient {
    /** 标记显示多久（刻）。 */
    private static final int MARKER_TICKS = 600;
    /** 只看一种矿时（ADR-0033 §八）标记留 60 秒，多写几条字。 */
    private static final int FOCUSED_TICKS = 1200;
    /** 最多给几条最近的矿脉写字，其余只画点。 */
    private static final int LABELS = 12;
    private static final int FOCUSED_LABELS = 20;

    private record Marker(Vec3 pos, Block block, int count, int color) {
    }

    /** 扫描到的兴趣点（ADR-0014）。 */
    private record PoiMark(Vec3 pos, PoiType type, boolean visited) {
    }

    /** 扫描、声呐找到的地标（ADR-0026）。 */
    private record LandmarkMark(Vec3 pos, LandmarkType type) {
    }

    private static List<Marker> markers = List.of();
    private static List<PoiMark> pois = List.of();
    private static List<LandmarkMark> landmarks = List.of();
    private static long scannedAt;
    private static boolean focused;

    private MultitoolClient() {
    }

    public static void registerEvents(IEventBus modBus, IEventBus gameBus) {
        modBus.addListener(RegisterClientPayloadHandlersEvent.class,
                event -> event.register(ScanResultPayload.TYPE, MultitoolClient::handle));
        gameBus.addListener(ClientTickEvent.Post.class, event -> {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.player != null && minecraft.player.getVehicle() instanceof ShipEntity) {
                // 坐在飞船上时模式键切换飞船的武器（ShipClient）
                return;
            }
            while (UniverseKeys.TOOL_MODE.consumeClick()) {
                if (minecraft.gui.screen() == null && minecraft.player != null
                        && minecraft.player.getMainHandItem().getItem() instanceof MultitoolItem) {
                    if (minecraft.hasShiftDown()) {
                        // Shift + 模式键：选扫描只看哪种矿（ADR-0033 §八）
                        minecraft.gui.setScreen(new ScanFilterScreen());
                    } else {
                        ClientPacketDistributor.sendToServer(ToolModePayload.INSTANCE);
                    }
                }
            }
        });
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
        focused = payload.focused();
        pois = payload.pois().stream().map(poi -> new PoiMark(Vec3.atBottomCenterOf(poi.pos()).add(0, 1.5, 0), poi.type(), poi.visited()))
                .toList();
        landmarks = payload.landmarks().stream().map(landmark -> new LandmarkMark(Vec3.atBottomCenterOf(landmark.pos()).add(0, 2, 0),
                landmark.type())).toList();
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
        ItemStack tool = player.getMainHandItem();
        ToolMode mode = Multitool.activeMode(tool);
        int cx = graphics.guiWidth() / 2;
        int cy = graphics.guiHeight() / 2;
        if (mode.combat() && minecraft.options.getCameraType().isFirstPerson()) {
            reticle(graphics, cx, cy, mode);
        }
        if (mode.weapon() != null) {
            weapon(graphics, minecraft, player, tool, mode, cx, cy);
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

    /**
     * 装在工具上的武器（ADR-0026 §六）：准星右下写武器名；烈焰标枪在准星上方画一条蓄力条，蓄满了变白。
     * 蓄力按客户端自己按住使用键的时间算，和服务端差一两刻不要紧。
     */
    private static void weapon(GuiGraphicsExtractor graphics, Minecraft minecraft, LocalPlayer player, ItemStack tool, ToolMode mode, int cx,
            int cy) {
        Font font = minecraft.font;
        ToolWeapon weapon = mode.weapon();
        int color = 0xC0000000 | weapon.color();
        Component name = Component.translatable("hud.universe.multitool.mode." + mode.name().toLowerCase(Locale.ROOT));
        graphics.text(font, name, cx + 12, cy + 6, color, true);
        if (mode != ToolMode.BLAZE_JAVELIN || !player.isUsingItem()) {
            return;
        }
        int full = ToolWeapon.chargeTicks(SuitTech.tool(tool).boost(Stat.FIRE_RATE));
        float share = Mth.clamp(player.getTicksUsingItem() / (float) full, 0, 1);
        int width = 40;
        int x = cx - width / 2;
        int y = cy - 14;
        graphics.fill(x - 1, y - 1, x + width + 1, y + 3, 0xA0000000);
        graphics.fill(x, y, x + Math.round(width * share), y + 2, share >= 1 ? 0xFFFFFFFF : 0xFF000000 | weapon.color());
    }

    /** 开火模式的准星框：准星四角各一个折角，颜色和张开的大小按武器来（散射开得最大）。 */
    private static void reticle(GuiGraphicsExtractor graphics, int cx, int cy, ToolMode mode) {
        int color = mode.weapon() == null ? 0xC06FE3FF : 0xC0000000 | mode.weapon().color();
        int r = switch (mode) {
            case SCATTER_BLASTER -> 11;
            case PULSE_SPITTER -> 9;
            case BLAZE_JAVELIN -> 5;
            default -> 7;
        };
        int len = 3;
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sy = -1; sy <= 1; sy += 2) {
                int x = cx + sx * r;
                int y = cy + sy * r;
                // 横的一段和竖的一段，朝准星收拢
                int x0 = Math.min(x, x - sx * len);
                int y0 = Math.min(y, y - sy * len);
                graphics.fill(x0, y, x0 + len + 1, y + 1, color);
                graphics.fill(x, y0, x + 1, y0 + len + 1, color);
            }
        }
    }

    // ---- 矿脉标记

    private static void renderMarkers(GuiGraphicsExtractor graphics, DeltaTracker delta) {
        Minecraft minecraft = Minecraft.getInstance();
        if (markers.isEmpty() && pois.isEmpty() && landmarks.isEmpty() || minecraft.level == null || minecraft.player == null
                || minecraft.gui.hud.isHidden()) {
            return;
        }
        long age = minecraft.level.getGameTime() - scannedAt;
        int lifetime = focused ? FOCUSED_TICKS : MARKER_TICKS;
        if (age > lifetime || age < 0) {
            markers = List.of();
            pois = List.of();
            landmarks = List.of();
            return;
        }
        int alpha = age > lifetime - 100 ? (int) (255 * (lifetime - age) / 100F) : 255;
        ScreenProjection projection = ScreenProjection.of(minecraft, graphics.guiWidth(), graphics.guiHeight());
        Vec3 eye = projection.eye();
        Font font = minecraft.font;
        // 兴趣点先占位置写字，矿脉的字避开它们
        List<int[]> boxes = new ArrayList<>();
        renderPois(graphics, projection, font, alpha, boxes);
        // 先投影到屏幕上，只留看得见的
        List<OnScreen> visible = new ArrayList<>();
        for (Marker marker : markers) {
            int[] point = projection.project(marker.pos());
            if (projection.onScreen(point, 20)) {
                visible.add(new OnScreen(marker, point[0], point[1], marker.pos().distanceTo(eye)));
            }
        }
        visible.sort(Comparator.comparingDouble(OnScreen::distance));
        // 看得见的里面由近到远挑几条写字，和已经写了的字叠在一起就跳过
        List<Component> texts = new ArrayList<>();
        int poiBoxes = boxes.size();
        for (OnScreen on : visible) {
            Component text = null;
            if (boxes.size() - poiBoxes < (focused ? FOCUSED_LABELS : LABELS)) {
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
            if (focused) {
                // 只看一种矿：大一号的十字，外面再套一个菱形框
                graphics.fill(sx - 5, sy - 1, sx + 6, sy + 2, shadow);
                graphics.fill(sx - 1, sy - 5, sx + 2, sy + 6, shadow);
                graphics.fill(sx - 4, sy, sx + 5, sy + 1, color);
                graphics.fill(sx, sy - 4, sx + 1, sy + 5, color);
                diamond(graphics, sx, sy, (alpha * 5 / 8) << 24 | on.marker().color() & 0xFFFFFF, 0);
            } else {
                graphics.fill(sx - 3, sy - 1, sx + 4, sy + 2, shadow);
                graphics.fill(sx - 1, sy - 3, sx + 2, sy + 4, shadow);
                graphics.fill(sx - 2, sy, sx + 3, sy + 1, color);
                graphics.fill(sx, sy - 2, sx + 1, sy + 3, color);
            }
            Component text = texts.get(i);
            if (text != null) {
                graphics.text(font, text, sx + (focused ? 8 : 6), sy - 4, (alpha << 24) | lighten(on.marker().color()), true);
            }
        }
    }

    private record OnScreen(Marker marker, int x, int y, double distance) {
    }

    /** 兴趣点：按种类上色的菱形框，写上名字和距离；去过的画暗一些。 */
    private static void renderPois(GuiGraphicsExtractor graphics, ScreenProjection projection, Font font, int alpha, List<int[]> boxes) {
        Vec3 eye = projection.eye();
        List<PoiMark> sorted = new ArrayList<>(pois);
        // 近的先占位置写字
        sorted.sort(Comparator.comparingDouble(poi -> poi.pos().distanceToSqr(eye)));
        for (PoiMark poi : sorted) {
            int[] point = projection.project(poi.pos());
            if (!projection.onScreen(point, 10)) {
                continue;
            }
            int a = poi.visited() ? alpha / 2 : alpha;
            int color = (a << 24) | poi.type().color();
            diamond(graphics, point[0], point[1], color, (a * 3 / 4) << 24);
            Component text = Component.translatable(poi.visited() ? "hud.universe.multitool.poi_visited" : "hud.universe.multitool.poi",
                    Component.translatable("poi.universe." + poi.type().serializedName()), (int) Math.round(poi.pos().distanceTo(eye)));
            int[] box = {point[0] + 7, point[1] - 5, point[0] + 7 + font.width(text), point[1] + 5};
            if (boxes.stream().noneMatch(other -> box[0] < other[2] && other[0] < box[2] && box[1] < other[3] && other[1] < box[3])) {
                boxes.add(box);
                graphics.text(font, text, point[0] + 7, point[1] - 4, (a << 24) | lighten(poi.type().color()), true);
            }
        }
        List<LandmarkMark> sites = new ArrayList<>(landmarks);
        sites.sort(Comparator.comparingDouble(site -> site.pos().distanceToSqr(eye)));
        for (LandmarkMark site : sites) {
            int[] point = projection.project(site.pos());
            if (!projection.onScreen(point, 10)) {
                continue;
            }
            diamond(graphics, point[0], point[1], (alpha << 24) | site.type().color(), (alpha * 3 / 4) << 24);
            Component text = Component.translatable("hud.universe.multitool.poi", Component.translatable("landmark.universe." + site.type().id()),
                    (int) Math.round(site.pos().distanceTo(eye)));
            int[] box = {point[0] + 7, point[1] - 5, point[0] + 7 + font.width(text), point[1] + 5};
            if (boxes.stream().noneMatch(other -> box[0] < other[2] && other[0] < box[2] && box[1] < other[3] && other[1] < box[3])) {
                boxes.add(box);
                graphics.text(font, text, point[0] + 7, point[1] - 4, (alpha << 24) | lighten(site.type().color()), true);
            }
        }
    }

    /** 空心菱形，带一圈阴影。 */
    public static void diamond(GuiGraphicsExtractor graphics, int x, int y, int color, int shadow) {
        for (int i = -4; i <= 4; i++) {
            int w = 4 - Math.abs(i);
            graphics.fill(x - w - 1, y + i, x - w + 1, y + i + 1, shadow);
            graphics.fill(x + w, y + i, x + w + 2, y + i + 1, shadow);
        }
        for (int i = -3; i <= 3; i++) {
            int w = 3 - Math.abs(i);
            graphics.fill(x - w, y + i, x - w + 1, y + i + 1, color);
            graphics.fill(x + w, y + i, x + w + 1, y + i + 1, color);
        }
        graphics.fill(x, y, x + 1, y + 1, color);
    }

    /** 文字用标记颜色往白色靠一半，深色的矿也看得清。 */
    static int lighten(int rgb) {
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
