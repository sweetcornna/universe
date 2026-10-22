package io.github.sweetcornna.universe.darkfog;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.github.sweetcornna.universe.Universe;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * 一个维度里设施的耐久（ADR-0028 §四），存在维度的 {@code data/universe/facilities.dat}：只记掉过血、还没修满的方块，
 * 连着当时是什么方块，方块换了就作废。残影的位置不存档，残影方块加载时自己登记（只有已加载的残影能修）。
 */
public final class FacilityData extends SavedData {
    /** 存档格式版本。改格式时加一，并在读取时迁移旧格式（技术架构 §3.10）。 */
    private static final int FORMAT = 1;

    /** 一条耐久记录：方块位置、当时的方块 ID、还剩多少。 */
    public record Entry(long pos, String block, float health) {
        static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.LONG.fieldOf("pos").forGetter(Entry::pos),
                Codec.STRING.fieldOf("block").forGetter(Entry::block),
                Codec.FLOAT.fieldOf("health").forGetter(Entry::health)
        ).apply(i, Entry::new));
    }

    public static final Codec<FacilityData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.optionalFieldOf("format").forGetter(data -> Optional.of(FORMAT)),
            Entry.CODEC.listOf().optionalFieldOf("damaged", List.of()).forGetter(data -> List.copyOf(data.damaged.values()))
    ).apply(i, (format, damaged) -> new FacilityData(damaged)));

    public static final SavedDataType<FacilityData> TYPE = new SavedDataType<>(Universe.id("facilities"), FacilityData::new, CODEC);

    private final Map<Long, Entry> damaged = new HashMap<>();
    /** 已加载的残影。 */
    private final Set<Long> ghosts = new HashSet<>();
    /** 建造无人机认领了、正在飞过去的位置（修或者重建）。 */
    private final Set<Long> claimed = new HashSet<>();

    private FacilityData() {
    }

    private FacilityData(List<Entry> damaged) {
        for (Entry entry : damaged) {
            this.damaged.put(entry.pos(), entry);
        }
    }

    public static FacilityData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(TYPE);
    }

    // ---- 耐久

    /** 记着的耐久；方块 ID 对不上（拆了换了别的）时没有。 */
    public Optional<Float> health(long pos, String block) {
        Entry entry = this.damaged.get(pos);
        return entry != null && entry.block().equals(block) ? Optional.of(entry.health()) : Optional.empty();
    }

    public void setHealth(long pos, String block, float health) {
        this.damaged.put(pos, new Entry(pos, block, health));
        this.setDirty();
    }

    public void clearHealth(long pos) {
        if (this.damaged.remove(pos) != null) {
            this.setDirty();
        }
    }

    /** 掉过血的位置（拷贝，调用方可以边看边改）。 */
    public List<Entry> damaged() {
        return new ArrayList<>(this.damaged.values());
    }

    public boolean anyDamaged() {
        return !this.damaged.isEmpty();
    }

    // ---- 残影

    public void addGhost(long pos) {
        this.ghosts.add(pos);
    }

    public void removeGhost(long pos) {
        this.ghosts.remove(pos);
        this.claimed.remove(pos);
    }

    public Set<Long> ghosts() {
        return Collections.unmodifiableSet(this.ghosts);
    }

    // ---- 无人机认领

    public boolean claim(long pos) {
        return this.claimed.add(pos);
    }

    public void release(long pos) {
        this.claimed.remove(pos);
    }

    public boolean claimed(long pos) {
        return this.claimed.contains(pos);
    }
}
