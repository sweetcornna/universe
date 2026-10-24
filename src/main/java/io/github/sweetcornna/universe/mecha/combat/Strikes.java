package io.github.sweetcornna.universe.mecha.combat;

import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.core.darkfog.AmmoKind;
import io.github.sweetcornna.universe.darkfog.DarkFogEnemy;
import io.github.sweetcornna.universe.darkfog.entity.DarkFogBuilding;
import io.github.sweetcornna.universe.darkfog.hive.HiveBuilding;
import io.github.sweetcornna.universe.entity.PlasmaBolt;
import io.github.sweetcornna.universe.mecha.fleet.FleetUnit;
import io.github.sweetcornna.universe.sentinel.Sentinel;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * 机甲和舰队打出去的伤害（ADR-0033）：来源记在机甲的主人（玩家）头上，黑雾的基地、巢穴照常认得出是玩家方面打的。
 * 只打黑雾和怪物，不打玩家、守卫机、舰队单位和中立的生物。
 */
public final class Strikes {
    private Strikes() {
    }

    public static DamageSource source(ServerLevel level, @Nullable Entity direct, @Nullable Entity owner) {
        return level.damageSources().source(PlasmaBolt.DAMAGE, direct, owner);
    }

    /** 黑雾（单位和建筑）。 */
    public static boolean darkFog(Entity entity) {
        return entity instanceof DarkFogEnemy && entity.isAlive();
    }

    public static boolean building(Entity entity) {
        return entity instanceof DarkFogBuilding || entity instanceof HiveBuilding;
    }

    /**
     * 机甲能打的敌人：黑雾，和怪物（原版的 {@link Enemy}）。守卫机、舰队单位、玩家不打；僵尸猪灵这类中立的，
     * 只有在找 {@code player} 麻烦的时候才打，免得一枪惹来一群。
     */
    public static boolean hostile(Entity entity, @Nullable Player player) {
        if (!(entity instanceof LivingEntity living) || !living.isAlive() || entity instanceof Player || entity instanceof FleetUnit
                || entity instanceof Sentinel) {
            return false;
        }
        if (entity instanceof DarkFogEnemy) {
            return true;
        }
        if (!(entity instanceof Enemy)) {
            return false;
        }
        if (entity instanceof NeutralMob && entity instanceof Mob mob) {
            return player != null && mob.getTarget() == player;
        }
        return true;
    }

    /**
     * 打一下：不管上次挨打过了多久都算（连发的弹不互相吞）。
     *
     * @return 伤害生效了
     */
    public static boolean hit(ServerLevel level, LivingEntity target, float damage, @Nullable Entity direct, @Nullable Entity owner) {
        if (!target.isAlive() || damage <= 0) {
            return false;
        }
        PlasmaBolt.clearHurtCooldown(target);
        return target.hurtServer(level, source(level, direct, owner), damage);
    }

    /**
     * 范围伤害：只伤黑雾，离爆心越远伤害越低（到边上一半），推开一点。
     *
     * @return 炸到了几个
     */
    public static int splash(ServerLevel level, Vec3 center, float damage, double radius, double push, @Nullable Entity direct,
            @Nullable Entity owner) {
        int hit = 0;
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(radius + 3), Strikes::darkFog)) {
            double distance = Math.sqrt(target.getBoundingBox().distanceToSqr(center));
            float amount = AmmoKind.splash(damage, radius, distance);
            if (amount <= 0 || !hit(level, target, amount, direct, owner)) {
                continue;
            }
            hit++;
            if (push != 0 && target.isAlive()) {
                shove(target, center, push * (1 - distance / Math.max(1e-6, radius)));
            }
        }
        return hit;
    }

    /** 从 {@code center} 往外推（{@code strength} 是负数就往里吸），按击退抗性打折。 */
    public static void shove(LivingEntity target, Vec3 center, double strength) {
        double resistance = target.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
        Vec3 away = target.getBoundingBox().getCenter().subtract(center);
        if (resistance >= 1 || away.lengthSqr() < 1e-6 || strength == 0) {
            return;
        }
        Vec3 shove = away.normalize().scale(strength * (1 - resistance));
        target.push(new Vec3(shove.x, strength > 0 ? Math.abs(strength) * 0.3 : shove.y, shove.z));
    }

    /** 从 {@code from} 到 {@code to} 中间没有方块挡着。 */
    public static boolean clear(ServerLevel level, Vec3 from, Vec3 to, Entity viewer) {
        return level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, viewer)).getType() == HitResult.Type.MISS;
    }

    /** 瞄哪里：碰撞箱的中间（大个子的建筑也是）。 */
    public static Vec3 aimPoint(Entity target) {
        return target.getBoundingBox().getCenter();
    }

    /** 一道子弹的曳光：沿路撒一串小亮点，打中的地方迸一点火星。 */
    public static void tracer(ServerLevel level, Vec3 from, Vec3 to, int rgb) {
        Vec3 path = to.subtract(from);
        double length = path.length();
        if (length < 1e-3) {
            return;
        }
        DustParticleOptions dust = new DustParticleOptions(rgb, 0.45F);
        int steps = Math.max(2, (int) (length / 1.2));
        for (int i = 1; i <= steps; i++) {
            Vec3 p = from.add(path.scale(i / (double) steps));
            level.sendParticles(dust, p.x, p.y, p.z, 1, 0, 0, 0, 0);
        }
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.CRIT, to.x, to.y, to.z, 4, 0.1, 0.1, 0.1, 0.2);
    }
}
