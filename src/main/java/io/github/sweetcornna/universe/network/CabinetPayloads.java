package io.github.sweetcornna.universe.network;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import io.github.sweetcornna.universe.Universe;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** 空间站多功能工具柜的数据包（ADR-0026）。 */
public final class CabinetPayloads {
    private CabinetPayloads() {
    }

    /**
     * 柜子里的一把工具。自带的模块由客户端按种子自己算（{@code MultitoolMarket.Offer#modules()}），价钱按服务端算好的。
     *
     * @param type  {@code MultitoolClass} 的序号
     * @param grade {@code Grade} 的序号
     * @param price 标价（含自带的模块）
     */
    public record Offer(int slot, long seed, int type, int grade, long price, boolean sold) {
        static void write(FriendlyByteBuf buf, Offer offer) {
            buf.writeVarInt(offer.slot);
            buf.writeLong(offer.seed);
            buf.writeVarInt(offer.type);
            buf.writeVarInt(offer.grade);
            buf.writeVarLong(offer.price);
            buf.writeBoolean(offer.sold);
        }

        static Offer read(FriendlyByteBuf buf) {
            return new Offer(buf.readVarInt(), buf.readLong(), buf.readVarInt(), buf.readVarInt(), buf.readVarLong(), buf.readBoolean());
        }
    }

    /**
     * 服务端 → 客户端：柜子的内容。打开柜子和每次买完发一份。
     *
     * @param open    true 时打开界面；false 只刷新开着的界面
     * @param credits 玩家现在的信用点
     * @param offers  今天挂着的工具
     * @param tradeIn 手上的旧工具折多少钱；手上没拿多功能工具时 -1
     * @param notice  上一次买的结果
     */
    public record State(boolean open, long credits, List<Offer> offers, long tradeIn, Optional<Component> notice) implements CustomPacketPayload {
        public static final Type<State> TYPE = new Type<>(Universe.id("cabinet_state"));
        public static final int MAX_ROWS = 8;
        public static final StreamCodec<RegistryFriendlyByteBuf, State> STREAM_CODEC = StreamCodec.of(State::write, State::read);

        private static void write(RegistryFriendlyByteBuf buf, State state) {
            buf.writeBoolean(state.open);
            buf.writeVarLong(state.credits);
            int offers = Math.min(MAX_ROWS, state.offers.size());
            buf.writeVarInt(offers);
            for (int i = 0; i < offers; i++) {
                Offer.write(buf, state.offers.get(i));
            }
            buf.writeVarLong(state.tradeIn + 1);
            ComponentSerialization.OPTIONAL_STREAM_CODEC.encode(buf, state.notice);
        }

        private static State read(RegistryFriendlyByteBuf buf) {
            boolean open = buf.readBoolean();
            long credits = buf.readVarLong();
            int count = Math.min(MAX_ROWS, buf.readVarInt());
            List<Offer> offers = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                offers.add(Offer.read(buf));
            }
            long tradeIn = buf.readVarLong() - 1;
            return new State(open, credits, offers, tradeIn, ComponentSerialization.OPTIONAL_STREAM_CODEC.decode(buf));
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** 客户端 → 服务端：买下第几把（手上的旧工具折价收走）。 */
    public record Buy(int slot) implements CustomPacketPayload {
        public static final Type<Buy> TYPE = new Type<>(Universe.id("cabinet_buy"));
        public static final StreamCodec<ByteBuf, Buy> STREAM_CODEC = ByteBufCodecs.VAR_INT.map(Buy::new, Buy::slot);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
