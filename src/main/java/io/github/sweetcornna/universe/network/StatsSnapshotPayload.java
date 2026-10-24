package io.github.sweetcornna.universe.network;

import java.util.ArrayList;
import java.util.List;

import io.github.sweetcornna.universe.Universe;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * 服务端 → 客户端：统计面板的快照（ADR-0015、ADR-0033 §四）。
 *
 * @param scope        快照看的地方：维度、{@code system:<序号>} 或空字符串（全部）
 * @param range        时间范围的序号
 * @param dimensions   有统计的维度，面板的星球、星系列表用
 * @param rows         每种物品每分钟的产出、消耗、运进、运出
 * @param generation   发电能力（每刻平均焦耳）
 * @param demand       用电需求（每刻平均焦耳）
 * @param charging     充进蓄电器和物流站的电（每刻平均焦耳）
 * @param satisfaction 满足率 0–1
 * @param generators   各种发电设备的发电能力（每刻平均焦耳），从多到少
 * @param consumers    各种用电设备的需求，从多到少
 */
public record StatsSnapshotPayload(String scope, int range, List<String> dimensions, List<Row> rows, double generation, double demand,
        double charging, double satisfaction, List<Part> generators, List<Part> consumers) implements CustomPacketPayload {
    public static final Type<StatsSnapshotPayload> TYPE = new Type<>(Universe.id("stats_snapshot"));
    public static final StreamCodec<FriendlyByteBuf, StatsSnapshotPayload> STREAM_CODEC = StreamCodec.of(StatsSnapshotPayload::write,
            StatsSnapshotPayload::read);
    /** 一次最多发多少种物品。 */
    public static final int MAX_ROWS = 256;
    /** 发电、用电各最多发几种设备。 */
    public static final int MAX_PARTS = 24;

    public record Row(String item, float produced, float consumed, float imported, float exported) {
    }

    /** 一种设备（方块 ID）的电。 */
    public record Part(String kind, float power) {
    }

    private static void write(FriendlyByteBuf buf, StatsSnapshotPayload payload) {
        buf.writeUtf(payload.scope, 256);
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
            buf.writeFloat(row.imported);
            buf.writeFloat(row.exported);
        }
        buf.writeDouble(payload.generation);
        buf.writeDouble(payload.demand);
        buf.writeDouble(payload.charging);
        buf.writeDouble(payload.satisfaction);
        writeParts(buf, payload.generators);
        writeParts(buf, payload.consumers);
    }

    private static void writeParts(FriendlyByteBuf buf, List<Part> parts) {
        int n = Math.min(MAX_PARTS, parts.size());
        buf.writeVarInt(n);
        for (int i = 0; i < n; i++) {
            buf.writeUtf(parts.get(i).kind, 256);
            buf.writeFloat(parts.get(i).power);
        }
    }

    private static List<Part> readParts(FriendlyByteBuf buf) {
        int n = Math.min(MAX_PARTS, buf.readVarInt());
        List<Part> parts = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            parts.add(new Part(buf.readUtf(256), buf.readFloat()));
        }
        return parts;
    }

    private static StatsSnapshotPayload read(FriendlyByteBuf buf) {
        String scope = buf.readUtf(256);
        int range = buf.readVarInt();
        int d = buf.readVarInt();
        List<String> dimensions = new ArrayList<>(d);
        for (int i = 0; i < d; i++) {
            dimensions.add(buf.readUtf(256));
        }
        int n = Math.min(MAX_ROWS, buf.readVarInt());
        List<Row> rows = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            rows.add(new Row(buf.readUtf(256), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat()));
        }
        double generation = buf.readDouble();
        double demand = buf.readDouble();
        double charging = buf.readDouble();
        double satisfaction = buf.readDouble();
        List<Part> generators = readParts(buf);
        List<Part> consumers = readParts(buf);
        return new StatsSnapshotPayload(scope, range, dimensions, rows, generation, demand, charging, satisfaction, generators, consumers);
    }

    @Override
    public Type<StatsSnapshotPayload> type() {
        return TYPE;
    }
}
