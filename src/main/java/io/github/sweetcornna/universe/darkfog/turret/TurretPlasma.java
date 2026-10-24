package io.github.sweetcornna.universe.darkfog.turret;

import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.core.darkfog.AmmoKind;
import io.github.sweetcornna.universe.core.darkfog.TurretKind;
import io.github.sweetcornna.universe.darkfog.DarkFogEnemy;
import io.github.sweetcornna.universe.registry.UniverseEntities;
import io.github.sweetcornna.universe.registry.UniverseSounds;
import io.github.sweetcornna.universe.registry.sound.HostileSounds;
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * 等离子团（ADR-0028 §八）：等离子炮塔、近程等离子炮塔打出去的，直线飞、不受重力，撞到黑雾或方块就炸，
 * 从黑雾旁边 {@value #FUSE} 格内擦过也炸（近炸引信）。范围伤害只伤黑雾、不破坏方块。飞够时间没撞上就散掉。不存档。
 */
public class TurretPlasma extends ThrowableProjectile {
    /** 等离子胶囊：青蓝色。 */
    public static final int PLASMA = 0;
    /** 反物质胶囊：品红色，大一圈。 */
    public static final int ANTIMATTER = 1;
    /** 近程等离子炮塔：紫色，小。 */
    public static final int SHORT_RANGE = 2;
    private static final int[] COLORS = {0x6ED8FF, 0xFF5AD2, 0xB070FF};
    private static final float[] SIZES = {0.9F, 1.3F, 0.7F};
    private static final EntityDataAccessor<Integer> DATA_VARIANT = SynchedEntityData.defineId(TurretPlasma.class, EntityDataSerializers.INT);
    private static final int LIFETIME = 60;
    /** 近炸：离黑雾的碰撞箱这么近就炸（格）。 */
    private static final double FUSE = 2;

    private @Nullable BlockPos turret;
    private float damage;
    private double radius;

    public TurretPlasma(EntityType<? extends TurretPlasma> type, Level level) {
        super(type, level);
    }

    /** 等离子炮塔装着这种胶囊，从 {@code from} 打向 {@code target}（还没加进世界）。 */
    public static TurretPlasma launch(ServerLevel level, BlockPos turret, Vec3 from, Vec3 target, AmmoKind ammo) {
        return launch(level, turret, from, target, ammo.damage(), ammo.radius(), ammo == AmmoKind.ANTIMATTER_CAPSULE ? ANTIMATTER : PLASMA);
    }

    /** 近程等离子炮塔的一发。 */
    public static TurretPlasma shortRange(ServerLevel level, BlockPos turret, Vec3 from, Vec3 target) {
        return launch(level, turret, from, target, TurretKind.SR_PLASMA_DAMAGE, TurretKind.SR_PLASMA_RADIUS, SHORT_RANGE);
    }

    private static TurretPlasma launch(ServerLevel level, BlockPos turret, Vec3 from, Vec3 target, float damage, double radius, int variant) {
        TurretPlasma plasma = new TurretPlasma(UniverseEntities.TURRET_PLASMA.get(), level);
        plasma.setPos(from);
        plasma.turret = turret.immutable();
        plasma.damage = damage;
        plasma.radius = radius;
        plasma.entityData.set(DATA_VARIANT, variant);
        Vec3 direction = target.subtract(from);
        plasma.setDeltaMovement(direction.lengthSqr() < 1e-6 ? new Vec3(0, TurretKind.PLASMA_SPEED, 0)
                : direction.normalize().scale(TurretKind.PLASMA_SPEED));
        return plasma;
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
        builder.define(DATA_VARIANT, PLASMA);
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
            // 拖尾：沿着这一刻走过的路撒一串
            Vec3 now = this.position();
            double length = before.distanceTo(now);
            if (length > 1e-3 && this.tickCount > 1) {
                DustParticleOptions dust = new DustParticleOptions(this.color(), this.size());
                int steps = Math.max(1, (int) (length / 0.5));
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

    /**
     * 近炸引信：这一刻飞过的那段路离哪个黑雾不到 {@link #FUSE} 格（从它的碰撞箱边上算），就在离它最近的那一点炸。
     * 打高空的枪骑全靠这个：等离子团飞几十格要一秒多，枪骑早挪开了，正好撞上的很少。
     */
    private @Nullable Vec3 fuse(ServerLevel level, Vec3 from, Vec3 to) {
        AABB swept = new AABB(from, to).inflate(FUSE + 2);
        Vec3 best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Mob mob : level.getEntitiesOfClass(Mob.class, swept, mob -> mob instanceof DarkFogEnemy && mob.isAlive())) {
            Vec3 center = mob.getBoundingBox().getCenter();
            Vec3 point = closest(from, to, center);
            double distance = point.distanceTo(center) - mob.getBbWidth() * 0.5;
            if (distance <= FUSE && distance < bestDistance) {
                bestDistance = distance;
                best = point;
            }
        }
        return best;
    }

    /** 线段 {@code a}–{@code b} 上离 {@code p} 最近的一点。 */
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
        int killed = Turrets.splash(level, center, this.damage, this.radius, 0, this);
        Turrets.creditKills(level, this.turret, killed);
        int variant = this.variant();
        level.sendParticles(new DustParticleOptions(this.color(), 1.6F), center.x, center.y, center.z, 12 + 8 * variant,
                this.radius * 0.35, this.radius * 0.35, this.radius * 0.35, 0);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, center.x, center.y, center.z, 10, this.radius * 0.3, this.radius * 0.3, this.radius * 0.3, 0.3);
        if (variant == ANTIMATTER) {
            level.sendParticles(ParticleTypes.EXPLOSION, center.x, center.y, center.z, 1, 0, 0, 0, 0);
            level.sendParticles(ParticleTypes.REVERSE_PORTAL, center.x, center.y, center.z, 40, this.radius * 0.4, this.radius * 0.4, this.radius * 0.4, 0.4);
        }
        level.playSound(null, center.x, center.y, center.z, (variant == ANTIMATTER ? UniverseSounds.EXPLOSION_SMALL : HostileSounds.PLASMA_BURST).get(),
                SoundSource.BLOCKS, variant == ANTIMATTER ? 1.0F : 0.8F, variant == SHORT_RANGE ? 1.15F : 1.0F);
        this.discard();
    }
}
