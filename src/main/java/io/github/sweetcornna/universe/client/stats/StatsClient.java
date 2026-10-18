package io.github.sweetcornna.universe.client.stats;

import java.util.Optional;

import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.client.UniverseKeys;
import io.github.sweetcornna.universe.network.StatsSnapshotPayload;
import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** 生产统计的客户端部分（ADR-0015）：O 键打开面板，收服务端发来的快照，记住上次选的星球和时间范围。 */
public final class StatsClient {
    private static @Nullable StatsSnapshotPayload snapshot;
    /** 上次看的维度（空字符串是全部）和时间范围；第一次打开时是玩家所在的维度、一分钟。 */
    private static @Nullable String lastDimension;
    private static int lastRange;

    private StatsClient() {
    }

    public static void registerEvents(IEventBus modBus, IEventBus gameBus) {
        modBus.addListener(RegisterClientPayloadHandlersEvent.class, event -> event.register(StatsSnapshotPayload.TYPE, StatsClient::handle));
        gameBus.addListener(ClientTickEvent.Post.class, event -> {
            Minecraft minecraft = Minecraft.getInstance();
            while (UniverseKeys.STATS.consumeClick()) {
                if (minecraft.gui.screen() == null && minecraft.player != null && minecraft.level != null) {
                    String dimension = lastDimension != null ? lastDimension : minecraft.level.dimension().identifier().toString();
                    minecraft.gui.setScreen(new StatsScreen(dimension, lastRange));
                }
            }
        });
        gameBus.addListener(ClientPlayerNetworkEvent.LoggingOut.class, event -> {
            snapshot = null;
            lastDimension = null;
            lastRange = 0;
        });
    }

    private static void handle(StatsSnapshotPayload payload, IPayloadContext context) {
        snapshot = payload;
    }

    static Optional<StatsSnapshotPayload> snapshot() {
        return Optional.ofNullable(snapshot);
    }

    static void remember(String dimension, int range) {
        lastDimension = dimension;
        lastRange = range;
    }
}
