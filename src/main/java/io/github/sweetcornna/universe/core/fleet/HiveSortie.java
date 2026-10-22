package io.github.sweetcornna.universe.core.fleet;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.github.sweetcornna.universe.core.darkfog.DarkFogLoot;
import io.github.sweetcornna.universe.core.procgen.Rng;

/**
 * 护卫舰清剿黑雾巢穴（ADR-0028 §八）。和远征一样出发时把每段遭遇算好、回来领，不同的是：
 *
 * <ul>
 *   <li>不挂在每天的任务里，挑一个有巢穴的恒星系就能派；难度按巢穴的等级定，时长和同难度的远征一样。</li>
 *   <li>每段都是战斗，只看战斗属性。</li>
 *   <li>奖励只有信用点；赢的段数交给巢穴那边打掉单位和建筑，打掉的按黑雾的掉落带回来（{@link #loot}）。</li>
 * </ul>
 *
 * <p>和 {@link Expedition#offers} 分开算，每天的远征任务不受影响。
 */
public final class HiveSortie {
    /** 每多少级黑雾等级难度加一。 */
    static final int LEVELS_PER_DIFFICULTY = 7;
    /** 打掉一座建筑按几份掉落算（单位按 1 份）。 */
    static final double BUILDING_LOOT = 5;

    private HiveSortie() {
    }

    /** 巢穴等级对应的难度：0–6 级一星，7–13 级两星……28 级起五星。 */
    public static int difficulty(int darkFogLevel) {
        return Math.clamp(1 + darkFogLevel / LEVELS_PER_DIFFICULTY, 1, Expedition.MINUTES.length);
    }

    /** 清剿这座巢穴的任务。 */
    public static Expedition.Offer offer(int darkFogLevel) {
        int difficulty = difficulty(darkFogLevel);
        return new Expedition.Offer(Expedition.Kind.COMBAT, difficulty, Expedition.MINUTES[difficulty - 1]);
    }

    /** 出发：算好 2 + 难度 段遭遇，全是战斗。队伍里不能有受损的。 */
    public static List<Expedition.Event> resolve(int difficulty, List<Frigate> team, Rng rng) {
        return Expedition.encounters(difficulty, team, rng, r -> Area.COMBAT);
    }

    /** 一段赢下来的机会。 */
    public static double chance(List<Frigate> team, int difficulty) {
        return Expedition.chance(Expedition.strength(team, Area.COMBAT), difficulty);
    }

    /** 赢了几段。 */
    public static int successes(List<Expedition.Event> events) {
        return (int) events.stream().filter(Expedition.Event::success).count();
    }

    /** 信用点：每赢一段 300–450 × 难度，全赢再给 400 × 难度。 */
    public static List<Expedition.Reward> rewards(int difficulty, List<Expedition.Event> events, Rng rng) {
        int credits = 0;
        for (Expedition.Event event : events) {
            if (event.success()) {
                credits += 300 * difficulty + rng.range(0, 150 * difficulty);
            }
        }
        if (!events.isEmpty() && successes(events) == events.size()) {
            credits += 400 * difficulty;
        }
        return credits > 0 ? List.of(new Expedition.Reward(Expedition.CREDITS, credits)) : List.of();
    }

    /** 打掉的单位和建筑带回来的黑雾掉落：单位 1 份、建筑 {@link #BUILDING_LOOT} 份，档次看巢穴等级。什么都没打掉就没有。 */
    public static List<Expedition.Reward> loot(int darkFogLevel, int unitsKilled, int buildingsDestroyed, Rng rng) {
        double weight = unitsKilled + buildingsDestroyed * BUILDING_LOOT;
        if (weight <= 0) {
            return List.of();
        }
        Map<String, Integer> totals = new LinkedHashMap<>();
        for (DarkFogLoot.Drop drop : DarkFogLoot.roll(darkFogLevel, weight, rng)) {
            totals.merge(drop.item(), drop.count(), Integer::sum);
        }
        List<Expedition.Reward> rewards = new ArrayList<>();
        totals.forEach((item, count) -> rewards.add(new Expedition.Reward(item, count)));
        return rewards;
    }
}
