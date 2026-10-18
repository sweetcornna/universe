package io.github.sweetcornna.universe.core.logistics;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/**
 * 物流网络（ADR-0015）：全服所有物流站的账本和在途的运输。每刻推进行程，每秒调度一次。
 *
 * <p>调度：每座有空闲载具的站，按格子看一遍——需求格去找航程内最近的供应站（空着去、装了回来），供应格去找最近的
 * 需求站（装好了去、空着回来）。本地的用无人机，同一维度里、在自己的航程内；远程的用运输船，在不同星球的站之间，
 * 跨恒星系要两个翘曲器。每格每轮每个方向最多派一架，载具不满一定量不出发（免得零零碎碎地跑）。派出时扣电。
 */
public final class LogisticsNetwork {
    /** 每隔几刻调度一次。 */
    public static final int DISPATCH_INTERVAL = 20;
    public static final int DRONE_CAPACITY = 50;
    public static final int VESSEL_CAPACITY = 500;
    /** 载具至少装这么多才出发（需求或存货比这少时，够数就走）。 */
    public static final int DRONE_MIN_LOAD = 10;
    public static final int VESSEL_MIN_LOAD = 100;
    /** 无人机每秒飞几格，每段起降几秒。 */
    public static final double DRONE_SPEED = 8;
    public static final double DRONE_TAKEOFF = 2;
    /** 运输船每段：同一恒星系 20 秒起降 + 每天文单位 30 秒；跨恒星系 40 秒 + 每光年 20 秒。 */
    public static final double VESSEL_BASE = 20;
    public static final double VESSEL_PER_AU = 30;
    public static final double WARP_BASE = 40;
    public static final double WARP_PER_LY = 20;
    public static final int WARPERS_PER_TRIP = 2;
    /** 派一架无人机：起飞 200 焦，来回每格 8 焦。 */
    public static final long DRONE_ENERGY = 200;
    public static final long DRONE_ENERGY_PER_BLOCK = 8;
    /** 派一艘运输船：20 万焦，每天文单位 10 万焦；跃迁一次 100 万焦。 */
    public static final long VESSEL_ENERGY = 200_000;
    public static final long VESSEL_ENERGY_PER_AU = 100_000;
    public static final long WARP_ENERGY = 1_000_000;

    /** 轨道采集器这类自己产出东西的站，每产出一批告诉它一声（生产统计用）。 */
    @FunctionalInterface
    public interface ProductionListener {
        ProductionListener NONE = (station, item, count) -> {
        };

        void produced(Station station, String item, int count);
    }

    private final Map<UUID, Station> stations = new LinkedHashMap<>();
    private ProductionListener listener = ProductionListener.NONE;
    private final List<Trip> trips = new ArrayList<>();
    /** 行程或站点每变一次加一，服务端据此判断要不要给玩家重发在飞的载具。 */
    private long revision;

    // ---- 账本

    public void add(Station station) {
        this.stations.put(station.id(), station);
    }

    /** 拆掉一座站。在途的载具：家没了的，回来时消失；目的地没了的，原路返回。 */
    public @Nullable Station remove(UUID id) {
        this.revision++;
        return this.stations.remove(id);
    }

    public long revision() {
        return this.revision;
    }

    public void setProductionListener(ProductionListener listener) {
        this.listener = listener;
    }

    public @Nullable Station get(UUID id) {
        return this.stations.get(id);
    }

    public Collection<Station> stations() {
        return Collections.unmodifiableCollection(this.stations.values());
    }

    public List<Trip> trips() {
        return Collections.unmodifiableList(this.trips);
    }

    /** 读档时恢复一趟行程。 */
    public void restoreTrip(Trip trip) {
        this.trips.add(trip);
    }

    // ---- 推进

    /** 推进到 {@code now}（游戏刻）：处理到达和返航，每 {@link #DISPATCH_INTERVAL} 刻产出和调度一次。 */
    public void tick(long now) {
        for (int i = 0; i < this.trips.size(); i++) {
            Trip trip = this.trips.get(i);
            if (!trip.returning() && now >= trip.arriveAt()) {
                this.trips.set(i, this.arrive(trip));
            }
        }
        for (Iterator<Trip> it = this.trips.iterator(); it.hasNext();) {
            Trip trip = it.next();
            if (trip.returning() && now >= trip.returnAt()) {
                this.complete(trip);
                it.remove();
                this.revision++;
            }
        }
        if (now % DISPATCH_INTERVAL == 0) {
            for (Station station : this.stations.values()) {
                station.produce(DISPATCH_INTERVAL / 20.0, this.listener);
            }
            this.dispatch(now);
        }
    }

    /** 到了对方的站：送货的卸下，取货的装上。 */
    private Trip arrive(Trip trip) {
        Station home = this.stations.get(trip.home());
        Station other = this.stations.get(trip.other());
        StationSlot slot = other == null ? null : matching(other, trip.otherSlot(), trip.item());
        if (trip.delivering()) {
            int left = trip.cargo();
            if (slot != null) {
                slot.releaseIncoming(trip.amount());
                left -= slot.add(left, other.kind().slotCapacity());
            }
            return trip.arrived(left);
        }
        if (slot == null) {
            return trip.arrived(0);
        }
        if (home == null) {
            // 家已经没了：不拿了，把预订放掉
            slot.releaseOutgoing(trip.amount());
            return trip.arrived(0);
        }
        return trip.arrived(slot.pickUp(trip.amount()));
    }

    /** 回到家：载具空闲，取回来的货（或者没送出去的货）入库。 */
    private void complete(Trip trip) {
        Station home = this.stations.get(trip.home());
        if (home == null) {
            return;
        }
        home.land(trip.vehicle());
        StationSlot slot = matching(home, trip.homeSlot(), trip.item());
        if (slot != null) {
            if (!trip.delivering()) {
                slot.releaseIncoming(trip.amount());
            }
            slot.add(trip.cargo(), home.kind().slotCapacity());
        }
    }

    /** 那一格还是这种物品时返回它（期间格子的物品不能换，这里只是防万一）。 */
    private static @Nullable StationSlot matching(Station station, int index, String item) {
        if (index < 0 || index >= station.kind().slots()) {
            return null;
        }
        StationSlot slot = station.slot(index);
        return slot.item().equals(item) ? slot : null;
    }

    // ---- 调度

    private void dispatch(long now) {
        for (Station station : this.stations.values()) {
            for (int i = 0; i < station.kind().slots(); i++) {
                StationSlot slot = station.slot(i);
                if (slot.isEmpty()) {
                    continue;
                }
                if (station.idleDrones() > 0) {
                    this.dispatchDrone(station, i, slot, now);
                }
                if (station.idleVessels() > 0 && station.kind().remote()) {
                    this.dispatchVessel(station, i, slot, now);
                }
            }
        }
    }

    private void dispatchDrone(Station home, int index, StationSlot slot, long now) {
        if (slot.local() == Logic.DEMAND && slot.need() > 0) {
            Candidate source = this.nearestDrone(home, slot.item(), Logic.SUPPLY);
            if (source != null) {
                int amount = Math.min(DRONE_CAPACITY, Math.min(slot.need(), source.slot.available()));
                if (amount >= Math.min(DRONE_MIN_LOAD, slot.need()) && this.payDrone(home, source.distance)) {
                    source.slot.reserveOutgoing(amount);
                    slot.reserveIncoming(amount);
                    this.launchDrone(home, index, source, amount, 0, false, now);
                }
            }
        }
        if (home.idleDrones() > 0 && slot.local() == Logic.SUPPLY && slot.available() > 0) {
            Candidate target = this.nearestDrone(home, slot.item(), Logic.DEMAND);
            if (target != null) {
                int amount = Math.min(DRONE_CAPACITY, Math.min(slot.available(), target.slot.need()));
                if (amount >= Math.min(DRONE_MIN_LOAD, target.slot.need()) && this.payDrone(home, target.distance)) {
                    int loaded = slot.take(amount);
                    target.slot.reserveIncoming(loaded);
                    this.launchDrone(home, index, target, loaded, loaded, true, now);
                }
            }
        }
    }

    private boolean payDrone(Station home, double distance) {
        return home.spend(droneEnergy(distance));
    }

    /** 派一架无人机要多少电。 */
    public static long droneEnergy(double distance) {
        return DRONE_ENERGY + Math.round(2 * distance * DRONE_ENERGY_PER_BLOCK);
    }

    /** 无人机飞一段要几刻。 */
    public static long droneLegTicks(double distance) {
        return Math.round((DRONE_TAKEOFF + distance / DRONE_SPEED) * 20);
    }

    private void launchDrone(Station home, int index, Candidate other, int amount, int cargo, boolean delivering, long now) {
        home.launch(Vehicle.DRONE);
        long leg = droneLegTicks(other.distance);
        this.revision++;
        this.trips.add(new Trip(Vehicle.DRONE, home.id(), other.station.id(), home.slot(index).item(), index, other.index, amount, cargo,
                delivering, now, now + leg, now + 2 * leg, false, false));
    }

    /** 同一维度里、在 {@code home} 的航程内、某种设置的同种物品格，最近的那个。 */
    private @Nullable Candidate nearestDrone(Station home, String item, Logic wanted) {
        Candidate best = null;
        for (Station other : this.stations.values()) {
            if (other == home || !other.dimension().equals(home.dimension()) || other.kind() == StationKind.COLLECTOR) {
                continue;
            }
            int index = other.slotOf(item);
            if (index < 0) {
                continue;
            }
            StationSlot slot = other.slot(index);
            if (slot.local() != wanted || (wanted == Logic.SUPPLY ? slot.available() : slot.need()) <= 0) {
                continue;
            }
            double distance = home.blocksTo(other);
            if (distance > home.droneRange()) {
                continue;
            }
            if (best == null || distance < best.distance) {
                best = new Candidate(other, index, slot, distance);
            }
        }
        return best;
    }

    private void dispatchVessel(Station home, int index, StationSlot slot, long now) {
        if (slot.remote() == Logic.DEMAND && slot.need() > 0) {
            Candidate source = this.nearestVessel(home, slot.item(), Logic.SUPPLY);
            if (source != null) {
                int amount = Math.min(VESSEL_CAPACITY, Math.min(slot.need(), source.slot.available()));
                if (amount >= Math.min(VESSEL_MIN_LOAD, slot.need()) && this.payVessel(home, source.station)) {
                    source.slot.reserveOutgoing(amount);
                    slot.reserveIncoming(amount);
                    this.launchVessel(home, index, source, amount, 0, false, now);
                }
            }
        }
        if (home.idleVessels() > 0 && slot.remote() == Logic.SUPPLY && slot.available() > 0) {
            Candidate target = this.nearestVessel(home, slot.item(), Logic.DEMAND);
            if (target != null) {
                int amount = Math.min(VESSEL_CAPACITY, Math.min(slot.available(), target.slot.need()));
                if (amount >= Math.min(VESSEL_MIN_LOAD, target.slot.need()) && this.payVessel(home, target.station)) {
                    int loaded = slot.take(amount);
                    target.slot.reserveIncoming(loaded);
                    this.launchVessel(home, index, target, loaded, loaded, true, now);
                }
            }
        }
    }

    /** 扣电和翘曲器；不够就不派。 */
    private boolean payVessel(Station home, Station other) {
        Location from = home.location();
        Location to = other.location();
        boolean warp = !from.sameSystem(to);
        if (warp && home.warpers() < WARPERS_PER_TRIP) {
            return false;
        }
        if (!home.spend(vesselEnergy(from, to))) {
            return false;
        }
        if (warp) {
            home.setWarpers(home.warpers() - WARPERS_PER_TRIP);
        }
        return true;
    }

    /** 派一艘运输船要多少电。 */
    public static long vesselEnergy(Location from, Location to) {
        if (!from.sameSystem(to)) {
            return VESSEL_ENERGY + WARP_ENERGY;
        }
        return VESSEL_ENERGY + Math.round(from.auTo(to) * VESSEL_ENERGY_PER_AU);
    }

    /** 运输船飞一段要几刻。 */
    public static long vesselLegTicks(Location from, Location to) {
        double seconds = from.sameSystem(to)
                ? VESSEL_BASE + from.auTo(to) * VESSEL_PER_AU
                : WARP_BASE + from.lightYearsTo(to) * WARP_PER_LY;
        return Math.round(seconds * 20);
    }

    private void launchVessel(Station home, int index, Candidate other, int amount, int cargo, boolean delivering, long now) {
        home.launch(Vehicle.VESSEL);
        long leg = vesselLegTicks(home.location(), other.station.location());
        boolean warp = !home.location().sameSystem(other.station.location());
        this.revision++;
        this.trips.add(new Trip(Vehicle.VESSEL, home.id(), other.station.id(), home.slot(index).item(), index, other.index, amount, cargo,
                delivering, now, now + leg, now + 2 * leg, false, warp));
    }

    /**
     * 别的星球上、能远程往来的站里某种设置的同种物品格，航程最短的那个。跨恒星系的只在自己有翘曲器时才考虑。
     */
    private @Nullable Candidate nearestVessel(Station home, String item, Logic wanted) {
        Candidate best = null;
        for (Station other : this.stations.values()) {
            if (other == home || !other.kind().remote() || other.location().sameBody(home.location())) {
                continue;
            }
            boolean warp = !other.location().sameSystem(home.location());
            if (warp && home.warpers() < WARPERS_PER_TRIP) {
                continue;
            }
            int index = other.slotOf(item);
            if (index < 0) {
                continue;
            }
            StationSlot slot = other.slot(index);
            if (slot.remote() != wanted || (wanted == Logic.SUPPLY ? slot.available() : slot.need()) <= 0) {
                continue;
            }
            double ticks = vesselLegTicks(home.location(), other.location());
            if (best == null || ticks < best.distance) {
                best = new Candidate(other, index, slot, ticks);
            }
        }
        return best;
    }

    /** 调度时找到的对方：哪座站的哪一格，距离（无人机是格数，运输船是一段的刻数）。 */
    private record Candidate(Station station, int index, StationSlot slot, double distance) {
    }
}
