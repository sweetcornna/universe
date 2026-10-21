package io.github.sweetcornna.universe.blueprint;

import java.util.Arrays;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;
import java.util.stream.LongStream;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.github.sweetcornna.universe.core.blueprint.BlueprintGeometry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 一个工地（ADR-0023）：一次放下的蓝图留下的虚影。每格记着要放的方块状态、设备设置；建好一格就从 {@link #remaining} 里去掉。
 *
 * <p>位置相对于 {@link #origin}（工地的最小角）用 {@link BlueprintGeometry#pack} 压成整数，顺序是从下往上。
 */
public final class BuildSite {
    private static final Codec<int[]> INTS = Codec.INT_STREAM.xmap(IntStream::toArray, Arrays::stream);
    private static final Codec<long[]> LONGS = Codec.LONG_STREAM.xmap(LongStream::toArray, Arrays::stream);
    static final Codec<BuildSite> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.fieldOf("id").forGetter(site -> site.id),
            UUIDUtil.CODEC.fieldOf("owner").forGetter(site -> site.owner),
            ResourceKey.codec(Registries.DIMENSION).fieldOf("dimension").forGetter(site -> site.dimension),
            BlockPos.CODEC.fieldOf("origin").forGetter(site -> site.origin),
            BlockState.CODEC.listOf().fieldOf("palette").forGetter(site -> site.palette),
            INTS.fieldOf("positions").forGetter(site -> site.positions),
            INTS.fieldOf("states").forGetter(site -> site.states),
            Codec.unboundedMap(Codec.STRING, CompoundTag.CODEC).optionalFieldOf("settings", Map.of())
                    .forGetter(site -> BuildSite.stringKeys(site.settings)),
            LONGS.fieldOf("remaining").forGetter(site -> site.remaining.toLongArray()),
            Codec.INT.optionalFieldOf("placed", 0).forGetter(site -> site.placed)
    ).apply(i, BuildSite::new));

    private final int id;
    private final UUID owner;
    private final ResourceKey<Level> dimension;
    private final BlockPos origin;
    private final List<BlockState> palette;
    private final int[] positions;
    private final int[] states;
    private final Map<Integer, CompoundTag> settings;
    private final BitSet remaining;
    private int placed;
    /** 被无人机认领了、正在去建的格子（不存档）。 */
    final BitSet claimed = new BitSet();
    /** 这一阵先不看的格子：被占着、缺材料（不存档，隔一会儿清掉重新看）。 */
    final BitSet skipped = new BitSet();
    /** 变了几次（同步用）。 */
    int version;

    BuildSite(int id, UUID owner, ResourceKey<Level> dimension, BlockPos origin, List<BlockState> palette, int[] positions, int[] states,
            Map<String, CompoundTag> settings, long[] remaining, int placed) {
        this.id = id;
        this.owner = owner;
        this.dimension = dimension;
        this.origin = origin;
        this.palette = List.copyOf(palette);
        this.positions = positions;
        this.states = states;
        this.settings = intKeys(settings);
        this.remaining = BitSet.valueOf(remaining);
        this.placed = placed;
    }

    /** 新工地：全部没建。 */
    static BuildSite create(int id, UUID owner, ResourceKey<Level> dimension, BlockPos origin, List<BlockState> palette, int[] positions,
            int[] states, Map<Integer, CompoundTag> settings) {
        BitSet all = new BitSet(positions.length);
        all.set(0, positions.length);
        return new BuildSite(id, owner, dimension, origin, palette, positions, states, stringKeys(settings), all.toLongArray(), 0);
    }

    public int id() {
        return this.id;
    }

    public UUID owner() {
        return this.owner;
    }

    public ResourceKey<Level> dimension() {
        return this.dimension;
    }

    public BlockPos origin() {
        return this.origin;
    }

    public int size() {
        return this.positions.length;
    }

    public List<BlockState> palette() {
        return this.palette;
    }

    int[] positions() {
        return this.positions;
    }

    int[] states() {
        return this.states;
    }

    public BlockPos pos(int index) {
        int packed = this.positions[index];
        return this.origin.offset(BlueprintGeometry.x(packed), BlueprintGeometry.y(packed), BlueprintGeometry.z(packed));
    }

    public BlockState state(int index) {
        return this.palette.get(this.states[index]);
    }

    public @Nullable CompoundTag settings(int index) {
        return this.settings.get(index);
    }

    public @Nullable Item cost(int index) {
        return Blueprints.cost(this.state(index));
    }

    public BitSet remaining() {
        return this.remaining;
    }

    public boolean isRemaining(int index) {
        return this.remaining.get(index);
    }

    public int left() {
        return this.remaining.cardinality();
    }

    public int placed() {
        return this.placed;
    }

    /** 这一格建好了（或者被别的办法填上了）。 */
    void finish(int index, boolean byDrone) {
        if (this.remaining.get(index)) {
            this.remaining.clear(index);
            this.claimed.clear(index);
            this.skipped.clear(index);
            if (byDrone) {
                this.placed++;
            }
            this.version++;
        }
    }

    /** 这一格让给后放的工地。 */
    void drop(int index) {
        this.finish(index, false);
    }

    /** 工地的水平范围（最小 x、z，最大 x、z）。 */
    int[] bounds() {
        int maxX = 0;
        int maxZ = 0;
        for (int packed : this.positions) {
            maxX = Math.max(maxX, BlueprintGeometry.x(packed));
            maxZ = Math.max(maxZ, BlueprintGeometry.z(packed));
        }
        return new int[] {this.origin.getX(), this.origin.getZ(), this.origin.getX() + maxX, this.origin.getZ() + maxZ};
    }

    private static Map<Integer, CompoundTag> intKeys(Map<String, CompoundTag> map) {
        Map<Integer, CompoundTag> result = new HashMap<>();
        map.forEach((key, value) -> {
            try {
                result.put(Integer.parseInt(key), value);
            } catch (NumberFormatException ignored) {
                // 坏掉的条目跳过
            }
        });
        return result;
    }

    private static Map<String, CompoundTag> stringKeys(Map<Integer, CompoundTag> map) {
        Map<String, CompoundTag> result = new HashMap<>();
        map.forEach((key, value) -> result.put(Integer.toString(key), value));
        return result;
    }
}
