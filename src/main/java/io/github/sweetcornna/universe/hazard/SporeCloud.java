package io.github.sweetcornna.universe.hazard;

import io.github.sweetcornna.universe.core.hazard.SporeDrift;
import io.github.sweetcornna.universe.core.procgen.Rng;
import io.github.sweetcornna.universe.registry.UniverseEntities;
import io.github.sweetcornna.universe.registry.sound.HazardSounds;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * 孢子云（ADR-0034 §六）：毒雾星球上贴着地面飘过来的一团孢子，半径 {@value SporeDrift#RADIUS} 格，顺着风走，
 * 飘 60–90 秒散掉。云里毒性防护掉得快 {@value SporeDrift#TOXIC_FACTOR} 倍，画面发绿。
 *
 * <p>位置照常同步；生下来的时刻、能飘多久、样子的种子用实体数据同步，客户端自己算浓淡，画雾、撒孢子粒子。不存档。
 */
public class SporeCloud extends Entity {
    private static final EntityDataAccessor<Long> DATA_BORN = SynchedEntityData.defineId(SporeCloud.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Integer> DATA_LIFE = SynchedEntityData.defineId(SporeCloud.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_SEED = SynchedEntityData.defineId(SporeCloud.class, EntityDataSerializers.INT);
    /** 云底离地多高。 */
    private static final double HOVER = 1.2;
    /** 云里孢子的颜色：黄绿。 */
    public static final int COLOR = 0x8FB44A;

    private long planetSeed;
    private double wind = 0.5;

    public SporeCloud(EntityType<? extends SporeCloud> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    /** 在 {@code at}（地面）放一团云，已经加进世界。 */
    public static SporeCloud spawn(ServerLevel level, Vec3 at, long planetSeed, double wind, Rng rng) {
        SporeCloud cloud = new SporeCloud(UniverseEntities.SPORE_CLOUD.get(), level);
        cloud.setPos(at.x, at.y + HOVER, at.z);
        cloud.planetSeed = planetSeed;
        cloud.wind = wind;
        cloud.entityData.set(DATA_BORN, level.getGameTime());
        cloud.entityData.set(DATA_LIFE, SporeDrift.lifetime(rng));
        cloud.entityData.set(DATA_SEED, rng.nextInt(Integer.MAX_VALUE));
        return level.addFreshEntity(cloud) ? cloud : null;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_BORN, 0L);
        builder.define(DATA_LIFE, SporeDrift.LIFE_MIN);
        builder.define(DATA_SEED, 0);
    }

    public int age() {
        return (int) (this.level().getGameTime() - this.entityData.get(DATA_BORN));
    }

    public int life() {
        return this.entityData.get(DATA_LIFE);
    }

    /** 样子的种子（雾团怎么摆）。 */
    public int seed() {
        return this.entityData.get(DATA_SEED);
    }

    /** 现在有多浓：0–1。 */
    public double density() {
        return SporeDrift.density(this.age(), this.life());
    }

    /** {@code at} 在不在云里（浓到算数的时候）。 */
    public boolean contains(Vec3 at) {
        return SporeDrift.thick(this.density()) && SporeDrift.inside(at.x - this.getX(), at.y - this.getY(), at.z - this.getZ());
    }

    /** 快点散掉（飘进了基地、调试命令）。 */
    public void dissipate() {
        int age = this.age();
        if (this.life() - age > 100) {
            this.entityData.set(DATA_LIFE, age + 100);
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide()) {
            this.spores();
            return;
        }
        ServerLevel level = (ServerLevel) this.level();
        int age = this.age();
        if (age >= this.life() || age < 0) {
            this.discard();
            return;
        }
        double[] v = SporeDrift.velocity(this.planetSeed, this.wind, level.getGameTime(), this.seed());
        double x = this.getX() + v[0];
        double z = this.getZ() + v[1];
        // 贴着地面飘：慢慢跟上地形的高低
        int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(x), (int) Math.floor(z));
        double y = this.getY() + (ground + HOVER - this.getY()) * 0.05;
        this.setPos(x, y, z);
        if (age % 40 == 0 && (Hazards.exempt(level, this.blockPosition()) || !Hazards.enabled(level))) {
            this.dissipate();
        }
        if (age % 80 == 0) {
            level.playSound(null, this.getX(), this.getY(), this.getZ(), HazardSounds.SPORE_CLOUD.get(), SoundSource.AMBIENT,
                    (float) (0.9 * this.density()) + 0.1F, 0.9F + this.random.nextFloat() * 0.2F);
        }
        if (SporeDrift.thick(this.density())) {
            long time = level.getGameTime();
            for (ServerPlayer player : level.players()) {
                if (!player.isSpectator() && this.contains(player.position().add(0, player.getBbHeight() / 2, 0))) {
                    Hazards.spores(player, time);
                }
            }
        }
    }

    /** 客户端：云里慢慢飘着的孢子，一点点黄绿色的尘。雾团本身由渲染器画。 */
    private void spores() {
        double density = this.density();
        if (density <= 0.05) {
            return;
        }
        int count = (int) Math.ceil(3 * density);
        for (int i = 0; i < count; i++) {
            double angle = this.random.nextDouble() * Math.PI * 2;
            double r = Math.sqrt(this.random.nextDouble()) * SporeDrift.RADIUS;
            double x = this.getX() + Math.cos(angle) * r;
            double z = this.getZ() + Math.sin(angle) * r;
            double y = this.getY() - 0.8 + this.random.nextDouble() * 3.2;
            if (this.random.nextInt(3) == 0) {
                this.level().addParticle(ParticleTypes.SPORE_BLOSSOM_AIR, x, y, z, 0, 0, 0);
            } else {
                this.level().addParticle(new DustParticleOptions(COLOR, 0.6F + this.random.nextFloat() * 0.6F), x, y, z, 0, 0.01, 0);
            }
        }
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        return false;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 128 * 128;
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
    }
}
