package io.github.sweetcornna.universe.client.logistics;

import java.util.Optional;

import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.client.ClientGalaxy;
import io.github.sweetcornna.universe.client.HudMarker;
import io.github.sweetcornna.universe.client.UniverseKeys;
import io.github.sweetcornna.universe.client.galaxy.GalaxyText;
import io.github.sweetcornna.universe.core.galaxy.BodyId;
import io.github.sweetcornna.universe.core.galaxy.Galaxy;
import io.github.sweetcornna.universe.core.logistics.StationKind;
import io.github.sweetcornna.universe.network.ControlPanelPayloads;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;

/**
 * 物流控制面板的客户端（ADR-0033 §三）：按键（默认 I）打开面板，收服务端的快照，"定位"时在 HUD 上标出那座站或配送器。
 * 星图画物流路线也用这里的快照。
 */
public final class ControlPanelClient {
    /** 定位的标记：走到这么近就收起来，最多留这么久（10 分钟）。 */
    private static final double ARRIVED = 6;
    private static final long TARGET_TICKS = 20 * 60 * 10;
    private static final int MARK = 0x4FD8FF;

    private static ControlPanelPayloads.@Nullable Snapshot snapshot;
    /** 收到快照时客户端的刻数，用来把快照里服务端的时刻往后推。 */
    private static long receivedAt;
    private static int version;
    private static @Nullable Target target;
    private static long lastRequest;
    private static final long STALE_MILLIS = 2000;

    /** HUD 上标着的地方。 */
    private record Target(String dimension, BlockPos pos, Component label, long until) {
    }

    private ControlPanelClient() {
    }

    public static void registerEvents(IEventBus modBus, IEventBus gameBus) {
        modBus.addListener(RegisterClientPayloadHandlersEvent.class, event -> event.register(ControlPanelPayloads.Snapshot.TYPE,
                (payload, context) -> context.enqueueWork(() -> receive(payload))));
        modBus.addListener(RegisterGuiLayersEvent.class,
                event -> event.registerBelow(VanillaGuiLayers.CROSSHAIR, Universe.id("logistics_locate"), ControlPanelClient::renderTarget));
        gameBus.addListener(ClientTickEvent.Post.class, event -> {
            Minecraft minecraft = Minecraft.getInstance();
            while (UniverseKeys.CONTROL_PANEL.consumeClick()) {
                if (minecraft.gui.screen() == null && minecraft.player != null) {
                    minecraft.gui.setScreen(new ControlPanelScreen(null));
                }
            }
            if (target != null && minecraft.level != null && minecraft.level.getGameTime() > target.until) {
                target = null;
            }
        });
        gameBus.addListener(ClientPlayerNetworkEvent.LoggingOut.class, event -> {
            snapshot = null;
            target = null;
        });
    }

    private static void receive(ControlPanelPayloads.Snapshot payload) {
        Minecraft minecraft = Minecraft.getInstance();
        snapshot = payload;
        receivedAt = minecraft.level == null ? 0 : minecraft.level.getGameTime();
        version++;
    }

    /** 跟服务端要一份新的快照。 */
    public static void request() {
        lastRequest = Util.getMillis();
        ClientPacketDistributor.sendToServer(new ControlPanelPayloads.Request());
    }

    /** 星图开着时用：上一次要快照超过两秒就再要一次。 */
    public static void requestIfStale() {
        if (Util.getMillis() - lastRequest > STALE_MILLIS && Minecraft.getInstance().getConnection() != null) {
            request();
        }
    }

    public static Optional<ControlPanelPayloads.Snapshot> snapshot() {
        return Optional.ofNullable(snapshot);
    }

    /** 每收到一份快照加一。 */
    public static int version() {
        return version;
    }

    /** 估计的服务端现在的游戏刻（快照的时刻加上收到以后过了多久）。 */
    public static double serverTime(float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        ControlPanelPayloads.Snapshot current = snapshot;
        if (current == null || minecraft.level == null) {
            return 0;
        }
        return current.time() + (minecraft.level.getGameTime() - receivedAt) + partialTick;
    }

    // ---- 定位

    public static void locate(String dimension, BlockPos pos, Component label) {
        Minecraft minecraft = Minecraft.getInstance();
        long now = minecraft.level == null ? 0 : minecraft.level.getGameTime();
        target = new Target(dimension, pos.immutable(), label, now + TARGET_TICKS);
    }

    /** 定位着的就是这里。 */
    public static boolean locating(String dimension, BlockPos pos) {
        return target != null && target.dimension.equals(dimension) && target.pos.equals(pos);
    }

    public static void clearTarget() {
        target = null;
    }

    private static void renderTarget(GuiGraphicsExtractor graphics, DeltaTracker delta) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        Target current = target;
        if (player == null || current == null || minecraft.level == null || minecraft.gui.hud.isHidden()
                || !minecraft.level.dimension().identifier().toString().equals(current.dimension)) {
            return;
        }
        Vec3 at = Vec3.atCenterOf(current.pos);
        double distance = player.position().distanceTo(at);
        if (distance < ARRIVED) {
            target = null;
            return;
        }
        float time = player.tickCount + delta.getGameTimeDeltaPartialTick(false);
        Component label = Component.translatable("hud.universe.control_panel.locate", current.label, (int) Math.round(distance));
        HudMarker.draw(graphics, minecraft, at, MARK, label, 0.7F + 0.3F * Mth.sin(time * 0.3F));
    }

    // ---- 显示

    /** 站的名字：起过名的用名字，没起的是"星际物流站 7"这样的种类加编号。 */
    public static Component stationName(ControlPanelPayloads.StationRow row) {
        return row.name().isEmpty() ? defaultName(row) : Component.literal(row.name());
    }

    /** 没起名时的名字：种类加编号。 */
    public static Component defaultName(ControlPanelPayloads.StationRow row) {
        StationKind[] kinds = StationKind.values();
        StationKind kind = kinds[Math.floorMod(row.kind(), kinds.length)];
        return Component.translatable("gui.universe.control_panel.default_name." + kind.name().toLowerCase(java.util.Locale.ROOT), row.number());
    }

    public static Item item(String id) {
        Identifier key = Identifier.tryParse(id);
        return key == null ? Items.AIR : BuiltInRegistries.ITEM.getOptional(key).orElse(Items.AIR);
    }

    public static String itemName(String id) {
        Item item = item(id);
        return item == Items.AIR ? id : Component.translatable(item.getDescriptionId()).getString();
    }

    /** 一个天体路径（{@code s0/p1}、{@code s0/space}）的显示名。 */
    public static String placeName(String body) {
        Optional<Galaxy> galaxy = ClientGalaxy.galaxy();
        if (galaxy.isEmpty()) {
            return body;
        }
        Optional<BodyId> id = BodyId.parse(body);
        if (id.isPresent()) {
            return galaxy.get().body(id.get()).map(GalaxyText::planet).orElse(body);
        }
        int slash = body.indexOf('/');
        if (body.startsWith("s") && slash > 1) {
            try {
                int system = Integer.parseInt(body.substring(1, slash));
                return Component.translatable("gui.universe.control_panel.space", systemName(system)).getString();
            } catch (NumberFormatException e) {
                return body;
            }
        }
        return body;
    }

    public static String systemName(int system) {
        return ClientGalaxy.galaxy().flatMap(galaxy -> galaxy.system(system)).map(GalaxyText::system).orElse("#" + system);
    }

    /** 物品的颜色（星图上的线）：按编号散列出一个色相，亮度和饱和度固定，暗的背景上看得清。 */
    public static int itemColor(String item) {
        float hue = (item.hashCode() & 0xFFFF) / 65536F;
        return 0xFF000000 | Mth.hsvToRgb(hue, 0.55F, 1F);
    }
}
