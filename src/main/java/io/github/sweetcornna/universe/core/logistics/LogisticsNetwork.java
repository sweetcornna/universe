package io.github.sweetcornna.universe.core.logistics;

import io.github.sweetcornna.universe.core.research.Upgrades;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
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
 *
 * <p>"最近"是同一层里的最近：配对先看点对点的伙伴、物品路线、同组，最后才是普通配对（{@link Tier}，ADR-0033 §三）。
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

        /** 星际运输船把货卸进了需求站：从哪个维度运到哪个维度、什么物品、卸下几个（进出口统计用）。 */
        default void shipped(String fromDimension, String toDimension, String item, int count) {
        }
    }

    private final Map<UUID, Station> stations = new LinkedHashMap<>();
    private ProductionListener listener = ProductionListener.NONE;
    /** 升级科技（ADR-0018）：载具速度的倍数、容量升到几级。服务端每刻按研究进度设，不存盘。 */
    private double speedFactor = 1;
    private int capacityLevel;
    private final List<Trip> trips = new ArrayList<>();
    /** 物品路线（ADR-0033 §三），全局的设置；按物品分好的索引在路线变了以后重建。 */
    private final List<ItemRoute> routes = new ArrayList<>();
    private @Nullable Map<String, List<ItemRoute>> routesByItem;
    /** 行程或站点每变一次加一，服务端据此判断要不要给玩家重发在飞的载具。 */
    private long revision;

    // ---- 账本

    /** 加进账本；还没编号的按种类编上下一个号。 */
    public void add(Station station) {
        if (station.number() == 0) {
            int last = 0;
            for (Station other : this.stations.values()) {
                if (other.kind() == station.kind()) {
                    last = Math.max(last, other.number());
                }
            }
            station.setNumber(last + 1);
        }
        this.stations.put(station.id(), station);
    }

    /** 拆掉一座站。在途的载具：家没了的，回来时消失；目的地没了的，原路返回。别的站的伙伴列表里也去掉它。 */
    public @Nullable Station remove(UUID id) {
        this.revision++;
        Station removed = this.stations.remove(id);
        if (removed != null) {
            for (Station other : this.stations.values()) {
                other.removePartner(id);
            }
        }
        return removed;
    }

    // ---- 物品路线

    public List<ItemRoute> routes() {
        return Collections.unmodifiableList(this.routes);
    }

    public @Nullable ItemRoute route(UUID id) {
        for (ItemRoute route : this.routes) {
            if (route.id().equals(id)) {
                return route;
            }
        }
        return null;
    }

    /** 加一条路线；两头一样、和已有的重复、或者满了 {@link ItemRoute#MAX} 条时不加。 */
    public boolean addRoute(ItemRoute route) {
        if (!route.valid() || this.routes.size() >= ItemRoute.MAX) {
            return false;
        }
        for (ItemRoute existing : this.routes) {
            if (existing.sameAs(route) || existing.id().equals(route.id())) {
                return false;
            }
        }
        this.routes.add(route);
        this.routesByItem = null;
        return true;
    }

    public boolean removeRoute(UUID id) {
        this.routesByItem = null;
        return this.routes.removeIf(route -> route.id().equals(id));
    }

    private List<ItemRoute> routesFor(String item) {
        if (this.routesByItem == null) {
            Map<String, List<ItemRoute>> index = new HashMap<>();
            for (ItemRoute route : this.routes) {
                index.computeIfAbsent(route.item(), key -> new ArrayList<>()).add(route);
            }
            this.routesByItem = index;
        }
        return this.routesByItem.getOrDefault(item, List.of());
    }

    /** 在这条路线两头的站：出发的一头这种物品设成远程供应，到达的一头设成远程需求。 */
    public List<Station> stationsOn(ItemRoute route) {
        List<Station> result = new ArrayList<>();
        for (Station station : this.stations.values()) {
            int index = station.slotOf(route.item());
            if (index < 0 || !station.kind().remote()) {
                continue;
            }
            Logic remote = station.slot(index).remote();
            if (remote == Logic.SUPPLY && route.startsAt(station.location()) || remote == Logic.DEMAND && route.endsAt(station.location())) {
                result.add(station);
            }
        }
        return result;
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
            long points = trip.points();
            if (slot != null) {
                slot.releaseIncoming(trip.amount());
                int added = slot.add(left, points, other.kind().slotCapacity());
                points -= StationSlot.share(points, added, left);
                left -= added;
                if (added > 0 && trip.vehicle() == Vehicle.VESSEL && home != null) {
                    this.listener.shipped(home.dimension(), other.dimension(), trip.item(), added);
                }
            }
            return trip.arrived(left, points);
        }
        if (slot == null) {
            return trip.arrived(0, 0);
        }
        if (home == null) {
            // 家已经没了：不拿了，把预订放掉
            slot.releaseOutgoing(trip.amount());
            return trip.arrived(0, 0);
        }
        long before = slot.points();
        int taken = slot.pickUp(trip.amount());
        return trip.arrived(taken, before - slot.points());
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
            int added = slot.add(trip.cargo(), trip.points(), home.kind().slotCapacity());
            Station other = this.stations.get(trip.other());
            if (added > 0 && !trip.delivering() && trip.vehicle() == Vehicle.VESSEL && other != null) {
                this.listener.shipped(other.dimension(), home.dimension(), trip.item(), added);
            }
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
    //
    // 每轮先按物品把所有的格子排好，再给每格算一个"锁定的层次"：它在供需双方都能往来的对象里，最高能配到哪一层。
    // 找配对时按层次从高到低、同一层按距离挑；对方锁在更高的层次上（那一层有对象等着）就让开，不去抢它；自己更高
    // 一层有对象时也不往下找。对方没货了、满了，锁就落下来，下一轮低一层的就能配上（ADR-0033 §三）。

    private void dispatch(long now) {
        Map<String, List<Entry>> byItem = new HashMap<>();
        List<Entry> entries = new ArrayList<>();
        for (Station station : this.stations.values()) {
            for (int i = 0; i < station.kind().slots(); i++) {
                StationSlot slot = station.slot(i);
                if (!slot.isEmpty()) {
                    Entry entry = new Entry(station, i, slot);
                    entries.add(entry);
                    byItem.computeIfAbsent(slot.item(), item -> new ArrayList<>()).add(entry);
                }
            }
        }
        for (Entry entry : entries) {
            List<Entry> same = byItem.get(entry.slot.item());
            entry.localLock = this.lock(entry, same, false);
            entry.remoteLock = this.lock(entry, same, true);
        }
        for (Entry entry : entries) {
            Station station = entry.station;
            List<Entry> same = byItem.get(entry.slot.item());
            if (station.idleDrones() > 0) {
                this.dispatchDrone(entry, same, now);
            }
            if (station.idleVessels() > 0 && station.kind().remote()) {
                this.dispatchVessel(entry, same, now);
            }
        }
    }

    /** 这一格在本地（无人机）或远程（运输船）这一边，最高能配到哪一层；没有能往来的对象时是 {@code null}。 */
    private @Nullable Tier lock(Entry entry, List<Entry> same, boolean remote) {
        Logic logic = remote ? entry.slot.remote() : entry.slot.local();
        boolean supply = logic == Logic.SUPPLY && entry.slot.available() > 0;
        if (!supply && !(logic == Logic.DEMAND && entry.slot.need() > 0)) {
            return null;
        }
        Logic wanted = supply ? Logic.DEMAND : Logic.SUPPLY;
        Tier best = null;
        for (Entry other : same) {
            if (other.station == entry.station || (remote ? other.slot.remote() : other.slot.local()) != wanted
                    || (supply ? other.slot.need() : other.slot.available()) <= 0) {
                continue;
            }
            Station from = supply ? entry.station : other.station;
            Station to = supply ? other.station : entry.station;
            if (!(remote ? vesselReachable(from, to) : droneReachable(from, to))) {
                continue;
            }
            best = Tier.best(best, this.tier(from, to, entry.slot.item(), remote));
            if (best == Tier.POINT_TO_POINT) {
                break;
            }
        }
        return best;
    }

    /** 两座站之间有一方能派无人机过去：同一维度、都不是采集器、在派的一方的航程内。 */
    private static boolean droneReachable(Station a, Station b) {
        if (!a.dimension().equals(b.dimension()) || a.kind() == StationKind.COLLECTOR || b.kind() == StationKind.COLLECTOR) {
            return false;
        }
        double distance = a.blocksTo(b);
        return a.drones() > 0 && distance <= a.droneRange() || b.drones() > 0 && distance <= b.droneRange();
    }

    /** 两座站之间有一方能派运输船过去：都能远程往来、不在同一颗星球上，跨恒星系的一方要有翘曲器。 */
    private static boolean vesselReachable(Station a, Station b) {
        if (!a.kind().remote() || !b.kind().remote() || a.location().sameBody(b.location())) {
            return false;
        }
        return canSend(a, b) || canSend(b, a);
    }

    private static boolean canSend(Station from, Station to) {
        return from.vessels() > 0 && (from.location().sameSystem(to.location()) || from.warpers() >= WARPERS_PER_TRIP);
    }

    /**
     * 供方把这种物品给需方算哪一层（ADR-0033 §三）：点对点的伙伴、星球路线、星系路线（只算运输船）、同组、普通配对。
     *
     * @return {@code null} 是不能配：有一方开了指定配对，又不在前四层里
     */
    public @Nullable Tier tier(Station supply, Station demand, String item, boolean remote) {
        if (supply.partneredWith(demand)) {
            return Tier.POINT_TO_POINT;
        }
        if (remote) {
            Tier route = null;
            for (ItemRoute candidate : this.routesFor(item)) {
                if (candidate.matches(item, supply.location(), demand.location())) {
                    route = Tier.best(route, candidate.systems() ? Tier.SYSTEM_ROUTE : Tier.PLANET_ROUTE);
                }
            }
            if (route != null) {
                return route;
            }
        }
        if (supply.group() != 0 && supply.group() == demand.group()) {
            return Tier.GROUP;
        }
        return supply.isolated() || demand.isolated() ? null : Tier.NORMAL;
    }

    private void dispatchDrone(Entry entry, List<Entry> same, long now) {
        Station home = entry.station;
        StationSlot slot = entry.slot;
        int index = entry.index;
        if (slot.local() == Logic.DEMAND && slot.need() > 0) {
            Candidate source = this.pick(entry, same, false, Logic.SUPPLY);
            if (source != null) {
                int amount = Math.min(this.droneCapacity(), Math.min(slot.need(), source.slot.available()));
                if (amount >= Math.min(DRONE_MIN_LOAD, slot.need()) && this.payDrone(home, source.distance)) {
                    source.slot.reserveOutgoing(amount);
                    slot.reserveIncoming(amount);
                    this.launchDrone(home, index, source, amount, 0, 0, false, now);
                }
            }
        }
        if (home.idleDrones() > 0 && slot.local() == Logic.SUPPLY && slot.available() > 0) {
            Candidate target = this.pick(entry, same, false, Logic.DEMAND);
            if (target != null) {
                int amount = Math.min(this.droneCapacity(), Math.min(slot.available(), target.slot.need()));
                if (amount >= Math.min(DRONE_MIN_LOAD, target.slot.need()) && this.payDrone(home, target.distance)) {
                    long before = slot.points();
                    int loaded = slot.take(amount);
                    target.slot.reserveIncoming(loaded);
                    this.launchDrone(home, index, target, loaded, loaded, before - slot.points(), true, now);
                }
            }
        }
    }

    /**
     * 给这一格挑对方，{@code wanted} 是对方要设成什么（供应或需求）。层次高的先，同一层里近的先；对方锁在更高的层次上
     * 就跳过（让路），比自己锁定的层次低的也不要。无人机看自己的航程，距离是格数；运输船跨恒星系要自己有翘曲器，距离是
     * 一段的刻数。
     */
    private @Nullable Candidate pick(Entry entry, List<Entry> same, boolean remote, Logic wanted) {
        Station home = entry.station;
        String item = entry.slot.item();
        Tier own = remote ? entry.remoteLock : entry.localLock;
        Candidate best = null;
        Tier bestTier = null;
        for (Entry other : same) {
            Station station = other.station;
            if (station == home) {
                continue;
            }
            double distance;
            if (remote) {
                if (!station.kind().remote() || station.location().sameBody(home.location())
                        || !station.location().sameSystem(home.location()) && home.warpers() < WARPERS_PER_TRIP) {
                    continue;
                }
                distance = vesselLegTicks(home.location(), station.location());
            } else {
                if (!station.dimension().equals(home.dimension()) || station.kind() == StationKind.COLLECTOR) {
                    continue;
                }
                distance = home.blocksTo(station);
                if (distance > home.droneRange()) {
                    continue;
                }
            }
            StationSlot slot = other.slot;
            if ((remote ? slot.remote() : slot.local()) != wanted || (wanted == Logic.SUPPLY ? slot.available() : slot.need()) <= 0) {
                continue;
            }
            Tier tier = wanted == Logic.SUPPLY ? this.tier(station, home, item, remote) : this.tier(home, station, item, remote);
            if (tier == null || tier.below(remote ? other.remoteLock : other.localLock) || tier.below(own)) {
                continue;
            }
            if (best == null || tier.ordinal() < bestTier.ordinal() || tier == bestTier && distance < best.distance) {
                best = new Candidate(station, other.index, slot, distance);
                bestTier = tier;
            }
        }
        return best;
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
        return droneLegTicks(distance, 1);
    }

    /** 无人机飞一段要几刻，飞行速度乘 speed（起降的时间不变）。 */
    public static long droneLegTicks(double distance, double speed) {
        return Math.round((DRONE_TAKEOFF + distance / (DRONE_SPEED * speed)) * 20);
    }

    /** 设升级科技的加成：载具速度的倍数、载具容量的级数。 */
    public void setUpgrades(double speedFactor, int capacityLevel) {
        this.speedFactor = speedFactor;
        this.capacityLevel = capacityLevel;
    }

    public int droneCapacity() {
        return Upgrades.carrierCapacity(DRONE_CAPACITY, this.capacityLevel);
    }

    public int vesselCapacity() {
        return Upgrades.carrierCapacity(VESSEL_CAPACITY, this.capacityLevel);
    }

    private void launchDrone(Station home, int index, Candidate other, int amount, int cargo, long points, boolean delivering, long now) {
        home.launch(Vehicle.DRONE);
        long leg = droneLegTicks(other.distance, this.speedFactor);
        this.revision++;
        this.trips.add(new Trip(Vehicle.DRONE, home.id(), other.station.id(), home.slot(index).item(), index, other.index, amount, cargo,
                delivering, now, now + leg, now + 2 * leg, false, false, points));
    }

    private void dispatchVessel(Entry entry, List<Entry> same, long now) {
        Station home = entry.station;
        StationSlot slot = entry.slot;
        int index = entry.index;
        int minLoad = home.vesselMinLoad(this.vesselCapacity());
        if (slot.remote() == Logic.DEMAND && slot.need() > 0) {
            Candidate source = this.pick(entry, same, true, Logic.SUPPLY);
            if (source != null) {
                int amount = Math.min(this.vesselCapacity(), Math.min(slot.need(), source.slot.available()));
                if (amount >= Math.min(minLoad, slot.need()) && this.payVessel(home, source.station)) {
                    source.slot.reserveOutgoing(amount);
                    slot.reserveIncoming(amount);
                    this.launchVessel(home, index, source, amount, 0, 0, false, now);
                }
            }
        }
        if (home.idleVessels() > 0 && slot.remote() == Logic.SUPPLY && slot.available() > 0) {
            Candidate target = this.pick(entry, same, true, Logic.DEMAND);
            if (target != null) {
                int amount = Math.min(this.vesselCapacity(), Math.min(slot.available(), target.slot.need()));
                if (amount >= Math.min(minLoad, target.slot.need()) && this.payVessel(home, target.station)) {
                    long before = slot.points();
                    int loaded = slot.take(amount);
                    target.slot.reserveIncoming(loaded);
                    this.launchVessel(home, index, target, loaded, loaded, before - slot.points(), true, now);
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
        return vesselLegTicks(from, to, 1);
    }

    /** 运输船飞一段要几刻，航行的部分除以 speed（起降的时间不变）。 */
    public static long vesselLegTicks(Location from, Location to, double speed) {
        double seconds = from.sameSystem(to)
                ? VESSEL_BASE + from.auTo(to) * VESSEL_PER_AU / speed
                : WARP_BASE + from.lightYearsTo(to) * WARP_PER_LY / speed;
        return Math.round(seconds * 20);
    }

    private void launchVessel(Station home, int index, Candidate other, int amount, int cargo, long points, boolean delivering, long now) {
        home.launch(Vehicle.VESSEL);
        long leg = vesselLegTicks(home.location(), other.station.location(), this.speedFactor);
        boolean warp = !home.location().sameSystem(other.station.location());
        this.revision++;
        this.trips.add(new Trip(Vehicle.VESSEL, home.id(), other.station.id(), home.slot(index).item(), index, other.index, amount, cargo,
                delivering, now, now + leg, now + 2 * leg, false, warp, points));
    }

    /** 调度时找到的对方：哪座站的哪一格，距离（无人机是格数，运输船是一段的刻数）。 */
    private record Candidate(Station station, int index, StationSlot slot, double distance) {
    }

    /** 一轮调度里的一格，带着它在两边锁定的层次。 */
    private static final class Entry {
        final Station station;
        final int index;
        final StationSlot slot;
        @Nullable Tier localLock;
        @Nullable Tier remoteLock;

        Entry(Station station, int index, StationSlot slot) {
            this.station = station;
            this.index = index;
            this.slot = slot;
        }
    }
}
