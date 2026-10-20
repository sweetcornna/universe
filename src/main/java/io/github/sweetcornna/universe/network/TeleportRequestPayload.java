package io.github.sweetcornna.universe.network;

import io.github.sweetcornna.universe.Universe;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * 客户端 → 服务端：在传送器的界面里选了一个目的地（ADR-0017）。
 *
 * @param pos  用的是哪台传送器
 * @param kind {@link OpenTeleporterPayload#BASE} 或 {@link OpenTeleporterPayload#STATION}
 * @param id   基地的 UUID 或恒星系序号
 */
public record TeleportRequestPayload(BlockPos pos, byte kind, String id) implements CustomPacketPayload {
    public static final Type<TeleportRequestPayload> TYPE = new Type<>(Universe.id("teleport_request"));
    public static final StreamCodec<ByteBuf, TeleportRequestPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, TeleportRequestPayload::pos,
            ByteBufCodecs.BYTE, TeleportRequestPayload::kind,
            ByteBufCodecs.stringUtf8(64), TeleportRequestPayload::id,
            TeleportRequestPayload::new);

    @Override
    public Type<TeleportRequestPayload> type() {
        return TYPE;
    }
}
