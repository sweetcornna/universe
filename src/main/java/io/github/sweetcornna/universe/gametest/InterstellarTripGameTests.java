package io.github.sweetcornna.universe.gametest;

import java.util.List;
import java.util.UUID;

import io.github.sweetcornna.universe.core.galaxy.Galaxy;
import io.github.sweetcornna.universe.core.galaxy.StarSystem;
import io.github.sweetcornna.universe.core.logistics.Location;
import io.github.sweetcornna.universe.core.logistics.Logic;
import io.github.sweetcornna.universe.core.logistics.LogisticsNetwork;
import io.github.sweetcornna.universe.core.logistics.Station;
import io.github.sweetcornna.universe.core.logistics.StationKind;
import io.github.sweetcornna.universe.core.logistics.Trip;
import io.github.sweetcornna.universe.core.logistics.Vehicle;
import io.github.sweetcornna.universe.core.research.ResearchState;
import io.github.sweetcornna.universe.core.research.Upgrades;
import io.github.sweetcornna.universe.factory.research.ResearchData;
import io.github.sweetcornna.universe.logistics.StationBlockEntity;
import io.github.sweetcornna.universe.registry.UniverseBlocks;
import io.github.sweetcornna.universe.registry.UniverseItems;
import io.github.sweetcornna.universe.world.planet.Planets;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;

import static io.github.sweetcornna.universe.gametest.LogisticsDispatchGameTests.forget;
import static io.github.sweetcornna.universe.gametest.LogisticsDispatchGameTests.network;
import static io.github.sweetcornna.universe.gametest.LogisticsDispatchGameTests.slot;
import static io.github.sweetcornna.universe.gametest.LogisticsDispatchGameTests.station;
import static io.github.sweetcornna.universe.gametest.LogisticsDispatchGameTests.tall;
import static io.github.sweetcornna.universe.gametest.LogisticsDispatchGameTests.tripsFrom;

/**
 * 跨恒星系的星际物流（ADR-0015、ADR-0033 §三）在游戏里完整跑一趟。一个测试是这里的供应站派船：装上货、用掉两个翘曲器跃迁到
 * 最近的另一个恒星系、卸进那边需求站的格子、空船飞回来；另一个反过来，是那边的需求站派船过来取货再带回去。由
 * {@link UniverseGameTests} 登记。
 *
 * <p>运输船跨恒星系一段要 40 秒起降加每光年 20 秒航行，只有航行的部分随"物流载具引擎"加快（{@link LogisticsNetwork#vesselLegTicks}）。
 * 测试里把这项无限研究设到 {@link #ENGINE_LEVEL} 级，一来一回两千刻上下。航程在派出时就算定了，派出以后马上恢复原来的级数，
 * 别的测试的载具只在等调度的这一秒里跟着变快。研究进度全服共用，两个测试同时跑：第一个设的时候记下原来的级数，两个都派出以后
 * 才恢复。
 *
 * <p>那边的站和 {@link LogisticsDispatchGameTests#routeStartsOnceStationBuilt} 一样只建账本记录：别的星球上的站本来就只靠账本往来，
 * 那颗星球不用加载。
 */
final class InterstellarTripGameTests {
    /** 测试期间"物流载具引擎"的级数：航行速度 ×10（{@link Upgrades#carrierSpeed}）。 */
    static final int ENGINE_LEVEL = 36;
    /** 两个测试的时限（刻）：一来一回加上等调度，留出余量。 */
    static final int MAX_TICKS = 3000;
    /** 供应站的存货和一船运的量：比运输船的容量少、比最小载货量多，一趟运完。 */
    private static final int STOCK = 1000;
    private static final int LOAD = 300;
    /** 正在用加速的测试有几个，以及加速前的级数。 */
    private static int boosted;
    private static int savedEngine;

    private InterstellarTripGameTests() {
    }

    // ---- 送货

    /**
     * 这里的供应站把货送到另一个恒星系：派出时货从供应站扣掉、扣两个翘曲器和跃迁的电，去程和回程各是一段跃迁的时间；半路上两头都没有
     * 这批货；到了卸进需求站的格子；回来时运输船回到自己的站，空着。
     */
    static void vesselDeliversAcrossSystems(GameTestHelper helper) {
        String item = "minecraft:heart_of_the_sea";
        BlockPos pos = new BlockPos(3, 1, 3);
        tall(helper, pos, UniverseBlocks.INTERSTELLAR_LOGISTICS_STATION.get().defaultBlockState());
        MinecraftServer server = helper.getLevel().getServer();
        Station[] stations = new Station[2];
        Trip[] trip = new Trip[1];
        helper.startSequence().thenExecuteAfter(1, () -> {
            Station supply = station(helper, pos);
            stations[0] = supply;
            slot(supply, item, STOCK, 10_000, Logic.STORAGE, Logic.SUPPLY);
            Station demand = remote(helper, supply, "universe:gametest_interstellar_delivery");
            stations[1] = demand;
            slot(demand, item, 0, LOAD, Logic.STORAGE, Logic.DEMAND);
            network(helper).add(demand);
            boost(server);
            // 运输船和翘曲器从投放口放进去，和玩家手放的一样由方块实体收进账本
            StationBlockEntity entity = helper.getBlockEntity(pos, StationBlockEntity.class);
            entity.setItem(StationBlockEntity.VESSEL_SLOT, new ItemStack(UniverseItems.LOGISTICS_VESSEL.get()));
            entity.setItem(StationBlockEntity.WARPER_SLOT, new ItemStack(UniverseItems.SPACE_WARPER.get(), LogisticsNetwork.WARPERS_PER_TRIP));
        }).thenWaitUntil(() -> {
            StationBlockEntity entity = helper.getBlockEntity(pos, StationBlockEntity.class);
            helper.assertTrue(entity.getItem(StationBlockEntity.VESSEL_SLOT).isEmpty() && entity.getItem(StationBlockEntity.WARPER_SLOT).isEmpty(),
                    "投放口里的运输船和翘曲器没收进账本");
        }).thenWaitUntil(() -> helper.assertFalse(tripsFrom(helper, stations[0]).isEmpty(), "供应站的运输船没出发"))
                .thenExecute(() -> {
                    release(server);
                    Station supply = stations[0];
                    Station demand = stations[1];
                    trip[0] = only(helper, supply);
                    Trip launched = trip[0];
                    helper.assertTrue(launched.vehicle() == Vehicle.VESSEL, "派出的不是运输船");
                    helper.assertTrue(launched.warp(), "去别的恒星系没跃迁");
                    helper.assertTrue(launched.delivering(), "供应站派出的船没装货");
                    helper.assertTrue(launched.other().equals(demand.id()), "运输船没去那边的需求站");
                    helper.assertValueEqual(launched.cargo(), LOAD, "船上的货");
                    helper.assertValueEqual(supply.slot(0).count(), STOCK - LOAD, "派出后供应站剩下的");
                    helper.assertValueEqual(demand.slot(0).incoming(), LOAD, "需求站记着在途的");
                    helper.assertValueEqual(supply.warpers(), 0, "派出后剩下的翘曲器");
                    helper.assertValueEqual(supply.energy(), StationKind.INTERSTELLAR.battery()
                            - LogisticsNetwork.vesselEnergy(supply.location(), demand.location()), "派出后供应站的电");
                    helper.assertValueEqual(supply.idleVessels(), 0, "派出后停着的运输船");
                    checkLegs(helper, launched, supply.location(), demand.location());
                })
                .thenWaitUntil(() -> helper.assertTrue(now(server) >= halfway(trip[0]), "去程还没过半"))
                .thenExecute(() -> {
                    Trip flying = only(helper, stations[0]);
                    helper.assertTrue(!flying.returning() && flying.cargo() == LOAD, "半路上的运输船没载着货往那边飞");
                    helper.assertValueEqual(stations[1].slot(0).count(), 0, "运输船半路上需求站就有货了");
                })
                .thenWaitUntil(() -> helper.assertValueEqual(stations[1].slot(0).count(), LOAD, "需求站收到的"))
                .thenExecute(() -> {
                    helper.assertTrue(now(server) >= trip[0].arriveAt(), "运输船还没到就卸了货");
                    Trip back = only(helper, stations[0]);
                    helper.assertTrue(back.returning() && back.cargo() == 0, "卸完货的运输船没空着往回飞");
                    helper.assertValueEqual(stations[1].slot(0).incoming(), 0, "卸完货需求站还记着在途的");
                    helper.assertValueEqual(stations[0].idleVessels(), 0, "运输船还在路上就算回了家");
                })
                .thenWaitUntil(() -> helper.assertTrue(tripsFrom(helper, stations[0]).isEmpty(), "运输船没飞回来"))
                .thenExecute(() -> {
                    Station supply = stations[0];
                    helper.assertTrue(now(server) >= trip[0].returnAt(), "运输船回来得比回程的时间早");
                    helper.assertTrue(supply.vessels() == 1 && supply.idleVessels() == 1, "运输船没回到供应站：" + supply.idleVessels() + " / "
                            + supply.vessels());
                    helper.assertValueEqual(supply.slot(0).count(), STOCK - LOAD, "回来后供应站的存货");
                    helper.assertValueEqual(stations[1].slot(0).count(), LOAD, "回来后需求站的存货");
                    forget(helper, stations);
                })
                .thenSucceed();
    }

    // ---- 取货

    /**
     * 另一个恒星系的需求站派船来这里取货：派出时这边只预订、不扣货，用的是那边的翘曲器和电；到了这边才装走；带着货跃迁回去，卸进
     * 自己的格子，运输船停回那边的站。
     */
    static void vesselFetchesAcrossSystems(GameTestHelper helper) {
        String item = "minecraft:ghast_tear";
        BlockPos pos = new BlockPos(3, 1, 3);
        tall(helper, pos, UniverseBlocks.INTERSTELLAR_LOGISTICS_STATION.get().defaultBlockState());
        MinecraftServer server = helper.getLevel().getServer();
        Station[] stations = new Station[2];
        Trip[] trip = new Trip[1];
        helper.startSequence().thenExecuteAfter(1, () -> {
            Station supply = station(helper, pos);
            stations[0] = supply;
            slot(supply, item, STOCK, 10_000, Logic.STORAGE, Logic.SUPPLY);
            Station demand = remote(helper, supply, "universe:gametest_interstellar_fetch");
            stations[1] = demand;
            slot(demand, item, 0, LOAD, Logic.STORAGE, Logic.DEMAND);
            demand.addVessels(1);
            demand.setWarpers(LogisticsNetwork.WARPERS_PER_TRIP);
            boost(server);
            network(helper).add(demand);
        }).thenWaitUntil(() -> helper.assertFalse(tripsFrom(helper, stations[1]).isEmpty(), "需求站的运输船没出发"))
                .thenExecute(() -> {
                    release(server);
                    Station supply = stations[0];
                    Station demand = stations[1];
                    trip[0] = only(helper, demand);
                    Trip launched = trip[0];
                    helper.assertTrue(launched.vehicle() == Vehicle.VESSEL, "派出的不是运输船");
                    helper.assertTrue(launched.warp(), "去别的恒星系没跃迁");
                    helper.assertFalse(launched.delivering(), "需求站派出的船装着货");
                    helper.assertTrue(launched.other().equals(supply.id()), "运输船没来这边的供应站");
                    helper.assertValueEqual(launched.amount(), LOAD, "预订的货量");
                    helper.assertValueEqual(launched.cargo(), 0, "去程船上的货");
                    helper.assertValueEqual(supply.slot(0).count(), STOCK, "船还没到，供应站的货就少了");
                    helper.assertValueEqual(supply.slot(0).outgoing(), LOAD, "供应站给运输船预订的");
                    helper.assertValueEqual(demand.slot(0).incoming(), LOAD, "需求站记着在途的");
                    helper.assertValueEqual(demand.warpers(), 0, "派出后需求站剩下的翘曲器");
                    helper.assertValueEqual(demand.energy(), StationKind.INTERSTELLAR.battery()
                            - LogisticsNetwork.vesselEnergy(demand.location(), supply.location()), "派出后需求站的电");
                    helper.assertValueEqual(supply.energy(), StationKind.INTERSTELLAR.battery(), "供应站不派船也扣了电");
                    checkLegs(helper, launched, demand.location(), supply.location());
                })
                .thenWaitUntil(() -> helper.assertValueEqual(stations[0].slot(0).count(), STOCK - LOAD, "运输船从供应站装走的"))
                .thenExecute(() -> {
                    helper.assertTrue(now(server) >= trip[0].arriveAt(), "运输船还没到就装了货");
                    Trip back = only(helper, stations[1]);
                    helper.assertTrue(back.returning() && back.cargo() == LOAD, "装了货的运输船没载着货往回飞");
                    helper.assertValueEqual(stations[0].slot(0).outgoing(), 0, "装走以后供应站还留着预订");
                    helper.assertValueEqual(stations[1].slot(0).count(), 0, "运输船还在路上需求站就有货了");
                })
                .thenWaitUntil(() -> helper.assertTrue(tripsFrom(helper, stations[1]).isEmpty(), "运输船没飞回去"))
                .thenExecute(() -> {
                    Station demand = stations[1];
                    helper.assertTrue(now(server) >= trip[0].returnAt(), "运输船回去得比回程的时间早");
                    helper.assertValueEqual(demand.slot(0).count(), LOAD, "需求站收到的");
                    helper.assertValueEqual(demand.slot(0).incoming(), 0, "收到货以后需求站还记着在途的");
                    helper.assertTrue(demand.vessels() == 1 && demand.idleVessels() == 1, "运输船没回到需求站：" + demand.idleVessels() + " / "
                            + demand.vessels());
                    helper.assertValueEqual(stations[0].slot(0).count(), STOCK - LOAD, "回去后供应站的存货");
                    forget(helper, stations);
                })
                .thenSucceed();
    }

    // ---- 共用

    /** 离这座站所在的恒星系最近、有星球的另一个恒星系，它的第一颗星球上的一座星际物流站（只有账本记录，电是满的）。 */
    private static Station remote(GameTestHelper helper, Station here, String dimension) {
        Galaxy galaxy = Planets.galaxy(helper.getLevel().getServer());
        StarSystem home = galaxy.systems().get(here.location().system());
        StarSystem nearest = null;
        for (StarSystem system : galaxy.systems()) {
            if (system.index() != home.index() && !system.bodies().isEmpty()
                    && (nearest == null || home.distanceTo(system) < home.distanceTo(nearest))) {
                nearest = system;
            }
        }
        helper.assertTrue(nearest != null, "星团里没有别的有星球的恒星系");
        Station station = new Station(UUID.randomUUID(), StationKind.INTERSTELLAR, dimension, 0, 64, 0,
                LogisticsDispatchGameTests.location(galaxy, nearest.bodies().getFirst()));
        station.setEnergy(StationKind.INTERSTELLAR.battery());
        helper.assertFalse(station.location().sameSystem(here.location()), "那边的站和这边在同一个恒星系");
        return station;
    }

    /** 去程、回程各是一段跃迁：按升了级的引擎算，比没升级时快，一来一回放得进时限。 */
    private static void checkLegs(GameTestHelper helper, Trip trip, Location from, Location to) {
        long leg = LogisticsNetwork.vesselLegTicks(from, to, Upgrades.carrierSpeed(ENGINE_LEVEL));
        helper.assertValueEqual(trip.arriveAt() - trip.departAt(), leg, "去程的刻数");
        helper.assertValueEqual(trip.returnAt() - trip.arriveAt(), leg, "回程的刻数");
        helper.assertTrue(leg < LogisticsNetwork.vesselLegTicks(from, to), "物流载具引擎没让运输船变快");
        helper.assertTrue(2 * leg + 100 < MAX_TICKS, "最近的恒星系太远（" + from.lightYearsTo(to) + " 光年），一来一回 " + 2 * leg + " 刻放不进时限");
    }

    /** 这座站派出的唯一一趟行程。 */
    private static Trip only(GameTestHelper helper, Station home) {
        List<Trip> trips = tripsFrom(helper, home);
        helper.assertValueEqual(trips.size(), 1, "在途的运输船数");
        return trips.getFirst();
    }

    private static long halfway(Trip trip) {
        return (trip.departAt() + trip.arriveAt()) / 2;
    }

    /** 账本按主世界的游戏时间推进（{@code Logistics}）。 */
    private static long now(MinecraftServer server) {
        return server.overworld().getGameTime();
    }

    /** 把"物流载具引擎"设到 {@link #ENGINE_LEVEL} 级；第一个来设的记下原来的级数。 */
    private static void boost(MinecraftServer server) {
        ResearchState research = ResearchData.get(server).state();
        if (boosted++ == 0) {
            savedEngine = research.level(Upgrades.CARRIER_ENGINE);
        }
        research.setLevel(Upgrades.CARRIER_ENGINE, ENGINE_LEVEL);
    }

    /** 运输船已经派出（航程定了）：最后一个用完的恢复原来的级数。 */
    private static void release(MinecraftServer server) {
        if (--boosted == 0) {
            ResearchData.get(server).state().setLevel(Upgrades.CARRIER_ENGINE, savedEngine);
        }
    }
}
