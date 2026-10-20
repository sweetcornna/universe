package io.github.sweetcornna.universe.network;

import io.github.sweetcornna.universe.Universe;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** 客户端 → 服务端：给基地改名（ADR-0017）。空名字恢复默认名。 */
public record RenameBasePayload(BlockPos pos, String name) implements CustomPacketPayload {
    public static final Type<RenameBasePayload> TYPE = new Type<>(Universe.id("rename_base"));
    public static final StreamCodec<ByteBuf, RenameBasePayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, RenameBasePayload::pos,
            ByteBufCodecs.stringUtf8(128), RenameBasePayload::name,
            RenameBasePayload::new);

    @Override
    public Type<RenameBasePayload> type() {
        return TYPE;
    }
}
