package io.github.sweetcornna.universe.network;

import java.util.List;

import io.github.sweetcornna.universe.Universe;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * 服务端 → 客户端：多功能工具扫描到的矿脉，HUD 上标出来一段时间。
 */
public record ScanResultPayload(List<Marker> markers) implements CustomPacketPayload {
    public static final Type<ScanResultPayload> TYPE = new Type<>(Universe.id("scan_result"));
    public static final int MAX_MARKERS = 128;

    /** 一条矿脉：中心位置、矿石方块、有几块。 */
    public record Marker(BlockPos pos, Identifier block, int count) {
        public static final StreamCodec<ByteBuf, Marker> STREAM_CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, Marker::pos,
                Identifier.STREAM_CODEC, Marker::block,
                ByteBufCodecs.VAR_INT, Marker::count,
                Marker::new);
    }

    public static final StreamCodec<ByteBuf, ScanResultPayload> STREAM_CODEC =
            Marker.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_MARKERS)).map(ScanResultPayload::new, ScanResultPayload::markers);

    @Override
    public Type<ScanResultPayload> type() {
        return TYPE;
    }
}
