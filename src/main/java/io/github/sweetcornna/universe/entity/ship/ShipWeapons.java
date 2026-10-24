package io.github.sweetcornna.universe.entity.ship;

import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.asteroid.Asteroid;
import io.github.sweetcornna.universe.core.ship.ShipCombat;
import io.github.sweetcornna.universe.entity.PlasmaBolt;
import io.github.sweetcornna.universe.network.ShipControlPayload;
import io.github.sweetcornna.universe.pirate.PirateFighter;
import io.github.sweetcornna.universe.registry.sound.ShipSounds;
import io.github.sweetcornna.universe.sentinel.Sentinel;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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
 * <p>开火方向由服务端按驾驶员的视线算，客户端只上报按没按开火键。每门炮（光束）有自己的射界（{@link ShipCombat#clampToArc}，
 * 按方块的朝向）：轮到的炮转不过去就换一门转得过去的打，都转不过去时朝射界边缘打。采矿光束是即时的射线，
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
    /** 客户端看到的目标大约晚几刻（插值加网络）。 */
    private static final double VIEW_LAG = 3;
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

    /** 炮口朝的方向：驾驶员的视线；哪门炮（光束）都转不过去时，取射界边缘里离视线最近的方向。 */
    public static Vec3 aim(ShipEntity ship, Entity pilot) {
        Vec3 look = pilot.getViewVector(1);
        ShipStructure structure = ship.structure();
        if (structure == null) {
            return look;
        }
        double[] wanted = arr(look);
        double[] best = wanted;
        double bestAngle = Double.MAX_VALUE;
        for (int index : weapons(ship, structure)) {
            double[] clamped = ShipCombat.clampToArc(weaponYaw(ship, structure.block(index)), wanted);
            double angle = ShipCombat.angle(clamped, wanted);
            if (angle < bestAngle) {
                bestAngle = angle;
                best = clamped;
            }
        }
        return vec(best);
    }

    /** 有没有哪门炮（光束）转得过去打 {@code target}：客户端把射界外的锁定框画成灰色。 */
    public static boolean inArc(ShipEntity ship, Entity pilot, Entity target) {
        ShipStructure structure = ship.structure();
        if (structure == null) {
            return false;
        }
        double[] to = arr(target.getBoundingBox().getCenter().subtract(pilot.getEyePosition()));
        for (int index : weapons(ship, structure)) {
            if (ShipCombat.inArc(weaponYaw(ship, structure.block(index)), to)) {
                return true;
            }
        }
        return false;
    }

    /** 现在用的那种武器（光子炮或采矿光束）在结构里的下标。 */
    private static int[] weapons(ShipEntity ship, ShipStructure structure) {
        return ship.beamMode() ? structure.beams() : structure.cannons();
    }

    /** 武器方块此刻朝的偏航（度）：方块的朝向跟着船转。 */
    public static float weaponYaw(ShipEntity ship, ShipStructure.ShipBlock block) {
        return facing(block).toYRot() + ship.getYRot() - ship.baseYaw();
    }

    private static Direction facing(ShipStructure.ShipBlock block) {
        return block.state().hasProperty(BlockStateProperties.HORIZONTAL_FACING)
                ? block.state().getValue(BlockStateProperties.HORIZONTAL_FACING) : Direction.NORTH;
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
            // 客户端插值，看到的目标比服务端晚几刻：几刻前的位置也算对准了，不然近处快速飞过的海盗总也锁不上
            Vec3 seen = to.subtract(entity.getDeltaMovement().scale(VIEW_LAG));
            double angle = Math.min(ShipCombat.angle(arr(look), arr(to.scale(1 / distance))),
                    ShipCombat.angle(arr(look), arr(seen.normalize())));
            if (angle < bestAngle) {
                bestAngle = angle;
                best = entity;
            }
        }
        return best;
    }

    /** 武器方块的炮口在世界里的位置：方块中心朝炮口方向挪出去一点。 */
    public static Vec3 muzzle(ShipEntity ship, ShipStructure.ShipBlock block, float relativeYaw) {
        Direction facing = facing(block);
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
        Vec3 look = pilot.getViewVector(1);
        Entity target = assistTarget(ship, pilot);
        // 光子弹带着飞船的速度：高速飞行时也打得到前面
        Vec3 shipVelocity = ship.getDeltaMovement();
        for (int i = 0; i < shots; i++) {
            // 轮到的炮转不过去就往后找一门转得过去的；一门都转不过去，轮到的那门朝射界边缘打
            Vec3 muzzle = null;
            Vec3 direction = null;
            for (int k = 0; k < cannons.length; k++) {
                ShipStructure.ShipBlock block = structure.block(cannons[ship.nextCannon(cannons.length)]);
                Vec3 from = muzzle(ship, block, relative);
                double[] want = target == null ? arr(look) : lead(target, from, shipVelocity);
                float yaw = weaponYaw(ship, block);
                if (ShipCombat.inArc(yaw, want)) {
                    muzzle = from;
                    direction = vec(ShipCombat.normalize(want));
                    break;
                }
                if (k == 0) {
                    muzzle = from;
                    direction = vec(ShipCombat.clampToArc(yaw, want));
                }
                if (k == cannons.length - 1) {
                    // 转了一圈又回到第一门：下一发从它后面那门轮起
                    ship.nextCannon(cannons.length);
                }
            }
            // 火控校准的技术和战斗机的专长（ADR-0024）
            float damage = (float) (ShipCombat.CANNON_DAMAGE * ship.structure().stats().damage());
            PlasmaBolt bolt = PlasmaBolt.shoot(level, pilot, muzzle, direction, damage, BOLT_COLOR, ShipCombat.BOLT_SPEED,
                    0.4F, ShipCombat.BOLT_LIFETIME);
            bolt.setDeltaMovement(bolt.getDeltaMovement().add(shipVelocity));
            level.addFreshEntity(bolt);
            level.playSound(null, muzzle.x, muzzle.y, muzzle.z, ShipSounds.PHOTON.get(), SoundSource.PLAYERS, 0.8F,
                    0.95F + level.getRandom().nextFloat() * 0.1F);
        }
    }

    /**
     * 打 {@code target} 要朝的方向（带提前量）。弹体带着飞船的速度，下一刻才飞第一步，而目标这一刻还要再动一下，
     * 所以从它这一刻动完的位置算起。转着弯的海盗战机连加速度一起预判（假设它照现在的加速度接着转），不然一直打在它身后：
     * 按刻累加，目标第 m 步时在 {@code p + (m+1)v + a(m+1)(m+2)/2}，换成从这一刻动完算起就是
     * {@code (p + v + a) + m(v + 1.5a) + a m²/2}。
     */
    private static double[] lead(Entity target, Vec3 muzzle, Vec3 shipVelocity) {
        Vec3 velocity = target.getDeltaMovement();
        Vec3 accel = target instanceof PirateFighter pirate ? pirate.acceleration() : Vec3.ZERO;
        Vec3 to = target.getBoundingBox().getCenter().add(velocity).add(accel).subtract(muzzle);
        Vec3 relative = velocity.add(accel.scale(1.5)).subtract(shipVelocity);
        return ShipCombat.lead(arr(to), arr(relative), arr(accel), ShipCombat.BOLT_SPEED, ShipCombat.BOLT_LIFETIME);
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

    /**
     * 采矿光束照到哪：朝驾驶员的视线，有自动瞄准的目标就直接对着它（光束是即时的，不要提前量）。从第一台转得过去的光束照出去；
     * 都转不过去就用第一台，朝射界边缘照。
     */
    public static BeamHit beam(ShipEntity ship, Entity pilot) {
        ShipStructure structure = ship.structure();
        float relative = ship.getYRot() - ship.baseYaw();
        Vec3 look = pilot.getViewVector(1);
        Entity target = assistTarget(ship, pilot);
        Vec3 from = null;
        Vec3 direction = null;
        for (int index : structure.beams()) {
            ShipStructure.ShipBlock block = structure.block(index);
            Vec3 muzzle = muzzle(ship, block, relative);
            double[] wanted = target == null ? arr(look) : arr(target.getBoundingBox().getCenter().subtract(muzzle));
            float yaw = weaponYaw(ship, block);
            if (ShipCombat.inArc(yaw, wanted)) {
                from = muzzle;
                direction = vec(ShipCombat.normalize(wanted));
                break;
            }
            if (from == null) {
                from = muzzle;
                direction = vec(ShipCombat.clampToArc(yaw, wanted));
            }
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
            // 几台光束照着同一处，伤害叠加
            target.beamed(level, ship, pilot, (float) (ShipCombat.BEAM_DAMAGE * ship.structure().beams().length * ship.structure().stats().damage()));
        } else if (entity != null && ship.tickCount % BEAM_HURT_INTERVAL == 0) {
            entity.hurtServer(level, level.damageSources().source(PlasmaBolt.DAMAGE, ship, pilot), BEAM_HURT);
        }
        // 每段 0.8 秒、两头淡入淡出，8 刻放一段正好接上；音高不能变，变了接缝处会错相
        if (ship.tickCount % 8 == 0) {
            level.playSound(null, hit.from().x, hit.from().y, hit.from().z, ShipSounds.BEAM.get(), SoundSource.PLAYERS, 0.7F, 1.0F);
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
        if (hit.entity() instanceof Asteroid asteroid && level.getRandom().nextInt(2) == 0) {
            asteroid.chips(hit.to());
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
