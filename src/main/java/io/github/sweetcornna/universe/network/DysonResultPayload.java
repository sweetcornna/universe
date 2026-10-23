package io.github.sweetcornna.universe.network;

import io.github.sweetcornna.universe.Universe;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * 服务端 → 客户端：编辑器改一层网格的结果（ADR-0030 §四）。
 *
 * @param ok     改成了没有
 * @param key    结果的语言键
 * @param detail 具体原因的语言键（几何哪儿不对、蓝图哪儿坏了），没有是空串
 */
public record DysonResultPayload(int layer, boolean ok, String key, String detail) implements CustomPacketPayload {
    public static final Type<DysonResultPayload> TYPE = new Type<>(Universe.id("dyson_result"));
    public static final StreamCodec<ByteBuf, DysonResultPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, DysonResultPayload::layer,
            ByteBufCodecs.BOOL, DysonResultPayload::ok,
            ByteBufCodecs.stringUtf8(256), DysonResultPayload::key,
            ByteBufCodecs.stringUtf8(256), DysonResultPayload::detail,
            DysonResultPayload::new);

    @Override
    public Type<DysonResultPayload> type() {
        return TYPE;
    }
}
