package io.github.sweetcornna.universe.portal;

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
 * 已经放下的先驱者传送门（ADR-0024），存在 {@code data/universe/portals.dat}：维度 ID → 石台中心的台面那一格和门前的朝向。
 * 位置本身由种子算得出来，存下来是因为放的时候按当时的地形定了高度。
 */
public final class PortalSites extends SavedData {
    /** 存档格式版本。改格式时加一，并在读取时迁移旧格式（技术架构 §3.10）。 */
    private static final int FORMAT = 1;

    /** @param front 门前的朝向（{@code Direction#get2DDataValue}） */
    public record Placed(int x, int y, int z, int front) {
        static final Codec<Placed> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("x").forGetter(Placed::x),
                Codec.INT.fieldOf("y").forGetter(Placed::y),
                Codec.INT.fieldOf("z").forGetter(Placed::z),
                Codec.INT.fieldOf("front").forGetter(Placed::front)
        ).apply(i, Placed::new));
    }

    public static final Codec<PortalSites> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.optionalFieldOf("format").forGetter(data -> Optional.of(FORMAT)),
            Codec.unboundedMap(Codec.STRING, Placed.CODEC).optionalFieldOf("placed", Map.of()).forGetter(data -> data.placed)
    ).apply(i, (format, placed) -> new PortalSites(placed)));

    public static final SavedDataType<PortalSites> TYPE = new SavedDataType<>(Universe.id("portals"), PortalSites::new, CODEC);

    private final Map<String, Placed> placed = new HashMap<>();

    private PortalSites() {
    }

    private PortalSites(Map<String, Placed> placed) {
        this.placed.putAll(placed);
    }

    public static PortalSites get(MinecraftServer server) {
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
}
