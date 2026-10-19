package io.github.sweetcornna.universe.asteroid;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.core.procgen.Rng;
import io.github.sweetcornna.universe.core.space.AsteroidField;
import io.github.sweetcornna.universe.core.space.AsteroidField.Material;
import io.github.sweetcornna.universe.core.space.AsteroidField.Shape;
import io.github.sweetcornna.universe.entity.PlasmaBolt;
import io.github.sweetcornna.universe.entity.ship.ShipBeamTarget;
import io.github.sweetcornna.universe.entity.ship.ShipCargo;
import io.github.sweetcornna.universe.entity.ship.ShipEntity;
import io.github.sweetcornna.universe.registry.UniverseEntities;
import io.github.sweetcornna.universe.registry.UniverseStats;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.entity.IEntityWithComplexSpawn;

/**
 * 小行星（ADR-0017）：太空里的一团岩石，按种子由 {@link AsteroidField#shape} 长出来，慢慢自转。采矿光束每刻打掉耐久，
 * 光子炮和等离子弹只算三分之一；碎了按矿点产出，开飞船打的收进货舱，走着打的进背包，装不下的漂在原地。
 * 飞船撞上来会被弹开，撞得快掉船体（飞船每刻沿着扫过的线段找小行星，见 {@link ShipEntity#bump}）。海盗的炮火打到小行星上被挡住，不算伤害。
 *
 * <p>由 {@link Asteroids} 在玩家附近按格生成，离所有玩家都远了就消失，不存档。客户端只拿到种子，自己长出同样的形状来画。
 */
public class Asteroid extends Entity implements ShipBeamTarget, IEntityWithComplexSpawn {
    /** 剩下的耐久（占总耐久的比例）。 */
    private static final EntityDataAccessor<Float> DATA_HEALTH = SynchedEntityData.defineId(Asteroid.class, EntityDataSerializers.FLOAT);
    /** 光子炮、等离子弹打小行星只算这么多：采矿还是得靠光束。 */
    private static final float WEAPON_FACTOR = 1 / 3F;
    /** 碰撞用的球比包住所有方块的球小一点：方块拼的一团不是实心球。 */
    private static final double HIT_RADIUS = 0.85;
    /** 离最近的玩家这么远就消失（格）。 */
    static final double DESPAWN_RADIUS = 224;

    private long seed;
    private @Nullable Shape shape;
    /** 在 {@link AsteroidField.Slot#key()} 里的身份（服务端）。 */
    private long slot;
    /** 客户端：画用的网格，第一次画的时候建。 */
    public @Nullable Object renderCache;
    /** 客户端：挨了一下以后抖几刻。 */
    public int shake;

    public Asteroid(EntityType<? extends Asteroid> type, Level level) {
        super(type, level);
    }

    /** 服务端：在 {@code center}（小行星的中心）造一颗。 */
    static Asteroid create(ServerLevel level, long seed, long slot, Vec3 center) {
        Asteroid asteroid = new Asteroid(UniverseEntities.ASTEROID.get(), level);
        asteroid.seed = seed;
        asteroid.slot = slot;
        asteroid.shape = AsteroidField.shape(seed);
        asteroid.setPos(center.x, center.y - asteroid.shape.radius(), center.z);
        asteroid.refreshDimensions();
        return asteroid;
    }

    public long seed() {
        return this.seed;
    }

    long slot() {
        return this.slot;
    }

    public Shape shape() {
        if (this.shape == null) {
            this.shape = AsteroidField.shape(this.seed);
        }
        return this.shape;
    }

    /** 小行星的中心（自转、碰撞绕它算）。 */
    public Vec3 center() {
        return this.position().add(0, this.shape().radius(), 0);
    }

    /** 碰撞用的球半径：方块拼成的一团比包围球瘦一些。 */
    public double hitRadius() {
        return this.shape().radius() * HIT_RADIUS;
    }

    /** 剩下的耐久占总耐久的比例。 */
    public float health() {
        return this.entityData.get(DATA_HEALTH);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_HEALTH, 1.0F);
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        if (this.shape == null) {
            return super.getDimensions(pose);
        }
        float size = (float) (this.shape.radius() * 2);
        return EntityDimensions.fixed(size, size);
    }

    @Override
    public boolean isPickable() {
        return !this.isRemoved();
    }

    @Override
    public boolean isAttackable() {
        return true;
    }

    @Override
    public boolean canBeCollidedWith(@Nullable Entity other) {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        // 小的也要在两百多格外看得见：飞进小行星带时远远就能看到一片
        return distance < 288 * 288;
    }

    // ---- 开采

    @Override
    public void beamed(ServerLevel level, ShipEntity ship, Player pilot, float damage) {
        this.mine(level, damage, pilot instanceof ServerPlayer player ? player : null);
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        // 只有玩家打的算：海盗的炮火被小行星挡住
        ServerPlayer miner = miner(source);
        if (this.isRemoved() || miner == null || damage <= 0) {
            return false;
        }
        float factor = source.getDirectEntity() instanceof PlasmaBolt ? WEAPON_FACTOR : 1;
        this.mine(level, damage * factor, miner);
        return true;
    }

    private static @Nullable ServerPlayer miner(DamageSource source) {
        Entity entity = source.getEntity();
        if (entity instanceof ServerPlayer player) {
            return player;
        }
        if (entity instanceof ShipEntity ship && ship.pilot() instanceof ServerPlayer pilot) {
            return pilot;
        }
        return null;
    }

    private void mine(ServerLevel level, float damage, @Nullable ServerPlayer miner) {
        if (this.isRemoved()) {
            return;
        }
        double durability = this.shape().durability();
        float health = (float) Math.max(0, this.health() - damage / durability);
        this.entityData.set(DATA_HEALTH, health);
        if (this.tickCount % 6 == 0) {
            Vec3 c = this.center();
            level.playSound(null, c.x, c.y, c.z, SoundEvents.STONE_HIT, SoundSource.BLOCKS, 0.8F, 0.6F + this.random.nextFloat() * 0.2F);
        }
        if (health <= 0) {
            this.shatter(level, miner);
        }
    }

    /** 碎了：产出给打碎它的玩家（飞船的货舱或者背包），装不下的漂在原地；一团碎石，记统计，冷却后才重新长出来。 */
    private void shatter(ServerLevel level, @Nullable ServerPlayer miner) {
        Shape shape = this.shape();
        Vec3 c = this.center();
        ShipEntity ship = miner != null && miner.getRootVehicle() instanceof ShipEntity s ? s : null;
        List<ItemStack> stacks = new ArrayList<>();
        for (AsteroidField.Yield yield : AsteroidField.yield(shape, Rng.of(this.random.nextLong()))) {
            stacks.add(new ItemStack(AsteroidMaterials.item(yield.material()), yield.count()));
        }
        MutableComponent list = Component.empty();
        int left = 0;
        for (int i = 0; i < stacks.size(); i++) {
            ItemStack stack = stacks.get(i);
            if (i > 0) {
                list.append(Component.translatable("message.universe.list_separator"));
            }
            list.append(Component.translatable("message.universe.item_count", stack.getCount(), stack.getHoverName()));
            if (ship != null) {
                ShipCargo.insert(ship, level.registryAccess(), stack);
            } else if (miner != null) {
                miner.getInventory().add(stack);
            }
            if (!stack.isEmpty()) {
                left += stack.getCount();
                ItemEntity item = new ItemEntity(level, c.x, c.y, c.z, stack);
                item.setNoGravity(true);
                item.setDeltaMovement((this.random.nextDouble() - 0.5) * 0.05, (this.random.nextDouble() - 0.5) * 0.05,
                        (this.random.nextDouble() - 0.5) * 0.05);
                level.addFreshEntity(item);
            }
        }
        if (miner != null) {
            miner.sendOverlayMessage(Component.translatable(ship != null ? "message.universe.asteroid.mined_stowed" : "message.universe.asteroid.mined",
                    list));
            if (left > 0 && ship != null) {
                miner.sendSystemMessage(Component.translatable("message.universe.asteroid.cargo_full", left).withStyle(ChatFormatting.YELLOW));
            }
            miner.awardStat(UniverseStats.ASTEROIDS_MINED.get());
        }
        // 碎石：岩石和每种矿各撒一些
        double r = shape.radius() * 0.5;
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, AsteroidMaterials.block(shape.voxels().get(0).material())), c.x, c.y, c.z,
                40, r, r, r, 0.2);
        for (Material material : shape.ores().keySet()) {
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, AsteroidMaterials.block(material)), c.x, c.y, c.z, 14, r, r, r, 0.2);
        }
        level.sendParticles(ParticleTypes.POOF, c.x, c.y, c.z, 12, r, r, r, 0.05);
        level.sendParticles(ParticleTypes.EXPLOSION, c.x, c.y, c.z, 1, 0, 0, 0, 0);
        level.playSound(null, c.x, c.y, c.z, SoundEvents.STONE_BREAK, SoundSource.BLOCKS, 1.5F, 0.6F);
        level.playSound(null, c.x, c.y, c.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 0.5F, 1.4F);
        Asteroids.onShattered(level, this);
        this.discard();
    }

    /** 客户端：光束照着的地方迸出碎石。 */
    public void chips(Vec3 at) {
        Shape shape = this.shape();
        Material material = shape.voxels().get(this.random.nextInt(shape.voxels().size())).material();
        BlockState state = AsteroidMaterials.block(material);
        this.level().addParticle(new BlockParticleOption(ParticleTypes.BLOCK, state), at.x, at.y, at.z, (this.random.nextDouble() - 0.5) * 0.3,
                (this.random.nextDouble() - 0.5) * 0.3, (this.random.nextDouble() - 0.5) * 0.3);
    }

    // ---- 每刻

    @Override
    public void tick() {
        super.tick();
        if (this.level() instanceof ServerLevel level) {
            if (this.tickCount % 20 == 0 && level.getNearestPlayer(this.getX(), this.getY(), this.getZ(), DESPAWN_RADIUS, false) == null) {
                this.discard();
                return;
            }
        } else if (this.shake > 0) {
            this.shake--;
        }
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) {
        super.onSyncedDataUpdated(accessor);
        if (DATA_HEALTH.equals(accessor) && this.level().isClientSide() && this.tickCount > 0) {
            this.shake = 4;
        }
    }

    // ---- 同步、不存档

    @Override
    public void writeSpawnData(RegistryFriendlyByteBuf buffer) {
        buffer.writeLong(this.seed);
    }

    @Override
    public void readSpawnData(RegistryFriendlyByteBuf buffer) {
        this.seed = buffer.readLong();
        this.shape = AsteroidField.shape(this.seed);
        this.refreshDimensions();
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }
}
