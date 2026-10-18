package io.github.sweetcornna.universe.network;

import io.github.sweetcornna.universe.Universe;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** 客户端 → 服务端：按了技术配置键，打开外骨骼和多功能工具的配置界面（ADR-0014）。 */
public record OpenTechPayload() implements CustomPacketPayload {
    public static final OpenTechPayload INSTANCE = new OpenTechPayload();
    public static final Type<OpenTechPayload> TYPE = new Type<>(Universe.id("open_tech"));
    public static final StreamCodec<ByteBuf, OpenTechPayload> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public Type<OpenTechPayload> type() {
        return TYPE;
    }
}
