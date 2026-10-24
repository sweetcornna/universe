package io.github.sweetcornna.universe.network;

import java.util.ArrayList;
import java.util.List;

import io.github.sweetcornna.universe.Universe;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * 服务端 → 客户端：仪表盘上每张图的数（ADR-0033 §四），和玩家身上钉的图一一对应。每 5 秒发一次，只发钉了的。
 *
 * <p>每种图的三个数和两条线：
 * <ul>
 * <li>物品：a 每分钟产出，b 每分钟消耗，c 参考产量；线是产出、消耗的曲线</li>
 * <li>电力：a 发电能力，b 用电需求（焦/刻），c 满足率；线是发电、需求的曲线</li>
 * <li>研究：title 是在研究的科技，a 攒了的点数，b 要攒的点数，c 队里还有几项；entries 是队里接下来的几项（count 是级数）</li>
 * <li>戴森球：a 总功率，b 接收站取走的（焦/刻），c 太阳帆张数；线 A 是最近几分钟的总功率</li>
 * <li>物流站：title 是站名；entries 是每格的物品、数量、上限</li>
 * </ul>
 */
public record DashboardDataPayload(List<Reading> readings) implements CustomPacketPayload {
    public static final Type<DashboardDataPayload> TYPE = new Type<>(Universe.id("dashboard_data"));
    public static final StreamCodec<FriendlyByteBuf, DashboardDataPayload> STREAM_CODEC = StreamCodec.of(DashboardDataPayload::write,
            DashboardDataPayload::read);

    public record Entry(String item, long count, long max) {
    }

    public record Reading(String title, double a, double b, double c, float[] lineA, float[] lineB, List<Entry> entries) {
        public static final Reading EMPTY = new Reading("", 0, 0, 0, new float[0], new float[0], List.of());
    }

    private static void write(FriendlyByteBuf buf, DashboardDataPayload payload) {
        buf.writeVarInt(payload.readings.size());
        for (Reading reading : payload.readings) {
            buf.writeUtf(reading.title, 256);
            buf.writeDouble(reading.a);
            buf.writeDouble(reading.b);
            buf.writeDouble(reading.c);
            StatsDetailPayload.writeFloats(buf, reading.lineA);
            StatsDetailPayload.writeFloats(buf, reading.lineB);
            int n = Math.min(16, reading.entries.size());
            buf.writeVarInt(n);
            for (int i = 0; i < n; i++) {
                Entry entry = reading.entries.get(i);
                buf.writeUtf(entry.item, 256);
                buf.writeVarLong(entry.count);
                buf.writeVarLong(entry.max);
            }
        }
    }

    private static DashboardDataPayload read(FriendlyByteBuf buf) {
        int size = Math.min(16, buf.readVarInt());
        List<Reading> readings = new ArrayList<>(size);
        for (int r = 0; r < size; r++) {
            String title = buf.readUtf(256);
            double a = buf.readDouble();
            double b = buf.readDouble();
            double c = buf.readDouble();
            float[] lineA = StatsDetailPayload.readFloats(buf);
            float[] lineB = StatsDetailPayload.readFloats(buf);
            int n = Math.min(16, buf.readVarInt());
            List<Entry> entries = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                entries.add(new Entry(buf.readUtf(256), buf.readVarLong(), buf.readVarLong()));
            }
            readings.add(new Reading(title, a, b, c, lineA, lineB, entries));
        }
        return new DashboardDataPayload(readings);
    }

    @Override
    public Type<DashboardDataPayload> type() {
        return TYPE;
    }
}
