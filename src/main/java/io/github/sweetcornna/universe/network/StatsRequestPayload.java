package io.github.sweetcornna.universe.network;

import io.github.sweetcornna.universe.Universe;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * 客户端 → 服务端：统计面板要一份快照（ADR-0015）。
 *
 * @param dimension 维度 id；空字符串是全部星球加起来
 * @param range     时间范围（{@code ProductionStats.Range} 的序号）
 */
public record StatsRequestPayload(String dimension, int range) implements CustomPacketPayload {
    public static final Type<StatsRequestPayload> TYPE = new Type<>(Universe.id("stats_request"));
    public static final StreamCodec<ByteBuf, StatsRequestPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(256), StatsRequestPayload::dimension,
            ByteBufCodecs.VAR_INT, StatsRequestPayload::range,
            StatsRequestPayload::new);

    @Override
    public Type<StatsRequestPayload> type() {
        return TYPE;
    }
}
