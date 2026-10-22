package io.github.sweetcornna.universe.sentinel;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.github.sweetcornna.universe.Universe;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * 守卫机撤走到什么时候（ADR-0026），存在 {@code data/universe/sentinel_dormancy.dat}：键是星球维度，值是主世界的游戏刻数。
 * 守卫塔的终端关停、打光五级通缉都会让一颗星球的守卫机停一阵，重开服务器也接着算。
 */
public final class SentinelDormancy extends SavedData {
    /** 存档格式版本。改格式时加一，并在读取时迁移旧格式（技术架构 §3.10）。 */
    private static final int FORMAT = 1;

    public static final Codec<SentinelDormancy> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.optionalFieldOf("format").forGetter(data -> Optional.of(FORMAT)),
            Codec.unboundedMap(Codec.STRING, Codec.LONG).optionalFieldOf("until", Map.of()).forGetter(data -> data.until)
    ).apply(i, (format, until) -> new SentinelDormancy(until)));

    public static final SavedDataType<SentinelDormancy> TYPE = new SavedDataType<>(Universe.id("sentinel_dormancy"), SentinelDormancy::new, CODEC);

    private final Map<String, Long> until = new HashMap<>();

    private SentinelDormancy() {
    }

    private SentinelDormancy(Map<String, Long> until) {
        this.until.putAll(until);
    }

    public static SentinelDormancy get(MinecraftServer server) {
        return server.getDataStorage().computeIfAbsent(TYPE);
    }

    /** 这颗星球的守卫机停到哪一刻；没停时为 0。 */
    public long until(String dimension) {
        return this.until.getOrDefault(dimension, 0L);
    }

    /** 提前结束这颗星球的停机。 */
    public void clear(String dimension) {
        if (this.until.remove(dimension) != null) {
            this.setDirty();
        }
    }

    /** 停到 {@code time}；已经停得更久就不变。顺手清掉过期的。 */
    public void extend(String dimension, long time, long now) {
        this.until.values().removeIf(t -> t <= now);
        if (time > this.until(dimension)) {
            this.until.put(dimension, time);
            this.setDirty();
        }
    }
}
