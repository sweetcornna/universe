package io.github.sweetcornna.universe.network;

import java.util.ArrayList;
import java.util.List;

import io.github.sweetcornna.universe.Universe;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * 服务端 → 客户端：统计面板的快照（ADR-0015）。
 *
 * @param dimension    快照的维度；空字符串是全部
 * @param range        时间范围的序号
 * @param dimensions   有统计的维度，面板的星球列表用
 * @param rows         每种物品每分钟的产出和消耗
 * @param generation   发电能力（每刻平均焦耳）
 * @param demand       用电需求（每刻平均焦耳）
 * @param charging     充进蓄电器和物流站的电（每刻平均焦耳）
 * @param satisfaction 满足率 0–1
 */
public record StatsSnapshotPayload(String dimension, int range, List<String> dimensions, List<Row> rows, double generation, double demand,
        double charging, double satisfaction) implements CustomPacketPayload {
    public static final Type<StatsSnapshotPayload> TYPE = new Type<>(Universe.id("stats_snapshot"));
    public static final StreamCodec<FriendlyByteBuf, StatsSnapshotPayload> STREAM_CODEC = StreamCodec.of(StatsSnapshotPayload::write,
            StatsSnapshotPayload::read);
    /** 一次最多发多少种物品。 */
    public static final int MAX_ROWS = 256;

    public record Row(String item, float produced, float consumed) {
    }

    private static void write(FriendlyByteBuf buf, StatsSnapshotPayload payload) {
        buf.writeUtf(payload.dimension, 256);
        buf.writeVarInt(payload.range);
        buf.writeVarInt(payload.dimensions.size());
        payload.dimensions.forEach(dimension -> buf.writeUtf(dimension, 256));
        int n = Math.min(MAX_ROWS, payload.rows.size());
        buf.writeVarInt(n);
        for (int i = 0; i < n; i++) {
            Row row = payload.rows.get(i);
            buf.writeUtf(row.item, 256);
            buf.writeFloat(row.produced);
            buf.writeFloat(row.consumed);
        }
        buf.writeDouble(payload.generation);
        buf.writeDouble(payload.demand);
        buf.writeDouble(payload.charging);
        buf.writeDouble(payload.satisfaction);
    }

    private static StatsSnapshotPayload read(FriendlyByteBuf buf) {
        String dimension = buf.readUtf(256);
        int range = buf.readVarInt();
        int d = buf.readVarInt();
        List<String> dimensions = new ArrayList<>(d);
        for (int i = 0; i < d; i++) {
            dimensions.add(buf.readUtf(256));
        }
        int n = Math.min(MAX_ROWS, buf.readVarInt());
        List<Row> rows = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            rows.add(new Row(buf.readUtf(256), buf.readFloat(), buf.readFloat()));
        }
        return new StatsSnapshotPayload(dimension, range, dimensions, rows, buf.readDouble(), buf.readDouble(), buf.readDouble(),
                buf.readDouble());
    }

    @Override
    public Type<StatsSnapshotPayload> type() {
        return TYPE;
    }
}
