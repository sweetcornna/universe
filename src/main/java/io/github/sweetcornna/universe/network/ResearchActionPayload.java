package io.github.sweetcornna.universe.network;

import io.github.sweetcornna.universe.Universe;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * 客户端 → 服务端：在科技树界面上开始研究、停止研究、上交物品完成科技。服务端会重新检查前置和物品。
 */
public record ResearchActionPayload(Action action, String tech) implements CustomPacketPayload {
    public static final Type<ResearchActionPayload> TYPE = new Type<>(Universe.id("research_action"));
    public static final StreamCodec<ByteBuf, ResearchActionPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.idMapper(i -> Action.values()[i], Action::ordinal), ResearchActionPayload::action,
            ByteBufCodecs.STRING_UTF8, ResearchActionPayload::tech,
            ResearchActionPayload::new);

    @Override
    public Type<ResearchActionPayload> type() {
        return TYPE;
    }

    public enum Action {
        /** 让研究站研究这个科技。 */
        START,
        /** 停止当前的研究，点数保留。 */
        STOP,
        /** 上交物品完成这个科技。 */
        HAND_IN
    }
}
