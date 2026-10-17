package io.github.sweetcornna.universe.entity;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.core.ecology.BodyPlan;
import io.github.sweetcornna.universe.core.ecology.CreatureAnatomy;
import io.github.sweetcornna.universe.core.ecology.CreatureSpecies;
import io.github.sweetcornna.universe.core.ecology.Fauna;
import io.github.sweetcornna.universe.core.ecology.Temperament;
import io.github.sweetcornna.universe.core.terrain.BiomeKind;
import io.github.sweetcornna.universe.registry.UniverseItems;
import io.github.sweetcornna.universe.world.planet.Planets;
import io.github.sweetcornna.universe.world.planet.gen.PlanetBiomes;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
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
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * 星球上的程序化动物（玩法设计 §8）。外观、属性、性情都由 {@link CreatureSpecies} 决定，
 * 实体本身只同步、保存物种在星球动物表里的序号，两端各自从星球种子算出物种。
 */
public class AlienCreature extends PathfinderMob {
    private static final EntityDataAccessor<Integer> DATA_SPECIES = SynchedEntityData.defineId(AlienCreature.class,
            EntityDataSerializers.INT);
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

    private @Nullable Fauna cachedFauna;
    private int cachedIndex;
    private @Nullable CreatureSpecies cachedSpecies;
    private boolean speciesAssigned;
    private int alarm;
    private @Nullable LivingEntity threat;

    public AlienCreature(EntityType<? extends AlienCreature> type, Level level) {
        super(type, level);
    }

    public static void setClientFauna(Supplier<Fauna> supplier) {
        clientFauna = supplier;
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
    }

    /** 这个世界的动物表：服务端按星球维度算，客户端用当前星球的。 */
    public Fauna fauna() {
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
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.entityData.set(DATA_SPECIES, input.getIntOr("Species", 0));
        this.speciesAssigned = true;
    }

    // ---------------------------------------------------------------- 行为

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new HerdPanicGoal(this, 1.7));
        this.goalSelector.addGoal(2, when(this::fights, new MeleeAttackGoal(this, 1.3, true)));
        this.goalSelector.addGoal(3, when(() -> this.temperament() == Temperament.SKITTISH,
                new AvoidEntityGoal<>(this, Player.class, 12, 1.2, 1.7)));
        // 胆小的动物也躲着别的物种里的捕食者
        this.goalSelector.addGoal(3, when(() -> !this.fights(), new AvoidEntityGoal<>(this, AlienCreature.class,
                other -> other instanceof AlienCreature c && c.temperament() == Temperament.PREDATOR
                        && c.speciesIndex() != this.speciesIndex(), 10, 1.2, 1.6, other -> true)));
        this.registerMovementGoals();
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8));
        this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));

        this.targetSelector.addGoal(1, when(this::fights, new HurtByTargetGoal(this) {
            @Override
            protected void alertOther(Mob other, LivingEntity hurtByMob) {
                // 只叫上同一物种的同伴
                if (other instanceof AlienCreature c && c.speciesIndex() == AlienCreature.this.speciesIndex()) {
                    super.alertOther(other, hurtByMob);
                }
            }
        }.setAlertOthers()));
        this.targetSelector.addGoal(2, when(() -> this.temperament() == Temperament.PREDATOR,
                new NearestAttackableTargetGoal<>(this, Player.class, 10, true, false,
                        (target, level) -> level.getDifficulty() != Difficulty.PEACEFUL)));
        this.targetSelector.addGoal(3, when(() -> this.temperament() == Temperament.PREDATOR,
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
