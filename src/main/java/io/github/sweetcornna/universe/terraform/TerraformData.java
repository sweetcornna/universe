package io.github.sweetcornna.universe.terraform;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.core.terraform.TerraformState;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * 星球改造的状态（ADR-0036 §六），整个世界一份，存在 {@code data/universe/terraform.dat}：每颗动过的星球一份
 * {@link TerraformState}，按天体路径（{@code s3/p2}）存；再加上每面轨道反射镜在哪、对着哪颗星球、是不是切成了遮阳板，
 * 以及哪几座生态播种站在给哪颗星球浇水。
 * 每一项都是可选的，旧存档没有就读成空的；以后删掉的星球、气体读进来只是多一条用不上的记录。
 */
public final class TerraformData extends SavedData {
    /** 存档格式版本。改格式时加一，并在读取时迁移旧格式（技术架构 §3.10）。 */
    private static final int FORMAT = 1;

    /** 一颗星球的状态，照 {@link TerraformState#fromStored} 的参数存。 */
    private record StateRecord(Map<String, Double> gases, double biomass, int mirrors, int shades, List<List<String>> pioneers, double irrigation,
            long lastUpdate, int announced) {
        static final Codec<StateRecord> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.unboundedMap(Codec.STRING, Codec.DOUBLE).optionalFieldOf("gases", Map.of()).forGetter(StateRecord::gases),
                Codec.DOUBLE.optionalFieldOf("biomass", 0.0).forGetter(StateRecord::biomass),
                Codec.INT.optionalFieldOf("mirrors", 0).forGetter(StateRecord::mirrors),
                Codec.INT.optionalFieldOf("shades", 0).forGetter(StateRecord::shades),
                Codec.STRING.listOf().listOf().optionalFieldOf("pioneers", List.of()).forGetter(StateRecord::pioneers),
                Codec.DOUBLE.optionalFieldOf("irrigation", 0.0).forGetter(StateRecord::irrigation),
                Codec.LONG.optionalFieldOf("last_update", 0L).forGetter(StateRecord::lastUpdate),
                Codec.INT.optionalFieldOf("announced", 0).forGetter(StateRecord::announced)
        ).apply(i, StateRecord::new));

        static StateRecord of(TerraformState state) {
            return new StateRecord(state.storedGases(), state.biomass(), state.mirrors(), state.shades(), state.storedPioneers(),
                    state.irrigation(), state.lastUpdate(), state.announced());
        }

        TerraformState toState() {
            return TerraformState.fromStored(this.gases, this.biomass, this.mirrors, this.shades, this.pioneers, this.irrigation, this.lastUpdate,
                    this.announced);
        }
    }

    /** 一面轨道反射镜：在哪个维度的哪一格，对着哪颗星球，是不是遮阳板。 */
    public record Mirror(String dimension, long pos, String planet, boolean shade) {
        static final Codec<Mirror> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("dimension").forGetter(Mirror::dimension),
                Codec.LONG.fieldOf("pos").forGetter(Mirror::pos),
                Codec.STRING.fieldOf("planet").forGetter(Mirror::planet),
                Codec.BOOL.optionalFieldOf("shade", false).forGetter(Mirror::shade)
        ).apply(i, Mirror::new));

        public BlockPos blockPos() {
            return BlockPos.of(this.pos);
        }

        String key() {
            return key(this.dimension, this.pos);
        }

        static String key(String dimension, long pos) {
            return dimension + "@" + pos;
        }
    }

    /** 一座在浇水的生态播种站：在哪个维度的哪一格，浇的是哪颗星球。 */
    public record Seeder(String dimension, long pos, String planet) {
        static final Codec<Seeder> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("dimension").forGetter(Seeder::dimension),
                Codec.LONG.fieldOf("pos").forGetter(Seeder::pos),
                Codec.STRING.fieldOf("planet").forGetter(Seeder::planet)
        ).apply(i, Seeder::new));

        String key() {
            return Mirror.key(this.dimension, this.pos);
        }
    }

    private static final Codec<TerraformData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.optionalFieldOf("format").forGetter(data -> Optional.of(FORMAT)),
            Codec.unboundedMap(Codec.STRING, StateRecord.CODEC).optionalFieldOf("planets", Map.of()).forGetter(TerraformData::records),
            Mirror.CODEC.listOf().optionalFieldOf("mirrors", List.of()).forGetter(data -> List.copyOf(data.mirrors.values())),
            Seeder.CODEC.listOf().optionalFieldOf("seeders", List.of()).forGetter(data -> List.copyOf(data.seeders.values()))
    ).apply(i, (format, planets, mirrors, seeders) -> {
        TerraformData data = new TerraformData();
        planets.forEach((path, record) -> data.states.put(path, record.toState()));
        mirrors.forEach(mirror -> data.mirrors.put(mirror.key(), mirror));
        seeders.forEach(seeder -> data.seeders.put(seeder.key(), seeder));
        return data;
    }));

    public static final SavedDataType<TerraformData> TYPE = new SavedDataType<>(Universe.id("terraform"), TerraformData::new, CODEC);

    /** 按天体路径排，存出来的文件每次一样。 */
    private final Map<String, TerraformState> states = new TreeMap<>();
    private final Map<String, Mirror> mirrors = new LinkedHashMap<>();
    private final Map<String, Seeder> seeders = new LinkedHashMap<>();

    private TerraformData() {
    }

    public static TerraformData get(MinecraftServer server) {
        return server.getDataStorage().computeIfAbsent(TYPE);
    }

    private Map<String, StateRecord> records() {
        Map<String, StateRecord> records = new TreeMap<>();
        this.states.forEach((path, state) -> {
            if (!blank(state)) {
                records.put(path, StateRecord.of(state));
            }
        });
        return records;
    }

    /** 什么都没动过（推进的时间不算）：不用存。 */
    static boolean blank(TerraformState state) {
        return state.isEmpty() && state.biomass() == 0 && state.pioneers().isEmpty() && state.irrigation() == 0 && state.announced() == 0;
    }

    /** 这颗星球的状态；没动过是 {@link TerraformState#EMPTY}。 */
    public TerraformState state(String path) {
        return this.states.getOrDefault(path, TerraformState.EMPTY);
    }

    /** 记下新状态并存盘。一般走 {@link Terraforming#update}，它还会提示阶段、同步给客户端。 */
    void put(String path, TerraformState state) {
        this.states.put(path, state);
        this.setDirty();
    }

    /** 动过的星球的路径和状态。 */
    public Map<String, TerraformState> states() {
        return Collections.unmodifiableMap(this.states);
    }

    // ---- 轨道反射镜

    public Optional<Mirror> mirror(String dimension, long pos) {
        return Optional.ofNullable(this.mirrors.get(Mirror.key(dimension, pos)));
    }

    void putMirror(Mirror mirror) {
        this.mirrors.put(mirror.key(), mirror);
        this.setDirty();
    }

    Optional<Mirror> removeMirror(String dimension, long pos) {
        Mirror removed = this.mirrors.remove(Mirror.key(dimension, pos));
        if (removed != null) {
            this.setDirty();
        }
        return Optional.ofNullable(removed);
    }

    public List<Mirror> mirrors() {
        return List.copyOf(this.mirrors.values());
    }

    // ---- 在浇水的生态播种站

    public Optional<Seeder> seeder(String dimension, long pos) {
        return Optional.ofNullable(this.seeders.get(Mirror.key(dimension, pos)));
    }

    void putSeeder(Seeder seeder) {
        this.seeders.put(seeder.key(), seeder);
        this.setDirty();
    }

    Optional<Seeder> removeSeeder(String dimension, long pos) {
        Seeder removed = this.seeders.remove(Mirror.key(dimension, pos));
        if (removed != null) {
            this.setDirty();
        }
        return Optional.ofNullable(removed);
    }

    /** 给这颗星球浇水的站有几座。 */
    public int seeders(String planet) {
        int count = 0;
        for (Seeder seeder : this.seeders.values()) {
            if (seeder.planet().equals(planet)) {
                count++;
            }
        }
        return count;
    }
}
