package io.github.sweetcornna.universe.network;

import io.github.sweetcornna.universe.Universe;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * 客户端 → 服务端：分析目镜分析完了一样东西（ADR-0012）。服务端重新检查距离和目标，记进发现日志。
 *
 * @param target 分析的是什么
 * @param entity 动物的实体 ID（{@link Target#ENTITY}）
 * @param pos    方块的位置（{@link Target#BLOCK}）
 * @param body   天上的天体，{@code BodyId} 的路径（{@link Target#BODY}）；恒星（{@link Target#STAR}）时不用
 */
public record AnalyzePayload(Target target, int entity, BlockPos pos, String body) implements CustomPacketPayload {
    public static final Type<AnalyzePayload> TYPE = new Type<>(Universe.id("analyze"));
    public static final StreamCodec<ByteBuf, AnalyzePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.idMapper(i -> Target.values()[Math.floorMod(i, Target.values().length)], Target::ordinal), AnalyzePayload::target,
            ByteBufCodecs.VAR_INT, AnalyzePayload::entity,
            BlockPos.STREAM_CODEC, AnalyzePayload::pos,
            ByteBufCodecs.stringUtf8(64), AnalyzePayload::body,
            AnalyzePayload::new);

    public enum Target {
        ENTITY,
        BLOCK,
        BODY,
        STAR
    }

    public static AnalyzePayload entity(int id) {
        return new AnalyzePayload(Target.ENTITY, id, BlockPos.ZERO, "");
    }

    public static AnalyzePayload block(BlockPos pos) {
        return new AnalyzePayload(Target.BLOCK, 0, pos, "");
    }

    public static AnalyzePayload body(String path) {
        return new AnalyzePayload(Target.BODY, 0, BlockPos.ZERO, path);
    }

    public static AnalyzePayload star() {
        return new AnalyzePayload(Target.STAR, 0, BlockPos.ZERO, "");
    }

    @Override
    public Type<AnalyzePayload> type() {
        return TYPE;
    }
}
