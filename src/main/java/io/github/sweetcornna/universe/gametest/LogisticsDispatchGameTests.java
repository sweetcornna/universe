package io.github.sweetcornna.universe.gametest;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import io.github.sweetcornna.universe.core.galaxy.Galaxy;
import io.github.sweetcornna.universe.core.galaxy.Planet;
import io.github.sweetcornna.universe.core.galaxy.StarSystem;
import io.github.sweetcornna.universe.core.logistics.ItemRoute;
import io.github.sweetcornna.universe.core.logistics.Location;
import io.github.sweetcornna.universe.core.logistics.Logic;
import io.github.sweetcornna.universe.core.logistics.LogisticsNetwork;
import io.github.sweetcornna.universe.core.logistics.Station;
import io.github.sweetcornna.universe.core.logistics.StationKind;
import io.github.sweetcornna.universe.core.logistics.StationSlot;
import io.github.sweetcornna.universe.core.logistics.Trip;
import io.github.sweetcornna.universe.core.logistics.Vehicle;
import io.github.sweetcornna.universe.core.mecha.Distribution;
import io.github.sweetcornna.universe.factory.TallMachineBlock;
import io.github.sweetcornna.universe.logistics.ControlPanel;
import io.github.sweetcornna.universe.logistics.DistributorBlockEntity;
import io.github.sweetcornna.universe.logistics.DistributorRegistry;
import io.github.sweetcornna.universe.logistics.Logistics;
import io.github.sweetcornna.universe.logistics.StationBlockEntity;
import io.github.sweetcornna.universe.network.ControlPanelPayloads;
import io.github.sweetcornna.universe.network.ControlPanelPayloads.StationEdit;
import io.github.sweetcornna.universe.registry.UniverseBlocks;
import io.github.sweetcornna.universe.world.planet.Planets;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/**
 * 星际物流调度（ADR-0033 §三）的游戏内测试：点对点的伙伴优先于更近的站、运到还没有站的星球的路线在那边建好站以后走起来、
 * 指定配对的站不被普通配对拿走、控制面板远程改设置生效。由 {@link UniverseGameTests} 登记。
 *
 * <p>账本是全服共用的，别的测试里也有物流站。每个测试用一种别处不用的物品，免得和它们配上；结束时把自己的站从账本里去掉。
 */
final class LogisticsDispatchGameTests {
    private LogisticsDispatchGameTests() {
    }

    private static void tall(GameTestHelper helper, BlockPos pos, BlockState state) {
        helper.setBlock(pos, state.setValue(TallMachineBlock.HALF, DoubleBlockHalf.LOWER));
        helper.setBlock(pos.above(), state.setValue(TallMachineBlock.HALF, DoubleBlockHalf.UPPER));
    }

    private static Station station(GameTestHelper helper, BlockPos pos) {
        Station station = helper.getBlockEntity(pos, StationBlockEntity.class).station();
        helper.assertTrue(station != null, "账本里没有 " + pos + " 的站");
        station.setEnergy(station.kind().battery());
        return station;
    }

    private static StationSlot slot(Station station, String item, int count, int max, Logic local, Logic remote) {
        StationSlot slot = station.slot(0);
        slot.setItem(item);
        slot.setMax(max);
        slot.setLocal(local);
        slot.setRemote(remote);
        station.insert(item, count);
        return slot;
    }

    private static LogisticsNetwork network(GameTestHelper helper) {
        return Logistics.network(helper.getLevel().getServer());
    }

    private static List<Trip> tripsFrom(GameTestHelper helper, Station home) {
        List<Trip> trips = new ArrayList<>();
        for (Trip trip : network(helper).trips()) {
            if (trip.home().equals(home.id())) {
                trips.add(trip);
            }
        }
        return trips;
    }

    private static void forget(GameTestHelper helper, Station... stations) {
        for (Station station : stations) {
            network(helper).remove(station.id());
        }
    }

    // ---- 点对点

    /** 需求站的伙伴在远处、普通的供应站就在旁边：无人机去伙伴那里取货。 */
    static void partnerBeatsNearerStation(GameTestHelper helper) {
        String item = "minecraft:amethyst_shard";
        BlockPos demandPos = new BlockPos(1, 1, 1);
        BlockPos nearPos = new BlockPos(3, 1, 3);
        BlockPos partnerPos = new BlockPos(5, 1, 5);
        BlockState planetary = UniverseBlocks.PLANETARY_LOGISTICS_STATION.get().defaultBlockState();
        tall(helper, demandPos, planetary);
        tall(helper, nearPos, planetary);
        tall(helper, partnerPos, planetary);
        Station[] stations = new Station[3];
        helper.startSequence().thenExecuteAfter(1, () -> {
            stations[0] = station(helper, demandPos);
            stations[1] = station(helper, nearPos);
            stations[2] = station(helper, partnerPos);
            slot(stations[0], item, 0, 100, Logic.DEMAND, Logic.STORAGE);
            slot(stations[1], item, 200, 1000, Logic.SUPPLY, Logic.STORAGE);
            slot(stations[2], item, 200, 1000, Logic.SUPPLY, Logic.STORAGE);
            stations[0].addDrones(2);
            helper.assertTrue(stations[0].addPartner(stations[2].id()), "加不上伙伴");
            helper.assertTrue(stations[0].blocksTo(stations[1]) < stations[0].blocksTo(stations[2]), "普通的站应该更近");
        }).thenWaitUntil(() -> helper.assertFalse(tripsFrom(helper, stations[0]).isEmpty(), "需求站还没派无人机"))
                .thenExecute(() -> {
                    for (Trip trip : tripsFrom(helper, stations[0])) {
                        helper.assertTrue(trip.other().equals(stations[2].id()), "无人机去了近处的普通站，没去伙伴那里");
                    }
                    forget(helper, stations);
                }).thenSucceed();
    }

    // ---- 物品路线

    /** 星球在账本里的位置（卫星按它的行星算轨道），跟 {@code Logistics} 里一样算。 */
    private static Location location(Galaxy galaxy, Planet body) {
        StarSystem system = galaxy.systems().get(body.id().system());
        double au = galaxy.body(body.id().parent()).map(parent -> parent.orbit().radius()).orElse(1.0);
        return new Location(system.index(), au, system.x(), system.y(), system.z(), body.id().path());
    }

    /**
     * 先设一条运到别的星球的路线，那边还没有站，运输船不动；那边建起需求站以后，运输船就按路线去，不去同时建好、离得更近的
     * 普通需求站。
     */
    static void routeStartsOnceStationBuilt(GameTestHelper helper) {
        String item = "minecraft:nautilus_shell";
        BlockPos supplyPos = new BlockPos(3, 1, 3);
        tall(helper, supplyPos, UniverseBlocks.INTERSTELLAR_LOGISTICS_STATION.get().defaultBlockState());
        MinecraftServer server = helper.getLevel().getServer();
        Station[] stations = new Station[3];
        String[] target = new String[1];
        helper.startSequence().thenExecuteAfter(1, () -> {
            Station supply = station(helper, supplyPos);
            stations[0] = supply;
            slot(supply, item, 2000, 10_000, Logic.STORAGE, Logic.SUPPLY);
            supply.addVessels(2);
            // 同一个恒星系里另外两颗星球：远的那颗设路线，近的那颗放一座普通的需求站
            Galaxy galaxy = Planets.galaxy(server);
            StarSystem system = galaxy.systems().get(supply.location().system());
            List<Planet> others = new ArrayList<>();
            for (Planet body : system.bodies()) {
                if (!body.id().path().equals(supply.location().body())) {
                    others.add(body);
                }
            }
            helper.assertTrue(others.size() >= 2, "母星所在的恒星系里没有另外两颗星球");
            others.sort(Comparator.comparingDouble(body -> LogisticsNetwork.vesselLegTicks(supply.location(), location(galaxy, body))));
            Planet near = others.getFirst();
            Planet far = others.getLast();
            helper.assertTrue(LogisticsNetwork.vesselLegTicks(supply.location(), location(galaxy, near))
                    <= LogisticsNetwork.vesselLegTicks(supply.location(), location(galaxy, far)), "近的星球不比远的近");
            target[0] = far.id().path();
            helper.assertTrue(ControlPanel.editRoute(server, new ControlPanelPayloads.RouteEdit(true, StationEdit.NONE, item, false,
                    supply.location().body(), target[0])), "建不了路线");
            stations[1] = new Station(UUID.randomUUID(), StationKind.INTERSTELLAR, "universe:gametest_far", 0, 64, 0, location(galaxy, far));
            stations[2] = new Station(UUID.randomUUID(), StationKind.INTERSTELLAR, "universe:gametest_near", 0, 64, 0, location(galaxy, near));
        }).thenIdle(45).thenExecute(() -> {
            helper.assertTrue(tripsFrom(helper, stations[0]).isEmpty(), "那边还没有站，运输船不该出发");
            // 两边同时建站：路线那头的远，普通的近
            for (int i = 1; i <= 2; i++) {
                slot(stations[i], item, 0, 1000, Logic.STORAGE, Logic.DEMAND);
                network(helper).add(stations[i]);
            }
        }).thenWaitUntil(() -> helper.assertFalse(tripsFrom(helper, stations[0]).isEmpty(), "建好站以后运输船没出发"))
                .thenExecute(() -> {
                    Trip trip = tripsFrom(helper, stations[0]).getFirst();
                    helper.assertTrue(trip.vehicle() == Vehicle.VESSEL, "派出的不是运输船");
                    helper.assertTrue(trip.other().equals(stations[1].id()), "运输船没按路线走，去了近处的普通站");
                    LogisticsNetwork network = network(helper);
                    for (ItemRoute route : List.copyOf(network.routes())) {
                        if (route.item().equals(item)) {
                            network.removeRoute(route.id());
                        }
                    }
                    forget(helper, stations);
                }).thenSucceed();
    }

    // ---- 指定配对

    /** 开了指定配对的供应站，旁边普通的需求站不来拿；分到同一组以后才配得上。 */
    static void isolatedStationKeepsItsGoods(GameTestHelper helper) {
        String item = "minecraft:echo_shard";
        BlockPos isolatedPos = new BlockPos(1, 1, 1);
        BlockPos demandPos = new BlockPos(4, 1, 4);
        BlockState planetary = UniverseBlocks.PLANETARY_LOGISTICS_STATION.get().defaultBlockState();
        tall(helper, isolatedPos, planetary);
        tall(helper, demandPos, planetary);
        Station[] stations = new Station[2];
        helper.startSequence().thenExecuteAfter(1, () -> {
            stations[0] = station(helper, isolatedPos);
            stations[1] = station(helper, demandPos);
            slot(stations[0], item, 300, 1000, Logic.SUPPLY, Logic.STORAGE);
            slot(stations[1], item, 0, 100, Logic.DEMAND, Logic.STORAGE);
            stations[1].addDrones(2);
            stations[0].setIsolated(true);
        }).thenIdle(45).thenExecute(() -> {
            helper.assertTrue(tripsFrom(helper, stations[1]).isEmpty(), "普通配对拿走了指定配对的站的货");
            helper.assertValueEqual(stations[0].slot(0).count(), 300, "指定配对的站的存货");
            stations[0].setGroup(4);
            stations[1].setGroup(4);
        }).thenWaitUntil(() -> helper.assertFalse(tripsFrom(helper, stations[1]).isEmpty(), "分到同一组以后还是没配上"))
                .thenExecute(() -> forget(helper, stations)).thenSucceed();
    }

    // ---- 远程改设置

    /**
     * 控制面板走的服务端方法（{@link ControlPanel#editStation} 这几个）：改名、分组、指定配对、起送量、伙伴、格子的物品、供需和上限
     * 都改到账本上，和站前的按钮叠加起来；配送器的模式直接改到方块上并记进登记表；路线能建能删。
     */
    static void remoteEditApplies(GameTestHelper helper) {
        String item = "minecraft:prismarine_crystals";
        BlockPos stationPos = new BlockPos(1, 1, 1);
        BlockPos partnerPos = new BlockPos(5, 1, 5);
        BlockPos distributorPos = new BlockPos(3, 2, 1);
        tall(helper, stationPos, UniverseBlocks.INTERSTELLAR_LOGISTICS_STATION.get().defaultBlockState());
        tall(helper, partnerPos, UniverseBlocks.INTERSTELLAR_LOGISTICS_STATION.get().defaultBlockState());
        helper.setBlock(distributorPos.below(), Blocks.CHEST);
        helper.setBlock(distributorPos, UniverseBlocks.LOGISTICS_DISTRIBUTOR.get());
        MinecraftServer server = helper.getLevel().getServer();
        Station[] stations = new Station[2];
        helper.startSequence().thenExecuteAfter(1, () -> {
            Station station = station(helper, stationPos);
            Station partner = station(helper, partnerPos);
            stations[0] = station;
            stations[1] = partner;
            UUID id = station.id();
            helper.assertTrue(edit(server, id, StationEdit.RENAME, 0, 0, "  铁矿外运  ", StationEdit.NONE), "改不了名");
            helper.assertValueEqual(station.name(), "铁矿外运", "名字");
            helper.assertTrue(edit(server, id, StationEdit.GROUP, 0, 3, "", StationEdit.NONE), "改不了分组");
            helper.assertValueEqual(station.group(), 3, "分组");
            helper.assertTrue(edit(server, id, StationEdit.ISOLATE, 0, 1, "", StationEdit.NONE), "开不了指定配对");
            helper.assertTrue(station.isolated(), "指定配对没开");
            helper.assertTrue(edit(server, id, StationEdit.MIN_LOAD, 0, 50, "", StationEdit.NONE), "改不了起送量");
            helper.assertValueEqual(station.minLoad(), 50, "起送量");
            helper.assertTrue(edit(server, id, StationEdit.PARTNER_ADD, 0, 0, "", partner.id()), "加不上伙伴");
            helper.assertTrue(station.partneredWith(partner), "伙伴没加上");
            helper.assertTrue(edit(server, partner.id(), StationEdit.PARTNER_REMOVE, 0, 0, "", id), "从另一头去不掉伙伴");
            helper.assertFalse(station.partneredWith(partner), "伙伴没去掉");
            // 格子：设物品、供需、上限
            helper.assertTrue(edit(server, id, StationEdit.SLOT_ITEM, 1, 0, item, StationEdit.NONE), "设不了物品");
            helper.assertValueEqual(station.slot(1).item(), item, "第二格的物品");
            helper.assertFalse(edit(server, id, StationEdit.SLOT_ITEM, 2, 0, item, StationEdit.NONE), "同一种物品占了两格");
            helper.assertFalse(edit(server, id, StationEdit.SLOT_ITEM, 2, 0, "universe:no_such_item", StationEdit.NONE), "认了不存在的物品");
            helper.assertTrue(edit(server, id, StationEdit.SLOT_BUTTON, 1, StationBlockEntity.CYCLE_REMOTE, "", StationEdit.NONE), "改不了远程供需");
            helper.assertValueEqual(station.slot(1).remote(), Logic.SUPPLY, "远程供需");
            int max = station.slot(1).max();
            helper.assertTrue(edit(server, id, StationEdit.SLOT_BUTTON, 1, StationBlockEntity.MAX_MINUS, "", StationEdit.NONE), "改不了上限");
            // 站前的按钮走同一个方法，效果叠在一起
            ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
            helper.getBlockEntity(stationPos, StationBlockEntity.class).clickButton(player, 16 + StationBlockEntity.MAX_MINUS);
            helper.assertValueEqual(station.slot(1).max(), max - 200, "远程和站前各减一次的上限");
            station.insert(item, 5);
            helper.assertFalse(edit(server, id, StationEdit.SLOT_ITEM, 1, 0, "minecraft:stone", StationEdit.NONE), "有货的格子换了物品");
            // 路线
            helper.assertTrue(ControlPanel.editRoute(server, new ControlPanelPayloads.RouteEdit(true, StationEdit.NONE, item, true, "0", "1")),
                    "建不了星系路线");
            ItemRoute route = Logistics.network(server).routes().stream().filter(r -> r.item().equals(item)).findFirst().orElse(null);
            helper.assertTrue(route != null, "路线没记下来");
            helper.assertFalse(ControlPanel.editRoute(server, new ControlPanelPayloads.RouteEdit(true, StationEdit.NONE, item, true, "0", "1")),
                    "重复的路线也建了");
            helper.assertTrue(ControlPanel.editRoute(server, new ControlPanelPayloads.RouteEdit(false, route.id(), "", false, "", "")), "删不了路线");
            // 快照里看得到改过的站
            ControlPanelPayloads.StationRow row = ControlPanel.snapshot(server).stations().stream().filter(r -> r.id().equals(id)).findFirst()
                    .orElse(null);
            helper.assertTrue(row != null && row.name().equals("铁矿外运") && row.group() == 3, "快照里的站没变");
            // 配送器：加载着的直接改
            DistributorBlockEntity distributor = helper.getBlockEntity(distributorPos, DistributorBlockEntity.class);
            String dimension = helper.getLevel().dimension().identifier().toString();
            BlockPos absolute = helper.absolutePos(distributorPos);
            helper.assertTrue(ControlPanel.editDistributor(server, new ControlPanelPayloads.DistributorEdit(dimension, absolute,
                    DistributorBlockEntity.CYCLE_PEER)), "改不了配送器的模式");
            helper.assertValueEqual(distributor.peer(), Distribution.Peer.SUPPLY, "配送器对配送器的模式");
            DistributorRegistry.Entry entry = DistributorRegistry.get(server).get(new DistributorRegistry.Key(dimension, absolute));
            helper.assertTrue(entry != null && entry.peer() == Distribution.Peer.SUPPLY, "登记表里没记上新的模式");
            helper.assertFalse(ControlPanel.editDistributor(server, new ControlPanelPayloads.DistributorEdit(dimension, absolute,
                    DistributorBlockEntity.SET_FILTER)), "远程改了配送器的物品");
            forget(helper, stations);
        }).thenSucceed();
    }

    private static boolean edit(MinecraftServer server, UUID station, int action, int slot, int value, String text, UUID other) {
        return ControlPanel.editStation(server, new StationEdit(station, action, slot, value, text, other));
    }
}
