package io.github.sweetcornna.universe.network;

import io.github.sweetcornna.universe.Universe;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** 太空遭遇的数据包（ADR-0024）。 */
public final class EncounterPayloads {
    private EncounterPayloads() {
    }

    /**
     * 服务端 → 客户端：玩家收到的信号在哪，HUD 上标出来。
     *
     * @param kind   {@link #NONE}、{@link #DERELICT}、{@link #DISTRESS}
     * @param system 在哪个恒星系的太空里
     * @param hull   遇险的货船还剩多少船体（百分比）；海盗还没露面时为 -1
     */
    public record Signal(int kind, int system, double x, double y, double z, int hull) implements CustomPacketPayload {
        public static final int NONE = 0;
        public static final int DERELICT = 1;
        public static final int DISTRESS = 2;
        public static final Signal EMPTY = new Signal(NONE, -1, 0, 0, 0, -1);
        public static final Type<Signal> TYPE = new Type<>(Universe.id("encounter_signal"));
        public static final StreamCodec<ByteBuf, Signal> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Signal::kind,
                ByteBufCodecs.VAR_INT, Signal::system,
                ByteBufCodecs.DOUBLE, Signal::x,
                ByteBufCodecs.DOUBLE, Signal::y,
                ByteBufCodecs.DOUBLE, Signal::z,
                ByteBufCodecs.VAR_INT, Signal::hull,
                Signal::new);

        @Override
        public Type<Signal> type() {
            return TYPE;
        }
    }
}
