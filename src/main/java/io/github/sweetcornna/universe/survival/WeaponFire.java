package io.github.sweetcornna.universe.survival;

import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.core.survival.ToolWeapon;
import io.github.sweetcornna.universe.encounter.SpaceEncounters;
import io.github.sweetcornna.universe.entity.PlasmaBolt;
import io.github.sweetcornna.universe.entity.PlasmaGrenade;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * 装在多功能工具上的武器怎么开火（ADR-0026 §六）。和爆能枪一样，伤害记在开火的人头上：打守卫机算挑衅、
 * 打死动物算捕杀，由守卫机受伤、生物死亡的现有逻辑去记。
 *
 * <p>这里只管打出去，射速、热量、蓄力在 {@link Multitool} 里算；开火的人可以是任何生物，游戏内测试用假玩家。
 */
public final class WeaponFire {
    /** 标枪打中时把目标水平推开的速度（格/刻）。 */
    private static final double JAVELIN_KNOCKBACK = 0.4;
    /** 标枪的光束每隔几格撒一个粒子。 */
    private static final double BEAM_STEP = 0.6;

    private WeaponFire() {
    }

    /** 散射爆能枪：一次打出 8 颗弹丸，散开，飞 16 格就没了。 */
    public static void scatter(ServerLevel level, LivingEntity shooter, Vec3 from, Vec3 direction, double damageBoost) {
        ToolWeapon weapon = ToolWeapon.SCATTER_BLASTER;
        int lifetime = weapon.lifetime(ToolWeapon.BOLT_SPEED);
        for (int i = 0; i < weapon.projectiles(); i++) {
            level.addFreshEntity(PlasmaBolt.shoot(level, shooter, from, direction, weapon.damage(damageBoost), weapon.color(),
                    ToolWeapon.BOLT_SPEED, ToolWeapon.SCATTER_SPREAD, lifetime));
        }
        level.playSound(null, shooter.getX(), shooter.getEyeY(), shooter.getZ(), SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.PLAYERS,
                0.6F, 0.7F + shooter.getRandom().nextFloat() * 0.1F);
        level.playSound(null, shooter.getX(), shooter.getEyeY(), shooter.getZ(), SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 0.4F, 1.3F);
        muzzleFlash(level, from, direction, weapon.color(), 4);
    }

    /** 脉冲喷射器：一颗散得很开的小弹。 */
    public static void pulse(ServerLevel level, LivingEntity shooter, Vec3 from, Vec3 direction, double damageBoost) {
        ToolWeapon weapon = ToolWeapon.PULSE_SPITTER;
        level.addFreshEntity(PlasmaBolt.shoot(level, shooter, from, direction, weapon.damage(damageBoost), weapon.color(),
                ToolWeapon.BOLT_SPEED, ToolWeapon.PULSE_SPREAD, weapon.lifetime(ToolWeapon.BOLT_SPEED)));
        level.playSound(null, shooter.getX(), shooter.getEyeY(), shooter.getZ(), SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 0.15F,
                2.0F);
        muzzleFlash(level, from, direction, weapon.color(), 1);
    }

    /**
     * 烈焰标枪：沿视线瞬间打中 120 格内的第一个目标，画一道光束。
     *
     * @param eye    瞄准的起点（眼睛）
     * @param look   视线方向
     * @param muzzle 光束画出来的起点（枪口）
     * @return 打中的实体；打中方块或什么都没打中是 null
     */
    public static @Nullable Entity javelin(ServerLevel level, LivingEntity shooter, Vec3 eye, Vec3 look, Vec3 muzzle, double damageBoost) {
        ToolWeapon weapon = ToolWeapon.BLAZE_JAVELIN;
        Vec3 end = eye.add(look.normalize().scale(weapon.range()));
        BlockHitResult block = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, shooter));
        Vec3 stop = block.getType() == HitResult.Type.MISS ? end : block.getLocation();
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(shooter, eye, stop, new AABB(eye, stop).inflate(1),
                target -> !target.isSpectator() && target.isPickable() && target.getRootVehicle() != shooter.getRootVehicle(),
                eye.distanceToSqr(stop));
        Vec3 impact = hit == null ? stop : hit.getLocation();
        Entity struck = null;
        if (hit != null) {
            struck = hit.getEntity();
            DamageSource source = level.damageSources().source(PlasmaBolt.DAMAGE, shooter, shooter);
            PlasmaBolt.clearHurtCooldown(struck);
            if (struck.hurtServer(level, source, weapon.damage(damageBoost))) {
                shooter.setLastHurtMob(struck);
                double resistance = struck instanceof LivingEntity living ? living.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE) : 0;
                Vec3 push = look.multiply(1, 0, 1);
                if (resistance < 1 && push.lengthSqr() > 1e-6) {
                    struck.push(push.normalize().scale(JAVELIN_KNOCKBACK * (1 - resistance)));
                }
            }
            level.sendParticles(ParticleTypes.FLAME, impact.x, impact.y, impact.z, 10, 0.2, 0.2, 0.2, 0.05);
        } else if (block.getType() == HitResult.Type.BLOCK) {
            level.sendParticles(ParticleTypes.LAVA, impact.x, impact.y, impact.z, 3, 0.1, 0.1, 0.1, 0);
            // 打中遇险的货船（ADR-0024），和等离子弹一样算
            SpaceEncounters.onBoltHit(level, shooter, block.getBlockPos());
        }
        beam(level, muzzle, impact, weapon.color());
        level.playSound(null, shooter.getX(), shooter.getEyeY(), shooter.getZ(), SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 0.9F,
                0.6F);
        level.playSound(null, impact.x, impact.y, impact.z, SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.6F, 1.4F);
        return struck;
    }

    /** 等离子发射器：打出一颗会弹跳的榴弹。 */
    public static PlasmaGrenade launch(ServerLevel level, LivingEntity shooter, Vec3 from, Vec3 direction, double damageBoost) {
        PlasmaGrenade grenade = PlasmaGrenade.launch(level, shooter, from, direction, damageBoost);
        level.addFreshEntity(grenade);
        level.playSound(null, shooter.getX(), shooter.getEyeY(), shooter.getZ(), SoundEvents.CROSSBOW_SHOOT, SoundSource.PLAYERS, 0.8F,
                0.6F);
        muzzleFlash(level, from, direction, ToolWeapon.PLASMA_LAUNCHER.color(), 3);
        return grenade;
    }

    /** 光束：一串和武器同色的光点，外加几点火星。 */
    private static void beam(ServerLevel level, Vec3 from, Vec3 to, int color) {
        DustParticleOptions dust = new DustParticleOptions(color, 1.0F);
        double length = from.distanceTo(to);
        int steps = Math.max(1, (int) (length / BEAM_STEP));
        for (int i = 0; i <= steps; i++) {
            Vec3 p = from.lerp(to, i / (double) steps);
            level.sendParticles(dust, p.x, p.y, p.z, 1, 0, 0, 0, 0);
            if (i % 8 == 4) {
                level.sendParticles(ParticleTypes.SMALL_FLAME, p.x, p.y, p.z, 1, 0.02, 0.02, 0.02, 0.005);
            }
        }
    }

    /** 枪口火光：放在枪口前一格多，贴着镜头的粒子会大得遮住视野。 */
    private static void muzzleFlash(ServerLevel level, Vec3 from, Vec3 direction, int color, int count) {
        Vec3 flash = from.add(direction.normalize().scale(1.2));
        level.sendParticles(new DustParticleOptions(color, 0.8F), flash.x, flash.y, flash.z, count, 0.05, 0.05, 0.05, 0);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, flash.x, flash.y, flash.z, 1, 0.02, 0.02, 0.02, 0.02);
    }
}
