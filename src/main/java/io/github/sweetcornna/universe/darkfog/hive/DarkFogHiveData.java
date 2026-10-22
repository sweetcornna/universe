package io.github.sweetcornna.universe.darkfog.hive;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.core.darkfog.HiveState;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * 黑雾的太空巢穴（ADR-0028 §八），存在 {@code data/universe/dark_fog_hives.dat}：每个恒星系最多一座，记着位置和状态
 * （等级、经验、物质、威胁、每座建筑的血量、港口里的单位数、几个计时）。建筑和单位的实体不存档，玩家飞近时按这里放出来。
 *
 * <p>{@code planned} 记着开局的巢穴放过没有：放过以后改"初始占领"不会再多出、少掉巢穴。
 */
public final class DarkFogHiveData extends SavedData {
    /** 存档格式版本。改格式时加一，并在读取时迁移旧格式（技术架构 §3.10）。 */
    private static final int FORMAT = 1;

    private static final Codec<HiveState.Status> STATUS = Codec.STRING.xmap(name -> HiveState.Status.valueOf(name.toUpperCase(Locale.ROOT)),
            status -> status.name().toLowerCase(Locale.ROOT));

    static final Codec<HiveState> HIVE = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.fieldOf("system").forGetter(HiveState::system),
            Codec.DOUBLE.listOf().fieldOf("pos").forGetter(hive -> List.of(hive.x(), hive.y(), hive.z())),
            STATUS.fieldOf("status").forGetter(HiveState::status),
            Codec.INT.optionalFieldOf("level", 0).forGetter(HiveState::level),
            Codec.DOUBLE.optionalFieldOf("xp", 0.0).forGetter(HiveState::xp),
            Codec.DOUBLE.optionalFieldOf("matter", 0.0).forGetter(HiveState::matter),
            Codec.DOUBLE.optionalFieldOf("threat", 0.0).forGetter(HiveState::threat),
            Codec.FLOAT.listOf().optionalFieldOf("health", List.of()).forGetter(hive -> floats(hive.healthArray())),
            Codec.INT.listOf().optionalFieldOf("units", List.of()).forGetter(hive -> ints(hive.unitArray())),
            Codec.DOUBLE.listOf().optionalFieldOf("progress", List.of()).forGetter(hive -> doubles(hive.progressArray())),
            Codec.LONG.optionalFieldOf("updated", 0L).forGetter(HiveState::updated),
            Codec.INT.listOf().optionalFieldOf("timers", List.of()).forGetter(hive -> ints(hive.timers()))
    ).apply(i, (system, pos, status, level, xp, matter, threat, health, units, progress, updated, timers) ->
            new HiveState(system, pos.get(0), pos.get(1), pos.get(2), status, level, xp, matter, threat, toFloats(health), toInts(units),
                    toDoubles(progress), updated, toInts(timers))));

    public static final Codec<DarkFogHiveData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.optionalFieldOf("format").forGetter(data -> Optional.of(FORMAT)),
            Codec.BOOL.optionalFieldOf("planned", false).forGetter(data -> data.planned),
            HIVE.listOf().optionalFieldOf("hives", List.of()).forGetter(data -> List.copyOf(data.hives.values()))
    ).apply(i, (format, planned, hives) -> new DarkFogHiveData(planned, hives)));

    public static final SavedDataType<DarkFogHiveData> TYPE = new SavedDataType<>(Universe.id("dark_fog_hives"), DarkFogHiveData::new, CODEC);

    private final Map<Integer, HiveState> hives = new HashMap<>();
    private boolean planned;

    private DarkFogHiveData() {
    }

    private DarkFogHiveData(boolean planned, List<HiveState> hives) {
        this.planned = planned;
        for (HiveState hive : hives) {
            this.hives.put(hive.system(), hive);
        }
    }

    public static DarkFogHiveData get(MinecraftServer server) {
        return server.getDataStorage().computeIfAbsent(TYPE);
    }

    public Optional<HiveState> hive(int system) {
        return Optional.ofNullable(this.hives.get(system));
    }

    /** 所有的巢穴，按恒星系编号（彻底消灭的也在里面）。 */
    public Map<Integer, HiveState> hives() {
        return this.hives;
    }

    void put(HiveState hive) {
        this.hives.put(hive.system(), hive);
        this.setDirty();
    }

    void remove(int system) {
        if (this.hives.remove(system) != null) {
            this.setDirty();
        }
    }

    boolean planned() {
        return this.planned;
    }

    void markPlanned() {
        this.planned = true;
        this.setDirty();
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
