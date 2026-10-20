package io.github.sweetcornna.universe.network;

import io.github.sweetcornna.universe.Universe;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** 客户端 → 服务端：手册里点了"重看结局"（ADR-0018）。服务端确认研究完了"任务完成"，回一份结局和统计。 */
public record EndingRequestPayload() implements CustomPacketPayload {
    public static final Type<EndingRequestPayload> TYPE = new Type<>(Universe.id("ending_request"));
    public static final StreamCodec<ByteBuf, EndingRequestPayload> STREAM_CODEC = StreamCodec.unit(new EndingRequestPayload());

    @Override
    public Type<EndingRequestPayload> type() {
        return TYPE;
    }
}
