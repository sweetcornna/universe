package io.github.sweetcornna.universe.network;

import io.github.sweetcornna.universe.Universe;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * 客户端 → 服务端：在科技树界面上开始研究、停止研究、上交物品完成科技，以及排研究队列（ADR-0033 §五）。服务端会重新检查
 * 前置和物品。队列的操作带着第几项和那一项的 ID，两边对不上（别人刚改过队列）就不动。
 */
public record ResearchActionPayload(Action action, String tech, int index) implements CustomPacketPayload {
    public static final Type<ResearchActionPayload> TYPE = new Type<>(Universe.id("research_action"));
    public static final StreamCodec<ByteBuf, ResearchActionPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.idMapper(i -> Action.values()[i], Action::ordinal), ResearchActionPayload::action,
            ByteBufCodecs.STRING_UTF8, ResearchActionPayload::tech,
            ByteBufCodecs.VAR_INT, ResearchActionPayload::index,
            ResearchActionPayload::new);

    public ResearchActionPayload(Action action, String tech) {
        this(action, tech, -1);
    }

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
        HAND_IN,
        /** 排到研究队列的最后。 */
        QUEUE_ADD,
        /** 从队列里去掉第 index 项。 */
        QUEUE_REMOVE,
        /** 第 index 项往前挪一格。 */
        QUEUE_UP,
        /** 第 index 项往后挪一格。 */
        QUEUE_DOWN,
        /** 清空队列。 */
        QUEUE_CLEAR
    }
}
