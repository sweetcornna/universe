package io.github.sweetcornna.universe.fossil;

import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.core.fossil.StoneSentryRules;
import io.github.sweetcornna.universe.registry.UniverseBlocks;
import io.github.sweetcornna.universe.registry.UniverseEntities;
import io.github.sweetcornna.universe.registry.sound.FossilSounds;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * 石哨兵扔的石块（ADR-0034 §三）：吊着飞，砸中人掉 {@value StoneSentryRules#ROCK_DAMAGE} 点血（按难度缩放），落地碎成一团石屑。
 * 石哨兵之间不互相砸。不存档。
 */
public class SentryRock extends ThrowableProjectile {
    public static final double GRAVITY = 0.04;
    private static final byte EVENT_SHATTER = EntityEvent.DEATH;
    private static final int LIFETIME = 80;

    public SentryRock(EntityType<? extends SentryRock> type, Level level) {
        super(type, level);
    }

    /** 从 {@code from} 按初速度 {@code velocity} 扔出去，已经加进世界。 */
    public static @Nullable SentryRock launch(ServerLevel level, Entity owner, Vec3 from, Vec3 velocity) {
        SentryRock rock = new SentryRock(UniverseEntities.SENTRY_ROCK.get(), level);
        rock.setOwner(owner);
        rock.setPos(from);
        rock.setDeltaMovement(velocity);
        return level.addFreshEntity(rock) ? rock : null;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
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
        if (!this.level().isClientSide() && this.tickCount > LIFETIME) {
            this.discard();
        }
    }

    @Override
    protected boolean canHitEntity(Entity entity) {
        return super.canHitEntity(entity) && !(entity instanceof StoneSentry);
    }

    @Override
    protected void onHitEntity(EntityHitResult hitResult) {
        super.onHitEntity(hitResult);
        if (this.level() instanceof ServerLevel level && hitResult.getEntity() instanceof LivingEntity living) {
            LivingEntity owner = this.getOwner() instanceof LivingEntity mob ? mob : null;
            living.hurtServer(level, this.damageSources().mobProjectile(this, owner), StoneSentryRules.ROCK_DAMAGE);
        }
    }

    @Override
    protected void onHit(HitResult hitResult) {
        super.onHit(hitResult);
        if (!(this.level() instanceof ServerLevel level) || this.isRemoved()) {
            return;
        }
        Vec3 at = hitResult.getLocation();
        level.playSound(null, at.x, at.y, at.z, FossilSounds.ROCK_IMPACT.get(), SoundSource.HOSTILE, 1.0F, 0.9F + this.random.nextFloat() * 0.2F);
        level.broadcastEntityEvent(this, EVENT_SHATTER);
        this.discard();
    }

    @Override
    public void handleEntityEvent(@EntityEvent.Value byte id) {
        if (id != EVENT_SHATTER) {
            super.handleEntityEvent(id);
            return;
        }
        BlockParticleOption dust = new BlockParticleOption(ParticleTypes.BLOCK, UniverseBlocks.PLANET_COBBLESTONE.get().defaultBlockState());
        for (int i = 0; i < 14; i++) {
            this.level().addParticle(dust, this.getX(), this.getY(), this.getZ(),
                    (this.random.nextDouble() - 0.5) * 0.3, this.random.nextDouble() * 0.25, (this.random.nextDouble() - 0.5) * 0.3);
        }
    }
}
