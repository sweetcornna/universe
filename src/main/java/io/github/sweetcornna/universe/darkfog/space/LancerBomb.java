package io.github.sweetcornna.universe.darkfog.space;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.core.darkfog.LancerRaid;
import io.github.sweetcornna.universe.core.darkfog.Tactics;
import io.github.sweetcornna.universe.darkfog.Facilities;
import io.github.sweetcornna.universe.darkfog.entity.DarkFogDamage;
import io.github.sweetcornna.universe.darkfog.shield.Shields;
import io.github.sweetcornna.universe.entity.PlasmaBolt;
import io.github.sweetcornna.universe.registry.UniverseEntities;
import io.github.sweetcornna.universe.registry.UniverseSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * 枪骑扔的炸弹（ADR-0028 §八）：差不多直着往下掉，落地炸开，半径 {@link LancerRaid#BOMB_RADIUS} 格里的设施都扣耐久，
 * 溅到的人也伤。路上碰到展开的护盾穹顶就在穹顶上炸，伤害交给护盾挡（{@link Shields#absorb}），挡剩下的才接着往下掉。
 */
public class LancerBomb extends ThrowableProjectile {
    public static final double GRAVITY = 0.06;
    public static final int COLOR = 0xFF6A2A;
    private static final int LIFETIME = 300;
    /** 扔出去时往下的速度。 */
    private static final double DROP_SPEED = 0.45;

    private float damage = LancerRaid.BOMB_DAMAGE;
    /** 已经碰过护盾了（穿过去的不再挡第二次）。 */
    private boolean shielded;

    public LancerBomb(EntityType<? extends LancerBomb> type, Level level) {
        super(type, level);
    }

    /** 从 {@code at} 往下扔一颗，已经加进世界。{@code owner} 是扔的枪骑（测试里可以没有）。 */
    public static LancerBomb drop(ServerLevel level, @Nullable Entity owner, Vec3 at, float damage) {
        LancerBomb bomb = new LancerBomb(UniverseEntities.LANCER_BOMB.get(), level);
        bomb.setOwner(owner);
        bomb.setPos(at);
        bomb.damage = damage;
        bomb.setDeltaMovement((level.getRandom().nextDouble() - 0.5) * 0.04, -DROP_SPEED, (level.getRandom().nextDouble() - 0.5) * 0.04);
        level.addFreshEntity(bomb);
        return bomb;
    }

    public float damage() {
        return this.damage;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
    }

    @Override
    protected double getDefaultGravity() {
        return GRAVITY;
    }

    @Override
    public void tick() {
        if (this.level() instanceof ServerLevel level && !this.shielded) {
            // 这一刻要走的那一段碰没碰到穹顶（重力和阻力按原版的顺序先算上）
            Vec3 from = this.position();
            Vec3 to = from.add(this.getDeltaMovement().add(0, -GRAVITY, 0).scale(this.getAirDrag()));
            Optional<Vec3> contact = Shields.intercept(level, from, to);
            if (contact.isPresent()) {
                this.shielded = true;
                Vec3 at = contact.get();
                float left = Shields.absorb(level, at, this.damage);
                level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y, at.z, 1, 0, 0, 0, 0);
                level.playSound(null, at.x, at.y, at.z, UniverseSounds.EXPLOSION_SMALL.get(), SoundSource.HOSTILE, 1.0F, 1.1F);
                if (left <= 0) {
                    this.discard();
                    return;
                }
                this.damage = left;
            }
        }
        super.tick();
        if (this.level().isClientSide()) {
            if (this.tickCount > 1) {
                this.level().addParticle(new DustParticleOptions(COLOR, 1.4F), this.getX(), this.getY() + 0.2, this.getZ(), 0, 0, 0);
                if (this.random.nextInt(2) == 0) {
                    this.level().addParticle(ParticleTypes.SMOKE, this.getX(), this.getY() + 0.4, this.getZ(), 0, 0.02, 0);
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
        if (this.level() instanceof ServerLevel level) {
            this.explode(level, hitResult.getLocation());
            this.discard();
        }
    }

    /** 炸开：半径里的设施按离爆心的距离扣耐久（两格高的按下半截算一次），溅到的人也伤。 */
    void explode(ServerLevel level, Vec3 at) {
        double radius = LancerRaid.BOMB_RADIUS;
        int r = Mth.ceil(radius);
        BlockPos center = BlockPos.containing(at);
        Set<BlockPos> hit = new HashSet<>();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    pos.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
                    BlockState state = level.getBlockState(pos);
                    if (!Facilities.isFacility(state)) {
                        continue;
                    }
                    BlockPos origin = Facilities.origin(state, pos).immutable();
                    if (!hit.add(origin)) {
                        continue;
                    }
                    double factor = LancerRaid.splash(Vec3.atCenterOf(origin).distanceTo(at) - 0.5);
                    if (factor > 0) {
                        Facilities.damage(level, origin, (float) (this.damage * Tactics.FACILITY_DAMAGE * factor), this.getOwner());
                    }
                }
            }
        }
        AABB box = new AABB(at, at).inflate(radius);
        for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class, box, DarkFogDamage::splashable)) {
            double factor = LancerRaid.splash(living.getBoundingBox().getCenter().distanceTo(at) - living.getBbWidth() * 0.5);
            if (factor > 0) {
                PlasmaBolt.clearHurtCooldown(living);
                living.hurtServer(level, DarkFogDamage.source(level, this, this.getOwner()), (float) (this.damage * LancerRaid.PLAYER_SPLASH * factor));
            }
        }
        level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y + 0.3, at.z, 2, 0.6, 0.3, 0.6, 0);
        level.sendParticles(new DustParticleOptions(COLOR, 1.8F), at.x, at.y + 0.3, at.z, 20, radius * 0.5, 0.3, radius * 0.5, 0);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y + 0.3, at.z, 16, 0.4, 0.2, 0.4, 0.4);
        level.playSound(null, at.x, at.y, at.z, UniverseSounds.EXPLOSION_LARGE.get(), SoundSource.HOSTILE, 1.0F, 1.0F);
    }
}
