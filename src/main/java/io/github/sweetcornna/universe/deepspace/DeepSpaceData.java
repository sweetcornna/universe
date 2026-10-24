package io.github.sweetcornna.universe.deepspace;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.core.deepspace.DeepSpaceSites.Kind;
import io.github.sweetcornna.universe.core.deepspace.Integrity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * 深空兴趣点的状态（ADR-0034 §四），存在 {@code data/universe/deep_space.dat}：哪些残骸、前哨站已经搭在太空里了（搭在哪、朝哪），
 * 残骸的结构完整度和熔毁倒计时，碎片带里哪几块已经打掉了。方块本身存在太空维度里。
 *
 * <p>每个星系按种子定下的兴趣点用 {@code "星系/序号"} 当键；开发命令放的残骸用 {@code "custom/编号"}。
 */
public final class DeepSpaceData extends SavedData {
    /** 存档格式版本。改格式时加一，并在读取时迁移旧格式（技术架构 §3.10）。 */
    private static final int FORMAT = 1;
    private static final Codec<Kind> KIND_CODEC = Codec.STRING.xmap(name -> Kind.valueOf(name.toUpperCase(Locale.ROOT)), Kind::key);

    /** 一处兴趣点。 */
    public static final class State {
        final String key;
        final Kind kind;
        /** 搭在哪个维度、原点、转了几个 90°；没搭的时候维度是空串。 */
        String dimension = "";
        long origin;
        int rotation;
        long seed;
        /** 残骸的长度；0 表示按种子定。 */
        int length;
        boolean built;
        final Integrity integrity;
        /** 碎片带：打掉了的序号。 */
        final BitSet cleared;
        /** 碎片带：全打光以后什么时候（游戏刻）重新长出来；0 表示没在等。 */
        long resetAt;

        State(String key, Kind kind) {
            this(key, kind, "", 0, 0, 0, 0, false, new Integrity(), new BitSet(), 0);
        }

        State(String key, Kind kind, String dimension, long origin, int rotation, long seed, int length, boolean built, Integrity integrity,
                BitSet cleared, long resetAt) {
            this.key = key;
            this.kind = kind;
            this.dimension = dimension;
            this.origin = origin;
            this.rotation = rotation;
            this.seed = seed;
            this.length = length;
            this.built = built;
            this.integrity = integrity;
            this.cleared = cleared;
            this.resetAt = resetAt;
        }

        public String key() {
            return this.key;
        }

        public Kind kind() {
            return this.kind;
        }

        public boolean built() {
            return this.built;
        }

        public Integrity integrity() {
            return this.integrity;
        }

        public boolean cleared(int slot) {
            return this.cleared.get(slot);
        }
    }

    private record Saved(String key, Kind kind, String dimension, long origin, int rotation, long seed, int length, boolean built, int integrity,
            int countdown, boolean salvaged, boolean gone, List<Integer> cleared, long resetAt) {
        static final Codec<Saved> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("key").forGetter(Saved::key),
                KIND_CODEC.fieldOf("kind").forGetter(Saved::kind),
                Codec.STRING.optionalFieldOf("dimension", "").forGetter(Saved::dimension),
                Codec.LONG.optionalFieldOf("origin", 0L).forGetter(Saved::origin),
                Codec.INT.optionalFieldOf("rotation", 0).forGetter(Saved::rotation),
                Codec.LONG.optionalFieldOf("seed", 0L).forGetter(Saved::seed),
                Codec.INT.optionalFieldOf("length", 0).forGetter(Saved::length),
                Codec.BOOL.optionalFieldOf("built", false).forGetter(Saved::built),
                Codec.INT.optionalFieldOf("integrity", Integrity.FULL).forGetter(Saved::integrity),
                Codec.INT.optionalFieldOf("countdown", -1).forGetter(Saved::countdown),
                Codec.BOOL.optionalFieldOf("salvaged", false).forGetter(Saved::salvaged),
                Codec.BOOL.optionalFieldOf("gone", false).forGetter(Saved::gone),
                Codec.INT.listOf().optionalFieldOf("cleared", List.of()).forGetter(Saved::cleared),
                Codec.LONG.optionalFieldOf("reset_at", 0L).forGetter(Saved::resetAt)
        ).apply(i, Saved::new));

        static Saved of(State state) {
            List<Integer> cleared = new ArrayList<>();
            state.cleared.stream().forEach(cleared::add);
            Integrity integrity = state.integrity;
            return new Saved(state.key, state.kind, state.dimension, state.origin, state.rotation, state.seed, state.length, state.built,
                    integrity.value(), integrity.countdown(), integrity.salvaged(), integrity.gone(), cleared, state.resetAt);
        }

        State state() {
            BitSet bits = new BitSet();
            this.cleared.forEach(bits::set);
            return new State(this.key, this.kind, this.dimension, this.origin, this.rotation, this.seed, this.length, this.built,
                    new Integrity(this.integrity, this.countdown, this.salvaged, this.gone), bits, this.resetAt);
        }
    }

    public static final Codec<DeepSpaceData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.optionalFieldOf("format").forGetter(data -> Optional.of(FORMAT)),
            Saved.CODEC.listOf().optionalFieldOf("sites", List.of()).forGetter(data -> data.states.values().stream().map(Saved::of).toList()),
            Codec.INT.optionalFieldOf("next_custom", 0).forGetter(data -> data.nextCustom)
    ).apply(i, (format, sites, next) -> new DeepSpaceData(sites, next)));

    public static final SavedDataType<DeepSpaceData> TYPE = new SavedDataType<>(Universe.id("deep_space"), DeepSpaceData::new, CODEC);

    private final Map<String, State> states = new LinkedHashMap<>();
    private int nextCustom;

    private DeepSpaceData() {
    }

    private DeepSpaceData(List<Saved> sites, int nextCustom) {
        for (Saved saved : sites) {
            this.states.put(saved.key(), saved.state());
        }
        this.nextCustom = nextCustom;
    }

    public static DeepSpaceData get(MinecraftServer server) {
        return server.getDataStorage().computeIfAbsent(TYPE);
    }

    /** 星系 {@code system} 第 {@code index} 个兴趣点的键。 */
    public static String key(int system, int index) {
        return system + "/" + index;
    }

    public State state(String key, Kind kind) {
        return this.states.computeIfAbsent(key, k -> {
            this.setDirty();
            return new State(k, kind);
        });
    }

    public Optional<State> find(String key) {
        return Optional.ofNullable(this.states.get(key));
    }

    public Collection<State> all() {
        return List.copyOf(this.states.values());
    }

    /** 开发命令放的残骸、前哨站：新开一个键。 */
    String newCustomKey() {
        this.setDirty();
        return "custom/" + this.nextCustom++;
    }

    void remove(String key) {
        if (this.states.remove(key) != null) {
            this.setDirty();
        }
    }

    void changed() {
        this.setDirty();
    }
}
