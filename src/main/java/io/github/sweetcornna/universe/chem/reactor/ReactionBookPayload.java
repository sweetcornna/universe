package io.github.sweetcornna.universe.chem.reactor;

import java.util.List;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.core.chem.reaction.ReactionBook;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** 服务端 → 客户端：全服发现了的反应（ADR-0037）。登录时发一次，有新发现时发给所有玩家；数据很小，整份发。 */
public record ReactionBookPayload(List<String> discovered) implements CustomPacketPayload {
    public static final Type<ReactionBookPayload> TYPE = new Type<>(Universe.id("reaction_book"));
    public static final StreamCodec<ByteBuf, ReactionBookPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(64).apply(ByteBufCodecs.list(256)), ReactionBookPayload::discovered,
            ReactionBookPayload::new);

    public static ReactionBookPayload of(ReactionBook book) {
        return new ReactionBookPayload(book.names());
    }

    public ReactionBook book() {
        return ReactionBook.load(this.discovered);
    }

    @Override
    public Type<ReactionBookPayload> type() {
        return TYPE;
    }
}
