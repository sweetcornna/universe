package io.github.sweetcornna.universe.entity;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.core.ecology.BodyPlan;
import io.github.sweetcornna.universe.core.ecology.CreatureAnatomy;
import io.github.sweetcornna.universe.core.ecology.CreatureSpecies;
import io.github.sweetcornna.universe.core.ecology.Fauna;
import io.github.sweetcornna.universe.core.ecology.Temperament;
import io.github.sweetcornna.universe.exocraft.Submarine;
import io.github.sweetcornna.universe.registry.UniverseItems;
import io.github.sweetcornna.universe.world.planet.Planets;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.RandomSwimmingGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.navigation.WaterBoundPathNavigation;
import net.minecraft.world.entity.animal.fish.WaterAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * 水里的程序化动物（ADR-0026）：鱼、鳗、水母、乌贼。物种在星球的水生物种表里（{@link Fauna#marine()}），实体只同步、保存序号。
 * <p>
 * 鱼成群地游、看见人就躲；鳗住在深水，咬水里的人；水母慢慢漂，碰到的会被蜇；乌贼平时躲人，巨型的被惹急了会反击。
 * 都掉生鱼，个子越大掉得越多。离开水会憋死。
 */
public class AquaticCreature extends WaterAnimal {
    private static final EntityDataAccessor<Integer> DATA_SPECIES = SynchedEntityData.defineId(AquaticCreature.class,
            EntityDataSerializers.INT);
    /** 物种对应的碰撞箱（格，未乘大小），两端共用。 */
    private static final Map<CreatureSpecies, EntityDimensions> DIMENSIONS = new LinkedHashMap<>(32, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<CreatureSpecies, EntityDimensions> eldest) {
            return this.size() > 128;
        }
    };
    /** 游泳的加速度：每刻按"游速属性 × 这个数"加速，水的阻力每刻留下九成（原版的鱼是 0.01）。 */
    private static final float SWIM_ACCELERATION = 0.012F;
    /** 水母的战利品表：生物胶质。 */
    public static final ResourceKey<LootTable> JELLY_LOOT = ResourceKey.create(Registries.LOOT_TABLE, Universe.id("entities/aquatic_creature_jelly"));

    private @Nullable Fauna cachedFauna;
    private int cachedIndex;
    private @Nullable CreatureSpecies cachedSpecies;
    private boolean speciesAssigned;
    /** 客户端：游动的俯仰角（度，正数朝下），渲染用。 */
    private float swimPitch;
    private float swimPitchO;

    public AquaticCreature(EntityType<? extends AquaticCreature> type, Level level) {
        super(type, level);
        this.moveControl = new SwimControl(this);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 6)
                .add(Attributes.MOVEMENT_SPEED, 1.0)
                .add(Attributes.ATTACK_DAMAGE, 2)
                .add(Attributes.FOLLOW_RANGE, 16);
    }

    // ---------------------------------------------------------------- 物种

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_SPECIES, Fauna.MARINE_INDEX);
    }

    /** 这只动物的物种表：服务端按星球维度算，客户端用当前星球的。 */
    public Fauna fauna() {
        if (this.level() instanceof ServerLevel server) {
            Fauna fauna = Planets.fauna(server).orElse(Fauna.FALLBACK);
            return fauna.marine().isEmpty() ? Fauna.FALLBACK : fauna;
        }
        Fauna fauna = AlienCreature.clientFauna();
        return fauna.marine().isEmpty() ? Fauna.FALLBACK : fauna;
    }

    public int speciesIndex() {
        return this.entityData.get(DATA_SPECIES);
    }

    public CreatureSpecies species() {
        Fauna fauna = this.fauna();
        int index = this.speciesIndex();
        CreatureSpecies species = this.cachedSpecies;
        if (species == null || this.cachedFauna != fauna || this.cachedIndex != index) {
            species = fauna.get(Math.max(Fauna.MARINE_INDEX, index));
            this.cachedFauna = fauna;
            this.cachedIndex = index;
            this.cachedSpecies = species;
        }
        return species;
    }

    public BodyPlan plan() {
        return this.species().plan();
    }

    /** 设定物种，并按物种设置生命、速度、攻击和体型。 */
    public void setSpecies(CreatureSpecies species) {
        this.entityData.set(DATA_SPECIES, species.index());
        this.speciesAssigned = true;
        this.setBase(Attributes.MAX_HEALTH, species.health());
        this.setBase(Attributes.MOVEMENT_SPEED, species.speed());
        this.setBase(Attributes.ATTACK_DAMAGE, Math.max(1, species.attack()));
        this.setBase(Attributes.SCALE, species.size());
        this.setHealth(this.getMaxHealth());
        this.refreshDimensions();
        this.registerSpeciesGoals(species);
    }

    private void setBase(Holder<Attribute> attribute, double value) {
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
                return EntityDimensions.scalable(anatomy.hitWidth(), height).withEyeHeight(height * 0.6f);
            });
        }
    }

    @Override
    protected Component getTypeName() {
        CreatureSpecies species = this.species();
        return Component.translatable("entity.universe.species", species.name().zh(), species.name().en());
    }

    @Override
    public @Nullable SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason reason,
            @Nullable SpawnGroupData groupData) {
        if (!this.speciesAssigned) {
            this.setSpecies(this.chooseSpecies(level.getRandom()));
        }
        return super.finalizeSpawn(level, difficulty, reason, groupData);
    }

    /** 刷怪蛋、命令召唤时：按这里的水有多深挑一种，挑不到就随便一种。 */
    private CreatureSpecies chooseSpecies(RandomSource random) {
        Fauna fauna = this.fauna();
        CreatureSpecies picked = fauna.pickMarine(this.waterAbove(12), random.nextDouble());
        List<CreatureSpecies> marine = fauna.marine();
        return picked != null ? picked : marine.get(random.nextInt(marine.size()));
    }

    /** 头顶至少有这么多格水。 */
    private boolean waterAbove(int depth) {
        BlockPos.MutableBlockPos pos = this.blockPosition().mutable();
        for (int i = 0; i < depth; i++) {
            if (!this.level().getFluidState(pos).is(FluidTags.WATER)) {
                return false;
            }
            pos.move(0, 1, 0);
        }
        return true;
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putInt("Species", this.speciesIndex());
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        // /summon 也会走这里（不带数据）：没存物种时留给 finalizeSpawn 按水深挑
        input.getInt("Species").ifPresent(index -> {
            this.entityData.set(DATA_SPECIES, index);
            this.speciesAssigned = true;
            this.registerSpeciesGoals(this.species());
        });
    }

    // ---------------------------------------------------------------- 行为

    @Override
    protected PathNavigation createNavigation(Level level) {
        return new WaterBoundPathNavigation(this, level);
    }

    /** 物种定下来以后才知道是鱼还是鳗：按体型和性情装上 AI。 */
    private void registerSpeciesGoals(CreatureSpecies species) {
        if (this.level().isClientSide()) {
            return;
        }
        this.goalSelector.removeAllGoals(goal -> true);
        this.targetSelector.removeAllGoals(goal -> true);
        BodyPlan plan = species.plan();
        Temperament temperament = species.temperament();
        if (temperament.fights()) {
            this.goalSelector.addGoal(1, new MeleeAttackGoal(this, plan == BodyPlan.EEL ? 1.5 : 1.2, true));
            this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
            if (temperament == Temperament.PREDATOR) {
                // 鳗只咬泡在水里的人
                this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, 20, true, false,
                        (target, level) -> level.getDifficulty() != Difficulty.PEACEFUL && target.isInWater()
                                && !(target.getVehicle() instanceof Submarine)));
            }
        } else if (plan != BodyPlan.JELLY) {
            this.goalSelector.addGoal(0, new PanicGoal(this, 1.6));
        }
        if (temperament == Temperament.SKITTISH) {
            this.goalSelector.addGoal(2, new AvoidEntityGoal<>(this, Player.class, 8.0F, 1.4, 1.7));
        }
        if (plan == BodyPlan.FISH) {
            this.goalSelector.addGoal(3, new SchoolGoal(this));
        }
        this.goalSelector.addGoal(4, new RandomSwimmingGoal(this, 1.0, plan == BodyPlan.JELLY ? 80 : 30));
    }

    @Override
    protected void travelInWater(Vec3 input, double baseGravity, boolean isFalling, double oldY) {
        double length = input.length();
        if (length > 1e-4) {
            this.moveRelative(SWIM_ACCELERATION * (float) length, input.scale(1 / length));
        }
        this.move(MoverType.SELF, this.getDeltaMovement());
        this.setDeltaMovement(this.getDeltaMovement().scale(0.9));
        if (this.getTarget() == null && this.plan() != BodyPlan.JELLY) {
            this.setDeltaMovement(this.getDeltaMovement().add(0.0, -0.004, 0.0));
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide()) {
            // 顺着游的方向抬头、低头；水母一直立着，离了水躺平
            this.swimPitchO = this.swimPitch;
            double dx = this.getX() - this.xo;
            double dy = this.getY() - this.yo;
            double dz = this.getZ() - this.zo;
            double horizontal = Math.sqrt(dx * dx + dz * dz);
            float target = 0;
            if (this.isInWater() && this.plan() != BodyPlan.JELLY && horizontal + Math.abs(dy) > 0.02) {
                target = Mth.clamp((float) (Mth.atan2(-dy, horizontal) * Mth.RAD_TO_DEG), -60, 60);
            }
            this.swimPitch = Mth.lerp(0.15F, this.swimPitch, target);
        }
    }

    /** 渲染用的俯仰角（度，正数朝下）。 */
    public float swimPitch(float partialTick) {
        return Mth.lerp(partialTick, this.swimPitchO, this.swimPitch);
    }

    @Override
    public void aiStep() {
        // 离了水在地上扑腾
        if (!this.isInWater() && this.onGround() && this.verticalCollision) {
            this.setDeltaMovement(this.getDeltaMovement().add((this.random.nextFloat() * 2 - 1) * 0.05F, 0.4F,
                    (this.random.nextFloat() * 2 - 1) * 0.05F));
            this.setOnGround(false);
            this.needsSync = true;
            this.makeSound(SoundEvents.COD_FLOP);
        }
        super.aiStep();
        if (this.level() instanceof ServerLevel level && this.plan() == BodyPlan.JELLY && this.isAlive() && this.tickCount % 5 == 0) {
            this.sting(level);
        }
    }

    /** 水母：碰到它的东西被蜇一下，再中一点毒。同一个目标一秒最多蜇一次（受伤后的无敌时间）。 */
    private void sting(ServerLevel level) {
        float damage = (float) Math.max(1, this.species().attack());
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(0.15),
                e -> e != this && !(e instanceof AquaticCreature other && other.plan() == BodyPlan.JELLY) && EntitySelector.NO_CREATIVE_OR_SPECTATOR.test(e)
                        && !(e.getVehicle() instanceof Submarine))) {
            if (target.getInvulnerableTime() > 10) {
                continue;
            }
            if (target.hurtServer(level, this.damageSources().mobAttack(this), damage)) {
                target.addEffect(new MobEffectInstance(MobEffects.POISON, 60, 0), this);
                this.playSound(SoundEvents.SLIME_ATTACK, 0.5F, 1.6F);
            }
        }
    }

    @Override
    public boolean removeWhenFarAway(double distSqr) {
        return !this.hasCustomName();
    }

    // ---------------------------------------------------------------- 声音和掉落

    @Override
    protected @Nullable SoundEvent getAmbientSound() {
        return null;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return this.plan() == BodyPlan.SQUID ? SoundEvents.SQUID_HURT : SoundEvents.COD_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return this.plan() == BodyPlan.SQUID ? SoundEvents.SQUID_DEATH : SoundEvents.COD_DEATH;
    }

    @Override
    protected SoundEvent getSwimSound() {
        return SoundEvents.FISH_SWIM;
    }

    @Override
    public float getVoicePitch() {
        return (float) (1.2 / Math.sqrt(Math.max(0.3, this.species().size())));
    }

    /** 水母掉生物胶质，用自己的战利品表；别的掉生鱼。 */
    @Override
    protected void dropFromLootTable(ServerLevel level, DamageSource source, boolean playerKilled) {
        if (this.plan() == BodyPlan.JELLY) {
            this.dropFromLootTable(level, source, playerKilled, JELLY_LOOT);
        } else {
            super.dropFromLootTable(level, source, playerKilled);
        }
    }

    /** 战利品表给一条生鱼；大个子另外多掉（巨型乌贼能掉一堆）。水母不多掉。 */
    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean killedByPlayer) {
        super.dropCustomDeathLoot(level, source, killedByPlayer);
        if (this.plan() == BodyPlan.JELLY) {
            return;
        }
        double size = this.species().size();
        int extra = (int) Math.floor((size - 0.8) * 1.6 + this.random.nextDouble());
        if (extra > 0) {
            this.spawnAtLocation(level, new ItemStack(UniverseItems.ALIEN_FISH.get(), extra));
        }
    }

    // ---------------------------------------------------------------- 游泳

    /** 往目标游：转向目标、按游速加速，有上下的分量时往上或往下划。 */
    private static final class SwimControl extends MoveControl<AquaticCreature> {
        SwimControl(AquaticCreature mob) {
            super(mob);
        }

        @Override
        public void tick() {
            if (this.mob.isEyeInFluid(FluidTags.WATER)) {
                // 中性浮力：抵消 travelInWater 里的那点下沉
                this.mob.setDeltaMovement(this.mob.getDeltaMovement().add(0.0, 0.004, 0.0));
            }
            if (this.operation != MoveControl.Operation.MOVE_TO || this.mob.getNavigation().isDone()) {
                this.mob.setSpeed(0.0F);
                return;
            }
            float target = (float) (this.speedModifier * this.mob.getAttributeValue(Attributes.MOVEMENT_SPEED));
            this.mob.setSpeed(Mth.lerp(0.125F, this.mob.getSpeed(), target));
            double dx = this.wantedX - this.mob.getX();
            double dy = this.wantedY - this.mob.getY();
            double dz = this.wantedZ - this.mob.getZ();
            double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (distance > 1e-4 && dy != 0) {
                this.mob.setDeltaMovement(this.mob.getDeltaMovement().add(0.0, this.mob.getSpeed() * (dy / distance) * 0.08, 0.0));
            }
            if (dx != 0 || dz != 0) {
                float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
                this.mob.setYRot(this.rotlerp(this.mob.getYRot(), yaw, this.mob.plan() == BodyPlan.JELLY ? 10.0F : 30.0F));
                this.mob.yBodyRot = this.mob.getYRot();
            }
        }
    }

    /** 鱼群：离同一种的伙伴太远时游回鱼群的中心。 */
    private static final class SchoolGoal extends Goal {
        private final AquaticCreature fish;
        private int cooldown;

        SchoolGoal(AquaticCreature fish) {
            this.fish = fish;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (--this.cooldown > 0) {
                return false;
            }
            this.cooldown = 20 + this.fish.getRandom().nextInt(20);
            Vec3 centre = this.centre();
            return centre != null && centre.distanceToSqr(this.fish.position()) > 16;
        }

        @Override
        public void start() {
            Vec3 centre = this.centre();
            if (centre != null) {
                this.fish.getNavigation().moveTo(centre.x, centre.y, centre.z, 1.2);
            }
        }

        @Override
        public boolean canContinueToUse() {
            return !this.fish.getNavigation().isDone();
        }

        private @Nullable Vec3 centre() {
            int index = this.fish.speciesIndex();
            List<AquaticCreature> mates = this.fish.level().getEntitiesOfClass(AquaticCreature.class, this.fish.getBoundingBox().inflate(12),
                    other -> other != this.fish && other.speciesIndex() == index);
            if (mates.isEmpty()) {
                return null;
            }
            double x = 0;
            double y = 0;
            double z = 0;
            for (AquaticCreature mate : mates) {
                x += mate.getX();
                y += mate.getY();
                z += mate.getZ();
            }
            return new Vec3(x / mates.size(), y / mates.size(), z / mates.size());
        }
    }
}
