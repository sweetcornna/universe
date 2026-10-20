package io.github.sweetcornna.universe.client.story;

import java.util.Map;

import io.github.sweetcornna.universe.core.story.Milestone;
import io.github.sweetcornna.universe.network.EndingPayload;
import io.github.sweetcornna.universe.network.StoryStatePayload;
import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;

/**
 * 客户端的剧情进度（ADR-0018）：服务端同步过来的节点，给手册的"日志"一章用；收到结局时打开结局画面。
 */
public final class ClientStory {
    private static Map<String, Long> milestones = Map.of();

    private ClientStory() {
    }

    public static void registerEvents(IEventBus modBus, IEventBus gameBus) {
        modBus.addListener(RegisterClientPayloadHandlersEvent.class, event -> {
            event.register(StoryStatePayload.TYPE, (payload, context) -> context.enqueueWork(() -> milestones = Map.copyOf(payload.milestones())));
            event.register(EndingPayload.TYPE, (payload, context) -> context.enqueueWork(
                    () -> Minecraft.getInstance().gui.setScreen(new EndingScreen(payload))));
        });
        gameBus.addListener(ClientPlayerNetworkEvent.LoggingOut.class, event -> milestones = Map.of());
    }

    public static boolean reached(Milestone milestone) {
        return milestones.containsKey(milestone.id());
    }

    /** 到过的节点 → 到的时候（主世界的游戏刻）。 */
    public static Map<String, Long> milestones() {
        return milestones;
    }
}
