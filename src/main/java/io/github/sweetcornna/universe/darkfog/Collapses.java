package io.github.sweetcornna.universe.darkfog;

import java.util.Map;
import java.util.WeakHashMap;

import io.github.sweetcornna.universe.core.audio.SoundBudget;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

/**
 * 黑雾建筑倒塌的声音（ADR-0029 §五）：一次清掉整座基地时，同一片地方一秒半里只放头三声爆炸和崩塌，
 * 其余的只有烟和火花。每个世界（服务端、客户端各一个）一份账。
 */
public final class Collapses {
    private static final Map<Level, SoundBudget> BUDGETS = new WeakHashMap<>();

    private Collapses() {
    }

    /** 这座建筑倒塌时放不放声音。 */
    public static synchronized boolean allow(Entity building) {
        Level level = building.level();
        return BUDGETS.computeIfAbsent(level, key -> new SoundBudget(3, 30, 48))
                .allow(level.getGameTime(), building.getX(), building.getY(), building.getZ());
    }
}
