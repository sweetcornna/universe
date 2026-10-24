package io.github.sweetcornna.universe.darkfog.turret;

import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.core.darkfog.AmmoKind;
import io.github.sweetcornna.universe.core.darkfog.Ballistics;
import io.github.sweetcornna.universe.darkfog.DarkFogEnemy;
import io.github.sweetcornna.universe.registry.UniverseEntities;
import io.github.sweetcornna.universe.registry.UniverseSounds;
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
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * 聚爆炮的炮弹（ADR-0028 §五）：没有空气阻力，按 {@link Ballistics} 倒推的初速走弧线，撞到方块或黑雾就炸，
 * 范围伤害只伤黑雾、不破坏方块。不存档。
 */
public class TurretShell extends ThrowableProjectile {
    private static final EntityDataAccessor<Integer> DATA_AMMO = SynchedEntityData.defineId(TurretShell.class, EntityDataSerializers.INT);
    /** 炮弹水平飞的速度（格/刻），越远飞得越久。 */
    private static final int LIFETIME = Ballistics.MAX_TICKS + 40;

    private @Nullable BlockPos turret;

    public TurretShell(EntityType<? extends TurretShell> type, Level level) {
        super(type, level);
    }

    /** 从 {@code from} 打向 {@code target}（还没加进世界）。 */
    public static TurretShell launch(ServerLevel level, BlockPos turret, Vec3 from, Vec3 target, AmmoKind ammo) {
        TurretShell shell = new TurretShell(UniverseEntities.TURRET_SHELL.get(), level);
        shell.setPos(from);
        shell.turret = turret.immutable();
        shell.entityData.set(DATA_AMMO, ammo.ordinal());
        Vec3 delta = target.subtract(from);
        int ticks = Ballistics.flightTicks(Math.hypot(delta.x, delta.z), ammo.speed());
        double[] v = Ballistics.launchVelocity(delta.x, delta.y, delta.z, ticks, Ballistics.GRAVITY);
        shell.setDeltaMovement(v[0], v[1], v[2]);
        return shell;
    }

    public AmmoKind ammo() {
        AmmoKind[] kinds = AmmoKind.values();
        return kinds[Math.floorMod(this.entityData.get(DATA_AMMO), kinds.length)];
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_AMMO, AmmoKind.SHELL.ordinal());
    }

    @Override
    protected double getDefaultGravity() {
        return Ballistics.GRAVITY;
    }

    @Override
    protected float getAirDrag() {
        return 1.0F;
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide()) {
            int color = switch (this.ammo()) {
                case HIGH_EXPLOSIVE_SHELL -> 0xFF8A3A;
                case CRYSTAL_SHELL -> 0xB27CFF;
                default -> 0xFFC66B;
            };
            this.level().addParticle(new DustParticleOptions(color, 0.9F), this.getX(), this.getY(), this.getZ(), 0, 0, 0);
            return;
        }
        if (this.tickCount > LIFETIME) {
            this.burst((ServerLevel) this.level());
        }
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
        AmmoKind ammo = this.ammo();
        Vec3 center = this.position();
        int killed = Turrets.splash(level, center, ammo, this);
        Turrets.creditKills(level, this.turret, killed);
        level.sendParticles(ParticleTypes.EXPLOSION, center.x, center.y, center.z, 2, 0.4, 0.3, 0.4, 0);
        if (ammo == AmmoKind.CRYSTAL_SHELL) {
            level.sendParticles(ParticleTypes.REVERSE_PORTAL, center.x, center.y, center.z, 40, ammo.radius() * 0.4, 0.5, ammo.radius() * 0.4, 0.3);
        } else {
            level.sendParticles(ParticleTypes.LAVA, center.x, center.y, center.z, 8, 0.6, 0.2, 0.6, 0);
        }
        level.sendParticles(ParticleTypes.LARGE_SMOKE, center.x, center.y, center.z, 10, ammo.radius() * 0.3, 0.3, ammo.radius() * 0.3, 0.02);
        level.playSound(null, center.x, center.y, center.z, UniverseSounds.EXPLOSION_SMALL.get(), SoundSource.BLOCKS, 1.0F, 0.95F);
        this.discard();
    }
}
