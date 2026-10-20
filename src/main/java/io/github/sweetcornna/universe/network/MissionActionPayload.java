package io.github.sweetcornna.universe.network;

import io.github.sweetcornna.universe.Universe;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** 客户端 → 服务端：在任务板上接任务、放弃、交付（ADR-0017）。 */
public record MissionActionPayload(BlockPos pos, int action, int slot) implements CustomPacketPayload {
    public static final int ACCEPT = 0;
    public static final int ABANDON = 1;
    public static final int HAND_IN = 2;
    public static final Type<MissionActionPayload> TYPE = new Type<>(Universe.id("mission_action"));
    public static final StreamCodec<ByteBuf, MissionActionPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, MissionActionPayload::pos,
            ByteBufCodecs.VAR_INT, MissionActionPayload::action,
            ByteBufCodecs.VAR_INT, MissionActionPayload::slot,
            MissionActionPayload::new);

    @Override
    public Type<MissionActionPayload> type() {
        return TYPE;
    }
}
