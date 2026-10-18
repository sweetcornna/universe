package io.github.sweetcornna.universe.network;

import io.github.sweetcornna.universe.Universe;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** 服务端 → 客户端：玩家右键了信号增强器，打开选兴趣点的界面（ADR-0014）。 */
public record OpenSignalBoosterPayload(BlockPos pos) implements CustomPacketPayload {
    public static final Type<OpenSignalBoosterPayload> TYPE = new Type<>(Universe.id("open_signal_booster"));
    public static final StreamCodec<ByteBuf, OpenSignalBoosterPayload> STREAM_CODEC =
            BlockPos.STREAM_CODEC.map(OpenSignalBoosterPayload::new, OpenSignalBoosterPayload::pos);

    @Override
    public Type<OpenSignalBoosterPayload> type() {
        return TYPE;
    }
}
