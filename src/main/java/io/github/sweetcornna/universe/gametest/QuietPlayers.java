package io.github.sweetcornna.universe.gametest;

import java.util.UUID;

import com.mojang.authlib.GameProfile;
import io.netty.channel.ChannelFutureListener;
import org.jspecify.annotations.Nullable;

import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;

/**
 * 收不到包的假玩家。原版的两种都不合用：{@code makeMockServerPlayer} 没有连接，服务端给它发一行提示就空指针；
 * {@code makeMockServerPlayerInLevel} 进了玩家列表，各处给玩家同步状态时往它的假连接上发本模组的包，NeoForge 认定这条连接
 * 没登记这些频道，抛异常把测试服务端拖垮。这里的连接把发给它的包全部丢掉。
 */
final class QuietPlayers {
    private QuietPlayers() {
    }

    /** 给没有连接的假玩家接上一条什么也不发的连接。 */
    static ServerPlayer silence(ServerPlayer player) {
        new ServerGamePacketListenerImpl(player.level().getServer(), new Connection(PacketFlow.SERVERBOUND), player,
                CommonListenerCookie.createInitial(player.getGameProfile(), false)) {
            @Override
            public void send(Packet<?> packet) {
            }

            @Override
            public void send(Packet<?> packet, @Nullable ChannelFutureListener listener) {
            }
        };
        return player;
    }

    /** 放进 {@code level} 的假玩家：只进这一个维度，不进玩家列表。测完用 {@code removePlayerImmediately} 收掉。 */
    static ServerPlayer inLevel(ServerLevel level, double x, double y, double z) {
        GameProfile profile = new GameProfile(UUID.randomUUID(), "test-quiet");
        ServerPlayer player = silence(new ServerPlayer(level.getServer(), level, profile, ClientInformation.createDefault()));
        player.snapTo(x, y, z);
        level.addNewPlayer(player);
        return player;
    }
}
