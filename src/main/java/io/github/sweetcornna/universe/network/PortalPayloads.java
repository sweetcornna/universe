package io.github.sweetcornna.universe.network;

import java.util.List;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.core.portal.PortalAddress;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** 先驱者传送门拨号盘的数据包（ADR-0024）。 */
public final class PortalPayloads {
    private PortalPayloads() {
    }

    /**
     * 服务端 → 客户端：打开拨号盘。
     *
     * @param own   这座门自己的地址（{@link PortalAddress#LENGTH} 个符文）
     * @param known 玩家认识的符文（按位）
     * @param all   创造模式：不认识的符文也能按
     */
    public record Open(BlockPos dial, List<Integer> own, int known, boolean all) implements CustomPacketPayload {
        public static final Type<Open> TYPE = new Type<>(Universe.id("portal_open"));
        public static final StreamCodec<ByteBuf, Open> STREAM_CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, Open::dial,
                ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(PortalAddress.LENGTH)), Open::own,
                ByteBufCodecs.VAR_INT, Open::known,
                ByteBufCodecs.BOOL, Open::all,
                Open::new);

        @Override
        public Type<Open> type() {
            return TYPE;
        }
    }

    /** 客户端 → 服务端：在这个拨号台上拨这个地址。 */
    public record Dial(BlockPos dial, List<Integer> glyphs) implements CustomPacketPayload {
        public static final Type<Dial> TYPE = new Type<>(Universe.id("portal_dial"));
        public static final StreamCodec<ByteBuf, Dial> STREAM_CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, Dial::dial,
                ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(PortalAddress.LENGTH)), Dial::glyphs,
                Dial::new);

        @Override
        public Type<Dial> type() {
            return TYPE;
        }
    }
}
