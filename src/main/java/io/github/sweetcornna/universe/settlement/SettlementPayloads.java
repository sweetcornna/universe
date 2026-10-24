package io.github.sweetcornna.universe.settlement;

import io.github.sweetcornna.universe.Universe;
import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** 聚落管理界面（ADR-0034 §二）的数据包。 */
public final class SettlementPayloads {
    private SettlementPayloads() {
    }

    /**
     * 服务端 → 客户端：界面的内容（聚落的全部数值、"我的聚落"表、玩家的信用点和声望），打包成一个 NBT（{@link Settlements#view}）。
     *
     * @param open 没开着界面时打开；否则只更新开着的界面
     */
    public record View(CompoundTag data, boolean open) implements CustomPacketPayload {
        public static final Type<View> TYPE = new Type<>(Universe.id("settlement_view"));
        public static final StreamCodec<ByteBuf, View> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.TRUSTED_COMPOUND_TAG, View::data,
                ByteBufCodecs.BOOL, View::open,
                View::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /**
     * 客户端 → 服务端：界面上的一个操作。能不能做由服务端判（总督本人、人在聚落里）。
     *
     * @param settlement 聚落编号（{@code Settlement#id}）；"我的聚落"表可以是空串
     * @param plot       第几块地基
     * @param value      建筑、产品的序号，决定的选项
     */
    public record Action(String settlement, int action, int plot, int value) implements CustomPacketPayload {
        public static final int REFRESH = 0;
        public static final int CLAIM = 1;
        public static final int START = 2;
        public static final int DELIVER = 3;
        public static final int OPEN = 4;
        public static final int UPGRADE = 5;
        public static final int CANCEL = 6;
        public static final int DECIDE = 7;
        public static final int PRODUCT = 8;
        public static final int WITHDRAW = 9;
        public static final int SELL = 10;
        public static final int TREASURY = 11;
        public static final int STOCK = 12;
        public static final int PAY_DEBT = 13;
        public static final int RESIGN = 14;
        /** 只要"我的聚落"表（按键打开）。 */
        public static final int LIST = 15;
        public static final Type<Action> TYPE = new Type<>(Universe.id("settlement_action"));
        public static final StreamCodec<ByteBuf, Action> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.stringUtf8(128), Action::settlement,
                ByteBufCodecs.VAR_INT, Action::action,
                ByteBufCodecs.VAR_INT, Action::plot,
                ByteBufCodecs.VAR_INT, Action::value,
                Action::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
