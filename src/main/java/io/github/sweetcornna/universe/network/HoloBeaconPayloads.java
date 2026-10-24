package io.github.sweetcornna.universe.network;

import java.util.List;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.beacon.MemoCodecs;
import io.github.sweetcornna.universe.core.beacon.BeaconStyle;
import io.github.sweetcornna.universe.core.beacon.BeaconVisibility;
import io.github.sweetcornna.universe.core.beacon.Memo;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/** 全息信标（ADR-0033 §六）的网络包。 */
public final class HoloBeaconPayloads {
    private static final StreamCodec<ByteBuf, BeaconVisibility> VISIBILITY = ByteBufCodecs.VAR_INT.map(BeaconVisibility::byOrdinal,
            BeaconVisibility::ordinal);

    private HoloBeaconPayloads() {
    }

    /** 服务端 → 客户端：打开这个信标的界面，界面读客户端方块实体里同步过来的设置。 */
    public record Open(BlockPos pos) implements CustomPacketPayload {
        public static final Type<Open> TYPE = new Type<>(Universe.id("holo_beacon_open"));
        public static final StreamCodec<ByteBuf, Open> STREAM_CODEC = StreamCodec.composite(BlockPos.STREAM_CODEC, Open::pos, Open::new);

        @Override
        public Type<Open> type() {
            return TYPE;
        }
    }

    /**
     * 客户端 → 服务端：改信标的设置。
     *
     * @param icon 图标物品的 ID，空字符串是没有图标
     */
    public record Update(BlockPos pos, String label, String icon, int color, BeaconVisibility visibility, Memo memo)
            implements CustomPacketPayload {
        public static final Type<Update> TYPE = new Type<>(Universe.id("holo_beacon_update"));
        public static final StreamCodec<ByteBuf, Update> STREAM_CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, Update::pos,
                ByteBufCodecs.stringUtf8(BeaconStyle.MAX_LABEL * 4), Update::label,
                ByteBufCodecs.stringUtf8(256), Update::icon,
                ByteBufCodecs.VAR_INT, Update::color,
                VISIBILITY, Update::visibility,
                MemoCodecs.STREAM_CODEC, Update::memo,
                Update::new);

        @Override
        public Type<Update> type() {
            return TYPE;
        }
    }

    /** 一个信标在 HUD 和星图上要用的东西。system 是所在的恒星系，不在任何恒星系里（主世界）是 -1。 */
    public record Entry(ResourceKey<Level> dimension, BlockPos pos, int system, String icon, int color, String label, BeaconVisibility visibility) {
        public static final StreamCodec<ByteBuf, Entry> STREAM_CODEC = StreamCodec.composite(
                ResourceKey.streamCodec(Registries.DIMENSION), Entry::dimension,
                BlockPos.STREAM_CODEC, Entry::pos,
                ByteBufCodecs.VAR_INT, Entry::system,
                ByteBufCodecs.stringUtf8(256), Entry::icon,
                ByteBufCodecs.VAR_INT, Entry::color,
                ByteBufCodecs.stringUtf8(BeaconStyle.MAX_LABEL * 4), Entry::label,
                VISIBILITY, Entry::visibility,
                Entry::new);
    }

    /** 服务端 → 客户端：世界里所有的信标，登录时和有信标变了的时候发。 */
    public record All(List<Entry> beacons) implements CustomPacketPayload {
        public static final int MAX = 1024;
        public static final Type<All> TYPE = new Type<>(Universe.id("holo_beacons"));
        public static final StreamCodec<ByteBuf, All> STREAM_CODEC = StreamCodec.composite(
                Entry.STREAM_CODEC.apply(ByteBufCodecs.list(MAX)), All::beacons,
                All::new);

        @Override
        public Type<All> type() {
            return TYPE;
        }
    }
}
