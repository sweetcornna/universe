package io.github.sweetcornna.universe.network;

import io.github.sweetcornna.universe.Universe;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * 服务端 → 客户端：船上的玩家要看跃迁的画面效果了（ADR-0012）。
 *
 * @param stage  阶段
 * @param ticks  这个阶段持续多少刻
 * @param system 目标恒星系
 */
public record WarpPayload(Stage stage, int ticks, int system) implements CustomPacketPayload {
    public static final Type<WarpPayload> TYPE = new Type<>(Universe.id("warp"));
    public static final StreamCodec<ByteBuf, WarpPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.idMapper(i -> Stage.values()[i], Stage::ordinal), WarpPayload::stage,
            ByteBufCodecs.VAR_INT, WarpPayload::ticks,
            ByteBufCodecs.VAR_INT, WarpPayload::system,
            WarpPayload::new);

    @Override
    public Type<WarpPayload> type() {
        return TYPE;
    }

    public enum Stage {
        /** 开始充能：星光拉成线，视野变宽。 */
        SPOOL,
        /** 到了：一道白光，星光收回去。 */
        ARRIVE,
        /** 中断了（驾驶员下船、翘曲器没了）。 */
        CANCEL
    }
}
