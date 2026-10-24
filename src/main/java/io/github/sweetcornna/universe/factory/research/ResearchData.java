package io.github.sweetcornna.universe.factory.research;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.core.research.ResearchState;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * 整个世界共用的研究进度，存在 {@code data/universe/research.dat}（ADR-0011）。科技 ID 存成字符串：
 * 以后删掉的科技读进来只是多一条用不上的记录，不会让整份进度解析失败。
 */
public final class ResearchData extends SavedData {
    /**
     * 存档格式版本。改格式时加一，并在读取时迁移旧格式（技术架构 §3.10）。2：加了升级科技的级数（ADR-0018），
     * 旧存档没有这一项，读成全是 0 级。研究队列（ADR-0033 §五）是可选的一项，旧存档读成空队列，不用加版本。
     */
    private static final int FORMAT = 2;

    private static final Codec<ResearchData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.optionalFieldOf("format").forGetter(data -> Optional.of(FORMAT)),
            Codec.STRING.listOf().fieldOf("done").forGetter(data -> List.copyOf(data.state.done())),
            Codec.STRING.optionalFieldOf("current").forGetter(data -> Optional.ofNullable(data.state.current())),
            Codec.unboundedMap(Codec.STRING, Codec.INT).fieldOf("progress").forGetter(data -> data.state.progress()),
            Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("levels", Map.of()).forGetter(data -> data.state.levels()),
            Codec.STRING.listOf().optionalFieldOf("queue", List.of()).forGetter(data -> List.copyOf(data.state.queue()))
    ).apply(i, (format, done, current, progress, levels, queue) ->
            new ResearchData(new ResearchState(new LinkedHashSet<>(done), current.orElse(null), progress, levels, queue))));

    public static final SavedDataType<ResearchData> TYPE = new SavedDataType<>(Universe.id("research"), ResearchData::new, CODEC);

    private final ResearchState state;

    private ResearchData() {
        this(new ResearchState());
    }

    private ResearchData(ResearchState state) {
        this.state = state;
    }

    public static ResearchData get(MinecraftServer server) {
        return server.getDataStorage().computeIfAbsent(TYPE);
    }

    /** 改了之后要调 {@link #setDirty()}；一般走 {@link Research} 里的方法，它们会存盘并同步给玩家。 */
    public ResearchState state() {
        return this.state;
    }
}
