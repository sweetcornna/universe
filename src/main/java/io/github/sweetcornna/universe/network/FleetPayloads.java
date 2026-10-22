package io.github.sweetcornna.universe.network;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.core.fleet.Area;
import io.github.sweetcornna.universe.core.fleet.Expedition;
import io.github.sweetcornna.universe.core.fleet.Frigate;
import io.github.sweetcornna.universe.core.fleet.FrigateTrait;
import io.github.sweetcornna.universe.core.fleet.FrigateType;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** 货船控制台、舰队终端和船舱天空的数据包（ADR-0024）。 */
public final class FleetPayloads {
    /** 一次最多发几行（护卫舰、跃迁目标）。 */
    public static final int MAX_ROWS = 64;

    private FleetPayloads() {
    }

    private static <T> void writeList(FriendlyByteBuf buf, List<T> list, java.util.function.BiConsumer<FriendlyByteBuf, T> writer) {
        int n = Math.min(MAX_ROWS, list.size());
        buf.writeVarInt(n);
        for (int i = 0; i < n; i++) {
            writer.accept(buf, list.get(i));
        }
    }

    private static <T> List<T> readList(FriendlyByteBuf buf, java.util.function.Function<FriendlyByteBuf, T> reader) {
        int n = Math.min(MAX_ROWS, buf.readVarInt());
        List<T> list = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            list.add(reader.apply(buf));
        }
        return list;
    }

    private static List<Integer> readInts(FriendlyByteBuf buf) {
        return readList(buf, FriendlyByteBuf::readVarInt);
    }

    private static void writeInts(FriendlyByteBuf buf, List<Integer> ints) {
        writeList(buf, ints, FriendlyByteBuf::writeVarInt);
    }

    /**
     * 服务端 → 客户端：这艘货船现在停在哪个恒星系的什么地方。进船舱时、货船跃迁以后发，船舱里照着它画太空。
     */
    public record Location(int freighter, int system, int x, int y, int z) implements CustomPacketPayload {
        public static final Type<Location> TYPE = new Type<>(Universe.id("freighter_location"));
        public static final StreamCodec<ByteBuf, Location> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Location::freighter,
                ByteBufCodecs.VAR_INT, Location::system,
                ByteBufCodecs.VAR_INT, Location::x,
                ByteBufCodecs.VAR_INT, Location::y,
                ByteBufCodecs.VAR_INT, Location::z,
                Location::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** 货船能跳到的一个恒星系。 */
    public record Target(int system, String nameZh, String nameEn, double distance, boolean visited) {
        static void write(FriendlyByteBuf buf, Target target) {
            buf.writeVarInt(target.system);
            buf.writeUtf(target.nameZh);
            buf.writeUtf(target.nameEn);
            buf.writeDouble(target.distance);
            buf.writeBoolean(target.visited);
        }

        static Target read(FriendlyByteBuf buf) {
            return new Target(buf.readVarInt(), buf.readUtf(), buf.readUtf(), buf.readDouble(), buf.readBoolean());
        }
    }

    /**
     * 服务端 → 客户端：货船控制台的内容。
     *
     * @param grade    货船的档次（{@code Grade} 的序号）
     * @param warpers  背包里的空间翘曲器
     * @param spooling 正在充能
     */
    public record Console(boolean open, int system, String nameZh, String nameEn, int grade, double range, int warpers, boolean spooling,
            List<Target> targets, Optional<Component> notice) implements CustomPacketPayload {
        public static final Type<Console> TYPE = new Type<>(Universe.id("freighter_console"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Console> STREAM_CODEC = StreamCodec.of(Console::write, Console::read);

        private static void write(RegistryFriendlyByteBuf buf, Console state) {
            buf.writeBoolean(state.open);
            buf.writeVarInt(state.system);
            buf.writeUtf(state.nameZh);
            buf.writeUtf(state.nameEn);
            buf.writeVarInt(state.grade);
            buf.writeDouble(state.range);
            buf.writeVarInt(state.warpers);
            buf.writeBoolean(state.spooling);
            writeList(buf, state.targets, Target::write);
            ComponentSerialization.OPTIONAL_STREAM_CODEC.encode(buf, state.notice);
        }

        private static Console read(RegistryFriendlyByteBuf buf) {
            return new Console(buf.readBoolean(), buf.readVarInt(), buf.readUtf(), buf.readUtf(), buf.readVarInt(), buf.readDouble(),
                    buf.readVarInt(), buf.readBoolean(), readList(buf, Target::read), ComponentSerialization.OPTIONAL_STREAM_CODEC.decode(buf));
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** 客户端 → 服务端：让货船跃迁到这个恒星系。 */
    public record Warp(int system) implements CustomPacketPayload {
        public static final Type<Warp> TYPE = new Type<>(Universe.id("freighter_warp"));
        public static final StreamCodec<ByteBuf, Warp> STREAM_CODEC = ByteBufCodecs.VAR_INT.map(Warp::new, Warp::system);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** 舰队里的一艘护卫舰，客户端照它还原成 {@link Frigate} 算属性。 */
    public record FrigateInfo(int id, String designation, int type, List<Integer> base, List<Integer> traits, int fuel, int expeditions,
            boolean damaged, boolean busy) {
        public static FrigateInfo of(Frigate frigate, boolean busy) {
            List<Integer> base = new ArrayList<>();
            for (int value : frigate.baseStats()) {
                base.add(value);
            }
            return new FrigateInfo(frigate.id(), frigate.designation(), frigate.type().ordinal(), base,
                    frigate.traits().stream().map(Enum::ordinal).toList(), frigate.baseFuel(), frigate.expeditions(), frigate.damaged(), busy);
        }

        public Frigate frigate() {
            int[] stats = new int[Area.values().length];
            for (int i = 0; i < stats.length && i < this.base.size(); i++) {
                stats[i] = this.base.get(i);
            }
            FrigateType[] types = FrigateType.values();
            FrigateTrait[] all = FrigateTrait.values();
            List<FrigateTrait> traits = this.traits.stream().filter(t -> t >= 0 && t < all.length).map(t -> all[t]).toList();
            return new Frigate(this.id, this.designation, types[Math.clamp(this.type, 0, types.length - 1)], stats, traits, this.fuel,
                    this.expeditions, this.damaged);
        }

        public static void write(FriendlyByteBuf buf, FrigateInfo info) {
            buf.writeVarInt(info.id);
            buf.writeUtf(info.designation);
            buf.writeVarInt(info.type);
            writeInts(buf, info.base);
            writeInts(buf, info.traits);
            buf.writeVarInt(info.fuel);
            buf.writeVarInt(info.expeditions);
            buf.writeBoolean(info.damaged);
            buf.writeBoolean(info.busy);
        }

        public static FrigateInfo read(FriendlyByteBuf buf) {
            return new FrigateInfo(buf.readVarInt(), buf.readUtf(), buf.readVarInt(), readInts(buf), readInts(buf), buf.readVarInt(), buf.readVarInt(),
                    buf.readBoolean(), buf.readBoolean());
        }
    }

    /** 今天的一个远征任务。 */
    public record OfferInfo(int kind, int difficulty, int minutes, boolean taken) {
        public Expedition.Offer offer() {
            Expedition.Kind[] kinds = Expedition.Kind.values();
            return new Expedition.Offer(kinds[Math.clamp(this.kind, 0, kinds.length - 1)], this.difficulty, this.minutes);
        }

        static void write(FriendlyByteBuf buf, OfferInfo info) {
            buf.writeVarInt(info.kind);
            buf.writeVarInt(info.difficulty);
            buf.writeVarInt(info.minutes);
            buf.writeBoolean(info.taken);
        }

        static OfferInfo read(FriendlyByteBuf buf) {
            return new OfferInfo(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readBoolean());
        }
    }

    /** 揭开了的一段遭遇。{@code frigate} 是受损或沉没的那艘的编号，没有时 -1。 */
    public record EventInfo(int area, boolean success, int frigate, int outcome) {
        static void write(FriendlyByteBuf buf, EventInfo info) {
            buf.writeVarInt(info.area);
            buf.writeBoolean(info.success);
            buf.writeVarInt(info.frigate + 1);
            buf.writeVarInt(info.outcome);
        }

        static EventInfo read(FriendlyByteBuf buf) {
            return new EventInfo(buf.readVarInt(), buf.readBoolean(), buf.readVarInt() - 1, buf.readVarInt());
        }
    }

    /**
     * 一支在外面的远征。
     *
     * @param events 已经揭开的那几段
     * @param total  一共几段
     */
    public record VoyageInfo(int kind, int difficulty, long start, long end, List<Integer> team, List<EventInfo> events, int total) {
        static void write(FriendlyByteBuf buf, VoyageInfo info) {
            buf.writeVarInt(info.kind);
            buf.writeVarInt(info.difficulty);
            buf.writeVarLong(info.start);
            buf.writeVarLong(info.end);
            writeInts(buf, info.team);
            writeList(buf, info.events, EventInfo::write);
            buf.writeVarInt(info.total);
        }

        static VoyageInfo read(FriendlyByteBuf buf) {
            return new VoyageInfo(buf.readVarInt(), buf.readVarInt(), buf.readVarLong(), buf.readVarLong(), readInts(buf),
                    readList(buf, EventInfo::read), buf.readVarInt());
        }
    }

    /**
     * 服务端 → 客户端：舰队终端的内容。
     *
     * @param fuel 背包里的发射燃料
     * @param now  主世界的游戏刻（远征的进度按它算）
     */
    public record Terminal(boolean open, long credits, int fuel, long now, List<FrigateInfo> frigates, List<OfferInfo> offers,
            List<VoyageInfo> voyages, Optional<Component> notice) implements CustomPacketPayload {
        public static final Type<Terminal> TYPE = new Type<>(Universe.id("fleet_terminal"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Terminal> STREAM_CODEC = StreamCodec.of(Terminal::write, Terminal::read);

        private static void write(RegistryFriendlyByteBuf buf, Terminal state) {
            buf.writeBoolean(state.open);
            buf.writeVarLong(state.credits);
            buf.writeVarInt(state.fuel);
            buf.writeVarLong(state.now);
            writeList(buf, state.frigates, FrigateInfo::write);
            writeList(buf, state.offers, OfferInfo::write);
            writeList(buf, state.voyages, VoyageInfo::write);
            ComponentSerialization.OPTIONAL_STREAM_CODEC.encode(buf, state.notice);
        }

        private static Terminal read(RegistryFriendlyByteBuf buf) {
            return new Terminal(buf.readBoolean(), buf.readVarLong(), buf.readVarInt(), buf.readVarLong(), readList(buf, FrigateInfo::read),
                    readList(buf, OfferInfo::read), readList(buf, VoyageInfo::read), ComponentSerialization.OPTIONAL_STREAM_CODEC.decode(buf));
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /**
     * 客户端 → 服务端：在舰队终端上做的事。
     *
     * @param value 出发：任务的序号；领取：第几支远征；修理、解散：护卫舰编号
     * @param team  出发时派的护卫舰
     */
    public record Action(int action, int value, List<Integer> team) implements CustomPacketPayload {
        public static final int LAUNCH = 0;
        public static final int CLAIM = 1;
        public static final int REPAIR = 2;
        public static final int DISMISS = 3;
        /** 只要一份新的内容（界面开着时隔几秒要一次，看远征走到哪了）。 */
        public static final int REFRESH = 4;
        public static final Type<Action> TYPE = new Type<>(Universe.id("fleet_action"));
        public static final StreamCodec<ByteBuf, Action> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Action::action,
                ByteBufCodecs.VAR_INT, Action::value,
                ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(8)), Action::team,
                Action::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
