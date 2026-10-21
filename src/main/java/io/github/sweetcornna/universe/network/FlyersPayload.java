package io.github.sweetcornna.universe.network;

import java.util.ArrayList;
import java.util.List;

import io.github.sweetcornna.universe.Universe;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * 服务端 → 客户端：建造无人机和配送机器人的航段（ADR-0023）。服务端只在一架的去向变了时发一段，客户端按游戏时间从起点
 * 朝目标飞；目标是实体（玩家）时追着它飞。
 *
 * @param reset   先清掉客户端这个维度里所有的航段（进入维度时发的全量）
 * @param flyers  新的或者变了的航段
 * @param removed 落地了的
 */
public record FlyersPayload(boolean reset, List<Flyer> flyers, List<Integer> removed) implements CustomPacketPayload {
    public static final Type<FlyersPayload> TYPE = new Type<>(Universe.id("flyers"));
    public static final StreamCodec<FriendlyByteBuf, FlyersPayload> STREAM_CODEC = StreamCodec.of(FlyersPayload::write, FlyersPayload::read);

    public static final int DRONE = 0;
    public static final int BOT = 1;

    /**
     * 一段航线。
     *
     * @param kind   {@link #DRONE} 或 {@link #BOT}
     * @param start  出发的游戏刻
     * @param speed  格/刻；0 是停在起点
     * @param entity 追着飞的实体；-1 是飞向 (tx, ty, tz)
     * @param cargo  带着的物品（数字 ID），-1 是空着
     */
    public record Flyer(int id, int kind, double fx, double fy, double fz, long start, float speed, int entity, double tx, double ty, double tz,
            int cargo) {
    }

    private static void write(FriendlyByteBuf buf, FlyersPayload payload) {
        buf.writeBoolean(payload.reset);
        buf.writeVarInt(payload.flyers.size());
        for (Flyer flyer : payload.flyers) {
            buf.writeVarInt(flyer.id);
            buf.writeByte(flyer.kind);
            buf.writeDouble(flyer.fx);
            buf.writeDouble(flyer.fy);
            buf.writeDouble(flyer.fz);
            buf.writeVarLong(flyer.start);
            buf.writeFloat(flyer.speed);
            buf.writeVarInt(flyer.entity + 1);
            buf.writeDouble(flyer.tx);
            buf.writeDouble(flyer.ty);
            buf.writeDouble(flyer.tz);
            buf.writeVarInt(flyer.cargo + 1);
        }
        buf.writeVarInt(payload.removed.size());
        for (int id : payload.removed) {
            buf.writeVarInt(id);
        }
    }

    private static FlyersPayload read(FriendlyByteBuf buf) {
        boolean reset = buf.readBoolean();
        int n = buf.readVarInt();
        List<Flyer> flyers = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            flyers.add(new Flyer(buf.readVarInt(), buf.readByte(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readVarLong(),
                    buf.readFloat(), buf.readVarInt() - 1, buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readVarInt() - 1));
        }
        int m = buf.readVarInt();
        List<Integer> removed = new ArrayList<>(m);
        for (int i = 0; i < m; i++) {
            removed.add(buf.readVarInt());
        }
        return new FlyersPayload(reset, flyers, removed);
    }

    @Override
    public Type<FlyersPayload> type() {
        return TYPE;
    }
}
