package io.github.sweetcornna.universe.gravity;

import io.github.sweetcornna.universe.Universe;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** 引力抓取器的包（ADR-0034 §七）。 */
public final class GravityPayloads {
    private GravityPayloads() {
    }

    /** 客户端 → 服务端：抓着东西时左键扔出去，或者潜行左键往前推。 */
    public record Action(boolean push) implements CustomPacketPayload {
        public static final Type<Action> TYPE = new Type<>(Universe.id("gravity_action"));
        public static final StreamCodec<ByteBuf, Action> STREAM_CODEC = ByteBufCodecs.BOOL.map(Action::new, Action::push);

        @Override
        public Type<Action> type() {
            return TYPE;
        }
    }

    /**
     * 服务端 → 客户端：谁抓着什么（画引力束）。
     *
     * @param target 抓着的实体；−1 表示松开了
     */
    public record Beam(int holder, int target) implements CustomPacketPayload {
        public static final Type<Beam> TYPE = new Type<>(Universe.id("gravity_beam"));
        public static final StreamCodec<ByteBuf, Beam> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Beam::holder,
                ByteBufCodecs.VAR_INT.map(i -> i - 1, i -> i + 1), Beam::target,
                Beam::new);

        @Override
        public Type<Beam> type() {
            return TYPE;
        }
    }

    /** 服务端 → 客户端：推了一下（画锥形的冲击波）。 */
    public record Pushed(int holder) implements CustomPacketPayload {
        public static final Type<Pushed> TYPE = new Type<>(Universe.id("gravity_pushed"));
        public static final StreamCodec<ByteBuf, Pushed> STREAM_CODEC = ByteBufCodecs.VAR_INT.map(Pushed::new, Pushed::holder);

        @Override
        public Type<Pushed> type() {
            return TYPE;
        }
    }
}
