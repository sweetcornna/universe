package io.github.sweetcornna.universe.network;

import io.github.sweetcornna.universe.Universe;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * 客户端 → 服务端：驾驶员按下、松开开火键，或者按了武器模式切换键（ADR-0017）。开火的方向由服务端按驾驶员的视线算。
 *
 * @param action {@link #FIRE_ON}、{@link #FIRE_OFF} 或 {@link #MODE}
 */
public record ShipControlPayload(int action) implements CustomPacketPayload {
    public static final int FIRE_ON = 0;
    public static final int FIRE_OFF = 1;
    public static final int MODE = 2;
    public static final Type<ShipControlPayload> TYPE = new Type<>(Universe.id("ship_control"));
    public static final StreamCodec<ByteBuf, ShipControlPayload> STREAM_CODEC = ByteBufCodecs.VAR_INT.map(ShipControlPayload::new,
            ShipControlPayload::action);

    @Override
    public Type<ShipControlPayload> type() {
        return TYPE;
    }
}
