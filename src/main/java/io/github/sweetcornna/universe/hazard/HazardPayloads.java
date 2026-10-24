package io.github.sweetcornna.universe.hazard;

import io.github.sweetcornna.universe.Universe;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** 局部危害的数据包（ADR-0034 §六）。 */
public final class HazardPayloads {
    private HazardPayloads() {
    }

    /**
     * 服务端 → 客户端：地面在震，镜头晃一会儿（火山喷发的预警和喷发）。间歇泉、尘卷风客户端自己看得见，不发这个。
     *
     * @param strength 晃多大（度）
     * @param ticks    晃多久
     */
    public record Shake(float strength, int ticks) implements CustomPacketPayload {
        public static final Type<Shake> TYPE = new Type<>(Universe.id("hazard_shake"));
        public static final StreamCodec<ByteBuf, Shake> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.FLOAT, Shake::strength,
                ByteBufCodecs.VAR_INT, Shake::ticks,
                Shake::new);

        @Override
        public Type<Shake> type() {
            return TYPE;
        }
    }
}
