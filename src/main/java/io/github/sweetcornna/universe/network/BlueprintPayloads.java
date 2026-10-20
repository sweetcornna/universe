package io.github.sweetcornna.universe.network;

import java.util.List;

import io.github.sweetcornna.universe.Universe;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** 蓝图（ADR-0018）用到的网络包。 */
public final class BlueprintPayloads {
    private BlueprintPayloads() {
    }

    /** 服务端 → 客户端：打开保存蓝图的界面，带上选区的大小。 */
    public record OpenSave(int sizeX, int sizeY, int sizeZ) implements CustomPacketPayload {
        public static final Type<OpenSave> TYPE = new Type<>(Universe.id("blueprint_open_save"));
        public static final StreamCodec<ByteBuf, OpenSave> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, OpenSave::sizeX,
                ByteBufCodecs.VAR_INT, OpenSave::sizeY,
                ByteBufCodecs.VAR_INT, OpenSave::sizeZ,
                OpenSave::new);

        @Override
        public Type<OpenSave> type() {
            return TYPE;
        }
    }

    /** 客户端 → 服务端：把主手空白蓝图框住的选区存成蓝图。 */
    public record Save(String name) implements CustomPacketPayload {
        public static final int MAX_NAME = 32;
        public static final Type<Save> TYPE = new Type<>(Universe.id("blueprint_save"));
        public static final StreamCodec<ByteBuf, Save> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.stringUtf8(MAX_NAME * 4), Save::name,
                Save::new);

        @Override
        public Type<Save> type() {
            return TYPE;
        }
    }

    /** 客户端 → 服务端：主手的蓝图顺时针转 90°。 */
    public record Rotate() implements CustomPacketPayload {
        public static final Type<Rotate> TYPE = new Type<>(Universe.id("blueprint_rotate"));
        public static final StreamCodec<ByteBuf, Rotate> STREAM_CODEC = StreamCodec.unit(new Rotate());

        @Override
        public Type<Rotate> type() {
            return TYPE;
        }
    }

    /**
     * 客户端 → 服务端：拿着蓝图右键（射线比原版的交互距离长）。空白蓝图时 pos 是点的方块，画好的蓝图时是放的那一格
     * （客户端算好，可能对齐到上一次放的位置）。
     */
    public record Click(BlockPos pos) implements CustomPacketPayload {
        public static final Type<Click> TYPE = new Type<>(Universe.id("blueprint_click"));
        public static final StreamCodec<ByteBuf, Click> STREAM_CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, Click::pos,
                Click::new);

        @Override
        public Type<Click> type() {
            return TYPE;
        }
    }

    /** 客户端 → 服务端：要一张蓝图的内容来画预览。 */
    public record Request(int id) implements CustomPacketPayload {
        public static final Type<Request> TYPE = new Type<>(Universe.id("blueprint_request"));
        public static final StreamCodec<ByteBuf, Request> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Request::id,
                Request::new);

        @Override
        public Type<Request> type() {
            return TYPE;
        }
    }

    /**
     * 服务端 → 客户端：一张蓝图的方块，画预览用；找不到这张蓝图时 palette 为空。
     *
     * @param positions 每个方块在蓝图里的位置（{@code BlueprintGeometry.pack}）
     * @param states    每个方块的状态在 palette 里的下标
     */
    public record Content(int id, int sizeX, int sizeY, int sizeZ, List<BlockState> palette, List<Integer> positions, List<Integer> states)
            implements CustomPacketPayload {
        public static final Type<Content> TYPE = new Type<>(Universe.id("blueprint_content"));
        public static final StreamCodec<ByteBuf, Content> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Content::id,
                ByteBufCodecs.VAR_INT, Content::sizeX,
                ByteBufCodecs.VAR_INT, Content::sizeY,
                ByteBufCodecs.VAR_INT, Content::sizeZ,
                ByteBufCodecs.idMapper(Block.BLOCK_STATE_REGISTRY).apply(ByteBufCodecs.list()), Content::palette,
                ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()), Content::positions,
                ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()), Content::states,
                Content::new);

        @Override
        public Type<Content> type() {
            return TYPE;
        }
    }
}
