package io.github.sweetcornna.universe.entity;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.core.ecology.BodyPlan;
import io.github.sweetcornna.universe.core.ecology.CreatureAnatomy;
import io.github.sweetcornna.universe.core.ecology.CreatureSpecies;
import io.github.sweetcornna.universe.core.ecology.Fauna;
import io.github.sweetcornna.universe.core.ecology.Husbandry;
import io.github.sweetcornna.universe.core.ecology.Temperament;
import io.github.sweetcornna.universe.core.galaxy.BodyId;
import io.github.sweetcornna.universe.core.terrain.BiomeKind;
import io.github.sweetcornna.universe.registry.UniverseItems;
import io.github.sweetcornna.universe.world.planet.Planets;
import io.github.sweetcornna.universe.world.planet.gen.PlanetBiomes;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
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
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.CommonHooks;

/**
 * 星球上的程序化动物（玩法设计 §8）。外观、属性、性情都由 {@link CreatureSpecies} 决定，
 * 实体本身只同步、保存物种在星球动物表里的序号，两端各自从星球种子算出物种。
 *
 * <p>喂养（ADR-0017）：按食性喂对东西，冒爱心，30 秒后产出一份产物，5 分钟后才肯再吃；每喂一次有机会驯服。
 * 驯服后记下主人和出生的星球（物种从此按出生的星球算，带到别处也不变），不会消失，跟着主人走、帮主人打架；
 * 空手潜行右键切换跟随和待命，够大的走兽空手右键骑上去（跳跃键蓄力跳）。
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
    private static final String FALLBACK_HOME = "-";
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

    public AlienCreature(EntityType<? extends AlienCreature> type, Level level) {
        super(type, level);
    }

    public static void setClientFauna(Supplier<Fauna> current, Function<String, Fauna> byHome) {
        clientFauna = current;
        clientHomeFauna = byHome;
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
    }

    /** 这只动物的物种表：驯服的按出生的星球；没驯服的按所在的世界，服务端按星球维度算，客户端用当前星球的。 */
    public Fauna fauna() {
        String home = this.entityData.get(DATA_HOME);
        if (!home.isEmpty()) {
            if (home.equals(FALLBACK_HOME)) {
                return Fauna.FALLBACK;
            }
            return this.level() instanceof ServerLevel server ? Planets.fauna(server.getServer(), home) : clientHomeFauna.apply(home);
        }
        if (this.level() instanceof ServerLevel server) {
            return Planets.fauna(server).orElse(Fauna.FALLBACK);
        }
        return clientFauna.get();
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

    public Temperament temperament() {
        return this.species().temperament();
    }

    /** 设定物种，并按物种设置生命、速度、攻击和体型。 */
    public void setSpecies(CreatureSpecies species) {
        this.entityData.set(DATA_SPECIES, species.index());
        this.speciesAssigned = true;
        this.applyStats(species);
    }

    protected void applyStats(CreatureSpecies species) {
        this.setBase(Attributes.MAX_HEALTH, species.health());
        this.setBase(Attributes.MOVEMENT_SPEED, species.speed());
        this.setBase(Attributes.ATTACK_DAMAGE, Math.max(1, species.attack()));
        this.setBase(Attributes.SCALE, species.size());
        // 能骑的蓄满力一跳有三四格高，落地不该摔伤
        this.setBase(Attributes.SAFE_FALL_DISTANCE, Husbandry.rideable(species) ? 6 : 3);
        this.setHealth(this.getMaxHealth());
        this.refreshDimensions();
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
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.entityData.set(DATA_SPECIES, input.getIntOr("Species", 0));
        this.speciesAssigned = true;
        EntityReference<LivingEntity> owner = EntityReference.read(input, "Owner");
        this.entityData.set(DATA_OWNER, Optional.ofNullable(owner));
        this.entityData.set(DATA_HOME, input.getStringOr("Home", ""));
        this.entityData.set(DATA_STAYING, input.getBooleanOr("Staying", false));
        this.feedCooldown = input.getIntOr("FeedCooldown", 0);
        this.produceTimer = input.getIntOr("ProduceTimer", 0);
    }

    // ---------------------------------------------------------------- 行为

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(0, new TamedGoals.Stay(this));
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
            if (!CreatureHusbandry.eats(species, stack)) {
                if (!this.level().isClientSide()) {
                    player.sendOverlayMessage(Component.translatable("message.universe.creature.wrong_food", this.getDisplayName()));
                }
                return InteractionResult.FAIL;
            }
            return this.feed(player, stack, species);
        }
        if (stack.isEmpty() && this.isOwnedBy(player)) {
            if (player.isSecondaryUseActive()) {
                if (!this.level().isClientSide()) {
                    boolean stay = !this.isStaying();
                    this.entityData.set(DATA_STAYING, stay);
                    this.getNavigation().stop();
                    if (stay) {
                        this.setTarget(null);
                    }
                    player.sendOverlayMessage(Component.translatable(stay ? "message.universe.creature.staying" : "message.universe.creature.following",
                            this.getDisplayName()));
                }
                return InteractionResult.SUCCESS;
            }
            if (Husbandry.rideable(species) && !this.isVehicle() && !player.isPassenger()) {
                if (!this.level().isClientSide()) {
                    this.entityData.set(DATA_STAYING, false);
                    this.setTarget(null);
                    this.getNavigation().stop();
                    player.startRiding(this);
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
        if (this.isTamed() && !hungry) {
            if (hurt) {
                this.eat(player, stack);
                this.heal(Husbandry.FEED_HEAL);
                this.particles(level, ParticleTypes.HAPPY_VILLAGER, 5);
                return InteractionResult.SUCCESS_SERVER;
            }
            player.sendOverlayMessage(Component.translatable("message.universe.creature.not_hungry", this.getDisplayName(),
                    (this.feedCooldown + 19) / 20));
            return InteractionResult.FAIL;
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
        if (this.isTamed()) {
            this.particles(level, ParticleTypes.HEART, 3);
            return InteractionResult.SUCCESS_SERVER;
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
        return !this.isTamed() && super.removeWhenFarAway(distSqr);
    }

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

    /** 个子越大叫声越低。 */
    @Override
    public float getVoicePitch() {
        double size = this.species().size();
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
        double size = this.species().size();
        int extra = (int) Math.floor((size - 0.9) * 2.2 + this.random.nextDouble());
        if (extra > 0) {
            boolean cooked = this.isOnFire();
            this.spawnAtLocation(level, new ItemStack(cooked ? UniverseItems.COOKED_ALIEN_MEAT.get() : UniverseItems.ALIEN_MEAT.get(), extra));
        }
    }
}
