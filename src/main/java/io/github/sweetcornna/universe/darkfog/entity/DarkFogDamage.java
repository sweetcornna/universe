package io.github.sweetcornna.universe.darkfog.entity;

import java.util.List;

import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.darkfog.DarkFogEnemy;
import io.github.sweetcornna.universe.mecha.fleet.FleetUnit;
import io.github.sweetcornna.universe.mecha.fleet.MechaFleet;
import io.github.sweetcornna.universe.sentinel.Sentinel;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageScaling;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 黑雾的伤害类型（ADR-0028）：电弧、激光、等离子炮弹都算这一种。不随难度缩放，和平难度下照样打人（黑雾按模组自己的规则，
 * 关掉 {@code dark_fog} 才不出）。
 */
public final class DarkFogDamage {
    public static final ResourceKey<DamageType> KEY = ResourceKey.create(Registries.DAMAGE_TYPE, Universe.id("dark_fog"));

    private DarkFogDamage() {
    }

    public static void bootstrap(BootstrapContext<DamageType> context) {
        context.register(KEY, new DamageType("universe.dark_fog", DamageScaling.NEVER, 0.1F));
    }

    public static DamageSource source(ServerLevel level, Entity direct, @Nullable Entity owner) {
        return level.damageSources().source(KEY, direct, owner);
    }

    /** 黑雾打不打这个实体：活着的玩家（不打创造、旁观模式）。守卫机、外星人、动物都不理。 */
    public static boolean targetable(@Nullable Entity entity) {
        return entity instanceof Player player && player.isAlive() && !player.isCreative() && !player.isSpectator();
    }

    /** 黑雾的单位打不打这个实体：上面那些玩家，再加上玩家机甲放出来的舰队单位（ADR-0033 §一）。 */
    public static boolean engageable(@Nullable Entity entity) {
        return targetable(entity) || entity instanceof FleetUnit unit && unit.isAlive();
    }

    /** {@code range} 格内的舰队单位；没有编队出动时不搜。 */
    public static List<FleetUnit> fleetUnits(ServerLevel level, Vec3 pos, double range) {
        if (!MechaFleet.anyOut()) {
            return List.of();
        }
        return level.getEntitiesOfClass(FleetUnit.class, AABB.ofSize(pos, range * 2, range * 2, range * 2), LivingEntity::isAlive);
    }

    /** {@code range} 格内离 {@code pos} 最近的玩家或者舰队单位。 */
    public static @Nullable LivingEntity nearestTarget(ServerLevel level, Vec3 pos, double range) {
        LivingEntity best = level.getNearestPlayer(pos.x, pos.y, pos.z, range, DarkFogDamage::targetable);
        double bestDistance = best == null ? range * range : best.distanceToSqr(pos);
        for (FleetUnit unit : fleetUnits(level, pos, range)) {
            double distance = unit.distanceToSqr(pos);
            if (distance < bestDistance) {
                best = unit;
                bestDistance = distance;
            }
        }
        return best;
    }

    /** 弹体、溅射不伤自己人，也不碰守卫机（互不理睬）。 */
    public static boolean spared(Entity entity) {
        return entity instanceof DarkFogEnemy || entity instanceof Sentinel;
    }

    /** 溅射能打到的：玩家和舰队单位之外不打别的活物，免得把动物、外星人炸死。 */
    public static boolean splashable(Entity entity) {
        return entity instanceof LivingEntity && engageable(entity);
    }
}
