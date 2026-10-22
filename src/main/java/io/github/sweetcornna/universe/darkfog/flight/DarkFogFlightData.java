package io.github.sweetcornna.universe.darkfog.flight;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.core.darkfog.Flight;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * 巢穴派出去的中继站和种子（ADR-0028 §八），存在 {@code data/universe/dark_fog_flights.dat}：每次飞行的出发点、目的地、几个时刻、
 * 挑好的落点和实体的血量。实体不存档。
 *
 * <p>另外记着每颗星球上玩家设施的一个位置（星球加载着时每分钟更新）：星球没加载时中继站照这个挑落点。
 */
public final class DarkFogFlightData extends SavedData {
    /** 存档格式版本。改格式时加一，并在读取时迁移旧格式（技术架构 §3.10）。 */
    private static final int FORMAT = 1;

    private static final Codec<Flight.Kind> KIND = Codec.STRING.xmap(name -> Flight.Kind.valueOf(name.toUpperCase(Locale.ROOT)),
            kind -> kind.name().toLowerCase(Locale.ROOT));

    private static final Codec<Flight> FLIGHT = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.fieldOf("id").forGetter(Flight::id),
            KIND.fieldOf("kind").forGetter(Flight::kind),
            Codec.INT.fieldOf("system").forGetter(Flight::system),
            Codec.INT.optionalFieldOf("target", -1).forGetter(Flight::target),
            Codec.STRING.optionalFieldOf("dimension", "").forGetter(Flight::dimension),
            Codec.INT.optionalFieldOf("level", 0).forGetter(Flight::level),
            Codec.DOUBLE.listOf().fieldOf("from").forGetter(flight -> List.of(flight.fromX(), flight.fromY(), flight.fromZ())),
            Codec.LONG.listOf().fieldOf("times").forGetter(flight -> List.of(flight.launched(), flight.departEnd(), flight.arriveStart(), flight.end())),
            Codec.INT.optionalFieldOf("attempts", 0).forGetter(Flight::attempts),
            Codec.INT.listOf().optionalFieldOf("site", List.of()).forGetter(flight -> flight.siteChosen()
                    ? List.of(flight.siteX(), flight.siteZ(), flight.baseId()) : List.of()),
            Codec.FLOAT.optionalFieldOf("health", 1.0F).forGetter(Flight::health)
    ).apply(i, (id, kind, system, target, dimension, level, from, times, attempts, site, health) -> new Flight(id, kind, system, target, dimension,
            level, padDoubles(from, 3), padLongs(times, 4), attempts, site.stream().mapToInt(Integer::intValue).toArray(), health)));

    public static final Codec<DarkFogFlightData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.optionalFieldOf("format").forGetter(data -> Optional.of(FORMAT)),
            Codec.INT.optionalFieldOf("next_id", 0).forGetter(data -> data.nextId),
            FLIGHT.listOf().optionalFieldOf("flights", List.of()).forGetter(data -> data.flights),
            Codec.unboundedMap(Codec.STRING, Codec.INT.listOf()).optionalFieldOf("facilities", Map.of()).forGetter(data -> data.facilities)
    ).apply(i, (format, nextId, flights, facilities) -> new DarkFogFlightData(nextId, flights, facilities)));

    public static final SavedDataType<DarkFogFlightData> TYPE = new SavedDataType<>(Universe.id("dark_fog_flights"), DarkFogFlightData::new, CODEC);

    private int nextId;
    private final List<Flight> flights = new ArrayList<>();
    /** 星球维度 → 那里玩家设施的一个位置 {x, z}。 */
    private final Map<String, List<Integer>> facilities = new HashMap<>();

    private DarkFogFlightData() {
    }

    private DarkFogFlightData(int nextId, List<Flight> flights, Map<String, List<Integer>> facilities) {
        this.nextId = nextId;
        this.flights.addAll(flights);
        facilities.forEach((key, pos) -> {
            if (pos.size() >= 2) {
                this.facilities.put(key, List.copyOf(pos));
            }
        });
        for (Flight flight : flights) {
            this.nextId = Math.max(this.nextId, flight.id() + 1);
        }
    }

    public static DarkFogFlightData get(MinecraftServer server) {
        return server.getDataStorage().computeIfAbsent(TYPE);
    }

    public List<Flight> flights() {
        return this.flights;
    }

    public Optional<Flight> flight(int id) {
        return this.flights.stream().filter(flight -> flight.id() == id).findFirst();
    }

    int nextId() {
        int id = this.nextId++;
        this.setDirty();
        return id;
    }

    void add(Flight flight) {
        this.flights.add(flight);
        this.setDirty();
    }

    boolean remove(int id) {
        boolean removed = this.flights.removeIf(flight -> flight.id() == id);
        if (removed) {
            this.setDirty();
        }
        return removed;
    }

    /** 这颗星球上玩家设施的一个位置 {x, z}；没记过是空。 */
    public Optional<int[]> facility(String dimension) {
        List<Integer> pos = this.facilities.get(dimension);
        return pos == null ? Optional.empty() : Optional.of(new int[] {pos.get(0), pos.get(1)});
    }

    /** 记下玩家设施的位置（星球加载着时每分钟记一次；游戏内测试也用）。 */
    public void setFacility(String dimension, int x, int z) {
        List<Integer> old = this.facilities.get(dimension);
        if (old == null || old.get(0) != x || old.get(1) != z) {
            this.facilities.put(dimension, List.of(x, z));
            this.setDirty();
        }
    }

    /** 游戏内测试用：忘掉记下的位置。 */
    public void removeFacility(String dimension) {
        if (this.facilities.remove(dimension) != null) {
            this.setDirty();
        }
    }

    private static double[] padDoubles(List<Double> list, int size) {
        double[] values = new double[size];
        for (int i = 0; i < Math.min(size, list.size()); i++) {
            values[i] = list.get(i);
        }
        return values;
    }

    private static long[] padLongs(List<Long> list, int size) {
        long[] values = new long[size];
        for (int i = 0; i < Math.min(size, list.size()); i++) {
            values[i] = list.get(i);
        }
        return values;
    }
}
