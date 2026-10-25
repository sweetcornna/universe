package io.github.sweetcornna.universe.network;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Function;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.core.logistics.ItemRoute;
import io.github.sweetcornna.universe.core.logistics.Station;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** 物流控制面板的数据包（ADR-0033 §三）：要一份账本的快照、远程改站和配送器的设置、管物品路线。 */
public final class ControlPanelPayloads {
    /** 一份快照里最多几座站、几台配送器、几艘在飞的运输船。 */
    public static final int MAX_STATIONS = 1024;
    public static final int MAX_DISTRIBUTORS = 1024;
    public static final int MAX_VESSELS = 1024;
    private static final int MAX_TEXT = 256;

    private ControlPanelPayloads() {
    }

    private static <T> void writeList(FriendlyByteBuf buf, List<T> list, int max, BiConsumer<FriendlyByteBuf, T> writer) {
        int n = Math.min(max, list.size());
        buf.writeVarInt(n);
        for (int i = 0; i < n; i++) {
            writer.accept(buf, list.get(i));
        }
    }

    private static <T> List<T> readList(FriendlyByteBuf buf, int max, Function<FriendlyByteBuf, T> reader) {
        int n = buf.readVarInt();
        if (n < 0 || n > max) {
            throw new IllegalArgumentException("列表太长：" + n);
        }
        List<T> list = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            list.add(reader.apply(buf));
        }
        return list;
    }

    /** 客户端 → 服务端：要一份快照（打开控制面板、星图时，开着的时候每两秒一次）。 */
    public record Request() implements CustomPacketPayload {
        public static final Type<Request> TYPE = new Type<>(Universe.id("control_panel_request"));
        public static final StreamCodec<ByteBuf, Request> STREAM_CODEC = StreamCodec.unit(new Request());

        @Override
        public Type<Request> type() {
            return TYPE;
        }
    }

    /** 一格：物品、数量、上限、本地和远程的供需（{@code Logic} 的序号）、在途运来的。 */
    public record SlotRow(String item, int count, int max, int local, int remote, int incoming) {
        static void write(FriendlyByteBuf buf, SlotRow row) {
            buf.writeUtf(row.item, MAX_TEXT);
            buf.writeVarInt(row.count);
            buf.writeVarInt(row.max);
            buf.writeVarInt(row.local);
            buf.writeVarInt(row.remote);
            buf.writeVarInt(row.incoming);
        }

        static SlotRow read(FriendlyByteBuf buf) {
            return new SlotRow(buf.readUtf(MAX_TEXT), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
        }
    }

    /**
     * 一座站。{@code kind} 是 {@code StationKind} 的序号，{@code body}、{@code system} 是它在星团里的位置；{@code editable} 是收快照的
     * 玩家能不能远程改它（自己的、队友的、没主人的，或者他是管理员）。
     */
    public record StationRow(UUID id, int kind, String dimension, BlockPos pos, String body, int system, String name, int number,
            int group, boolean isolated, int minLoad, List<UUID> partners, List<SlotRow> slots, int drones, int idleDrones, int vessels,
            int idleVessels, int warpers, int range, long energy, boolean editable) {
        static void write(FriendlyByteBuf buf, StationRow row) {
            buf.writeUUID(row.id);
            buf.writeVarInt(row.kind);
            buf.writeUtf(row.dimension, MAX_TEXT);
            buf.writeBlockPos(row.pos);
            buf.writeUtf(row.body, MAX_TEXT);
            buf.writeVarInt(row.system);
            buf.writeUtf(row.name, Station.MAX_NAME * 4);
            buf.writeVarInt(row.number);
            buf.writeVarInt(row.group);
            buf.writeBoolean(row.isolated);
            buf.writeVarInt(row.minLoad);
            writeList(buf, row.partners, Station.MAX_PARTNERS, (b, uuid) -> b.writeUUID(uuid));
            writeList(buf, row.slots, 8, SlotRow::write);
            buf.writeVarInt(row.drones);
            buf.writeVarInt(row.idleDrones);
            buf.writeVarInt(row.vessels);
            buf.writeVarInt(row.idleVessels);
            buf.writeVarInt(row.warpers);
            buf.writeVarInt(row.range);
            buf.writeVarLong(row.energy);
            buf.writeBoolean(row.editable);
        }

        static StationRow read(FriendlyByteBuf buf) {
            return new StationRow(buf.readUUID(), buf.readVarInt(), buf.readUtf(MAX_TEXT), buf.readBlockPos(), buf.readUtf(MAX_TEXT),
                    buf.readVarInt(), buf.readUtf(Station.MAX_NAME * 4), buf.readVarInt(), buf.readVarInt(), buf.readBoolean(), buf.readVarInt(),
                    readList(buf, Station.MAX_PARTNERS, b -> b.readUUID()), readList(buf, 8, SlotRow::read), buf.readVarInt(),
                    buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarLong(),
                    buf.readBoolean());
        }
    }

    /** 登记表里的一台配送器；{@code icarus}、{@code peer} 是两种模式的序号，{@code pending} 是改了还没生效（没加载）。 */
    public record DistributorRow(String dimension, BlockPos pos, String item, int icarus, int peer, int bots, boolean pending,
            boolean editable) {
        static void write(FriendlyByteBuf buf, DistributorRow row) {
            buf.writeUtf(row.dimension, MAX_TEXT);
            buf.writeBlockPos(row.pos);
            buf.writeUtf(row.item, MAX_TEXT);
            buf.writeVarInt(row.icarus);
            buf.writeVarInt(row.peer);
            buf.writeVarInt(row.bots);
            buf.writeBoolean(row.pending);
            buf.writeBoolean(row.editable);
        }

        static DistributorRow read(FriendlyByteBuf buf) {
            return new DistributorRow(buf.readUtf(MAX_TEXT), buf.readBlockPos(), buf.readUtf(MAX_TEXT), buf.readVarInt(), buf.readVarInt(),
                    buf.readVarInt(), buf.readBoolean(), buf.readBoolean());
        }
    }

    /** 一条物品路线和走着它的站；{@code editable} 是收快照的玩家能不能删它。 */
    public record RouteRow(UUID id, String item, boolean systems, String from, String to, List<UUID> stations, boolean editable) {
        static void write(FriendlyByteBuf buf, RouteRow row) {
            buf.writeUUID(row.id);
            buf.writeUtf(row.item, MAX_TEXT);
            buf.writeBoolean(row.systems);
            buf.writeUtf(row.from, MAX_TEXT);
            buf.writeUtf(row.to, MAX_TEXT);
            writeList(buf, row.stations, MAX_STATIONS, (b, uuid) -> b.writeUUID(uuid));
            buf.writeBoolean(row.editable);
        }

        static RouteRow read(FriendlyByteBuf buf) {
            return new RouteRow(buf.readUUID(), buf.readUtf(MAX_TEXT), buf.readBoolean(), buf.readUtf(MAX_TEXT), buf.readUtf(MAX_TEXT),
                    readList(buf, MAX_STATIONS, b -> b.readUUID()), buf.readBoolean());
        }

        public ItemRoute route() {
            return new ItemRoute(this.id, this.item, this.systems, this.from, this.to);
        }
    }

    /** 一艘在飞的运输船：从家（{@code from}）到对方的站，什么物品，出发、到达、回家的时刻（服务端的游戏刻）。 */
    public record VesselRow(String item, String fromBody, String toBody, int fromSystem, int toSystem, long departAt, long arriveAt,
            long returnAt, boolean delivering) {
        static void write(FriendlyByteBuf buf, VesselRow row) {
            buf.writeUtf(row.item, MAX_TEXT);
            buf.writeUtf(row.fromBody, MAX_TEXT);
            buf.writeUtf(row.toBody, MAX_TEXT);
            buf.writeVarInt(row.fromSystem);
            buf.writeVarInt(row.toSystem);
            buf.writeVarLong(row.departAt);
            buf.writeVarLong(row.arriveAt);
            buf.writeVarLong(row.returnAt);
            buf.writeBoolean(row.delivering);
        }

        static VesselRow read(FriendlyByteBuf buf) {
            return new VesselRow(buf.readUtf(MAX_TEXT), buf.readUtf(MAX_TEXT), buf.readUtf(MAX_TEXT), buf.readVarInt(), buf.readVarInt(),
                    buf.readVarLong(), buf.readVarLong(), buf.readVarLong(), buf.readBoolean());
        }
    }

    /** 服务端 → 客户端：全服的站、配送器、路线和在飞的运输船。{@code time} 是发的时候的游戏刻。 */
    public record Snapshot(long time, List<StationRow> stations, List<DistributorRow> distributors, List<RouteRow> routes,
            List<VesselRow> vessels) implements CustomPacketPayload {
        public static final Type<Snapshot> TYPE = new Type<>(Universe.id("control_panel_snapshot"));
        public static final StreamCodec<FriendlyByteBuf, Snapshot> STREAM_CODEC = StreamCodec.of(Snapshot::write, Snapshot::read);

        private static void write(FriendlyByteBuf buf, Snapshot snapshot) {
            buf.writeVarLong(snapshot.time);
            writeList(buf, snapshot.stations, MAX_STATIONS, StationRow::write);
            writeList(buf, snapshot.distributors, MAX_DISTRIBUTORS, DistributorRow::write);
            writeList(buf, snapshot.routes, ItemRoute.MAX, RouteRow::write);
            writeList(buf, snapshot.vessels, MAX_VESSELS, VesselRow::write);
        }

        private static Snapshot read(FriendlyByteBuf buf) {
            return new Snapshot(buf.readVarLong(), readList(buf, MAX_STATIONS, StationRow::read),
                    readList(buf, MAX_DISTRIBUTORS, DistributorRow::read), readList(buf, ItemRoute.MAX, RouteRow::read),
                    readList(buf, MAX_VESSELS, VesselRow::read));
        }

        @Override
        public Type<Snapshot> type() {
            return TYPE;
        }
    }

    /**
     * 客户端 → 服务端：远程改一座站。{@code slot}、{@code value}、{@code text}、{@code other} 按动作用：
     * 改名用 text；分组、最小载货量、航程用 value；伙伴用 other；格子的物品用 slot 和 text（物品编号，空串是清掉）；
     * 格子的按钮用 slot 和 value（{@code StationBlockEntity} 的按钮编号）。
     */
    public record StationEdit(UUID station, int action, int slot, int value, String text, UUID other) implements CustomPacketPayload {
        public static final int RENAME = 0;
        public static final int GROUP = 1;
        public static final int ISOLATE = 2;
        public static final int MIN_LOAD = 3;
        public static final int PARTNER_ADD = 4;
        public static final int PARTNER_REMOVE = 5;
        public static final int RANGE = 6;
        public static final int SLOT_ITEM = 7;
        public static final int SLOT_BUTTON = 8;
        public static final UUID NONE = new UUID(0, 0);

        public static final Type<StationEdit> TYPE = new Type<>(Universe.id("control_panel_station"));
        public static final StreamCodec<ByteBuf, StationEdit> STREAM_CODEC = StreamCodec.composite(
                UUIDUtil.STREAM_CODEC, StationEdit::station,
                ByteBufCodecs.VAR_INT, StationEdit::action,
                ByteBufCodecs.VAR_INT, StationEdit::slot,
                ByteBufCodecs.VAR_INT, StationEdit::value,
                ByteBufCodecs.stringUtf8(MAX_TEXT), StationEdit::text,
                UUIDUtil.STREAM_CODEC, StationEdit::other,
                StationEdit::new);

        public static StationEdit of(UUID station, int action, int value) {
            return new StationEdit(station, action, 0, value, "", NONE);
        }

        @Override
        public Type<StationEdit> type() {
            return TYPE;
        }
    }

    /** 客户端 → 服务端：远程换配送器的模式（{@code DistributorBlockEntity} 的两个模式按钮）。 */
    public record DistributorEdit(String dimension, BlockPos pos, int button) implements CustomPacketPayload {
        public static final Type<DistributorEdit> TYPE = new Type<>(Universe.id("control_panel_distributor"));
        public static final StreamCodec<ByteBuf, DistributorEdit> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.stringUtf8(MAX_TEXT), DistributorEdit::dimension,
                BlockPos.STREAM_CODEC, DistributorEdit::pos,
                ByteBufCodecs.VAR_INT, DistributorEdit::button,
                DistributorEdit::new);

        @Override
        public Type<DistributorEdit> type() {
            return TYPE;
        }
    }

    /** 客户端 → 服务端：新建（{@code add}）或删掉一条物品路线；删的时候只看 {@code id}。 */
    public record RouteEdit(boolean add, UUID id, String item, boolean systems, String from, String to) implements CustomPacketPayload {
        public static final Type<RouteEdit> TYPE = new Type<>(Universe.id("control_panel_route"));
        public static final StreamCodec<ByteBuf, RouteEdit> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL, RouteEdit::add,
                UUIDUtil.STREAM_CODEC, RouteEdit::id,
                ByteBufCodecs.stringUtf8(MAX_TEXT), RouteEdit::item,
                ByteBufCodecs.BOOL, RouteEdit::systems,
                ByteBufCodecs.stringUtf8(MAX_TEXT), RouteEdit::from,
                ByteBufCodecs.stringUtf8(MAX_TEXT), RouteEdit::to,
                RouteEdit::new);

        @Override
        public Type<RouteEdit> type() {
            return TYPE;
        }
    }
}
