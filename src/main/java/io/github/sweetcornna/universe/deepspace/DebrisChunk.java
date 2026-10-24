package io.github.sweetcornna.universe.deepspace;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.core.deepspace.DebrisField;
import io.github.sweetcornna.universe.core.deepspace.DebrisField.Size;
import io.github.sweetcornna.universe.core.deepspace.Layout;
import io.github.sweetcornna.universe.core.procgen.Rng;
import io.github.sweetcornna.universe.entity.PlasmaBolt;
import io.github.sweetcornna.universe.entity.ship.ShipBeamTarget;
import io.github.sweetcornna.universe.entity.ship.ShipCargoContainer;
import io.github.sweetcornna.universe.entity.ship.ShipEntity;
import io.github.sweetcornna.universe.registry.UniverseBlocks;
import io.github.sweetcornna.universe.registry.UniverseEntities;
import io.github.sweetcornna.universe.registry.sound.DeepSpaceSounds;
import io.github.sweetcornna.universe.world.space.SpaceDimensions;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoveSimulationType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.entity.IEntityWithComplexSpawn;

/**
 * 碎片带里的一块残骸碎片（ADR-0034 §四）：几格到几十格残骸部件拼成的一团，慢慢翻滚。和小行星一样，采矿光束和多功能工具
 * 打得碎（光子炮、等离子弹只算三分之一），碎了按大小掷战利品表，开飞船打的收进货舱，走着打的进背包，装不下的漂在原地。
 * 小块的能用引力抓取器抓、被牵引光束吸进货舱。被推、被扔以后会飘，太空里没有阻力，慢慢减速。
 *
 * <p>由 {@link DebrisFields} 在玩家附近生成，离所有玩家都远了就消失，不存档；打碎的记在 {@link DeepSpaceData} 里，不再长出来。
 * 客户端只拿到种子和大小，自己搭出同样的形状来画。
 */
public class DebrisChunk extends Entity implements ShipBeamTarget, IEntityWithComplexSpawn {
    private static final EntityDataAccessor<Float> DATA_HEALTH = SynchedEntityData.defineId(DebrisChunk.class, EntityDataSerializers.FLOAT);
    public static final ResourceKey<LootTable> MEDIUM_LOOT = ResourceKey.create(Registries.LOOT_TABLE, Universe.id("entities/debris_chunk_medium"));
    public static final ResourceKey<LootTable> LARGE_LOOT = ResourceKey.create(Registries.LOOT_TABLE, Universe.id("entities/debris_chunk_large"));
    /** 光子炮、等离子弹打碎片只算这么多。 */
    private static final float WEAPON_FACTOR = 1 / 3F;
    /** 离最近的玩家这么远就消失（格）。 */
    static final double DESPAWN_RADIUS = 256;
    /** 太空里每刻剩下的速度：没有空气，只是让推出去的东西别一路飘出星系。 */
    private static final double SPACE_DRAG = 0.995;

    private long seed;
    private Size size = Size.SMALL;
    private @Nullable Layout shape;
    /** 属于哪片碎片带（{@link DeepSpaceData} 的键）和第几块；熔毁炸出来的不属于碎片带。 */
    private @Nullable String field;
    private int slot = -1;
    /** 客户端：画用的网格。 */
    public @Nullable Object renderCache;
    /** 客户端：挨了一下以后抖几刻。 */
    public int shake;

    public DebrisChunk(EntityType<? extends DebrisChunk> type, Level level) {
        super(type, level);
        this.setNoGravity(true);
    }

    /** 服务端：在 {@code center}（碎片的中心）造一块。 */
    public static DebrisChunk create(ServerLevel level, long seed, Size size, Vec3 center, @Nullable String field, int slot) {
        DebrisChunk chunk = new DebrisChunk(UniverseEntities.DEBRIS_CHUNK.get(), level);
        chunk.seed = seed;
        chunk.size = size;
        chunk.field = field;
        chunk.slot = slot;
        chunk.shape = DebrisField.shape(seed, size);
        chunk.refreshDimensions();
        chunk.setPos(center.x, center.y - chunk.radius(), center.z);
        return chunk;
    }

    public long seed() {
        return this.seed;
    }

    public Size size() {
        return this.size;
    }

    @Nullable String field() {
        return this.field;
    }

    int slot() {
        return this.slot;
    }

    public Layout shape() {
        if (this.shape == null) {
            this.shape = DebrisField.shape(this.seed, this.size);
        }
        return this.shape;
    }

    /** 外接球半径（格）。 */
    public double radius() {
        return DebrisField.radius(this.shape(), this.size);
    }

    public Vec3 center() {
        return this.position().add(0, this.radius(), 0);
    }

    /** 飞船撞上来时按多大的球算：拼起来的一团没有包围球那么实。 */
    public double hitRadius() {
        return this.radius() * 0.8;
    }

    /** 小块的能抓、能被牵引光束吸走。 */
    public boolean small() {
        return this.size == Size.SMALL;
    }

    public float health() {
        return this.entityData.get(DATA_HEALTH);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_HEALTH, 1.0F);
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        float size = (float) (this.radius() * 2);
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
        return distance < 256 * 256;
    }

    // ---- 拆

    @Override
    public void beamed(ServerLevel level, ShipEntity ship, Player pilot, float damage) {
        this.mine(level, damage, pilot instanceof ServerPlayer player ? player : null);
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        ServerPlayer miner = miner(source);
        if (this.isRemoved() || miner == null || damage <= 0) {
            return false;
        }
        this.mine(level, damage * (source.getDirectEntity() instanceof PlasmaBolt ? WEAPON_FACTOR : 1), miner);
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
        float health = (float) Math.max(0, this.health() - damage / this.size.durability());
        this.entityData.set(DATA_HEALTH, health);
        if (this.tickCount % 6 == 0) {
            Vec3 c = this.center();
            level.playSound(null, c.x, c.y, c.z, SoundEvents.NETHERITE_BLOCK_HIT, SoundSource.BLOCKS, 0.8F, 0.7F + this.random.nextFloat() * 0.2F);
        }
        if (health <= 0) {
            ShipEntity ship = miner != null && miner.getRootVehicle() instanceof ShipEntity s ? s : null;
            this.shatter(level, miner, ship);
        }
    }

    /** 碎片打碎了掉的东西：按大小掷一次战利品表。 */
    List<ItemStack> roll(ServerLevel level, @Nullable Entity looter) {
        ResourceKey<LootTable> key = switch (this.size) {
            case SMALL -> this.getType().getDefaultLootTable().orElse(MEDIUM_LOOT);
            case MEDIUM -> MEDIUM_LOOT;
            case LARGE -> LARGE_LOOT;
        };
        List<ItemStack> items = new ArrayList<>();
        LootTable table = level.getServer().reloadableRegistries().getLootTable(key);
        LootParams params = new LootParams.Builder(level)
                .withParameter(LootContextParams.ORIGIN, this.center())
                .withOptionalParameter(LootContextParams.THIS_ENTITY, looter)
                .create(LootContextParamSets.CHEST);
        table.getRandomItems(params, items::add);
        return items;
    }

    /**
     * 碎了：产出给打碎它的玩家（飞船的货舱或者背包），装不下的漂在原地；一团碎片，记下这一块没了。
     *
     * @param into 收进这艘船的货舱（开船打的、牵引光束吸进来的）；为空时进 {@code miner} 的背包
     */
    void shatter(ServerLevel level, @Nullable ServerPlayer miner, @Nullable ShipEntity into) {
        if (this.isRemoved()) {
            return;
        }
        Vec3 c = this.center();
        List<ItemStack> stacks = this.roll(level, miner);
        MutableComponent list = Component.empty();
        int left = 0;
        ShipCargoContainer cargo = into == null ? null : ShipCargoContainer.forShip(into, level.registryAccess());
        for (int i = 0; i < stacks.size(); i++) {
            ItemStack stack = stacks.get(i);
            if (i > 0) {
                list.append(Component.translatable("message.universe.list_separator"));
            }
            list.append(Component.translatable("message.universe.item_count", stack.getCount(), stack.getHoverName()));
            if (cargo != null) {
                cargo.insert(stack);
            } else if (miner != null) {
                miner.getInventory().add(stack);
            }
            if (!stack.isEmpty()) {
                left += stack.getCount();
                ItemEntity item = new ItemEntity(level, c.x, c.y, c.z, stack);
                item.setNoGravity(SpaceDimensions.isSpace(level.dimension()));
                item.setDeltaMovement((this.random.nextDouble() - 0.5) * 0.06, (this.random.nextDouble() - 0.5) * 0.06,
                        (this.random.nextDouble() - 0.5) * 0.06);
                level.addFreshEntity(item);
            }
        }
        if (cargo != null) {
            cargo.setChanged();
        }
        if (miner != null && !stacks.isEmpty()) {
            miner.sendOverlayMessage(Component.translatable(into != null ? "message.universe.debris.salvaged_stowed" : "message.universe.debris.salvaged",
                    list));
            if (left > 0 && into != null) {
                miner.sendSystemMessage(Component.translatable("message.universe.asteroid.cargo_full", left).withStyle(ChatFormatting.YELLOW));
            }
        }
        double r = this.radius() * 0.6;
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, UniverseBlocks.DERELICT_PLATING.get().defaultBlockState()),
                c.x, c.y, c.z, (int) (20 + 30 * r), r, r, r, 0.2);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, c.x, c.y, c.z, 12, r, r, r, 0.3);
        if (this.size != Size.SMALL) {
            level.sendParticles(ParticleTypes.EXPLOSION, c.x, c.y, c.z, 1, 0, 0, 0, 0);
        }
        level.playSound(null, c.x, c.y, c.z, DeepSpaceSounds.DEBRIS_BREAK.get(), SoundSource.BLOCKS, this.size == Size.SMALL ? 0.7F : 1.2F,
                this.size == Size.LARGE ? 0.8F : 1.0F);
        DebrisFields.onGone(level, this);
        this.discard();
    }

    /**
     * 牵引光束吸到船边：产出收进货舱（调用的人最后 {@code setChanged}），不放碎裂的特效。装不下的漂在船边。
     *
     * @return 收进去了几件；一件都装不下时为 −1，这块碎片留着
     */
    int stow(ServerLevel level, ShipEntity ship, ShipCargoContainer cargo) {
        if (this.isRemoved()) {
            return 0;
        }
        List<ItemStack> stacks = this.roll(level, ship.pilot());
        int moved = 0;
        int total = 0;
        for (ItemStack stack : stacks) {
            total += stack.getCount();
            moved += cargo.insert(stack);
        }
        if (moved == 0 && total > 0) {
            return -1;
        }
        for (ItemStack stack : stacks) {
            if (!stack.isEmpty()) {
                ItemEntity item = new ItemEntity(level, this.getX(), this.getY(), this.getZ(), stack);
                item.setNoGravity(true);
                level.addFreshEntity(item);
            }
        }
        DebrisFields.onGone(level, this);
        this.discard();
        return moved;
    }

    /** 客户端：光束照着的地方迸出碎屑。 */
    public void chips(Vec3 at) {
        this.level().addParticle(new BlockParticleOption(ParticleTypes.BLOCK, UniverseBlocks.DERELICT_PLATING.get().defaultBlockState()),
                at.x, at.y, at.z, (this.random.nextDouble() - 0.5) * 0.3, (this.random.nextDouble() - 0.5) * 0.3, (this.random.nextDouble() - 0.5) * 0.3);
    }

    // ---- 每刻

    @Override
    public void tick() {
        super.tick();
        Vec3 v = this.getDeltaMovement();
        if (v.lengthSqr() > 1e-6) {
            if (!SpaceDimensions.isSpace(this.level().dimension())) {
                // 星球上：照样往下掉，落地蹭住
                v = v.add(0, -0.04, 0).scale(0.98);
            } else {
                v = v.scale(SPACE_DRAG);
            }
            this.move(MoverType.SELF, v);
            if (this.onGround()) {
                v = v.multiply(0.6, 0, 0.6);
            }
            this.setDeltaMovement(v.lengthSqr() < 1e-5 ? Vec3.ZERO : v);
        } else if (!SpaceDimensions.isSpace(this.level().dimension()) && !this.onGround()) {
            this.setDeltaMovement(0, -0.04, 0);
        }
        if (this.level() instanceof ServerLevel level) {
            if (this.tickCount % 20 == 0 && level.getNearestPlayer(this.getX(), this.getY(), this.getZ(), DESPAWN_RADIUS, false) == null) {
                this.discard();
            }
        } else if (this.shake > 0) {
            this.shake--;
        }
    }

    /**
     * 两边都算漂移，同原版的掉落物：客户端在两次同步之间自己往前推，飞散的碎片才不一顿一顿。
     * 不这么设的话 26.x 不许客户端调 {@code move}，开发环境里熔毁炸出来的碎片直接让客户端崩掉。
     */
    @Override
    public MoveSimulationType getMoveSimulationType() {
        return MoveSimulationType.SERVER_AND_CLIENT;
    }

    /** 翻滚的转轴和每刻转多少度：按种子定，客户端画的时候用。 */
    public double[] spin() {
        Rng rng = Rng.of(this.seed ^ 0x5DEECE66DL);
        double x = rng.range(-1.0, 1.0);
        double y = rng.range(-1.0, 1.0);
        double z = rng.range(-1.0, 1.0);
        double length = Math.max(1e-3, Math.sqrt(x * x + y * y + z * z));
        double rate = rng.range(0.15, 0.6) * (this.size == Size.LARGE ? 0.5 : 1);
        return new double[] {x / length, y / length, z / length, rate};
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
        buffer.writeByte(this.size.ordinal());
    }

    @Override
    public void readSpawnData(RegistryFriendlyByteBuf buffer) {
        this.seed = buffer.readLong();
        this.size = Size.values()[Math.clamp(buffer.readByte(), 0, Size.values().length - 1)];
        this.shape = DebrisField.shape(this.seed, this.size);
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
