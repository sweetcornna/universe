package io.github.sweetcornna.universe.mecha.fleet;

import io.github.sweetcornna.universe.Universe;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** 舰队的包（ADR-0033 §一）。 */
public final class FleetPayloads {
    private FleetPayloads() {
    }

    /** 客户端 → 服务端：按了舰队键，在星球上出动或召回地面编队，在太空里管太空舰队。 */
    public record Toggle() implements CustomPacketPayload {
        public static final Toggle INSTANCE = new Toggle();
        public static final Type<Toggle> TYPE = new Type<>(Universe.id("fleet_toggle"));
        public static final StreamCodec<ByteBuf, Toggle> STREAM_CODEC = StreamCodec.unit(INSTANCE);

        @Override
        public Type<Toggle> type() {
            return TYPE;
        }
    }
}
