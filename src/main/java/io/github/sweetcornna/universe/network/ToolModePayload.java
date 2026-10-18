package io.github.sweetcornna.universe.network;

import io.github.sweetcornna.universe.Universe;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** 客户端 → 服务端：按了多功能工具的模式切换键（ADR-0014）。 */
public record ToolModePayload() implements CustomPacketPayload {
    public static final ToolModePayload INSTANCE = new ToolModePayload();
    public static final Type<ToolModePayload> TYPE = new Type<>(Universe.id("tool_mode"));
    public static final StreamCodec<ByteBuf, ToolModePayload> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public Type<ToolModePayload> type() {
        return TYPE;
    }
}
