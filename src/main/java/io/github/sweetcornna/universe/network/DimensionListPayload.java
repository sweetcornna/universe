package io.github.sweetcornna.universe.network;

import java.util.List;

import io.github.sweetcornna.universe.Universe;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * 服务端运行时新增、移除了哪些维度，客户端据此更新维度清单（{@code /execute in} 等命令的补全）。
 *
 * <p>进入维度本身不需要客户端事先知道它：重生包里自带维度键和维度类型。
 * 登录时的维度清单由原版的登录包下发，已经包含当时加载着的运行时维度。
 */
public record DimensionListPayload(List<ResourceKey<Level>> added, List<ResourceKey<Level>> removed)
        implements CustomPacketPayload {
    public static final Type<DimensionListPayload> TYPE = new Type<>(Universe.id("dimension_list"));

    private static final StreamCodec<ByteBuf, List<ResourceKey<Level>>> KEYS =
            ResourceKey.streamCodec(Registries.DIMENSION).apply(ByteBufCodecs.list());
    public static final StreamCodec<ByteBuf, DimensionListPayload> STREAM_CODEC = StreamCodec.composite(
            KEYS, DimensionListPayload::added,
            KEYS, DimensionListPayload::removed,
            DimensionListPayload::new);

    public DimensionListPayload {
        added = List.copyOf(added);
        removed = List.copyOf(removed);
    }

    public static DimensionListPayload added(ResourceKey<Level> key) {
        return new DimensionListPayload(List.of(key), List.of());
    }

    public static DimensionListPayload removed(ResourceKey<Level> key) {
        return new DimensionListPayload(List.of(), List.of(key));
    }

    @Override
    public Type<DimensionListPayload> type() {
        return TYPE;
    }
}
