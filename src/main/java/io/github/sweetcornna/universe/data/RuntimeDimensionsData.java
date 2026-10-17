package io.github.sweetcornna.universe.data;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.github.sweetcornna.universe.Universe;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * 本模组在运行时创建过的维度清单，存在 {@code data/universe/runtime_dimensions.dat}。
 *
 * <p>每条记录包括维度 ID、地形模板名，以及维度当前是否加载。服务器启动时只重建处于加载状态的维度，
 * 其余的等有人前往时再加载（ADR-0004）。维度自己的区块、实体和数据照常存在 {@code dimensions/} 目录下。
 */
public final class RuntimeDimensionsData extends SavedData {
    /** 存档格式版本。改格式时加一，并在读取时迁移旧格式（技术架构 §3.10）。 */
    private static final int FORMAT = 1;

    /**
     * @param template 模板名，见 {@code world.DimensionTemplate}。存成字符串而不是枚举：读到不认识的模板时
     *                 只跳过这一条，不会因为整份清单解析失败而被空清单覆盖
     * @param loaded   维度当前是否加载，服务器启动时据此决定要不要重建
     */
    public record Entry(Identifier id, String template, boolean loaded) {
        private static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
                Identifier.CODEC.fieldOf("id").forGetter(Entry::id),
                Codec.STRING.fieldOf("template").forGetter(Entry::template),
                Codec.BOOL.fieldOf("loaded").forGetter(Entry::loaded)
        ).apply(i, Entry::new));
    }

    // 版本号总是写出：optionalFieldOf(名称, 默认值) 遇到等于默认值的值不会写出，以后升版本就分不清新旧格式
    private static final Codec<RuntimeDimensionsData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.optionalFieldOf("format").forGetter(data -> Optional.of(FORMAT)),
            Entry.CODEC.listOf().fieldOf("dimensions").forGetter(data -> List.copyOf(data.entries.values()))
    ).apply(i, (format, entries) -> new RuntimeDimensionsData(entries)));

    public static final SavedDataType<RuntimeDimensionsData> TYPE =
            new SavedDataType<>(Universe.id("runtime_dimensions"), RuntimeDimensionsData::new, CODEC);

    /** 按创建顺序排列，启动时也按这个顺序重建。 */
    private final Map<Identifier, Entry> entries = new LinkedHashMap<>();

    private RuntimeDimensionsData() {
    }

    private RuntimeDimensionsData(List<Entry> entries) {
        entries.forEach(entry -> this.entries.put(entry.id(), entry));
    }

    public static RuntimeDimensionsData get(MinecraftServer server) {
        return server.getDataStorage().computeIfAbsent(TYPE);
    }

    public Collection<Entry> entries() {
        return Collections.unmodifiableCollection(this.entries.values());
    }

    public Optional<Entry> get(Identifier id) {
        return Optional.ofNullable(this.entries.get(id));
    }

    /** 新增一条记录；已有同 ID 的记录时覆盖它，顺序不变。 */
    public void put(Entry entry) {
        this.entries.put(entry.id(), entry);
        this.setDirty();
    }

    public void setLoaded(Identifier id, boolean loaded) {
        Entry entry = this.entries.get(id);
        if (entry != null && entry.loaded() != loaded) {
            this.put(new Entry(id, entry.template(), loaded));
        }
    }
}
