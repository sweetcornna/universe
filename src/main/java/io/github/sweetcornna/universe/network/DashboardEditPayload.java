package io.github.sweetcornna.universe.network;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.core.stats.Dashboard;
import io.github.sweetcornna.universe.stats.DashboardPins;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * 客户端 → 服务端：改仪表盘（ADR-0033 §四）：钉一张图、关掉、拖到别处、收起或展开、全部回到右边叠着。
 *
 * @param index 改第几张（钉新的时不用）
 * @param pin   钉新的时是那张图；拖动时只用它的位置
 */
public record DashboardEditPayload(Action action, int index, Dashboard.Pin pin) implements CustomPacketPayload {
    public static final Type<DashboardEditPayload> TYPE = new Type<>(Universe.id("dashboard_edit"));
    public static final StreamCodec<ByteBuf, DashboardEditPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.idMapper(i -> Action.values()[i], Action::ordinal), DashboardEditPayload::action,
            ByteBufCodecs.VAR_INT, DashboardEditPayload::index,
            DashboardPins.PIN_STREAM_CODEC, DashboardEditPayload::pin,
            DashboardEditPayload::new);

    @Override
    public Type<DashboardEditPayload> type() {
        return TYPE;
    }

    /** 按序号编码，新的只往后加。 */
    public enum Action {
        ADD,
        REMOVE,
        PLACE,
        TOGGLE,
        /** 全部回到右边叠着。 */
        RESET
    }
}
