package io.github.sweetcornna.universe.fleet;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.core.fleet.Area;
import io.github.sweetcornna.universe.core.fleet.Expedition;
import io.github.sweetcornna.universe.core.fleet.Fleet;
import io.github.sweetcornna.universe.core.fleet.Frigate;
import io.github.sweetcornna.universe.core.fleet.FrigateTrait;
import io.github.sweetcornna.universe.core.fleet.FrigateType;
import io.github.sweetcornna.universe.core.tech.Grade;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * 各人的舰队（ADR-0024），存在 {@code data/universe/fleets.dat}：货船、护卫舰、远征（{@link Fleet}），外壳现在放在哪个
 * 恒星系的什么位置，边上停着哪几艘护卫舰，召唤货船的冷却。货船的编号全服递增，也是船舱维度的编号。
 */
public final class FleetData extends SavedData {
    /** 存档格式版本。改格式时加一，并在读取时迁移旧格式（技术架构 §3.10）。 */
    private static final int FORMAT = 1;

    /**
     * 外壳放在哪里。
     *
     * @param parked 每个泊位停着的护卫舰种类（{@link FrigateType} 的序号），空着是 -1
     */
    public record Placed(int system, int x, int y, int z, List<Integer> parked) {
        public Placed {
            parked = List.copyOf(parked);
        }

        static final Codec<Placed> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("system").forGetter(Placed::system),
                Codec.INT.fieldOf("x").forGetter(Placed::x),
                Codec.INT.fieldOf("y").forGetter(Placed::y),
                Codec.INT.fieldOf("z").forGetter(Placed::z),
                Codec.INT.listOf().optionalFieldOf("parked", List.of()).forGetter(Placed::parked)
        ).apply(i, Placed::new));
    }

    /** 一个人的一份。 */
    public static final class Entry {
        private final Fleet fleet;
        private @Nullable Placed placed;
        private long summonReady;

        Entry(Fleet fleet, @Nullable Placed placed, long summonReady) {
            this.fleet = fleet;
            this.placed = placed;
            this.summonReady = summonReady;
        }

        public Fleet fleet() {
            return this.fleet;
        }

        public Optional<Placed> placed() {
            return Optional.ofNullable(this.placed);
        }

        void setPlaced(@Nullable Placed placed) {
            this.placed = placed;
        }

        public long summonReady() {
            return this.summonReady;
        }

        void setSummonReady(long tick) {
            this.summonReady = tick;
        }
    }

    private static <E extends Enum<E>> Codec<E> named(Class<E> type) {
        return Codec.STRING.xmap(name -> Enum.valueOf(type, name.toUpperCase(Locale.ROOT)), value -> value.name().toLowerCase(Locale.ROOT));
    }

    private static final Codec<Frigate> FRIGATE = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.fieldOf("id").forGetter(Frigate::id),
            Codec.STRING.fieldOf("designation").forGetter(Frigate::designation),
            named(FrigateType.class).fieldOf("type").forGetter(Frigate::type),
            Codec.INT.listOf().fieldOf("base").forGetter(f -> java.util.Arrays.stream(f.baseStats()).boxed().toList()),
            named(FrigateTrait.class).listOf().optionalFieldOf("traits", List.of()).forGetter(Frigate::traits),
            Codec.INT.fieldOf("fuel").forGetter(Frigate::baseFuel),
            Codec.INT.optionalFieldOf("expeditions", 0).forGetter(Frigate::expeditions),
            Codec.BOOL.optionalFieldOf("damaged", false).forGetter(Frigate::damaged)
    ).apply(i, (id, designation, type, base, traits, fuel, expeditions, damaged) -> new Frigate(id, designation, type,
            fixed(base), traits, fuel, expeditions, damaged)));

    /** 四项属性；格式不对时补零截断，不让一艘坏数据的护卫舰毁掉整份存档。 */
    private static int[] fixed(List<Integer> base) {
        int[] stats = new int[Area.values().length];
        for (int k = 0; k < stats.length && k < base.size(); k++) {
            stats[k] = base.get(k);
        }
        return stats;
    }

    private static final Codec<Fleet.Freighter> FREIGHTER = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.fieldOf("id").forGetter(Fleet.Freighter::id),
            Codec.LONG.fieldOf("seed").forGetter(Fleet.Freighter::seed),
            named(Grade.class).fieldOf("grade").forGetter(Fleet.Freighter::grade)
    ).apply(i, Fleet.Freighter::new));

    private static final Codec<Expedition.Event> EVENT = RecordCodecBuilder.create(i -> i.group(
            named(Area.class).fieldOf("area").forGetter(Expedition.Event::area),
            Codec.BOOL.fieldOf("success").forGetter(Expedition.Event::success),
            Codec.INT.optionalFieldOf("frigate", -1).forGetter(Expedition.Event::frigate),
            named(Expedition.Outcome.class).optionalFieldOf("outcome", Expedition.Outcome.NONE).forGetter(Expedition.Event::outcome)
    ).apply(i, Expedition.Event::new));

    private static final Codec<Expedition.Offer> OFFER = RecordCodecBuilder.create(i -> i.group(
            named(Expedition.Kind.class).fieldOf("kind").forGetter(Expedition.Offer::kind),
            Codec.INT.fieldOf("difficulty").forGetter(Expedition.Offer::difficulty),
            Codec.INT.fieldOf("minutes").forGetter(Expedition.Offer::minutes)
    ).apply(i, Expedition.Offer::new));

    private static final Codec<Fleet.Voyage> VOYAGE = RecordCodecBuilder.create(i -> i.group(
            Codec.LONG.fieldOf("day").forGetter(Fleet.Voyage::day),
            Codec.INT.fieldOf("slot").forGetter(Fleet.Voyage::slot),
            OFFER.fieldOf("offer").forGetter(Fleet.Voyage::offer),
            Codec.INT.listOf().fieldOf("team").forGetter(Fleet.Voyage::team),
            Codec.LONG.fieldOf("start").forGetter(Fleet.Voyage::start),
            EVENT.listOf().fieldOf("events").forGetter(Fleet.Voyage::events),
            Codec.LONG.fieldOf("seed").forGetter(Fleet.Voyage::seed)
    ).apply(i, Fleet.Voyage::new));

    private static final Codec<Entry> ENTRY = RecordCodecBuilder.create(i -> i.group(
            FREIGHTER.optionalFieldOf("freighter").forGetter(e -> e.fleet.freighter()),
            FRIGATE.listOf().optionalFieldOf("frigates", List.of()).forGetter(e -> e.fleet.frigates()),
            Codec.INT.optionalFieldOf("next_frigate", 0).forGetter(e -> e.fleet.nextFrigate()),
            VOYAGE.listOf().optionalFieldOf("voyages", List.of()).forGetter(e -> e.fleet.voyages()),
            Codec.STRING.listOf().optionalFieldOf("taken", List.of()).forGetter(e -> List.copyOf(e.fleet.taken())),
            Placed.CODEC.optionalFieldOf("placed").forGetter(Entry::placed),
            Codec.LONG.optionalFieldOf("summon_ready", 0L).forGetter(Entry::summonReady)
    ).apply(i, (freighter, frigates, next, voyages, taken, placed, summon) -> new Entry(
            new Fleet(freighter.orElse(null), frigates, next, voyages, new HashSet<>(taken)), placed.orElse(null), summon)));

    public static final Codec<FleetData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.optionalFieldOf("format").forGetter(data -> Optional.of(FORMAT)),
            Codec.INT.optionalFieldOf("next_freighter", 0).forGetter(data -> data.nextFreighter),
            Codec.unboundedMap(UUIDUtil.STRING_CODEC, ENTRY).optionalFieldOf("fleets", Map.of()).forGetter(data -> data.fleets)
    ).apply(i, (format, next, fleets) -> new FleetData(next, fleets)));

    public static final SavedDataType<FleetData> TYPE = new SavedDataType<>(Universe.id("fleets"), FleetData::new, CODEC);

    private int nextFreighter;
    private final Map<UUID, Entry> fleets = new HashMap<>();

    private FleetData() {
    }

    private FleetData(int nextFreighter, Map<UUID, Entry> fleets) {
        this.nextFreighter = nextFreighter;
        this.fleets.putAll(fleets);
    }

    public static FleetData get(MinecraftServer server) {
        return server.getDataStorage().computeIfAbsent(TYPE);
    }

    /** 这个人的一份；没有时建一份空的。 */
    public Entry entry(UUID owner) {
        return this.fleets.computeIfAbsent(owner, id -> new Entry(new Fleet(), null, 0));
    }

    /** 去掉这个人的一份（测试用完收拾）。 */
    public void remove(UUID owner) {
        if (this.fleets.remove(owner) != null) {
            this.setDirty();
        }
    }

    public Optional<Entry> find(UUID owner) {
        return Optional.ofNullable(this.fleets.get(owner));
    }

    public Map<UUID, Entry> all() {
        return java.util.Collections.unmodifiableMap(this.fleets);
    }

    /** 货船编号对应的主人。 */
    public Optional<UUID> owner(int freighter) {
        for (Map.Entry<UUID, Entry> e : this.fleets.entrySet()) {
            if (e.getValue().fleet.freighter().map(f -> f.id() == freighter).orElse(false)) {
                return Optional.of(e.getKey());
            }
        }
        return Optional.empty();
    }

    /** 这个恒星系里放着外壳的货船。 */
    public List<Map.Entry<UUID, Entry>> inSystem(int system) {
        List<Map.Entry<UUID, Entry>> found = new ArrayList<>();
        for (Map.Entry<UUID, Entry> e : this.fleets.entrySet()) {
            if (e.getValue().placed != null && e.getValue().placed.system() == system) {
                found.add(e);
            }
        }
        return found;
    }

    public int takeFreighterId() {
        this.setDirty();
        return this.nextFreighter++;
    }

    /** 改了舰队里的东西以后调一下，存档时才会写。 */
    public void changed() {
        this.setDirty();
    }
}
