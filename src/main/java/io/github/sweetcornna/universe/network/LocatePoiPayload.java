package io.github.sweetcornna.universe.network;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.core.poi.PoiType;
import io.github.sweetcornna.universe.poi.Pois;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** 客户端 → 服务端：在信号增强器界面里选了一种兴趣点（ADR-0014）。服务端检查距离、扣导航数据、定位。 */
public record LocatePoiPayload(BlockPos booster, PoiType poi) implements CustomPacketPayload {
    public static final Type<LocatePoiPayload> TYPE = new Type<>(Universe.id("locate_poi"));
    public static final StreamCodec<ByteBuf, LocatePoiPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, LocatePoiPayload::booster,
            ByteBufCodecs.idMapper(i -> PoiType.values()[Math.floorMod(i, PoiType.values().length)], PoiType::ordinal), LocatePoiPayload::poi,
            LocatePoiPayload::new);

    public static void handle(LocatePoiPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            Pois.locate(player, payload.booster(), payload.poi());
        }
    }

    @Override
    public Type<LocatePoiPayload> type() {
        return TYPE;
    }
}
