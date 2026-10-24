package io.github.sweetcornna.universe.beacon;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.github.sweetcornna.universe.core.beacon.Memo;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** 备忘（{@link Memo}）的存档和网络格式。读进来的都过一遍 {@link Memo#clean}。 */
public final class MemoCodecs {
    public static final Codec<Memo.Line> LINE_CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("text").forGetter(Memo.Line::text),
            Codec.BOOL.optionalFieldOf("done", false).forGetter(Memo.Line::done)
    ).apply(i, Memo.Line::new));
    public static final Codec<Memo> CODEC = LINE_CODEC.listOf().xmap(Memo::clean, Memo::lines);

    public static final StreamCodec<ByteBuf, Memo.Line> LINE_STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(Memo.MAX_CHARS * 4), Memo.Line::text,
            ByteBufCodecs.BOOL, Memo.Line::done,
            Memo.Line::new);
    public static final StreamCodec<ByteBuf, Memo> STREAM_CODEC = LINE_STREAM_CODEC.apply(ByteBufCodecs.list(Memo.MAX_LINES * 2))
            .map(Memo::clean, Memo::lines);

    private MemoCodecs() {
    }
}
