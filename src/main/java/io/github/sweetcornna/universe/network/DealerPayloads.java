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

/** 空间站飞船交易终端的数据包（ADR-0024）。 */
public final class DealerPayloads {
    private DealerPayloads() {
    }

    /**
     * 一艘挂着卖的船。外形和属性由客户端按种子用 {@code ShipDesigner} 自己算，价钱按服务端算好的。
     *
     * @param type  {@code ShipType} 的序号
     * @param grade {@code Grade} 的序号
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
     * 停在机库里、能卖掉的一艘自己的船。
     *
     * @param entity 实体编号
     * @param blocks 方块数
     * @param cargo  船上的箱子里还有东西（要先卸掉）
     */
    public record Owned(int entity, int type, int grade, int blocks, long price, boolean cargo) {
        static void write(FriendlyByteBuf buf, Owned owned) {
            buf.writeVarInt(owned.entity);
            buf.writeVarInt(owned.type);
            buf.writeVarInt(owned.grade);
            buf.writeVarInt(owned.blocks);
            buf.writeVarLong(owned.price);
            buf.writeBoolean(owned.cargo);
        }

        static Owned read(FriendlyByteBuf buf) {
            return new Owned(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarLong(), buf.readBoolean());
        }
    }

    /** 一艘挂着雇的护卫舰。 */
    public record FrigateOffer(int slot, FleetPayloads.FrigateInfo frigate, long price, boolean sold) {
        static void write(FriendlyByteBuf buf, FrigateOffer offer) {
            buf.writeVarInt(offer.slot);
            FleetPayloads.FrigateInfo.write(buf, offer.frigate);
            buf.writeVarLong(offer.price);
            buf.writeBoolean(offer.sold);
        }

        static FrigateOffer read(FriendlyByteBuf buf) {
            return new FrigateOffer(buf.readVarInt(), FleetPayloads.FrigateInfo.read(buf), buf.readVarLong(), buf.readBoolean());
        }
    }

    /**
     * 今天挂着卖的货船。外形由客户端按种子算。
     *
     * @param range 跃迁航程（光年）
     */
    public record FreighterOffer(long seed, int grade, long price, double range, boolean sold) {
        static void write(FriendlyByteBuf buf, FreighterOffer offer) {
            buf.writeLong(offer.seed);
            buf.writeVarInt(offer.grade);
            buf.writeVarLong(offer.price);
            buf.writeDouble(offer.range);
            buf.writeBoolean(offer.sold);
        }

        static FreighterOffer read(FriendlyByteBuf buf) {
            return new FreighterOffer(buf.readLong(), buf.readVarInt(), buf.readVarLong(), buf.readDouble(), buf.readBoolean());
        }
    }

    /**
     * 服务端 → 客户端：终端的内容。打开终端和每次买卖以后发一份。
     *
     * @param open      true 时打开界面；false 只刷新开着的界面
     * @param credits   玩家现在的信用点
     * @param padFree   起降台上空着，买的船放得下
     * @param ships     今天挂着卖的船
     * @param owned     机库里自己的船
     * @param frigates  今天挂着雇的护卫舰
     * @param freighter 今天挂着卖的货船
     * @param fleet     自己舰队里已经有几艘护卫舰；没有货船时 -1
     * @param notice    上一次买卖的结果
     */
    public record State(boolean open, long credits, boolean padFree, List<Offer> ships, List<Owned> owned, List<FrigateOffer> frigates,
            Optional<FreighterOffer> freighter, int fleet, Optional<Component> notice) implements CustomPacketPayload {
        public static final Type<State> TYPE = new Type<>(Universe.id("dealer_state"));
        public static final int MAX_ROWS = 16;
        public static final StreamCodec<RegistryFriendlyByteBuf, State> STREAM_CODEC = StreamCodec.of(State::write, State::read);

        private static void write(RegistryFriendlyByteBuf buf, State state) {
            buf.writeBoolean(state.open);
            buf.writeVarLong(state.credits);
            buf.writeBoolean(state.padFree);
            int ships = Math.min(MAX_ROWS, state.ships.size());
            buf.writeVarInt(ships);
            for (int i = 0; i < ships; i++) {
                Offer.write(buf, state.ships.get(i));
            }
            int owned = Math.min(MAX_ROWS, state.owned.size());
            buf.writeVarInt(owned);
            for (int i = 0; i < owned; i++) {
                Owned.write(buf, state.owned.get(i));
            }
            int frigates = Math.min(MAX_ROWS, state.frigates.size());
            buf.writeVarInt(frigates);
            for (int i = 0; i < frigates; i++) {
                FrigateOffer.write(buf, state.frigates.get(i));
            }
            buf.writeBoolean(state.freighter.isPresent());
            state.freighter.ifPresent(offer -> FreighterOffer.write(buf, offer));
            buf.writeVarInt(state.fleet + 1);
            ComponentSerialization.OPTIONAL_STREAM_CODEC.encode(buf, state.notice);
        }

        private static State read(RegistryFriendlyByteBuf buf) {
            boolean open = buf.readBoolean();
            long credits = buf.readVarLong();
            boolean padFree = buf.readBoolean();
            int ships = Math.min(MAX_ROWS, buf.readVarInt());
            List<Offer> offers = new ArrayList<>();
            for (int i = 0; i < ships; i++) {
                offers.add(Offer.read(buf));
            }
            int n = Math.min(MAX_ROWS, buf.readVarInt());
            List<Owned> owned = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                owned.add(Owned.read(buf));
            }
            int f = Math.min(MAX_ROWS, buf.readVarInt());
            List<FrigateOffer> frigates = new ArrayList<>();
            for (int i = 0; i < f; i++) {
                frigates.add(FrigateOffer.read(buf));
            }
            Optional<FreighterOffer> freighter = buf.readBoolean() ? Optional.of(FreighterOffer.read(buf)) : Optional.empty();
            int fleet = buf.readVarInt() - 1;
            return new State(open, credits, padFree, offers, owned, frigates, freighter, fleet, ComponentSerialization.OPTIONAL_STREAM_CODEC.decode(buf));
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** 客户端 → 服务端：买下第几艘。 */
    public record Buy(int slot) implements CustomPacketPayload {
        public static final Type<Buy> TYPE = new Type<>(Universe.id("dealer_buy"));
        public static final StreamCodec<ByteBuf, Buy> STREAM_CODEC = ByteBufCodecs.VAR_INT.map(Buy::new, Buy::slot);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** 客户端 → 服务端：雇下第几艘护卫舰。 */
    public record Hire(int slot) implements CustomPacketPayload {
        public static final Type<Hire> TYPE = new Type<>(Universe.id("dealer_hire"));
        public static final StreamCodec<ByteBuf, Hire> STREAM_CODEC = ByteBufCodecs.VAR_INT.map(Hire::new, Hire::slot);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** 客户端 → 服务端：买下今天挂着的货船。 */
    public record BuyFreighter(long seed) implements CustomPacketPayload {
        public static final Type<BuyFreighter> TYPE = new Type<>(Universe.id("dealer_buy_freighter"));
        public static final StreamCodec<ByteBuf, BuyFreighter> STREAM_CODEC = ByteBufCodecs.VAR_LONG.map(BuyFreighter::new, BuyFreighter::seed);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** 客户端 → 服务端：卖掉这艘船（实体编号）。 */
    public record Sell(int entity) implements CustomPacketPayload {
        public static final Type<Sell> TYPE = new Type<>(Universe.id("dealer_sell"));
        public static final StreamCodec<ByteBuf, Sell> STREAM_CODEC = ByteBufCodecs.VAR_INT.map(Sell::new, Sell::entity);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
