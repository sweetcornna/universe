package io.github.sweetcornna.universe.network;

import java.util.ArrayList;
import java.util.List;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.core.logistics.SpaceRoute;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * 服务端 → 客户端：玩家所在维度里在飞的物流载具（ADR-0015）。时刻是游戏刻，客户端按自己的游戏时间插值，
 * 行程变了才重发。恒星系的太空维度里还有路过的运输船（ADR-0030）：两头的位置服务端已经换算成太空坐标。
 */
public record LogisticsFlightsPayload(List<Flight> flights) implements CustomPacketPayload {
    public static final Type<LogisticsFlightsPayload> TYPE = new Type<>(Universe.id("logistics_flights"));
    public static final StreamCodec<FriendlyByteBuf, LogisticsFlightsPayload> STREAM_CODEC = StreamCodec.of(LogisticsFlightsPayload::write,
            LogisticsFlightsPayload::read);

    /**
     * 一趟行程。
     *
     * @param vessel   运输船（否则是无人机）
     * @param homeKind 出发的站是哪种端（{@link SpaceRoute} 的常量）：{@code PAD} 在这个维度里，{@code AWAY} 看不见
     * @param from     出发站的起降点；太空里是航线这一头的点
     * @param to       对方的起降点；太空里是航线那一头的点
     * @param route    恒星系太空维度里运输船的航线（{@link SpaceRoute}），否则是星球上的起降和拱形航线
     * @param via      太空航线的控制点（绕开恒星）；不是太空航线时不用
     */
    public record Flight(boolean vessel, boolean route, int homeKind, int otherKind, double fromX, double fromY, double fromZ, double toX,
            double toY, double toZ, double viaX, double viaY, double viaZ, long departAt, long arriveAt, long returnAt) {
        public boolean homeHere() {
            return this.homeKind == SpaceRoute.PAD;
        }

        public boolean otherHere() {
            return this.otherKind == SpaceRoute.PAD;
        }
    }

    private static void write(FriendlyByteBuf buf, LogisticsFlightsPayload payload) {
        buf.writeVarInt(payload.flights.size());
        for (Flight flight : payload.flights) {
            buf.writeByte((flight.vessel ? 1 : 0) | flight.homeKind << 1 | flight.otherKind << 3 | (flight.route ? 32 : 0));
            buf.writeDouble(flight.fromX);
            buf.writeDouble(flight.fromY);
            buf.writeDouble(flight.fromZ);
            buf.writeDouble(flight.toX);
            buf.writeDouble(flight.toY);
            buf.writeDouble(flight.toZ);
            if (flight.route) {
                buf.writeDouble(flight.viaX);
                buf.writeDouble(flight.viaY);
                buf.writeDouble(flight.viaZ);
            }
            buf.writeVarLong(flight.departAt);
            buf.writeVarLong(flight.arriveAt - flight.departAt);
            buf.writeVarLong(flight.returnAt - flight.arriveAt);
        }
    }

    private static LogisticsFlightsPayload read(FriendlyByteBuf buf) {
        int n = buf.readVarInt();
        List<Flight> flights = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            int flags = buf.readUnsignedByte();
            int homeKind = flags >> 1 & 3;
            int otherKind = flags >> 3 & 3;
            double fx = buf.readDouble();
            double fy = buf.readDouble();
            double fz = buf.readDouble();
            double tx = buf.readDouble();
            double ty = buf.readDouble();
            double tz = buf.readDouble();
            boolean route = (flags & 32) != 0;
            double vx = route ? buf.readDouble() : 0;
            double vy = route ? buf.readDouble() : 0;
            double vz = route ? buf.readDouble() : 0;
            long depart = buf.readVarLong();
            long arrive = depart + buf.readVarLong();
            long back = arrive + buf.readVarLong();
            flights.add(new Flight((flags & 1) != 0, route, homeKind, otherKind, fx, fy, fz, tx, ty, tz, vx, vy, vz, depart, arrive, back));
        }
        return new LogisticsFlightsPayload(flights);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
