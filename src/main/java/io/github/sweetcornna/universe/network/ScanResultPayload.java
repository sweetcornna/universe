package io.github.sweetcornna.universe.network;

import java.util.List;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.core.poi.LandmarkType;
import io.github.sweetcornna.universe.core.poi.PoiType;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * 服务端 → 客户端：多功能工具扫描到的矿脉和兴趣点（ADR-0014）、地标（ADR-0026），HUD 上标出来一段时间。
 *
 * @param focused 只扫了一种矿（ADR-0033 §八）：标记画大一些、留得久一些
 */
public record ScanResultPayload(List<Marker> markers, List<PoiMarker> pois, List<LandmarkMarker> landmarks, boolean focused)
        implements CustomPacketPayload {
    public static final Type<ScanResultPayload> TYPE = new Type<>(Universe.id("scan_result"));
    public static final int MAX_MARKERS = 128;
    public static final int MAX_POIS = 32;
    public static final int MAX_LANDMARKS = 16;

    public ScanResultPayload(List<Marker> markers, List<PoiMarker> pois, List<LandmarkMarker> landmarks) {
        this(markers, pois, landmarks, false);
    }

    /** 一条矿脉：中心位置、矿石方块、有几块。 */
    public record Marker(BlockPos pos, Identifier block, int count) {
        public static final StreamCodec<ByteBuf, Marker> STREAM_CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, Marker::pos,
                Identifier.STREAM_CODEC, Marker::block,
                ByteBufCodecs.VAR_INT, Marker::count,
                Marker::new);
    }

    /** 一个兴趣点：地面位置、种类、这个玩家去过没有。 */
    public record PoiMarker(BlockPos pos, PoiType type, boolean visited) {
        public static final StreamCodec<ByteBuf, PoiMarker> STREAM_CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, PoiMarker::pos,
                ByteBufCodecs.idMapper(i -> PoiType.values()[Math.floorMod(i, PoiType.values().length)], PoiType::ordinal), PoiMarker::type,
                ByteBufCodecs.BOOL, PoiMarker::visited,
                PoiMarker::new);
    }

    /** 一个地标：海底的位置、种类。 */
    public record LandmarkMarker(BlockPos pos, LandmarkType type) {
        public static final StreamCodec<ByteBuf, LandmarkMarker> STREAM_CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, LandmarkMarker::pos,
                ByteBufCodecs.idMapper(i -> LandmarkType.values()[Math.floorMod(i, LandmarkType.values().length)], LandmarkType::ordinal),
                LandmarkMarker::type,
                LandmarkMarker::new);
    }

    public static final StreamCodec<ByteBuf, ScanResultPayload> STREAM_CODEC = StreamCodec.composite(
            Marker.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_MARKERS)), ScanResultPayload::markers,
            PoiMarker.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_POIS)), ScanResultPayload::pois,
            LandmarkMarker.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_LANDMARKS)), ScanResultPayload::landmarks,
            ByteBufCodecs.BOOL, ScanResultPayload::focused,
            ScanResultPayload::new);

    @Override
    public Type<ScanResultPayload> type() {
        return TYPE;
    }
}
