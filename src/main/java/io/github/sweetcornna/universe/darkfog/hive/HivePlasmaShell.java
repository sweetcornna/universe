package io.github.sweetcornna.universe.darkfog.hive;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import io.github.sweetcornna.universe.core.darkfog.HiveWeapons;
import io.github.sweetcornna.universe.darkfog.entity.DarkFogDamage;
import io.github.sweetcornna.universe.entity.PlasmaBolt;
import io.github.sweetcornna.universe.entity.ship.ShipEntity;
import io.github.sweetcornna.universe.registry.UniverseEntities;
import io.github.sweetcornna.universe.registry.sound.HostileSounds;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * 巢穴等离子塔的炮弹（ADR-0028 §八）：太空里打飞船用，不受重力、直线飞，飞得不快。打中东西、或者从飞船、人旁边
 * {@link #FUSE} 格内擦过就炸开，范围里的飞船和没坐飞船的人都挨打（坐在飞船里的按飞船算一次）。飞满寿命自己散掉。
 */
public class HivePlasmaShell extends ThrowableProjectile {
    /** 近炸：离目标的碰撞箱这么近就炸。 */
    public static final double FUSE = 2.5;
    private static final byte EVENT_BURST = EntityEvent.DEATH;

    private float damage = 10;
    private int lifetime = 90;

    public HivePlasmaShell(EntityType<? extends HivePlasmaShell> type, Level level) {
        super(type, level);
    }

    /** 从 {@code from} 朝 {@code direction} 打出一发，已经加进世界。 */
    public static HivePlasmaShell fire(ServerLevel level, Entity owner, Vec3 from, Vec3 direction, float damage, double speed, float inaccuracy,
            int lifetime) {
        HivePlasmaShell shell = new HivePlasmaShell(UniverseEntities.HIVE_PLASMA_SHELL.get(), level);
        shell.setOwner(owner);
        shell.setPos(from);
        shell.damage = damage;
        shell.lifetime = lifetime;
        shell.shoot(direction.x, direction.y, direction.z, (float) speed, inaccuracy);
        level.addFreshEntity(shell);
        return shell;
    }

    public float damage() {
        return this.damage;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
    }

    @Override
    protected double getDefaultGravity() {
        return 0;
    }

    @Override
    protected float getAirDrag() {
        return 1;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 256 * 256;
    }

    @Override
    public void tick() {
        Vec3 before = this.position();
        super.tick();
        if (this.level().isClientSide()) {
            if (this.tickCount > 1) {
                Vec3 now = this.position();
                DustParticleOptions dust = new DustParticleOptions(HiveBuilding.PLASMA_COLOR, 1.4F);
                for (int i = 0; i < 3; i++) {
                    Vec3 p = before.lerp(now, i / 3.0);
                    this.level().addParticle(dust, p.x, p.y, p.z, 0, 0, 0);
                }
            }
            return;
        }
        if (this.isRemoved()) {
            return;
        }
        if (this.tickCount > this.lifetime) {
            this.discard();
            return;
        }
        if (this.level() instanceof ServerLevel level && this.nearTarget(level)) {
            this.burst(level, this.position());
        }
    }

    /** 近炸：飞船或者人在 {@link #FUSE} 格内。 */
    private boolean nearTarget(ServerLevel level) {
        AABB box = this.getBoundingBox().inflate(FUSE + 8);
        for (ShipEntity ship : level.getEntitiesOfClass(ShipEntity.class, box, HiveBuilding::shootableShip)) {
            if (ship.getBoundingBox().inflate(FUSE).contains(this.position())) {
                return true;
            }
        }
        for (Player player : this.players(level)) {
            if (DarkFogDamage.targetable(player) && !(player.getVehicle() instanceof ShipEntity)
                    && player.getBoundingBox().inflate(FUSE).contains(this.position())) {
                return true;
            }
        }
        return false;
    }

    /** 打塔的玩家：塔在测试里看着的假玩家也算。 */
    private List<? extends Player> players(ServerLevel level) {
        return this.getOwner() instanceof HiveBuilding tower ? tower.players(level) : level.players();
    }

    @Override
    protected boolean canHitEntity(Entity entity) {
        return super.canHitEntity(entity) && !DarkFogDamage.spared(entity);
    }

    @Override
    protected void onHit(HitResult hitResult) {
        super.onHit(hitResult);
        if (this.level() instanceof ServerLevel level && !this.isRemoved()) {
            this.burst(level, hitResult.getLocation());
        }
    }

    /** 炸开：范围里的飞船、没坐飞船的人挨打，离爆心越远越轻。 */
    private void burst(ServerLevel level, Vec3 at) {
        double radius = HiveWeapons.Weapon.PLASMA.splash();
        Set<Entity> hit = new LinkedHashSet<>();
        AABB box = new AABB(at, at).inflate(radius + 8);
        for (ShipEntity ship : level.getEntitiesOfClass(ShipEntity.class, box, HiveBuilding::shootableShip)) {
            if (Math.sqrt(ship.getBoundingBox().distanceToSqr(at)) <= radius) {
                hit.add(ship);
            }
        }
        for (Player player : this.players(level)) {
            if (DarkFogDamage.targetable(player) && !(player.getVehicle() instanceof ShipEntity)
                    && Math.sqrt(player.getBoundingBox().distanceToSqr(at)) <= radius) {
                hit.add(player);
            }
        }
        for (Entity entity : hit) {
            float amount = HiveWeapons.splashDamage(this.damage, radius, Math.sqrt(entity.getBoundingBox().distanceToSqr(at)));
            PlasmaBolt.clearHurtCooldown(entity);
            entity.hurtServer(level, DarkFogDamage.source(level, this, this.getOwner()), amount);
        }
        level.playSound(null, at.x, at.y, at.z, HostileSounds.PLASMA_BURST.get(), SoundSource.HOSTILE, 1.0F, 0.85F);
        this.setPos(at);
        level.broadcastEntityEvent(this, EVENT_BURST);
        this.discard();
    }

    @Override
    public void handleEntityEvent(@EntityEvent.Value byte id) {
        if (id != EVENT_BURST) {
            super.handleEntityEvent(id);
            return;
        }
        double radius = HiveWeapons.Weapon.PLASMA.splash();
        this.level().addParticle(ParticleTypes.EXPLOSION, this.getX(), this.getY(), this.getZ(), 0, 0, 0);
        DustParticleOptions dust = new DustParticleOptions(HiveBuilding.PLASMA_COLOR, 1.8F);
        for (int i = 0; i < 24; i++) {
            double yaw = this.random.nextDouble() * Math.PI * 2;
            double pitch = Math.acos(this.random.nextDouble() * 2 - 1);
            double r = radius * (0.5 + this.random.nextDouble() * 0.5);
            double dx = Math.sin(pitch) * Math.cos(yaw);
            double dy = Math.cos(pitch);
            double dz = Math.sin(pitch) * Math.sin(yaw);
            this.level().addParticle(dust, this.getX() + dx * r, this.getY() + dy * r, this.getZ() + dz * r, 0, 0, 0);
            this.level().addParticle(ParticleTypes.ELECTRIC_SPARK, this.getX(), this.getY(), this.getZ(), dx * 0.4, dy * 0.4, dz * 0.4);
        }
    }
}
