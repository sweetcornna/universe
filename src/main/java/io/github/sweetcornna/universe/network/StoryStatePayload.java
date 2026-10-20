package io.github.sweetcornna.universe.network;

import java.util.HashMap;
import java.util.Map;

import io.github.sweetcornna.universe.Universe;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * 服务端 → 客户端：整个世界到过的剧情节点和到的时候（ADR-0018）。登录时发一次，到了新节点时发给所有玩家；手册的
 * "日志"一章按它显示。
 */
public record StoryStatePayload(Map<String, Long> milestones) implements CustomPacketPayload {
    public static final Type<StoryStatePayload> TYPE = new Type<>(Universe.id("story_state"));
    public static final StreamCodec<ByteBuf, StoryStatePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.<ByteBuf, String, Long, Map<String, Long>>map(HashMap::new, ByteBufCodecs.STRING_UTF8, ByteBufCodecs.VAR_LONG),
            StoryStatePayload::milestones,
            StoryStatePayload::new);

    @Override
    public Type<StoryStatePayload> type() {
        return TYPE;
    }
}
