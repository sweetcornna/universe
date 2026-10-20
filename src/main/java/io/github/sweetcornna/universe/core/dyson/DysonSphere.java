package io.github.sweetcornna.universe.core.dyson;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.TreeMap;

/**
 * 一个恒星系的戴森球（ADR-0016）：戴森云的轨道和太阳帆、球壳的各层、在飞的火箭，按时间推进。
 *
 * <p>功率按戴森球计划：戴森云里每张帆 36 kW、每个结构点 96 kW、每个单元点 15 kW，都乘恒星的光照系数；换成本模组
 * 是每刻 1.8、4.8、0.75 焦。射线接收站每刻向它要电，戴森球不够分时按各自要的比例分，用上一刻的总需求算。
 */
public final class DysonSphere {
    public static final double SAIL_POWER = 1.8;
    public static final double SP_POWER = 4.8;
    public static final double CELL_POWER = 0.75;
    /** 太阳帆的寿命：5400 秒。 */
    public static final long SAIL_LIFETIME = 108_000;
    /** 每个节点每分钟吸几张帆。 */
    public static final int ABSORB_PER_MINUTE = 10;
    /** 火箭从发射到加上结构点要几刻。 */
    public static final long ROCKET_FLIGHT = 400;
    public static final int MAX_ORBITS = 20;
    public static final int MAX_LAYERS = 10;
    public static final double MIN_RADIUS = 0.03;
    /** 不研究应力系统时能建的纬度，以及两级应力系统以后的。 */
    public static final double LATITUDE_BASE = 30;
    public static final double LATITUDE_STRESS_1 = 60;
    public static final double LATITUDE_STRESS_2 = 90;
    /** 一次推进最多补算多久（刻），免得停服很久以后一下子算太多。 */
    private static final long MAX_STEP = 72_000;

    /** 在飞的火箭：到达时刻、去哪一层、建哪个结构。 */
    public record Rocket(long arrival, int layer, boolean node, int index) {
        DysonLayer.Target target() {
            return new DysonLayer.Target(this.node, this.index);
        }
    }

    private final double lightFactor;
    private final double maxRadius;
    private final TreeMap<Integer, SwarmOrbit> orbits = new TreeMap<>();
    private final TreeMap<Integer, DysonLayer> layers = new TreeMap<>();
    private final List<Rocket> rockets = new ArrayList<>();
    private long lastAdvance = Long.MIN_VALUE;
    /** 结构、进度每变一次加一，服务端据此决定要不要重新同步。 */
    private long revision;
    private double requested;
    private double lastRequested;
    private double drawn;
    private double lastDrawn;
    /** 结构点、单元点的合计，按 revision 缓存（接收站每刻都要问总功率）。 */
    private long cachedRevision = -1;
    private long cachedSp;
    private long cachedCells;

    /**
     * @param lightFactor 恒星的光照系数 min(2.5, 光度^0.3)
     * @param maxRadius   层和轨道半径的上限（天文单位）
     */
    public DysonSphere(double lightFactor, double maxRadius) {
        this.lightFactor = lightFactor;
        this.maxRadius = Math.max(MIN_RADIUS * 2, maxRadius);
    }

    /** 新的戴森球：自带一条戴森云的轨道，在半径上限的六成处。 */
    public static DysonSphere create(double lightFactor, double maxRadius) {
        DysonSphere sphere = new DysonSphere(lightFactor, maxRadius);
        sphere.addOrbit(sphere.maxRadius * 0.6, 0, 0);
        return sphere;
    }

    /** 半径的上限：最内侧行星轨道的九成，至少 0.06 天文单位。 */
    public static double maxRadius(double innermostOrbit) {
        return Math.max(MIN_RADIUS * 2, innermostOrbit * 0.9);
    }

    /** 光照系数：min(2.5, 光度^0.3)。 */
    public static double lightFactor(double luminosity) {
        return Math.min(2.5, Math.pow(Math.max(0, luminosity), 0.3));
    }

    /** 研究了几级应力系统时能建的纬度。 */
    public static double latitudeLimit(int stressLevel) {
        return stressLevel >= 2 ? LATITUDE_STRESS_2 : stressLevel == 1 ? LATITUDE_STRESS_1 : LATITUDE_BASE;
    }

    public double lightFactor() {
        return this.lightFactor;
    }

    public double maxRadius() {
        return this.maxRadius;
    }

    public long revision() {
        return this.revision;
    }

    private void changed() {
        this.revision++;
    }

    public Collection<SwarmOrbit> orbits() {
        return Collections.unmodifiableCollection(this.orbits.values());
    }

    public SwarmOrbit orbit(int id) {
        return this.orbits.get(id);
    }

    public Collection<DysonLayer> layers() {
        return Collections.unmodifiableCollection(this.layers.values());
    }

    public DysonLayer layer(int id) {
        return this.layers.get(id);
    }

    public List<Rocket> rockets() {
        return Collections.unmodifiableList(this.rockets);
    }

    // ---- 编辑 ----

    public double clampRadius(double radius) {
        return Math.max(MIN_RADIUS, Math.min(this.maxRadius, Double.isFinite(radius) ? radius : MIN_RADIUS));
    }

    private static double clampInclination(double degrees) {
        return Double.isFinite(degrees) ? Math.max(0, Math.min(90, degrees)) : 0;
    }

    private static double wrapDegrees(double degrees) {
        return Double.isFinite(degrees) ? ((degrees % 360) + 360) % 360 : 0;
    }

    private static int freeId(TreeMap<Integer, ?> map, int max) {
        for (int id = 1; id <= max; id++) {
            if (!map.containsKey(id)) {
                return id;
            }
        }
        return -1;
    }

    /** 加一条戴森云的轨道，返回编号；满了返回 -1。 */
    public int addOrbit(double radius, double inclination, double ascendingNode) {
        int id = freeId(this.orbits, MAX_ORBITS);
        if (id > 0) {
            this.orbits.put(id, new SwarmOrbit(id, this.clampRadius(radius), clampInclination(inclination), wrapDegrees(ascendingNode)));
            this.changed();
        }
        return id;
    }

    /** 改轨道，帆跟着走。 */
    public boolean updateOrbit(int id, double radius, double inclination, double ascendingNode) {
        SwarmOrbit orbit = this.orbits.get(id);
        if (orbit == null) {
            return false;
        }
        orbit.set(this.clampRadius(radius), clampInclination(inclination), wrapDegrees(ascendingNode));
        this.changed();
        return true;
    }

    /** 删轨道，上面的帆消散。 */
    public boolean removeOrbit(int id) {
        if (this.orbits.remove(id) == null) {
            return false;
        }
        this.changed();
        return true;
    }

    /** 加一层，返回编号；满了返回 -1。 */
    public int addLayer(double radius, double inclination, double ascendingNode, DysonGrid.Template template, int density) {
        int id = freeId(this.layers, MAX_LAYERS);
        if (id > 0) {
            this.layers.put(id, new DysonLayer(id, this.clampRadius(radius), clampInclination(inclination), wrapDegrees(ascendingNode),
                    template, density));
            this.changed();
        }
        return id;
    }

    /**
     * 改一层：朝向随时能改；半径、模板、密度只有还没开始建时能改。
     *
     * @return 改成了没有（层不存在，或者已经开始建了却要改形状）
     */
    public boolean updateLayer(int id, double radius, double inclination, double ascendingNode, DysonGrid.Template template, int density) {
        DysonLayer layer = this.layers.get(id);
        if (layer == null) {
            return false;
        }
        double r = this.clampRadius(radius);
        int d = Math.max(1, Math.min(template.maxDensity(), density));
        boolean reshape = Math.abs(r - layer.radius()) > 1e-9 || template != layer.grid().template() || d != layer.grid().density();
        if (reshape && !layer.empty()) {
            return false;
        }
        if (reshape) {
            layer.reshape(r, template, d);
        }
        layer.orient(clampInclination(inclination), wrapDegrees(ascendingNode));
        this.changed();
        return true;
    }

    /** 删一层，建好的全部没了，在路上的火箭也作废。 */
    public boolean removeLayer(int id) {
        if (this.layers.remove(id) == null) {
            return false;
        }
        this.rockets.removeIf(rocket -> rocket.layer() == id);
        this.changed();
        return true;
    }

    // ---- 发射 ----

    /** 弹射器射一张帆进 orbit 号轨道；轨道不存在时返回 false。 */
    public boolean launchSail(int orbitId, long now) {
        return this.addSails(orbitId, 1, now, SAIL_LIFETIME);
    }

    /** 射一张帆，寿命是 lifetime 刻（太阳帆寿命升级过的，ADR-0018）。 */
    public boolean launchSail(int orbitId, long now, long lifetime) {
        return this.addSails(orbitId, 1, now, lifetime);
    }

    /** 往轨道里加 count 张刚发射的帆，各活 lifetime 刻；轨道不存在时返回 false。 */
    public boolean addSails(int orbitId, int count, long now, long lifetime) {
        SwarmOrbit orbit = this.orbits.get(orbitId);
        if (orbit == null || count <= 0) {
            return false;
        }
        orbit.add(count, now + lifetime);
        return true;
    }

    /** 调试用：把一层在纬度限制以内的节点、框架、球壳直接建满。 */
    public boolean complete(int layerId, double limit) {
        DysonLayer layer = this.layers.get(layerId);
        if (layer == null) {
            return false;
        }
        DysonGrid grid = layer.grid();
        int[] nodes = layer.nodeProgress();
        for (int i = 0; i < nodes.length; i++) {
            if (grid.nodeWithin(i, limit)) {
                nodes[i] = DysonLayer.NODE_SP;
            }
        }
        int[] frames = layer.frameProgress();
        for (int i = 0; i < frames.length; i++) {
            if (grid.frameWithin(i, limit)) {
                frames[i] = layer.frameCost(i);
            }
        }
        int[] cells = layer.cellProgress();
        for (int i = 0; i < cells.length; i++) {
            if (grid.shellWithin(i, limit)) {
                cells[i] = layer.shellCost(i);
            }
        }
        layer.restore(nodes, frames, cells);
        this.changed();
        return true;
    }

    /** 这时候有没有能建的结构（发射井据此决定要不要发射）。 */
    public boolean hasTarget(double limit) {
        for (DysonLayer layer : this.layers.values()) {
            if (layer.nextTarget(limit) != null) {
                return true;
            }
        }
        return false;
    }

    /** 发射井发射一枚火箭，去编号最小的、还有能建结构的层；没有可建的时返回 null。 */
    public Rocket launchRocket(double limit, long now) {
        for (DysonLayer layer : this.layers.values()) {
            DysonLayer.Target target = layer.nextTarget(limit);
            if (target != null) {
                layer.reserve(target, 1);
                Rocket rocket = new Rocket(now + ROCKET_FLIGHT, layer.id(), target.node(), target.index());
                this.rockets.add(rocket);
                return rocket;
            }
        }
        return null;
    }

    // ---- 推进 ----

    /** 推进到 now：帆到期消散，火箭到达，节点吸帆铺壳。服务端每秒调一次。 */
    public void advance(long now) {
        if (this.lastAdvance == Long.MIN_VALUE || now <= this.lastAdvance) {
            this.lastAdvance = Math.max(now, this.lastAdvance == Long.MIN_VALUE ? now : this.lastAdvance);
            return;
        }
        long step = Math.min(MAX_STEP, now - this.lastAdvance);
        this.lastAdvance = now;
        boolean changed = false;
        for (SwarmOrbit orbit : this.orbits.values()) {
            changed |= orbit.expire(now) > 0;
        }
        Iterator<Rocket> it = this.rockets.iterator();
        while (it.hasNext()) {
            Rocket rocket = it.next();
            if (rocket.arrival() > now) {
                continue;
            }
            it.remove();
            DysonLayer layer = this.layers.get(rocket.layer());
            if (layer != null) {
                layer.build(rocket.target());
                changed = true;
            }
        }
        double seconds = step / 20.0;
        for (DysonLayer layer : this.layers.values()) {
            int quota = layer.absorbQuota(seconds);
            if (quota <= 0) {
                continue;
            }
            int taken = this.takeSails(quota);
            if (taken > 0) {
                int laid = layer.lay(taken);
                changed |= laid > 0;
            }
        }
        if (changed) {
            this.changed();
        }
    }

    /** 从整个戴森云里取最多 max 张帆，先取最快到期的。 */
    private int takeSails(int max) {
        int taken = 0;
        while (taken < max) {
            SwarmOrbit soonest = null;
            for (SwarmOrbit orbit : this.orbits.values()) {
                if (orbit.sails() > 0 && (soonest == null || orbit.earliest() < soonest.earliest())) {
                    soonest = orbit;
                }
            }
            if (soonest == null) {
                break;
            }
            taken += soonest.takeEarliest(max - taken);
        }
        return taken;
    }

    // ---- 功率 ----

    public long sails() {
        long total = 0;
        for (SwarmOrbit orbit : this.orbits.values()) {
            total += orbit.sails();
        }
        return total;
    }

    public long structurePoints() {
        this.refreshTotals();
        return this.cachedSp;
    }

    public long cellPoints() {
        this.refreshTotals();
        return this.cachedCells;
    }

    private void refreshTotals() {
        if (this.cachedRevision == this.revision) {
            return;
        }
        long sp = 0;
        long cells = 0;
        for (DysonLayer layer : this.layers.values()) {
            sp += layer.structurePoints();
            cells += layer.cellPoints();
        }
        this.cachedSp = sp;
        this.cachedCells = cells;
        this.cachedRevision = this.revision;
    }

    /** 戴森云的功率（焦/刻）。 */
    public double swarmPower() {
        return this.sails() * SAIL_POWER * this.lightFactor;
    }

    /** 球壳各层的功率（焦/刻）。 */
    public double shellPower() {
        return (this.structurePoints() * SP_POWER + this.cellPoints() * CELL_POWER) * this.lightFactor;
    }

    /** 戴森球现在的总功率（焦/刻）。 */
    public double generation() {
        return this.swarmPower() + this.shellPower();
    }

    /** 纬度限制以内全部建完时球壳的功率（焦/刻）。 */
    public double shellCapacity(double limit) {
        double total = 0;
        for (DysonLayer layer : this.layers.values()) {
            total += layer.structureCapacity(limit) * SP_POWER + layer.cellCapacity(limit) * CELL_POWER;
        }
        return total * this.lightFactor;
    }

    /** 这一刻每个接收站能拿到它要的多少：上一刻的总需求不超过总功率时是 1。 */
    public double supplyFraction() {
        if (this.lastRequested <= 0) {
            return 1;
        }
        return Math.min(1, this.generation() / this.lastRequested);
    }

    /** 接收站这一刻要多少（焦），下一刻据此分配。 */
    public void request(double joules) {
        this.requested += Math.max(0, joules);
    }

    /** 接收站这一刻实际取走多少（焦），统计用。 */
    public void draw(double joules) {
        this.drawn += Math.max(0, joules);
    }

    /** 每刻开头调一次：这一刻的需求、取用变成"上一刻"的。 */
    public void roll() {
        this.lastRequested = this.requested;
        this.lastDrawn = this.drawn;
        this.requested = 0;
        this.drawn = 0;
    }

    /** 上一刻接收站一共取走了多少（焦/刻）。 */
    public double lastDrawn() {
        return this.lastDrawn;
    }

    // ---- 存档 ----

    /** 读档：恢复一条轨道和它的帆。 */
    public void restoreOrbit(int id, double radius, double inclination, double ascendingNode, long[] expiries, int[] counts) {
        if (id < 1 || id > MAX_ORBITS) {
            return;
        }
        SwarmOrbit orbit = new SwarmOrbit(id, this.clampRadius(radius), clampInclination(inclination), wrapDegrees(ascendingNode));
        for (int i = 0; i < Math.min(expiries.length, counts.length); i++) {
            orbit.add(counts[i], expiries[i]);
        }
        this.orbits.put(id, orbit);
        this.changed();
    }

    /** 读档：恢复一层和它的进度。 */
    public DysonLayer restoreLayer(int id, double radius, double inclination, double ascendingNode, DysonGrid.Template template, int density,
            int[] nodes, int[] frames, int[] cells) {
        if (id < 1 || id > MAX_LAYERS) {
            return null;
        }
        DysonLayer layer = new DysonLayer(id, this.clampRadius(radius), clampInclination(inclination), wrapDegrees(ascendingNode),
                template, density);
        layer.restore(nodes, frames, cells);
        this.layers.put(id, layer);
        this.changed();
        return layer;
    }

    /** 读档：恢复在飞的火箭（层要先恢复）。 */
    public void restoreRocket(Rocket rocket) {
        DysonLayer layer = this.layers.get(rocket.layer());
        if (layer == null) {
            return;
        }
        int count = rocket.node() ? layer.grid().nodeCount() : layer.grid().frameCount();
        if (rocket.index() < 0 || rocket.index() >= count) {
            return;
        }
        layer.reserve(rocket.target(), 1);
        this.rockets.add(rocket);
    }

    public long lastAdvance() {
        return this.lastAdvance;
    }

    public void restoreLastAdvance(long tick) {
        this.lastAdvance = tick;
    }

    // ---- 几何 ----

    /**
     * 层或轨道上的点从它自己的坐标（y 是极轴）换到恒星系的坐标（y 是黄道面的法线，和太空维度一致）：
     * 先绕极轴自转 spin，再绕 x 轴倾斜 inclination，最后绕 y 轴转到升交点经度。角度都是度。
     */
    public static double[] orient(double[] local, double inclination, double ascendingNode, double spin) {
        double s = Math.toRadians(spin);
        double x = local[0] * Math.cos(s) - local[2] * Math.sin(s);
        double z = local[0] * Math.sin(s) + local[2] * Math.cos(s);
        double y = local[1];
        double i = Math.toRadians(inclination);
        double y1 = y * Math.cos(i) - z * Math.sin(i);
        double z1 = y * Math.sin(i) + z * Math.cos(i);
        double n = Math.toRadians(ascendingNode);
        double x2 = x * Math.cos(n) - z1 * Math.sin(n);
        double z2 = x * Math.sin(n) + z1 * Math.cos(n);
        return new double[] {x2, y1, z2};
    }

    /** 层自转一周要几刻（只是画面）：半径 0.1 天文单位时 20 分钟，按开普勒定律随半径变。 */
    public static double spinPeriod(double radius) {
        return 24000 * Math.pow(radius / 0.1, 1.5);
    }

    /** 戴森云里的帆绕一圈要几刻（只是画面）：半径 0.1 天文单位时 10 分钟。 */
    public static double sailPeriod(double radius) {
        return 12000 * Math.pow(radius / 0.1, 1.5);
    }
}
