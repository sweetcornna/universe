package io.github.sweetcornna.universe.core.chem.reaction;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * 全服发现了的反应（ADR-0037 §一、§五）：自由投料做成一批、读了实验记录，这条反应就记下。反应釜的列表只能选发现了的，
 * 化学手册里发现了的打勾。按名字存，读到不认识的名字跳过。
 */
public final class ReactionBook {
    private final Set<Reaction> discovered = EnumSet.noneOf(Reaction.class);

    public ReactionBook() {
    }

    /** 从名字读回来，不认识的跳过。 */
    public static ReactionBook load(Collection<String> names) {
        ReactionBook book = new ReactionBook();
        for (String name : names) {
            Reaction reaction = Reaction.byName(name);
            if (reaction != null) {
                book.discovered.add(reaction);
            }
        }
        return book;
    }

    /** 按反应的顺序写出名字。 */
    public List<String> names() {
        List<String> names = new ArrayList<>();
        for (Reaction reaction : this.discovered) {
            names.add(reaction.name());
        }
        return names;
    }

    /** 记下一条；以前没有返回 {@code true}。 */
    public boolean discover(Reaction reaction) {
        return this.discovered.add(reaction);
    }

    public boolean discovered(Reaction reaction) {
        return this.discovered.contains(reaction);
    }

    public int count() {
        return this.discovered.size();
    }

    /**
     * 手册里列不列出这条：发现了的总列，没发现的研究了对应科技才列（ADR-0037 §四）。
     *
     * @param researched 科技研究完了没有
     */
    public boolean listed(Reaction reaction, Predicate<String> researched) {
        return this.discovered(reaction) || researched.test(reaction.research());
    }
}
