package io.github.sweetcornna.universe.core.poi;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import io.github.sweetcornna.universe.core.ecology.Fauna;
import io.github.sweetcornna.universe.core.galaxy.Planet;
import io.github.sweetcornna.universe.core.galaxy.PlanetType;
import io.github.sweetcornna.universe.core.procgen.Rng;
import io.github.sweetcornna.universe.core.procgen.Seeds;
import io.github.sweetcornna.universe.core.sentinel.SentinelLevel;
import io.github.sweetcornna.universe.core.terrain.TerrainModel;

/**
 * 一颗星球上地标的分布（ADR-0026）。和兴趣点（{@link PoiLayout}）一样不用生成区块就能算：每种地标按自己的网格划格，
 * 每格最多一个，有没有、在哪儿都由星球种子和这种地标的派生键 {@code landmark/<种类>} 决定。
 *
 * <p>和别的东西撞上时地标让开：{@link Blocked} 由适配层给（兴趣点、传送门、守卫塔），地标之间排在前面的种类优先。
 * 找不到合适的位置就不放。
 */
public final class LandmarkLayout {
    /** 中心离格子边至少这么远：建筑不会伸进别的格子。 */
    static final int MARGIN = 24;
    /** 海底的地标只放在比海平面低这么多格以上的海底。 */
    public static final int SEABED_DEPTH = 12;
    /** 和别的地标之间至少隔开多少格（两边的半径以外）。 */
    static final int SPACING = 8;
    private static final int ATTEMPTS = 8;
    private static final int CACHE_SIZE = 2048;

    /** 地面：某一列的地表（海底）高度。 */
    public interface Ground {
        double height(int x, int z);

        /** 这一列能放东西（不是熔岩湖之类）。 */
        boolean usable(int x, int z);
    }

    /** 某个位置附近已经有别的东西（兴趣点、传送门、守卫塔），地标要让开。 */
    public interface Blocked {
        boolean blocked(int x, int z, int radius);

        Blocked NONE = (x, z, radius) -> false;
    }

    private final long seed;
    private final Set<LandmarkType> kinds;
    private final Ground ground;
    private final int seaLevel;
    private final Blocked blocked;
    /** 中心的坐标范围（世界边界往里收一点）；不限时为整数最大值。 */
    private final int limit;
    private final Map<Long, Optional<Landmark>> cache = new LinkedHashMap<>(256, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Long, Optional<Landmark>> eldest) {
            return this.size() > CACHE_SIZE;
        }
    };

    /**
     * @param planetSeed 星球种子
     * @param kinds      这颗星球上会有的地标
     * @param ground     选址用的地面
     * @param seaLevel   海平面
     * @param worldSize  世界边界的边长；0 表示不限
     * @param blocked    要让开的东西
     */
    public LandmarkLayout(long planetSeed, Set<LandmarkType> kinds, Ground ground, int seaLevel, int worldSize, Blocked blocked) {
        this.seed = planetSeed;
        this.kinds = kinds.isEmpty() ? Set.of() : EnumSet.copyOf(kinds);
        this.ground = ground;
        this.seaLevel = seaLevel;
        this.limit = worldSize <= 0 ? Integer.MAX_VALUE : worldSize / 2 - MARGIN;
        this.blocked = blocked;
    }

    public static LandmarkLayout of(Planet planet, Blocked blocked) {
        TerrainModel model = TerrainModel.of(planet);
        Ground ground = new Ground() {
            @Override
            public double height(int x, int z) {
                return model.sample(x, z).height();
            }

            @Override
            public boolean usable(int x, int z) {
                return !model.lavaLake(x, z);
            }
        };
        return new LandmarkLayout(planet.seed(), kinds(planet), ground, model.seaLevel(), planet.worldSize(), blocked);
    }

    /** 这颗星球上会有哪些地标（ADR-0026 的表）。不能登陆的星球没有。 */
    public static Set<LandmarkType> kinds(Planet planet) {
        Set<LandmarkType> kinds = EnumSet.noneOf(LandmarkType.class);
        if (!planet.landable()) {
            return kinds;
        }
        for (LandmarkType type : LandmarkType.values()) {
            if (allowed(type, planet)) {
                kinds.add(type);
            }
        }
        return kinds;
    }

    private static boolean allowed(LandmarkType type, Planet planet) {
        if (type.seabed()) {
            return Fauna.hasSea(planet.liquid());
        }
        return switch (type) {
            case TRADING_POST, SETTLEMENT -> planet.atmosphere().present() && planet.type() != PlanetType.BARREN;
            case MANUFACTURING_FACILITY -> SentinelLevel.of(planet).present();
            case FOSSIL_PIT -> switch (planet.type()) {
                case HABITABLE, BARREN, SCORCHED, FROZEN -> true;
                default -> false;
            };
            default -> true;
        };
    }

    public Set<LandmarkType> kinds() {
        return this.kinds;
    }

    /** 某种地标在某个格子里的那一处。 */
    public Optional<Landmark> cell(LandmarkType type, int cellX, int cellZ) {
        if (!this.kinds.contains(type)) {
            return Optional.empty();
        }
        // 种类放在最高几位：格子的坐标远远用不到那么大
        long packed = pack(cellX, cellZ) ^ (long) type.ordinal() << 58;
        synchronized (this.cache) {
            Optional<Landmark> cached = this.cache.get(packed);
            if (cached != null) {
                return cached;
            }
        }
        Optional<Landmark> landmark = this.compute(type, cellX, cellZ);
        synchronized (this.cache) {
            this.cache.put(packed, landmark);
        }
        return landmark;
    }

    private Optional<Landmark> compute(LandmarkType type, int cellX, int cellZ) {
        long key = Seeds.derive(Seeds.derive(this.seed, type.seedKey()), pack(cellX, cellZ));
        Rng rng = Rng.of(key);
        if (!rng.chance(type.chance())) {
            return Optional.empty();
        }
        int span = type.grid() - 2 * MARGIN;
        for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
            int x = cellX * type.grid() + MARGIN + rng.nextInt(span);
            int z = cellZ * type.grid() + MARGIN + rng.nextInt(span);
            if (Math.abs(x) > this.limit || Math.abs(z) > this.limit || !this.suitable(type, x, z)
                    || this.blocked.blocked(x, z, type.radius()) || this.crowded(type, x, z)) {
                continue;
            }
            return Optional.of(new Landmark(type, x, z, cellX, cellZ, key, rng.nextLong(), rng.nextInt(4)));
        }
        return Optional.empty();
    }

    /** 中心和占地范围四角、四边：海底的够深，高低差不超过这种地标的要求。 */
    private boolean suitable(LandmarkType type, int x, int z) {
        int r = type.radius();
        double min = Double.MAX_VALUE;
        double max = -Double.MAX_VALUE;
        for (int i = -1; i <= 1; i++) {
            for (int j = -1; j <= 1; j++) {
                int sx = x + i * r;
                int sz = z + j * r;
                if (!this.ground.usable(sx, sz)) {
                    return false;
                }
                double h = this.ground.height(sx, sz);
                if (type.seabed() ? h > this.seaLevel - SEABED_DEPTH : h < this.seaLevel + 1) {
                    return false;
                }
                min = Math.min(min, h);
                max = Math.max(max, h);
                if (max - min > type.slope()) {
                    return false;
                }
            }
        }
        return true;
    }

    /** 排在前面的地标已经占了附近。 */
    private boolean crowded(LandmarkType type, int x, int z) {
        for (LandmarkType other : this.kinds) {
            if (other.ordinal() >= type.ordinal()) {
                break;
            }
            if (this.nearest(other, x, z, type.radius() + other.radius() + SPACING).isPresent()) {
                return true;
            }
        }
        return false;
    }

    /** 中心落在这个区块里的地标（每种最多一个），按种类排好。 */
    public List<Landmark> inChunk(int chunkX, int chunkZ) {
        List<Landmark> found = new ArrayList<>();
        for (LandmarkType type : this.kinds) {
            int cellX = Math.floorDiv(chunkX * 16, type.grid());
            int cellZ = Math.floorDiv(chunkZ * 16, type.grid());
            this.cell(type, cellX, cellZ).filter(l -> l.x() >> 4 == chunkX && l.z() >> 4 == chunkZ).ifPresent(found::add);
        }
        return found;
    }

    /** 半径以内最近的一处这种地标。从所在的格子一圈圈往外找，外圈不可能更近时就停下。 */
    public Optional<Landmark> nearest(LandmarkType type, double x, double z, double radius) {
        if (!this.kinds.contains(type)) {
            return Optional.empty();
        }
        int grid = type.grid();
        int cx = Math.floorDiv((int) Math.floor(x), grid);
        int cz = Math.floorDiv((int) Math.floor(z), grid);
        int rings = (int) Math.ceil(radius / grid) + 1;
        Landmark best = null;
        double bestSq = radius * radius;
        for (int ring = 0; ring <= rings; ring++) {
            double closest = Math.max(0, (ring - 1) * grid + MARGIN);
            if (closest * closest > bestSq) {
                break;
            }
            for (int i = -ring; i <= ring; i++) {
                for (int j = -ring; j <= ring; j++) {
                    if (Math.max(Math.abs(i), Math.abs(j)) != ring) {
                        continue;
                    }
                    Optional<Landmark> landmark = this.cell(type, cx + i, cz + j);
                    if (landmark.isPresent()) {
                        double d = landmark.get().distanceSq(x, z);
                        if (d <= bestSq) {
                            best = landmark.get();
                            bestSq = d;
                        }
                    }
                }
            }
        }
        return Optional.ofNullable(best);
    }

    /** 半径以内所有种类的地标，由近到远。 */
    public List<Landmark> within(double x, double z, double radius) {
        List<Landmark> found = new ArrayList<>();
        double radiusSq = radius * radius;
        for (LandmarkType type : this.kinds) {
            int grid = type.grid();
            int x0 = Math.floorDiv((int) Math.floor(x - radius), grid);
            int x1 = Math.floorDiv((int) Math.ceil(x + radius), grid);
            int z0 = Math.floorDiv((int) Math.floor(z - radius), grid);
            int z1 = Math.floorDiv((int) Math.ceil(z + radius), grid);
            for (int cx = x0; cx <= x1; cx++) {
                for (int cz = z0; cz <= z1; cz++) {
                    this.cell(type, cx, cz).filter(l -> l.distanceSq(x, z) <= radiusSq).ifPresent(found::add);
                }
            }
        }
        found.sort(Comparator.comparingDouble(l -> l.distanceSq(x, z)));
        return found;
    }

    /** 地标中心的地面（海底）高度估计；建筑真正放在哪一格由世界生成找地面决定。 */
    public int estimatedY(Landmark landmark) {
        return (int) Math.floor(this.ground.height(landmark.x(), landmark.z()));
    }

    private static long pack(int cellX, int cellZ) {
        return (long) cellX << 32 ^ cellZ & 0xFFFFFFFFL;
    }
}
