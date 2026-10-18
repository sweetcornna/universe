package io.github.sweetcornna.universe.network;

import io.github.sweetcornna.universe.Universe;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * 客户端 → 服务端：驾驶员在星图上点了"跃迁"。服务端重新检查飞船、射程、引力井和空间翘曲器（ADR-0012）。
 *
 * @param system 目标恒星系的编号
 */
public record WarpRequestPayload(int system) implements CustomPacketPayload {
    public static final Type<WarpRequestPayload> TYPE = new Type<>(Universe.id("warp_request"));
    public static final StreamCodec<ByteBuf, WarpRequestPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, WarpRequestPayload::system,
            WarpRequestPayload::new);

    @Override
    public Type<WarpRequestPayload> type() {
        return TYPE;
    }
}
