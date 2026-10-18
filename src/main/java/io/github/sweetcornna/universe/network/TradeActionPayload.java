package io.github.sweetcornna.universe.network;

import io.github.sweetcornna.universe.Universe;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * 客户端 → 服务端：在交易界面上点了一行（ADR-0017）。服务端重新检查终端的距离、物品和信用点，价钱以服务端为准。
 *
 * @param buy  true 购买，false 卖出
 * @param key  那一行的键（{@link TradeStatePayload.Row#key()}）
 * @param mode 0 一件，1 一组（Shift），2 全部（Ctrl，只用于卖出）
 */
public record TradeActionPayload(boolean buy, String key, int mode) implements CustomPacketPayload {
    public static final Type<TradeActionPayload> TYPE = new Type<>(Universe.id("trade_action"));
    public static final StreamCodec<ByteBuf, TradeActionPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, TradeActionPayload::buy,
            ByteBufCodecs.stringUtf8(256), TradeActionPayload::key,
            ByteBufCodecs.VAR_INT, TradeActionPayload::mode,
            TradeActionPayload::new);

    public static final int ONE = 0;
    public static final int STACK = 1;
    public static final int ALL = 2;

    @Override
    public Type<TradeActionPayload> type() {
        return TYPE;
    }
}
