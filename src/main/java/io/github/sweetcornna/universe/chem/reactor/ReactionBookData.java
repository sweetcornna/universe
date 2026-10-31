package io.github.sweetcornna.universe.chem.reactor;

import java.util.List;
import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.core.chem.reaction.ReactionBook;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * 全服发现了的反应（ADR-0037 §六），存在 {@code data/universe/reaction_book.dat}。改动走 {@link ReactionBooks}，它会存盘并同步给玩家。
 * {@code migrated} 记着有没有从老存档的产量图里补过：1.1.0 以前做过的反应算发现了。
 */
public final class ReactionBookData extends SavedData {
    /** 存档格式版本。改格式时加一，并在读取时迁移旧格式。 */
    private static final int FORMAT = 1;

    private static final Codec<ReactionBookData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.optionalFieldOf("format").forGetter(data -> Optional.of(FORMAT)),
            Codec.BOOL.optionalFieldOf("migrated", false).forGetter(data -> data.migrated),
            Codec.STRING.listOf().optionalFieldOf("discovered", List.of()).forGetter(data -> data.book.names())
    ).apply(i, (format, migrated, discovered) -> new ReactionBookData(ReactionBook.load(discovered), migrated)));

    public static final SavedDataType<ReactionBookData> TYPE = new SavedDataType<>(Universe.id("reaction_book"), ReactionBookData::new, CODEC);

    private final ReactionBook book;
    private boolean migrated;

    private ReactionBookData() {
        this(new ReactionBook(), false);
    }

    private ReactionBookData(ReactionBook book, boolean migrated) {
        this.book = book;
        this.migrated = migrated;
    }

    /** 改了之后要调 {@link #setDirty()}。 */
    public ReactionBook book() {
        return this.book;
    }

    boolean migrated() {
        return this.migrated;
    }

    void markMigrated() {
        this.migrated = true;
        this.setDirty();
    }
}
