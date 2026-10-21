package io.github.sweetcornna.universe.blueprint;

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
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.github.sweetcornna.universe.Universe;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * 所有维度的工地（ADR-0023），存在 {@code data/universe/build_sites.dat}。另外按维度记着每个虚影位置属于哪个工地的哪一格，
 * 后放的工地盖掉先放的同一格。
 */
public final class BuildSites extends SavedData {
    private static final int FORMAT = 1;
    private static final Codec<BuildSites> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.optionalFieldOf("format").forGetter(data -> Optional.of(FORMAT)),
            Codec.INT.optionalFieldOf("next_id", 1).forGetter(data -> data.nextId),
            Codec.lazyInitialized(() -> BuildSite.CODEC).listOf().optionalFieldOf("sites", List.of())
                    .forGetter(data -> List.copyOf(data.sites.values()))
    ).apply(i, (format, nextId, sites) -> new BuildSites(nextId, sites)));
    public static final SavedDataType<BuildSites> TYPE = new SavedDataType<>(Universe.id("build_sites"), BuildSites::new, CODEC);

    private int nextId = 1;
    private final Map<Integer, BuildSite> sites = new LinkedHashMap<>();
    /** 维度 → 打包坐标 → 工地编号（高 32 位）和格子下标（低 32 位）。 */
    private final Map<ResourceKey<Level>, Map<Long, Long>> index = new HashMap<>();

    private BuildSites() {
    }

    private BuildSites(int nextId, List<BuildSite> sites) {
        this.nextId = nextId;
        for (BuildSite site : sites) {
            if (site.left() > 0) {
                this.sites.put(site.id(), site);
                this.indexSite(site);
            }
        }
    }

    public static BuildSites get(MinecraftServer server) {
        return server.getDataStorage().computeIfAbsent(TYPE);
    }

    private void indexSite(BuildSite site) {
        Map<Long, Long> map = this.index.computeIfAbsent(site.dimension(), key -> new HashMap<>());
        for (int i = site.remaining().nextSetBit(0); i >= 0; i = site.remaining().nextSetBit(i + 1)) {
            map.put(site.pos(i).asLong(), (long) site.id() << 32 | i);
        }
    }

    /** 新工地。和已有工地重叠的格子归新的。 */
    public BuildSite add(UUID owner, ResourceKey<Level> dimension, BlockPos origin, List<BlockState> palette, int[] positions, int[] states,
            Map<Integer, CompoundTag> settings) {
        BuildSite site = BuildSite.create(this.nextId++, owner, dimension, origin, palette, positions, states, settings);
        Map<Long, Long> map = this.index.computeIfAbsent(dimension, key -> new HashMap<>());
        List<BuildSite> touched = new ArrayList<>();
        for (int i = 0; i < site.size(); i++) {
            Long old = map.put(site.pos(i).asLong(), (long) site.id() << 32 | i);
            if (old != null) {
                BuildSite other = this.sites.get((int) (old >>> 32));
                if (other != null) {
                    other.drop((int) (long) old);
                    touched.add(other);
                }
            }
        }
        this.sites.put(site.id(), site);
        for (BuildSite other : touched) {
            if (other.left() == 0) {
                this.sites.remove(other.id());
            }
        }
        this.setDirty();
        return site;
    }

    public @Nullable BuildSite site(int id) {
        return this.sites.get(id);
    }

    public Collection<BuildSite> all() {
        return this.sites.values();
    }

    /** 这个维度里的工地。 */
    public List<BuildSite> in(ResourceKey<Level> dimension) {
        List<BuildSite> list = new ArrayList<>();
        for (BuildSite site : this.sites.values()) {
            if (site.dimension().equals(dimension)) {
                list.add(site);
            }
        }
        return list;
    }

    /** 这个位置上的虚影：工地编号和格子下标；没有时为空。 */
    public long @Nullable [] ghostAt(ResourceKey<Level> dimension, BlockPos pos) {
        Map<Long, Long> map = this.index.get(dimension);
        Long value = map == null ? null : map.get(pos.asLong());
        return value == null ? null : new long[] {value >>> 32, (int) (long) value};
    }

    /**
     * 一格建好了。
     *
     * @return 工地因此建完了（已经去掉）
     */
    public boolean finish(BuildSite site, int index, boolean byDrone) {
        if (!site.isRemaining(index)) {
            return false;
        }
        site.finish(index, byDrone);
        Map<Long, Long> map = this.index.get(site.dimension());
        if (map != null) {
            map.remove(site.pos(index).asLong(), (long) site.id() << 32 | index);
        }
        this.setDirty();
        if (site.left() == 0) {
            this.sites.remove(site.id());
            return true;
        }
        return false;
    }

    /** 去掉一个工地。 */
    public void remove(BuildSite site) {
        if (this.sites.remove(site.id()) == null) {
            return;
        }
        Map<Long, Long> map = this.index.get(site.dimension());
        if (map != null) {
            for (int i = site.remaining().nextSetBit(0); i >= 0; i = site.remaining().nextSetBit(i + 1)) {
                map.remove(site.pos(i).asLong(), (long) site.id() << 32 | i);
            }
        }
        site.remaining().clear();
        site.version++;
        this.setDirty();
    }
}
