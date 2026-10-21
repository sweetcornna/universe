package io.github.sweetcornna.universe.core.logistics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class LogisticsNetworkTest {
    private static final String IRON = "minecraft:iron_ingot";
    private static final Location HOME = new Location(0, 1.0, 0, 0, 0, "s0/p0");
    private static final Location MOON = new Location(0, 1.0, 0, 0, 0, "s0/p0/m0");
    private static final Location OUTER = new Location(0, 3.0, 0, 0, 0, "s0/p3");
    private static final Location OTHER_STAR = new Location(1, 1.0, 4, 3, 0, "s1/p0");

    private static Station station(StationKind kind, String dimension, int x, Location location) {
        Station station = new Station(UUID.randomUUID(), kind, dimension, x, 64, 0, location);
        station.setEnergy(kind.battery());
        return station;
    }

    private static StationSlot slot(Station station, String item, int count, int max, Logic local, Logic remote) {
        StationSlot slot = station.slot(0);
        assertTrue(slot.setItem(item));
        slot.setMax(max);
        slot.setLocal(local);
        slot.setRemote(remote);
        station.insert(item, count);
        return slot;
    }

    /** 一直推进到没有在途的行程。 */
    private static long settle(LogisticsNetwork network, long from, long limit) {
        long now = from;
        do {
            network.tick(now++);
        } while ((!network.trips().isEmpty() || now % LogisticsNetwork.DISPATCH_INTERVAL != 1) && now < limit);
        return now;
    }

    @Test
    void supplyStationSendsDronesUntilDemandIsFull() {
        LogisticsNetwork network = new LogisticsNetwork();
        Station supply = station(StationKind.PLANETARY, "overworld", 0, HOME);
        Station demand = station(StationKind.PLANETARY, "overworld", 200, HOME);
        StationSlot from = slot(supply, IRON, 500, 5000, Logic.SUPPLY, Logic.STORAGE);
        StationSlot to = slot(demand, IRON, 0, 200, Logic.DEMAND, Logic.STORAGE);
        supply.addDrones(10);
        network.add(supply);
        network.add(demand);

        network.tick(0);
        assertEquals(1, network.trips().size(), "每格每轮只派一架");
        Trip trip = network.trips().getFirst();
        assertTrue(trip.delivering());
        assertEquals(50, trip.cargo());
        assertEquals(450, from.count(), "派出时就装货");
        assertEquals(50, to.incoming(), "到之前算在途");
        // 200 格，每秒 8 格，加 2 秒起降：一段 27 秒
        assertEquals(LogisticsNetwork.droneLegTicks(200), trip.arriveAt());
        assertEquals(540, trip.arriveAt());

        settle(network, 1, 20_000);
        assertEquals(200, to.count());
        assertEquals(300, from.count());
        assertEquals(0, to.incoming());
        assertEquals(10, supply.idleDrones(), "无人机都回来了");
        assertTrue(supply.energy() < StationKind.PLANETARY.battery(), "派无人机扣电");
    }

    @Test
    void upgradesMakeDronesFasterAndCarryMore() {
        LogisticsNetwork network = new LogisticsNetwork();
        network.setUpgrades(2, 6);
        Station supply = station(StationKind.PLANETARY, "overworld", 0, HOME);
        Station demand = station(StationKind.PLANETARY, "overworld", 200, HOME);
        slot(supply, IRON, 500, 5000, Logic.SUPPLY, Logic.STORAGE);
        slot(demand, IRON, 0, 200, Logic.DEMAND, Logic.STORAGE);
        supply.addDrones(10);
        network.add(supply);
        network.add(demand);

        network.tick(0);
        Trip trip = network.trips().getFirst();
        // 容量 6 级：50 → 80；速度翻倍：飞行 12.5 秒，起降还是 2 秒
        assertEquals(80, trip.cargo());
        assertEquals(290, trip.arriveAt());
        assertEquals(LogisticsNetwork.droneLegTicks(200, 2), trip.arriveAt());
        assertEquals(800, network.vesselCapacity());
        assertEquals(LogisticsNetwork.vesselLegTicks(HOME, OUTER), Math.round((LogisticsNetwork.VESSEL_BASE + 2 * LogisticsNetwork.VESSEL_PER_AU) * 20));
        assertEquals(Math.round((LogisticsNetwork.VESSEL_BASE + LogisticsNetwork.VESSEL_PER_AU) * 20), LogisticsNetwork.vesselLegTicks(HOME, OUTER, 2));
    }

    @Test
    void demandStationFetchesWithItsOwnDronesAndReservesTheGoods() {
        LogisticsNetwork network = new LogisticsNetwork();
        Station supply = station(StationKind.PLANETARY, "overworld", 0, HOME);
        Station demand = station(StationKind.PLANETARY, "overworld", 100, HOME);
        StationSlot from = slot(supply, IRON, 60, 5000, Logic.SUPPLY, Logic.STORAGE);
        StationSlot to = slot(demand, IRON, 0, 1000, Logic.DEMAND, Logic.STORAGE);
        demand.addDrones(5);
        network.add(supply);
        network.add(demand);

        network.tick(0);
        assertEquals(1, network.trips().size());
        assertFalse(network.trips().getFirst().delivering());
        assertEquals(50, from.outgoing());
        // 预订的那部分外面拿不走
        assertEquals(10, supply.extract(0, 100));
        assertEquals(50, from.count());

        settle(network, 1, 20_000);
        assertEquals(50, to.count());
        assertEquals(0, from.count());
        assertEquals(0, from.outgoing());
        assertEquals(0, to.incoming());
    }

    @Test
    void dronesStayHomeOutOfRangeOrWithoutPower() {
        LogisticsNetwork network = new LogisticsNetwork();
        Station supply = station(StationKind.PLANETARY, "overworld", 0, HOME);
        Station far = station(StationKind.PLANETARY, "overworld", 1500, HOME);
        Station otherWorld = station(StationKind.PLANETARY, "universe:s0/p0/m0", 50, MOON);
        slot(supply, IRON, 500, 5000, Logic.SUPPLY, Logic.STORAGE);
        slot(far, IRON, 0, 100, Logic.DEMAND, Logic.STORAGE);
        slot(otherWorld, IRON, 0, 100, Logic.DEMAND, Logic.STORAGE);
        supply.addDrones(10);
        network.add(supply);
        network.add(far);
        network.add(otherWorld);

        network.tick(0);
        assertTrue(network.trips().isEmpty(), "默认航程 1000 格，别的维度不去");

        supply.setDroneRange(2000);
        supply.setEnergy(0);
        network.tick(20);
        assertTrue(network.trips().isEmpty(), "没电不派");

        supply.setEnergy(StationKind.PLANETARY.battery());
        network.tick(40);
        assertEquals(1, network.trips().size());
    }

    @Test
    void dronesWaitForAWorthwhileLoad() {
        LogisticsNetwork network = new LogisticsNetwork();
        Station supply = station(StationKind.PLANETARY, "overworld", 0, HOME);
        Station demand = station(StationKind.PLANETARY, "overworld", 50, HOME);
        StationSlot from = slot(supply, IRON, 3, 5000, Logic.SUPPLY, Logic.STORAGE);
        slot(demand, IRON, 0, 100, Logic.DEMAND, Logic.STORAGE);
        supply.addDrones(1);
        network.add(supply);
        network.add(demand);
        network.tick(0);
        assertTrue(network.trips().isEmpty(), "只有 3 个，不值得跑一趟");
        supply.insert(IRON, 20);
        network.tick(20);
        assertEquals(1, network.trips().size());
        assertEquals(0, from.count());
    }

    @Test
    void vesselsNeedWarpersToLeaveTheSystem() {
        LogisticsNetwork network = new LogisticsNetwork();
        Station home = station(StationKind.INTERSTELLAR, "overworld", 0, HOME);
        Station away = station(StationKind.INTERSTELLAR, "universe:s1/p0", 0, OTHER_STAR);
        StationSlot from = slot(home, IRON, 2000, 10_000, Logic.STORAGE, Logic.SUPPLY);
        StationSlot to = slot(away, IRON, 0, 1000, Logic.STORAGE, Logic.DEMAND);
        home.addVessels(2);
        network.add(home);
        network.add(away);

        network.tick(0);
        assertTrue(network.trips().isEmpty(), "没有翘曲器不去别的恒星系");

        home.setWarpers(3);
        network.tick(20);
        assertEquals(1, network.trips().size());
        Trip trip = network.trips().getFirst();
        assertTrue(trip.warp());
        assertEquals(500, trip.cargo());
        assertEquals(1, home.warpers(), "来回一趟两个翘曲器");
        // 5 光年：40 秒 + 每光年 20 秒 = 140 秒
        assertEquals(20 + 140 * 20, trip.arriveAt());

        settle(network, 21, 100_000);
        assertEquals(500, to.count(), "只剩一个翘曲器，第二趟不去");
        assertEquals(1500, from.count());
    }

    @Test
    void vesselsInTheSameSystemFlyByOrbitDistance() {
        LogisticsNetwork network = new LogisticsNetwork();
        Station home = station(StationKind.INTERSTELLAR, "overworld", 0, HOME);
        Station outer = station(StationKind.INTERSTELLAR, "universe:s0/p3", 0, OUTER);
        slot(home, IRON, 0, 1000, Logic.STORAGE, Logic.DEMAND);
        slot(outer, IRON, 800, 10_000, Logic.STORAGE, Logic.SUPPLY);
        home.addVessels(1);
        network.add(home);
        network.add(outer);
        network.tick(0);
        Trip trip = network.trips().getFirst();
        assertFalse(trip.warp());
        // 2 天文单位：20 秒 + 每天文单位 30 秒 = 80 秒
        assertEquals(80 * 20, trip.arriveAt());
        assertEquals(500, trip.amount());
    }

    @Test
    void removedStationsDoNotSwallowGoods() {
        LogisticsNetwork network = new LogisticsNetwork();
        Station supply = station(StationKind.PLANETARY, "overworld", 0, HOME);
        Station demand = station(StationKind.PLANETARY, "overworld", 100, HOME);
        StationSlot from = slot(supply, IRON, 100, 5000, Logic.SUPPLY, Logic.STORAGE);
        slot(demand, IRON, 0, 50, Logic.DEMAND, Logic.STORAGE);
        supply.addDrones(1);
        network.add(supply);
        network.add(demand);
        network.tick(0);
        assertEquals(50, from.count());
        // 目的地在半路上被拆了：货原路带回来
        network.remove(demand.id());
        settle(network, 1, 20_000);
        assertEquals(100, from.count());
        assertEquals(1, supply.idleDrones());
    }

    @Test
    void pickupForAVanishedHomeReleasesTheReservation() {
        LogisticsNetwork network = new LogisticsNetwork();
        Station supply = station(StationKind.PLANETARY, "overworld", 0, HOME);
        Station demand = station(StationKind.PLANETARY, "overworld", 100, HOME);
        StationSlot from = slot(supply, IRON, 100, 5000, Logic.SUPPLY, Logic.STORAGE);
        slot(demand, IRON, 0, 50, Logic.DEMAND, Logic.STORAGE);
        demand.addDrones(1);
        network.add(supply);
        network.add(demand);
        network.tick(0);
        assertEquals(50, from.outgoing());
        network.remove(demand.id());
        settle(network, 1, 20_000);
        assertEquals(0, from.outgoing());
        assertEquals(100, from.count());
    }

    @Test
    void collectorsProduceByElapsedTime() {
        LogisticsNetwork network = new LogisticsNetwork();
        Station collector = station(StationKind.COLLECTOR, "universe:s0/space", 0, OUTER);
        collector.slot(0).setItem("universe:hydrogen");
        collector.slot(1).setItem("universe:deuterium");
        collector.setProduction(0, 4 * 0.8);
        collector.setProduction(1, 0.2 * 0.8);
        network.add(collector);
        for (long t = 0; t < 20 * 60; t++) {
            network.tick(t);
        }
        // 一分钟：氢 4 × 0.8 × 60 = 192，重氢 0.2 × 0.8 × 60 = 9.6
        assertEquals(192, collector.slot(0).count());
        assertEquals(9, collector.slot(1).count());
    }

    @Test
    void slotItemsCannotChangeWhileGoodsAreInTransit() {
        LogisticsNetwork network = new LogisticsNetwork();
        Station supply = station(StationKind.PLANETARY, "overworld", 0, HOME);
        Station demand = station(StationKind.PLANETARY, "overworld", 100, HOME);
        StationSlot from = slot(supply, IRON, 50, 5000, Logic.SUPPLY, Logic.STORAGE);
        StationSlot to = slot(demand, IRON, 0, 50, Logic.DEMAND, Logic.STORAGE);
        supply.addDrones(1);
        network.add(supply);
        network.add(demand);
        network.tick(0);
        assertEquals(0, from.count());
        assertFalse(to.setItem("minecraft:gold_ingot"), "有货在路上，不能换");
        assertFalse(supply.removeDrones(1) > 0, "飞出去的无人机拿不出来");
        assertNotNull(network.get(supply.id()));
    }

    @Test
    void slotKeepsProliferationPoints() {
        Station station = station(StationKind.PLANETARY, "overworld", 0, HOME);
        StationSlot slot = slot(station, IRON, 0, 100, Logic.STORAGE, Logic.STORAGE);
        assertEquals(60, station.insert(IRON, 60, 120));
        assertEquals(2, slot.level());
        // 只放得下 40 个：带进去 40 个的点数
        assertEquals(40, station.insert(IRON, 50, 200));
        assertEquals(120 + 160, slot.points());
        assertEquals(2, slot.level(), "平均 2.8 点，按 2 点给");
        assertEquals(10, station.extract(0, 10, 2));
        assertEquals(260, slot.points());
        // 不指定点数拿：按比例带走
        assertEquals(45, station.extract(0, 45));
        assertEquals(130, slot.points());
        // 点数不会超过每个 4 点
        station.restoreCount(0, 10);
        assertEquals(40, slot.points());
        assertEquals(4, slot.level());
        assertEquals(3, StationSlot.share(10, 1, 3));
        assertEquals(10, StationSlot.share(10, 5, 5));
    }

    @Test
    void pointsTravelWithTheCargo() {
        LogisticsNetwork network = new LogisticsNetwork();
        Station supply = station(StationKind.PLANETARY, "overworld", 0, HOME);
        Station demand = station(StationKind.PLANETARY, "overworld", 200, HOME);
        StationSlot from = slot(supply, IRON, 0, 5000, Logic.SUPPLY, Logic.STORAGE);
        supply.insert(IRON, 500, 1000);
        StationSlot to = slot(demand, IRON, 0, 200, Logic.DEMAND, Logic.STORAGE);
        supply.addDrones(10);
        network.add(supply);
        network.add(demand);

        network.tick(0);
        assertEquals(100, network.trips().getFirst().points(), "50 个，每个 2 点");
        settle(network, 1, 20_000);
        assertEquals(200, to.count());
        assertEquals(400, to.points());
        assertEquals(600, from.points());
        assertEquals(2, to.level());

        // 对方来取：取货的回程也带点数
        Station picker = station(StationKind.PLANETARY, "overworld", -200, HOME);
        StationSlot wanted = slot(picker, IRON, 0, 100, Logic.DEMAND, Logic.STORAGE);
        picker.addDrones(1);
        network.add(picker);
        settle(network, 20_001, 60_000);
        assertEquals(100, wanted.count());
        assertEquals(200, wanted.points());
    }
}
