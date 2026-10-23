package io.github.sweetcornna.universe.dyson;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.IntStream;
import java.util.stream.LongStream;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.core.dyson.DysonGrid;
import io.github.sweetcornna.universe.core.dyson.DysonLayer;
import io.github.sweetcornna.universe.core.dyson.DysonSphere;
import io.github.sweetcornna.universe.core.dyson.SwarmOrbit;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * 所有恒星系的戴森球，存在全局存档 {@code data/universe/dyson.dat}（ADR-0016）。核心层的类不能依赖序列化库，
 * 这里用记录类转一道。光照系数和半径上限也存下来（由星团种子决定，不会变）。
 */
public final class DysonData extends SavedData {
    /** 存档格式版本。改格式时加一，并在读取时迁移旧格式（技术架构 §3.10）。 */
    private static final int FORMAT = 1;

    private static final Codec<int[]> INTS = Codec.INT_STREAM.xmap(IntStream::toArray, Arrays::stream);
    private static final Codec<long[]> LONGS = Codec.LONG_STREAM.xmap(LongStream::toArray, Arrays::stream);

    private record OrbitRecord(int id, double radius, double inclination, double ascendingNode, long[] expiries, int[] counts) {
        static final Codec<OrbitRecord> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("id").forGetter(OrbitRecord::id),
                Codec.DOUBLE.fieldOf("radius").forGetter(OrbitRecord::radius),
                Codec.DOUBLE.fieldOf("inclination").forGetter(OrbitRecord::inclination),
                Codec.DOUBLE.fieldOf("node").forGetter(OrbitRecord::ascendingNode),
                LONGS.fieldOf("expiries").forGetter(OrbitRecord::expiries),
                INTS.fieldOf("counts").forGetter(OrbitRecord::counts)
        ).apply(i, OrbitRecord::new));
    }

    /**
     * 自己画的网格（ADR-0030 §四）：节点坐标按 double 的原始位存（读回来一位不差，服务端才认得出没动的节点），
     * 框架两个一组，球壳先是每片几条边、再是顶点。
     */
    private record GridRecord(long[] nodes, int[] frames, int[] sides, int[] shells) {
        static final Codec<GridRecord> CODEC = RecordCodecBuilder.create(i -> i.group(
                LONGS.fieldOf("nodes").forGetter(GridRecord::nodes),
                INTS.fieldOf("frames").forGetter(GridRecord::frames),
                INTS.fieldOf("sides").forGetter(GridRecord::sides),
                INTS.fieldOf("shells").forGetter(GridRecord::shells)
        ).apply(i, GridRecord::new));

        static GridRecord of(DysonGrid grid) {
            long[] nodes = new long[grid.nodeCount() * 3];
            for (int n = 0; n < grid.nodeCount(); n++) {
                for (int k = 0; k < 3; k++) {
                    nodes[n * 3 + k] = Double.doubleToRawLongBits(grid.node(n)[k]);
                }
            }
            int[] frames = new int[grid.frameCount() * 2];
            for (int f = 0; f < grid.frameCount(); f++) {
                frames[f * 2] = grid.frame(f)[0];
                frames[f * 2 + 1] = grid.frame(f)[1];
            }
            int[] sides = new int[grid.shellCount()];
            List<Integer> shells = new ArrayList<>();
            for (int s = 0; s < grid.shellCount(); s++) {
                sides[s] = grid.shell(s).length;
                for (int v : grid.shell(s)) {
                    shells.add(v);
                }
            }
            return new GridRecord(nodes, frames, sides, shells.stream().mapToInt(Integer::intValue).toArray());
        }

        /** 拼回网格；数据坏了抛 {@link IllegalArgumentException}。 */
        DysonGrid toGrid(DysonGrid.Template template, int density) {
            if (this.nodes.length % 3 != 0 || this.frames.length % 2 != 0) {
                throw new IllegalArgumentException("bad grid lengths");
            }
            double[][] positions = new double[this.nodes.length / 3][];
            for (int n = 0; n < positions.length; n++) {
                positions[n] = new double[] {Double.longBitsToDouble(this.nodes[n * 3]), Double.longBitsToDouble(this.nodes[n * 3 + 1]),
                        Double.longBitsToDouble(this.nodes[n * 3 + 2])};
            }
            int[][] edges = new int[this.frames.length / 2][];
            for (int f = 0; f < edges.length; f++) {
                edges[f] = new int[] {this.frames[f * 2], this.frames[f * 2 + 1]};
            }
            int[][] loops = new int[this.sides.length][];
            int at = 0;
            for (int s = 0; s < loops.length; s++) {
                if (this.sides[s] < 3 || at + this.sides[s] > this.shells.length) {
                    throw new IllegalArgumentException("bad shell " + s);
                }
                loops[s] = Arrays.copyOfRange(this.shells, at, at + this.sides[s]);
                at += this.sides[s];
            }
            return DysonGrid.custom(template, density, positions, edges, loops);
        }
    }

    /** 颜色（ADR-0030 §四）：0 是默认色；全是默认色的层不存。 */
    private record ColorRecord(int[] nodes, int[] frames, int[] shells) {
        static final Codec<ColorRecord> CODEC = RecordCodecBuilder.create(i -> i.group(
                INTS.optionalFieldOf("nodes", new int[0]).forGetter(ColorRecord::nodes),
                INTS.optionalFieldOf("frames", new int[0]).forGetter(ColorRecord::frames),
                INTS.optionalFieldOf("shells", new int[0]).forGetter(ColorRecord::shells)
        ).apply(i, ColorRecord::new));

        static Optional<ColorRecord> of(DysonLayer layer) {
            int[] nodes = layer.nodeColors();
            int[] frames = layer.frameColors();
            int[] shells = layer.shellColors();
            boolean any = Arrays.stream(nodes).anyMatch(c -> c != 0) || Arrays.stream(frames).anyMatch(c -> c != 0)
                    || Arrays.stream(shells).anyMatch(c -> c != 0);
            return any ? Optional.of(new ColorRecord(nodes, frames, shells)) : Optional.empty();
        }
    }

    private record LayerRecord(int id, double radius, double inclination, double ascendingNode, String template, int density, int[] nodes,
            int[] frames, int[] cells, Optional<GridRecord> grid, Optional<ColorRecord> colors) {
        static final Codec<LayerRecord> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("id").forGetter(LayerRecord::id),
                Codec.DOUBLE.fieldOf("radius").forGetter(LayerRecord::radius),
                Codec.DOUBLE.fieldOf("inclination").forGetter(LayerRecord::inclination),
                Codec.DOUBLE.fieldOf("node").forGetter(LayerRecord::ascendingNode),
                Codec.STRING.fieldOf("template").forGetter(LayerRecord::template),
                Codec.INT.fieldOf("density").forGetter(LayerRecord::density),
                INTS.fieldOf("nodes").forGetter(LayerRecord::nodes),
                INTS.fieldOf("frames").forGetter(LayerRecord::frames),
                INTS.fieldOf("cells").forGetter(LayerRecord::cells),
                // ADR-0030：旧存档没有这两项，照模板读
                GridRecord.CODEC.optionalFieldOf("grid").forGetter(LayerRecord::grid),
                ColorRecord.CODEC.optionalFieldOf("colors").forGetter(LayerRecord::colors)
        ).apply(i, LayerRecord::new));
    }

    private static final Codec<DysonSphere.Rocket> ROCKET_CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.LONG.fieldOf("arrival").forGetter(DysonSphere.Rocket::arrival),
            Codec.INT.fieldOf("layer").forGetter(DysonSphere.Rocket::layer),
            Codec.BOOL.fieldOf("node").forGetter(DysonSphere.Rocket::node),
            Codec.INT.fieldOf("index").forGetter(DysonSphere.Rocket::index)
    ).apply(i, DysonSphere.Rocket::new));

    private record SphereRecord(double lightFactor, double maxRadius, long lastAdvance, List<OrbitRecord> orbits, List<LayerRecord> layers,
            List<DysonSphere.Rocket> rockets) {
        static final Codec<SphereRecord> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.DOUBLE.fieldOf("light").forGetter(SphereRecord::lightFactor),
                Codec.DOUBLE.fieldOf("max_radius").forGetter(SphereRecord::maxRadius),
                Codec.LONG.optionalFieldOf("last_advance", Long.MIN_VALUE).forGetter(SphereRecord::lastAdvance),
                OrbitRecord.CODEC.listOf().optionalFieldOf("orbits", List.of()).forGetter(SphereRecord::orbits),
                LayerRecord.CODEC.listOf().optionalFieldOf("layers", List.of()).forGetter(SphereRecord::layers),
                ROCKET_CODEC.listOf().optionalFieldOf("rockets", List.of()).forGetter(SphereRecord::rockets)
        ).apply(i, SphereRecord::new));

        static SphereRecord of(DysonSphere sphere) {
            List<OrbitRecord> orbits = new ArrayList<>();
            for (SwarmOrbit orbit : sphere.orbits()) {
                orbits.add(new OrbitRecord(orbit.id(), orbit.radius(), orbit.inclination(), orbit.ascendingNode(), orbit.expiries(),
                        orbit.counts()));
            }
            List<LayerRecord> layers = new ArrayList<>();
            for (DysonLayer layer : sphere.layers()) {
                layers.add(new LayerRecord(layer.id(), layer.radius(), layer.inclination(), layer.ascendingNode(),
                        layer.grid().template().name(), layer.grid().density(), layer.nodeProgress(), layer.frameProgress(), layer.cellProgress(),
                        layer.grid().custom() ? Optional.of(GridRecord.of(layer.grid())) : Optional.empty(), ColorRecord.of(layer)));
            }
            return new SphereRecord(sphere.lightFactor(), sphere.maxRadius(), sphere.lastAdvance(), orbits, layers, List.copyOf(sphere.rockets()));
        }

        DysonSphere toSphere() {
            DysonSphere sphere = new DysonSphere(this.lightFactor, this.maxRadius);
            for (OrbitRecord orbit : this.orbits) {
                sphere.restoreOrbit(orbit.id, orbit.radius, orbit.inclination, orbit.ascendingNode, orbit.expiries, orbit.counts);
            }
            for (LayerRecord layer : this.layers) {
                DysonGrid.Template template;
                try {
                    template = DysonGrid.Template.valueOf(layer.template);
                } catch (IllegalArgumentException e) {
                    template = DysonGrid.Template.GEODESIC;
                }
                DysonLayer restored = null;
                if (layer.grid.isPresent()) {
                    try {
                        DysonGrid grid = layer.grid.get().toGrid(template, layer.density);
                        restored = sphere.restoreLayer(layer.id, layer.radius, layer.inclination, layer.ascendingNode, grid, layer.nodes,
                                layer.frames, layer.cells);
                    } catch (IllegalArgumentException e) {
                        Universe.LOGGER.warn("Dyson layer {} has a broken custom grid, falling back to its template: {}", layer.id, e.getMessage());
                    }
                }
                if (restored == null) {
                    restored = sphere.restoreLayer(layer.id, layer.radius, layer.inclination, layer.ascendingNode, template, layer.density,
                            layer.nodes, layer.frames, layer.cells);
                }
                if (restored != null && layer.colors.isPresent()) {
                    ColorRecord colors = layer.colors.get();
                    restored.restoreColors(colors.nodes, colors.frames, colors.shells);
                }
            }
            this.rockets.forEach(sphere::restoreRocket);
            sphere.restoreLastAdvance(this.lastAdvance);
            return sphere;
        }
    }

    private static final Codec<DysonData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.optionalFieldOf("format").forGetter(data -> Optional.of(FORMAT)),
            Codec.unboundedMap(Codec.STRING, SphereRecord.CODEC).fieldOf("spheres").forGetter(DysonData::records)
    ).apply(i, (format, records) -> {
        DysonData data = new DysonData();
        records.forEach((system, record) -> {
            try {
                data.spheres.put(Integer.parseInt(system), record.toSphere());
            } catch (NumberFormatException e) {
                Universe.LOGGER.warn("Ignoring Dyson sphere with bad system index {}", system);
            }
        });
        return data;
    }));

    public static final SavedDataType<DysonData> TYPE = new SavedDataType<>(Universe.id("dyson"), DysonData::new, CODEC);

    private final TreeMap<Integer, DysonSphere> spheres = new TreeMap<>();

    private DysonData() {
    }

    public static DysonData get(MinecraftServer server) {
        return server.getDataStorage().computeIfAbsent(TYPE);
    }

    private Map<String, SphereRecord> records() {
        Map<String, SphereRecord> records = new TreeMap<>();
        this.spheres.forEach((system, sphere) -> records.put(Integer.toString(system), SphereRecord.of(sphere)));
        return records;
    }

    public Map<Integer, DysonSphere> spheres() {
        return this.spheres;
    }

    /** 游戏内测试用：一个戴森球按存档格式写成 NBT 再读回来（ADR-0030 §四）。 */
    public static DysonSphere roundTrip(DysonSphere sphere) {
        var tag = SphereRecord.CODEC.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, SphereRecord.of(sphere)).getOrThrow();
        return SphereRecord.CODEC.parse(net.minecraft.nbt.NbtOps.INSTANCE, tag).getOrThrow().toSphere();
    }

    /** 游戏内测试用：去掉每层的 grid、colors 两项，当成 ADR-0030 以前的存档读。 */
    public static DysonSphere asOldSave(DysonSphere sphere) {
        var tag = (net.minecraft.nbt.CompoundTag) SphereRecord.CODEC.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, SphereRecord.of(sphere))
                .getOrThrow();
        tag.getListOrEmpty("layers").forEach(layer -> {
            if (layer instanceof net.minecraft.nbt.CompoundTag compound) {
                compound.remove("grid");
                compound.remove("colors");
            }
        });
        return SphereRecord.CODEC.parse(net.minecraft.nbt.NbtOps.INSTANCE, tag).getOrThrow().toSphere();
    }
}
