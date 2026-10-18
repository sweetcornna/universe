package io.github.sweetcornna.universe.trade;

import io.github.sweetcornna.universe.registry.UniverseAttachments;
import net.minecraft.world.entity.player.Player;

/** 玩家的信用点（ADR-0017）：记在玩家身上，死亡不丢，同步给本人。 */
public final class Credits {
    private Credits() {
    }

    public static long get(Player player) {
        return player.getData(UniverseAttachments.CREDITS);
    }

    /** 加（或减）信用点，不会减到负数。返回加完以后的数。 */
    public static long add(Player player, long amount) {
        long now = Math.max(0, Math.addExact(get(player), amount));
        player.setData(UniverseAttachments.CREDITS, now);
        return now;
    }

    /** 信用点写成带千分位的数（服务端也能用，提示文字里用）。 */
    public static String format(long credits) {
        return String.format(java.util.Locale.ROOT, "%,d", credits);
    }

    /** 够就扣掉并返回 true，不够什么都不变。 */
    public static boolean spend(Player player, long amount) {
        long credits = get(player);
        if (amount < 0 || credits < amount) {
            return false;
        }
        player.setData(UniverseAttachments.CREDITS, credits - amount);
        return true;
    }
}
