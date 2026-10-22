package io.github.sweetcornna.universe.network;

import io.github.sweetcornna.universe.Universe;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** 枪骑轰炸的数据包（ADR-0028 §八）。 */
public final class LancerPayloads {
    private LancerPayloads() {
    }

    /**
     * 服务端 → 客户端：巢穴派枪骑来轰炸这颗星球了。发给这颗星球上的人，HUD 上标出它们出现的地方。
     *
     * @param target 要炸的那片设施的中心
     * @param from   枪骑出现的地方（目标上空）
     * @param count  几架
     * @param ticks  还有多少刻到；0 表示已经到了，-1 表示结束了（清掉 HUD 上的标记）
     */
    public record Warning(BlockPos target, BlockPos from, int count, int ticks) implements CustomPacketPayload {
        public static final Warning CLEAR = new Warning(BlockPos.ZERO, BlockPos.ZERO, 0, -1);
        public static final Type<Warning> TYPE = new Type<>(Universe.id("lancer_warning"));
        public static final StreamCodec<ByteBuf, Warning> STREAM_CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, Warning::target,
                BlockPos.STREAM_CODEC, Warning::from,
                ByteBufCodecs.VAR_INT, Warning::count,
                ByteBufCodecs.VAR_INT, Warning::ticks,
                Warning::new);

        @Override
        public Type<Warning> type() {
            return TYPE;
        }
    }
}
