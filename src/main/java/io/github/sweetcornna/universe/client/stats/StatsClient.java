package io.github.sweetcornna.universe.client.stats;

import java.util.Optional;

import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.client.UniverseKeys;
import io.github.sweetcornna.universe.network.StatsDetailPayload;
import io.github.sweetcornna.universe.network.StatsDysonPayload;
import io.github.sweetcornna.universe.network.StatsSnapshotPayload;
import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 生产统计的客户端部分（ADR-0015、ADR-0033 §四）：O 键打开面板，收服务端发来的快照、选中物品的详情和戴森球的统计，
 * 记住上次看的地方、时间范围、哪一页和选中的物品。仪表盘也在这里接上。
 */
public final class StatsClient {
    private static @Nullable StatsSnapshotPayload snapshot;
    private static @Nullable StatsDetailPayload detail;
    private static @Nullable StatsDysonPayload dyson;
    /** 上次看的地方（空字符串是全部）、时间范围、哪一页、选中的物品；第一次打开时是玩家所在的维度、一分钟、生产。 */
    private static @Nullable String lastScope;
    private static int lastRange;
    private static int lastTab;
    private static String lastItem = "";

    private StatsClient() {
    }

    public static void registerEvents(IEventBus modBus, IEventBus gameBus) {
        modBus.addListener(RegisterClientPayloadHandlersEvent.class, event -> {
            event.register(StatsSnapshotPayload.TYPE, StatsClient::handle);
            event.register(StatsDetailPayload.TYPE, StatsClient::handleDetail);
            event.register(StatsDysonPayload.TYPE, StatsClient::handleDyson);
        });
        gameBus.addListener(ClientTickEvent.Post.class, event -> {
            Minecraft minecraft = Minecraft.getInstance();
            while (UniverseKeys.STATS.consumeClick()) {
                if (minecraft.gui.screen() == null && minecraft.player != null && minecraft.level != null) {
                    String scope = lastScope != null ? lastScope : minecraft.level.dimension().identifier().toString();
                    minecraft.gui.setScreen(new StatsScreen(scope, lastRange, lastTab, lastItem));
                }
            }
        });
        gameBus.addListener(ClientPlayerNetworkEvent.LoggingOut.class, event -> {
            snapshot = null;
            detail = null;
            dyson = null;
            lastScope = null;
            lastRange = 0;
            lastTab = 0;
            lastItem = "";
        });
        DashboardHud.registerEvents(modBus, gameBus);
    }

    private static void handle(StatsSnapshotPayload payload, IPayloadContext context) {
        snapshot = payload;
    }

    private static void handleDetail(StatsDetailPayload payload, IPayloadContext context) {
        detail = payload;
    }

    private static void handleDyson(StatsDysonPayload payload, IPayloadContext context) {
        dyson = payload;
    }

    static Optional<StatsSnapshotPayload> snapshot() {
        return Optional.ofNullable(snapshot);
    }

    static Optional<StatsDetailPayload> detail() {
        return Optional.ofNullable(detail);
    }

    static Optional<StatsDysonPayload> dyson() {
        return Optional.ofNullable(dyson);
    }

    static void remember(String scope, int range, int tab, String item) {
        lastScope = scope;
        lastRange = range;
        lastTab = tab;
        lastItem = item;
    }
}
