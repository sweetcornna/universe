package io.github.sweetcornna.universe.poi;

import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.github.sweetcornna.universe.core.poi.PoiType;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * 信号增强器定位出来的航点（ADR-0014）：哪个维度、哪个位置、哪种兴趣点。HUD 上画出方向和距离，走到 20 格内消失。
 * 挂在玩家身上并同步给本人；没有航点时 {@code type} 为空字符串。
 */
public record Waypoint(ResourceKey<Level> dimension, BlockPos pos, String type) {
    public static final Waypoint NONE = new Waypoint(Level.OVERWORLD, BlockPos.ZERO, "");
    /** 走到多近算到达。 */
    public static final int ARRIVE = 20;

    public static final MapCodec<Waypoint> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            ResourceKey.codec(Registries.DIMENSION).fieldOf("dimension").forGetter(Waypoint::dimension),
            BlockPos.CODEC.fieldOf("pos").forGetter(Waypoint::pos),
            Codec.STRING.optionalFieldOf("type", "").forGetter(Waypoint::type)
    ).apply(i, Waypoint::new));

    public static final StreamCodec<ByteBuf, Waypoint> STREAM_CODEC = StreamCodec.composite(
            ResourceKey.streamCodec(Registries.DIMENSION), Waypoint::dimension,
            BlockPos.STREAM_CODEC, Waypoint::pos,
            ByteBufCodecs.STRING_UTF8, Waypoint::type,
            Waypoint::new);

    public boolean active() {
        return !this.type.isEmpty();
    }

    public Optional<PoiType> poiType() {
        if (!this.active()) {
            return Optional.empty();
        }
        try {
            return Optional.of(PoiType.byName(this.type));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
