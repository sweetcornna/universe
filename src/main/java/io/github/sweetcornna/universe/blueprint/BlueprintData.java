package io.github.sweetcornna.universe.blueprint;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.core.blueprint.BlueprintMeta;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * 所有画好的蓝图，存在 {@code data/universe/blueprints.dat}（ADR-0018）。物品上只记编号，免得一块 32×32×32 的蓝图
 * 塞进物品数据、每次同步都发一遍。
 */
public final class BlueprintData extends SavedData {
    /** 存档格式版本。改格式时加一，并在读取时迁移旧格式（技术架构 §3.10）。 */
    private static final int FORMAT = 1;

    /**
     * 一张蓝图的内容。
     *
     * @param palette   用到的方块状态
     * @param positions 每个方块在蓝图里的位置（{@code BlueprintGeometry.pack}）
     * @param states    每个方块的状态在 palette 里的下标
     * @param settings  第几个方块 → 它的设备设置
     * @param meta      作者、版本、说明（ADR-0033 §九），M18 以前存的蓝图没有作者、是第 1 版
     */
    public record Stored(String name, int sizeX, int sizeY, int sizeZ, List<BlockState> palette, int[] positions, int[] states,
            Map<Integer, CompoundTag> settings, Meta meta) {
        private static final Codec<int[]> INTS = Codec.INT_STREAM.xmap(IntStream::toArray, Arrays::stream);
        static final Codec<Stored> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("name").forGetter(Stored::name),
                Codec.INT.fieldOf("size_x").forGetter(Stored::sizeX),
                Codec.INT.fieldOf("size_y").forGetter(Stored::sizeY),
                Codec.INT.fieldOf("size_z").forGetter(Stored::sizeZ),
                BlockState.CODEC.listOf().fieldOf("palette").forGetter(Stored::palette),
                INTS.fieldOf("positions").forGetter(Stored::positions),
                INTS.fieldOf("states").forGetter(Stored::states),
                Codec.unboundedMap(Codec.STRING, CompoundTag.CODEC).xmap(BlueprintData::intKeys, BlueprintData::stringKeys)
                        .optionalFieldOf("settings", Map.of()).forGetter(Stored::settings),
                Meta.MAP_CODEC.forGetter(Stored::meta)
        ).apply(i, Stored::new));

        public int blocks() {
            return this.positions.length;
        }

        public Stored withMeta(Meta meta) {
            return new Stored(this.name, this.sizeX, this.sizeY, this.sizeZ, this.palette, this.positions, this.states, this.settings, meta);
        }
    }

    /** 蓝图的作者（保存的玩家）、版本号（同名的再存一次加 1）、说明。 */
    public record Meta(String author, int version, String description) {
        public static final Meta NONE = new Meta("", 1, "");
        static final com.mojang.serialization.MapCodec<Meta> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.STRING.optionalFieldOf("author", "").forGetter(Meta::author),
                Codec.INT.optionalFieldOf("version", 1).forGetter(Meta::version),
                Codec.STRING.optionalFieldOf("description", "").forGetter(Meta::description)
        ).apply(i, Meta::new));
        static final net.minecraft.network.codec.StreamCodec<io.netty.buffer.ByteBuf, Meta> STREAM_CODEC =
                net.minecraft.network.codec.StreamCodec.composite(
                        net.minecraft.network.codec.ByteBufCodecs.STRING_UTF8, Meta::author,
                        net.minecraft.network.codec.ByteBufCodecs.VAR_INT, Meta::version,
                        net.minecraft.network.codec.ByteBufCodecs.STRING_UTF8, Meta::description,
                        Meta::new);
    }

    private static final Codec<BlueprintData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.optionalFieldOf("format").forGetter(data -> Optional.of(FORMAT)),
            Codec.INT.optionalFieldOf("next_id", 1).forGetter(data -> data.nextId),
            // 延迟取：先用到 Stored 时，它的初始化会反过来触发这个类的初始化，那时 Stored.CODEC 还是 null
            Codec.unboundedMap(Codec.STRING, Codec.lazyInitialized(() -> Stored.CODEC)).xmap(BlueprintData::intKeys, BlueprintData::stringKeys)
                    .optionalFieldOf("blueprints", Map.of()).forGetter(data -> data.blueprints)
    ).apply(i, (format, nextId, blueprints) -> new BlueprintData(nextId, blueprints)));

    public static final SavedDataType<BlueprintData> TYPE = new SavedDataType<>(Universe.id("blueprints"), BlueprintData::new, CODEC);

    private int nextId = 1;
    private final Map<Integer, Stored> blueprints = new HashMap<>();

    private BlueprintData() {
    }

    private BlueprintData(int nextId, Map<Integer, Stored> blueprints) {
        this.nextId = nextId;
        this.blueprints.putAll(blueprints);
    }

    public static BlueprintData get(MinecraftServer server) {
        return server.getDataStorage().computeIfAbsent(TYPE);
    }

    /** 存一张新蓝图，返回它的编号。 */
    public int add(Stored stored) {
        int id = this.nextId++;
        this.blueprints.put(id, stored);
        this.setDirty();
        return id;
    }

    public @Nullable Stored get(int id) {
        return this.blueprints.get(id);
    }

    /** 同名的蓝图再存一次是第几版（ADR-0033 §九）。 */
    public int nextVersion(String name) {
        return BlueprintMeta.nextVersion(this.blueprints.values().stream().filter(stored -> stored.name().equals(name))
                .map(stored -> stored.meta().version()).toList());
    }

    private static <V> Map<Integer, V> intKeys(Map<String, V> map) {
        Map<Integer, V> result = new HashMap<>();
        map.forEach((key, value) -> {
            try {
                result.put(Integer.parseInt(key), value);
            } catch (NumberFormatException ignored) {
                // 坏掉的条目跳过
            }
        });
        return result;
    }

    private static <V> Map<String, V> stringKeys(Map<Integer, V> map) {
        Map<String, V> result = new HashMap<>();
        map.forEach((key, value) -> result.put(Integer.toString(key), value));
        return result;
    }
}
