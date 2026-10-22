package io.github.sweetcornna.universe.network;

import java.util.List;
import java.util.UUID;

import io.github.sweetcornna.universe.Universe;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** 载具召唤台的包（ADR-0026）。 */
public final class VehiclePayloads {
    private VehiclePayloads() {
    }

    /**
     * 名册上的一辆车。
     *
     * @param kind      载具种类的注册名
     * @param name      玩家起的名字，没起是空的
     * @param dimension 在哪个维度（维度的 id）
     * @param distance  离召唤台多远（格）；在别的维度是 -1
     */
    public record Vehicle(UUID id, String kind, String name, String dimension, int distance) {
        public static final StreamCodec<ByteBuf, Vehicle> STREAM_CODEC = StreamCodec.composite(
                UUIDUtil.STREAM_CODEC, Vehicle::id,
                ByteBufCodecs.STRING_UTF8, Vehicle::kind,
                ByteBufCodecs.STRING_UTF8, Vehicle::name,
                ByteBufCodecs.STRING_UTF8, Vehicle::dimension,
                ByteBufCodecs.VAR_INT, Vehicle::distance,
                Vehicle::new);
    }

    /** 服务端 → 客户端：右键了召唤台，列出你的车。 */
    public record Listing(BlockPos summoner, List<Vehicle> vehicles) implements CustomPacketPayload {
        public static final Type<Listing> TYPE = new Type<>(Universe.id("vehicle_listing"));
        public static final StreamCodec<ByteBuf, Listing> STREAM_CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, Listing::summoner,
                Vehicle.STREAM_CODEC.apply(ByteBufCodecs.list(64)), Listing::vehicles,
                Listing::new);

        @Override
        public Type<Listing> type() {
            return TYPE;
        }
    }

    /** 客户端 → 服务端：在召唤台的列表里选了一辆车。 */
    public record Summon(BlockPos summoner, UUID vehicle) implements CustomPacketPayload {
        public static final Type<Summon> TYPE = new Type<>(Universe.id("vehicle_summon"));
        public static final StreamCodec<ByteBuf, Summon> STREAM_CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, Summon::summoner,
                UUIDUtil.STREAM_CODEC, Summon::vehicle,
                Summon::new);

        @Override
        public Type<Summon> type() {
            return TYPE;
        }
    }
}
