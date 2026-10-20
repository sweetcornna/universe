package io.github.sweetcornna.universe.story;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.core.story.Milestone;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * 整个世界的剧情进度，存在 {@code data/universe/story.dat}（ADR-0018）：到过哪些节点、在主世界的第几刻到的。
 * 节点 ID 存成字符串，不认识的照样读进来、照样存回去。
 */
public final class StoryData extends SavedData {
    /** 存档格式版本。改格式时加一，并在读取时迁移旧格式（技术架构 §3.10）。 */
    private static final int FORMAT = 1;

    private static final Codec<StoryData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.optionalFieldOf("format").forGetter(data -> Optional.of(FORMAT)),
            Codec.unboundedMap(Codec.STRING, Codec.LONG).optionalFieldOf("milestones", Map.of()).forGetter(data -> data.milestones)
    ).apply(i, (format, milestones) -> new StoryData(milestones)));

    public static final SavedDataType<StoryData> TYPE = new SavedDataType<>(Universe.id("story"), StoryData::new, CODEC);

    private final Map<String, Long> milestones = new HashMap<>();

    private StoryData() {
    }

    private StoryData(Map<String, Long> milestones) {
        this.milestones.putAll(milestones);
    }

    public static StoryData get(MinecraftServer server) {
        return server.getDataStorage().computeIfAbsent(TYPE);
    }

    public boolean reached(Milestone milestone) {
        return this.milestones.containsKey(milestone.id());
    }

    /** 记下到了这个节点；第一次到时返回真（要自己 {@link #setDirty()}）。 */
    public boolean reach(Milestone milestone, long gameTime) {
        return this.milestones.putIfAbsent(milestone.id(), gameTime) == null;
    }

    /** 到过的节点 → 到的时候（主世界的游戏刻）。 */
    public Map<String, Long> milestones() {
        return Map.copyOf(this.milestones);
    }

    /** 调试用：忘掉一个节点。 */
    public void forget(Milestone milestone) {
        this.milestones.remove(milestone.id());
    }

    public void clear() {
        this.milestones.clear();
    }
}
