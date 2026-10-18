package io.github.sweetcornna.universe.network;

import java.util.ArrayList;
import java.util.List;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.core.dyson.DysonGrid;
import io.github.sweetcornna.universe.core.dyson.DysonLayer;
import io.github.sweetcornna.universe.core.dyson.DysonSphere;
import io.github.sweetcornna.universe.core.dyson.SwarmOrbit;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * 服务端 → 客户端：玩家所在恒星系的戴森球（ADR-0016）。网格由模板算，只发进度；戴森云只发每条轨道的帆数。
 *
 * @param system        恒星系的序号
 * @param lightFactor   光照系数
 * @param maxRadius     半径上限（天文单位）
 * @param latitudeLimit 现在能建的纬度
 * @param drawn         接收站上一刻取走的电（焦/刻）
 * @param rockets       在飞的火箭
 */
public record DysonSyncPayload(int system, double lightFactor, double maxRadius, double latitudeLimit, double drawn, int rockets,
        List<Orbit> orbits, List<Layer> layers) implements CustomPacketPayload {
    public static final Type<DysonSyncPayload> TYPE = new Type<>(Universe.id("dyson_sync"));
    public static final StreamCodec<FriendlyByteBuf, DysonSyncPayload> STREAM_CODEC = StreamCodec.of(DysonSyncPayload::write,
            DysonSyncPayload::read);

    public record Orbit(int id, double radius, double inclination, double ascendingNode, long sails) {
    }

    public record Layer(int id, double radius, double inclination, double ascendingNode, int template, int density, int[] nodes, int[] frames,
            int[] cells) {
    }

    public static DysonSyncPayload of(int system, DysonSphere sphere, double latitudeLimit) {
        List<Orbit> orbits = new ArrayList<>();
        for (SwarmOrbit orbit : sphere.orbits()) {
            orbits.add(new Orbit(orbit.id(), orbit.radius(), orbit.inclination(), orbit.ascendingNode(), orbit.sails()));
        }
        List<Layer> layers = new ArrayList<>();
        for (DysonLayer layer : sphere.layers()) {
            layers.add(new Layer(layer.id(), layer.radius(), layer.inclination(), layer.ascendingNode(), layer.grid().template().ordinal(),
                    layer.grid().density(), layer.nodeProgress(), layer.frameProgress(), layer.cellProgress()));
        }
        return new DysonSyncPayload(system, sphere.lightFactor(), sphere.maxRadius(), latitudeLimit, sphere.lastDrawn(), sphere.rockets().size(),
                orbits, layers);
    }

    /** 在客户端拼回一个戴森球（帆都当作永不到期）。 */
    public DysonSphere toSphere() {
        DysonSphere sphere = new DysonSphere(this.lightFactor, this.maxRadius);
        for (Orbit orbit : this.orbits) {
            long count = Math.min(Integer.MAX_VALUE, orbit.sails);
            sphere.restoreOrbit(orbit.id, orbit.radius, orbit.inclination, orbit.ascendingNode,
                    count > 0 ? new long[] {Long.MAX_VALUE - SwarmOrbit.BUCKET} : new long[0], count > 0 ? new int[] {(int) count} : new int[0]);
        }
        for (Layer layer : this.layers) {
            sphere.restoreLayer(layer.id, layer.radius, layer.inclination, layer.ascendingNode, DysonGrid.Template.byOrdinal(layer.template),
                    layer.density, layer.nodes, layer.frames, layer.cells);
        }
        return sphere;
    }

    private static void write(FriendlyByteBuf buf, DysonSyncPayload payload) {
        buf.writeVarInt(payload.system);
        buf.writeDouble(payload.lightFactor);
        buf.writeDouble(payload.maxRadius);
        buf.writeDouble(payload.latitudeLimit);
        buf.writeDouble(payload.drawn);
        buf.writeVarInt(payload.rockets);
        buf.writeVarInt(payload.orbits.size());
        for (Orbit orbit : payload.orbits) {
            buf.writeVarInt(orbit.id);
            buf.writeDouble(orbit.radius);
            buf.writeFloat((float) orbit.inclination);
            buf.writeFloat((float) orbit.ascendingNode);
            buf.writeVarLong(orbit.sails);
        }
        buf.writeVarInt(payload.layers.size());
        for (Layer layer : payload.layers) {
            buf.writeVarInt(layer.id);
            buf.writeDouble(layer.radius);
            buf.writeFloat((float) layer.inclination);
            buf.writeFloat((float) layer.ascendingNode);
            buf.writeVarInt(layer.template);
            buf.writeVarInt(layer.density);
            buf.writeVarIntArray(layer.nodes);
            buf.writeVarIntArray(layer.frames);
            buf.writeVarIntArray(layer.cells);
        }
    }

    private static DysonSyncPayload read(FriendlyByteBuf buf) {
        int system = buf.readVarInt();
        double light = buf.readDouble();
        double maxRadius = buf.readDouble();
        double limit = buf.readDouble();
        double drawn = buf.readDouble();
        int rockets = buf.readVarInt();
        int orbitCount = Math.min(DysonSphere.MAX_ORBITS, buf.readVarInt());
        List<Orbit> orbits = new ArrayList<>(orbitCount);
        for (int i = 0; i < orbitCount; i++) {
            orbits.add(new Orbit(buf.readVarInt(), buf.readDouble(), buf.readFloat(), buf.readFloat(), buf.readVarLong()));
        }
        int layerCount = Math.min(DysonSphere.MAX_LAYERS, buf.readVarInt());
        List<Layer> layers = new ArrayList<>(layerCount);
        for (int i = 0; i < layerCount; i++) {
            layers.add(new Layer(buf.readVarInt(), buf.readDouble(), buf.readFloat(), buf.readFloat(), buf.readVarInt(), buf.readVarInt(),
                    buf.readVarIntArray(4096), buf.readVarIntArray(4096), buf.readVarIntArray(4096)));
        }
        return new DysonSyncPayload(system, light, maxRadius, limit, drawn, rockets, orbits, layers);
    }

    @Override
    public Type<DysonSyncPayload> type() {
        return TYPE;
    }
}
