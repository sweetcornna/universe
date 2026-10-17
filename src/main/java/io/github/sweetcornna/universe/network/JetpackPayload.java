package io.github.sweetcornna.universe.network;

import io.github.sweetcornna.universe.Universe;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * 客户端 → 服务端：喷气背包开始、停止推进。推力在客户端加，服务端据此扣能量、清掉摔落距离。
 */
public record JetpackPayload(boolean thrusting) implements CustomPacketPayload {
    public static final Type<JetpackPayload> TYPE = new Type<>(Universe.id("jetpack"));
    public static final StreamCodec<ByteBuf, JetpackPayload> STREAM_CODEC =
            ByteBufCodecs.BOOL.map(JetpackPayload::new, JetpackPayload::thrusting);

    @Override
    public Type<JetpackPayload> type() {
        return TYPE;
    }
}
