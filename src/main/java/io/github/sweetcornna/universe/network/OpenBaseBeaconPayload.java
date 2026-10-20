package io.github.sweetcornna.universe.network;

import io.github.sweetcornna.universe.Universe;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * 服务端 → 客户端：主人右键了自己的基地信标，打开改名界面（ADR-0017）。
 *
 * @param name   现在的名字，空串是默认名
 * @param number 默认名里的编号
 * @param body   所在天体的路径，不在星团里的维度是空串
 */
public record OpenBaseBeaconPayload(BlockPos pos, String name, int number, String body) implements CustomPacketPayload {
    public static final Type<OpenBaseBeaconPayload> TYPE = new Type<>(Universe.id("open_base_beacon"));
    public static final StreamCodec<ByteBuf, OpenBaseBeaconPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, OpenBaseBeaconPayload::pos,
            ByteBufCodecs.stringUtf8(128), OpenBaseBeaconPayload::name,
            ByteBufCodecs.VAR_INT, OpenBaseBeaconPayload::number,
            ByteBufCodecs.stringUtf8(32), OpenBaseBeaconPayload::body,
            OpenBaseBeaconPayload::new);

    @Override
    public Type<OpenBaseBeaconPayload> type() {
        return TYPE;
    }
}
