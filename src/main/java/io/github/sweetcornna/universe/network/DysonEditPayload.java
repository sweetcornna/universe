package io.github.sweetcornna.universe.network;

import io.github.sweetcornna.universe.Universe;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * 客户端 → 服务端：在戴森球编辑器里改玩家所在恒星系的戴森球（ADR-0016）。服务端会把数值夹到合法范围。
 *
 * @param action   做什么
 * @param id       轨道或层的编号（新建时不用）
 * @param template 网格模板的序号（只有层用）
 */
public record DysonEditPayload(Action action, int id, double radius, double inclination, double ascendingNode, int template, int density)
        implements CustomPacketPayload {
    public static final Type<DysonEditPayload> TYPE = new Type<>(Universe.id("dyson_edit"));
    public static final StreamCodec<FriendlyByteBuf, DysonEditPayload> STREAM_CODEC = StreamCodec.of(DysonEditPayload::write,
            DysonEditPayload::read);

    public enum Action {
        ADD_ORBIT,
        UPDATE_ORBIT,
        REMOVE_ORBIT,
        ADD_LAYER,
        UPDATE_LAYER,
        REMOVE_LAYER
    }

    private static void write(FriendlyByteBuf buf, DysonEditPayload payload) {
        buf.writeEnum(payload.action);
        buf.writeVarInt(payload.id);
        buf.writeDouble(payload.radius);
        buf.writeDouble(payload.inclination);
        buf.writeDouble(payload.ascendingNode);
        buf.writeVarInt(payload.template);
        buf.writeVarInt(payload.density);
    }

    private static DysonEditPayload read(FriendlyByteBuf buf) {
        return new DysonEditPayload(buf.readEnum(Action.class), buf.readVarInt(), buf.readDouble(), buf.readDouble(), buf.readDouble(),
                buf.readVarInt(), buf.readVarInt());
    }

    @Override
    public Type<DysonEditPayload> type() {
        return TYPE;
    }
}
