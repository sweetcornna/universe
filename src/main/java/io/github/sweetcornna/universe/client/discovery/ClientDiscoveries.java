package io.github.sweetcornna.universe.client.discovery;

import java.util.Optional;

import io.github.sweetcornna.universe.client.galaxy.GalaxyText;
import io.github.sweetcornna.universe.core.discovery.DiscoveryKey;
import io.github.sweetcornna.universe.core.discovery.DiscoveryLog;
import io.github.sweetcornna.universe.core.procgen.NameGenerator.Name;
import io.github.sweetcornna.universe.network.DiscoveryPayload;
import io.github.sweetcornna.universe.client.UniverseKeys;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Util;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 客户端的发现日志副本（ADR-0012）：服务端进服时发整份、之后发变化。名字、星图、发现日志界面都从这里读。
 */
public final class ClientDiscoveries {
    private static final DiscoveryLog LOG = new DiscoveryLog();
    /** 每次变化加一，界面据此刷新缓存。 */
    private static int version;
    private static int lastReward;
    private static long lastRewardAt;
    private static DiscoveryKey lastKey;

    private ClientDiscoveries() {
    }

    public static void registerEvents(IEventBus modBus, IEventBus gameBus) {
        modBus.addListener(RegisterClientPayloadHandlersEvent.class, event -> event.register(DiscoveryPayload.TYPE, ClientDiscoveries::handle));
        gameBus.addListener(ClientPlayerNetworkEvent.LoggingOut.class, event -> {
            LOG.clear();
            version++;
        });
        // 按发现日志键（默认 K）打开发现日志
        gameBus.addListener(ClientTickEvent.Post.class, event -> {
            Minecraft minecraft = Minecraft.getInstance();
            while (UniverseKeys.DISCOVERY_LOG.consumeClick()) {
                if (minecraft.gui.screen() == null && minecraft.player != null) {
                    minecraft.gui.setScreen(new DiscoveryLogScreen());
                }
            }
        });
    }

    private static void handle(DiscoveryPayload payload, IPayloadContext context) {
        if (payload.full()) {
            LOG.clear();
        }
        for (DiscoveryPayload.Entry entry : payload.entries()) {
            entry.entry().ifPresent(LOG::put);
        }
        version++;
        if (payload.reward() > 0 && !payload.entries().isEmpty()) {
            lastReward = payload.reward();
            lastRewardAt = Util.getMillis();
            lastKey = payload.entries().getFirst().entry().map(DiscoveryLog.Entry::key).orElse(null);
        }
    }

    public static DiscoveryLog log() {
        return LOG;
    }

    public static int version() {
        return version;
    }

    public static boolean known(DiscoveryKey key) {
        return LOG.contains(key);
    }

    public static Optional<DiscoveryLog.Entry> entry(DiscoveryKey key) {
        return LOG.get(key);
    }

    /** 显示用的名字：改过名用新名字，否则按界面语言取程序生成的名字。 */
    public static String name(DiscoveryKey key, Name generated) {
        return LOG.get(key).map(DiscoveryLog.Entry::customName).orElseGet(() -> GalaxyText.name(generated));
    }

    /** 最近一次新发现拿到的勘探数据，HUD 显示几秒。 */
    public static int lastReward() {
        return lastReward;
    }

    public static long lastRewardAt() {
        return lastRewardAt;
    }

    public static DiscoveryKey lastKey() {
        return lastKey;
    }
}
