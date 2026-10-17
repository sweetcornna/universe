package io.github.sweetcornna.universe.network;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * 网络包注册（技术架构 §3.10）。
 *
 * <p>发往客户端的包只在这里登记格式，处理器在 {@code UniverseClient} 里通过
 * {@code RegisterClientPayloadHandlersEvent} 注册，这样专用服务器不会加载客户端类。
 */
public final class UniverseNetwork {
    /** 协议版本：任何包的格式改动都要加一，版本不同的客户端和服务端会拒绝连接。 */
    private static final String PROTOCOL_VERSION = "1";

    private UniverseNetwork() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar(PROTOCOL_VERSION)
                .playToClient(DimensionListPayload.TYPE, DimensionListPayload.STREAM_CODEC);
    }
}
