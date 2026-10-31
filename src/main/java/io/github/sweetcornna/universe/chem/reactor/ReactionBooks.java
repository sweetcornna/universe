package io.github.sweetcornna.universe.chem.reactor;

import io.github.sweetcornna.universe.core.chem.reaction.Reaction;
import io.github.sweetcornna.universe.core.chem.reaction.ReactionBook;
import io.github.sweetcornna.universe.core.chem.reaction.YieldGrid;
import io.github.sweetcornna.universe.factory.research.Research;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

/**
 * 全服发现了的反应（ADR-0037）：自由投料做成一批、读了实验记录就记下，聊天栏告诉所有人。服务端存在 {@link ReactionBookData}，
 * 登录时、有新发现时整份发给客户端；客户端的一份放在这里，反应釜的列表和化学手册读它。
 */
public final class ReactionBooks {
    private static volatile ReactionBook client = new ReactionBook();

    private ReactionBooks() {
    }

    public static void registerEvents(IEventBus gameBus) {
        gameBus.addListener(PlayerEvent.PlayerLoggedInEvent.class, event -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                PacketDistributor.sendToPlayer(player, ReactionBookPayload.of(book(player.level().getServer())));
            }
        });
    }

    /** 服务端的一份。头一次读老存档时，产量图里做成过一批的反应算发现了（ADR-0037 §六）。 */
    public static ReactionBook book(MinecraftServer server) {
        ReactionBookData data = server.getDataStorage().computeIfAbsent(ReactionBookData.TYPE);
        if (!data.migrated()) {
            ReactorKnowledge knowledge = ReactorKnowledge.get(server);
            for (Reaction reaction : Reaction.values()) {
                for (int cell = 0; cell < YieldGrid.CELLS; cell++) {
                    if (knowledge.measured(reaction, cell)) {
                        data.book().discover(reaction);
                        break;
                    }
                }
            }
            data.markMigrated();
        }
        return data.book();
    }

    /**
     * 记下发现了一条反应。以前没有就存盘、同步，聊天栏告诉所有在线的玩家。
     *
     * @param announce 要不要在聊天栏说（老存档补记的不说）
     * @return 是不是新发现
     */
    public static boolean discover(MinecraftServer server, Reaction reaction, boolean announce) {
        if (!book(server).discover(reaction)) {
            return false;
        }
        server.getDataStorage().computeIfAbsent(ReactionBookData.TYPE).setDirty();
        PacketDistributor.sendToAllPlayers(ReactionBookPayload.of(book(server)));
        if (announce) {
            Component message = Component.translatable("message.universe.reaction_discovered",
                    Component.translatable("reaction.universe." + reaction.id())).withStyle(ChatFormatting.AQUA);
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                player.sendSystemMessage(message);
            }
        }
        return true;
    }

    /** 服务端线程上读存档，别的线程（客户端）读同步来的那份。 */
    public static ReactionBook current() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null && server.isSameThread()) {
            return book(server);
        }
        return client;
    }

    /** 手册里列不列出这条：发现了，或者研究了对应科技。两边都能调。 */
    public static boolean listed(Level level, Reaction reaction) {
        ReactionBook book = level instanceof ServerLevel serverLevel ? book(serverLevel.getServer()) : client;
        return book.listed(reaction, id -> Research.isUnlocked(level, id));
    }

    // ---- 客户端

    public static void receive(ReactionBookPayload payload) {
        client = payload.book();
    }

    public static ReactionBook client() {
        return client;
    }

    public static void clearClient() {
        client = new ReactionBook();
    }
}
