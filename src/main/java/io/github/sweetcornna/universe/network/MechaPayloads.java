package io.github.sweetcornna.universe.network;

import io.github.sweetcornna.universe.Universe;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** 机甲面板的包（ADR-0023）。 */
public final class MechaPayloads {
    private MechaPayloads() {
    }

    /** 客户端 → 服务端：按了机甲面板键。 */
    public record Open() implements CustomPacketPayload {
        public static final Open INSTANCE = new Open();
        public static final Type<Open> TYPE = new Type<>(Universe.id("open_mecha"));
        public static final StreamCodec<ByteBuf, Open> STREAM_CODEC = StreamCodec.unit(INSTANCE);

        @Override
        public Type<Open> type() {
            return TYPE;
        }
    }

    /** 客户端 → 服务端：改一个物流栏位的上下限。 */
    public record Bounds(int index, int min, int max) implements CustomPacketPayload {
        public static final Type<Bounds> TYPE = new Type<>(Universe.id("mecha_bounds"));
        public static final StreamCodec<ByteBuf, Bounds> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Bounds::index,
                ByteBufCodecs.VAR_INT, Bounds::min,
                ByteBufCodecs.VAR_INT, Bounds::max,
                Bounds::new);

        @Override
        public Type<Bounds> type() {
            return TYPE;
        }
    }
}
