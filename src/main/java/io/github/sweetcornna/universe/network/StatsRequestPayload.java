package io.github.sweetcornna.universe.network;

import io.github.sweetcornna.universe.Universe;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * 客户端 → 服务端：统计面板要一份快照（ADR-0015、ADR-0033 §四）。面板开着时每 2 秒要一次。
 *
 * @param scope 看哪儿：维度 id；{@code system:<序号>} 是一个星系；空字符串是全部
 * @param range 时间范围（{@code ProductionStats.Range} 的序号）
 * @param item  选中的物品，非空时服务端另发它的曲线、上下游、参考产量和进出口
 * @param tab   面板在哪一页（{@link #PRODUCTION}、{@link #POWER}、{@link #DYSON}）；戴森球那页另发戴森球的统计
 */
public record StatsRequestPayload(String scope, int range, String item, int tab) implements CustomPacketPayload {
    public static final int PRODUCTION = 0;
    public static final int POWER = 1;
    public static final int DYSON = 2;

    public static final Type<StatsRequestPayload> TYPE = new Type<>(Universe.id("stats_request"));
    public static final StreamCodec<ByteBuf, StatsRequestPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(256), StatsRequestPayload::scope,
            ByteBufCodecs.VAR_INT, StatsRequestPayload::range,
            ByteBufCodecs.stringUtf8(256), StatsRequestPayload::item,
            ByteBufCodecs.VAR_INT, StatsRequestPayload::tab,
            StatsRequestPayload::new);

    @Override
    public Type<StatsRequestPayload> type() {
        return TYPE;
    }
}
