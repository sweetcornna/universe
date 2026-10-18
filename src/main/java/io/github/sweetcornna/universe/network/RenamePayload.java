package io.github.sweetcornna.universe.network;

import io.github.sweetcornna.universe.Universe;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * 客户端 → 服务端：发现者在发现日志里给一样东西改名（ADR-0012）。空名字表示恢复原名。
 *
 * @param key  {@code DiscoveryKey} 的字符串形式
 * @param name 新名字
 */
public record RenamePayload(String key, String name) implements CustomPacketPayload {
    public static final Type<RenamePayload> TYPE = new Type<>(Universe.id("rename"));
    public static final StreamCodec<ByteBuf, RenamePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(256), RenamePayload::key,
            ByteBufCodecs.stringUtf8(128), RenamePayload::name,
            RenamePayload::new);

    @Override
    public Type<RenamePayload> type() {
        return TYPE;
    }
}
