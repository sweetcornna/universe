package io.github.sweetcornna.universe.core.power;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jspecify.annotations.Nullable;

/**
 * 覆盖式电网（技术架构 §3.7、玩法设计 §9.4）：电力感应塔在连接距离内自动连成一个电网，塔的覆盖范围内的设备
 * 自动接入，不用拉线。每刻每个电网结算一次：发电、用电、蓄电器充放电和满足率。
 *
 * <p>加塔时把它碰到的几个电网并成一个；拆塔时只在它原来的电网里重新找连通的部分，必要时拆成几个。设备归离它
 * 最近的一座覆盖它的塔，那座塔拆了就换一座，没有塔覆盖时断电。塔和设备都按格子建空间索引，增删只看附近。
 */
public final class PowerSystem {
    /** 空间索引的格子边长。 */
    private static final int CELL = 16;

    /**
     * 一种塔的规格。
     *
     * @param connectRange 和别的塔相连的距离（两座塔取大的那个）
     * @param coverRange   覆盖半径：水平方向是圆，上下各这么高
     */
    public record TowerSpec(int connectRange, int coverRange) {
    }

    private static final class Tower {
        final long id;
        final int x;
        final int y;
        final int z;
        final TowerSpec spec;
        final List<Device> devices = new ArrayList<>();
        Grid grid;

        Tower(long id, int x, int y, int z, TowerSpec spec) {
            this.id = id;
            this.x = x;
            this.y = y;
            this.z = z;
            this.spec = spec;
        }
    }

    private static final class Device {
        final long id;
        final int x;
        final int y;
        final int z;
        final PowerDevice device;
        @Nullable Tower tower;
        // 这一刻从设备读到的数，结算时不再重复问
        long demand;
        long generation;
        long chargeLimit;
        long dischargeLimit;

        Device(long id, int x, int y, int z, PowerDevice device) {
            this.id = id;
            this.x = x;
            this.y = y;
            this.z = z;
            this.device = device;
        }
    }

    /** 一个电网：连在一起的塔和它们覆盖的设备，以及上一刻的结算结果。 */
    public static final class Grid {
        private final List<Tower> towers = new ArrayList<>();
        private final List<Device> devices = new ArrayList<>();
        private long demand;
        private long generation;
        private long supplied;
        private long charged;
        private long discharged;
        private double satisfaction = 1;

        public int towerCount() {
            return this.towers.size();
        }

        public int deviceCount() {
            return this.devices.size();
        }

        /** 用电设备要的总量。 */
        public long demand() {
            return this.demand;
        }

        /** 发电机最多能发的总量。 */
        public long generation() {
            return this.generation;
        }

        /** 实际送到用电设备的总量。 */
        public long supplied() {
            return this.supplied;
        }

        /** 充进蓄电器的总量。 */
        public long charged() {
            return this.charged;
        }

        /** 蓄电器放出的总量。 */
        public long discharged() {
            return this.discharged;
        }

        public double satisfaction() {
            return this.satisfaction;
        }

        /** 离 (x, z) 最近的设备的编号（没有设备时看塔），电网是空的返回 {@link Long#MIN_VALUE}。黑雾找进攻目标用（ADR-0028 §三）。 */
        public long nearest(double x, double z) {
            long best = Long.MIN_VALUE;
            double bestDistance = Double.MAX_VALUE;
            for (Device device : this.devices) {
                double d = (device.x - x) * (device.x - x) + (device.z - z) * (device.z - z);
                if (d < bestDistance) {
                    bestDistance = d;
                    best = device.id;
                }
            }
            if (best == Long.MIN_VALUE) {
                for (Tower tower : this.towers) {
                    double d = (tower.x - x) * (tower.x - x) + (tower.z - z) * (tower.z - z);
                    if (d < bestDistance) {
                        bestDistance = d;
                        best = tower.id;
                    }
                }
            }
            return best;
        }
    }

    private final Map<Long, Tower> towers = new HashMap<>();
    private final Map<Long, Device> devices = new HashMap<>();
    private final Map<Long, List<Tower>> towerCells = new HashMap<>();
    private final Map<Long, List<Device>> deviceCells = new HashMap<>();
    private final List<Grid> grids = new ArrayList<>();
    private final Set<Device> unconnected = new LinkedHashSet<>();
    private int maxConnect;
    private int maxCover;

    // ---- 塔

    public void addTower(long id, int x, int y, int z, TowerSpec spec) {
        this.removeTower(id);
        Tower tower = new Tower(id, x, y, z, spec);
        this.towers.put(id, tower);
        cellList(this.towerCells, x, y, z).add(tower);
        this.maxConnect = Math.max(this.maxConnect, spec.connectRange());
        this.maxCover = Math.max(this.maxCover, spec.coverRange());

        // 碰到的电网并成一个，并进最大的那个
        Map<Grid, Boolean> touched = new IdentityHashMap<>();
        for (Tower other : this.connectedTo(tower)) {
            touched.put(other.grid, Boolean.TRUE);
        }
        Grid target = null;
        for (Grid grid : touched.keySet()) {
            if (target == null || grid.towers.size() > target.towers.size()) {
                target = grid;
            }
        }
        if (target == null) {
            target = new Grid();
            this.grids.add(target);
        }
        for (Grid grid : touched.keySet()) {
            if (grid != target) {
                this.merge(grid, target);
            }
        }
        target.towers.add(tower);
        tower.grid = target;

        // 覆盖范围里还没接上的设备接到这座塔上
        int r = spec.coverRange();
        for (Device device : near(this.deviceCells, x, y, z, r)) {
            if (device.tower == null && covers(tower, device)) {
                this.attach(device, tower);
            }
        }
    }

    public void removeTower(long id) {
        Tower tower = this.towers.remove(id);
        if (tower == null) {
            return;
        }
        cellList(this.towerCells, tower.x, tower.y, tower.z).remove(tower);
        Grid grid = tower.grid;
        List<Device> orphans = new ArrayList<>(tower.devices);
        for (Device device : orphans) {
            this.detach(device);
        }
        grid.towers.remove(tower);
        this.split(grid);
        for (Device device : orphans) {
            Tower other = this.coveringTower(device);
            if (other != null) {
                this.attach(device, other);
            }
        }
    }

    public boolean hasTower(long id) {
        return this.towers.containsKey(id);
    }

    // ---- 设备

    public void addDevice(long id, int x, int y, int z, PowerDevice device) {
        this.removeDevice(id);
        Device node = new Device(id, x, y, z, device);
        this.devices.put(id, node);
        cellList(this.deviceCells, x, y, z).add(node);
        Tower tower = this.coveringTower(node);
        if (tower != null) {
            this.attach(node, tower);
        } else {
            this.unconnected.add(node);
        }
    }

    public void removeDevice(long id) {
        Device device = this.devices.remove(id);
        if (device == null) {
            return;
        }
        cellList(this.deviceCells, device.x, device.y, device.z).remove(device);
        if (device.tower != null) {
            this.detach(device);
        }
        this.unconnected.remove(device);
    }

    /** 设备所在的电网；没有塔覆盖时为空。 */
    public @Nullable Grid gridOf(long deviceId) {
        Device device = this.devices.get(deviceId);
        return device == null || device.tower == null ? null : device.tower.grid;
    }

    /** 塔所在的电网。 */
    public @Nullable Grid gridOfTower(long towerId) {
        Tower tower = this.towers.get(towerId);
        return tower == null ? null : tower.grid;
    }

    public List<Grid> grids() {
        return Collections.unmodifiableList(this.grids);
    }

    // ---- 结算

    public void tick() {
        for (Grid grid : this.grids) {
            settle(grid);
        }
        // 没接上电网的设备也通知一声，好让它们停下来
        for (Device device : this.unconnected) {
            device.device.settle(device.device.demand() > 0 ? 0 : 1, 0, 0);
        }
    }

    private static void settle(Grid grid) {
        long demand = 0;
        long generation = 0;
        long chargeRoom = 0;
        long dischargeRoom = 0;
        for (Device device : grid.devices) {
            PowerDevice p = device.device;
            device.demand = Math.max(0, p.demand());
            device.generation = Math.max(0, p.generation());
            device.chargeLimit = Math.max(0, p.chargeLimit());
            device.dischargeLimit = Math.max(0, p.dischargeLimit());
            demand += device.demand;
            generation += device.generation;
            chargeRoom += device.chargeLimit;
            dischargeRoom += device.dischargeLimit;
        }
        double satisfaction;
        double load;
        long charged = 0;
        long discharged = 0;
        if (generation >= demand) {
            // 发电够用：多出来的充进蓄电器，发电机只发需要的那么多
            satisfaction = 1;
            charged = Math.min(generation - demand, chargeRoom);
            load = generation == 0 ? 0 : (double) (demand + charged) / generation;
        } else {
            // 不够：蓄电器补上，还不够就所有设备按满足率降速
            discharged = Math.min(demand - generation, dischargeRoom);
            load = 1;
            satisfaction = (double) (generation + discharged) / demand;
        }
        double chargeShare = chargeRoom == 0 ? 0 : (double) charged / chargeRoom;
        double dischargeShare = dischargeRoom == 0 ? 0 : (double) discharged / dischargeRoom;
        for (Device device : grid.devices) {
            long stored = Math.round(device.chargeLimit * chargeShare) - Math.round(device.dischargeLimit * dischargeShare);
            device.device.settle(satisfaction, device.generation > 0 ? load : 0, stored);
        }
        grid.demand = demand;
        grid.generation = generation;
        grid.supplied = Math.min(demand, generation + discharged);
        grid.charged = charged;
        grid.discharged = discharged;
        grid.satisfaction = satisfaction;
    }

    // ---- 连通性

    private void attach(Device device, Tower tower) {
        this.unconnected.remove(device);
        device.tower = tower;
        tower.devices.add(device);
        tower.grid.devices.add(device);
    }

    private void detach(Device device) {
        Tower tower = device.tower;
        if (tower != null) {
            tower.devices.remove(device);
            tower.grid.devices.remove(device);
            device.tower = null;
        }
        this.unconnected.add(device);
    }

    private void merge(Grid from, Grid into) {
        for (Tower tower : from.towers) {
            tower.grid = into;
        }
        into.towers.addAll(from.towers);
        into.devices.addAll(from.devices);
        this.grids.remove(from);
    }

    /** 拆塔以后在原来的电网里重新找连通的部分，多于一块就拆开（最大的一块留在原来的电网里）。 */
    private void split(Grid grid) {
        if (grid.towers.isEmpty()) {
            this.grids.remove(grid);
            return;
        }
        Map<Tower, Boolean> seen = new IdentityHashMap<>();
        List<List<Tower>> parts = new ArrayList<>();
        ArrayDeque<Tower> queue = new ArrayDeque<>();
        for (Tower start : grid.towers) {
            if (seen.containsKey(start)) {
                continue;
            }
            List<Tower> part = new ArrayList<>();
            seen.put(start, Boolean.TRUE);
            queue.add(start);
            while (!queue.isEmpty()) {
                Tower current = queue.poll();
                part.add(current);
                for (Tower next : this.connectedTo(current)) {
                    if (next.grid == grid && !seen.containsKey(next)) {
                        seen.put(next, Boolean.TRUE);
                        queue.add(next);
                    }
                }
            }
            parts.add(part);
        }
        if (parts.size() == 1) {
            return;
        }
        parts.sort(Comparator.comparingInt(List::size));
        Collections.reverse(parts);
        grid.towers.clear();
        grid.devices.clear();
        for (int i = 0; i < parts.size(); i++) {
            Grid target = i == 0 ? grid : new Grid();
            if (i > 0) {
                this.grids.add(target);
            }
            for (Tower tower : parts.get(i)) {
                tower.grid = target;
                target.towers.add(tower);
                target.devices.addAll(tower.devices);
            }
        }
    }

    private List<Tower> connectedTo(Tower tower) {
        List<Tower> out = new ArrayList<>();
        int range = Math.max(tower.spec.connectRange(), this.maxConnect);
        for (Tower other : near(this.towerCells, tower.x, tower.y, tower.z, range)) {
            if (other != tower && connects(tower, other)) {
                out.add(other);
            }
        }
        return out;
    }

    private @Nullable Tower coveringTower(Device device) {
        Tower best = null;
        long bestDistance = Long.MAX_VALUE;
        for (Tower tower : near(this.towerCells, device.x, device.y, device.z, this.maxCover)) {
            if (covers(tower, device)) {
                long dx = tower.x - device.x;
                long dz = tower.z - device.z;
                long d = dx * dx + dz * dz;
                if (d < bestDistance || d == bestDistance && best != null && tower.id < best.id) {
                    best = tower;
                    bestDistance = d;
                }
            }
        }
        return best;
    }

    static boolean connects(int ax, int ay, int az, int ar, int bx, int by, int bz, int br) {
        long dx = ax - bx;
        long dy = ay - by;
        long dz = az - bz;
        long r = Math.max(ar, br);
        return dx * dx + dy * dy + dz * dz <= r * r;
    }

    private static boolean connects(Tower a, Tower b) {
        return connects(a.x, a.y, a.z, a.spec.connectRange(), b.x, b.y, b.z, b.spec.connectRange());
    }

    static boolean covers(int tx, int ty, int tz, int range, int x, int y, int z) {
        long dx = tx - x;
        long dz = tz - z;
        return Math.abs(ty - y) <= range && dx * dx + dz * dz <= (long) range * range;
    }

    private static boolean covers(Tower tower, Device device) {
        return covers(tower.x, tower.y, tower.z, tower.spec.coverRange(), device.x, device.y, device.z);
    }

    // ---- 空间索引

    private static long cellKey(int cx, int cy, int cz) {
        return ((cx & 0x1FFFFFL) << 42) | ((cy & 0x1FFFFFL) << 21) | (cz & 0x1FFFFFL);
    }

    private static <T> List<T> cellList(Map<Long, List<T>> cells, int x, int y, int z) {
        return cells.computeIfAbsent(cellKey(Math.floorDiv(x, CELL), Math.floorDiv(y, CELL), Math.floorDiv(z, CELL)), key -> new ArrayList<>());
    }

    private static <T> List<T> near(Map<Long, List<T>> cells, int x, int y, int z, int range) {
        List<T> out = new ArrayList<>();
        int x0 = Math.floorDiv(x - range, CELL);
        int x1 = Math.floorDiv(x + range, CELL);
        int y0 = Math.floorDiv(y - range, CELL);
        int y1 = Math.floorDiv(y + range, CELL);
        int z0 = Math.floorDiv(z - range, CELL);
        int z1 = Math.floorDiv(z + range, CELL);
        for (int cx = x0; cx <= x1; cx++) {
            for (int cy = y0; cy <= y1; cy++) {
                for (int cz = z0; cz <= z1; cz++) {
                    List<T> list = cells.get(cellKey(cx, cy, cz));
                    if (list != null) {
                        out.addAll(list);
                    }
                }
            }
        }
        return out;
    }
}
