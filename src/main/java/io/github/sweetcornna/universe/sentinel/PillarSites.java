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
 * 守卫塔（ADR-0026），存在 {@code data/universe/pillars.dat}：维度 ID → 已经放下的塔（底座中心那一格、终端朝哪），
 * 以及终端上次关停守卫机是第几个游戏日。位置由种子算得出来，存下来是因为放的时候按当时的地形定了高度。
 */
public final class PillarSites extends SavedData {
    /** 存档格式版本。改格式时加一，并在读取时迁移旧格式（技术架构 §3.10）。 */
    private static final int FORMAT = 1;

    /** @param front 终端朝哪（{@code Direction#get2DDataValue}） */
    public record Placed(int x, int y, int z, int front) {
        static final Codec<Placed> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("x").forGetter(Placed::x),
                Codec.INT.fieldOf("y").forGetter(Placed::y),
                Codec.INT.fieldOf("z").forGetter(Placed::z),
                Codec.INT.fieldOf("front").forGetter(Placed::front)
        ).apply(i, Placed::new));
    }

    public static final Codec<PillarSites> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.optionalFieldOf("format").forGetter(data -> Optional.of(FORMAT)),
            Codec.unboundedMap(Codec.STRING, Placed.CODEC).optionalFieldOf("placed", Map.of()).forGetter(data -> data.placed),
            Codec.unboundedMap(Codec.STRING, Codec.LONG).optionalFieldOf("shutdown_day", Map.of()).forGetter(data -> data.shutdownDay)
    ).apply(i, (format, placed, shutdownDay) -> new PillarSites(placed, shutdownDay)));

    public static final SavedDataType<PillarSites> TYPE = new SavedDataType<>(Universe.id("pillars"), PillarSites::new, CODEC);

    private final Map<String, Placed> placed = new HashMap<>();
    private final Map<String, Long> shutdownDay = new HashMap<>();

    private PillarSites() {
    }

    private PillarSites(Map<String, Placed> placed, Map<String, Long> shutdownDay) {
        this.placed.putAll(placed);
        this.shutdownDay.putAll(shutdownDay);
    }

    public static PillarSites get(MinecraftServer server) {
        return server.getDataStorage().computeIfAbsent(TYPE);
    }

    public Optional<Placed> find(String dimension) {
        return Optional.ofNullable(this.placed.get(dimension));
    }

    void put(String dimension, Placed site) {
        this.placed.put(dimension, site);
        this.setDirty();
    }

    void remove(String dimension) {
        if (this.placed.remove(dimension) != null) {
            this.setDirty();
        }
    }

    /** 这颗星球的塔上次关停守卫机是第几个游戏日；没关停过为 -1。 */
    long shutdownDay(String dimension) {
        return this.shutdownDay.getOrDefault(dimension, -1L);
    }

    void markShutdown(String dimension, long day) {
        this.shutdownDay.put(dimension, day);
        this.setDirty();
    }
}
