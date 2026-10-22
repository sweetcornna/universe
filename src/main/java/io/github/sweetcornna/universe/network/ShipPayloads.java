package io.github.sweetcornna.universe.network;

import java.util.List;

import io.github.sweetcornna.universe.Universe;
import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** 飞船面板、召唤和自动驾驶的包（ADR-0024）。 */
public final class ShipPayloads {
    private ShipPayloads() {
    }

    /** 服务端 → 客户端：飞行中改了技术，驾驶台的方块实体数据换了（只有客户端该知道的那部分）。 */
    public record Helm(int entity, CompoundTag data) implements CustomPacketPayload {
        public static final Type<Helm> TYPE = new Type<>(Universe.id("ship_helm"));
        public static final StreamCodec<ByteBuf, Helm> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Helm::entity,
                ByteBufCodecs.COMPOUND_TAG, Helm::data,
                Helm::new);

        @Override
        public Type<Helm> type() {
            return TYPE;
        }
    }

    /** 客户端 → 服务端：按了召唤键。在太空里开着船时是打开自动驾驶的目的地列表。 */
    public record Summon() implements CustomPacketPayload {
        public static final Summon INSTANCE = new Summon();
        public static final Type<Summon> TYPE = new Type<>(Universe.id("ship_summon"));
        public static final StreamCodec<ByteBuf, Summon> STREAM_CODEC = StreamCodec.unit(INSTANCE);

        @Override
        public Type<Summon> type() {
            return TYPE;
        }
    }

    /**
     * 自动驾驶的一个目的地。
     *
     * @param kind     {@link #PLANET}、{@link #MOON}、{@link #STATION}……
     * @param distance 离船多远（格）
     */
    public record Destination(int kind, String nameZh, String nameEn, int distance) {
        public static final int PLANET = 0;
        public static final int MOON = 1;
        public static final int STATION = 2;
        public static final int FREIGHTER = 3;
        public static final int SIGNAL = 4;
        /** 不是去哪：把自己的货船叫到船头前面（ADR-0024）。 */
        public static final int SUMMON_FREIGHTER = 5;
        /** 这个系的黑洞（ADR-0024），停在视界外面。 */
        public static final int BLACK_HOLE = 6;
        public static final StreamCodec<ByteBuf, Destination> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Destination::kind,
                ByteBufCodecs.STRING_UTF8, Destination::nameZh,
                ByteBufCodecs.STRING_UTF8, Destination::nameEn,
                ByteBufCodecs.VAR_INT, Destination::distance,
                Destination::new);
    }

    /** 服务端 → 客户端：打开自动驾驶的目的地列表。 */
    public record Destinations(List<Destination> list) implements CustomPacketPayload {
        public static final Type<Destinations> TYPE = new Type<>(Universe.id("ship_destinations"));
        public static final StreamCodec<ByteBuf, Destinations> STREAM_CODEC = Destination.STREAM_CODEC.apply(ByteBufCodecs.list(64))
                .map(Destinations::new, Destinations::list);

        @Override
        public Type<Destinations> type() {
            return TYPE;
        }
    }

    /** 客户端 → 服务端：选了自动驾驶的目的地（列表里的下标）。 */
    public record Navigate(int index) implements CustomPacketPayload {
        public static final Type<Navigate> TYPE = new Type<>(Universe.id("ship_navigate"));
        public static final StreamCodec<ByteBuf, Navigate> STREAM_CODEC = ByteBufCodecs.VAR_INT.map(Navigate::new, Navigate::index);

        @Override
        public Type<Navigate> type() {
            return TYPE;
        }
    }
}
