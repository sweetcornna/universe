package io.github.sweetcornna.universe.core.logistics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.Test;

/** 五层配对、让路、指定配对、物品路线和最小载货量（ADR-0033 §三）。 */
class DispatchTierTest {
    private static final String IRON = "minecraft:iron_ingot";
    private static final String COPPER = "minecraft:copper_ingot";
    private static final Location HOME = new Location(0, 1.0, 0, 0, 0, "s0/p0");
    private static final Location MOON = new Location(0, 1.0, 0, 0, 0, "s0/p0/m0");
    private static final Location OUTER = new Location(0, 3.0, 0, 0, 0, "s0/p3");
    private static final Location OTHER_STAR = new Location(1, 1.0, 4, 3, 0, "s1/p0");

    private static Station station(StationKind kind, int x, Location location) {
        Station station = new Station(UUID.randomUUID(), kind, "dim:" + location.body(), x, 64, 0, location);
        station.setEnergy(kind.battery());
        return station;
    }

    private static Station planetary(int x) {
        return station(StationKind.PLANETARY, x, HOME);
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

    private static Trip only(LogisticsNetwork network) {
        assertEquals(1, network.trips().size());
        return network.trips().getFirst();
    }

    // ---- 层次

    @Test
    void partnerBeatsANearerStation() {
        LogisticsNetwork network = new LogisticsNetwork();
        Station demand = planetary(0);
        Station near = planetary(100);
        Station partner = planetary(600);
        slot(demand, IRON, 0, 500, Logic.DEMAND, Logic.STORAGE);
        slot(near, IRON, 500, 5000, Logic.SUPPLY, Logic.STORAGE);
        slot(partner, IRON, 500, 5000, Logic.SUPPLY, Logic.STORAGE);
        demand.addDrones(1);
        demand.addPartner(partner.id());
        network.add(demand);
        network.add(near);
        network.add(partner);

        network.tick(0);
        assertEquals(partner.id(), only(network).other());
    }

    @Test
    void partnerCountsFromEitherSide() {
        LogisticsNetwork network = new LogisticsNetwork();
        Station supply = planetary(0);
        Station demand = planetary(10);
        partnerOf(demand, supply);
        assertEquals(Tier.POINT_TO_POINT, network.tier(supply, demand, IRON, false));
        assertEquals(Tier.POINT_TO_POINT, network.tier(demand, supply, IRON, true));
    }

    private static void partnerOf(Station station, Station partner) {
        assertTrue(partner.addPartner(station.id()));
    }

    @Test
    void sameGroupBeatsNormalPairing() {
        LogisticsNetwork network = new LogisticsNetwork();
        Station demand = planetary(0);
        Station near = planetary(100);
        Station grouped = planetary(600);
        slot(demand, IRON, 0, 500, Logic.DEMAND, Logic.STORAGE);
        slot(near, IRON, 500, 5000, Logic.SUPPLY, Logic.STORAGE);
        slot(grouped, IRON, 500, 5000, Logic.SUPPLY, Logic.STORAGE);
        demand.setGroup(3);
        grouped.setGroup(3);
        near.setGroup(4);
        demand.addDrones(1);
        network.add(demand);
        network.add(near);
        network.add(grouped);

        network.tick(0);
        assertEquals(grouped.id(), only(network).other());
    }

    @Test
    void fallsBackWhenTheBetterPartnerIsEmpty() {
        LogisticsNetwork network = new LogisticsNetwork();
        Station demand = planetary(0);
        Station near = planetary(100);
        Station partner = planetary(600);
        slot(demand, IRON, 0, 500, Logic.DEMAND, Logic.STORAGE);
        slot(near, IRON, 500, 5000, Logic.SUPPLY, Logic.STORAGE);
        slot(partner, IRON, 0, 5000, Logic.SUPPLY, Logic.STORAGE);
        demand.addDrones(1);
        demand.addPartner(partner.id());
        network.add(demand);
        network.add(near);
        network.add(partner);

        network.tick(0);
        assertEquals(near.id(), only(network).other(), "伙伴没货，往下一层找");
    }

    @Test
    void lowerTiersYieldToAWaitingPartner() {
        LogisticsNetwork network = new LogisticsNetwork();
        Station supply = planetary(0);
        Station partner = planetary(800);
        Station stranger = planetary(50);
        slot(supply, IRON, 1000, 5000, Logic.SUPPLY, Logic.STORAGE);
        StationSlot partnerSlot = slot(partner, IRON, 0, 50, Logic.DEMAND, Logic.STORAGE);
        slot(stranger, IRON, 0, 500, Logic.DEMAND, Logic.STORAGE);
        partner.addDrones(1);
        stranger.addDrones(1);
        supply.addPartner(partner.id());
        network.add(supply);
        network.add(partner);
        network.add(stranger);

        network.tick(0);
        Trip trip = only(network);
        assertEquals(partner.id(), trip.home(), "伙伴要货时，普通配对的站不来抢");
        assertEquals(1, stranger.idleDrones());

        // 伙伴那边满了（在途的也算），锁落下来，下一轮普通配对就能拿
        assertEquals(0, partnerSlot.need());
        network.tick(LogisticsNetwork.DISPATCH_INTERVAL);
        assertEquals(2, network.trips().size());
        assertEquals(0, stranger.idleDrones());
    }

    @Test
    void ownBetterPairingStopsTheSearchFromGoingLower() {
        // 需求站的伙伴在远处，只能靠伙伴自己的无人机送；近处的普通供应站也不去拿
        LogisticsNetwork network = new LogisticsNetwork();
        Station demand = planetary(0);
        Station near = planetary(100);
        Station partner = planetary(1500);
        slot(demand, IRON, 0, 500, Logic.DEMAND, Logic.STORAGE);
        slot(near, IRON, 500, 5000, Logic.SUPPLY, Logic.STORAGE);
        slot(partner, IRON, 500, 5000, Logic.SUPPLY, Logic.STORAGE);
        demand.addDrones(1);
        partner.addDrones(1);
        partner.setDroneRange(2000);
        demand.addPartner(partner.id());
        network.add(demand);
        network.add(near);
        network.add(partner);

        network.tick(0);
        Trip trip = only(network);
        assertEquals(partner.id(), trip.home());
        assertTrue(trip.delivering());
        assertEquals(1, demand.idleDrones());
    }

    @Test
    void isolatedStationsAreLeftOutOfNormalPairing() {
        LogisticsNetwork network = new LogisticsNetwork();
        Station supply = planetary(0);
        Station demand = planetary(100);
        slot(supply, IRON, 500, 5000, Logic.SUPPLY, Logic.STORAGE);
        slot(demand, IRON, 0, 500, Logic.DEMAND, Logic.STORAGE);
        demand.addDrones(1);
        supply.setIsolated(true);
        network.add(supply);
        network.add(demand);

        network.tick(0);
        assertTrue(network.trips().isEmpty(), "指定配对的站不在普通配对里");
        assertNull(network.tier(supply, demand, IRON, false));

        supply.setGroup(2);
        demand.setGroup(2);
        network.tick(LogisticsNetwork.DISPATCH_INTERVAL);
        assertEquals(supply.id(), only(network).other(), "同组的照样能配");
    }

    @Test
    void isolatedStationsOnlyUseTheirOwnPairings() {
        LogisticsNetwork network = new LogisticsNetwork();
        Station supply = planetary(0);
        Station demand = planetary(100);
        slot(supply, IRON, 500, 5000, Logic.SUPPLY, Logic.STORAGE);
        slot(demand, IRON, 0, 500, Logic.DEMAND, Logic.STORAGE);
        demand.addDrones(1);
        demand.setIsolated(true);
        network.add(supply);
        network.add(demand);

        network.tick(0);
        assertTrue(network.trips().isEmpty());
        demand.addPartner(supply.id());
        network.tick(LogisticsNetwork.DISPATCH_INTERVAL);
        assertEquals(supply.id(), only(network).other());
    }

    // ---- 物品路线

    private static Station interstellar(Location location) {
        Station station = station(StationKind.INTERSTELLAR, 0, location);
        station.addVessels(1);
        return station;
    }

    @Test
    void planetRouteSendsPastANearerPlanet() {
        LogisticsNetwork network = new LogisticsNetwork();
        Station supply = interstellar(HOME);
        Station moon = interstellar(MOON);
        Station outer = interstellar(OUTER);
        slot(supply, IRON, 1000, 10000, Logic.STORAGE, Logic.SUPPLY);
        slot(moon, IRON, 0, 1000, Logic.STORAGE, Logic.DEMAND);
        slot(outer, IRON, 0, 1000, Logic.STORAGE, Logic.DEMAND);
        moon.removeVessels(1);
        outer.removeVessels(1);
        network.add(supply);
        network.add(moon);
        network.add(outer);
        assertTrue(network.addRoute(ItemRoute.planets(IRON, "s0/p0", "s0/p3")));

        network.tick(0);
        assertEquals(outer.id(), only(network).other());
    }

    @Test
    void routeToAPlanetWithoutStationsStartsOnceOneIsBuilt() {
        LogisticsNetwork network = new LogisticsNetwork();
        Station supply = interstellar(HOME);
        supply.addVessels(1);
        Station moon = interstellar(MOON);
        moon.removeVessels(1);
        slot(supply, IRON, 5000, 10000, Logic.STORAGE, Logic.SUPPLY);
        slot(moon, IRON, 0, 1000, Logic.STORAGE, Logic.DEMAND);
        network.add(supply);
        network.add(moon);
        assertTrue(network.addRoute(ItemRoute.planets(IRON, "s0/p0", "s0/p3")));

        network.tick(0);
        assertEquals(moon.id(), only(network).other(), "那边还没有站，照普通配对走");

        Station outer = interstellar(OUTER);
        outer.removeVessels(1);
        slot(outer, IRON, 0, 1000, Logic.STORAGE, Logic.DEMAND);
        network.add(outer);
        assertEquals(1, network.stationsOn(network.routes().getFirst()).stream().filter(s -> s == outer).count());
        network.tick(LogisticsNetwork.DISPATCH_INTERVAL);
        assertEquals(2, network.trips().size());
        assertEquals(outer.id(), network.trips().get(1).other(), "站建好了，按路线走");
    }

    @Test
    void systemRouteBeatsAPlanetInTheSameSystem() {
        LogisticsNetwork network = new LogisticsNetwork();
        Station supply = interstellar(HOME);
        supply.setWarpers(10);
        Station outer = interstellar(OUTER);
        Station far = interstellar(OTHER_STAR);
        outer.removeVessels(1);
        far.removeVessels(1);
        slot(supply, IRON, 1000, 10000, Logic.STORAGE, Logic.SUPPLY);
        slot(outer, IRON, 0, 1000, Logic.STORAGE, Logic.DEMAND);
        slot(far, IRON, 0, 1000, Logic.STORAGE, Logic.DEMAND);
        network.add(supply);
        network.add(outer);
        network.add(far);
        assertTrue(network.addRoute(ItemRoute.systems(IRON, 0, 1)));

        assertEquals(Tier.SYSTEM_ROUTE, network.tier(supply, far, IRON, true));
        assertEquals(Tier.NORMAL, network.tier(supply, outer, IRON, true));
        assertEquals(Tier.NORMAL, network.tier(supply, far, COPPER, true), "路线只管它那一种物品");
        assertEquals(Tier.NORMAL, network.tier(far, supply, IRON, true), "路线有方向");
        network.tick(0);
        Trip trip = only(network);
        assertEquals(far.id(), trip.other());
        assertTrue(trip.warp());
    }

    @Test
    void planetRouteRanksAboveSystemRoute() {
        LogisticsNetwork network = new LogisticsNetwork();
        Station supply = interstellar(HOME);
        Station far = interstellar(OTHER_STAR);
        assertTrue(network.addRoute(ItemRoute.systems(IRON, 0, 1)));
        assertEquals(Tier.SYSTEM_ROUTE, network.tier(supply, far, IRON, true));
        assertTrue(network.addRoute(ItemRoute.planets(IRON, "s0/p0", "s1/p0")));
        assertEquals(Tier.PLANET_ROUTE, network.tier(supply, far, IRON, true));
        assertEquals(Tier.NORMAL, network.tier(supply, far, IRON, false), "无人机不看路线");
    }

    @Test
    void routesRejectDuplicatesAndLoops() {
        LogisticsNetwork network = new LogisticsNetwork();
        assertTrue(network.addRoute(ItemRoute.planets(IRON, "s0/p0", "s0/p3")));
        assertFalse(network.addRoute(ItemRoute.planets(IRON, "s0/p0", "s0/p3")));
        assertFalse(network.addRoute(ItemRoute.planets(IRON, "s0/p0", "s0/p0")));
        assertFalse(network.addRoute(ItemRoute.systems(IRON, 2, 2)), "同一个星系里不算星系路线");
        assertTrue(network.addRoute(ItemRoute.planets(IRON, "s0/p3", "s0/p0")));
        ItemRoute route = network.routes().getFirst();
        assertTrue(network.removeRoute(route.id()));
        assertEquals(1, network.routes().size());
    }

    @Test
    void routeEndsKnowTheirSystems() {
        assertEquals(3, ItemRoute.planets(IRON, "s3/p1/m0", "s12/p0").fromSystem());
        assertEquals(12, ItemRoute.planets(IRON, "s3/p1/m0", "s12/p0").toSystem());
        assertEquals(7, ItemRoute.systems(IRON, 7, 1).fromSystem());
        assertEquals(-1, new ItemRoute(UUID.randomUUID(), IRON, false, "space", "s0/p0").fromSystem());
        assertTrue(ItemRoute.planets(IRON, "s0/p0", "s0/p3").startsAt(HOME));
        assertFalse(ItemRoute.planets(IRON, "s0/p0", "s0/p3").startsAt(MOON), "卫星不算它的行星");
    }

    // ---- 最小载货量

    @Test
    void vesselsWaitForTheirMinimumLoad() {
        LogisticsNetwork network = new LogisticsNetwork();
        Station supply = interstellar(HOME);
        Station demand = interstellar(OUTER);
        demand.removeVessels(1);
        StationSlot from = slot(supply, IRON, 200, 10000, Logic.STORAGE, Logic.SUPPLY);
        slot(demand, IRON, 0, 2000, Logic.STORAGE, Logic.DEMAND);
        supply.setMinLoad(50);
        network.add(supply);
        network.add(demand);
        assertEquals(250, supply.vesselMinLoad(network.vesselCapacity()));

        network.tick(0);
        assertTrue(network.trips().isEmpty(), "不到半船不走");
        supply.insert(IRON, 100);
        network.tick(LogisticsNetwork.DISPATCH_INTERVAL);
        assertEquals(300, only(network).cargo());
        assertEquals(0, from.count());
    }

    @Test
    void defaultMinimumLoadIsTheOldHundred() {
        Station station = interstellar(HOME);
        assertEquals(LogisticsNetwork.VESSEL_MIN_LOAD, station.vesselMinLoad(500));
        station.setMinLoad(100);
        assertEquals(500, station.vesselMinLoad(500));
        station.setMinLoad(30);
        assertEquals(25, station.minLoad(), "只能选那几档");
        station.cycleMinLoad();
        assertEquals(50, station.minLoad());
        station.setMinLoad(100);
        station.cycleMinLoad();
        assertEquals(0, station.minLoad());
    }

    // ---- 站的设置

    @Test
    void stationsAreNumberedPerKind() {
        LogisticsNetwork network = new LogisticsNetwork();
        Station a = planetary(0);
        Station b = interstellar(HOME);
        Station c = planetary(10);
        network.add(a);
        network.add(b);
        network.add(c);
        assertEquals(1, a.number());
        assertEquals(1, b.number());
        assertEquals(2, c.number());
        network.remove(a.id());
        Station d = planetary(20);
        network.add(d);
        assertEquals(3, d.number(), "拆掉的编号不再用");
    }

    @Test
    void removingAStationDropsItFromPartnerLists() {
        LogisticsNetwork network = new LogisticsNetwork();
        Station a = planetary(0);
        Station b = planetary(10);
        a.addPartner(b.id());
        assertFalse(a.addPartner(a.id()), "不能和自己配");
        network.add(a);
        network.add(b);
        network.remove(b.id());
        assertTrue(a.partners().isEmpty());
    }

    @Test
    void namesAndGroupsAreClamped() {
        Station station = planetary(0);
        station.setName("  " + "长".repeat(40) + "  ");
        assertEquals(Station.MAX_NAME, station.name().length());
        station.setGroup(12);
        assertEquals(Station.MAX_GROUP, station.group());
        station.setGroup(-1);
        assertEquals(0, station.group());
        for (int i = 0; i < Station.MAX_PARTNERS; i++) {
            assertTrue(station.addPartner(UUID.randomUUID()));
        }
        assertFalse(station.addPartner(UUID.randomUUID()));
    }

    @Test
    void assigningAnItemKeepsItUniqueAndWaitsForTransit() {
        Station station = planetary(0);
        assertTrue(station.assign(0, IRON));
        assertFalse(station.assign(1, IRON), "同一种物品不能占两格");
        assertTrue(station.assign(1, COPPER));
        station.insert(COPPER, 10);
        assertFalse(station.assign(1, "minecraft:gold_ingot"), "有货不能换");
        assertTrue(station.assign(0, ""), "空格子能清掉");
        assertFalse(station.assign(9, IRON));
        Station collector = station(StationKind.COLLECTOR, 0, HOME);
        assertFalse(collector.assign(0, IRON), "采集器的格子是固定的");
    }
}
