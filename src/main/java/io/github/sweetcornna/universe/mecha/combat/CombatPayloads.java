package io.github.sweetcornna.universe.mecha.combat;

import io.github.sweetcornna.universe.Universe;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** 机甲战斗的包（ADR-0033 §二）。 */
public final class CombatPayloads {
    private CombatPayloads() {
    }

    /** 客户端 → 服务端：护盾爆发键按下（开始蓄力）或者松开（放出）。 */
    public record Burst(boolean pressed) implements CustomPacketPayload {
        public static final Type<Burst> TYPE = new Type<>(Universe.id("shield_burst"));
        public static final StreamCodec<ByteBuf, Burst> STREAM_CODEC = StreamCodec.composite(ByteBufCodecs.BOOL, Burst::pressed, Burst::new);

        @Override
        public Type<Burst> type() {
            return TYPE;
        }
    }

    /** 服务端 → 客户端：护盾爆发放出去了（{@code fired} 为假是没放成），冷却还剩几刻。 */
    public record BurstState(boolean fired, int cooldown) implements CustomPacketPayload {
        public static final Type<BurstState> TYPE = new Type<>(Universe.id("shield_burst_state"));
        public static final StreamCodec<ByteBuf, BurstState> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL, BurstState::fired,
                ByteBufCodecs.VAR_INT, BurstState::cooldown,
                BurstState::new);

        @Override
        public Type<BurstState> type() {
            return TYPE;
        }
    }
}
