package io.github.sweetcornna.universe.network;

import io.github.sweetcornna.universe.Universe;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** 伙伴界面的包（ADR-0026 §九）。按钮走菜单按钮，这里只有改名。 */
public final class CompanionPayloads {
    private CompanionPayloads() {
    }

    /** 客户端 → 服务端：给开着界面的伙伴改名。空名字恢复物种名，服务端再校验一遍长度。 */
    public record Rename(int containerId, String name) implements CustomPacketPayload {
        public static final Type<Rename> TYPE = new Type<>(Universe.id("rename_companion"));
        public static final StreamCodec<ByteBuf, Rename> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Rename::containerId,
                ByteBufCodecs.stringUtf8(128), Rename::name,
                Rename::new);

        @Override
        public Type<Rename> type() {
            return TYPE;
        }
    }
}
