package io.github.sweetcornna.universe.network;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.beacon.MemoCodecs;
import io.github.sweetcornna.universe.core.beacon.Memo;
import io.github.sweetcornna.universe.core.beacon.MemoKeys;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** 星球和恒星系的备忘（ADR-0033 §六）：键见 {@link MemoKeys}。 */
public final class MemoPayloads {
    private static final StreamCodec<ByteBuf, String> KEY = ByteBufCodecs.stringUtf8(MemoKeys.MAX_LENGTH);

    private MemoPayloads() {
    }

    /** 客户端 → 服务端：要这一页备忘来看、来改。 */
    public record Open(String key) implements CustomPacketPayload {
        public static final Type<Open> TYPE = new Type<>(Universe.id("memo_open"));
        public static final StreamCodec<ByteBuf, Open> STREAM_CODEC = StreamCodec.composite(KEY, Open::key, Open::new);

        @Override
        public Type<Open> type() {
            return TYPE;
        }
    }

    /** 服务端 → 客户端：一页备忘的内容，客户端收到就打开界面。 */
    public record Content(String key, Memo memo) implements CustomPacketPayload {
        public static final Type<Content> TYPE = new Type<>(Universe.id("memo_content"));
        public static final StreamCodec<ByteBuf, Content> STREAM_CODEC = StreamCodec.composite(
                KEY, Content::key,
                MemoCodecs.STREAM_CODEC, Content::memo,
                Content::new);

        @Override
        public Type<Content> type() {
            return TYPE;
        }
    }

    /** 客户端 → 服务端：存一页备忘。 */
    public record Save(String key, Memo memo) implements CustomPacketPayload {
        public static final Type<Save> TYPE = new Type<>(Universe.id("memo_save"));
        public static final StreamCodec<ByteBuf, Save> STREAM_CODEC = StreamCodec.composite(
                KEY, Save::key,
                MemoCodecs.STREAM_CODEC, Save::memo,
                Save::new);

        @Override
        public Type<Save> type() {
            return TYPE;
        }
    }
}
