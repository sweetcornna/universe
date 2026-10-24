package io.github.sweetcornna.universe.mecha.combat;

import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.darkfog.DarkFogEnemy;
import io.github.sweetcornna.universe.registry.UniverseEntities;
import io.github.sweetcornna.universe.registry.UniverseSounds;
import io.github.sweetcornna.universe.registry.sound.HostileSounds;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * 舰队的攻击无人机、驱逐舰和机甲的等离子炮打出的等离子弹（ADR-0033）：直线飞、不受重力，撞到黑雾或方块就炸，
 * 从黑雾旁边擦过也炸（近炸引信）。范围伤害只伤黑雾、不破坏方块，来源记在机甲的主人头上（{@link #getOwner()} 是玩家）。
 * 不存档。
 */
public class PlasmaShot extends ThrowableProjectile {
    /** 舰队的：浅蓝。 */
    public static final int FLEET = 0;
    /** 机甲的等离子胶囊：青色。 */
    public static final int PLASMA = 1;
    /** 机甲的反物质胶囊：品红，大一圈。 */
    public static final int ANTIMATTER = 2;
    /** 驱逐舰的主炮：浅蓝，大。 */
    public static final int HEAVY = 3;
    private static final int[] COLORS = {0x8FC8FF, 0x6ED8FF, 0xFF5AD2, 0xA8D8FF};
    private static final float[] SIZES = {0.6F, 0.9F, 1.3F, 1.6F};
    private static final EntityDataAccessor<Integer> DATA_VARIANT = SynchedEntityData.defineId(PlasmaShot.class, EntityDataSerializers.INT);
    private static final int LIFETIME = 80;
    private static final double FUSE = 1.5;

    private float damage;
    private double radius;
    /** 打出它的东西（舰队单位）；机甲自己打的是空的。伤害的直接来源。 */
    private @Nullable Entity shooter;

    public PlasmaShot(EntityType<? extends PlasmaShot> type, Level level) {
        super(type, level);
    }

    /** 从 {@code from} 打向 {@code target}（已经加进世界）。 */
    public static PlasmaShot fire(ServerLevel level, Player owner, @Nullable Entity shooter, Vec3 from, Vec3 target, float damage, double radius,
            double speed, int variant) {
        PlasmaShot shot = new PlasmaShot(UniverseEntities.PLASMA_SHOT.get(), level);
        shot.setPos(from);
        shot.setOwner(owner);
        shot.shooter = shooter;
        shot.damage = damage;
        shot.radius = radius;
        shot.entityData.set(DATA_VARIANT, variant);
        Vec3 direction = target.subtract(from);
        shot.setDeltaMovement(direction.lengthSqr() < 1e-6 ? new Vec3(0, speed, 0) : direction.normalize().scale(speed));
        level.addFreshEntity(shot);
        return shot;
    }

    public int variant() {
        return Math.floorMod(this.entityData.get(DATA_VARIANT), COLORS.length);
    }

    public int color() {
        return COLORS[this.variant()];
    }

    public float size() {
        return SIZES[this.variant()];
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_VARIANT, FLEET);
    }

    @Override
    protected double getDefaultGravity() {
        return 0;
    }

    @Override
    protected float getAirDrag() {
        return 1.0F;
    }

    @Override
    public void tick() {
        Vec3 before = this.position();
        super.tick();
        if (this.level().isClientSide()) {
            Vec3 now = this.position();
            double length = before.distanceTo(now);
            if (length > 1e-3 && this.tickCount > 1) {
                DustParticleOptions dust = new DustParticleOptions(this.color(), this.size() * 0.8F);
                int steps = Math.max(1, (int) (length / 0.6));
                for (int i = 0; i < steps; i++) {
                    Vec3 p = before.lerp(now, i / (double) steps);
                    this.level().addParticle(dust, p.x, p.y, p.z, 0, 0, 0);
                }
            }
            return;
        }
        if (this.isRemoved()) {
            return;
        }
        if (this.level() instanceof ServerLevel level && this.tickCount > 1) {
            Vec3 near = this.fuse(level, before, this.position());
            if (near != null) {
                this.setPos(near);
                this.burst(level);
                return;
            }
        }
        if (this.isInWater() || this.tickCount > LIFETIME) {
            this.discard();
        }
    }

    /** 近炸引信：这一刻飞过的那段路离哪个黑雾不到 {@link #FUSE} 格，就在离它最近的那一点炸。 */
    private @Nullable Vec3 fuse(ServerLevel level, Vec3 from, Vec3 to) {
        AABB swept = new AABB(from, to).inflate(FUSE + 3);
        Vec3 best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Mob mob : level.getEntitiesOfClass(Mob.class, swept, mob -> mob instanceof DarkFogEnemy && mob.isAlive())) {
            Vec3 center = mob.getBoundingBox().getCenter();
            Vec3 point = closest(from, to, center);
            double distance = Math.sqrt(mob.getBoundingBox().distanceToSqr(point));
            if (distance <= FUSE && distance < bestDistance) {
                bestDistance = distance;
                best = point;
            }
        }
        return best;
    }

    private static Vec3 closest(Vec3 a, Vec3 b, Vec3 p) {
        Vec3 ab = b.subtract(a);
        double length = ab.lengthSqr();
        if (length < 1e-9) {
            return a;
        }
        double t = Math.max(0, Math.min(1, p.subtract(a).dot(ab) / length));
        return a.add(ab.scale(t));
    }

    @Override
    protected boolean canHitEntity(Entity entity) {
        return entity instanceof DarkFogEnemy && super.canHitEntity(entity);
    }

    @Override
    protected void onHitEntity(EntityHitResult hitResult) {
        super.onHitEntity(hitResult);
        if (this.level() instanceof ServerLevel level) {
            this.burst(level);
        }
    }

    @Override
    protected void onHitBlock(BlockHitResult hitResult) {
        super.onHitBlock(hitResult);
        if (this.level() instanceof ServerLevel level) {
            this.setPos(hitResult.getLocation().add(hitResult.getDirection().getUnitVec3().scale(0.2)));
            this.burst(level);
        }
    }

    private void burst(ServerLevel level) {
        if (this.isRemoved()) {
            return;
        }
        Vec3 center = this.position();
        Entity direct = this.shooter != null && this.shooter.isAlive() ? this.shooter : this;
        Strikes.splash(level, center, this.damage, Math.max(0.8, this.radius), 0.15, direct, this.getOwner());
        int variant = this.variant();
        double r = Math.max(0.8, this.radius);
        level.sendParticles(new DustParticleOptions(this.color(), 1.4F), center.x, center.y, center.z, 10 + (int) (r * 6), r * 0.35, r * 0.35,
                r * 0.35, 0);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, center.x, center.y, center.z, 8, r * 0.3, r * 0.3, r * 0.3, 0.3);
        if (variant == ANTIMATTER || variant == HEAVY) {
            level.sendParticles(ParticleTypes.EXPLOSION, center.x, center.y, center.z, 1, 0, 0, 0, 0);
        }
        level.playSound(null, center.x, center.y, center.z, (variant == ANTIMATTER ? UniverseSounds.EXPLOSION_SMALL : HostileSounds.PLASMA_BURST).get(),
                SoundSource.PLAYERS, variant == HEAVY || variant == ANTIMATTER ? 1.0F : 0.6F, variant == FLEET ? 1.2F : 1.0F);
        this.discard();
    }
}
