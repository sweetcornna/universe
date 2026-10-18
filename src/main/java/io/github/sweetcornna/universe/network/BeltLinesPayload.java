package io.github.sweetcornna.universe.network;

import java.util.ArrayList;
import java.util.List;

import io.github.sweetcornna.universe.Universe;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * 服务端 → 客户端：几条传送带线路的快照（ADR-0011）。每条线路给出起点的方块位置、往后每格的走向（一个字节）、
 * 物品的位置和编号，以及尽头的储物仓是不是堵住了。位置从前往后递减，按差值写成变长整数，一格满载的传送带每个物品
 * 两三个字节。
 */
public record BeltLinesPayload(List<Line> lines) implements CustomPacketPayload {
    public static final Type<BeltLinesPayload> TYPE = new Type<>(Universe.id("belt_lines"));
    public static final StreamCodec<FriendlyByteBuf, BeltLinesPayload> STREAM_CODEC = StreamCodec.of(BeltLinesPayload::write,
            BeltLinesPayload::read);

    /**
     * 一条线路。
     *
     * @param start     起点的方块位置（{@code BlockPos#asLong}）
     * @param steps     第 k 格到第 k + 1 格的走向，见 {@link #step}
     * @param positions 物品的位置（单位，从线路起点算），从前往后
     * @param items     物品编号（物品注册表的数字 ID）
     * @param blocked   尽头的储物仓放不下了
     */
    public record Line(long start, byte[] steps, int[] positions, int[] items, boolean blocked) {
    }

    /** 相邻两格的坐标差（各在 -1 到 1 之间）编成一个字节。 */
    public static byte step(int dx, int dy, int dz) {
        return (byte) ((dx + 1) * 9 + (dy + 1) * 3 + dz + 1);
    }

    public static int[] delta(byte step) {
        int s = step;
        return new int[] {s / 9 - 1, s / 3 % 3 - 1, s % 3 - 1};
    }

    private static void write(FriendlyByteBuf buf, BeltLinesPayload payload) {
        buf.writeVarInt(payload.lines.size());
        for (Line line : payload.lines) {
            buf.writeLong(line.start);
            buf.writeByteArray(line.steps);
            int n = Math.min(line.positions.length, line.items.length);
            buf.writeVarInt(n);
            int previous = 0;
            for (int i = 0; i < n; i++) {
                // 第一个写本身，之后写和前一个的差（都是非负数）
                buf.writeVarInt(i == 0 ? line.positions[i] : previous - line.positions[i]);
                previous = line.positions[i];
                buf.writeVarInt(line.items[i]);
            }
            buf.writeBoolean(line.blocked);
        }
    }

    private static BeltLinesPayload read(FriendlyByteBuf buf) {
        int count = buf.readVarInt();
        List<Line> lines = new ArrayList<>(count);
        for (int k = 0; k < count; k++) {
            long start = buf.readLong();
            byte[] steps = buf.readByteArray();
            int n = buf.readVarInt();
            int[] positions = new int[n];
            int[] items = new int[n];
            int previous = 0;
            for (int i = 0; i < n; i++) {
                int value = buf.readVarInt();
                positions[i] = i == 0 ? value : previous - value;
                previous = positions[i];
                items[i] = buf.readVarInt();
            }
            lines.add(new Line(start, steps, positions, items, buf.readBoolean()));
        }
        return new BeltLinesPayload(lines);
    }

    @Override
    public Type<BeltLinesPayload> type() {
        return TYPE;
    }
}
