package io.github.sweetcornna.universe.network;

import java.util.ArrayList;
import java.util.List;

import io.github.sweetcornna.universe.Universe;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * 服务端 → 客户端：玩家右键了传送器，打开目的地列表（ADR-0017）。名字由客户端按自己的星团副本和语言拼。
 *
 * @param free      空间站里的传送器，不用电
 * @param energy    现在的电量（焦）
 * @param connected 接上了电网
 */
public record OpenTeleporterPayload(BlockPos pos, boolean free, long energy, boolean connected, List<Destination> destinations)
        implements CustomPacketPayload {
    public static final Type<OpenTeleporterPayload> TYPE = new Type<>(Universe.id("open_teleporter"));
    public static final StreamCodec<FriendlyByteBuf, OpenTeleporterPayload> STREAM_CODEC = StreamCodec.of(OpenTeleporterPayload::write,
            OpenTeleporterPayload::read);
    /** 列表最多多少项。 */
    public static final int MAX_DESTINATIONS = 128;
    public static final byte BASE = 0;
    public static final byte STATION = 1;

    /**
     * 一个目的地。
     *
     * @param kind      {@link #BASE} 或 {@link #STATION}
     * @param id        传送时回传给服务端的键：基地的 UUID 或恒星系序号
     * @param name      基地的名字，空串用默认名；空间站不用
     * @param number    基地默认名里的编号
     * @param body      基地所在天体的路径（{@code BodyId#path}），不在星团里的维度是空串
     * @param dimension 所在维度，名字拼不出来时显示它
     * @param system    所在恒星系；不在星团里是 -1
     * @param here      就是现在所在的地方（这台传送器所在的基地或空间站）
     */
    public record Destination(byte kind, String id, String name, int number, String body, String dimension, int system, boolean here) {
    }

    private static void write(FriendlyByteBuf buf, OpenTeleporterPayload payload) {
        buf.writeBlockPos(payload.pos);
        buf.writeBoolean(payload.free);
        buf.writeVarLong(payload.energy);
        buf.writeBoolean(payload.connected);
        int n = Math.min(MAX_DESTINATIONS, payload.destinations.size());
        buf.writeVarInt(n);
        for (int i = 0; i < n; i++) {
            Destination destination = payload.destinations.get(i);
            buf.writeByte(destination.kind);
            buf.writeUtf(destination.id, 64);
            buf.writeUtf(destination.name, 128);
            buf.writeVarInt(destination.number);
            buf.writeUtf(destination.body, 32);
            buf.writeUtf(destination.dimension, 256);
            buf.writeVarInt(destination.system + 1);
            buf.writeBoolean(destination.here);
        }
    }

    private static OpenTeleporterPayload read(FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        boolean free = buf.readBoolean();
        long energy = buf.readVarLong();
        boolean connected = buf.readBoolean();
        int n = Math.min(MAX_DESTINATIONS, buf.readVarInt());
        List<Destination> destinations = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            destinations.add(new Destination(buf.readByte(), buf.readUtf(64), buf.readUtf(128), buf.readVarInt(), buf.readUtf(32),
                    buf.readUtf(256), buf.readVarInt() - 1, buf.readBoolean()));
        }
        return new OpenTeleporterPayload(pos, free, energy, connected, destinations);
    }

    @Override
    public Type<OpenTeleporterPayload> type() {
        return TYPE;
    }
}
