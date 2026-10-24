package io.github.sweetcornna.universe.network;

import io.github.sweetcornna.universe.Universe;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * 客户端 → 服务端：在编辑器里改一层的网格（ADR-0030 §四）。服务端重新检查整份设计，结果用 {@link DysonResultPayload} 回给这个玩家，
 * 改成了就把新的戴森球同步给所有人。
 *
 * @param action   做什么
 * @param layer    层的编号
 * @param revision 编辑器看到的网格编号（拆、上色要和服务端一样，不然下标对不上）
 * @param kind     元素的种类（{@code DysonLayer.Element} 的序号，拆、上色用）
 * @param indices  元素的下标（拆、上色用）
 * @param color    颜色，0 是默认色（上色用）
 * @param data     改网格时是 {@code DysonBlueprint.pack} 的整份设计，导入时是蓝图串的 UTF-8
 */
public record DysonLayerEditPayload(Action action, int layer, int revision, int kind, int[] indices, int color, byte[] data)
        implements CustomPacketPayload {
    public static final Type<DysonLayerEditPayload> TYPE = new Type<>(Universe.id("dyson_layer_edit"));
    public static final StreamCodec<FriendlyByteBuf, DysonLayerEditPayload> STREAM_CODEC = StreamCodec.of(DysonLayerEditPayload::write,
            DysonLayerEditPayload::read);
    public static final int MAX_DATA = 256 * 1024;
    public static final int MAX_INDICES = 2048;

    public enum Action {
        /** 换成一整份新的设计：没建的随便加删，建了的必须留着。 */
        REDESIGN,
        /** 导入蓝图：层一点都没建时才行。 */
        IMPORT,
        /** 拆掉建了的元素，结构点不退。 */
        DEMOLISH,
        PAINT
    }

    public static DysonLayerEditPayload redesign(int layer, byte[] design) {
        return new DysonLayerEditPayload(Action.REDESIGN, layer, 0, 0, new int[0], 0, design);
    }

    public static DysonLayerEditPayload importing(int layer, String blueprint) {
        return new DysonLayerEditPayload(Action.IMPORT, layer, 0, 0, new int[0], 0, blueprint.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    public static DysonLayerEditPayload demolish(int layer, int revision, int kind, int[] indices) {
        return new DysonLayerEditPayload(Action.DEMOLISH, layer, revision, kind, indices, 0, new byte[0]);
    }

    public static DysonLayerEditPayload paint(int layer, int revision, int kind, int[] indices, int color) {
        return new DysonLayerEditPayload(Action.PAINT, layer, revision, kind, indices, color, new byte[0]);
    }

    private static void write(FriendlyByteBuf buf, DysonLayerEditPayload payload) {
        buf.writeEnum(payload.action);
        buf.writeVarInt(payload.layer);
        buf.writeVarInt(payload.revision);
        buf.writeVarInt(payload.kind);
        buf.writeVarIntArray(payload.indices);
        buf.writeInt(payload.color);
        buf.writeByteArray(payload.data);
    }

    private static DysonLayerEditPayload read(FriendlyByteBuf buf) {
        return new DysonLayerEditPayload(buf.readEnum(Action.class), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                buf.readVarIntArray(MAX_INDICES), buf.readInt(), buf.readByteArray(MAX_DATA));
    }

    @Override
    public Type<DysonLayerEditPayload> type() {
        return TYPE;
    }
}
