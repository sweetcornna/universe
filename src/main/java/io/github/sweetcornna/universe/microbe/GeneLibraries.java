package io.github.sweetcornna.universe.microbe;

import java.util.Collection;

import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.core.microbe.Gene;
import io.github.sweetcornna.universe.core.microbe.GeneLibrary;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

/**
 * 全服共享的基因库（ADR-0036 §五）：测序仪往里记，基因编辑台从里面选。服务端存在 {@link GeneLibraryData}，
 * 登录时、测出新东西时整份发给客户端；客户端的一份放在这里，菌种管的名字、编辑台的界面读它。
 */
public final class GeneLibraries {
    private static volatile GeneLibrary client = new GeneLibrary();

    private GeneLibraries() {
    }

    public static void registerEvents(IEventBus gameBus) {
        gameBus.addListener(PlayerEvent.PlayerLoggedInEvent.class, event -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                PacketDistributor.sendToPlayer(player, MicrobePayloads.Library.of(library(player.level().getServer())));
            }
        });
    }

    public static GeneLibrary library(MinecraftServer server) {
        return GeneLibraryData.get(server).library();
    }

    /**
     * 记下测出的东西。有新的就存盘、发给所有在线的玩家。
     *
     * @param strainKey 测的是菌种时它的键；组织为 {@code null}
     */
    public static GeneLibrary.SequenceResult sequence(MinecraftServer server, @Nullable String strainKey, Collection<Gene> genes) {
        GeneLibraryData data = GeneLibraryData.get(server);
        GeneLibrary.SequenceResult result = data.library().sequence(strainKey, genes);
        if (result.anythingNew()) {
            data.setDirty();
            PacketDistributor.sendToAllPlayers(MicrobePayloads.Library.of(data.library()));
        }
        return result;
    }

    /**
     * 这一边看得到的基因库：在服务端线程上读存档，在别的线程（客户端）读同步来的那份。物品的名字两边都会算，
     * 单人游戏里服务端和客户端在同一个进程里，所以按线程分。
     */
    public static GeneLibrary current() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null && server.isSameThread()) {
            return library(server);
        }
        return client;
    }

    /** 这种菌测过序没有：没测过的菌种只写"未鉴定的菌株"。 */
    public static boolean sequenced(String strainKey) {
        return current().sequenced(strainKey);
    }

    // ---- 客户端

    public static void receive(MicrobePayloads.Library payload) {
        client = payload.library();
    }

    public static GeneLibrary client() {
        return client;
    }

    public static void clearClient() {
        client = new GeneLibrary();
    }
}
