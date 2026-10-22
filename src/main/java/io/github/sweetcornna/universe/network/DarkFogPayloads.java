package io.github.sweetcornna.universe.network;

import io.github.sweetcornna.universe.Universe;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** 黑雾的数据包（ADR-0028）。 */
public final class DarkFogPayloads {
    private DarkFogPayloads() {
    }

    /**
     * 服务端 → 客户端：黑雾要来进攻了（ADR-0028 §三）。发给这颗星球上的人，HUD 上标出来袭的方向。
     *
     * @param target  要打的那片设施的中心
     * @param from    进攻部队出现的地方（目标朝基地方向 80 格外）
     * @param raiders 突袭者的数量
     * @param rangers 游骑兵的数量
     * @param ticks   还有多少刻到；0 表示已经到了，-1 表示打完了（清掉 HUD 上的标记）
     */
    public record Warning(BlockPos target, BlockPos from, int raiders, int rangers, int ticks) implements CustomPacketPayload {
        public static final Warning CLEAR = new Warning(BlockPos.ZERO, BlockPos.ZERO, 0, 0, -1);
        public static final Type<Warning> TYPE = new Type<>(Universe.id("dark_fog_warning"));
        public static final StreamCodec<ByteBuf, Warning> STREAM_CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, Warning::target,
                BlockPos.STREAM_CODEC, Warning::from,
                ByteBufCodecs.VAR_INT, Warning::raiders,
                ByteBufCodecs.VAR_INT, Warning::rangers,
                ByteBufCodecs.VAR_INT, Warning::ticks,
                Warning::new);

        @Override
        public Type<Warning> type() {
            return TYPE;
        }
    }
}
