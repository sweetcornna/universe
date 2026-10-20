package io.github.sweetcornna.universe.story;

import java.util.Collection;
import java.util.List;

import io.github.sweetcornna.universe.advancement.MilestoneTrigger;
import io.github.sweetcornna.universe.core.discovery.DiscoveryKey;
import io.github.sweetcornna.universe.core.discovery.DiscoveryLog;
import io.github.sweetcornna.universe.core.dyson.DysonSphere;
import io.github.sweetcornna.universe.core.research.ResearchState;
import io.github.sweetcornna.universe.core.story.Milestone;
import io.github.sweetcornna.universe.discovery.DiscoveryData;
import io.github.sweetcornna.universe.dyson.DysonData;
import io.github.sweetcornna.universe.factory.research.ResearchData;
import io.github.sweetcornna.universe.network.EndingPayload;
import io.github.sweetcornna.universe.network.EndingRequestPayload;
import io.github.sweetcornna.universe.network.StoryStatePayload;
import io.github.sweetcornna.universe.world.space.SpaceDimensions;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 剧情的服务端入口（ADR-0018）：整个世界第一次进入太空、第一次跃迁、铺满第一片球壳、做出第一个宇宙矩阵时，
 * 探测器 UNV-7 给所有在线玩家发一条日志；研究完"任务完成"时所有在线玩家看结局画面。
 */
public final class Story {
    /** 研究完就是通关的科技。 */
    public static final String FINAL_TECH = "mission_complete";

    private Story() {
    }

    public static void registerEvents(IEventBus gameBus) {
        gameBus.addListener(PlayerEvent.PlayerLoggedInEvent.class, event -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                PacketDistributor.sendToPlayer(player, state(player.level().getServer()));
                // 不在线时到达的剧情节点，进度补上
                StoryData data = StoryData.get(player.level().getServer());
                for (Milestone milestone : Milestone.values()) {
                    if (data.reached(milestone)) {
                        MilestoneTrigger.reach(player, MilestoneTrigger.story(milestone));
                    }
                }
            }
        });
        gameBus.addListener(PlayerEvent.PlayerChangedDimensionEvent.class, event -> {
            if (event.getEntity() instanceof ServerPlayer player && SpaceDimensions.isSpace(event.getTo())) {
                MilestoneTrigger.reach(player, MilestoneTrigger.SPACE);
                reach(player.level().getServer(), Milestone.FIRST_SPACE);
            }
        });
    }

    public static boolean reached(MinecraftServer server, Milestone milestone) {
        return StoryData.get(server).reached(milestone);
    }

    /** 到了一个节点：第一次到时存盘、发日志（或放结局），并同步给所有玩家。 */
    public static void reach(MinecraftServer server, Milestone milestone) {
        StoryData data = StoryData.get(server);
        if (!data.reach(milestone, server.overworld().getGameTime())) {
            return;
        }
        data.setDirty();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            MilestoneTrigger.reach(player, MilestoneTrigger.story(milestone));
        }
        if (milestone.hasLog()) {
            server.getPlayerList().broadcastSystemMessage(logMessage(milestone), false);
        }
        if (milestone == Milestone.MISSION_COMPLETE) {
            showEnding(server, server.getPlayerList().getPlayers());
        }
        sync(server);
    }

    /** 聊天栏里的一条日志："UNV-7 · 轨道"加正文。 */
    public static Component logMessage(Milestone milestone) {
        return Component.translatable("story.universe.log.chat",
                Component.translatable("story.universe.log." + milestone.id() + ".title").withStyle(ChatFormatting.AQUA),
                Component.translatable("story.universe.log." + milestone.id()).withStyle(ChatFormatting.GRAY));
    }

    /** 手册里重看结局：研究完"任务完成"才给。 */
    public static void handleEndingRequest(EndingRequestPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player && reached(player.level().getServer(), Milestone.MISSION_COMPLETE)) {
            showEnding(player.level().getServer(), List.of(player));
        }
    }

    /** 给这些玩家放结局画面。 */
    public static void showEnding(MinecraftServer server, Collection<ServerPlayer> players) {
        EndingPayload payload = stats(server);
        for (ServerPlayer player : List.copyOf(players)) {
            PacketDistributor.sendToPlayer(player, payload);
        }
    }

    /** 这个世界的统计。 */
    public static EndingPayload stats(MinecraftServer server) {
        DiscoveryLog log = DiscoveryData.get(server).log();
        int layers = 0;
        int shells = 0;
        double power = 0;
        for (DysonSphere sphere : DysonData.get(server).spheres().values()) {
            layers += sphere.builtLayers();
            shells += sphere.fullShells();
            power += sphere.generation();
        }
        ResearchState research = ResearchData.get(server).state();
        int levels = research.levels().values().stream().mapToInt(Integer::intValue).sum();
        int species = log.count(DiscoveryKey.Kind.CREATURE) + log.count(DiscoveryKey.Kind.PLANT) + log.count(DiscoveryKey.Kind.TREE);
        return new EndingPayload(server.overworld().getGameTime(), log.count(DiscoveryKey.Kind.SYSTEM), log.count(DiscoveryKey.Kind.BODY),
                species, layers, shells, power, research.done().size(), levels);
    }

    /** 戴森球推进以后看一眼有没有铺满的球壳（到过这个节点就不再看）。 */
    public static void checkShells(MinecraftServer server) {
        if (reached(server, Milestone.FIRST_SHELL)) {
            return;
        }
        for (DysonSphere sphere : DysonData.get(server).spheres().values()) {
            if (sphere.fullShells() > 0) {
                reach(server, Milestone.FIRST_SHELL);
                return;
            }
        }
    }

    /** 调试命令改了进度之后调用。 */
    public static void changed(MinecraftServer server) {
        StoryData.get(server).setDirty();
        sync(server);
    }

    private static StoryStatePayload state(MinecraftServer server) {
        return new StoryStatePayload(StoryData.get(server).milestones());
    }

    private static void sync(MinecraftServer server) {
        PacketDistributor.sendToAllPlayers(state(server));
    }
}
