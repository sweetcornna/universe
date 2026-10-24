package io.github.sweetcornna.universe.logistics;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.core.galaxy.BodyId;
import io.github.sweetcornna.universe.core.galaxy.Galaxy;
import io.github.sweetcornna.universe.core.logistics.ItemRoute;
import io.github.sweetcornna.universe.core.logistics.LogisticsNetwork;
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
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 物流控制面板的服务端（ADR-0033 §三）：给玩家发全服的站、配送器、路线和在飞的运输船，处理远程改设置。
 *
 * <p>远程改的是账本，不用加载站所在的区块；格子的按钮和站前的界面走同一个方法（{@link StationBlockEntity#applySetting}），
 * 效果一样。配送器加载着就直接改，没加载就记在登记表里等它加载。旁观者不能改。
 */
public final class ControlPanel {
    /** 同一个玩家两次要快照至少隔几刻（改了设置以后的那一份不算）。 */
    private static final int REQUEST_COOLDOWN = 10;
    private static final Map<ServerPlayer, Long> LAST_SENT = new WeakHashMap<>();

    private ControlPanel() {
    }

    // ---- 快照

    public static ControlPanelPayloads.Snapshot snapshot(MinecraftServer server) {
        LogisticsNetwork network = Logistics.network(server);
        List<ControlPanelPayloads.StationRow> stations = new ArrayList<>();
        for (Station station : network.stations()) {
            if (stations.size() >= ControlPanelPayloads.MAX_STATIONS) {
                break;
            }
            stations.add(row(station));
        }
        List<ControlPanelPayloads.RouteRow> routes = new ArrayList<>();
        for (ItemRoute route : network.routes()) {
            List<UUID> on = new ArrayList<>();
            network.stationsOn(route).forEach(station -> on.add(station.id()));
            routes.add(new ControlPanelPayloads.RouteRow(route.id(), route.item(), route.systems(), route.from(), route.to(), on));
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
        return new ControlPanelPayloads.Snapshot(server.overworld().getGameTime(), stations, distributors(server), routes, vessels);
    }

    private static ControlPanelPayloads.StationRow row(Station station) {
        List<ControlPanelPayloads.SlotRow> slots = new ArrayList<>();
        for (StationSlot slot : station.slots()) {
            slots.add(new ControlPanelPayloads.SlotRow(slot.item(), slot.count(), slot.max(), slot.local().ordinal(), slot.remote().ordinal(),
                    slot.incoming()));
        }
        return new ControlPanelPayloads.StationRow(station.id(), station.kind().ordinal(), station.dimension(),
                new BlockPos(station.x(), station.y(), station.z()), station.location().body(), station.location().system(), station.name(),
                station.number(), station.group(), station.isolated(), station.minLoad(), List.copyOf(station.partners()), slots,
                station.drones(), station.idleDrones(), station.vessels(), station.idleVessels(), station.warpers(), station.droneRange(),
                station.energy());
    }

    /** 登记表里的配送器；加载着的顺手对一下，方块已经没了的划掉。 */
    private static List<ControlPanelPayloads.DistributorRow> distributors(MinecraftServer server) {
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
                    entry.peer().ordinal(), entry.bots(), entry.pending()));
        }
        return rows;
    }

    private static @Nullable ServerLevel level(MinecraftServer server, String dimension) {
        Identifier id = Identifier.tryParse(dimension);
        return id == null ? null : server.getLevel(ResourceKey.create(Registries.DIMENSION, id));
    }

    private static void send(ServerPlayer player) {
        LAST_SENT.put(player, player.level().getGameTime());
        PacketDistributor.sendToPlayer(player, snapshot(player.level().getServer()));
    }

    // ---- 包

    public static void handleRequest(ControlPanelPayloads.Request payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            Long last = LAST_SENT.get(player);
            if (last == null || Math.abs(player.level().getGameTime() - last) >= REQUEST_COOLDOWN) {
                send(player);
            }
        }
    }

    public static void handleStation(ControlPanelPayloads.StationEdit payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player && !player.isSpectator()) {
            editStation(player.level().getServer(), payload);
            send(player);
        }
    }

    public static void handleDistributor(ControlPanelPayloads.DistributorEdit payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player && !player.isSpectator()) {
            editDistributor(player.level().getServer(), payload);
            send(player);
        }
    }

    public static void handleRoute(ControlPanelPayloads.RouteEdit payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player && !player.isSpectator()) {
            editRoute(player.level().getServer(), payload);
            send(player);
        }
    }

    // ---- 改设置（GameTest 也直接调这几个）

    /** 远程改一座站，返回改成了没有。 */
    public static boolean editStation(MinecraftServer server, ControlPanelPayloads.StationEdit edit) {
        LogisticsNetwork network = Logistics.network(server);
        Station station = network.get(edit.station());
        if (station == null) {
            return false;
        }
        boolean changed = switch (edit.action()) {
            case ControlPanelPayloads.StationEdit.RENAME -> {
                station.setName(edit.text());
                yield true;
            }
            case ControlPanelPayloads.StationEdit.GROUP -> {
                station.setGroup(edit.value());
                yield true;
            }
            case ControlPanelPayloads.StationEdit.ISOLATE -> {
                station.setIsolated(edit.value() != 0);
                yield true;
            }
            case ControlPanelPayloads.StationEdit.MIN_LOAD -> {
                if (!station.kind().remote()) {
                    yield false;
                }
                station.setMinLoad(edit.value());
                yield true;
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
                station.setDroneRange(edit.value());
                yield true;
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
        LogisticsNetwork network = Logistics.network(server);
        boolean changed;
        if (edit.add()) {
            String item = validItem(edit.item());
            changed = item != null && validEnd(server, edit.systems(), edit.from()) && validEnd(server, edit.systems(), edit.to())
                    && network.addRoute(new ItemRoute(UUID.randomUUID(), item, edit.systems(), edit.from(), edit.to()));
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
