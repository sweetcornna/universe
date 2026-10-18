package io.github.sweetcornna.universe.core.poi;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

import io.github.sweetcornna.universe.core.galaxy.Liquid;
import io.github.sweetcornna.universe.core.galaxy.Planet;
import io.github.sweetcornna.universe.core.galaxy.PlanetType;
import io.github.sweetcornna.universe.core.procgen.Rng;
import io.github.sweetcornna.universe.core.procgen.Seeds;
import io.github.sweetcornna.universe.core.terrain.TerrainModel;

/**
 * 一颗星球上兴趣点的分布（ADR-0014）。
 *
 * <p>星球按 {@value #CELL} 格一格划成网格，每格最多一个兴趣点；有没有、是哪种、在格子里的什么位置都由星球种子派生，
 * 选址时用核心层的地形模型挑一块够平、不在水下的地面。不用生成区块就能算出来，所以扫描和信号增强器能找到
 * 还没加载的兴趣点，世界生成在区块里放建筑时也用同一份结果。
 */
public final class PoiLayout {
    public static final int CELL = 160;
    /** 兴趣点中心离格子边至少这么远：建筑不会伸进别的格子，也不会超出世界生成能写的 3×3 区块。 */
    static final int MARGIN = 24;
    /** 每个格子最多试几处位置。 */
    private static final int ATTEMPTS = 6;
    private static final int CACHE_SIZE = 4096;

    /** 地面：某一列的地表高度，以及能不能站人（不在水下、熔岩湖里）。 */
    public interface Ground {
        double height(int x, int z);

        boolean dry(int x, int z);
    }

    private final long seed;
    private final double density;
    private final Ground ground;
    /** 兴趣点中心的坐标范围（世界边界往里收一点）；不限时为整数最大值。 */
    private final int limit;
    private final Map<Long, Optional<Poi>> cache = new LinkedHashMap<>(256, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Long, Optional<Poi>> eldest) {
            return this.size() > CACHE_SIZE;
        }
    };

    /**
     * @param planetSeed 星球种子
     * @param type       星球类型，决定兴趣点的密度
     * @param ground     选址用的地面
     * @param worldSize  世界边界的边长；0 表示不限
     */
    public PoiLayout(long planetSeed, PlanetType type, Ground ground, int worldSize) {
        this.seed = Seeds.derive(planetSeed, "poi");
        this.density = density(type);
        this.ground = ground;
        this.limit = worldSize <= 0 ? Integer.MAX_VALUE : worldSize / 2 - MARGIN;
    }

    public static PoiLayout of(Planet planet) {
        TerrainModel model = TerrainModel.of(planet);
        boolean liquid = planet.liquid() != Liquid.NONE;
        Ground ground = new Ground() {
            @Override
            public double height(int x, int z) {
                return model.sample(x, z).height();
            }

            @Override
            public boolean dry(int x, int z) {
                return (!liquid || model.sample(x, z).height() >= model.seaLevel() + 1) && !model.lavaLake(x, z);
            }
        };
        // 星球的世界边界（玩法设计 §2.3）还没有实装，玩家走得多远都行，兴趣点也就先不按边界截断。
        // 以后加上边界时传 planet.worldSize()：边界以内的兴趣点位置不会变。
        return new PoiLayout(planet.seed(), planet.type(), ground, 0);
    }

    /** 一个格子出兴趣点的概率：宜居、奇异、毒雾星球多一些，荒芜星球少一些。 */
    static double density(PlanetType type) {
        return switch (type) {
            case HABITABLE, EXOTIC -> 0.75;
            case TOXIC -> 0.7;
            case FROZEN, SCORCHED, IRRADIATED -> 0.6;
            case OCEAN -> 0.55;
            case BARREN -> 0.4;
            case GAS_GIANT -> 0;
        };
    }

    /** 某个格子里的兴趣点。 */
    public Optional<Poi> cell(int cellX, int cellZ) {
        long packed = pack(cellX, cellZ);
        synchronized (this.cache) {
            Optional<Poi> cached = this.cache.get(packed);
            if (cached != null) {
                return cached;
            }
        }
        Optional<Poi> poi = this.compute(cellX, cellZ, packed);
        synchronized (this.cache) {
            this.cache.put(packed, poi);
        }
        return poi;
    }

    private Optional<Poi> compute(int cellX, int cellZ, long packed) {
        long key = Seeds.derive(this.seed, packed);
        Rng rng = Rng.of(key);
        if (!rng.chance(this.density)) {
            return Optional.empty();
        }
        PoiType type = rng.weighted(List.of(PoiType.values()), PoiType::weight);
        int span = CELL - 2 * MARGIN;
        for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
            int x = cellX * CELL + MARGIN + rng.nextInt(span);
            int z = cellZ * CELL + MARGIN + rng.nextInt(span);
            if (Math.abs(x) > this.limit || Math.abs(z) > this.limit) {
                continue;
            }
            if (this.suitable(type, x, z)) {
                return Optional.of(new Poi(type, x, z, cellX, cellZ, key, rng.nextLong(), rng.nextInt(4)));
            }
        }
        return Optional.empty();
    }

    /** 中心和占地范围四角、四边都是干地，高低差不超过这种兴趣点的要求。 */
    private boolean suitable(PoiType type, int x, int z) {
        int r = type.radius();
        double min = Double.MAX_VALUE;
        double max = -Double.MAX_VALUE;
        for (int i = -1; i <= 1; i++) {
            for (int j = -1; j <= 1; j++) {
                int sx = x + i * r;
                int sz = z + j * r;
                if (!this.ground.dry(sx, sz)) {
                    return false;
                }
                double h = this.ground.height(sx, sz);
                min = Math.min(min, h);
                max = Math.max(max, h);
                if (max - min > type.slope()) {
                    return false;
                }
            }
        }
        return true;
    }

    /** 中心落在这个区块里的兴趣点（一个区块只属于一个格子，最多一个）。 */
    public Optional<Poi> inChunk(int chunkX, int chunkZ) {
        int cellX = Math.floorDiv(chunkX * 16, CELL);
        int cellZ = Math.floorDiv(chunkZ * 16, CELL);
        return this.cell(cellX, cellZ).filter(poi -> poi.x() >> 4 == chunkX && poi.z() >> 4 == chunkZ);
    }

    /** 中心在 (x, z) 半径以内的兴趣点，由近到远。 */
    public List<Poi> within(double x, double z, double radius) {
        List<Poi> found = new ArrayList<>();
        int x0 = Math.floorDiv((int) Math.floor(x - radius), CELL);
        int x1 = Math.floorDiv((int) Math.ceil(x + radius), CELL);
        int z0 = Math.floorDiv((int) Math.floor(z - radius), CELL);
        int z1 = Math.floorDiv((int) Math.ceil(z + radius), CELL);
        double radiusSq = radius * radius;
        for (int cx = x0; cx <= x1; cx++) {
            for (int cz = z0; cz <= z1; cz++) {
                this.cell(cx, cz).filter(poi -> poi.distanceSq(x, z) <= radiusSq).ifPresent(found::add);
            }
        }
        found.sort(Comparator.comparingDouble(poi -> poi.distanceSq(x, z)));
        return found;
    }

    /**
     * 半径以内满足条件的最近一个兴趣点。从所在的格子一圈圈往外找，外圈不可能更近时就停下，
     * 不必把半径里的格子都算一遍。
     */
    public Optional<Poi> nearest(double x, double z, double radius, Predicate<Poi> filter) {
        int cx = Math.floorDiv((int) Math.floor(x), CELL);
        int cz = Math.floorDiv((int) Math.floor(z), CELL);
        int rings = (int) Math.ceil(radius / CELL) + 1;
        Poi best = null;
        double bestSq = radius * radius;
        for (int ring = 0; ring <= rings; ring++) {
            // 第 ring 圈的格子离这一点至少 (ring - 1) 格宽，再加上兴趣点离格子边的距离
            double closest = Math.max(0, (ring - 1) * CELL + MARGIN);
            if (closest * closest > bestSq) {
                break;
            }
            for (int i = -ring; i <= ring; i++) {
                for (int j = -ring; j <= ring; j++) {
                    if (Math.max(Math.abs(i), Math.abs(j)) != ring) {
                        continue;
                    }
                    Optional<Poi> poi = this.cell(cx + i, cz + j);
                    if (poi.isPresent() && filter.test(poi.get())) {
                        double d = poi.get().distanceSq(x, z);
                        if (d <= bestSq) {
                            best = poi.get();
                            bestSq = d;
                        }
                    }
                }
            }
        }
        return Optional.ofNullable(best);
    }

    /** 兴趣点中心的地表高度估计（建筑真正放在哪一格由世界生成找地面决定）。 */
    public int estimatedY(Poi poi) {
        return (int) Math.floor(this.ground.height(poi.x(), poi.z()));
    }

    private static long pack(int cellX, int cellZ) {
        return (long) cellX << 32 ^ cellZ & 0xFFFFFFFFL;
    }
}
