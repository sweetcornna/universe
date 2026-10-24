package io.github.sweetcornna.universe.core.logistics;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
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
    // ---- 调度的设置（ADR-0033 §三）
    private String name = "";
    private int number;
    private int group;
    private final Set<UUID> partners = new LinkedHashSet<>();
    private boolean isolated;
    private int minLoad;

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

    /**
     * 给一格设定物品（空串是清掉）：同一种物品不能占两格，格子里有货或有在途的货时不能换，采集器的格子是固定的。
     * 站前的界面和控制面板都走这里。
     *
     * @return 改成了没有
     */
    public boolean assign(int index, String item) {
        if (index < 0 || index >= this.slots.length || this.kind == StationKind.COLLECTOR) {
            return false;
        }
        StationSlot slot = this.slots[index];
        if (slot.item().equals(item)) {
            return false;
        }
        if (!item.isEmpty() && this.slotOf(item) >= 0) {
            return false;
        }
        return slot.setItem(item);
    }

    // ---- 调度的设置

    /** 名字最长几个字，分组最大到几，最多几个点对点的伙伴。 */
    public static final int MAX_NAME = 32;
    public static final int MAX_GROUP = 9;
    public static final int MAX_PARTNERS = 16;
    /** 运输船最小载货量能选的百分数；0 是默认的 {@link LogisticsNetwork#VESSEL_MIN_LOAD} 个。 */
    public static final int[] MIN_LOADS = {0, 10, 25, 50, 100};

    /** 玩家起的名字；空的时候界面上显示"种类 + 编号"。 */
    public String name() {
        return this.name;
    }

    public void setName(String name) {
        String trimmed = name.strip();
        this.name = trimmed.length() > MAX_NAME ? trimmed.substring(0, MAX_NAME) : trimmed;
    }

    /** 同一种站里的编号，从 1 起；0 是还没编（加进账本时编上）。 */
    public int number() {
        return this.number;
    }

    public void setNumber(int number) {
        this.number = Math.max(0, number);
    }

    /** 分组：0 不分组，1–{@link #MAX_GROUP}。 */
    public int group() {
        return this.group;
    }

    public void setGroup(int group) {
        this.group = Math.max(0, Math.min(MAX_GROUP, group));
    }

    /** 点对点的伙伴。 */
    public Set<UUID> partners() {
        return Collections.unmodifiableSet(this.partners);
    }

    /** 加一个伙伴，返回加上了没有（不能是自己，不能超过 {@link #MAX_PARTNERS} 个）。 */
    public boolean addPartner(UUID partner) {
        if (partner.equals(this.id) || this.partners.size() >= MAX_PARTNERS) {
            return false;
        }
        return this.partners.add(partner);
    }

    public boolean removePartner(UUID partner) {
        return this.partners.remove(partner);
    }

    /** 和这座站是不是点对点的伙伴（任一方加了另一方就算）。 */
    public boolean partneredWith(Station other) {
        return this.partners.contains(other.id) || other.partners.contains(this.id);
    }

    /** 指定配对：只和伙伴、路线、同组的站往来，别的站普通配对时也不找它。 */
    public boolean isolated() {
        return this.isolated;
    }

    public void setIsolated(boolean isolated) {
        this.isolated = isolated;
    }

    /** 运输船最小载货量的百分数（{@link #MIN_LOADS} 里的一个）。 */
    public int minLoad() {
        return this.minLoad;
    }

    public void setMinLoad(int percent) {
        int best = 0;
        for (int option : MIN_LOADS) {
            if (Math.abs(option - percent) < Math.abs(best - percent)) {
                best = option;
            }
        }
        this.minLoad = best;
    }

    /** 换到下一档最小载货量。 */
    public void cycleMinLoad() {
        for (int i = 0; i < MIN_LOADS.length; i++) {
            if (MIN_LOADS[i] == this.minLoad) {
                this.minLoad = MIN_LOADS[(i + 1) % MIN_LOADS.length];
                return;
            }
        }
        this.minLoad = 0;
    }

    /** 这座站派出的运输船至少装几个才走（{@code capacity} 是一艘的容量）。 */
    public int vesselMinLoad(int capacity) {
        return this.minLoad == 0 ? LogisticsNetwork.VESSEL_MIN_LOAD : Math.max(1, (capacity * this.minLoad + 99) / 100);
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
        return this.insert(item, amount, 0);
    }

    /** 同上，这些物品一共带 {@code points} 点增产点数（ADR-0022）。 */
    public int insert(String item, int amount, long points) {
        int index = this.slotOf(item);
        return index < 0 ? 0 : this.slots[index].add(amount, points, Math.min(this.slots[index].max(), this.kind.slotCapacity()));
    }

    /** 直接改一格的数量：物品能力的事务回滚时用。 */
    public void restoreCount(int index, int count) {
        this.slots[index].setCount(Math.min(count, this.kind.slotCapacity()));
    }

    /** 直接改一格的点数：回滚时在 {@link #restoreCount} 之后调。 */
    public void restorePoints(int index, long points) {
        this.slots[index].setPoints(points);
    }

    /** 从一格里拿出去，不动别人预订的那部分，按比例带走点数。 */
    public int extract(int index, int amount) {
        return this.slots[index].take(amount);
    }

    /** 从一格里拿出去，每个带走 {@code level} 点。 */
    public int extract(int index, int amount, int level) {
        return this.slots[index].take(amount, level);
    }

    /** 直接设定一格的内容（拆了再放下时恢复）。 */
    public void restoreSlot(int index, StationSlot slot) {
        StationSlot target = this.slots[index];
        target.setItem("");
        target.setItem(slot.item());
        target.setCount(Math.min(slot.count(), this.kind.slotCapacity()));
        target.setPoints(slot.points());
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
