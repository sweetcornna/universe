package io.github.sweetcornna.universe.core.fleet;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.core.procgen.Rng;
import io.github.sweetcornna.universe.core.procgen.Seeds;

/**
 * 护卫舰远征（ADR-0024）。出发时就把每一段遭遇的成败算好，过程按时间一条条揭开，回来时结算奖励和损伤。
 *
 * <p>一段遭遇的成败：队伍在这一项上的属性合计乘上 0.75–1.25 的运气，够不够 {@link #required}。失败的那段让一艘
 * 护卫舰受损；已经受损的再受损就沉了，不再参加后面的遭遇。
 */
public final class Expedition {
    /** 一次最多派几艘。 */
    public static final int MAX_TEAM = 5;
    /** 同时最多几支远征。 */
    public static final int MAX_ACTIVE = 3;
    /** 每天挂几个远征任务。 */
    public static final int OFFERS_PER_DAY = 3;
    /** 难度 1–5 的时长（分钟，游戏时间）。 */
    static final int[] MINUTES = {10, 16, 22, 30, 40};
    /** 难度 1–5 出现的权重。 */
    static final double[] DIFFICULTY_WEIGHTS = {25, 30, 25, 13, 7};
    /** 一段遭遇是任务本身那一类的概率。 */
    static final double ON_TYPE = 0.7;
    /** 一吨燃料要几个发射燃料：每 {@code FUEL_PER_ITEM} 吨一个，向上取整。 */
    public static final int FUEL_PER_ITEM = 10;

    public enum Kind {
        COMBAT(Area.COMBAT),
        EXPLORATION(Area.EXPLORATION),
        INDUSTRIAL(Area.INDUSTRY),
        TRADE(Area.TRADE),
        BALANCED(null);

        private final @Nullable Area area;

        Kind(@Nullable Area area) {
            this.area = area;
        }

        public @Nullable Area area() {
            return this.area;
        }

        public String key() {
            return this.name().toLowerCase(Locale.ROOT);
        }
    }

    /** 终端上挂着的一个任务。 */
    public record Offer(Kind kind, int difficulty, int minutes) {
        public long ticks() {
            return this.minutes * 60L * 20;
        }
    }

    public enum Outcome {
        NONE, DAMAGED, LOST
    }

    /**
     * 一段遭遇。
     *
     * @param frigate 受损或沉没的护卫舰编号，没有时 -1
     */
    public record Event(Area area, boolean success, int frigate, Outcome outcome) {
    }

    /** 一份奖励：{@code key} 是物品 ID，或者 {@code credits}、{@code ship_module}、{@code glyph}。 */
    public record Reward(String key, int count) {
    }

    public static final String CREDITS = "credits";
    public static final String SHIP_MODULE = "ship_module";
    public static final String GLYPH = "glyph";

    private Expedition() {
    }

    /** 今天的远征任务。{@code seed} 是货船的种子。 */
    public static List<Offer> offers(long seed, long day) {
        Rng rng = Rng.of(Seeds.derive(Seeds.derive(seed, "expeditions"), day));
        List<Offer> offers = new ArrayList<>();
        List<Integer> levels = List.of(1, 2, 3, 4, 5);
        for (int i = 0; i < OFFERS_PER_DAY; i++) {
            Kind kind = rng.pick(Kind.values());
            int difficulty = rng.weighted(levels, d -> DIFFICULTY_WEIGHTS[d - 1]);
            offers.add(new Offer(kind, difficulty, MINUTES[difficulty - 1]));
        }
        return offers;
    }

    /** 一段遭遇要的属性合计。 */
    public static int required(int difficulty) {
        return 10 + 12 * difficulty;
    }

    /** 一段遭遇赢下来的机会：属性合计乘 0.75–1.25 的运气够得上 {@link #required} 的概率。 */
    public static double chance(int strength, int difficulty) {
        if (strength <= 0) {
            return 0;
        }
        double need = required(difficulty) / (double) strength;
        return Math.clamp((1.25 - need) / 0.5, 0, 1);
    }

    /** 这一队要多少个发射燃料。 */
    public static int fuelItems(List<Frigate> team) {
        int tonnes = 0;
        for (Frigate frigate : team) {
            tonnes += frigate.fuelCost();
        }
        return (tonnes + FUEL_PER_ITEM - 1) / FUEL_PER_ITEM;
    }

    /** 队伍在一项上的属性合计。 */
    /** 这支队伍出这个任务，平均每段成功的机会（按各种遭遇出现的机会加权）。 */
    public static double expected(Offer offer, List<Frigate> team) {
        Area main = offer.kind().area();
        double total = 0;
        for (Area area : Area.values()) {
            double share = (main == null ? 1 : 1 - ON_TYPE) / Area.values().length + (area == main ? ON_TYPE : 0);
            total += share * chance(strength(team, area), offer.difficulty());
        }
        return total;
    }

    public static int strength(List<Frigate> team, Area area) {
        int sum = 0;
        for (Frigate frigate : team) {
            sum += frigate.stat(area);
        }
        return sum;
    }

    /** 出发：算好 2 + 难度 段遭遇。队伍里不能有受损的。 */
    public static List<Event> resolve(Offer offer, List<Frigate> team, Rng rng) {
        return encounters(offer.difficulty(), team, rng,
                r -> offer.kind().area() != null && r.chance(ON_TYPE) ? offer.kind().area() : r.pick(Area.values()));
    }

    /** 算好 2 + 难度 段遭遇，每段是哪一类由 {@code areas} 挑（清剿用的也是这个，见 {@link HiveSortie}）。 */
    static List<Event> encounters(int difficulty, List<Frigate> team, Rng rng, java.util.function.Function<Rng, Area> areas) {
        if (team.isEmpty() || team.size() > MAX_TEAM) {
            throw new IllegalArgumentException("team size " + team.size());
        }
        List<Frigate> alive = new ArrayList<>(team);
        Map<Integer, Integer> hits = new HashMap<>();
        List<Event> events = new ArrayList<>();
        int count = 2 + difficulty;
        for (int i = 0; i < count; i++) {
            Area area = areas.apply(rng);
            if (alive.isEmpty()) {
                events.add(new Event(area, false, -1, Outcome.NONE));
                continue;
            }
            double roll = strength(alive, area) * rng.range(0.75, 1.25);
            if (roll >= required(difficulty)) {
                events.add(new Event(area, true, -1, Outcome.NONE));
                continue;
            }
            // 先伤没挨过打的；都挨过了才会有船沉
            List<Frigate> fresh = alive.stream().filter(f -> !hits.containsKey(f.id())).toList();
            Frigate victim = rng.pick(fresh.isEmpty() ? alive : fresh);
            int n = hits.merge(victim.id(), 1, Integer::sum);
            Outcome outcome = n >= 2 ? Outcome.LOST : Outcome.DAMAGED;
            if (outcome == Outcome.LOST) {
                alive.remove(victim);
            }
            events.add(new Event(area, false, victim.id(), outcome));
        }
        return events;
    }

    /** 按成功的遭遇算奖励；全部成功另给一笔信用点。 */
    public static List<Reward> rewards(Offer offer, List<Event> events, Rng rng) {
        Map<String, Integer> totals = new java.util.LinkedHashMap<>();
        int d = offer.difficulty();
        boolean all = true;
        for (Event event : events) {
            if (!event.success()) {
                all = false;
                continue;
            }
            switch (event.area()) {
                case COMBAT -> {
                    totals.merge(CREDITS, 400 * d + rng.range(0, 200 * d), Integer::sum);
                    if (rng.chance(0.5)) {
                        totals.merge("universe:pirate_scrap", 1, Integer::sum);
                    }
                }
                case EXPLORATION -> {
                    totals.merge("universe:survey_data", rng.range(1, 2), Integer::sum);
                    if (rng.chance(0.6)) {
                        totals.merge("universe:navigation_data", 1, Integer::sum);
                    }
                    if (rng.chance(0.04 * d)) {
                        totals.merge(SHIP_MODULE, 1, Integer::sum);
                    }
                    if (rng.chance(0.08)) {
                        totals.merge(GLYPH, 1, Integer::sum);
                    }
                }
                case INDUSTRY -> {
                    totals.merge("universe:titanium_ingot", rng.range(2, 4) * d, Integer::sum);
                    totals.merge("universe:steel", rng.range(1, 3) * d, Integer::sum);
                    if (rng.chance(0.15 * d)) {
                        totals.merge("universe:processor", 1, Integer::sum);
                    }
                }
                case TRADE -> totals.merge(CREDITS, 700 * d + rng.range(0, 300 * d), Integer::sum);
            }
        }
        if (all && !events.isEmpty()) {
            totals.merge(CREDITS, 500 * d, Integer::sum);
        }
        List<Reward> rewards = new ArrayList<>();
        totals.forEach((key, count) -> rewards.add(new Reward(key, count)));
        return rewards;
    }
}
