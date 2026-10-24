package io.github.sweetcornna.universe.core.fishing;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 钓鱼图鉴（ADR-0034 §一），记在玩家身上：每种鱼钓了几条、最重的一条多重、在哪颗星球钓的；一共放生了几条；
 * 还有几次甩竿带着放生的加成。不可变，改了就换一份新的（好让附加数据同步）。
 */
public record FishingLog(Map<String, Entry> entries, int released, int releaseCasts) {
    public static final FishingLog EMPTY = new FishingLog(Map.of(), 0, 0);

    /**
     * 一种鱼的记录。
     *
     * @param heaviest 最重的一条（千克）
     * @param planetZh 最重的那条在哪颗星球钓的
     */
    public record Entry(int count, double heaviest, String planetZh, String planetEn) {
    }

    public FishingLog {
        entries = Map.copyOf(entries);
    }

    public Optional<Entry> entry(FishSpecies species) {
        return Optional.ofNullable(this.entries.get(species.id()));
    }

    public boolean caught(FishSpecies species) {
        return this.entries.containsKey(species.id());
    }

    /** 钓到过几种。 */
    public int species() {
        return this.entries.size();
    }

    /** 一共钓了几条。 */
    public int total() {
        int total = 0;
        for (Entry entry : this.entries.values()) {
            total += entry.count();
        }
        return total;
    }

    /** 这种水域里钓到过几种。 */
    public int species(Habitat habitat) {
        int count = 0;
        for (FishSpecies species : FishTable.of(habitat)) {
            if (this.caught(species)) {
                count++;
            }
        }
        return count;
    }

    /** 记一条：比原来最重的还重就换掉重量和星球。 */
    public FishingLog record(FishSpecies species, double kilograms, String planetZh, String planetEn) {
        Map<String, Entry> map = new HashMap<>(this.entries);
        Entry old = map.get(species.id());
        if (old == null) {
            map.put(species.id(), new Entry(1, kilograms, planetZh, planetEn));
        } else if (kilograms > old.heaviest()) {
            map.put(species.id(), new Entry(old.count() + 1, kilograms, planetZh, planetEn));
        } else {
            map.put(species.id(), new Entry(old.count() + 1, old.heaviest(), old.planetZh(), old.planetEn()));
        }
        return new FishingLog(map, this.released, this.releaseCasts);
    }

    /** 这一条是不是新纪录（第一次钓到也算）。 */
    public boolean record(FishSpecies species, double kilograms) {
        Entry old = this.entries.get(species.id());
        return old == null || kilograms > old.heaviest();
    }

    /** 放生一条：记一笔，接下来 {@link BiteTimer#RELEASE_CASTS} 次甩竿有加成。 */
    public FishingLog release() {
        return new FishingLog(this.entries, this.released + 1, BiteTimer.RELEASE_CASTS);
    }

    /** 甩了一竿：用掉一次放生的加成。 */
    public FishingLog cast() {
        return this.releaseCasts <= 0 ? this : new FishingLog(this.entries, this.released, this.releaseCasts - 1);
    }

    /** 这一竿有没有放生的加成。 */
    public boolean blessed() {
        return this.releaseCasts > 0;
    }
}
