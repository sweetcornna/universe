package io.github.sweetcornna.universe.logistics;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.core.galaxy.BodyId;
import io.github.sweetcornna.universe.core.galaxy.Galaxy;
import io.github.sweetcornna.universe.core.logistics.ItemRoute;
import io.github.sweetcornna.universe.core.logistics.LogisticsNetwork;
import io.github.sweetcornna.universe.core.logistics.Ownership;
import io.github.sweetcornna.universe.core.logistics.Station;
import io.github.sweetcornna.universe.core.logistics.StationSlot;
import io.github.sweetcornna.universe.core.logistics.Trip;
import io.github.sweetcornna.universe.core.logistics.Vehicle;
import io.github.sweetcornna.universe.network.ControlPanelPayloads;
import io.github.sweetcornna.universe.world.planet.Planets;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.item.Items;
import net.minecraft.world.scores.PlayerTeam;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 物流控制面板的服务端（ADR-0033 §三）：给玩家发全服的站、配送器、路线和在飞的运输船，处理远程改设置。
 *
 * <p>远程改的是账本，不用加载站所在的区块；格子的按钮和站前的界面走同一个方法（{@link StationBlockEntity#applySetting}），
 * 效果一样。配送器加载着就直接改，没加载就记在登记表里等它加载。
 *
 * <p>谁都能看，远程改只给主人（放下站、配送器的玩家，建路线的玩家）、主人的队友和有 2 级权限的管理员；0.14 的档里没记主人的
 * 谁都能改。旁观者不能改。快照里每一行带着收的人能不能改，界面照着把按钮变灰。
 *
 * <p>快照在这一刻末尾发：同一刻里要了几次、改了几次，一个玩家只发一份；改了设置但什么也没变就不发。
 */
public final class ControlPanel {
    /** 同一个玩家两次要快照至少隔几刻（改了设置以后的那一份不算）。 */
    private static final int REQUEST_COOLDOWN = 10;
    private static final Map<ServerPlayer, Long> LAST_SENT = new WeakHashMap<>();
    /** 这一刻末尾要发快照的玩家。 */
    private static final Set<UUID> PENDING = new LinkedHashSet<>();

    private ControlPanel() {
    }

    public static void registerEvents(IEventBus gameBus) {
        gameBus.addListener(ServerTickEvent.Post.class, event -> flush(event.getServer()));
        gameBus.addListener(ServerStoppedEvent.class, event -> PENDING.clear());
    }

    // ---- 权限

    /** 能不能远程改主人是 {@code owner} 的东西（{@link Ownership}）。 */
    public static boolean mayEdit(ServerPlayer player, Optional<UUID> owner) {
        return Ownership.mayEdit(owner.orElse(null), player.getUUID(), player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER),
                id -> teammates(player, id));
    }

    /** 和建造无人机认工地一样看队伍：玩家有队伍，主人也在这个队伍里。主人不在线时按服务器记得的名字查记分板。 */
    private static boolean teammates(ServerPlayer player, UUID owner) {
        PlayerTeam team = player.getTeam();
        if (team == null) {
            return false;
        }
        MinecraftServer server = player.level().getServer();
        ServerPlayer online = server.getPlayerList().getPlayer(owner);
        if (online != null) {
            return online.getTeam() == team;
        }
        return server.services().nameToIdCache().get(owner).map(known -> server.getScoreboard().getPlayersTeam(known.name()) == team)
                .orElse(false);
    }

    /** 快照里这一行是不是收的人的（界面上的"别的玩家的"只看这个，旁观者另外拦）；没有收的人（GameTest）时只有没主人的算。 */
    private static boolean editable(@Nullable ServerPlayer viewer, Optional<UUID> owner) {
        return viewer == null ? owner.isEmpty() : mayEdit(viewer, owner);
    }

    /** 能不能删这条路线（{@link Ownership#mayDeleteRoute}）：建的人，或者 {@code on}（走这条路线的站）里有一座有主人的站是他能改的。 */
    private static boolean mayDeleteRoute(ServerPlayer player, ItemRoute route, List<Station> on) {
        return Ownership.mayDeleteRoute(route.creator(), on.stream().map(Station::owner).toList(), player.getUUID(),
                player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER), id -> teammates(player, id));
    }

    // ---- 快照

    public static ControlPanelPayloads.Snapshot snapshot(MinecraftServer server) {
        return snapshot(server, null);
    }

    /** 给 {@code viewer} 看的快照：每一行带着他能不能改。 */
    public static ControlPanelPayloads.Snapshot snapshot(MinecraftServer server, @Nullable ServerPlayer viewer) {
        LogisticsNetwork network = Logistics.network(server);
        List<ControlPanelPayloads.StationRow> stations = new ArrayList<>();
        for (Station station : network.stations()) {
            if (stations.size() >= ControlPanelPayloads.MAX_STATIONS) {
                break;
            }
            stations.add(row(station, editable(viewer, station.owner())));
        }
        List<ControlPanelPayloads.RouteRow> routes = new ArrayList<>();
        for (ItemRoute route : network.routes()) {
            List<Station> stationsOn = network.stationsOn(route);
            List<UUID> on = new ArrayList<>();
            stationsOn.forEach(station -> on.add(station.id()));
            boolean editable = viewer == null ? route.creator() == null : mayDeleteRoute(viewer, route, stationsOn);
            routes.add(new ControlPanelPayloads.RouteRow(route.id(), route.item(), route.systems(), route.from(), route.to(), on, editable));
        }
        List<ControlPanelPayloads.VesselRow> vessels = new ArrayList<>();
        for (Trip trip : network.trips()) {
            Station home = network.get(trip.home());
            Station other = network.get(trip.other());
            if (trip.vehicle() != Vehicle.VESSEL || home == null || other == null || vessels.size() >= ControlPanelPayloads.MAX_VESSELS) {
                continue;
            }
            vessels.add(new ControlPanelPayloads.VesselRow(trip.item(), home.location().body(), other.location().body(), home.location().system(),
                    other.location().system(), trip.departAt(), trip.arriveAt(), trip.returnAt(), trip.delivering()));
        }
        return new ControlPanelPayloads.Snapshot(server.overworld().getGameTime(), stations, distributors(server, viewer), routes, vessels);
    }

    private static ControlPanelPayloads.StationRow row(Station station, boolean editable) {
        List<ControlPanelPayloads.SlotRow> slots = new ArrayList<>();
        for (StationSlot slot : station.slots()) {
            slots.add(new ControlPanelPayloads.SlotRow(slot.item(), slot.count(), slot.max(), slot.local().ordinal(), slot.remote().ordinal(),
                    slot.incoming()));
        }
        return new ControlPanelPayloads.StationRow(station.id(), station.kind().ordinal(), station.dimension(),
                new BlockPos(station.x(), station.y(), station.z()), station.location().body(), station.location().system(), station.name(),
                station.number(), station.group(), station.isolated(), station.minLoad(), List.copyOf(station.partners()), slots,
                station.drones(), station.idleDrones(), station.vessels(), station.idleVessels(), station.warpers(), station.droneRange(),
                station.energy(), editable);
    }

    /** 登记表里的配送器；加载着的顺手对一下，方块已经没了的划掉。 */
    private static List<ControlPanelPayloads.DistributorRow> distributors(MinecraftServer server, @Nullable ServerPlayer viewer) {
        DistributorRegistry registry = DistributorRegistry.get(server);
        for (DistributorRegistry.Entry entry : List.copyOf(registry.entries())) {
            ServerLevel level = level(server, entry.dimension());
            if (level == null || !level.isLoaded(entry.pos())) {
                continue;
            }
            if (level.getBlockEntity(entry.pos()) instanceof DistributorBlockEntity distributor) {
                registry.record(level, distributor);
            } else {
                registry.removeKey(entry.key());
            }
        }
        List<ControlPanelPayloads.DistributorRow> rows = new ArrayList<>();
        for (DistributorRegistry.Entry entry : registry.entries()) {
            if (rows.size() >= ControlPanelPayloads.MAX_DISTRIBUTORS) {
                break;
            }
            rows.add(new ControlPanelPayloads.DistributorRow(entry.dimension(), entry.pos(), entry.item(), entry.icarus().ordinal(),
                    entry.peer().ordinal(), entry.bots(), entry.pending(), editable(viewer, entry.owner())));
        }
        return rows;
    }

    private static @Nullable ServerLevel level(MinecraftServer server, String dimension) {
        Identifier id = Identifier.tryParse(dimension);
        return id == null ? null : server.getLevel(ResourceKey.create(Registries.DIMENSION, id));
    }

    /** 这一刻末尾给这个玩家发一份快照。 */
    private static void queue(ServerPlayer player) {
        PENDING.add(player.getUUID());
    }

    /** 这一刻末尾要不要给他发快照（GameTest 用）。 */
    public static boolean pending(ServerPlayer player) {
        return PENDING.contains(player.getUUID());
    }

    /** 每刻末尾：要发的都发掉。下线了的不发；重生过的发给现在的那个玩家对象。 */
    private static void flush(MinecraftServer server) {
        if (PENDING.isEmpty()) {
            return;
        }
        List<UUID> players = List.copyOf(PENDING);
        PENDING.clear();
        for (UUID id : players) {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player != null) {
                LAST_SENT.put(player, player.level().getGameTime());
                PacketDistributor.sendToPlayer(player, snapshot(server, player));
            }
        }
    }

    // ---- 包

    public static void handleRequest(ControlPanelPayloads.Request payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            Long last = LAST_SENT.get(player);
            if (last == null || Math.abs(player.level().getGameTime() - last) >= REQUEST_COOLDOWN) {
                queue(player);
            }
        }
    }

    public static void handleStation(ControlPanelPayloads.StationEdit payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player && editStation(player, payload)) {
            queue(player);
        }
    }

    public static void handleDistributor(ControlPanelPayloads.DistributorEdit payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player && editDistributor(player, payload)) {
            queue(player);
        }
    }

    public static void handleRoute(ControlPanelPayloads.RouteEdit payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player && editRoute(player, payload)) {
            queue(player);
        }
    }

    // ---- 玩家远程改（先看权限；GameTest 也直接调这几个）

    /** 玩家远程改一座站。加伙伴时对方的站也得能改：伙伴两边都算，加上就绕过了对方的指定配对。 */
    public static boolean editStation(ServerPlayer player, ControlPanelPayloads.StationEdit edit) {
        MinecraftServer server = player.level().getServer();
        LogisticsNetwork network = Logistics.network(server);
        Station station = network.get(edit.station());
        if (player.isSpectator() || station == null || !mayEdit(player, station.owner())) {
            return false;
        }
        if (edit.action() == ControlPanelPayloads.StationEdit.PARTNER_ADD) {
            Station other = network.get(edit.other());
            if (other == null || !mayEdit(player, other.owner())) {
                return false;
            }
        }
        return editStation(server, edit);
    }

    /** 玩家远程换配送器的模式：主人看加载着的配送器，没加载就看登记表。 */
    public static boolean editDistributor(ServerPlayer player, ControlPanelPayloads.DistributorEdit edit) {
        MinecraftServer server = player.level().getServer();
        if (player.isSpectator() || !mayEdit(player, distributorOwner(server, edit.dimension(), edit.pos()))) {
            return false;
        }
        return editDistributor(server, edit);
    }

    private static Optional<UUID> distributorOwner(MinecraftServer server, String dimension, BlockPos pos) {
        ServerLevel level = level(server, dimension);
        if (level != null && level.isLoaded(pos) && level.getBlockEntity(pos) instanceof DistributorBlockEntity distributor) {
            return distributor.owner();
        }
        DistributorRegistry.Entry entry = DistributorRegistry.get(server).get(new DistributorRegistry.Key(dimension, pos));
        return entry == null ? Optional.empty() : entry.owner();
    }

    /** 玩家新建路线（记下是他建的），或者删掉一条他能删的路线（{@link #mayDeleteRoute}）。 */
    public static boolean editRoute(ServerPlayer player, ControlPanelPayloads.RouteEdit edit) {
        MinecraftServer server = player.level().getServer();
        if (player.isSpectator()) {
            return false;
        }
        if (!edit.add()) {
            LogisticsNetwork network = Logistics.network(server);
            ItemRoute route = network.route(edit.id());
            if (route == null || !mayDeleteRoute(player, route, network.stationsOn(route))) {
                return false;
            }
        }
        return editRoute(server, edit, player.getUUID());
    }

    // ---- 改设置（不看权限）

    /** 远程改一座站，返回真的改了没有（改成原样的不算）。 */
    public static boolean editStation(MinecraftServer server, ControlPanelPayloads.StationEdit edit) {
        LogisticsNetwork network = Logistics.network(server);
        Station station = network.get(edit.station());
        if (station == null) {
            return false;
        }
        boolean changed = switch (edit.action()) {
            case ControlPanelPayloads.StationEdit.RENAME -> {
                String before = station.name();
                station.setName(edit.text());
                yield !station.name().equals(before);
            }
            case ControlPanelPayloads.StationEdit.GROUP -> {
                int before = station.group();
                station.setGroup(edit.value());
                yield station.group() != before;
            }
            case ControlPanelPayloads.StationEdit.ISOLATE -> {
                boolean before = station.isolated();
                station.setIsolated(edit.value() != 0);
                yield station.isolated() != before;
            }
            case ControlPanelPayloads.StationEdit.MIN_LOAD -> {
                if (!station.kind().remote()) {
                    yield false;
                }
                int before = station.minLoad();
                station.setMinLoad(edit.value());
                yield station.minLoad() != before;
            }
            case ControlPanelPayloads.StationEdit.PARTNER_ADD -> {
                Station other = network.get(edit.other());
                yield other != null && other != station && station.addPartner(other.id());
            }
            case ControlPanelPayloads.StationEdit.PARTNER_REMOVE -> {
                // 哪一方加的都算伙伴，去掉时两边都去掉
                boolean removed = station.removePartner(edit.other());
                Station other = network.get(edit.other());
                yield (other != null && other.removePartner(station.id())) || removed;
            }
            case ControlPanelPayloads.StationEdit.RANGE -> {
                int before = station.droneRange();
                station.setDroneRange(edit.value());
                yield station.droneRange() != before;
            }
            case ControlPanelPayloads.StationEdit.SLOT_ITEM -> {
                String item = edit.text().isEmpty() ? "" : validItem(edit.text());
                yield item != null && station.assign(edit.slot(), item);
            }
            case ControlPanelPayloads.StationEdit.SLOT_BUTTON -> StationBlockEntity.applySetting(station, edit.slot(), edit.value());
            default -> false;
        };
        if (changed) {
            LogisticsData.get(server).setDirty();
        }
        return changed;
    }

    /** 是注册过的物品（不是空气）时返回规范的编号。 */
    private static @Nullable String validItem(String text) {
        Identifier id = Identifier.tryParse(text);
        if (id == null || !BuiltInRegistries.ITEM.containsKey(id) || BuiltInRegistries.ITEM.getValue(id) == Items.AIR) {
            return null;
        }
        return id.toString();
    }

    /** 远程换配送器的模式：加载着的直接改，没加载的记在登记表里。 */
    public static boolean editDistributor(MinecraftServer server, ControlPanelPayloads.DistributorEdit edit) {
        if (edit.button() != DistributorBlockEntity.CYCLE_ICARUS && edit.button() != DistributorBlockEntity.CYCLE_PEER) {
            return false;
        }
        DistributorRegistry registry = DistributorRegistry.get(server);
        DistributorRegistry.Key key = new DistributorRegistry.Key(edit.dimension(), edit.pos());
        ServerLevel level = level(server, edit.dimension());
        if (level != null && level.isLoaded(edit.pos())) {
            if (level.getBlockEntity(edit.pos()) instanceof DistributorBlockEntity distributor) {
                return distributor.remoteButton(edit.button());
            }
            registry.removeKey(key);
            return false;
        }
        return registry.cyclePending(key, edit.button());
    }

    /** 新建或删掉一条物品路线。 */
    public static boolean editRoute(MinecraftServer server, ControlPanelPayloads.RouteEdit edit) {
        return editRoute(server, edit, null);
    }

    /** 同上，新建的路线记下是 {@code creator} 建的。 */
    public static boolean editRoute(MinecraftServer server, ControlPanelPayloads.RouteEdit edit, @Nullable UUID creator) {
        LogisticsNetwork network = Logistics.network(server);
        boolean changed;
        if (edit.add()) {
            String item = validItem(edit.item());
            changed = item != null && validEnd(server, edit.systems(), edit.from()) && validEnd(server, edit.systems(), edit.to())
                    && network.addRoute(new ItemRoute(UUID.randomUUID(), item, edit.systems(), edit.from(), edit.to(), creator));
        } else {
            changed = network.removeRoute(edit.id());
        }
        if (changed) {
            LogisticsData.get(server).setDirty();
        }
        return changed;
    }

    /** 路线的一头：星系路线是星团里有的星系序号，星球路线是有的天体。 */
    private static boolean validEnd(MinecraftServer server, boolean systems, String end) {
        Galaxy galaxy = Planets.galaxy(server);
        if (systems) {
            try {
                int index = Integer.parseInt(end);
                return index >= 0 && index < galaxy.systems().size();
            } catch (NumberFormatException e) {
                return false;
            }
        }
        return BodyId.parse(end).flatMap(galaxy::body).isPresent();
    }
}
