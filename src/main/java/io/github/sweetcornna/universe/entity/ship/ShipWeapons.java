package io.github.sweetcornna.universe.entity.ship;

import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.core.ship.ShipCombat;
import io.github.sweetcornna.universe.entity.PlasmaBolt;
import io.github.sweetcornna.universe.network.ShipControlPayload;
import io.github.sweetcornna.universe.sentinel.Sentinel;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 飞船武器（ADR-0017）：光子炮开火、采矿光束照射、自动瞄准；船上的人挨的弹射物和爆炸伤害转给飞船。
 *
 * <p>开火方向由服务端按驾驶员的视线算（{@link ShipCombat#clampToCone}），客户端只上报按没按开火键。采矿光束是即时的射线，
 * 服务端判定打中了什么，客户端按同样的算法画光束。
 */
public final class ShipWeapons {
    /** 光子弹：暖黄色。 */
    public static final int BOLT_COLOR = 0xFFD45A;
    /** 采矿光束：橙红色。 */
    public static final int BEAM_COLOR = 0xFF6A3D;
    private static final DustParticleOptions BEAM_DUST = new DustParticleOptions(BEAM_COLOR, 0.9F);
    private static final DustParticleOptions BEAM_CORE = new DustParticleOptions(0xFFE6B8, 0.5F);
    /** 采矿光束打到生物时每隔几刻伤一次、伤多少。 */
    private static final int BEAM_HURT_INTERVAL = 10;
    private static final float BEAM_HURT = 3.5F;
    /** 一艘船最多画几道光束。 */
    private static final int MAX_DRAWN_BEAMS = 3;

    private ShipWeapons() {
    }

    public static void registerEvents(IEventBus gameBus) {
        gameBus.addListener(LivingIncomingDamageEvent.class, ShipWeapons::redirect);
    }

    /** 船上的人挨的弹射物、爆炸伤害由飞船承受。 */
    private static void redirect(LivingIncomingDamageEvent event) {
        LivingEntity entity = event.getEntity();
        if (!(entity.getVehicle() instanceof ShipEntity ship) || !(entity.level() instanceof ServerLevel level)) {
            return;
        }
        DamageSource source = event.getSource();
        if (!ShipEntity.isShipDamage(source)) {
            return;
        }
        event.setCanceled(true);
        ship.hurtServer(level, source, event.getAmount());
    }

    public static void handleControl(ShipControlPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player) || !(player.getVehicle() instanceof ShipEntity ship) || ship.pilot() != player) {
            return;
        }
        switch (payload.action()) {
            case ShipControlPayload.FIRE_ON -> ship.setFiring(true);
            case ShipControlPayload.FIRE_OFF -> ship.setFiring(false);
            case ShipControlPayload.MODE -> ship.toggleWeaponMode(player);
            default -> {
            }
        }
    }

    /** 能被自动瞄准的敌人：怪物、守卫机、海盗（海盗战机实现 {@link Enemy}）。 */
    public static boolean hostile(Entity entity) {
        return entity instanceof Enemy || entity instanceof Sentinel;
    }

    /** 炮口朝的方向：驾驶员的视线，限制在船头的锥形里。 */
    public static Vec3 aim(ShipEntity ship, Entity pilot) {
        Vec3 look = pilot.getViewVector(1);
        return vec(ShipCombat.clampToCone(ShipCombat.forward(ship.getYRot()), arr(look), ShipCombat.AIM_CONE));
    }

    /** 自动瞄准的目标：驾驶员视线 {@link ShipCombat#ASSIST_CONE}° 以内、最贴近视线的敌人。 */
    public static @Nullable Entity assistTarget(ShipEntity ship, Entity pilot) {
        Vec3 eye = pilot.getEyePosition();
        Vec3 look = pilot.getViewVector(1);
        AABB box = new AABB(eye, eye).inflate(ShipCombat.ASSIST_RANGE);
        Entity best = null;
        double bestAngle = ShipCombat.ASSIST_CONE;
        for (Entity entity : ship.level().getEntities(ship, box, e -> hostile(e) && e.isAlive() && e.getRootVehicle() != ship)) {
            Vec3 to = entity.getBoundingBox().getCenter().subtract(eye);
            double distance = to.length();
            if (distance < 1 || distance > ShipCombat.ASSIST_RANGE) {
                continue;
            }
            double angle = ShipCombat.angle(arr(look), arr(to.scale(1 / distance)));
            if (angle < bestAngle) {
                bestAngle = angle;
                best = entity;
            }
        }
        return best;
    }

    /** 武器方块的炮口在世界里的位置：方块中心朝炮口方向挪出去一点。 */
    public static Vec3 muzzle(ShipEntity ship, ShipStructure.ShipBlock block, float relativeYaw) {
        Direction facing = block.state().hasProperty(BlockStateProperties.HORIZONTAL_FACING)
                ? block.state().getValue(BlockStateProperties.HORIZONTAL_FACING) : Direction.NORTH;
        return ship.toWorld(block.x() + facing.getStepX() * 0.75, block.y() + 0.5, block.z() + facing.getStepZ() * 0.75, relativeYaw);
    }

    /** 服务端：光子炮打出 {@code shots} 发，几门炮轮流开火。 */
    static void fireCannons(ShipEntity ship, ServerLevel level, Player pilot, int shots) {
        ShipStructure structure = ship.structure();
        int[] cannons = structure.cannons();
        if (cannons.length == 0) {
            return;
        }
        float relative = ship.getYRot() - ship.baseYaw();
        double[] forward = ShipCombat.forward(ship.getYRot());
        Vec3 aim = aim(ship, pilot);
        Entity target = assistTarget(ship, pilot);
        // 光子弹带着飞船的速度：高速飞行时也打得到前面
        Vec3 shipVelocity = ship.getDeltaMovement();
        for (int i = 0; i < shots; i++) {
            Vec3 muzzle = muzzle(ship, structure.block(cannons[ship.nextCannon(cannons.length)]), relative);
            Vec3 direction = aim;
            if (target != null) {
                Vec3 to = target.getBoundingBox().getCenter().subtract(muzzle);
                Vec3 relativeVelocity = target.getDeltaMovement().subtract(shipVelocity);
                direction = vec(ShipCombat.clampToCone(forward, ShipCombat.lead(arr(to), arr(relativeVelocity), ShipCombat.BOLT_SPEED),
                        ShipCombat.AIM_CONE));
            }
            PlasmaBolt bolt = PlasmaBolt.shoot(level, pilot, muzzle, direction, ShipCombat.CANNON_DAMAGE, BOLT_COLOR, ShipCombat.BOLT_SPEED,
                    0.4F, ShipCombat.BOLT_LIFETIME);
            bolt.setDeltaMovement(bolt.getDeltaMovement().add(shipVelocity));
            level.addFreshEntity(bolt);
            level.playSound(null, muzzle.x, muzzle.y, muzzle.z, SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 0.35F,
                    1.9F + level.getRandom().nextFloat() * 0.1F);
        }
    }

    /**
     * 采矿光束的一次射线。
     *
     * @param from   第一台光束的炮口
     * @param to     打中的点（没打中就是射程尽头）
     * @param entity 打中的实体
     * @param hit    打中了方块或实体
     */
    public record BeamHit(Vec3 from, Vec3 to, @Nullable Entity entity, boolean hit) {
    }

    /** 采矿光束照到哪：瞄准方向同光子炮，有自动瞄准的目标就直接对着它（光束是即时的，不要提前量）。 */
    public static BeamHit beam(ShipEntity ship, Entity pilot) {
        ShipStructure structure = ship.structure();
        float relative = ship.getYRot() - ship.baseYaw();
        Vec3 from = muzzle(ship, structure.block(structure.beams()[0]), relative);
        Vec3 direction = aim(ship, pilot);
        Entity target = assistTarget(ship, pilot);
        if (target != null) {
            direction = vec(ShipCombat.clampToCone(ShipCombat.forward(ship.getYRot()),
                    arr(target.getBoundingBox().getCenter().subtract(from).normalize()), ShipCombat.AIM_CONE));
        }
        Level level = ship.level();
        Vec3 end = from.add(direction.scale(ShipCombat.BEAM_RANGE));
        BlockHitResult block = level.clip(new ClipContext(from, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, pilot));
        boolean hit = block.getType() != HitResult.Type.MISS;
        if (hit) {
            end = block.getLocation();
        }
        EntityHitResult entity = ProjectileUtil.getEntityHitResult(level, ship, from, end, new AABB(from, end).inflate(1),
                e -> e.isAlive() && e.isPickable() && !e.isSpectator() && e.getRootVehicle() != ship, 0.3F);
        if (entity != null) {
            return new BeamHit(from, entity.getLocation(), entity.getEntity(), true);
        }
        return new BeamHit(from, end, null, hit);
    }

    /** 服务端：采矿光束照射这一刻。 */
    static void fireBeam(ShipEntity ship, ServerLevel level, Player pilot) {
        BeamHit hit = beam(ship, pilot);
        Entity entity = hit.entity();
        if (entity instanceof ShipBeamTarget target) {
            target.beamed(level, ship, pilot, ShipCombat.BEAM_DAMAGE);
        } else if (entity != null && ship.tickCount % BEAM_HURT_INTERVAL == 0) {
            entity.hurtServer(level, level.damageSources().source(PlasmaBolt.DAMAGE, ship, pilot), BEAM_HURT);
        }
        if (ship.tickCount % 8 == 0) {
            level.playSound(null, hit.from().x, hit.from().y, hit.from().z, SoundEvents.BEACON_AMBIENT, SoundSource.PLAYERS, 0.8F, 1.8F);
        }
    }

    /** 客户端：画出光束（从每台光束的炮口到打中的点），打中的地方冒火花。 */
    public static void drawBeams(ShipEntity ship) {
        Player pilot = ship.pilot();
        ShipStructure structure = ship.structure();
        if (pilot == null || structure == null || structure.beams().length == 0) {
            return;
        }
        BeamHit hit = beam(ship, pilot);
        Level level = ship.level();
        float relative = ship.getYRot() - ship.baseYaw();
        int[] beams = structure.beams();
        for (int k = 0; k < Math.min(beams.length, MAX_DRAWN_BEAMS); k++) {
            Vec3 from = muzzle(ship, structure.block(beams[k]), relative);
            Vec3 path = hit.to().subtract(from);
            double length = path.length();
            int steps = Math.max(1, (int) (length / 0.7));
            double offset = level.getRandom().nextDouble();
            for (int i = 0; i < steps; i++) {
                Vec3 p = from.add(path.scale((i + offset) / steps));
                level.addParticle(i % 2 == 0 ? BEAM_DUST : BEAM_CORE, p.x, p.y, p.z, 0, 0, 0);
            }
        }
        if (hit.hit()) {
            Vec3 p = hit.to();
            for (int i = 0; i < 3; i++) {
                level.addParticle(ParticleTypes.ELECTRIC_SPARK, p.x, p.y, p.z, (level.getRandom().nextDouble() - 0.5) * 0.4,
                        (level.getRandom().nextDouble() - 0.5) * 0.4, (level.getRandom().nextDouble() - 0.5) * 0.4);
            }
            if (level.getRandom().nextInt(3) == 0) {
                level.addParticle(ParticleTypes.SMOKE, p.x, p.y, p.z, 0, 0.03, 0);
            }
        }
    }

    static double[] arr(Vec3 v) {
        return new double[] {v.x, v.y, v.z};
    }

    static Vec3 vec(double[] v) {
        return new Vec3(v[0], v[1], v[2]);
    }
}
