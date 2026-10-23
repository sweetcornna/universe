package io.github.sweetcornna.universe.network;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.entity.ship.ShipEntity;
import io.github.sweetcornna.universe.entity.ship.ShipPanel;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** 飞船货舱和喷气背包火焰的包（ADR-0030 §六）。 */
public final class ShipCargoPayloads {
    private ShipCargoPayloads() {
    }

    /** 坐在船上按了货舱键：开界面。坐着就行，不用是驾驶员。 */
    public static void handleOpen(Open payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player && player.isAlive()
                && player.getVehicle() instanceof ShipEntity ship) {
            ShipPanel.openCargo(player, ship);
        }
    }

    /** 客户端 → 服务端：坐在飞着的船上按了货舱键。 */
    public record Open() implements CustomPacketPayload {
        public static final Open INSTANCE = new Open();
        public static final Type<Open> TYPE = new Type<>(Universe.id("open_ship_cargo"));
        public static final StreamCodec<ByteBuf, Open> STREAM_CODEC = StreamCodec.unit(INSTANCE);

        @Override
        public Type<Open> type() {
            return TYPE;
        }
    }

    /** 服务端 → 客户端：附近一个玩家的喷气背包点着了、灭了。 */
    public record Jetpack(int entity, boolean on) implements CustomPacketPayload {
        public static final Type<Jetpack> TYPE = new Type<>(Universe.id("jetpack_flame"));
        public static final StreamCodec<ByteBuf, Jetpack> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Jetpack::entity,
                ByteBufCodecs.BOOL, Jetpack::on,
                Jetpack::new);

        @Override
        public Type<Jetpack> type() {
            return TYPE;
        }
    }
}
