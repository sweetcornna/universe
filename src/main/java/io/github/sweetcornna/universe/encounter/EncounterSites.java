package io.github.sweetcornna.universe.encounter;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.github.sweetcornna.universe.Universe;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * 太空里放下的遭遇（ADR-0024），存在 {@code data/universe/encounters.dat}：废弃货船、遇险的货船放在哪、谁的、什么时候拆。
 * 方块本身存在太空维度里；记下来是为了到点拆掉，重开服务器也不会留下拆不掉的船壳。
 */
public final class EncounterSites extends SavedData {
    /** 存档格式版本。改格式时加一，并在读取时迁移旧格式（技术架构 §3.10）。 */
    private static final int FORMAT = 1;

    public enum Kind {
        DERELICT, DISTRESS;

        static final Codec<Kind> CODEC = Codec.STRING.xmap(name -> Kind.valueOf(name.toUpperCase(Locale.ROOT)),
                kind -> kind.name().toLowerCase(Locale.ROOT));
    }

    /**
     * @param seed    船的种子，也是这处遭遇的编号
     * @param x       外壳的原点（船底中线）
     * @param expires 游戏刻数，到点拆
     */
    public record Site(long seed, Kind kind, int system, int x, int y, int z, UUID owner, long expires) {
        static final Codec<Site> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.LONG.fieldOf("seed").forGetter(Site::seed),
                Kind.CODEC.fieldOf("kind").forGetter(Site::kind),
                Codec.INT.fieldOf("system").forGetter(Site::system),
                Codec.INT.fieldOf("x").forGetter(Site::x),
                Codec.INT.fieldOf("y").forGetter(Site::y),
                Codec.INT.fieldOf("z").forGetter(Site::z),
                UUIDUtil.CODEC.fieldOf("owner").forGetter(Site::owner),
                Codec.LONG.fieldOf("expires").forGetter(Site::expires)
        ).apply(i, Site::new));
    }

    public static final Codec<EncounterSites> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.optionalFieldOf("format").forGetter(data -> Optional.of(FORMAT)),
            Site.CODEC.listOf().optionalFieldOf("sites", List.of()).forGetter(data -> data.sites)
    ).apply(i, (format, sites) -> new EncounterSites(sites)));

    public static final SavedDataType<EncounterSites> TYPE = new SavedDataType<>(Universe.id("encounters"), EncounterSites::new, CODEC);

    private final List<Site> sites = new ArrayList<>();

    private EncounterSites() {
    }

    private EncounterSites(List<Site> sites) {
        this.sites.addAll(sites);
    }

    public static EncounterSites get(MinecraftServer server) {
        return server.getDataStorage().computeIfAbsent(TYPE);
    }

    public List<Site> all() {
        return List.copyOf(this.sites);
    }

    public Optional<Site> find(long seed) {
        return this.sites.stream().filter(site -> site.seed() == seed).findFirst();
    }

    /** 这名玩家还没拆的遭遇。 */
    public Optional<Site> of(UUID owner) {
        return this.sites.stream().filter(site -> site.owner().equals(owner)).findFirst();
    }

    void add(Site site) {
        this.sites.add(site);
        this.setDirty();
    }

    void replace(Site site) {
        this.sites.replaceAll(old -> old.seed() == site.seed() ? site : old);
        this.setDirty();
    }

    void remove(long seed) {
        if (this.sites.removeIf(site -> site.seed() == seed)) {
            this.setDirty();
        }
    }
}
