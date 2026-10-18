package io.github.sweetcornna.universe.network;

import java.util.ArrayList;
import java.util.List;

import io.github.sweetcornna.universe.Universe;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * 服务端 → 客户端：玩家所在维度里在飞的物流载具（ADR-0015）。时刻是游戏刻，客户端按自己的游戏时间插值，
 * 行程变了才重发。
 */
public record LogisticsFlightsPayload(List<Flight> flights) implements CustomPacketPayload {
    public static final Type<LogisticsFlightsPayload> TYPE = new Type<>(Universe.id("logistics_flights"));
    public static final StreamCodec<FriendlyByteBuf, LogisticsFlightsPayload> STREAM_CODEC = StreamCodec.of(LogisticsFlightsPayload::write,
            LogisticsFlightsPayload::read);

    /**
     * 一趟行程。
     *
     * @param vessel    运输船（否则是无人机）
     * @param homeHere  出发的站在这个维度里
     * @param otherHere 对方的站在这个维度里
     * @param from      出发站的起降点
     * @param to        对方的起降点
     */
    public record Flight(boolean vessel, boolean homeHere, boolean otherHere, double fromX, double fromY, double fromZ, double toX,
            double toY, double toZ, long departAt, long arriveAt, long returnAt) {
    }

    private static void write(FriendlyByteBuf buf, LogisticsFlightsPayload payload) {
        buf.writeVarInt(payload.flights.size());
        for (Flight flight : payload.flights) {
            buf.writeByte((flight.vessel ? 1 : 0) | (flight.homeHere ? 2 : 0) | (flight.otherHere ? 4 : 0));
            buf.writeDouble(flight.fromX);
            buf.writeDouble(flight.fromY);
            buf.writeDouble(flight.fromZ);
            buf.writeDouble(flight.toX);
            buf.writeDouble(flight.toY);
            buf.writeDouble(flight.toZ);
            buf.writeVarLong(flight.departAt);
            buf.writeVarLong(flight.arriveAt - flight.departAt);
            buf.writeVarLong(flight.returnAt - flight.arriveAt);
        }
    }

    private static LogisticsFlightsPayload read(FriendlyByteBuf buf) {
        int n = buf.readVarInt();
        List<Flight> flights = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            int flags = buf.readByte();
            double fx = buf.readDouble();
            double fy = buf.readDouble();
            double fz = buf.readDouble();
            double tx = buf.readDouble();
            double ty = buf.readDouble();
            double tz = buf.readDouble();
            long depart = buf.readVarLong();
            long arrive = depart + buf.readVarLong();
            long back = arrive + buf.readVarLong();
            flights.add(new Flight((flags & 1) != 0, (flags & 2) != 0, (flags & 4) != 0, fx, fy, fz, tx, ty, tz, depart, arrive, back));
        }
        return new LogisticsFlightsPayload(flights);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
