package io.github.sweetcornna.universe.core.logistics;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;

/**
 * 控制面板远程改设置的权限（ADR-0033 §三）。面板列出整个星团的站、配送器和路线，谁都能看；远程改只给主人、主人的队友和
 * 有 2 级权限的管理员。没记主人的（0.14 的档里的）照旧谁都能改。走到跟前改不归这里管，那由够不够得着、出生点保护和领地模组决定。
 */
public final class Ownership {
    private Ownership() {
    }

    /**
     * @param owner    主人；{@code null} 是旧档里没记的
     * @param player   要改的玩家
     * @param operator 玩家有没有 2 级权限
     * @param teammate 主人和玩家是不是同一个队伍的
     */
    public static boolean mayEdit(@Nullable UUID owner, UUID player, boolean operator, Predicate<UUID> teammate) {
        return owner == null || owner.equals(player) || operator || teammate.test(owner);
    }

    /**
     * 能不能删一条路线：建的人能删。路线压过别的配对，别人建的路线拉走了自己站上的货也得删得掉，所以走这条路线的站里有一座是他能改的，
     * 他也能删。只算有主人的站：0.14 的站谁都能改，算上就谁都能删了。
     *
     * @param creator       建路线的人；{@code null} 是旧档里没记的
     * @param stationOwners 走这条路线的站的主人
     */
    public static boolean mayDeleteRoute(@Nullable UUID creator, List<Optional<UUID>> stationOwners, UUID player, boolean operator,
            Predicate<UUID> teammate) {
        return mayEdit(creator, player, operator, teammate)
                || stationOwners.stream().anyMatch(owner -> owner.isPresent() && mayEdit(owner.get(), player, operator, teammate));
    }
}
