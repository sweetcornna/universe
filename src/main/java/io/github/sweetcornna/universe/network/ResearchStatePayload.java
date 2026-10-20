package io.github.sweetcornna.universe.network;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.core.research.ResearchState;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * 服务端 → 客户端：整个世界的研究进度。登录时发一次，之后每次变化发给所有玩家；数据很小，整份发。
 */
public record ResearchStatePayload(List<String> done, Optional<String> current, Map<String, Integer> progress, Map<String, Integer> levels)
        implements CustomPacketPayload {
    public static final Type<ResearchStatePayload> TYPE = new Type<>(Universe.id("research_state"));
    public static final StreamCodec<ByteBuf, ResearchStatePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), ResearchStatePayload::done,
            ByteBufCodecs.optional(ByteBufCodecs.STRING_UTF8), ResearchStatePayload::current,
            ByteBufCodecs.<ByteBuf, String, Integer, Map<String, Integer>>map(HashMap::new, ByteBufCodecs.STRING_UTF8, ByteBufCodecs.VAR_INT),
            ResearchStatePayload::progress,
            ByteBufCodecs.<ByteBuf, String, Integer, Map<String, Integer>>map(HashMap::new, ByteBufCodecs.STRING_UTF8, ByteBufCodecs.VAR_INT),
            ResearchStatePayload::levels,
            ResearchStatePayload::new);

    public static ResearchStatePayload of(ResearchState state) {
        return new ResearchStatePayload(List.copyOf(state.done()), Optional.ofNullable(state.current()), Map.copyOf(state.progress()),
                Map.copyOf(state.levels()));
    }

    public ResearchState toState() {
        return new ResearchState(new LinkedHashSet<>(this.done), this.current.orElse(null), this.progress, this.levels);
    }

    @Override
    public Type<ResearchStatePayload> type() {
        return TYPE;
    }
}
