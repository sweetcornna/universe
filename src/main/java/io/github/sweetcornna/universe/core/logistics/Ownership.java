package io.github.sweetcornna.universe.core.logistics;

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
}
