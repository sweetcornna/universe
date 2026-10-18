package io.github.sweetcornna.universe.core.logistics;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * 账本里的一座物流站（ADR-0015）。方块实体只存 {@link #id()}，读写都走这里，所以站所在的星球卸载了，账本还在，
 * 别的星球的运输船照样能来装卸。
 */
public final class Station {
    /** 无人机航程的范围（格）。 */
    public static final int MIN_RANGE = 100;
    public static final int MAX_RANGE = 4000;
    public static final int DEFAULT_RANGE = 1000;

    private final UUID id;
    private final StationKind kind;
    private final String dimension;
    private final int x;
    private final int y;
    private final int z;
    private final Location location;
    private final StationSlot[] slots;
    private int drones;
    private int idleDrones;
    private int vessels;
    private int idleVessels;
    private int warpers;
    private long energy;
    private int droneRange = DEFAULT_RANGE;
    /** 轨道采集器每格每秒产出多少，以及攒着的零头。 */
    private final double[] production;
    private final double[] accumulated;

    public Station(UUID id, StationKind kind, String dimension, int x, int y, int z, Location location) {
        this.id = id;
        this.kind = kind;
        this.dimension = dimension;
        this.x = x;
        this.y = y;
        this.z = z;
        this.location = location;
        this.slots = new StationSlot[kind.slots()];
        for (int i = 0; i < this.slots.length; i++) {
            this.slots[i] = new StationSlot(kind.slotCapacity());
        }
        this.production = new double[kind.slots()];
        this.accumulated = new double[kind.slots()];
    }

    public UUID id() {
        return this.id;
    }

    public StationKind kind() {
        return this.kind;
    }

    public String dimension() {
        return this.dimension;
    }

    public int x() {
        return this.x;
    }

    public int y() {
        return this.y;
    }

    public int z() {
        return this.z;
    }

    public Location location() {
        return this.location;
    }

    public List<StationSlot> slots() {
        return Arrays.asList(this.slots);
    }

    public StationSlot slot(int index) {
        return this.slots[index];
    }

    /** 放这种物品的格子；没有时返回 -1。 */
    public int slotOf(String item) {
        for (int i = 0; i < this.slots.length; i++) {
            if (!this.slots[i].isEmpty() && this.slots[i].item().equals(item)) {
                return i;
            }
        }
        return -1;
    }

    // ---- 载具

    public int drones() {
        return this.drones;
    }

    public int idleDrones() {
        return this.idleDrones;
    }

    public int vessels() {
        return this.vessels;
    }

    public int idleVessels() {
        return this.idleVessels;
    }

    /**
     * 放进去几架无人机（不超过上限）。
     *
     * @return 实际放了几架
     */
    public int addDrones(int count) {
        int added = Math.max(0, Math.min(count, this.kind.maxDrones() - this.drones));
        this.drones += added;
        this.idleDrones += added;
        return added;
    }

    /** 拿出几架空闲的无人机，返回实际拿了几架。在外面飞的拿不出来。 */
    public int removeDrones(int count) {
        int removed = Math.max(0, Math.min(count, this.idleDrones));
        this.drones -= removed;
        this.idleDrones -= removed;
        return removed;
    }

    public int addVessels(int count) {
        int added = Math.max(0, Math.min(count, this.kind.maxVessels() - this.vessels));
        this.vessels += added;
        this.idleVessels += added;
        return added;
    }

    public int removeVessels(int count) {
        int removed = Math.max(0, Math.min(count, this.idleVessels));
        this.vessels -= removed;
        this.idleVessels -= removed;
        return removed;
    }

    void launch(Vehicle vehicle) {
        if (vehicle == Vehicle.DRONE) {
            this.idleDrones--;
        } else {
            this.idleVessels--;
        }
    }

    void land(Vehicle vehicle) {
        if (vehicle == Vehicle.DRONE) {
            this.idleDrones = Math.min(this.drones, this.idleDrones + 1);
        } else {
            this.idleVessels = Math.min(this.vessels, this.idleVessels + 1);
        }
    }

    /** 读档时恢复载具数（在外面飞的由行程恢复，这里直接记）。 */
    public void restoreVehicles(int drones, int idleDrones, int vessels, int idleVessels) {
        this.drones = Math.max(0, Math.min(drones, this.kind.maxDrones()));
        this.idleDrones = Math.max(0, Math.min(idleDrones, this.drones));
        this.vessels = Math.max(0, Math.min(vessels, this.kind.maxVessels()));
        this.idleVessels = Math.max(0, Math.min(idleVessels, this.vessels));
    }

    // ---- 翘曲器、电、航程

    /** 翘曲器格最多放多少个。 */
    public static final int MAX_WARPERS = 50;

    public int warpers() {
        return this.warpers;
    }

    public void setWarpers(int warpers) {
        this.warpers = Math.max(0, Math.min(MAX_WARPERS, warpers));
    }

    public long energy() {
        return this.energy;
    }

    public void setEnergy(long energy) {
        this.energy = Math.max(0, Math.min(this.kind.battery(), energy));
    }

    /** 充电，返回实际充进去多少。 */
    public long charge(long joules) {
        long added = Math.max(0, Math.min(joules, this.kind.battery() - this.energy));
        this.energy += added;
        return added;
    }

    /** 这一刻想从电网要多少电：电池越空要得越多，最多 {@link StationKind#maxCharge()}。 */
    public long chargeDemand() {
        long missing = this.kind.battery() - this.energy;
        return Math.max(0, Math.min(this.kind.maxCharge(), missing));
    }

    boolean spend(long joules) {
        if (this.energy < joules) {
            return false;
        }
        this.energy -= joules;
        return true;
    }

    public int droneRange() {
        return this.droneRange;
    }

    public void setDroneRange(int range) {
        this.droneRange = Math.max(MIN_RANGE, Math.min(MAX_RANGE, range));
    }

    // ---- 存取口：传送带、分拣器、玩家手动

    /**
     * 往这种物品的格子里放（不超过这一格设的上限）。
     *
     * @return 实际放了几个；没有这种物品的格子时是 0
     */
    public int insert(String item, int amount) {
        int index = this.slotOf(item);
        return index < 0 ? 0 : this.slots[index].add(amount, Math.min(this.slots[index].max(), this.kind.slotCapacity()));
    }

    /** 直接改一格的数量：物品能力的事务回滚时用。 */
    public void restoreCount(int index, int count) {
        this.slots[index].setCount(Math.min(count, this.kind.slotCapacity()));
    }

    /** 从一格里拿出去，不动别人预订的那部分。 */
    public int extract(int index, int amount) {
        return this.slots[index].take(amount);
    }

    /** 直接设定一格的内容（拆了再放下时恢复）。 */
    public void restoreSlot(int index, StationSlot slot) {
        StationSlot target = this.slots[index];
        target.setItem("");
        target.setItem(slot.item());
        target.setCount(Math.min(slot.count(), this.kind.slotCapacity()));
        target.setMax(Math.min(slot.max(), this.kind.slotCapacity()));
        target.setLocal(slot.local());
        target.setRemote(this.kind.remote() ? slot.remote() : Logic.STORAGE);
        target.reserveIncoming(slot.incoming());
        target.reserveOutgoing(slot.outgoing());
    }

    // ---- 轨道采集器

    public void setProduction(int index, double perSecond) {
        this.production[index] = Math.max(0, perSecond);
    }

    public double production(int index) {
        return this.production[index];
    }

    public double accumulated(int index) {
        return this.accumulated[index];
    }

    public void setAccumulated(int index, double value) {
        this.accumulated[index] = Math.max(0, value);
    }

    /** 按经过的时间产出（只有采集器有产量）。 */
    void produce(double seconds, LogisticsNetwork.ProductionListener listener) {
        for (int i = 0; i < this.slots.length; i++) {
            if (this.production[i] <= 0 || this.slots[i].isEmpty()) {
                continue;
            }
            this.accumulated[i] += this.production[i] * seconds;
            int whole = (int) this.accumulated[i];
            if (whole > 0) {
                this.accumulated[i] -= whole;
                int added = this.slots[i].add(whole, this.kind.slotCapacity());
                if (added > 0) {
                    listener.produced(this, this.slots[i].item(), added);
                }
            }
        }
    }

    /** 和另一座站的直线距离（格，只在同一维度里有意义）。 */
    public double blocksTo(Station other) {
        double dx = this.x - other.x;
        double dy = this.y - other.y;
        double dz = this.z - other.z;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}
