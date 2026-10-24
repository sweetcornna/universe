package io.github.sweetcornna.universe.deepspace;

import java.util.List;

import io.github.sweetcornna.universe.Universe;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** 深空的包（ADR-0034 §四）：HUD 上的兴趣点、出舱、开关牵引光束。 */
public final class DeepSpacePayloads {
    private DeepSpacePayloads() {
    }

    /**
     * HUD 上的一个兴趣点。
     *
     * @param kind      {@link io.github.sweetcornna.universe.core.deepspace.DeepSpaceSites.Kind} 的序号
     * @param integrity 残骸的结构完整度（别的是 −1）
     * @param countdown 熔毁倒计时（刻）；没在熔毁是 −1
     * @param salvaged  残骸的堆芯已经被拿走了
     */
    public record Marker(int kind, double x, double y, double z, int integrity, int countdown, boolean salvaged) {
        public static final StreamCodec<ByteBuf, Marker> STREAM_CODEC = new StreamCodec<>() {
            @Override
            public Marker decode(ByteBuf buf) {
                return new Marker(ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.DOUBLE.decode(buf), ByteBufCodecs.DOUBLE.decode(buf),
                        ByteBufCodecs.DOUBLE.decode(buf), ByteBufCodecs.VAR_INT.decode(buf) - 1, ByteBufCodecs.VAR_INT.decode(buf) - 1,
                        ByteBufCodecs.BOOL.decode(buf));
            }

            @Override
            public void encode(ByteBuf buf, Marker marker) {
                ByteBufCodecs.VAR_INT.encode(buf, marker.kind);
                ByteBufCodecs.DOUBLE.encode(buf, marker.x);
                ByteBufCodecs.DOUBLE.encode(buf, marker.y);
                ByteBufCodecs.DOUBLE.encode(buf, marker.z);
                ByteBufCodecs.VAR_INT.encode(buf, marker.integrity + 1);
                ByteBufCodecs.VAR_INT.encode(buf, marker.countdown + 1);
                ByteBufCodecs.BOOL.encode(buf, marker.salvaged);
            }
        };
    }

    /** 服务端 → 客户端：玩家附近的深空兴趣点（每秒一次）。 */
    public record Sites(List<Marker> markers) implements CustomPacketPayload {
        public static final Type<Sites> TYPE = new Type<>(Universe.id("deep_space_sites"));
        public static final StreamCodec<ByteBuf, Sites> STREAM_CODEC = Marker.STREAM_CODEC.apply(ByteBufCodecs.list(32))
                .map(Sites::new, Sites::markers);

        @Override
        public Type<Sites> type() {
            return TYPE;
        }
    }

    /** 客户端 → 服务端：在太空里开着船，按了出舱键。 */
    public record Eva() implements CustomPacketPayload {
        public static final Eva INSTANCE = new Eva();
        public static final Type<Eva> TYPE = new Type<>(Universe.id("ship_eva"));
        public static final StreamCodec<ByteBuf, Eva> STREAM_CODEC = StreamCodec.unit(INSTANCE);

        @Override
        public Type<Eva> type() {
            return TYPE;
        }
    }

    /** 客户端 → 服务端：开着船，按了牵引光束键。 */
    public record Tractor() implements CustomPacketPayload {
        public static final Tractor INSTANCE = new Tractor();
        public static final Type<Tractor> TYPE = new Type<>(Universe.id("ship_tractor"));
        public static final StreamCodec<ByteBuf, Tractor> STREAM_CODEC = StreamCodec.unit(INSTANCE);

        @Override
        public Type<Tractor> type() {
            return TYPE;
        }
    }
}
