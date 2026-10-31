package io.github.sweetcornna.universe.darkfog;

import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * 每个维度里加载着的黑雾单位和建筑（ADR-0038）。炮塔没有目标时每 4 刻要在几十格的范围里找一遍黑雾，维度里一个黑雾都没加载时
 * 怎么找都是空的，直接跳过。
 *
 * <p>只用来判断"一个都没有"。不会记少：黑雾都是普通实体，进维度一定先发加入事件（只有玩家不发）。可能记多：加入事件被排在后面的
 * 别的模组取消了、UUID 重复没加进去，这时只是少省一次查找，每 {@link #PRUNE_INTERVAL} 刻清一次。
 */
public final class LoadedEnemies {
    private static final int PRUNE_INTERVAL = 600;
    private static final Map<ResourceKey<Level>, Set<Entity>> LOADED = new HashMap<>();

    private LoadedEnemies() {
    }

    public static void registerEvents(IEventBus gameBus) {
        gameBus.addListener(EventPriority.LOWEST, EntityJoinLevelEvent.class, event -> {
            if (event.getEntity() instanceof DarkFogEnemy && event.getLevel() instanceof ServerLevel level) {
                LOADED.computeIfAbsent(level.dimension(), key -> Collections.newSetFromMap(new IdentityHashMap<>())).add(event.getEntity());
            }
        });
        gameBus.addListener(EntityLeaveLevelEvent.class, event -> {
            if (event.getEntity() instanceof DarkFogEnemy && event.getLevel() instanceof ServerLevel level) {
                Set<Entity> loaded = LOADED.get(level.dimension());
                if (loaded != null) {
                    loaded.remove(event.getEntity());
                }
            }
        });
        gameBus.addListener(LevelTickEvent.Post.class, event -> {
            if (event.getLevel() instanceof ServerLevel level && level.getGameTime() % PRUNE_INTERVAL == 0) {
                Set<Entity> loaded = LOADED.get(level.dimension());
                if (loaded != null) {
                    loaded.removeIf(entity -> entity.isRemoved() || entity.level() != level);
                }
            }
        });
        gameBus.addListener(ServerStoppedEvent.class, event -> LOADED.clear());
    }

    /** 这个维度里有没有加载着的黑雾。 */
    public static boolean any(ServerLevel level) {
        Set<Entity> loaded = LOADED.get(level.dimension());
        return loaded != null && !loaded.isEmpty();
    }
}
