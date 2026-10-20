package io.github.sweetcornna.universe.blueprint;

import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.github.sweetcornna.universe.core.blueprint.BlueprintGeometry;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * 空白蓝图上框选区的两个角（ADR-0018），记在物品上，带着所在的维度。
 */
public record BlueprintSelection(Optional<BlockPos> first, Optional<BlockPos> second, String dimension) {
    public static final Codec<BlueprintSelection> CODEC = RecordCodecBuilder.create(i -> i.group(
            BlockPos.CODEC.optionalFieldOf("first").forGetter(BlueprintSelection::first),
            BlockPos.CODEC.optionalFieldOf("second").forGetter(BlueprintSelection::second),
            Codec.STRING.fieldOf("dimension").forGetter(BlueprintSelection::dimension)
    ).apply(i, BlueprintSelection::new));
    public static final StreamCodec<ByteBuf, BlueprintSelection> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.optional(BlockPos.STREAM_CODEC), BlueprintSelection::first,
            ByteBufCodecs.optional(BlockPos.STREAM_CODEC), BlueprintSelection::second,
            ByteBufCodecs.STRING_UTF8, BlueprintSelection::dimension,
            BlueprintSelection::new);

    public boolean complete() {
        return this.first.isPresent() && this.second.isPresent();
    }

    /** 选区每边几格；没框完时为空。 */
    public Optional<int[]> size() {
        if (!this.complete()) {
            return Optional.empty();
        }
        BlockPos a = this.first.get();
        BlockPos b = this.second.get();
        return Optional.of(BlueprintGeometry.size(a.getX(), a.getY(), a.getZ(), b.getX(), b.getY(), b.getZ()));
    }
}
