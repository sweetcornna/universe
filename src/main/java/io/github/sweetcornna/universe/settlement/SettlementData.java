package io.github.sweetcornna.universe.settlement;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.core.civ.Race;
import io.github.sweetcornna.universe.core.fishing.Habitat;
import io.github.sweetcornna.universe.core.settlement.BuildingType;
import io.github.sweetcornna.universe.core.settlement.Plot;
import io.github.sweetcornna.universe.core.settlement.Product;
import io.github.sweetcornna.universe.core.settlement.Settlement;
import io.github.sweetcornna.universe.fishing.Fishing;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * 接管过的聚落（ADR-0034 §二），存在 {@code data/universe/settlements.dat}：每个聚落的数值（{@link Settlement}），
 * 鱼塘、瞭望塔攒下还没取的东西，每块地基在世界里现在盖成了什么样（{@link Entry#shown()}，区块没加载时施工也照走，
 * 下次加载再按数值补盖），以及鱼塘上次读到的水（{@link Entry#ponds()}）。
 */
public final class SettlementData extends SavedData {
    /** 存档格式版本。改格式时加一，并在读取时迁移旧格式（技术架构 §3.10）。 */
    private static final int FORMAT = 1;

    /** 一个聚落的一份。 */
    public static final class Entry {
        private final Settlement settlement;
        private final List<ItemStack> stock;
        private final int[] shown;
        private final int[] plotY;
        private final String name;
        private final Fishing.@Nullable Water[] ponds;

        Entry(Settlement settlement, List<ItemStack> stock, int[] shown, int[] plotY, String name, Fishing.@Nullable Water[] ponds) {
            this.settlement = settlement;
            this.stock = new ArrayList<>(stock);
            this.shown = shown;
            this.plotY = plotY;
            this.name = name;
            this.ponds = ponds;
        }

        public Settlement settlement() {
            return this.settlement;
        }

        /** 鱼塘、瞭望塔攒下的东西。 */
        public List<ItemStack> stock() {
            return this.stock;
        }

        /** 每块地基在世界里盖成的样子（{@link SettlementBuilder#signature}），-1 是还没盖过。 */
        public int[] shown() {
            return this.shown;
        }

        /** 每块地基整平以后的地面高度；{@link Integer#MIN_VALUE} 是还没整平。 */
        public int[] plotY() {
            return this.plotY;
        }

        /** 地基都整平过了。 */
        public boolean prepared() {
            for (int y : this.plotY) {
                if (y == Integer.MIN_VALUE) {
                    return false;
                }
            }
            return true;
        }

        /** 聚落的名字（外星语，接管时按种子起）。 */
        public String name() {
            return this.name;
        }

        /** 每块鱼塘地基上次加载着时读到的水（区块没加载时每天的鱼按它挑）；null 是还没记过。 */
        public Fishing.@Nullable Water[] ponds() {
            return this.ponds;
        }
    }

    /** 记下来的一块鱼塘的水。 */
    private record Pond(int plot, Fishing.Water water) {
    }

    private static final MapCodec<Fishing.Water> WATER = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.STRING.xmap(SettlementData::habitat, Habitat::key).fieldOf("habitat").forGetter(Fishing.Water::habitat),
            Codec.INT.optionalFieldOf("depth", 1).forGetter(Fishing.Water::depth),
            Codec.BOOL.optionalFieldOf("acid", false).forGetter(Fishing.Water::acid)
    ).apply(i, Fishing.Water::new));

    private static final Codec<Pond> POND = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.fieldOf("plot").forGetter(Pond::plot),
            WATER.forGetter(Pond::water)
    ).apply(i, Pond::new));

    private static final Codec<Settlement> SETTLEMENT = CompoundTag.CODEC.xmap(SettlementData::read, SettlementData::write);

    private static final Codec<Entry> ENTRY = RecordCodecBuilder.create(i -> i.group(
            SETTLEMENT.fieldOf("settlement").forGetter(Entry::settlement),
            ItemStack.OPTIONAL_CODEC.listOf().optionalFieldOf("stock", List.of()).forGetter(Entry::stock),
            Codec.INT.listOf().optionalFieldOf("shown", List.of()).forGetter(e -> java.util.Arrays.stream(e.shown).boxed().toList()),
            Codec.INT.listOf().optionalFieldOf("plot_y", List.of()).forGetter(e -> java.util.Arrays.stream(e.plotY).boxed().toList()),
            Codec.STRING.optionalFieldOf("name", "").forGetter(Entry::name),
            POND.listOf().optionalFieldOf("ponds", List.of()).forGetter(SettlementData::pondList)
    ).apply(i, (settlement, stock, shown, plotY, name, ponds) -> new Entry(settlement, stock.stream().filter(s -> !s.isEmpty()).toList(),
            ints(shown, settlement, -1), ints(plotY, settlement, Integer.MIN_VALUE), name, ponds(ponds, settlement))));

    public static final Codec<SettlementData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.optionalFieldOf("format", FORMAT).forGetter(d -> FORMAT),
            ENTRY.listOf().optionalFieldOf("settlements", List.of()).forGetter(d -> List.copyOf(d.entries.values()))
    ).apply(i, (format, entries) -> new SettlementData(entries)));

    public static final SavedDataType<SettlementData> TYPE = new SavedDataType<>(Universe.id("settlements"), SettlementData::new, CODEC);

    private final Map<String, Entry> entries = new LinkedHashMap<>();
    /** 维度 → 那里的聚落（查找用，不存档）。 */
    private final Map<String, List<Entry>> byDimension = new HashMap<>();

    public SettlementData() {
    }

    private SettlementData(List<Entry> list) {
        for (Entry entry : list) {
            this.entries.put(entry.settlement.id(), entry);
        }
        this.reindex();
    }

    public static SettlementData get(MinecraftServer server) {
        return server.getDataStorage().computeIfAbsent(TYPE);
    }

    private static int[] ints(List<Integer> list, Settlement settlement, int missing) {
        int[] values = new int[settlement.plotCount()];
        java.util.Arrays.fill(values, missing);
        for (int k = 0; k < values.length && k < list.size(); k++) {
            values[k] = list.get(k);
        }
        return values;
    }

    private static Fishing.@Nullable Water[] ponds(List<Pond> list, Settlement settlement) {
        Fishing.@Nullable Water[] ponds = new Fishing.Water[settlement.plotCount()];
        for (Pond pond : list) {
            if (pond.plot() >= 0 && pond.plot() < ponds.length) {
                ponds[pond.plot()] = pond.water();
            }
        }
        return ponds;
    }

    private static List<Pond> pondList(Entry entry) {
        List<Pond> list = new ArrayList<>();
        for (int k = 0; k < entry.ponds.length; k++) {
            if (entry.ponds[k] != null) {
                list.add(new Pond(k, entry.ponds[k]));
            }
        }
        return list;
    }

    /** 存档里的水域名；不认识的（以后删掉的）当淡水。 */
    private static Habitat habitat(String key) {
        for (Habitat habitat : Habitat.values()) {
            if (habitat.key().equals(key)) {
                return habitat;
            }
        }
        return Habitat.FRESH;
    }

    private void reindex() {
        this.byDimension.clear();
        for (Entry entry : this.entries.values()) {
            this.byDimension.computeIfAbsent(entry.settlement.dimension(), k -> new ArrayList<>()).add(entry);
        }
    }

    public Collection<Entry> all() {
        return this.entries.values();
    }

    public Optional<Entry> entry(String id) {
        return Optional.ofNullable(this.entries.get(id));
    }

    public List<Entry> in(String dimension) {
        return this.byDimension.getOrDefault(dimension, List.of());
    }

    /** 这个人管着的聚落。 */
    public List<Entry> ownedBy(UUID player) {
        List<Entry> list = new ArrayList<>();
        for (Entry entry : this.entries.values()) {
            if (entry.settlement.ownedBy(player)) {
                list.add(entry);
            }
        }
        return list;
    }

    /** 没有就记下这个聚落（第一次接管时）。 */
    public Entry add(Settlement settlement, String name) {
        Entry existing = this.entries.get(settlement.id());
        if (existing != null) {
            return existing;
        }
        Entry entry = new Entry(settlement, List.of(), ints(List.of(), settlement, -1), ints(List.of(), settlement, Integer.MIN_VALUE), name,
                ponds(List.of(), settlement));
        this.entries.put(settlement.id(), entry);
        this.reindex();
        this.setDirty();
        return entry;
    }

    /** 去掉（游戏内测试收尾用）。 */
    public void remove(String id) {
        if (this.entries.remove(id) != null) {
            this.reindex();
            this.setDirty();
        }
    }

    public void changed() {
        this.setDirty();
    }

    // ---- 读写

    public static CompoundTag write(Settlement s) {
        CompoundTag tag = new CompoundTag();
        tag.putString("dimension", s.dimension());
        tag.putLong("key", s.key());
        tag.putInt("x", s.x());
        tag.putInt("y", s.y());
        tag.putInt("z", s.z());
        tag.putInt("rotation", s.rotation());
        tag.putLong("seed", s.seed());
        tag.putString("race", s.race().id());
        s.owner().ifPresent(owner -> tag.putString("owner", owner.toString()));
        tag.putString("owner_name", s.ownerName());
        tag.putLong("claimed_day", s.claimedDay());
        tag.putInt("population", s.population());
        tag.putDouble("happiness", s.happiness());
        tag.putDouble("threat", s.threat());
        tag.putDouble("reserve", s.reserve());
        tag.putLong("debt", s.debt());
        tag.putLong("treasury", s.treasury());
        tag.putString("product", s.product().id());
        s.decision().ifPresent(d -> tag.putString("decision", d.id()));
        tag.putLong("day", s.day());
        tag.putLong("raid_at", s.raidAt());
        tag.putInt("raids_won", s.raidsWon());
        tag.putInt("raids_lost", s.raidsLost());
        ListTag boosts = new ListTag();
        for (Settlement.Boost boost : s.boosts()) {
            CompoundTag b = new CompoundTag();
            b.putInt("amount", boost.amount());
            b.putLong("until", boost.until());
            boosts.add(b);
        }
        tag.put("boosts", boosts);
        ListTag recent = new ListTag();
        for (String id : s.recentDecisions()) {
            recent.add(StringTag.valueOf(id));
        }
        tag.put("recent", recent);
        ListTag events = new ListTag();
        for (Settlement.Event event : s.events()) {
            CompoundTag e = new CompoundTag();
            e.putLong("day", event.day());
            e.putString("key", event.key());
            e.putString("arg", event.arg());
            events.add(e);
        }
        tag.put("events", events);
        ListTag plots = new ListTag();
        for (int i = 0; i < s.plotCount(); i++) {
            Plot plot = s.plot(i);
            CompoundTag p = new CompoundTag();
            if (plot.type() != null) {
                p.putString("type", plot.type().id());
                p.putInt("stage", plot.stage());
                p.putInt("grade", plot.grade());
                p.putBoolean("upgrading", plot.upgrading());
                p.putLong("ready_at", plot.readyAt());
                p.putLong("wait_start", plot.waitStart());
                p.putBoolean("opened", plot.opened());
                p.putBoolean("damaged", plot.damaged());
                CompoundTag delivered = new CompoundTag();
                plot.delivered().forEach(delivered::putInt);
                p.put("delivered", delivered);
            }
            plots.add(p);
        }
        tag.put("plots", plots);
        return tag;
    }

    public static Settlement read(CompoundTag tag) {
        Race race = Race.byId(tag.getStringOr("race", "trader")).orElse(Race.TRADER);
        Settlement s = new Settlement(tag.getStringOr("dimension", "minecraft:overworld"), tag.getLongOr("key", 0), tag.getIntOr("x", 0),
                tag.getIntOr("y", 64), tag.getIntOr("z", 0), tag.getIntOr("rotation", 0), tag.getLongOr("seed", 0), race);
        String owner = tag.getStringOr("owner", "");
        @Nullable UUID uuid = null;
        if (!owner.isEmpty()) {
            try {
                uuid = UUID.fromString(owner);
            } catch (IllegalArgumentException e) {
                uuid = null;
            }
        }
        String decision = tag.getStringOr("decision", "");
        s.restore(uuid, tag.getStringOr("owner_name", ""), tag.getLongOr("claimed_day", -1), tag.getIntOr("population", 3),
                tag.getDoubleOr("happiness", 50), tag.getDoubleOr("threat", 10), tag.getDoubleOr("reserve", 0), tag.getLongOr("debt", 0),
                tag.getLongOr("treasury", 0), Product.byId(tag.getStringOr("product", ""), Product.defaultFor(race)),
                decision.isEmpty() ? null : decision, tag.getLongOr("day", -1), tag.getLongOr("raid_at", -1), tag.getIntOr("raids_won", 0),
                tag.getIntOr("raids_lost", 0));
        List<Settlement.Boost> boosts = new ArrayList<>();
        for (Tag t : tag.getListOrEmpty("boosts")) {
            if (t instanceof CompoundTag b) {
                boosts.add(new Settlement.Boost(b.getIntOr("amount", 0), b.getLongOr("until", 0)));
            }
        }
        s.restoreBoosts(boosts);
        List<String> recent = new ArrayList<>();
        for (Tag t : tag.getListOrEmpty("recent")) {
            t.asString().ifPresent(recent::add);
        }
        s.restoreRecent(recent);
        List<Settlement.Event> events = new ArrayList<>();
        for (Tag t : tag.getListOrEmpty("events")) {
            if (t instanceof CompoundTag e) {
                events.add(new Settlement.Event(e.getLongOr("day", 0), e.getStringOr("key", ""), e.getStringOr("arg", "")));
            }
        }
        s.restoreEvents(events);
        ListTag plots = tag.getListOrEmpty("plots");
        for (int i = 0; i < s.plotCount() && i < plots.size(); i++) {
            if (!(plots.get(i) instanceof CompoundTag p) || p.getStringOr("type", "").isEmpty()) {
                continue;
            }
            BuildingType type;
            try {
                type = BuildingType.byId(p.getStringOr("type", ""));
            } catch (IllegalArgumentException e) {
                continue;
            }
            Map<String, Integer> delivered = new LinkedHashMap<>();
            CompoundTag d = p.getCompoundOrEmpty("delivered");
            for (String key : d.keySet()) {
                delivered.put(key, d.getIntOr(key, 0));
            }
            s.plot(i).restore(type, p.getIntOr("stage", 0), p.getIntOr("grade", 0), p.getBooleanOr("upgrading", false), p.getLongOr("ready_at", 0),
                    p.getLongOr("wait_start", 0), p.getBooleanOr("opened", false), p.getBooleanOr("damaged", false), delivered);
        }
        return s;
    }
}
