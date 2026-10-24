package io.github.sweetcornna.universe.survival;

import java.lang.ref.WeakReference;
import java.util.function.ToDoubleFunction;

import io.github.sweetcornna.universe.core.galaxy.Planet;
import io.github.sweetcornna.universe.core.survival.GravityModel;
import io.github.sweetcornna.universe.core.survival.ProjectileGravity;
import io.github.sweetcornna.universe.world.planet.Planets;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

/**
 * 不是生物的实体在星球上的重力（ADR-0030 §六）：掉落物、箭、雪球、经验球、下落的方块、点燃的 TNT 这些原版实体的重力
 * 乘上星球的重力。生物的重力是属性，由 {@link PlayerSurvival#applyGravity} 改；本模组自己的炮弹、飞船、单位
 * 各算各的弹道，不动。太空维度不是星球，照原版。
 *
 * <p>客户端也按同样的系数模拟掉落物和箭的运动，两边一致才不会抖。每个世界只查一次星球，结果按世界缓存。
 */
public final class EntityGravity {
    /** 客户端：这个世界的星球重力；还不知道星团时返回 {@link Double#NaN}（不缓存）。由客户端初始化时设置。 */
    public static ToDoubleFunction<Level> clientGravity = level -> 1;

    /** 哪些实体类要乘：不是生物、也不是本模组的实体。 */
    private static final ClassValue<Boolean> SCALED = new ClassValue<>() {
        @Override
        protected Boolean computeValue(Class<?> type) {
            return !LivingEntity.class.isAssignableFrom(type) && !type.getName().startsWith("io.github.sweetcornna.universe.");
        }
    };

    private static final Cache SERVER = new Cache();
    private static final Cache CLIENT = new Cache();

    private EntityGravity() {
    }

    /** {@code Entity.getGravity()} 的结果按星球重力换算以后的值（换算在 {@link ProjectileGravity}）。 */
    public static double scale(Entity entity, double gravity) {
        if (gravity == 0 || !SCALED.get(entity.getClass())) {
            return gravity;
        }
        return ProjectileGravity.scale(gravity, factor(entity.level()));
    }

    /** 这个世界里不是生物的实体的重力倍数（不是星球时是 1）。 */
    public static double factor(Level level) {
        Cache cache = level.isClientSide() ? CLIENT : SERVER;
        return cache.get(level);
    }

    private static double compute(Level level) {
        if (level instanceof ServerLevel server) {
            return Planets.planet(server).map(Planet::gravity).map(GravityModel::gravity).orElse(1.0);
        }
        return clientGravity.applyAsDouble(level);
    }

    /** 上一次查的世界和结果：实体按世界一个一个地刻，几乎总是命中。 */
    private static final class Cache {
        private volatile Entry last = new Entry(new WeakReference<>(null), 1);

        double get(Level level) {
            Entry entry = this.last;
            if (entry.level.get() == level) {
                return entry.factor;
            }
            double factor = compute(level);
            if (Double.isNaN(factor)) {
                return 1;
            }
            this.last = new Entry(new WeakReference<>(level), factor);
            return factor;
        }
    }

    private record Entry(WeakReference<Level> level, double factor) {
    }
}
