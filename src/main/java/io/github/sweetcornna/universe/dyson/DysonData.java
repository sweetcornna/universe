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

    private record LayerRecord(int id, double radius, double inclination, double ascendingNode, String template, int density, int[] nodes,
            int[] frames, int[] cells) {
        static final Codec<LayerRecord> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("id").forGetter(LayerRecord::id),
                Codec.DOUBLE.fieldOf("radius").forGetter(LayerRecord::radius),
                Codec.DOUBLE.fieldOf("inclination").forGetter(LayerRecord::inclination),
                Codec.DOUBLE.fieldOf("node").forGetter(LayerRecord::ascendingNode),
                Codec.STRING.fieldOf("template").forGetter(LayerRecord::template),
                Codec.INT.fieldOf("density").forGetter(LayerRecord::density),
                INTS.fieldOf("nodes").forGetter(LayerRecord::nodes),
                INTS.fieldOf("frames").forGetter(LayerRecord::frames),
                INTS.fieldOf("cells").forGetter(LayerRecord::cells)
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
                        layer.grid().template().name(), layer.grid().density(), layer.nodeProgress(), layer.frameProgress(), layer.cellProgress()));
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
                sphere.restoreLayer(layer.id, layer.radius, layer.inclination, layer.ascendingNode, template, layer.density, layer.nodes,
                        layer.frames, layer.cells);
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
}
