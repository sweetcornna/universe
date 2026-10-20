package io.github.sweetcornna.universe.mixin.client;

import java.net.InetAddress;

import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import io.github.sweetcornna.universe.client.dev.DevScript;
import net.minecraft.client.server.IntegratedServer;

/**
 * 开发脚本运行时，对局域网开放只监听本机回环地址。
 *
 * <p>无头的多人测试在同一台电脑上开两个客户端，第二个连 {@code localhost}。原版监听所有网卡，第一次这样做时 Windows
 * 防火墙会弹窗问要不要放行 Java，弹在正在用电脑的人的屏幕上。只监听回环地址就不会弹。只在带了
 * {@code -Duniverse.devScript} 启动时生效。
 */
@Mixin(IntegratedServer.class)
abstract class IntegratedServerMixin {
    @ModifyArg(method = "publishServer(Lnet/minecraft/server/MinecraftServer$MultiplayerScope;I)Z",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/network/ServerConnectionListener;startTcpServerListener(Ljava/net/InetAddress;I)V"),
            index = 0)
    private @Nullable InetAddress universe$loopbackOnly(@Nullable InetAddress address) {
        return DevScript.enabled() ? InetAddress.getLoopbackAddress() : address;
    }
}
