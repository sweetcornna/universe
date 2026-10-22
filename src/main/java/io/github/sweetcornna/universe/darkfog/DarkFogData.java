package io.github.sweetcornna.universe.darkfog;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.core.darkfog.BaseState;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * 黑雾的地面基地（ADR-0028 §二），存在 {@code data/universe/dark_fog.dat}：每个星球维度一份，记着选好的基地点和每处基地的状态
 * （等级、经验、物质、能量、威胁、每座建筑的血量、营地里的单位数）。建筑和单位的实体不存档，玩家走近时按这里放出来。
 */
public final class DarkFogData extends SavedData {
    /** 存档格式版本。改格式时加一，并在读取时迁移旧格式（技术架构 §3.10）。 */
    private static final int FORMAT = 1;

    private static final Codec<BaseState.Status> STATUS = Codec.STRING.xmap(name -> BaseState.Status.valueOf(name.toUpperCase(Locale.ROOT)),
            status -> status.name().toLowerCase(Locale.ROOT));

    private static final Codec<BaseState> BASE = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.fieldOf("id").forGetter(BaseState::id),
            Codec.INT.listOf().fieldOf("pos").forGetter(base -> List.of(base.x(), base.y(), base.z())),
            STATUS.fieldOf("status").forGetter(BaseState::status),
            Codec.INT.optionalFieldOf("level", 0).forGetter(BaseState::level),
            Codec.DOUBLE.optionalFieldOf("xp", 0.0).forGetter(BaseState::xp),
            Codec.DOUBLE.optionalFieldOf("matter", 0.0).forGetter(BaseState::matter),
            Codec.INT.optionalFieldOf("energy", 0).forGetter(BaseState::energy),
            Codec.DOUBLE.optionalFieldOf("threat", 0.0).forGetter(BaseState::threat),
            Codec.INT.optionalFieldOf("cooldown", 0).forGetter(BaseState::cooldown),
            Codec.BOOL.optionalFieldOf("ground", false).forGetter(BaseState::ground),
            Codec.FLOAT.listOf().optionalFieldOf("health", List.of()).forGetter(base -> floats(base.healthArray())),
            Codec.INT.listOf().optionalFieldOf("units", List.of()).forGetter(base -> ints(base.unitArray())),
            Codec.DOUBLE.listOf().optionalFieldOf("progress", List.of()).forGetter(base -> doubles(base.progressArray())),
            Codec.LONG.optionalFieldOf("updated", 0L).forGetter(BaseState::updated),
            Codec.INT.optionalFieldOf("build_delay", 0).forGetter(BaseState::buildDelay)
    ).apply(i, (id, pos, status, level, xp, matter, energy, threat, cooldown, ground, health, units, progress, updated, buildDelay) ->
            new BaseState(id, pos.get(0), pos.get(1), pos.get(2), status, level, xp, matter, energy, threat, cooldown, ground,
                    toFloats(health), toInts(units), toDoubles(progress), updated, buildDelay)));

    /** 一颗星球上的基地。 */
    public static final class Planet {
        static final Codec<Planet> CODEC = RecordCodecBuilder.create(i -> i.group(
                BASE.listOf().optionalFieldOf("bases", List.of()).forGetter(planet -> planet.bases)
        ).apply(i, Planet::new));

        private final List<BaseState> bases = new ArrayList<>();

        Planet(List<BaseState> bases) {
            this.bases.addAll(bases);
        }

        public List<BaseState> bases() {
            return this.bases;
        }

        public Optional<BaseState> base(int id) {
            return this.bases.stream().filter(base -> base.id() == id).findFirst();
        }

        int nextId() {
            return this.bases.stream().mapToInt(BaseState::id).max().orElse(-1) + 1;
        }
    }

    public static final Codec<DarkFogData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.optionalFieldOf("format").forGetter(data -> Optional.of(FORMAT)),
            Codec.unboundedMap(Codec.STRING, Planet.CODEC).optionalFieldOf("planets", Map.of()).forGetter(data -> data.planets)
    ).apply(i, (format, planets) -> new DarkFogData(planets)));

    public static final SavedDataType<DarkFogData> TYPE = new SavedDataType<>(Universe.id("dark_fog"), DarkFogData::new, CODEC);

    private final Map<String, Planet> planets = new HashMap<>();

    private DarkFogData() {
    }

    private DarkFogData(Map<String, Planet> planets) {
        this.planets.putAll(planets);
    }

    public static DarkFogData get(MinecraftServer server) {
        return server.getDataStorage().computeIfAbsent(TYPE);
    }

    public Optional<Planet> planet(String dimension) {
        return Optional.ofNullable(this.planets.get(dimension));
    }

    /** 第一次见到这颗星球：放下选好的基地点。 */
    Planet plan(String dimension, List<BaseState> sites) {
        Planet planet = new Planet(sites);
        this.planets.put(dimension, planet);
        this.setDirty();
        return planet;
    }

    /** 加一处基地（调试命令用）。 */
    BaseState add(String dimension, int x, int z) {
        Planet planet = this.planets.computeIfAbsent(dimension, key -> new Planet(List.of()));
        BaseState base = new BaseState(planet.nextId(), x, z);
        planet.bases.add(base);
        this.setDirty();
        return base;
    }

    void removeAll(String dimension) {
        Planet planet = this.planets.get(dimension);
        if (planet != null) {
            planet.bases.clear();
            this.setDirty();
        }
    }

    public Map<String, Planet> planets() {
        return this.planets;
    }

    private static List<Float> floats(float[] values) {
        List<Float> list = new ArrayList<>(values.length);
        for (float value : values) {
            list.add(value);
        }
        return list;
    }

    private static List<Integer> ints(int[] values) {
        List<Integer> list = new ArrayList<>(values.length);
        for (int value : values) {
            list.add(value);
        }
        return list;
    }

    private static List<Double> doubles(double[] values) {
        List<Double> list = new ArrayList<>(values.length);
        for (double value : values) {
            list.add(value);
        }
        return list;
    }

    private static float[] toFloats(List<Float> list) {
        float[] values = new float[list.size()];
        for (int i = 0; i < values.length; i++) {
            values[i] = list.get(i);
        }
        return values;
    }

    private static int[] toInts(List<Integer> list) {
        return list.stream().mapToInt(Integer::intValue).toArray();
    }

    private static double[] toDoubles(List<Double> list) {
        return list.stream().mapToDouble(Double::doubleValue).toArray();
    }
}
