package io.github.sweetcornna.universe.network;

import java.util.ArrayList;
import java.util.List;

import io.github.sweetcornna.universe.Universe;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * 服务端 → 客户端：统计面板戴森球那一页（ADR-0033 §四）。功率都是焦/刻。
 *
 * @param system     看的是哪个星系，-1 是还没有戴森球
 * @param systems    有戴森球的星系，面板上翻页用
 * @param generation 总功率
 * @param swarm      戴森云（太阳帆）的功率
 * @param sails      太阳帆有几张
 * @param siphon     黑雾巢穴偷走的
 * @param drawn      上一刻射线接收站取走的
 * @param layers     每一层
 */
public record StatsDysonPayload(int system, List<Integer> systems, double generation, double swarm, long sails, double siphon, double drawn,
        List<Layer> layers) implements CustomPacketPayload {
    public static final Type<StatsDysonPayload> TYPE = new Type<>(Universe.id("stats_dyson"));
    public static final StreamCodec<FriendlyByteBuf, StatsDysonPayload> STREAM_CODEC = StreamCodec.of(StatsDysonPayload::write,
            StatsDysonPayload::read);

    /** 一层球壳：建好的节点、框架、铺满的壳面和各自的总数，结构点、单元点和这一层的功率。半径是天文单位。 */
    public record Layer(int id, float radius, int nodes, int nodeTotal, int frames, int frameTotal, int shells, int shellTotal,
            long structurePoints, long cellPoints, double power) {
    }

    private static void write(FriendlyByteBuf buf, StatsDysonPayload payload) {
        buf.writeVarInt(payload.system + 1);
        buf.writeVarInt(payload.systems.size());
        payload.systems.forEach(buf::writeVarInt);
        buf.writeDouble(payload.generation);
        buf.writeDouble(payload.swarm);
        buf.writeVarLong(payload.sails);
        buf.writeDouble(payload.siphon);
        buf.writeDouble(payload.drawn);
        buf.writeVarInt(payload.layers.size());
        for (Layer layer : payload.layers) {
            buf.writeVarInt(layer.id);
            buf.writeFloat(layer.radius);
            buf.writeVarInt(layer.nodes);
            buf.writeVarInt(layer.nodeTotal);
            buf.writeVarInt(layer.frames);
            buf.writeVarInt(layer.frameTotal);
            buf.writeVarInt(layer.shells);
            buf.writeVarInt(layer.shellTotal);
            buf.writeVarLong(layer.structurePoints);
            buf.writeVarLong(layer.cellPoints);
            buf.writeDouble(layer.power);
        }
    }

    private static StatsDysonPayload read(FriendlyByteBuf buf) {
        int system = buf.readVarInt() - 1;
        int s = Math.min(256, buf.readVarInt());
        List<Integer> systems = new ArrayList<>(s);
        for (int i = 0; i < s; i++) {
            systems.add(buf.readVarInt());
        }
        double generation = buf.readDouble();
        double swarm = buf.readDouble();
        long sails = buf.readVarLong();
        double siphon = buf.readDouble();
        double drawn = buf.readDouble();
        int n = Math.min(64, buf.readVarInt());
        List<Layer> layers = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            layers.add(new Layer(buf.readVarInt(), buf.readFloat(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                    buf.readVarInt(), buf.readVarInt(), buf.readVarLong(), buf.readVarLong(), buf.readDouble()));
        }
        return new StatsDysonPayload(system, systems, generation, swarm, sails, siphon, drawn, layers);
    }

    @Override
    public Type<StatsDysonPayload> type() {
        return TYPE;
    }
}
