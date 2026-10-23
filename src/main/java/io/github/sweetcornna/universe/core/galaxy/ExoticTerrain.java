package io.github.sweetcornna.universe.core.galaxy;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import io.github.sweetcornna.universe.core.procgen.Seeds;
import io.github.sweetcornna.universe.core.terrain.TerrainModel;

/**
 * 奇异星球的地貌（ADR-0030 §二）：{@link ExoticForm} 的几何。
 *
 * <p>每个地貌单元（一根石柱、一簇晶柱、一座石拱）的位置、大小、朝向都由星球种子和所在的格子派生，选址用地形模型的高度，
 * 不管哪个区块先生成，跨区块边界的同一座石拱两边都对得上。世界生成每个区块只写自己的 16×16 列：{@link #chunk}
 * 收集碰到这个区块的单元，{@link Chunk#column} 给出一列里要放的竖直方块段；石柱、拱脚往下补到这一列真实的地面，不悬空。
 *
 * <p>选址让开兴趣点、地标、传送门、守卫塔和降落点（{@link Keepout}）；水下、熔岩里不放（柱状玄武岩可以伸进浅海，
 * 晶柱可以长在水里）。露出地表的矿和原油涌泉那一列不盖（{@link #BLOCKED}）。
 */
public final class ExoticTerrain {
    /** 列的状态：干地。 */
    public static final byte DRY = 0;
    /** 列的状态：地面上是水（海、湖）。 */
    public static final byte WET = 1;
    /** 列的状态：不放东西（熔岩、露出地表的矿、原油涌泉）。 */
    public static final byte BLOCKED = 2;
    /** 石柱、拱脚离这一列真实的地面最多往下补多少格；补不上的那一列不放，免得悬空。 */
    static final int MAX_ROOT = 14;

    /** 方块的种类，世界生成换成具体的方块。 */
    public enum Material {
        /** 玄武岩柱身。 */
        COLUMN,
        /** 柱顶。 */
        COLUMN_TOP,
        /** 柱间的节理：比两边低一格，颜色浅。 */
        JOINT,
        /** 石林的柱身（岩层，按高度分色）。 */
        SHAFT,
        /** 石林顶上的硬岩帽。 */
        CAPROCK,
        CRYSTAL,
        /** 结核球。 */
        BOULDER,
        /** 结核球上的白色裂纹。 */
        VEIN,
        /** 地上的小黑球。 */
        SPHERULE,
        /** 多边形的边：盐壳。 */
        SALT,
        /** 多边形的边：冰楔。 */
        ICE,
        /** 多边形的边：冻融分选出来的碎石。 */
        STONES,
        /** 石拱、石鳍（岩层）。 */
        ARCH
    }

    /**
     * 一列里的一段方块，两端都包含。
     *
     * @param embed 能替换天然的地面方块（岩石、土、沙；矿不换）；否则只放进空气、水和植物里
     */
    public record Span(int bottom, int top, Material material, boolean embed) {
    }

    /** 选址用的地面：地形模型。 */
    public interface Ground {
        double height(int x, int z);

        /** 熔岩湖。 */
        boolean lava(int x, int z);
    }

    /** 要让开的东西。 */
    public interface Keepout {
        /** 以 (x, z) 为中心、半径 radius 的圆碰到了要让开的东西。 */
        boolean blocked(int x, int z, int radius);

        Keepout NONE = (x, z, radius) -> false;
    }

    /** 区块里每一列的真实地面：最上面一个实心方块的 Y 和这一列的状态，下标 {@code dx * 16 + dz}。 */
    public record Columns(int[] ground, byte[] state) {
        public int ground(int dx, int dz) {
            return this.ground[dx * 16 + dz];
        }

        public byte state(int dx, int dz) {
            return this.state[dx * 16 + dz];
        }
    }

    /** 一种地貌的几何。 */
    interface Formation {
        /** 准备西北角在 (x0, z0) 的区块：收集碰到它的单元。 */
        Prepared prepare(int x0, int z0, Columns columns);
    }

    /** 准备好的区块。 */
    interface Prepared {
        /** (x, z) 这一列（区块里的 (dx, dz)）要放的方块段。 */
        void column(int x, int z, int dx, int dz, int ground, byte state, List<Span> out);
    }

    final ExoticForm form;
    final long seed;
    final Ground ground;
    final Keepout keepout;
    final Liquid liquid;
    final int seaLevel;
    final double temperature;
    /** 低重力的星球上石柱、石拱长得高一些：重力的 −1/2 次方，限制在 0.8 到 1.45。 */
    final double lift;
    final int maxY;
    private final Formation formation;

    /**
     * @param planetSeed        星球种子
     * @param gravity           表面重力（g）
     * @param temperatureKelvin 平均地表温度
     * @param maxY              能放方块的最高 Y
     */
    public ExoticTerrain(ExoticForm form, long planetSeed, double gravity, double temperatureKelvin, Liquid liquid, int seaLevel,
            int maxY, Ground ground, Keepout keepout) {
        this.form = form;
        this.seed = Seeds.derive(Seeds.derive(planetSeed, "exotic"), "terrain");
        this.ground = ground;
        this.keepout = keepout;
        this.liquid = liquid;
        this.seaLevel = seaLevel;
        this.temperature = temperatureKelvin;
        this.lift = Math.clamp(1 / Math.sqrt(Math.max(0.05, gravity)), 0.8, 1.45);
        this.maxY = maxY;
        this.formation = switch (form) {
            case BASALT_COLUMNS -> new ExoticBasalt(this);
            case HOODOOS -> new ExoticHoodoos(this);
            case GIANT_CRYSTALS -> new ExoticCrystals(this);
            case CONCRETIONS -> new ExoticConcretions(this);
            case POLYGONS -> new ExoticPolygons(this);
            case ARCHES -> new ExoticArches(this);
        };
    }

    /** 一颗奇异星球的地貌；不是奇异星球时为空。地形模型用世界生成共用的那一个（列缓存能互相命中）。 */
    public static Optional<ExoticTerrain> of(Planet planet, TerrainModel model, Keepout keepout) {
        return ExoticForm.of(planet).map(form -> new ExoticTerrain(form, planet.seed(), planet.gravity(), planet.temperature(),
                planet.liquid(), model.seaLevel(), TerrainModel.MAX_Y, ground(model), keepout));
    }

    public static Ground ground(TerrainModel model) {
        return new Ground() {
            @Override
            public double height(int x, int z) {
                return model.sample(x, z).height();
            }

            @Override
            public boolean lava(int x, int z) {
                return model.lavaLake(x, z);
            }
        };
    }

    public ExoticForm form() {
        return this.form;
    }

    /** 西北角在 (x0, z0) 的区块。 */
    public Chunk chunk(int x0, int z0, Columns columns) {
        return new Chunk(x0, z0, columns, this.formation.prepare(x0, z0, columns));
    }

    /** 准备好的一个区块。 */
    public static final class Chunk {
        private final int x0;
        private final int z0;
        private final Columns columns;
        private final Prepared prepared;

        private Chunk(int x0, int z0, Columns columns, Prepared prepared) {
            this.x0 = x0;
            this.z0 = z0;
            this.columns = columns;
            this.prepared = prepared;
        }

        /** 第 (dx, dz) 列要放的方块段。 */
        public List<Span> column(int dx, int dz) {
            List<Span> out = new ArrayList<>(4);
            this.prepared.column(this.x0 + dx, this.z0 + dz, dx, dz, this.columns.ground(dx, dz), this.columns.state(dx, dz), out);
            return out;
        }
    }

    /**
     * 离 (x, z) 最近的一处这种地貌，按地形模型估计（不生成区块）；半径以内没有时为空。从所在的区块一圈圈往外找，
     * 每 2×2 列看一列。给调试命令用。
     */
    public Optional<int[]> locate(int x, int z, int radius) {
        int cx = Math.floorDiv(x, 16);
        int cz = Math.floorDiv(z, 16);
        int threshold = switch (this.form) {
            case POLYGONS -> 0;
            case CONCRETIONS -> 2;
            default -> 6;
        };
        for (int ring = 0; ring <= Math.max(1, radius / 16); ring++) {
            int[] best = null;
            double bestSq = Double.MAX_VALUE;
            for (int i = -ring; i <= ring; i++) {
                for (int j = -ring; j <= ring; j++) {
                    if (Math.max(Math.abs(i), Math.abs(j)) != ring) {
                        continue;
                    }
                    int x0 = (cx + i) * 16;
                    int z0 = (cz + j) * 16;
                    Columns columns = this.estimate(x0, z0);
                    Chunk chunk = this.chunk(x0, z0, columns);
                    for (int dx = 0; dx < 16; dx += 2) {
                        for (int dz = 0; dz < 16; dz += 2) {
                            for (Span span : chunk.column(dx, dz)) {
                                double d = (x0 + dx - x) * (double) (x0 + dx - x) + (z0 + dz - z) * (double) (z0 + dz - z);
                                if (span.top() - columns.ground(dx, dz) >= threshold && d < bestSq) {
                                    bestSq = d;
                                    best = new int[] {x0 + dx, z0 + dz};
                                }
                            }
                        }
                    }
                }
            }
            if (best != null) {
                return Optional.of(best);
            }
        }
        return Optional.empty();
    }

    /** 按地形模型估计一个区块的地面（每 2×2 列采一次）。 */
    Columns estimate(int x0, int z0) {
        int[] ground = new int[256];
        byte[] state = new byte[256];
        for (int dx = 0; dx < 16; dx += 2) {
            for (int dz = 0; dz < 16; dz += 2) {
                int x = x0 + dx;
                int z = z0 + dz;
                int g = (int) Math.floor(this.ground.height(x, z));
                byte s = this.ground.lava(x, z) ? BLOCKED : this.dry(x, z) ? DRY : WET;
                for (int i = 0; i < 2; i++) {
                    for (int j = 0; j < 2; j++) {
                        ground[(dx + i) * 16 + dz + j] = g;
                        state[(dx + i) * 16 + dz + j] = s;
                    }
                }
            }
        }
        return new Columns(ground, state);
    }

    // ---- 各种地貌共用

    /** 地面高出液面、不是熔岩湖：石林、石拱、结核球、多边形只放在这样的地方。 */
    boolean dry(int x, int z) {
        if (this.ground.lava(x, z)) {
            return false;
        }
        return this.liquid == Liquid.NONE || this.ground.height(x, z) >= this.seaLevel + 1;
    }

    /** 干地，或者不超过 depth 格深的水（不是熔岩海）：柱状玄武岩伸进浅海，晶柱长在水里。 */
    boolean shallow(int x, int z, int depth) {
        if (this.ground.lava(x, z)) {
            return false;
        }
        return this.liquid == Liquid.NONE || this.liquid != Liquid.LAVA && this.ground.height(x, z) >= this.seaLevel - depth
                || this.ground.height(x, z) >= this.seaLevel + 1;
    }

    /** 一种单元在格子 (cx, cz) 里的种子。 */
    long cellSeed(String kind, int cx, int cz) {
        return Seeds.derive(Seeds.derive(this.seed, kind), (long) cx << 32 ^ cz & 0xFFFFFFFFL);
    }

    /** 由种子和坐标决定的 [0, 1) 均匀数。 */
    static double hash01(long seed, int x, int z) {
        return (Seeds.derive(seed, (long) x << 32 ^ z & 0xFFFFFFFFL) >>> 11) * 0x1.0p-53;
    }

    static double smooth(double lo, double hi, double v) {
        double t = Math.clamp((v - lo) / (hi - lo), 0, 1);
        return t * t * (3 - 2 * t);
    }

    /**
     * 从地面往上长的一段，底端补到这一列的地面：段比地面低就截掉，悬在地面上方就往下补（最多 maxRoot 格，
     * 补不上的不放）。
     */
    static void rooted(List<Span> out, int bottom, int top, Material material, int ground, int maxRoot) {
        if (top <= ground || bottom - ground - 1 > maxRoot) {
            return;
        }
        out.add(new Span(ground + 1, top, material, false));
    }

    int clampTop(int top) {
        return Math.min(top, this.maxY - 2);
    }
}
