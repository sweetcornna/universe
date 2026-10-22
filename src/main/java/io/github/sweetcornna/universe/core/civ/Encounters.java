package io.github.sweetcornna.universe.core.civ;

import java.util.ArrayList;
import java.util.List;

import io.github.sweetcornna.universe.core.procgen.Rng;

/** 对话题目表（ADR-0027 §四）。题目和文字是占位，剧情讨论后重写、加多。 */
public final class Encounters {
    /** 写在表里的选项顺序；显示时按种子打乱。 */
    private static final List<Value> OPTIONS = List.of(Value.PROFIT, Value.KNOWLEDGE, Value.VALOR);
    private static final List<Encounter> ALL = List.of(
            encounter("damaged_hull", "ship", "break", "metal", "help"),
            encounter("storm_coming", "storm", "danger", "sky", "travel"),
            encounter("old_stone", "stone", "ancient", "see", "learn"),
            encounter("the_hunt", "creature", "hunt", "food", "danger", "strength"),
            encounter("odd_signal", "signal", "data", "sky", "want"),
            encounter("offered_gift", "gift", "give", "friend", "take"));

    private Encounters() {
    }

    public static List<Encounter> all() {
        return ALL;
    }

    /** 按种子（NPC 加日期）挑一道题，选项顺序也按种子打乱。 */
    public static Encounter pick(long seed) {
        Rng rng = Rng.of(seed);
        Encounter encounter = ALL.get(rng.nextInt(ALL.size()));
        List<Value> options = new ArrayList<>(encounter.options());
        for (int i = options.size() - 1; i > 0; i--) {
            int j = rng.nextInt(i + 1);
            options.set(j, options.set(i, options.get(j)));
        }
        return new Encounter(encounter.id(), encounter.words(), List.copyOf(options));
    }

    private static Encounter encounter(String id, String... words) {
        List<Integer> concepts = new ArrayList<>();
        for (String word : words) {
            int concept = Lexicon.index(word);
            if (concept < 0) {
                throw new IllegalArgumentException("unknown concept: " + word);
            }
            concepts.add(concept);
        }
        return new Encounter(id, List.copyOf(concepts), OPTIONS);
    }
}
