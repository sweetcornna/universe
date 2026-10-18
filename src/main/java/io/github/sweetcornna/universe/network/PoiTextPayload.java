package io.github.sweetcornna.universe.network;

import io.github.sweetcornna.universe.Universe;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * 服务端 → 客户端：打开兴趣点的文字界面（ADR-0014）。日志和线索的文字由客户端按种子、段号自己拼，按客户端的语言显示。
 *
 * @param kind     数据终端的日志还是石碑的线索
 * @param seed     日志的种子（终端）
 * @param lore     第几段线索（石碑）
 * @param rewarded 这次是不是第一次，界面底下写上拿到了什么
 */
public record PoiTextPayload(Kind kind, long seed, int lore, boolean rewarded) implements CustomPacketPayload {
    public static final Type<PoiTextPayload> TYPE = new Type<>(Universe.id("poi_text"));
    public static final StreamCodec<ByteBuf, PoiTextPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.idMapper(i -> Kind.values()[Math.floorMod(i, Kind.values().length)], Kind::ordinal), PoiTextPayload::kind,
            ByteBufCodecs.VAR_LONG, PoiTextPayload::seed,
            ByteBufCodecs.VAR_INT, PoiTextPayload::lore,
            ByteBufCodecs.BOOL, PoiTextPayload::rewarded,
            PoiTextPayload::new);

    public enum Kind {
        TERMINAL,
        MONOLITH
    }

    @Override
    public Type<PoiTextPayload> type() {
        return TYPE;
    }
}
