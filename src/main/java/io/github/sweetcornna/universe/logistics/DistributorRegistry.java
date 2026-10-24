package io.github.sweetcornna.universe.logistics;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.core.mecha.Distribution;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * 全服物流配送器的登记表（ADR-0033 §三），存在 {@code data/universe/distributors.dat}。配送器本身只在区块加载时才有，
 * 控制面板靠这张表把没加载的也列出来。
 *
 * <p>控制面板远程只改两种模式：配送器加载着就直接改它；没加载就先记在表里（{@code pending}），等它加载时照着改。
 */
public final class DistributorRegistry extends SavedData {
    /** 一个维度里的一个位置。 */
    public record Key(String dimension, BlockPos pos) {
    }

    /** 表里的一行：物品（空串是没设）、两种模式、机器人数；{@code pending} 是远程改了还没生效。 */
    public record Entry(String dimension, BlockPos pos, String item, Distribution.Icarus icarus, Distribution.Peer peer, int bots,
            boolean pending) {
        static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("dimension").forGetter(Entry::dimension),
                BlockPos.CODEC.fieldOf("pos").forGetter(Entry::pos),
                Codec.STRING.optionalFieldOf("item", "").forGetter(Entry::item),
                Codec.STRING.optionalFieldOf("icarus", "NONE").forGetter(entry -> entry.icarus.name()),
                Codec.STRING.optionalFieldOf("peer", "NONE").forGetter(entry -> entry.peer.name()),
                Codec.INT.optionalFieldOf("bots", 0).forGetter(Entry::bots),
                Codec.BOOL.optionalFieldOf("pending", false).forGetter(Entry::pending)
        ).apply(i, (dimension, pos, item, icarus, peer, bots, pending) -> new Entry(dimension, pos, item,
                enumOr(Distribution.Icarus.class, icarus, Distribution.Icarus.NONE), enumOr(Distribution.Peer.class, peer, Distribution.Peer.NONE),
                bots, pending)));

        public Key key() {
            return new Key(this.dimension, this.pos);
        }
    }

    private static final Codec<DistributorRegistry> CODEC = Entry.CODEC.listOf().xmap(entries -> {
        DistributorRegistry registry = new DistributorRegistry();
        entries.forEach(entry -> registry.entries.put(entry.key(), entry));
        return registry;
    }, registry -> List.copyOf(registry.entries.values()));

    public static final SavedDataType<DistributorRegistry> TYPE = new SavedDataType<>(Universe.id("distributors"), DistributorRegistry::new,
            CODEC);

    private final Map<Key, Entry> entries = new LinkedHashMap<>();

    private DistributorRegistry() {
    }

    public static DistributorRegistry get(MinecraftServer server) {
        return server.getDataStorage().computeIfAbsent(TYPE);
    }

    public Collection<Entry> entries() {
        return Collections.unmodifiableCollection(this.entries.values());
    }

    public @Nullable Entry get(Key key) {
        return this.entries.get(key);
    }

    static Key key(ServerLevel level, BlockPos pos) {
        return new Key(level.dimension().identifier().toString(), pos.immutable());
    }

    /**
     * 配送器加载了：表里有远程改过还没生效的模式就交给它，返回那一行；否则返回 {@code null}，再由它把现状记进来。
     */
    @Nullable Entry takePending(ServerLevel level, BlockPos pos) {
        Entry entry = this.entries.get(key(level, pos));
        return entry != null && entry.pending ? entry : null;
    }

    /** 记下这台配送器的现状（有变化才标脏）。 */
    void record(ServerLevel level, DistributorBlockEntity distributor) {
        Key key = key(level, distributor.getBlockPos());
        String item = distributor.filter() == null ? "" : BuiltInRegistries.ITEM.getKey(distributor.filter()).toString();
        Entry entry = new Entry(key.dimension(), key.pos(), item, distributor.icarus(), distributor.peer(), distributor.bots(), false);
        if (!entry.equals(this.entries.get(key))) {
            this.entries.put(key, entry);
            this.setDirty();
        }
    }

    /** 配送器拆掉了。 */
    void remove(ServerLevel level, BlockPos pos) {
        if (this.entries.remove(key(level, pos)) != null) {
            this.setDirty();
        }
    }

    void removeKey(Key key) {
        if (this.entries.remove(key) != null) {
            this.setDirty();
        }
    }

    /** 没加载的配送器：远程换到下一个模式，等它加载时生效。 */
    boolean cyclePending(Key key, int button) {
        Entry entry = this.entries.get(key);
        if (entry == null) {
            return false;
        }
        Entry changed = switch (button) {
            case DistributorBlockEntity.CYCLE_ICARUS -> new Entry(entry.dimension, entry.pos, entry.item, next(entry.icarus), entry.peer,
                    entry.bots, true);
            case DistributorBlockEntity.CYCLE_PEER -> new Entry(entry.dimension, entry.pos, entry.item, entry.icarus, next(entry.peer),
                    entry.bots, true);
            default -> null;
        };
        if (changed == null) {
            return false;
        }
        this.entries.put(key, changed);
        this.setDirty();
        return true;
    }

    static Distribution.Icarus next(Distribution.Icarus icarus) {
        Distribution.Icarus[] values = Distribution.Icarus.values();
        return values[(icarus.ordinal() + 1) % values.length];
    }

    static Distribution.Peer next(Distribution.Peer peer) {
        Distribution.Peer[] values = Distribution.Peer.values();
        return values[(peer.ordinal() + 1) % values.length];
    }

    /** 按维度挑出来（GameTest 和调试用）。 */
    public List<Entry> in(String dimension) {
        List<Entry> result = new ArrayList<>();
        for (Entry entry : this.entries.values()) {
            if (entry.dimension.equals(dimension)) {
                result.add(entry);
            }
        }
        return result;
    }

    private static <E extends Enum<E>> E enumOr(Class<E> type, String name, E fallback) {
        try {
            return Enum.valueOf(type, name);
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }
}
