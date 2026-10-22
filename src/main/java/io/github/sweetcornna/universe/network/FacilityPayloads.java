package io.github.sweetcornna.universe.network;

import java.util.List;

import io.github.sweetcornna.universe.Universe;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** 设施耐久的数据包（ADR-0028 §四）。 */
public final class FacilityPayloads {
    private FacilityPayloads() {
    }

    /**
     * 服务端 → 客户端：附近掉了血的设施（准星对着时画耐久条）。
     *
     * @param positions 设施的位置（{@code BlockPos.asLong}，两格高的是下半截）
     * @param ratios    还剩的比例；1 表示修满了，客户端删掉这条
     * @param replace   整份替换（定时发的附近全部），否则只改这几条
     */
    public record Health(List<Long> positions, List<Float> ratios, boolean replace) implements CustomPacketPayload {
        public static final Type<Health> TYPE = new Type<>(Universe.id("facility_health"));
        public static final StreamCodec<ByteBuf, Health> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.LONG.apply(ByteBufCodecs.list(4096)), Health::positions,
                ByteBufCodecs.FLOAT.apply(ByteBufCodecs.list(4096)), Health::ratios,
                ByteBufCodecs.BOOL, Health::replace,
                Health::new);

        @Override
        public Type<Health> type() {
            return TYPE;
        }
    }
}
