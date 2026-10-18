package io.github.sweetcornna.universe.discovery;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.core.discovery.DiscoveryKey;
import io.github.sweetcornna.universe.core.discovery.DiscoveryLog;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * 全服共享的发现日志，存在 {@code data/universe/discoveries.dat}（ADR-0012）。东西用 {@link DiscoveryKey#id()} 存成字符串，
 * 解析不了的条目（以后删掉的种类）读档时跳过。
 */
public final class DiscoveryData extends SavedData {
    /** 存档格式版本。改格式时加一，并在读取时迁移旧格式（技术架构 §3.10）。 */
    private static final int FORMAT = 1;

    private record Stored(String key, Optional<UUID> by, String byName, long day, Optional<String> name) {
        static final Codec<Stored> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("key").forGetter(Stored::key),
                UUIDUtil.CODEC.optionalFieldOf("by").forGetter(Stored::by),
                Codec.STRING.optionalFieldOf("by_name", "").forGetter(Stored::byName),
                Codec.LONG.optionalFieldOf("day", 0L).forGetter(Stored::day),
                Codec.STRING.optionalFieldOf("name").forGetter(Stored::name)
        ).apply(i, Stored::new));

        static Stored of(DiscoveryLog.Entry entry) {
            return new Stored(entry.key().id(), Optional.ofNullable(entry.discoverer()), entry.discovererName(), entry.day(),
                    Optional.ofNullable(entry.customName()));
        }

        Optional<DiscoveryLog.Entry> entry() {
            return DiscoveryKey.parse(this.key).map(key -> new DiscoveryLog.Entry(key, this.by.orElse(null), this.byName, this.day,
                    this.name.orElse(null)));
        }
    }

    private static final Codec<DiscoveryData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.optionalFieldOf("format").forGetter(data -> Optional.of(FORMAT)),
            Stored.CODEC.listOf().fieldOf("entries").forGetter(data -> data.log.entries().stream().map(Stored::of).toList())
    ).apply(i, (format, entries) -> new DiscoveryData(entries)));

    public static final SavedDataType<DiscoveryData> TYPE = new SavedDataType<>(Universe.id("discoveries"), DiscoveryData::new, CODEC);

    private final DiscoveryLog log = new DiscoveryLog();

    private DiscoveryData() {
    }

    private DiscoveryData(List<Stored> entries) {
        for (Stored stored : entries) {
            stored.entry().ifPresent(this.log::put);
        }
    }

    public static DiscoveryData get(MinecraftServer server) {
        return server.getDataStorage().computeIfAbsent(TYPE);
    }

    /** 改了之后要调 {@link #setDirty()}；一般走 {@link Discoveries} 里的方法，它们会存盘并同步给玩家。 */
    public DiscoveryLog log() {
        return this.log;
    }
}
