package io.github.sweetcornna.universe.core.terrain;

import io.github.sweetcornna.universe.core.galaxy.Landform;
import io.github.sweetcornna.universe.core.galaxy.Liquid;
import io.github.sweetcornna.universe.core.galaxy.Planet;
import io.github.sweetcornna.universe.core.galaxy.TerrainProfile;
import io.github.sweetcornna.universe.core.procgen.Seeds;
import io.github.sweetcornna.universe.core.procgen.SimplexNoise;

/**
 * 一颗星球的地形高度场：给定水平坐标，算出地表高度和选群系用的气候值（技术架构 §3.3）。
 *
 * <p>高度分三层叠加：
 * <ol>
 *   <li>基础地形：扭曲过的大陆噪声定出海陆和大起伏，侵蚀低处隆起山脊，再加丘陵和细节；</li>
 *   <li>标志性地貌（{@link Landform}）：山脉、峡谷、方山、火山、峰林、沙丘、群岛、裂谷、天坑、高原，
 *       每颗星球只有其中一到三种，各自限定在一片片区域里，走一段路就能看到不同的景观；</li>
 *   <li>修饰：梯田、尖塔、陨坑、河谷。</li>
 * </ol>
 * 洞穴、悬崖下的凹进这些三维结构在适配层用 Minecraft 的密度函数叠加。
 *
 * <p>纯函数，结果只取决于参数和种子；实例不可变，可以在世界生成的多个线程间共享。
 */
public final class TerrainModel {
    /** 星球维度的高度范围，和主世界一样。 */
    public static final int MIN_Y = -64;
    public static final int MAX_Y = 319;
    private static final int LOWEST_SURFACE = MIN_Y + 8;
    /** 超过这个高度后山峰逐渐压平，不会顶到世界上限被削成平台。 */
    private static final double SOFT_CEILING = MAX_Y - 90;
    private static final double CEILING_RANGE = 60;
    private static final int CACHE_SIZE = 1024;

    private final TerrainProfile profile;
    private final double riverStrength;
    private final boolean floatingIslands;
    private final long seed;
    private final double mountainRanges;
    private final double canyons;
    private final double mesas;
    private final double volcanoes;
    private final double karst;
    private final double dunes;
    private final double archipelago;
    private final double rifts;
    private final double sinkholes;
    private final double plateaus;
    private final double activeVolcanoChance;
    private final double windCos;
    private final double windSin;

    private final SimplexNoise warpX;
    private final SimplexNoise warpZ;
    private final SimplexNoise continent;
    private final SimplexNoise erosion;
    private final SimplexNoise ridge;
    private final SimplexNoise hills;
    private final SimplexNoise detail;
    private final SimplexNoise terraceMask;
    private final SimplexNoise spikes;
    private final SimplexNoise spikeMask;
    private final SimplexNoise river;
    private final SimplexNoise temperature;
    private final SimplexNoise humidity;
    private final SimplexNoise island;
    private final SimplexNoise islandHeight;
    private final SimplexNoise region;
    private final SimplexNoise rangeBelt;
    private final SimplexNoise rangePeaks;
    private final SimplexNoise canyonPath;
    private final SimplexNoise mesaShape;
    private final SimplexNoise duneWarp;
    private final SimplexNoise riftPath;
    private final SimplexNoise plateauShape;
    private final ThreadLocal<Cache> cache = ThreadLocal.withInitial(Cache::new);

    public TerrainModel(TerrainProfile profile, Liquid liquid, long seed) {
        this.profile = profile;
        this.seed = seed;
        this.riverStrength = switch (liquid) {
            case WATER, ACID -> 1.0;
            case ICE, LAVA -> 0.6;
            case NONE -> 0.0;
        };
        this.floatingIslands = profile.floatingIslands();
        this.mountainRanges = profile.strength(Landform.MOUNTAIN_RANGES);
        this.canyons = profile.strength(Landform.CANYONS);
        this.mesas = profile.strength(Landform.MESAS);
        this.volcanoes = profile.strength(Landform.VOLCANOES);
        this.karst = profile.strength(Landform.KARST);
        this.dunes = profile.strength(Landform.DUNES);
        this.archipelago = liquid == Liquid.NONE ? 0 : profile.strength(Landform.ARCHIPELAGO);
        this.rifts = profile.strength(Landform.RIFTS);
        this.sinkholes = profile.strength(Landform.SINKHOLES);
        this.plateaus = profile.strength(Landform.PLATEAUS);
        this.activeVolcanoChance = liquid == Liquid.LAVA ? 0.9 : 0.55;
        double windAngle = unit(Seeds.derive(seed, "terrain/wind")) * Math.PI * 2;
        this.windCos = Math.cos(windAngle);
        this.windSin = Math.sin(windAngle);
        this.warpX = noise(seed, "warp_x");
        this.warpZ = noise(seed, "warp_z");
        this.continent = noise(seed, "continent");
        this.erosion = noise(seed, "erosion");
        this.ridge = noise(seed, "ridge");
        this.hills = noise(seed, "hills");
        this.detail = noise(seed, "detail");
        this.terraceMask = noise(seed, "terrace_mask");
        this.spikes = noise(seed, "spikes");
        this.spikeMask = noise(seed, "spike_mask");
        this.river = noise(seed, "river");
        this.temperature = noise(seed, "temperature");
        this.humidity = noise(seed, "humidity");
        this.island = noise(seed, "island");
        this.islandHeight = noise(seed, "island_height");
        this.region = noise(seed, "region");
        this.rangeBelt = noise(seed, "range_belt");
        this.rangePeaks = noise(seed, "range_peaks");
        this.canyonPath = noise(seed, "canyon_path");
        this.mesaShape = noise(seed, "mesa_shape");
        this.duneWarp = noise(seed, "dune_warp");
        this.riftPath = noise(seed, "rift_path");
        this.plateauShape = noise(seed, "plateau_shape");
    }

    public static TerrainModel of(Planet planet) {
        return new TerrainModel(planet.terrain(), planet.liquid(), planet.seed());
    }

    private static SimplexNoise noise(long seed, String name) {
        return new SimplexNoise(Seeds.derive(seed, "terrain/" + name));
    }

    public TerrainProfile profile() {
        return this.profile;
    }

    public int seaLevel() {
        return this.profile.seaLevel();
    }

    public boolean floatingIslands() {
        return this.floatingIslands;
    }

    /**
     * 某一列的采样结果。每个线程缓存最近用过的列：区块生成时同一列会被密度函数、地表高度、群系源各问一遍。
     */
    public Sample sample(int x, int z) {
        Cache c = this.cache.get();
        int index = (int) (mix(x, z) & (CACHE_SIZE - 1));
        Sample cached = c.values[index];
        if (cached != null && c.xs[index] == x && c.zs[index] == z) {
            return cached;
        }
        Sample sample = this.compute(x, z);
        c.xs[index] = x;
        c.zs[index] = z;
        c.values[index] = sample;
        return sample;
    }

    /**
     * 洞穴顶的参考高度：周围两圈（8 格、16 格）地表的最低值。
     *
     * <p>洞穴按"离地表多深"决定能不能挖。只看本列的话，陡崖顶下十几格离崖面可能只有一两格，
     * 芝士洞会把山体掏空，崖面上只剩一根根细柱。改用周围的最低地表，洞穴就和崖面保持距离。
     * 为了省采样，只在 8 格网格上算，中间双线性插值。
     */
    public double caveRoof(int x, int z) {
        int x0 = Math.floorDiv(x, 8) * 8;
        int z0 = Math.floorDiv(z, 8) * 8;
        double fx = (x - x0) / 8.0;
        double fz = (z - z0) / 8.0;
        double r00 = this.gridRoof(x0, z0);
        if (fx == 0 && fz == 0) {
            return r00;
        }
        double r10 = this.gridRoof(x0 + 8, z0);
        double r01 = this.gridRoof(x0, z0 + 8);
        double r11 = this.gridRoof(x0 + 8, z0 + 8);
        return lerp(fz, lerp(fx, r00, r10), lerp(fx, r01, r11));
    }

    private double gridRoof(int x, int z) {
        double roof = this.sample(x, z).height();
        for (int r = 8; r <= 16; r += 8) {
            for (int i = 0; i < 8; i++) {
                roof = Math.min(roof, this.sample(x + RING_X[i] * r, z + RING_Z[i] * r).height());
            }
        }
        return roof;
    }

    private static final int[] RING_X = {1, 1, 0, -1, -1, -1, 0, 1};
    private static final int[] RING_Z = {0, 1, 1, 1, 0, -1, -1, -1};

    public Sample compute(double x, double z) {
        TerrainProfile p = this.profile;
        double sea = p.seaLevel();

        // 扭曲坐标，让海岸线和山脉走向不那么规整
        double wx = x + 90 * this.warpX.fbm(x / 450, z / 450, 3);
        double wz = z + 90 * this.warpZ.fbm(x / 450, z / 450, 3);

        double scale = p.continentScale();
        double c = clamp(this.continent.fbm(wx / scale, wz / scale, 5) * 2.2, -1, 1);
        double e = clamp(this.erosion.fbm(wx / (scale * 0.8), wz / (scale * 0.8), 4) * 2.0, -1, 1);

        // —— 基础地形 ——
        double elevation = this.continentElevation(c);
        if (this.archipelago > 0) {
            // 群岛：在压低的大陆上用高频起伏顶出一片片小岛
            elevation += this.archipelago * 16 * this.hills.fbm(wx / 110, wz / 110, 4);
        }
        double land = smoothstep(-6, 10, elevation);

        // 河流沿噪声的零值线走。山在靠近河的地方逐渐矮下去，河从山间的谷地穿过，而不是在山体上切一道窄缝
        // 河流不是处处都有：另一层低频噪声划出有河的地区，河在地区边缘渐渐消失，就是河的源头
        double riverPresence = this.riverStrength > 0
                ? this.riverStrength * smoothstep(-0.3, 0.1, this.region.fbm(x / 1800 - 17.9, z / 1800 + 40.3, 2) * 2)
                : 0;
        double riverDistance = riverPresence > 0 ? lineDistance(this.river, wx, wz, 1100, 3) : Double.MAX_VALUE;
        double riverTaper = lerp(riverPresence, 1, smoothstep(10, 140, riverDistance));

        double mountainMask = smoothstep(0.15, -0.45, e) * land;
        // 脊状噪声的尺度和指数是按坡度统计挑的：尺度太小、指数太大，山脊会陡成一排细针
        double ridgeValue = this.ridge.ridged(wx / 450, wz / 450, 5);
        double mountains = p.ridges() * (30 + 1.5 * p.heightVariation()) * mountainMask * Math.pow(ridgeValue, 1.6);

        // —— 标志性地貌 ——
        double plateau = 0;
        if (this.plateaus > 0) {
            // 高原：大片抬升。边缘下部是缓的碎石坡、上部是陡崖，崖线被冲沟切得参差不齐
            double shape = this.plateauShape.fbm(wx / 900, wz / 900, 3) * 2
                    + 0.1 * this.detail.fbm(x / 70, z / 70, 3);
            double edge = smoothstep(0.0, 0.18, shape);
            plateau = (0.3 * edge + 0.7 * smoothstep(0.55, 0.8, edge)) * land;
            elevation += plateau * (35 + 45 * this.plateaus);
        }

        if (this.mountainRanges > 0) {
            // 山脉：低频脊状噪声连成一条条山带，带内是高耸的脊状山峰
            double belt = smoothstep(0.55, 0.85, this.rangeBelt.ridged(wx / 1600, wz / 1600, 2)) * land;
            if (belt > 0) {
                double peaks = this.rangePeaks.ridged(wx / 750, wz / 750, 6);
                double amp = 90 + 130 * this.mountainRanges;
                double m = belt * amp * Math.pow(peaks, 1.6);
                mountains += m;
                mountainMask = Math.max(mountainMask, belt);
            }
        }
        mountains *= riverTaper;
        elevation += mountains;

        // 丘陵和细碎起伏；高原顶上更平
        double hillAmp = p.hilliness() * (0.6 + 0.4 * (1 - mountainMask)) * (1 - 0.5 * plateau);
        elevation += hillAmp * 1.6 * this.hills.fbm(wx / 140, wz / 140, 4);
        elevation += p.roughness() * 3.5 * this.detail.fbm(x / 22, z / 22, 3);

        double mesa = 0;
        if (this.mesas > 0) {
            mesa = this.mesa(x, z, wx, wz, land);
            elevation += mesa * (28 + 32 * this.mesas);
        }

        double canyon = 0;
        if (this.canyons > 0) {
            double[] result = this.canyon(wx, wz, land);
            elevation += result[0];
            canyon = result[1];
        }

        // 河谷：河床切到液面以下，两侧是河漫滩，再往外谷坡的坡度有上限。
        // 放在峰林、火山之前：石峰可以紧贴河岸拔起，火山锥压在河谷上
        double riverChannel = 0;
        if (riverPresence > 0) {
            double halfWidth = 1 + 5 * riverPresence;
            double bed = -1 - 4 * riverPresence;
            double over = Math.max(0, riverDistance - halfWidth);
            double wallSlope = 0.45 + 0.35 * mountainMask;
            double valley = bed + 0.12 * Math.min(over, 24) + wallSlope * Math.max(0, over - 24);
            riverChannel = (1 - smoothstep(halfWidth - 1, halfWidth + 3, riverDistance)) * smoothstep(-3, 4, elevation)
                    * smoothstep(0.25, 0.6, riverPresence);
            elevation = lerp(smoothstep(0, 0.3, riverPresence), elevation, smoothMin(elevation, valley, 6));
        }

        double karstValue = 0;
        if (this.karst > 0) {
            double regionMask = smoothstep(-0.05, 0.3, this.region.fbm(x / 700 + 13.1, z / 700 - 7.7, 2) * 2) * land;
            if (regionMask > 0) {
                // 峰林之间是平坦的谷地，石峰从平地上拔起
                elevation = lerp(regionMask * 0.75, elevation, Math.min(elevation, 3 + 4 * this.hills.fbm(x / 90, z / 90, 2)));
                double towers = this.karstTowers(x, z) * regionMask;
                karstValue = towers > 0 ? regionMask : 0;
                elevation += towers;
            }
        }

        double volcanic = 0;
        double lava = 0;
        if (this.volcanoes > 0) {
            double[] result = this.volcanoes(x, z, elevation);
            elevation = result[0];
            volcanic = result[1];
            lava = result[2];
        }

        double dune = 0;
        if (this.dunes > 0) {
            double regionMask = smoothstep(-0.1, 0.25, this.region.fbm(x / 900 - 31.3, z / 900 + 5.9, 2) * 2)
                    * smoothstep(-2, 6, elevation) * (1 - smoothstep(30, 60, elevation));
            if (regionMask > 0) {
                elevation += regionMask * this.duneHeight(x, z);
                dune = regionMask;
            }
        }

        if (this.rifts > 0) {
            elevation += this.rift(wx, wz);
        }

        // —— 修饰 ——
        if (p.terraces() > 0 && elevation > 0) {
            double mask = p.terraces() * smoothstep(-0.1, 0.35, this.terraceMask.fbm(x / 500, z / 500, 2) * 2);
            if (mask > 0) {
                elevation = lerp(mask, elevation, terrace(elevation, 7));
            }
        }

        if (p.spikes() > 0) {
            double regionMask = smoothstep(-0.2, 0.4, this.spikeMask.fbm(x / 350, z / 350, 2) * 2);
            if (regionMask > 0) {
                double s = Math.max(0, (this.spikes.noise(x / 26, z / 26) - 0.5) / 0.5);
                elevation += p.spikes() * regionMask * 70 * s * s * s;
            }
        }

        if (p.craters() > 0) {
            elevation += this.craters(x, z, p.craters());
        }

        if (this.sinkholes > 0) {
            elevation -= this.sinkholes(x, z, elevation);
        }

        double riverFactor = riverChannel * (1 - volcanic) * smoothstep(4, -2, elevation);

        double height = sea + elevation;
        if (height > SOFT_CEILING) {
            height = SOFT_CEILING + CEILING_RANGE * Math.tanh((height - SOFT_CEILING) / CEILING_RANGE);
        }
        height = Math.max(height, LOWEST_SURFACE);
        double t = clamp(this.temperature.fbm(x / 1100, z / 1100, 3) * 2, -1, 1);
        double h = clamp(this.humidity.fbm(x / 800, z / 800, 3) * 2, -1, 1);
        return new Sample(height, height - sea, c, e, mountains, t, h, riverFactor, volcanic, dune, mesa, canyon, karstValue,
                lava);
    }

    /** 只由大陆度决定的基础高度（相对海平面）；有群岛的星球整体压到海平面附近。 */
    private double continentElevation(double c) {
        TerrainProfile p = this.profile;
        double elevation = (p.baseHeight() - p.seaLevel()) + p.heightVariation() * (c < 0 ? c * 1.3 : c);
        if (this.archipelago > 0) {
            elevation = elevation * (1 - 0.6 * this.archipelago) - 4 * this.archipelago;
        }
        return elevation;
    }

    /** 某点的基础高度，算法和 {@link #compute} 开头相同。火山用它定出锥体的绝对高度。 */
    private double continentElevationAt(double x, double z) {
        double wx = x + 90 * this.warpX.fbm(x / 450, z / 450, 3);
        double wz = z + 90 * this.warpZ.fbm(x / 450, z / 450, 3);
        double scale = this.profile.continentScale();
        return this.continentElevation(clamp(this.continent.fbm(wx / scale, wz / scale, 5) * 2.2, -1, 1));
    }

    /**
     * 方山：噪声高于阈值的地方顶起平顶台地，两级台阶。每级是下部的碎石坡加上部的陡崖，
     * 噪声刚过阈值的小块只剩坡顶一截短崖，成了孤丘，不会变成一两格宽的细柱。返回 0 到 1 的台地高度系数。
     */
    private double mesa(double x, double z, double wx, double wz, double land) {
        double regionMask = smoothstep(-0.15, 0.25, this.region.fbm(x / 800 + 71.3, z / 800 - 3.1, 2) * 2) * land;
        if (regionMask <= 0) {
            return 0;
        }
        double m = this.mesaShape.fbm(wx / 170, wz / 170, 3) * 2 + 0.03 * this.detail.fbm(x / 18, z / 18, 2);
        double tier1 = 0.25 * smoothstep(0.02, 0.2, m) + 0.4 * smoothstep(0.19, 0.23, m);
        double tier2 = 0.12 * smoothstep(0.4, 0.53, m) + 0.23 * smoothstep(0.52, 0.56, m);
        return regionMask * (tier1 + tier2);
    }

    /**
     * 峡谷：一片高原上蜿蜒的深谷，谷壁三级台阶。返回 {高度变化, 峡谷强度}。
     * 谷底能切到液面以下，有液体的星球谷底会积水成河。
     */
    private double[] canyon(double wx, double wz, double land) {
        double regionMask = smoothstep(-0.2, 0.25, this.region.fbm(wx / 1000 - 51.7, wz / 1000 + 22.9, 2) * 2) * land;
        if (regionMask <= 0) {
            return new double[] {0, 0};
        }
        double lift = regionMask * (30 + 30 * this.canyons);
        // 宽度按方块算：谷底宽十几格，谷口宽一两百格，每级台阶都有一段平台
        double distance = lineDistance(this.canyonPath, wx, wz, 520, 4);
        double floorHalf = 5 + 6 * this.canyons;
        double rimHalf = floorHalf + 45 + 55 * this.canyons;
        double f = 1 - clamp((distance - floorHalf) / (rimHalf - floorHalf), 0, 1);
        f = clamp(f + 0.04 * this.detail.fbm(wx / 90, wz / 90, 2), 0, 1);
        double depth = lift + 18 + 40 * this.canyons;
        // 三级台阶：每级是一段平台加一段陡壁
        double stepped = (Math.floor(f * 3) + smoothstep(0.6, 0.9, f * 3 - Math.floor(f * 3))) / 3;
        stepped = Math.min(1, stepped);
        return new double[] {lift - regionMask * depth * stepped, regionMask * smoothstep(0, 0.3, f)};
    }

    /**
     * 峰林：每个 24 格的网格单元按概率立一座石峰，成簇分布、疏密不一。
     * 石峰下粗上细、顶部浑圆，截面被噪声扭成不规则的形状，高矮相差好几倍。
     */
    private double karstTowers(double x, double z) {
        double cell = 24;
        int cx = (int) Math.floor(x / cell);
        int cz = (int) Math.floor(z / cell);
        double best = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                int gx = cx + dx;
                int gz = cz + dz;
                double cluster = this.region.noise(gx * cell / 260.0 + 3.3, gz * cell / 260.0 - 8.1);
                long h = mix(this.seed ^ 0x6B61727374L, mix(gx, gz));
                if (unit(h) > 0.25 + 0.6 * smoothstep(-0.5, 0.4, cluster)) {
                    continue;
                }
                h = mix(h, 1);
                double px = (gx + 0.2 + 0.6 * unit(h)) * cell;
                h = mix(h, 2);
                double pz = (gz + 0.2 + 0.6 * unit(h)) * cell;
                h = mix(h, 3);
                double radius = 6 + 9 * unit(h);
                h = mix(h, 4);
                double tall = unit(h);
                double height = (14 + 70 * tall * tall) * (0.5 + 0.5 * this.karst) * (0.7 + 0.3 * smoothstep(-0.3, 0.6, cluster));
                double ddx = x - px;
                double ddz = z - pz;
                double d = Math.sqrt(ddx * ddx + ddz * ddz);
                if (d >= radius * 1.3) {
                    continue;
                }
                double angle = Math.atan2(ddz, ddx);
                double wobble = 1 + 0.28 * this.detail.noise(Math.cos(angle) * 1.3 + gx * 0.71, Math.sin(angle) * 1.3 + gz * 0.53);
                double u = d / (radius * wobble);
                if (u >= 1) {
                    continue;
                }
                // 下部陡壁、上部收拢成圆顶
                double profile = 1 - Math.pow(u, 3.5);
                best = Math.max(best, height * Math.sqrt(profile) * (u < 0.92 ? 1 : (1 - u) / 0.08));
            }
        }
        return best;
    }

    /** 一座火山的参数。 */
    private record Volcano(double x, double z, double radius, double height, boolean active, double craterDepth) {
        static final double CRATER = 0.13;
    }

    private static final double VOLCANO_CELL = 700;

    /** 网格单元里的火山，没有就返回 null。 */
    private Volcano volcano(int cellX, int cellZ) {
        long h = mix(this.seed ^ 0x766F6C63L, mix(cellX, cellZ));
        if (unit(h) > 0.25 + 0.45 * this.volcanoes) {
            return null;
        }
        h = mix(h, 1);
        double px = (cellX + 0.25 + 0.5 * unit(h)) * VOLCANO_CELL;
        h = mix(h, 2);
        double pz = (cellZ + 0.25 + 0.5 * unit(h)) * VOLCANO_CELL;
        h = mix(h, 3);
        double radius = 110 + 160 * unit(h);
        h = mix(h, 4);
        double height = (60 + 120 * unit(h)) * (0.6 + 0.4 * this.volcanoes);
        h = mix(h, 5);
        boolean active = unit(h) < this.activeVolcanoChance;
        h = mix(h, 6);
        double craterDepth = height * (0.08 + 0.08 * unit(h));
        return new Volcano(px, pz, radius, height, active, craterDepth);
    }

    /**
     * 火山：每 700 格的网格单元按概率放一座。锥体略内凹，侧面有放射状冲沟，山脚外缘是一圈熔岩台地。
     *
     * <p>山脚叠在本地地形上，越往上越贴近按火山中心的基础高度算出的绝对高度，
     * 于是火山口底部是一片平地，活火山的火山口里是熔岩湖。返回 {新的高度, 火山强度, 熔岩湖 0 或 1}。
     */
    private double[] volcanoes(double x, double z, double elevation) {
        int cx = (int) Math.floor(x / VOLCANO_CELL);
        int cz = (int) Math.floor(z / VOLCANO_CELL);
        double strength = 0;
        double lava = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                Volcano v = this.volcano(cx + dx, cz + dz);
                if (v == null) {
                    continue;
                }
                double ddx = x - v.x();
                double ddz = z - v.z();
                double u = Math.sqrt(ddx * ddx + ddz * ddz) / v.radius();
                if (u >= 1.3) {
                    continue;
                }
                // 山脚外缘的熔岩台地
                double apron = (1 - smoothstep(0.7, 1.3, u)) * 4;
                if (u >= 1) {
                    elevation += apron;
                    continue;
                }
                double base = this.continentElevationAt(v.x(), v.z());
                // 放射状冲沟：在单位圆上取三维噪声，绕一圈没有接缝；越往山脚沟越深
                double ax = ddx / Math.max(1e-6, u * v.radius());
                double az = ddz / Math.max(1e-6, u * v.radius());
                double channel = Math.pow(1 - Math.abs(this.detail.noise(ax * 1.4 + v.x() * 0.01, az * 1.4, u * 2.2)), 4);
                double ribs = this.detail.noise(ax * 3.5, az * 3.5 + v.z() * 0.01, u * 7);
                double gullies = 1 - 0.22 * channel * smoothstep(0.14, 0.55, u) + 0.05 * ribs * u;
                double cone = v.height() * Math.pow(1 - u, 1.7) * gullies;
                double override = 1 - smoothstep(0.3, 0.9, u);
                double surface = lerp(override, elevation + cone, base + cone);
                if (u < Volcano.CRATER) {
                    double k = u / Volcano.CRATER;
                    double rim = base + v.height() * Math.pow(1 - Volcano.CRATER, 1.7);
                    double floor = rim - v.craterDepth();
                    // 内壁陡、底部平
                    surface = lerp(smoothstep(0.6, 1.0, k), floor, rim);
                    if (v.active() && k < 0.6 && floor > 3) {
                        lava = 1;
                    }
                }
                elevation = surface + apron;
                strength = Math.max(strength, 1 - smoothstep(0.55, 0.95, u));
            }
        }
        return new double[] {elevation, strength, lava};
    }

    /** 这颗星球有没有火山。没有的话地表规则里不用加熔岩湖那一条。 */
    public boolean hasVolcanoes() {
        return this.volcanoes > 0;
    }

    /** 这一列是否在活火山口的熔岩湖里。和 {@link Sample#lava()} 一致，但不用算整列地形，地表规则逐列调用。 */
    public boolean lavaLake(int x, int z) {
        if (this.volcanoes <= 0) {
            return false;
        }
        int cx = (int) Math.floor(x / VOLCANO_CELL);
        int cz = (int) Math.floor(z / VOLCANO_CELL);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                Volcano v = this.volcano(cx + dx, cz + dz);
                if (v == null || !v.active()) {
                    continue;
                }
                double ddx = x - v.x();
                double ddz = z - v.z();
                double u = Math.sqrt(ddx * ddx + ddz * ddz) / v.radius();
                if (u < Volcano.CRATER * 0.6) {
                    double rim = this.continentElevationAt(v.x(), v.z()) + v.height() * Math.pow(1 - Volcano.CRATER, 1.7);
                    if (rim - v.craterDepth() > 3) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** 横向沙丘：沿风向排列的不对称波，迎风坡缓、背风坡陡，波峰高度随位置起伏。 */
    private double duneHeight(double x, double z) {
        double along = x * this.windCos + z * this.windSin;
        double across = -x * this.windSin + z * this.windCos;
        double wavelength = 46;
        double t = along / wavelength + 0.9 * this.duneWarp.fbm(across / 160, along / 160, 2)
                + 0.25 * this.duneWarp.noise(across / 40, along / 90);
        double f = t - Math.floor(t);
        double profile = f < 0.72 ? smoothstep(0, 0.72, f) : 1 - smoothstep(0.72, 1, f);
        double amplitude = (5 + 11 * this.dunes) * (0.55 + 0.45 * this.duneWarp.fbm(x / 220 + 9.3, z / 220, 2));
        return amplitude * profile;
    }

    /** 裂谷：极低频噪声的零值线，谷底平坦、两壁陡峭，两肩略微隆起。 */
    private double rift(double wx, double wz) {
        double distance = lineDistance(this.riftPath, wx, wz, 2400, 3);
        double width = 40 + 30 * this.rifts;
        double depth = 45 + 55 * this.rifts;
        double carve = 1 - smoothstep(width * 0.55, width, distance);
        double shoulder = (1 - smoothstep(width, width * 3, distance)) * (1 - carve);
        return -carve * depth + shoulder * depth * 0.2;
    }

    /** 天坑：圆形的垂直深坑，坑壁两格内就落到底。 */
    private double sinkholes(double x, double z, double elevation) {
        if (elevation < 4) {
            return 0;
        }
        double cell = 170;
        int cx = (int) Math.floor(x / cell);
        int cz = (int) Math.floor(z / cell);
        double deepest = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                long h = mix(this.seed ^ 0x73696E6BL, mix(cx + dx, cz + dz));
                if (unit(h) > 0.15 + 0.3 * this.sinkholes) {
                    continue;
                }
                h = mix(h, 1);
                double px = (cx + dx + 0.3 + 0.4 * unit(h)) * cell;
                h = mix(h, 2);
                double pz = (cz + dz + 0.3 + 0.4 * unit(h)) * cell;
                h = mix(h, 3);
                double radius = 8 + 18 * unit(h);
                h = mix(h, 4);
                double depth = 40 + 60 * unit(h);
                double ddx = x - px;
                double ddz = z - pz;
                double d = Math.sqrt(ddx * ddx + ddz * ddz);
                if (d < radius + 3) {
                    double wall = clamp((radius + 3 - d) / 3, 0, 1);
                    deepest = Math.max(deepest, wall * wall * depth);
                }
            }
        }
        return deepest;
    }

    /**
     * 到噪声零值线的大致距离（方块）。河谷、峡谷、裂谷都沿零值线走，用方块距离定宽度。
     *
     * <p>按零值线附近梯度的中位数（约 2.5 / scale）换算，不逐点求梯度：逐点除以梯度在鞍点附近会把距离算得极大，
     * 梯度变化快的地方距离也跟着陡变，谷底会顶出薄墙、谷坡被拉得很陡。统一换算后各处宽窄有些出入，反倒更自然。
     */
    private static double lineDistance(SimplexNoise noise, double wx, double wz, double scale, int octaves) {
        return Math.abs(noise.fbm(wx / scale, wz / scale, octaves)) * scale / 2.5;
    }

    /** 平滑的最小值，{@code k} 是过渡宽度。 */
    static double smoothMin(double a, double b, double k) {
        double h = Math.max(k - Math.abs(a - b), 0) / k;
        return Math.min(a, b) - h * h * k / 4;
    }

    /** 台阶化：高度量化成每级 {@code step} 格，台阶之间是陡崖。 */
    private static double terrace(double elevation, double step) {
        double t = elevation / step;
        double base = Math.floor(t);
        return (base + smoothstep(0.35, 0.65, t - base)) * step;
    }

    /**
     * 浮空岛的密度，大于 0 为实心。只在海平面以上约 100 格的一层里出现。
     */
    public double islandDensity(double x, double y, double z) {
        if (!this.floatingIslands) {
            return -1;
        }
        double center = this.profile.seaLevel() + 100 + 25 * this.islandHeight.noise(x / 600, z / 600);
        double dy = (y - center) / 28;
        if (Math.abs(dy) > 1.2) {
            return -1;
        }
        double n = this.island.fbm(x / 110, y / 55, z / 110, 3) * 2;
        return n - 0.55 - dy * dy * 0.9;
    }

    /** 三层大小不同的陨坑：每个网格单元按概率放一个，坑底下凹、坑沿隆起，大坑中央有峰。 */
    private double craters(double x, double z, double density) {
        return this.craterLayer(x, z, 260, 0.35 * density, 0)
                + this.craterLayer(x, z, 90, 0.6 * density, 1)
                + this.craterLayer(x, z, 32, 0.75 * density, 2);
    }

    private double craterLayer(double x, double z, double cell, double probability, int layer) {
        int cx = (int) Math.floor(x / cell);
        int cz = (int) Math.floor(z / cell);
        double sum = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                long h = mix(this.seed + layer * 0x9E3779B97F4A7C15L, mix(cx + dx, cz + dz));
                if (unit(h) >= probability) {
                    continue;
                }
                h = mix(h, 1);
                double px = (cx + dx + 0.2 + 0.6 * unit(h)) * cell;
                h = mix(h, 2);
                double pz = (cz + dz + 0.2 + 0.6 * unit(h)) * cell;
                h = mix(h, 3);
                double radius = cell * (0.12 + 0.22 * unit(h));
                double ddx = x - px;
                double ddz = z - pz;
                double u = Math.sqrt(ddx * ddx + ddz * ddz) / radius;
                if (u >= 2) {
                    continue;
                }
                double depth = radius * 0.4;
                double rim = depth * 0.35;
                double v;
                if (u < 1) {
                    double u2 = u * u;
                    v = -depth * (1 - u2) + rim * u2 * u2;
                    if (radius > 35 && u < 0.25) {
                        double k = 1 - u / 0.25;
                        v += depth * 0.5 * k * k;
                    }
                } else {
                    double k = 2 - u;
                    v = rim * k * k;
                }
                sum += v;
            }
        }
        return sum;
    }

    private static long mix(long a, long b) {
        long z = a * 0x9E3779B97F4A7C15L + b;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    /** 哈希值的高 53 位换成 [0, 1) 的小数。 */
    private static double unit(long h) {
        return (h >>> 11) * 0x1.0p-53;
    }

    static double clamp(double v, double min, double max) {
        return v < min ? min : Math.min(v, max);
    }

    static double lerp(double t, double a, double b) {
        return a + t * (b - a);
    }

    /** 平滑阶跃。{@code edge0 > edge1} 时方向相反（从 1 降到 0）。 */
    static double smoothstep(double edge0, double edge1, double x) {
        double t = clamp((x - edge0) / (edge1 - edge0), 0, 1);
        return t * t * (3 - 2 * t);
    }

    /**
     * 一列的地形信息。
     *
     * @param height          地表高度（方块 Y）
     * @param elevation       相对海平面的高度
     * @param continentalness 大陆度，-1（深海）到 1（内陆）
     * @param erosion         侵蚀度，越低越多山
     * @param mountains       山脉贡献的高度
     * @param temperature     局部温度偏移，-1 到 1，用来在同一颗星球上分出冷暖地带
     * @param humidity        局部湿度，-1 到 1
     * @param river           河谷强度，0 到 1
     * @param volcanic        火山锥强度，0 到 1
     * @param dunes           沙丘区强度，0 到 1
     * @param mesa            方山台地强度，0 到 1
     * @param canyon          峡谷区强度，0 到 1
     * @param karst           峰林区强度，0 到 1
     * @param lava            活火山口里的熔岩湖，1 表示这一列的表层换成熔岩
     */
    public record Sample(double height, double elevation, double continentalness, double erosion, double mountains,
            double temperature, double humidity, double river, double volcanic, double dunes, double mesa, double canyon,
            double karst, double lava) {
    }

    private static final class Cache {
        final int[] xs = new int[CACHE_SIZE];
        final int[] zs = new int[CACHE_SIZE];
        final Sample[] values = new Sample[CACHE_SIZE];
    }
}
