package io.github.sweetcornna.universe.core.chem.reaction;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 自由投料时认反应（ADR-0037 §一）：只看原料格里有哪几种物品（不看顺序和数量）和催化剂格。
 * 反应物种类对得上的只有一条就是它；有几条时按催化剂挑，挑不出来就不反应。
 */
public final class ReactionMatcher {
    private ReactionMatcher() {
    }

    /**
     * 认出来的结果。
     *
     * @param reaction   认出的反应，认不出或挑不出为 {@code null}
     * @param candidates 反应物种类对得上的有几条
     */
    public record Match(Reaction reaction, int candidates) {
        public boolean found() {
            return this.reaction != null;
        }

        /** 对得上好几条、催化剂挑不出来。 */
        public boolean ambiguous() {
            return this.reaction == null && this.candidates > 1;
        }
    }

    /**
     * @param items    原料格里的物品（注册名），重复的、空的不算
     * @param catalyst 催化剂格里的催化剂，没有为 {@code null}
     */
    public static Match match(Collection<String> items, Catalyst catalyst) {
        Set<String> kinds = new HashSet<>(items);
        List<Reaction> candidates = candidates(kinds);
        if (candidates.size() == 1) {
            return new Match(candidates.getFirst(), 1);
        }
        for (Reaction reaction : candidates) {
            if (reaction.catalyst() == catalyst) {
                return new Match(reaction, candidates.size());
            }
        }
        return new Match(null, candidates.size());
    }

    /** 反应物种类正好是这几样的反应。 */
    public static List<Reaction> candidates(Set<String> kinds) {
        List<Reaction> list = new ArrayList<>();
        if (kinds.isEmpty()) {
            return list;
        }
        for (Reaction reaction : Reaction.values()) {
            if (reaction.reactantItems().equals(kinds)) {
                list.add(reaction);
            }
        }
        return list;
    }

    /** 放了这种催化剂（或者没放），这条反应算不算用对了催化剂：不用催化剂的反应总是算。 */
    public static boolean catalyzed(Reaction reaction, Catalyst catalyst) {
        return reaction.catalyst() == null || reaction.catalyst() == catalyst;
    }
}
