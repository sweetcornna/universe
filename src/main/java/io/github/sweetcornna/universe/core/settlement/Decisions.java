package io.github.sweetcornna.universe.core.settlement;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.SplittableRandom;

import org.jspecify.annotations.Nullable;

/**
 * 总督每天要做的决定（ADR-0034 §二）：请愿、纠纷、新来的人、政策四类，每类 6 个，每个 2–4 个选项。
 * 字在语言文件里（{@code settlement.universe.decision.<id>} 是问题，{@code .<序号>} 是选项），这里只有效果。
 */
public final class Decisions {
    public enum Kind {
        REQUEST,
        DISPUTE,
        ARRIVAL,
        POLICY;

        public String id() {
            return this.name().toLowerCase(Locale.ROOT);
        }
    }

    /**
     * 选一个选项的效果。信用点是总督自己出（负数）或拿到（正数）；钱不够的选项不能选。
     *
     * @param happiness 幸福马上变多少
     * @param boost     生产力临时变多少，持续 {@code days} 天
     * @param days      临时的生产力持续几天
     * @param threat    威胁变多少
     * @param reserve   储备变多少份
     * @param credits   总督的信用点变多少
     * @param population 人口变多少（加人要有空房）
     * @param standing  和这个种族的声望变多少
     * @param item      给总督的物品（空串是没有）
     * @param count     给几个
     * @param word      学一个这个种族的词
     */
    public record Effect(int happiness, int boost, int days, int threat, int reserve, int credits, int population, int standing,
            String item, int count, boolean word) {
        public static final Effect NONE = new Effect(0, 0, 0, 0, 0, 0, 0, 0, "", 0, false);

        static Builder of() {
            return new Builder();
        }
    }

    static final class Builder {
        private int happiness;
        private int boost;
        private int days;
        private int threat;
        private int reserve;
        private int credits;
        private int population;
        private int standing;
        private String item = "";
        private int count;
        private boolean word;

        Builder happy(int value) {
            this.happiness = value;
            return this;
        }

        Builder boost(int value, int forDays) {
            this.boost = value;
            this.days = forDays;
            return this;
        }

        Builder threat(int value) {
            this.threat = value;
            return this;
        }

        Builder reserve(int value) {
            this.reserve = value;
            return this;
        }

        Builder credits(int value) {
            this.credits = value;
            return this;
        }

        Builder people(int value) {
            this.population = value;
            return this;
        }

        Builder standing(int value) {
            this.standing = value;
            return this;
        }

        Builder item(String id, int amount) {
            this.item = id;
            this.count = amount;
            return this;
        }

        Builder word() {
            this.word = true;
            return this;
        }

        Effect build() {
            return new Effect(this.happiness, this.boost, this.days, this.threat, this.reserve, this.credits, this.population, this.standing,
                    this.item, this.count, this.word);
        }
    }

    /** 一个决定：要有 {@code requires} 这种建筑才会出（null 是不要）。 */
    public record Decision(String id, Kind kind, @Nullable BuildingType requires, List<Effect> options) {
        public String key() {
            return "settlement.universe.decision." + this.id;
        }

        public String optionKey(int option) {
            return this.key() + "." + option;
        }
    }

    public static final List<Decision> ALL = List.of(
            // 请愿
            decision("well", Kind.REQUEST, null,
                    Effect.of().credits(-800).happy(8).build(),
                    Effect.of().boost(-5, 2).happy(3).build(),
                    Effect.of().happy(-4).build()),
            decision("festival", Kind.REQUEST, null,
                    Effect.of().reserve(-20).happy(12).build(),
                    Effect.of().reserve(-8).happy(5).build(),
                    Effect.of().happy(-3).build()),
            decision("medkit", Kind.REQUEST, BuildingType.WORKSHOP,
                    Effect.of().credits(-400).happy(6).build(),
                    Effect.of().boost(-8, 2).happy(4).build(),
                    Effect.of().happy(-6).build()),
            decision("lights", Kind.REQUEST, null,
                    Effect.of().credits(-300).happy(4).threat(-5).build(),
                    Effect.of().threat(3).build()),
            decision("classroom", Kind.REQUEST, null,
                    Effect.of().credits(-1000).happy(6).boost(5, 5).build(),
                    Effect.of().standing(2).happy(3).word().build(),
                    Effect.of().happy(-3).build()),
            decision("monument", Kind.REQUEST, null,
                    Effect.of().reserve(-15).happy(5).standing(3).build(),
                    Effect.of().reserve(-10).happy(6).build(),
                    Effect.of().happy(2).build()),
            // 纠纷
            decision("irrigation", Kind.DISPUTE, BuildingType.FARM,
                    Effect.of().happy(2).build(),
                    Effect.of().boost(8, 3).happy(-4).build(),
                    Effect.of().credits(-600).happy(6).build()),
            decision("noise", Kind.DISPUTE, BuildingType.SALOON,
                    Effect.of().happy(-2).boost(4, 3).build(),
                    Effect.of().credits(-300).happy(3).build(),
                    Effect.of().happy(-5).build()),
            decision("missing_crate", Kind.DISPUTE, null,
                    Effect.of().happy(3).build(),
                    Effect.of().people(-1).threat(-3).happy(-2).build(),
                    Effect.of().reserve(-10).build()),
            decision("empty_lot", Kind.DISPUTE, null,
                    Effect.of().happy(1).build(),
                    Effect.of().boost(6, 3).happy(-2).build(),
                    Effect.of().happy(3).build()),
            decision("unpaid_trader", Kind.DISPUTE, null,
                    Effect.of().credits(-500).standing(2).build(),
                    Effect.of().boost(5, 2).build(),
                    Effect.of().standing(-2).build()),
            decision("stray_creature", Kind.DISPUTE, null,
                    Effect.of().happy(1).build(),
                    Effect.of().happy(-3).threat(-2).build(),
                    Effect.of().reserve(-6).happy(4).build()),
            // 新来的人
            decision("engineer", Kind.ARRIVAL, null,
                    Effect.of().people(1).boost(6, 5).build(),
                    Effect.NONE),
            decision("refugees", Kind.ARRIVAL, null,
                    Effect.of().people(2).reserve(-15).threat(10).build(),
                    Effect.of().reserve(-8).standing(2).build(),
                    Effect.of().happy(-4).build()),
            decision("peddler", Kind.ARRIVAL, null,
                    Effect.of().credits(400).build(),
                    Effect.of().happy(4).standing(2).build(),
                    Effect.NONE),
            decision("archivist", Kind.ARRIVAL, null,
                    Effect.of().credits(-600).item("universe:navigation_data", 2).build(),
                    Effect.of().people(1).standing(2).build(),
                    Effect.NONE),
            decision("veteran", Kind.ARRIVAL, null,
                    Effect.of().credits(-500).threat(-15).build(),
                    Effect.of().people(1).happy(-2).build(),
                    Effect.NONE),
            decision("family", Kind.ARRIVAL, BuildingType.HABITATION,
                    Effect.of().people(2).happy(3).build(),
                    Effect.NONE),
            // 政策
            decision("long_shifts", Kind.POLICY, BuildingType.WORKSHOP,
                    Effect.of().boost(15, 3).happy(-8).build(),
                    Effect.NONE,
                    Effect.of().boost(-8, 3).happy(8).build()),
            decision("market_tax", Kind.POLICY, BuildingType.MARKET,
                    Effect.of().credits(600).happy(-5).build(),
                    Effect.of().happy(2).build()),
            decision("night_watch", Kind.POLICY, null,
                    Effect.of().boost(-5, 3).threat(-15).build(),
                    Effect.NONE),
            decision("rationing", Kind.POLICY, null,
                    Effect.of().reserve(15).happy(-6).build(),
                    Effect.NONE),
            decision("caravan", Kind.POLICY, null,
                    Effect.of().credits(300).threat(8).standing(2).build(),
                    Effect.NONE),
            decision("rest_day", Kind.POLICY, null,
                    Effect.of().happy(10).boost(-6, 3).build(),
                    Effect.NONE,
                    Effect.of().happy(4).boost(-2, 1).build(),
                    Effect.of().boost(4, 2).happy(-3).build()));

    private Decisions() {
    }

    private static Decision decision(String id, Kind kind, @Nullable BuildingType requires, Effect... options) {
        return new Decision(id, kind, requires, List.of(options));
    }

    public static Optional<Decision> byId(String id) {
        for (Decision decision : ALL) {
            if (decision.id().equals(id)) {
                return Optional.of(decision);
            }
        }
        return Optional.empty();
    }

    /**
     * 今天出哪个决定：从有对应建筑的里面挑，不挑最近出过的几个。{@code seed} 由聚落和日子派生，同一天挑出来的一样。
     *
     * @param has    聚落有没有这种建筑
     * @param recent 最近出过的决定（不再挑）
     */
    public static Decision pick(long seed, java.util.function.Predicate<BuildingType> has, List<String> recent) {
        List<Decision> pool = new ArrayList<>();
        for (Decision decision : ALL) {
            if ((decision.requires() == null || has.test(decision.requires())) && !recent.contains(decision.id())) {
                pool.add(decision);
            }
        }
        if (pool.isEmpty()) {
            pool.addAll(ALL.stream().filter(d -> d.requires() == null).toList());
        }
        return pool.get(new SplittableRandom(seed).nextInt(pool.size()));
    }
}
