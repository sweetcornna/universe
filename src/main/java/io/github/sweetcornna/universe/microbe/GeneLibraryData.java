package io.github.sweetcornna.universe.microbe;

import java.util.List;
import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.core.microbe.GeneLibrary;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * 全服共享的基因库（ADR-0036 §五），存在 {@code data/universe/gene_library.dat}：测出的基因、测过的菌，都按名字存，
 * 读到不认识的基因名跳过。改动走 {@link GeneLibraries}，它会存盘并同步给玩家。
 */
public final class GeneLibraryData extends SavedData {
    /** 存档格式版本。改格式时加一，并在读取时迁移旧格式。 */
    private static final int FORMAT = 1;

    private static final Codec<GeneLibraryData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.optionalFieldOf("format").forGetter(data -> Optional.of(FORMAT)),
            Codec.STRING.listOf().optionalFieldOf("genes", List.of()).forGetter(data -> data.library.geneNames()),
            Codec.STRING.listOf().optionalFieldOf("strains", List.of()).forGetter(data -> List.copyOf(data.library.strains()))
    ).apply(i, (format, genes, strains) -> new GeneLibraryData(GeneLibrary.load(genes, strains))));

    public static final SavedDataType<GeneLibraryData> TYPE = new SavedDataType<>(Universe.id("gene_library"), GeneLibraryData::new, CODEC);

    private final GeneLibrary library;

    private GeneLibraryData() {
        this(new GeneLibrary());
    }

    private GeneLibraryData(GeneLibrary library) {
        this.library = library;
    }

    public static GeneLibraryData get(MinecraftServer server) {
        return server.getDataStorage().computeIfAbsent(TYPE);
    }

    /** 改了之后要调 {@link #setDirty()}。 */
    public GeneLibrary library() {
        return this.library;
    }
}
