package io.github.sweetcornna.universe.entity;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.advancement.MilestoneTrigger;
import io.github.sweetcornna.universe.core.discovery.DiscoveryLog;
import io.github.sweetcornna.universe.core.ecology.BodyPlan;
import io.github.sweetcornna.universe.core.ecology.Companion;
import io.github.sweetcornna.universe.core.ecology.CreatureAnatomy;
import io.github.sweetcornna.universe.core.ecology.CreatureSpecies;
import io.github.sweetcornna.universe.core.ecology.CreatureTraits;
import io.github.sweetcornna.universe.core.ecology.Fauna;
import io.github.sweetcornna.universe.core.ecology.Husbandry;
import io.github.sweetcornna.universe.core.ecology.Temperament;
import io.github.sweetcornna.universe.core.galaxy.BodyId;
import io.github.sweetcornna.universe.core.terrain.BiomeKind;
import io.github.sweetcornna.universe.registry.UniverseEntities;
import io.github.sweetcornna.universe.registry.UniverseItems;
import io.github.sweetcornna.universe.world.planet.Planets;
import io.github.sweetcornna.universe.world.planet.gen.PlanetBiomes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.PlayerRideableJumping;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.CommonHooks;

/**
 * 星球上的程序化动物（玩法设计 §8）。外观、属性、性情都由 {@link CreatureSpecies} 决定，
 * 实体本身只同步、保存物种在星球动物表里的序号，两端各自从星球种子算出物种。
 *
 * <p>喂养（ADR-0017）：按食性喂对东西，冒爱心，30 秒后产出一份产物，5 分钟后才肯再吃；每喂一次有机会驯服。
 * 驯服后记下主人和出生的星球（物种从此按出生的星球算，带到别处也不变），不会消失，跟着主人走、帮主人打架；
 * 够大的走兽空手右键骑上去（跳跃键蓄力跳）。
 *
 * <p>个体差异和伙伴（ADR-0026 §九）：每只有自己的性状（{@link CreatureTraits}：体型系数、颜色、性情），出生时按 UUID 定；
 * 驯服的是伙伴（{@link Companion}：信任、饱食、心情），空手潜行右键打开伙伴界面，每天能挖一次，吃饱了每两天下一个卵。
 * 卵孵出的幼崽是成年的一半大，两个游戏日长成。
 */
public class AlienCreature extends PathfinderMob implements OwnableEntity, PlayerRideableJumping {
    private static final EntityDataAccessor<Integer> DATA_SPECIES = SynchedEntityData.defineId(AlienCreature.class,
            EntityDataSerializers.INT);
    private static final EntityDataAccessor<Optional<EntityReference<LivingEntity>>> DATA_OWNER = SynchedEntityData.defineId(
            AlienCreature.class, EntityDataSerializers.OPTIONAL_LIVING_ENTITY_REFERENCE);
    /** 出生的星球（天体路径）：空的是没驯服、按所在的星球算；{@link #FALLBACK_HOME} 是不在星球上驯服的、按后备动物表算。 */
    private static final EntityDataAccessor<String> DATA_HOME = SynchedEntityData.defineId(AlienCreature.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Boolean> DATA_STAYING = SynchedEntityData.defineId(AlienCreature.class,
            EntityDataSerializers.BOOLEAN);
    /** 个体的性状：体型系数、颜色；性情是 -1 时还没定（老存档的第一刻之前），按物种的。 */
    private static final EntityDataAccessor<Float> DATA_TRAIT_SIZE = SynchedEntityData.defineId(AlienCreature.class,
            EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DATA_TINT = SynchedEntityData.defineId(AlienCreature.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Byte> DATA_TEMPERAMENT = SynchedEntityData.defineId(AlienCreature.class,
            EntityDataSerializers.BYTE);
    private static final String FALLBACK_HOME = "-";
    /** 伙伴挖出东西的战利品表（ADR-0026 §九）。 */
    public static final ResourceKey<LootTable> DIG_LOOT = ResourceKey.create(Registries.LOOT_TABLE, Universe.id("gameplay/companion_dig"));
    /** 挖掘时原地刨多少刻。 */
    public static final int DIG_TICKS = 60;
    /** 牧群受惊后多少 tick 内会一起逃。 */
    private static final int ALARM_TICKS = 100;
    /** 物种对应的碰撞箱（格，未乘大小），两端共用。 */
    private static final Map<CreatureSpecies, EntityDimensions> DIMENSIONS = new LinkedHashMap<>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<CreatureSpecies, EntityDimensions> eldest) {
            return this.size() > 256;
        }
    };
    /** 客户端当前星球的动物，由客户端入口设置，免得通用代码引用客户端类。 */
    private static volatile Supplier<Fauna> clientFauna = () -> Fauna.FALLBACK;
    /** 客户端按天体路径查动物（驯服的动物按出生的星球算）。 */
    private static volatile Function<String, Fauna> clientHomeFauna = path -> Fauna.FALLBACK;

    private @Nullable Fauna cachedFauna;
    private int cachedIndex;
    private @Nullable CreatureSpecies cachedSpecies;
    private boolean speciesAssigned;
    private int alarm;
    private @Nullable LivingEntity threat;
    /** 还要多少刻才再产出（驯服的这段时间里吃饱了）。 */
    private int feedCooldown;
    /** 野生的还要多少刻才吃下一口。 */
    private int tameCooldown;
    /** 还要多少刻产出（0 是没在等）。 */
    private int produceTimer;
    /** 骑着的人蓄好力、等落地时起跳的力度（0 到 1）。 */
    private float jumpPending;
    private @Nullable CreatureSpecies saddleSpecies;
    private float saddleHeight;
    /** 性状定下来了（生成时、读档时、卵孵出时）；没定的在第一刻按 UUID 补上。 */
    private boolean traitsSet;
    private @Nullable CreatureTraits cachedTraits;
    /** 体型要重算（读档、长大）。 */
    private boolean bodyDirty;
    /** 伙伴的状态，只在服务端、只有驯服的才有。 */
    private @Nullable Companion companion;
    /** 幼崽出生了多少刻，-1 是成年的。 */
    private int youngAge = -1;
    /** 还要刨多少刻。 */
    private int digTicks;

    public AlienCreature(EntityType<? extends AlienCreature> type, Level level) {
        super(type, level);
    }

    public static void setClientFauna(Supplier<Fauna> current, Function<String, Fauna> byHome) {
        clientFauna = current;
        clientHomeFauna = byHome;
    }

    /** 客户端当前星球的动物（水生动物也用这一份）。 */
    static Fauna clientFauna() {
        return clientFauna.get();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 10)
                .add(Attributes.MOVEMENT_SPEED, 0.25)
                .add(Attributes.ATTACK_DAMAGE, 2)
                .add(Attributes.FOLLOW_RANGE, 24);
    }

    // ---------------------------------------------------------------- 物种

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_SPECIES, 0);
        builder.define(DATA_OWNER, Optional.empty());
        builder.define(DATA_HOME, "");
        builder.define(DATA_STAYING, false);
        builder.define(DATA_TRAIT_SIZE, 1F);
        builder.define(DATA_TINT, CreatureTraits.NO_TINT);
        builder.define(DATA_TEMPERAMENT, (byte) -1);
    }

    /** 这只动物的物种表：驯服的按出生的星球；没驯服的按所在的世界，服务端按星球维度算，客户端用当前星球的。 */
    public Fauna fauna() {
        String home = this.entityData.get(DATA_HOME);
        if (this.level() instanceof ServerLevel server) {
            return faunaFor(server, home);
        }
        if (!home.isEmpty()) {
            return home.equals(FALLBACK_HOME) ? Fauna.FALLBACK : clientHomeFauna.apply(home);
        }
        return clientFauna.get();
    }

    /** 服务端：出生地（见 {@link #homeKey()}）对应的物种表，空的按这个世界的。 */
    private static Fauna faunaFor(ServerLevel level, String home) {
        if (home.isEmpty()) {
            return Planets.fauna(level).orElse(Fauna.FALLBACK);
        }
        return home.equals(FALLBACK_HOME) ? Fauna.FALLBACK : Planets.fauna(level.getServer(), home);
    }

    public int speciesIndex() {
        return this.entityData.get(DATA_SPECIES);
    }

    public CreatureSpecies species() {
        Fauna fauna = this.fauna();
        int index = this.speciesIndex();
        CreatureSpecies species = this.cachedSpecies;
        if (species == null || this.cachedFauna != fauna || this.cachedIndex != index) {
            species = fauna.get(index);
            this.cachedFauna = fauna;
            this.cachedIndex = index;
            this.cachedSpecies = species;
        }
        return species;
    }

    /** 这一只的性情：随物种，卵序列仪能改（ADR-0026 §九）。 */
    public Temperament temperament() {
        return this.traits().temperament();
    }

    /** 设定物种，并按物种设置生命、速度、攻击和体型；还没定性状的按 UUID 定一份野生的。 */
    public void setSpecies(CreatureSpecies species) {
        this.entityData.set(DATA_SPECIES, species.index());
        this.speciesAssigned = true;
        if (!this.traitsSet) {
            this.setTraitsData(CreatureTraits.natural(species, this.traitSeed()));
        }
        this.applyStats(species);
    }

    protected void applyStats(CreatureSpecies species) {
        this.setBase(Attributes.MAX_HEALTH, species.health());
        this.setBase(Attributes.MOVEMENT_SPEED, species.speed());
        this.setBase(Attributes.ATTACK_DAMAGE, Math.max(1, species.attack()));
        this.setBase(Attributes.SCALE, this.bodyScale(species));
        // 巨型物种一步跨得上一格高的坎
        this.setBase(Attributes.STEP_HEIGHT, Fauna.stepHeight(species));
        // 能骑的蓄满力一跳有三四格高，落地不该摔伤
        this.setBase(Attributes.SAFE_FALL_DISTANCE, Husbandry.rideable(species) ? 6 : 3);
        this.setHealth(this.getMaxHealth());
        this.refreshDimensions();
    }

    /** 体型：物种的大小 × 个体的体型系数；幼崽按长大的进度从一半长到一倍。 */
    private double bodyScale(CreatureSpecies species) {
        double scale = species.size() * this.traits().size();
        return this.youngAge >= 0 ? scale * Companion.youngScale(this.youngAge) : scale;
    }

    /** 性状、年龄变了：重算体型。 */
    private void refreshBody() {
        this.setBase(Attributes.SCALE, this.bodyScale(this.species()));
    }

    // ---------------------------------------------------------------- 性状

    /** 这只动物自己的性状（ADR-0026 §九）。还没定的（老存档的第一刻之前）按物种的默认。 */
    public CreatureTraits traits() {
        CreatureTraits traits = this.cachedTraits;
        if (traits != null) {
            return traits;
        }
        int temperament = this.entityData.get(DATA_TEMPERAMENT);
        Temperament[] values = Temperament.values();
        if (temperament < 0 || temperament >= values.length) {
            return CreatureTraits.of(this.species());
        }
        traits = new CreatureTraits(this.entityData.get(DATA_TRAIT_SIZE), this.entityData.get(DATA_TINT), values[temperament]);
        this.cachedTraits = traits;
        return traits;
    }

    /** 换一份性状（卵序列仪改的、卵上带的），体型跟着变。 */
    public void setTraits(CreatureTraits traits) {
        this.setTraitsData(traits);
        this.refreshBody();
    }

    private void setTraitsData(CreatureTraits traits) {
        this.entityData.set(DATA_TRAIT_SIZE, (float) traits.size());
        this.entityData.set(DATA_TINT, traits.tint());
        this.entityData.set(DATA_TEMPERAMENT, (byte) traits.temperament().ordinal());
        // 服务端留着原样的（同步过去的体型系数是 float）
        this.cachedTraits = traits;
        this.traitsSet = true;
    }

    /** 野生性状的种子：按 UUID，同一只每次算出来一样。 */
    private long traitSeed() {
        UUID id = this.getUUID();
        return id.getMostSignificantBits() ^ Long.rotateLeft(id.getLeastSignificantBits(), 31);
    }

    /** 还没长大。 */
    public boolean isYoung() {
        return this.youngAge >= 0;
    }

    /** 长大的进度（0–1），成年的是 1。 */
    public double growth() {
        return this.youngAge < 0 ? 1 : Math.min(1, this.youngAge / (double) Companion.GROW_TIME);
    }

    protected void setBase(Holder<Attribute> attribute, double value) {
        AttributeInstance instance = this.getAttribute(attribute);
        if (instance != null) {
            instance.setBaseValue(value);
        }
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) {
        super.onSyncedDataUpdated(accessor);
        if (DATA_SPECIES.equals(accessor)) {
            this.refreshDimensions();
        }
        if (DATA_TRAIT_SIZE.equals(accessor) || DATA_TINT.equals(accessor) || DATA_TEMPERAMENT.equals(accessor)) {
            this.cachedTraits = null;
        }
    }

    @Override
    protected EntityDimensions getDefaultDimensions(Pose pose) {
        CreatureSpecies species = this.species();
        synchronized (DIMENSIONS) {
            return DIMENSIONS.computeIfAbsent(species, s -> {
                CreatureAnatomy anatomy = CreatureAnatomy.of(s);
                float height = anatomy.hitHeight();
                return EntityDimensions.scalable(anatomy.hitWidth(), height).withEyeHeight(height * 0.85f);
            });
        }
    }

    /** 物种名：两种语言的名字都带上，由语言文件挑一个显示。 */
    @Override
    protected Component getTypeName() {
        CreatureSpecies species = this.species();
        return Component.translatable("entity.universe.species", species.name().zh(), species.name().en());
    }

    @Override
    public @Nullable SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty,
            EntitySpawnReason reason, @Nullable SpawnGroupData groupData) {
        if (!this.speciesAssigned) {
            this.setSpecies(this.chooseSpecies(level.getRandom()));
        }
        return super.finalizeSpawn(level, difficulty, reason, groupData);
    }

    /** 刷怪蛋、命令召唤时按脚下的群系挑物种，挑不到就在同类（地上的或飞的）里随便挑一种。 */
    private CreatureSpecies chooseSpecies(RandomSource random) {
        Fauna fauna = this.fauna();
        BiomeKind biome = PlanetBiomes.kind(this.level().getBiome(this.blockPosition()));
        CreatureSpecies picked = biome == null ? null : fauna.pick(biome, random.nextDouble());
        boolean flying = this.isFlyerType();
        if (picked != null && picked.plan().flying() == flying) {
            return picked;
        }
        List<CreatureSpecies> sameKind = fauna.species().stream().filter(s -> s.plan().flying() == flying).toList();
        List<CreatureSpecies> pool = sameKind.isEmpty() ? fauna.species() : sameKind;
        return pool.get(random.nextInt(pool.size()));
    }

    protected boolean isFlyerType() {
        return false;
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putInt("Species", this.speciesIndex());
        EntityReference.store(this.getOwnerReference(), output, "Owner");
        output.putString("Home", this.entityData.get(DATA_HOME));
        output.putBoolean("Staying", this.isStaying());
        output.putInt("FeedCooldown", this.feedCooldown);
        output.putInt("ProduceTimer", this.produceTimer);
        if (this.traitsSet) {
            CreatureTraits traits = this.traits();
            ValueOutput out = output.child("Traits");
            out.putDouble("Size", traits.size());
            out.putInt("Tint", traits.tint());
            out.putString("Temperament", traits.temperament().name());
        }
        if (this.youngAge >= 0) {
            output.putInt("Age", this.youngAge);
        }
        Companion companion = this.companion();
        if (companion != null) {
            ValueOutput out = output.child("Companion");
            out.putInt("Trust", companion.trust());
            out.putInt("Satiety", companion.satiety());
            out.putLong("TrustDay", companion.trustDay());
            out.putInt("TrustToday", companion.trustToday());
            out.putInt("LayProgress", companion.layProgress());
            out.putInt("Starving", companion.starving());
            out.putLong("DigReadyAt", companion.digReadyAt());
        }
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        // /summon 也会走这里（不带数据）：没存物种时留给 finalizeSpawn 按群系挑
        input.getInt("Species").ifPresent(index -> {
            this.entityData.set(DATA_SPECIES, index);
            this.speciesAssigned = true;
        });
        EntityReference<LivingEntity> owner = EntityReference.read(input, "Owner");
        this.entityData.set(DATA_OWNER, Optional.ofNullable(owner));
        this.entityData.set(DATA_HOME, input.getStringOr("Home", ""));
        this.entityData.set(DATA_STAYING, input.getBooleanOr("Staying", false));
        this.feedCooldown = input.getIntOr("FeedCooldown", 0);
        this.produceTimer = input.getIntOr("ProduceTimer", 0);
        // 老存档没有性状：第一刻按 UUID 补上，体型跟着重算
        input.child("Traits").ifPresent(traits -> this.setTraitsData(new CreatureTraits(traits.getDoubleOr("Size", 1),
                traits.getIntOr("Tint", CreatureTraits.NO_TINT), this.readTemperament(traits.getStringOr("Temperament", "")))));
        this.youngAge = Math.max(-1, input.getIntOr("Age", -1));
        this.companion = input.child("Companion").map(c -> Companion.restore(c.getIntOr("Trust", Companion.TAME_TRUST),
                c.getIntOr("Satiety", Companion.FULL), c.getLongOr("TrustDay", Long.MIN_VALUE), c.getIntOr("TrustToday", 0),
                c.getIntOr("LayProgress", 0), c.getIntOr("Starving", 0), c.getLongOr("DigReadyAt", 0))).orElse(null);
        this.bodyDirty = true;
        // 带物种数据的 /summon 不走 finalizeSpawn，也没有存过的属性：照物种定生命、速度、体型这些（读档时属性已经读回来了）
        if (this.speciesAssigned && input.read(TAG_ATTRIBUTES, AttributeInstance.Packed.LIST_CODEC).isEmpty()) {
            this.applyStats(this.species());
        }
    }

    private Temperament readTemperament(String name) {
        for (Temperament temperament : Temperament.values()) {
            if (temperament.name().equals(name)) {
                return temperament;
            }
        }
        return this.species().temperament();
    }

    // ---------------------------------------------------------------- 行为

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(0, new TamedGoals.Stay(this));
        this.goalSelector.addGoal(0, new TamedGoals.Dig(this));
        this.goalSelector.addGoal(1, new HerdPanicGoal(this, 1.7));
        this.goalSelector.addGoal(2, when(this::fights, new MeleeAttackGoal(this, 1.3, true)));
        this.goalSelector.addGoal(3, when(() -> this.temperament() == Temperament.SKITTISH && !this.isTamed(),
                new AvoidEntityGoal<>(this, Player.class, 12, 1.2, 1.7)));
        // 胆小的动物也躲着别的物种里的捕食者
        this.goalSelector.addGoal(3, when(() -> !this.fights(), new AvoidEntityGoal<>(this, AlienCreature.class,
                other -> other instanceof AlienCreature c && c.temperament() == Temperament.PREDATOR && !c.isTamed()
                        && c.speciesIndex() != this.speciesIndex(), 10, 1.2, 1.6, other -> true)));
        this.goalSelector.addGoal(4, new TamedGoals.FollowOwner(this, 1.25));
        this.registerMovementGoals();
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8));
        this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));

        this.targetSelector.addGoal(1, new TamedGoals.DefendOwner(this));
        this.targetSelector.addGoal(2, new TamedGoals.AssistOwner(this));
        this.targetSelector.addGoal(1, when(this::fights, new HurtByTargetGoal(this) {
            @Override
            protected void alertOther(Mob other, LivingEntity hurtByMob) {
                // 只叫上同一物种的同伴
                if (other instanceof AlienCreature c && c.speciesIndex() == AlienCreature.this.speciesIndex()) {
                    super.alertOther(other, hurtByMob);
                }
            }
        }.setAlertOthers()));
        this.targetSelector.addGoal(2, when(() -> this.temperament() == Temperament.PREDATOR && !this.isTamed(),
                new NearestAttackableTargetGoal<>(this, Player.class, 10, true, false,
                        (target, level) -> level.getDifficulty() != Difficulty.PEACEFUL)));
        // 驯服的捕食者只帮主人打，不自己去捕猎
        this.targetSelector.addGoal(3, when(() -> this.temperament() == Temperament.PREDATOR && !this.isTamed(),
                new NearestAttackableTargetGoal<>(this, AlienCreature.class, 120, true, false,
                        (target, level) -> target instanceof AlienCreature prey && !prey.fights()
                                && prey.speciesIndex() != this.speciesIndex()
                                && prey.getBbWidth() < this.getBbWidth() * 1.3)));
    }

    /** 闲逛；飞兽换成飞行的版本。 */
    protected void registerMovementGoals() {
        this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.9));
    }

    public boolean fights() {
        return this.temperament().fights();
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        boolean hurt = super.hurtServer(level, source, damage);
        if (hurt && !this.fights() && source.getEntity() instanceof LivingEntity attacker) {
            // 一只受伤，整群都跑
            for (AlienCreature mate : level.getEntitiesOfClass(AlienCreature.class, this.getBoundingBox().inflate(12, 4, 12),
                    c -> c.speciesIndex() == this.speciesIndex())) {
                mate.alarm(attacker);
            }
        }
        return hurt;
    }

    void alarm(LivingEntity attacker) {
        this.alarm = ALARM_TICKS;
        this.threat = attacker;
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level() instanceof ServerLevel level && this.isAlive()) {
            this.tickLife(level);
        }
    }

    private void tickLife(ServerLevel level) {
        if (!this.traitsSet && this.speciesAssigned) {
            this.setTraitsData(CreatureTraits.natural(this.species(), this.traitSeed()));
            this.bodyDirty = true;
        }
        if (this.youngAge >= 0) {
            if (++this.youngAge >= Companion.GROW_TIME) {
                this.youngAge = -1;
                this.bodyDirty = true;
                if (this.getOwner() instanceof ServerPlayer owner) {
                    owner.sendOverlayMessage(Component.translatable("message.universe.companion.grown", this.getDisplayName()));
                }
            } else if (this.youngAge % 20 == 0) {
                // 一秒长一点，看着是慢慢变大的
                this.bodyDirty = true;
            }
        }
        if (this.bodyDirty) {
            this.bodyDirty = false;
            this.refreshBody();
        }
        Companion companion = this.companion();
        if (companion != null && companion.tick(this.youngAge < 0)) {
            CreatureEggs.lay(level, this);
        }
        if (this.digTicks > 0) {
            this.tickDig(level);
        }
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        if (this.alarm > 0 && --this.alarm == 0) {
            this.threat = null;
        }
        if (this.feedCooldown > 0) {
            this.feedCooldown--;
        }
        if (this.tameCooldown > 0) {
            this.tameCooldown--;
        }
        if (this.produceTimer > 0 && --this.produceTimer == 0) {
            this.produce(level);
        }
    }

    // ---------------------------------------------------------------- 喂养和驯服

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        CreatureSpecies species = this.species();
        if (CreatureHusbandry.isFood(stack)) {
            if (!CreatureHusbandry.eats(this.temperament(), stack)) {
                if (!this.level().isClientSide()) {
                    player.sendOverlayMessage(Component.translatable("message.universe.creature.wrong_food", this.getDisplayName()));
                }
                return InteractionResult.FAIL;
            }
            return this.feed(player, stack, species);
        }
        if (stack.isEmpty() && this.isOwnedBy(player)) {
            if (player.isSecondaryUseActive()) {
                // 空手潜行右键：伙伴界面（跟随 / 待命挪到了界面的按钮上）
                if (player instanceof ServerPlayer serverPlayer) {
                    this.openCompanionScreen(serverPlayer);
                }
                return InteractionResult.SUCCESS;
            }
            if (this.canBeRidden() && !this.isVehicle() && !player.isPassenger()) {
                if (!this.level().isClientSide()) {
                    this.mount(player);
                }
                return InteractionResult.SUCCESS;
            }
        }
        return super.mobInteract(player, hand);
    }

    /**
     * 喂一口。五分钟里第一口产出一份产物；野生的每口都有机会驯服，一秒一口；驯服的吃饱了不吃，受了伤照样吃、回点血；
     * 正在攻击玩家的不吃。
     */
    private InteractionResult feed(Player player, ItemStack stack, CreatureSpecies species) {
        if (!(this.level() instanceof ServerLevel level)) {
            return InteractionResult.SUCCESS;
        }
        if (this.getTarget() instanceof Player) {
            return InteractionResult.FAIL;
        }
        boolean hurt = this.getHealth() < this.getMaxHealth();
        boolean hungry = this.feedCooldown == 0;
        Companion companion = this.companion();
        if (companion != null) {
            // 伙伴：九成饱以上不吃（受了伤照样吃），每口补饱食、涨信任；五分钟里第一口才产出
            if (!companion.hungry() && !hurt) {
                player.sendOverlayMessage(Component.translatable("message.universe.companion.full", this.getDisplayName()));
                return InteractionResult.FAIL;
            }
            this.eat(player, stack);
            if (hurt) {
                this.heal(Husbandry.FEED_HEAL);
            }
            companion.feed(level.getGameTime());
            if (hungry) {
                this.feedCooldown = Husbandry.FEED_COOLDOWN;
                this.produceTimer = Husbandry.PRODUCE_DELAY;
            }
            this.particles(level, ParticleTypes.HEART, 3);
            return InteractionResult.SUCCESS_SERVER;
        }
        if (!this.isTamed() && this.tameCooldown > 0) {
            // 还在嚼上一口
            return InteractionResult.FAIL;
        }
        this.eat(player, stack);
        if (hurt) {
            this.heal(Husbandry.FEED_HEAL);
        }
        if (hungry) {
            this.feedCooldown = Husbandry.FEED_COOLDOWN;
            this.produceTimer = Husbandry.PRODUCE_DELAY;
        }
        this.tameCooldown = Husbandry.TAME_INTERVAL;
        if (this.random.nextDouble() < Husbandry.tameChance(this.temperament())) {
            this.tame(level, player);
        } else {
            // 和原版驯马一样：没驯服冒烟
            this.particles(level, ParticleTypes.SMOKE, 6);
        }
        return InteractionResult.SUCCESS_SERVER;
    }

    private void eat(Player player, ItemStack stack) {
        stack.consume(1, player);
        this.playSound(SoundEvents.GENERIC_EAT.value(), 1, 0.8F + this.random.nextFloat() * 0.4F);
        this.gameEvent(GameEvent.EAT);
    }

    /** 驯服：记下主人和出生的星球，不再消失，不再记仇，回满血。 */
    public void tame(ServerLevel level, Player player) {
        this.entityData.set(DATA_OWNER, Optional.of(EntityReference.of(player)));
        this.entityData.set(DATA_HOME, homeOf(level));
        this.companion = Companion.tamed();
        this.setPersistenceRequired();
        this.setTarget(null);
        this.setLastHurtByMob(null);
        this.alarm = 0;
        this.threat = null;
        this.getNavigation().stop();
        this.setHealth(this.getMaxHealth());
        this.particles(level, ParticleTypes.HEART, 9);
        this.playSound(SoundEvents.PLAYER_LEVELUP, 0.6F, 1.4F);
        player.sendOverlayMessage(Component.translatable("message.universe.creature.tamed", this.getDisplayName()));
        if (player instanceof ServerPlayer serverPlayer) {
            MilestoneTrigger.reach(serverPlayer, MilestoneTrigger.TAME);
        }
    }

    /** 在这个世界驯服的动物记哪颗星球：本模组的星球记天体路径，别处（主世界、命令召唤的）记后备动物表。 */
    private static String homeOf(ServerLevel level) {
        if (Planets.fauna(level).isEmpty()) {
            return FALLBACK_HOME;
        }
        return Planets.bodyId(level.dimension()).map(BodyId::path).orElse(FALLBACK_HOME);
    }

    private void produce(ServerLevel level) {
        CreatureSpecies species = this.species();
        this.spawnAtLocation(level, CreatureHusbandry.product(species));
        this.playSound(CreatureHusbandry.productSound(Husbandry.product(species)), 1, (this.random.nextFloat() - this.random.nextFloat()) * 0.2F + 1);
        this.gameEvent(GameEvent.ENTITY_PLACE);
    }

    private void particles(ServerLevel level, ParticleOptions particle, int count) {
        level.sendParticles(particle, this.getX(), this.getY() + this.getBbHeight() * 0.8, this.getZ(), count,
                this.getBbWidth() * 0.4, this.getBbHeight() * 0.25, this.getBbWidth() * 0.4, 0.02);
    }

    /** 还要多少刻产出，0 是没在等（给开发脚本和调试用）。 */
    public int produceTimer() {
        return this.produceTimer;
    }

    // ---------------------------------------------------------------- 主人

    @Override
    public @Nullable EntityReference<LivingEntity> getOwnerReference() {
        return this.entityData.get(DATA_OWNER).orElse(null);
    }

    public boolean isTamed() {
        return this.entityData.get(DATA_OWNER).isPresent();
    }

    public boolean isOwnedBy(LivingEntity entity) {
        EntityReference<LivingEntity> owner = this.getOwnerReference();
        return owner != null && owner.getUUID().equals(entity.getUUID());
    }

    public boolean isStaying() {
        return this.entityData.get(DATA_STAYING);
    }

    /** 出生的星球；没驯服、或者不是在本模组的星球上驯服的为空。 */
    public Optional<BodyId> homeBody() {
        return BodyId.parse(this.entityData.get(DATA_HOME));
    }

    /** 记在卵上的出生地（ADR-0026 §九）：驯服的记出生的星球，野生的记所在的世界。孵出来的幼崽按它找物种表。 */
    public String homeKey() {
        String home = this.entityData.get(DATA_HOME);
        return home.isEmpty() && this.level() instanceof ServerLevel level ? homeOf(level) : home;
    }

    /**
     * 卵孵出一只幼崽（ADR-0026 §九）：归 {@code owner}（主人不在线也行），直接是伙伴，过两个游戏日长大。
     *
     * @param home    出生地，见 {@link #homeKey()}
     * @param species 物种序号
     * @return 放不下时是 null
     */
    public static @Nullable AlienCreature hatch(ServerLevel level, Vec3 pos, String home, int species, CreatureTraits traits,
            @Nullable UUID owner) {
        Optional<CreatureSpecies> kind = faunaFor(level, home).find(species);
        if (kind.isEmpty() || kind.get().plan().aquatic()) {
            return null;
        }
        EntityType<? extends AlienCreature> type = kind.get().plan().flying() ? UniverseEntities.ALIEN_FLYER.get()
                : UniverseEntities.ALIEN_CREATURE.get();
        AlienCreature baby = type.create(level, EntitySpawnReason.BREEDING);
        if (baby == null) {
            return null;
        }
        baby.entityData.set(DATA_HOME, home);
        baby.youngAge = 0;
        baby.setTraitsData(traits);
        baby.setSpecies(kind.get());
        baby.snapTo(pos.x, pos.y, pos.z, level.getRandom().nextFloat() * 360, 0);
        if (!level.noCollision(baby)) {
            baby.discard();
            return null;
        }
        if (owner != null) {
            baby.entityData.set(DATA_OWNER, Optional.of(EntityReference.of(owner)));
            baby.companion = Companion.tamed();
        }
        baby.setPersistenceRequired();
        level.addFreshEntity(baby);
        return baby;
    }

    // ---------------------------------------------------------------- 伙伴（ADR-0026 §九）

    /** 伙伴的状态；没驯服的是 null。老存档里驯服的第一次用到时补一份刚驯服的。 */
    public @Nullable Companion companion() {
        if (!this.isTamed() || this.level().isClientSide()) {
            return null;
        }
        if (this.companion == null) {
            this.companion = Companion.tamed();
        }
        return this.companion;
    }

    /** 打开伙伴界面。 */
    public void openCompanionScreen(ServerPlayer player) {
        player.openMenu(new SimpleMenuProvider((id, inventory, p) -> new CompanionMenu(id, inventory, this), this.getDisplayName()),
                buf -> buf.writeVarInt(this.getId()));
    }

    /** 跟随 / 待命切换。 */
    public void toggleStay(Player player) {
        boolean stay = !this.isStaying();
        this.entityData.set(DATA_STAYING, stay);
        this.getNavigation().stop();
        if (stay) {
            this.setTarget(null);
        }
        player.sendOverlayMessage(Component.translatable(stay ? "message.universe.creature.staying" : "message.universe.creature.following",
                this.getDisplayName()));
    }

    /** 能骑：够大的走兽，长大了的。 */
    public boolean canBeRidden() {
        return Husbandry.rideable(this.species()) && !this.isYoung();
    }

    /** 主人骑上去。 */
    public boolean mount(Player player) {
        if (!this.canBeRidden() || this.isVehicle() || player.isPassenger()) {
            return false;
        }
        this.entityData.set(DATA_STAYING, false);
        this.setTarget(null);
        this.getNavigation().stop();
        return player.startRiding(this);
    }

    public enum DigResult {
        STARTED,
        /** 今天挖过了。 */
        NOT_READY,
        /** 正在刨、被骑着、在水里。 */
        BUSY
    }

    /** 开始挖：原地刨 {@link #DIG_TICKS} 刻，刨完按战利品表挖出东西。每只每个游戏日一次。 */
    public DigResult startDig(ServerLevel level) {
        Companion companion = this.companion();
        if (companion == null || this.digTicks > 0 || this.isVehicle() || this.isPassenger() || this.isInWater()) {
            return DigResult.BUSY;
        }
        long time = level.getGameTime();
        if (!companion.canDig(time)) {
            return DigResult.NOT_READY;
        }
        companion.dig(time);
        this.digTicks = DIG_TICKS;
        this.getNavigation().stop();
        this.setTarget(null);
        return DigResult.STARTED;
    }

    public boolean isDigging() {
        return this.digTicks > 0;
    }

    /** 还要刨多少刻（给界面和开发脚本用）。 */
    public int digTicks() {
        return this.digTicks;
    }

    /** 刨的地方：身子前面半个身位的地面。 */
    Vec3 digSpot() {
        return this.position().add(Vec3.directionFromRotation(0, this.getYRot()).scale(this.getBbWidth() * 0.6));
    }

    private void tickDig(ServerLevel level) {
        Vec3 spot = this.digSpot();
        BlockPos below = BlockPos.containing(spot.x, this.getY() - 0.2, spot.z);
        BlockState ground = level.getBlockState(below);
        if (ground.isAir()) {
            ground = level.getBlockState(this.getOnPos());
        }
        if (this.digTicks % 4 == 0 && !ground.isAir()) {
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), spot.x, this.getY() + 0.1, spot.z, 6,
                    this.getBbWidth() * 0.2, 0.05, this.getBbWidth() * 0.2, 0.15);
        }
        if (this.digTicks % 8 == 0) {
            this.playSound(ground.getSoundType().getHitSound(), 0.9F, 0.8F + this.random.nextFloat() * 0.4F);
            this.swingForAttack(InteractionHand.MAIN_HAND);
        }
        if (--this.digTicks == 0) {
            this.finishDig(level, ground);
        }
    }

    /** 刨完了：按战利品表掉东西，心情好的多挖一份。 */
    private void finishDig(ServerLevel level, BlockState ground) {
        Companion companion = this.companion();
        LootTable table = level.getServer().reloadableRegistries().getLootTable(DIG_LOOT);
        LootParams params = new LootParams.Builder(level)
                .withParameter(LootContextParams.ORIGIN, this.digSpot())
                .withOptionalParameter(LootContextParams.THIS_ENTITY, this.getOwner())
                .withLuck(companion != null && companion.mood() == Companion.Mood.HAPPY ? 1 : 0)
                .create(LootContextParamSets.CHEST);
        table.getRandomItems(params, stack -> this.spawnAtLocation(level, stack));
        if (!ground.isAir()) {
            this.playSound(ground.getSoundType().getBreakSound(), 1, 1);
        }
        this.particles(level, ParticleTypes.HAPPY_VILLAGER, 6);
        if (this.getOwner() instanceof ServerPlayer owner) {
            owner.sendOverlayMessage(Component.translatable("message.universe.companion.dug", this.getDisplayName()));
        }
    }

    /** 放生：不再归谁，不跟着走，伙伴的状态清掉；出生的星球留着（物种不变）。 */
    public void release(ServerPlayer player) {
        if (!this.isOwnedBy(player)) {
            return;
        }
        this.ejectPassengers();
        this.entityData.set(DATA_OWNER, Optional.empty());
        this.entityData.set(DATA_STAYING, false);
        this.companion = null;
        this.digTicks = 0;
        this.getNavigation().stop();
        this.particles(player.level(), ParticleTypes.POOF, 8);
        player.sendOverlayMessage(Component.translatable("message.universe.companion.released", this.getDisplayName()));
    }

    /** 改名：服务端再整理、校验一遍（最多 {@link DiscoveryLog#MAX_NAME} 个字），空的恢复物种名。 */
    public void rename(ServerPlayer player, String raw) {
        Optional<String> clean = DiscoveryLog.cleanName(raw);
        if (clean.isEmpty()) {
            player.sendOverlayMessage(Component.translatable("message.universe.discovery.rename_invalid_name"));
            return;
        }
        this.setCustomName(clean.get().isEmpty() ? null : Component.literal(clean.get()));
    }

    /** 驯服的不打主人，也不打同一个主人的别的动物。 */
    @Override
    public boolean canAttack(LivingEntity target) {
        if (this.isTamed() && (this.isOwnedBy(target) || target instanceof AlienCreature other && other.isTamed()
                && Objects.equals(other.getOwnerReference(), this.getOwnerReference()))) {
            return false;
        }
        return super.canAttack(target);
    }

    @Override
    protected boolean considersEntityAsAlly(Entity other) {
        if (this.isTamed()) {
            LivingEntity owner = this.getOwner();
            if (other == owner || owner != null && other instanceof AlienCreature creature && creature.isOwnedBy(owner)) {
                return true;
            }
        }
        return super.considersEntityAsAlly(other);
    }

    @Override
    public boolean requiresCustomPersistence() {
        return this.isTamed() || super.requiresCustomPersistence();
    }

    @Override
    public boolean removeWhenFarAway(double distSqr) {
        // 巨型生物远远看得见，不随机消失，走出 128 格才收走（刷怪器保证 160 格内只有一只）
        if (Fauna.giant(this.species()) && distSqr <= DESPAWN_FAR * DESPAWN_FAR) {
            return false;
        }
        return !this.isTamed() && super.removeWhenFarAway(distSqr);
    }

    private static final double DESPAWN_FAR = 128;

    /** 驯服的死了告诉主人一声（和原版的狼一样）。 */
    @Override
    public void die(DamageSource source) {
        Component message = this.getCombatTracker().getDeathMessage();
        super.die(source);
        if (this.dead && this.level() instanceof ServerLevel level && level.getGameRules().get(GameRules.SHOW_DEATH_MESSAGES)
                && this.getOwner() instanceof ServerPlayer owner) {
            owner.sendSystemMessage(message);
        }
    }

    // ---------------------------------------------------------------- 骑乘

    @Override
    public @Nullable LivingEntity getControllingPassenger() {
        return this.getFirstPassenger() instanceof Player rider && this.isOwnedBy(rider) ? rider : super.getControllingPassenger();
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return this.getPassengers().isEmpty();
    }

    /** 骑的人坐在背上（走兽背的顶面，按体型缩放）。 */
    @Override
    protected Vec3 getPassengerAttachmentPoint(Entity passenger, EntityDimensions dimensions, float scale) {
        CreatureSpecies species = this.species();
        if (species != this.saddleSpecies) {
            this.saddleSpecies = species;
            this.saddleHeight = CreatureAnatomy.of(species).saddleHeight();
        }
        return new Vec3(0, this.saddleHeight * scale, 0);
    }

    @Override
    protected void tickRidden(Player controller, Vec3 riddenInput) {
        super.tickRidden(controller, riddenInput);
        this.setRot(controller.getYRot(), controller.getXRot() * 0.5F);
        this.yRotO = this.yBodyRot = this.yHeadRot = this.getYRot();
        if (this.isLocalInstanceAuthoritative() && this.onGround()) {
            if (this.jumpPending > 0) {
                double impulse = Husbandry.rideJump(this.species()) * this.jumpPending * this.getBlockJumpFactor();
                Vec3 movement = this.getDeltaMovement();
                this.setDeltaMovement(movement.x, impulse, movement.z);
                if (riddenInput.z > 0) {
                    // 往前跳时顺势往前蹿一点，和原版的马一样
                    float sin = Mth.sin(this.getYRot() * Mth.DEG_TO_RAD);
                    float cos = Mth.cos(this.getYRot() * Mth.DEG_TO_RAD);
                    this.setDeltaMovement(this.getDeltaMovement().add(-0.4 * sin * this.jumpPending, 0, 0.4 * cos * this.jumpPending));
                }
                this.needsSync = true;
                CommonHooks.onLivingJump(this);
            }
            this.jumpPending = 0;
        }
    }

    /** 骑着走：横着走慢一半、往后退只有四分之一，和原版的马一样。 */
    @Override
    protected Vec3 getRiddenInput(Player controller, Vec3 selfInput) {
        float sideways = controller.xxa * 0.5F;
        float forward = controller.zza;
        if (forward <= 0) {
            forward *= 0.25F;
        }
        return new Vec3(sideways, 0, forward);
    }

    @Override
    protected float getRiddenSpeed(Player controller) {
        return (float) Husbandry.rideSpeed(this.species());
    }

    @Override
    public void onPlayerJump(int jumpAmount) {
        this.jumpPending = this.getPlayerJumpPendingScale(Math.max(0, jumpAmount));
    }

    @Override
    public boolean canJump() {
        return this.getControllingPassenger() != null;
    }

    @Override
    public void handleStartJump(int jumpScale) {
        this.playSound(SoundEvents.CAMEL_DASH, 0.5F, this.getVoicePitch());
    }

    @Override
    public void handleStopJump() {
    }

    /** 按条件启用的目标：物种在构造之后才知道，所以目标都先注册上，用的时候再看性情。 */
    protected static Goal when(BooleanSupplier condition, Goal goal) {
        return new Goal() {
            {
                this.setFlags(goal.getFlags().isEmpty() ? EnumSet.noneOf(Goal.Flag.class) : EnumSet.copyOf(goal.getFlags()));
            }

            @Override
            public boolean canUse() {
                return condition.getAsBoolean() && goal.canUse();
            }

            @Override
            public boolean canContinueToUse() {
                return condition.getAsBoolean() && goal.canContinueToUse();
            }

            @Override
            public boolean isInterruptable() {
                return goal.isInterruptable();
            }

            @Override
            public void start() {
                goal.start();
            }

            @Override
            public void stop() {
                goal.stop();
            }

            @Override
            public boolean requiresUpdateEveryTick() {
                return goal.requiresUpdateEveryTick();
            }

            @Override
            public void tick() {
                goal.tick();
            }

            @Override
            public String toString() {
                return goal.toString();
            }
        };
    }

    /** 不会打架的动物挨打或者同伴挨打时，往远离凶手的方向逃。 */
    private static final class HerdPanicGoal extends PanicGoal {
        private final AlienCreature creature;

        HerdPanicGoal(AlienCreature creature, double speed) {
            super(creature, speed);
            this.creature = creature;
        }

        @Override
        protected boolean shouldPanic() {
            if (this.creature.fights()) {
                return false;
            }
            return super.shouldPanic() || this.creature.alarm > 0;
        }

        @Override
        protected boolean findRandomPosition() {
            LivingEntity threat = this.creature.threat != null ? this.creature.threat : this.creature.getLastHurtByMob();
            if (threat == null) {
                return super.findRandomPosition();
            }
            Vec3 pos = DefaultRandomPos.getPosAway(this.creature, 14, 6, threat.position());
            if (pos == null) {
                return super.findRandomPosition();
            }
            this.posX = pos.x;
            this.posY = pos.y;
            this.posZ = pos.z;
            return true;
        }
    }

    // ---------------------------------------------------------------- 叫声

    @Override
    protected @Nullable SoundEvent getAmbientSound() {
        return sounds(this.species().voice())[0];
    }

    @Override
    protected @Nullable SoundEvent getHurtSound(DamageSource source) {
        return sounds(this.species().voice())[1];
    }

    @Override
    protected @Nullable SoundEvent getDeathSound() {
        return sounds(this.species().voice())[2];
    }

    /** 个子越大叫声越低（按这一只的体型，幼崽叫得尖）。 */
    @Override
    public float getVoicePitch() {
        double size = this.getScale();
        return (float) ((this.random.nextFloat() - this.random.nextFloat()) * 0.15 + 1.25 / Math.sqrt(Math.max(0.3, size)));
    }

    @Override
    public int getAmbientSoundInterval() {
        return this.species().plan() == BodyPlan.SERPENT ? 240 : 160;
    }

    /** 叫声换成原版的音效：平时、受伤、死亡。 */
    private static SoundEvent[] sounds(CreatureSpecies.Voice voice) {
        return switch (voice) {
            case LOW -> new SoundEvent[] {SoundEvents.CAMEL_AMBIENT, SoundEvents.CAMEL_HURT, SoundEvents.CAMEL_DEATH};
            case GRUNT -> new SoundEvent[] {SoundEvents.HOGLIN_AMBIENT, SoundEvents.HOGLIN_HURT, SoundEvents.HOGLIN_DEATH};
            case BLEAT -> new SoundEvent[] {SoundEvents.LLAMA_AMBIENT, SoundEvents.LLAMA_HURT, SoundEvents.LLAMA_DEATH};
            case CHIRP -> new SoundEvent[] {SoundEvents.PARROT_AMBIENT, SoundEvents.PARROT_HURT, SoundEvents.PARROT_DEATH};
            case CROAK -> new SoundEvent[] {SoundEvents.FROG_AMBIENT, SoundEvents.FROG_HURT, SoundEvents.FROG_DEATH};
            case HISS -> new SoundEvent[] {SoundEvents.SPIDER_AMBIENT, SoundEvents.SPIDER_HURT, SoundEvents.SPIDER_DEATH};
            case TRILL -> new SoundEvent[] {SoundEvents.AXOLOTL_IDLE_AIR, SoundEvents.AXOLOTL_HURT, SoundEvents.AXOLOTL_DEATH};
            case SCREECH -> new SoundEvent[] {SoundEvents.PHANTOM_AMBIENT, SoundEvents.PHANTOM_HURT, SoundEvents.PHANTOM_DEATH};
            case HUM -> new SoundEvent[] {SoundEvents.SNIFFER_IDLE, SoundEvents.SNIFFER_HURT, SoundEvents.SNIFFER_DEATH};
        };
    }

    // ---------------------------------------------------------------- 掉落

    /** 大个子多掉几块肉。 */
    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean killedByPlayer) {
        super.dropCustomDeathLoot(level, source, killedByPlayer);
        double size = this.getScale();
        int extra = (int) Math.floor((size - 0.9) * 2.2 + this.random.nextDouble());
        if (extra > 0) {
            boolean cooked = this.isOnFire();
            this.spawnAtLocation(level, new ItemStack(cooked ? UniverseItems.COOKED_ALIEN_MEAT.get() : UniverseItems.ALIEN_MEAT.get(), extra));
        }
    }
}
