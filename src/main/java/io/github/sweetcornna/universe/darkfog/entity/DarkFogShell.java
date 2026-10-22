package io.github.sweetcornna.universe.darkfog.entity;

import io.github.sweetcornna.universe.core.darkfog.Tactics;
import io.github.sweetcornna.universe.darkfog.Facilities;
import io.github.sweetcornna.universe.entity.PlasmaBolt;
import io.github.sweetcornna.universe.registry.UniverseEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * 等离子哨塔吊射的炮弹（ADR-0028 §二）：有重力，落地炸开，溅射附近地上的玩家；脚离地面一格以上（跳起来）就躲过去了。
 * 落在设施上扣设施的耐久。
 */
public class DarkFogShell extends ThrowableProjectile {
    public static final double GRAVITY = 0.05;
    public static final double RADIUS = 2.5;
    private static final byte EVENT_BURST = EntityEvent.DEATH;
    private static final int LIFETIME = 120;
    static final int COLOR = 0xFF5A2A;

    private float damage = 6;

    public DarkFogShell(EntityType<? extends DarkFogShell> type, Level level) {
        super(type, level);
    }

    /** 从 {@code from} 吊射到 {@code to}，飞 {@code ticks} 刻，已经加进世界。 */
    public static DarkFogShell lob(ServerLevel level, Entity owner, Vec3 from, Vec3 to, int ticks, float damage) {
        DarkFogShell shell = new DarkFogShell(UniverseEntities.DARK_FOG_SHELL.get(), level);
        shell.setOwner(owner);
        shell.setPos(from);
        shell.damage = damage;
        double[] v = Tactics.ballistic(to.x - from.x, to.y - from.y, to.z - from.z, GRAVITY, ticks);
        shell.setDeltaMovement(v[0], v[1], v[2]);
        level.addFreshEntity(shell);
        return shell;
    }

    @Override
    protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
    }

    @Override
    protected double getDefaultGravity() {
        return GRAVITY;
    }

    @Override
    protected float getAirDrag() {
        return 1;
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide()) {
            if (this.tickCount > 1) {
                this.level().addParticle(new DustParticleOptions(COLOR, 1.2F), this.getX(), this.getY(), this.getZ(), 0, 0, 0);
                if (this.random.nextInt(3) == 0) {
                    this.level().addParticle(ParticleTypes.SMOKE, this.getX(), this.getY(), this.getZ(), 0, 0.01, 0);
                }
            }
            return;
        }
        if (this.tickCount > LIFETIME) {
            this.discard();
        }
    }

    @Override
    protected boolean canHitEntity(Entity entity) {
        return super.canHitEntity(entity) && !DarkFogDamage.spared(entity);
    }

    @Override
    protected void onHit(HitResult hitResult) {
        super.onHit(hitResult);
        if (!(this.level() instanceof ServerLevel level)) {
            return;
        }
        Vec3 at = hitResult.getLocation();
        if (hitResult instanceof BlockHitResult block && hitResult.getType() == HitResult.Type.BLOCK) {
            BlockPos pos = block.getBlockPos();
            if (Facilities.isFacility(level.getBlockState(pos))) {
                Facilities.damage(level, pos, (float) (this.damage * Tactics.FACILITY_DAMAGE), this.getOwner());
            }
        }
        burst(level, at);
        this.level().broadcastEntityEvent(this, EVENT_BURST);
        this.discard();
    }

    private void burst(ServerLevel level, Vec3 at) {
        AABB box = new AABB(at, at).inflate(RADIUS, 2, RADIUS);
        for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class, box, DarkFogDamage::splashable)) {
            double horizontal = Math.hypot(living.getX() - at.x, living.getZ() - at.z);
            if (Tactics.splash(horizontal, living.getY() - at.y, RADIUS)) {
                PlasmaBolt.clearHurtCooldown(living);
                living.hurtServer(level, DarkFogDamage.source(level, this, this.getOwner()),
                        (float) (this.damage * Tactics.splashFactor(horizontal, RADIUS)));
            }
        }
        level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 0.6F, 1.6F);
    }

    @Override
    public void handleEntityEvent(@EntityEvent.Value byte id) {
        if (id != EVENT_BURST) {
            super.handleEntityEvent(id);
            return;
        }
        this.level().addParticle(ParticleTypes.EXPLOSION, this.getX(), this.getY(), this.getZ(), 0, 0, 0);
        DustParticleOptions dust = new DustParticleOptions(COLOR, 1.5F);
        for (int i = 0; i < 16; i++) {
            double a = i / 16.0 * Math.PI * 2;
            this.level().addParticle(dust, this.getX() + Math.cos(a) * RADIUS * 0.8, this.getY() + 0.2, this.getZ() + Math.sin(a) * RADIUS * 0.8,
                    0, 0.02, 0);
            this.level().addParticle(ParticleTypes.ELECTRIC_SPARK, this.getX(), this.getY() + 0.2, this.getZ(),
                    Math.cos(a) * 0.3, 0.1, Math.sin(a) * 0.3);
        }
    }
}
