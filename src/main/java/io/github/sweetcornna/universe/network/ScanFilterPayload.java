package io.github.sweetcornna.universe.network;

import io.github.sweetcornna.universe.Universe;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** 客户端 → 服务端：多功能工具扫描只看哪种矿（ADR-0033 §八），物品 ID；空字符串是全看。 */
public record ScanFilterPayload(String item) implements CustomPacketPayload {
    public static final Type<ScanFilterPayload> TYPE = new Type<>(Universe.id("scan_filter"));
    public static final StreamCodec<ByteBuf, ScanFilterPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(256), ScanFilterPayload::item,
            ScanFilterPayload::new);

    @Override
    public Type<ScanFilterPayload> type() {
        return TYPE;
    }
}
