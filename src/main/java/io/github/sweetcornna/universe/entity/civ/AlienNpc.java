package io.github.sweetcornna.universe.entity.civ;

import java.util.EnumSet;

import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.civ.Civ;
import io.github.sweetcornna.universe.civ.Dialogues;
import io.github.sweetcornna.universe.core.civ.NpcRole;
import io.github.sweetcornna.universe.core.civ.Race;
import io.github.sweetcornna.universe.core.civ.Standing;
import io.github.sweetcornna.universe.core.procgen.NameGenerator;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * 外星文明的 NPC（ADR-0027 §三）：空间站、交易站、聚落里的访客、技术商、公会使者。不存档，由 {@code civ.NpcSpawner} 在玩家走近时
 * 按站位的种子放出来，走远了收走。站着看最近的玩家，偶尔转头、比个手势；右键打开对话。
 *
 * <p>打不死：挨打掉这个种族的声望（两秒内只算一次，连发的武器一秒打十下），往后躲几步再走回原地。
 */
public class AlienNpc extends PathfinderMob {
    /** 客户端：比个手势。 */
    private static final byte EVENT_GESTURE = 110;
    public static final int GESTURE_TICKS = 24;
    private static final int HIT_COOLDOWN = 40;
    private static final int FLEE_TICKS = 50;
    private static final EntityDataAccessor<Byte> DATA_RACE = SynchedEntityData.defineId(AlienNpc.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Byte> DATA_ROLE = SynchedEntityData.defineId(AlienNpc.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Long> DATA_SEED = SynchedEntityData.defineId(AlienNpc.class, EntityDataSerializers.LONG);
    private static final EntityDimensions TRADER = EntityDimensions.scalable(0.6F, 1.3F).withEyeHeight(1.15F);
    private static final EntityDimensions SCHOLAR = EntityDimensions.scalable(0.6F, 2.0F).withEyeHeight(1.75F);
    private static final EntityDimensions WARDEN = EntityDimensions.scalable(0.85F, 2.2F).withEyeHeight(1.9F);

    // ---- 服务端：管理器给的站位和键（每天一次的记录按键记）
    private String key = "";
    private NameGenerator.Name name = new NameGenerator.Name("", "");
    private @Nullable Vec3 home;
    private float homeYaw;
    private long lastHit = -HIT_COOLDOWN;
    private int fleeTicks;
    private @Nullable Vec3 fleeFrom;
    // ---- 客户端
    private int gestureTicks;

    public AlienNpc(EntityType<? extends AlienNpc> type, Level level) {
        super(type, level);
        this.setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20)
                .add(Attributes.MOVEMENT_SPEED, 0.25)
                .add(Attributes.FOLLOW_RANGE, 16);
    }

    /** 管理器放出来之前调：种族、角色、种子、名字和站位。 */
    public void setup(Race race, NpcRole role, long seed, String key, NameGenerator.Name name, Vec3 home, float yaw) {
        this.entityData.set(DATA_RACE, (byte) race.ordinal());
        this.entityData.set(DATA_ROLE, (byte) role.ordinal());
        this.entityData.set(DATA_SEED, seed);
        this.key = key;
        this.name = name;
        this.home = home;
        this.homeYaw = yaw;
        this.refreshDimensions();
        this.setCustomName(label(race, role, name));
        this.setCustomNameVisible(true);
        this.snapTo(home.x, home.y, home.z, yaw, 0);
        this.setYHeadRot(yaw);
        this.setYBodyRot(yaw);
    }

    /** 头顶的名字："某某 · 访客"，名字是种族的颜色。 */
    public static MutableComponent label(Race race, NpcRole role, NameGenerator.Name name) {
        return Component.translatable("entity.universe.alien_npc.label",
                Component.translatable("universe.name", name.zh(), name.en()).withColor(race.color()),
                Component.translatable("civ.universe.role." + role.id()).withStyle(ChatFormatting.GRAY));
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_RACE, (byte) 0);
        builder.define(DATA_ROLE, (byte) 0);
        builder.define(DATA_SEED, 0L);
    }

    public Race race() {
        Race[] races = Race.values();
        return races[Math.floorMod(this.entityData.get(DATA_RACE), races.length)];
    }

    public NpcRole role() {
        NpcRole[] roles = NpcRole.values();
        return roles[Math.floorMod(this.entityData.get(DATA_ROLE), roles.length)];
    }

    /** 这个人的种子（站位加日期；技术商、公会使者不换人，没有日期）。客户端拿它定个体的花色。 */
    public long seed() {
        return this.entityData.get(DATA_SEED);
    }

    /** 服务端：站位的键，每天一次的记录记成"动作:键"。 */
    public String key() {
        return this.key;
    }

    /** 服务端：名字（双语）。 */
    public NameGenerator.Name npcName() {
        return this.name;
    }

    /** 名字加角色，提示里用。 */
    public Component label() {
        return label(this.race(), this.role(), this.name);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) {
        super.onSyncedDataUpdated(accessor);
        if (DATA_RACE.equals(accessor)) {
            this.refreshDimensions();
        }
    }

    @Override
    protected EntityDimensions getDefaultDimensions(Pose pose) {
        return switch (this.race()) {
            case TRADER -> TRADER;
            case SCHOLAR -> SCHOLAR;
            case WARDEN -> WARDEN;
        };
    }

    // ---- 行为

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new BackOffGoal());
        this.goalSelector.addGoal(2, new ReturnHomeGoal());
        this.goalSelector.addGoal(3, new LookAtPlayerGoal(this, Player.class, 8, 0.6F));
        this.goalSelector.addGoal(4, new RandomLookAroundGoal(this));
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide()) {
            if (this.gestureTicks > 0) {
                this.gestureTicks--;
            }
            return;
        }
        if (this.fleeTicks > 0) {
            this.fleeTicks--;
        }
        // 偶尔比个手势：旁边有人时才比
        if (this.random.nextInt(320) == 0 && this.level().getNearestPlayer(this, 6) != null) {
            this.gesture();
        }
    }

    /** 比个手势（右键对话时也比一下）。 */
    public void gesture() {
        this.level().broadcastEntityEvent(this, EVENT_GESTURE);
    }

    @Override
    public void handleEntityEvent(byte id) {
        if (id == EVENT_GESTURE) {
            this.gestureTicks = GESTURE_TICKS;
        } else {
            super.handleEntityEvent(id);
        }
    }

    /** 客户端：手势还剩几刻（{@link #GESTURE_TICKS} 到 0）。 */
    public float gesture(float partialTicks) {
        return this.gestureTicks > 0 ? Math.max(0, this.gestureTicks - partialTicks) : 0;
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            // /kill、掉出世界
            return super.hurtServer(level, source, damage);
        }
        Entity attacker = source.getEntity();
        if (attacker instanceof ServerPlayer player && !player.isSpectator()) {
            long now = level.getGameTime();
            if (now - this.lastHit >= HIT_COOLDOWN) {
                this.lastHit = now;
                Civ.changeStanding(player, this.race(), Standing.ATTACK);
                // GameTest 的模拟玩家没有连接
                if (player.connection != null) {
                    player.sendOverlayMessage(Component.translatable("message.universe.npc.hit", this.label(),
                            Component.translatable("civ.universe.race." + this.race().id()), Standing.ATTACK).withStyle(ChatFormatting.RED));
                }
            }
            this.fleeFrom = attacker.position();
            this.fleeTicks = FLEE_TICKS;
            Vec3 away = this.position().subtract(attacker.position()).multiply(1, 0, 1);
            if (away.lengthSqr() > 1.0E-4) {
                away = away.normalize().scale(0.35);
                this.push(away.x, 0.12, away.z);
                this.markHurt();
            }
            this.playSound(SoundEvents.PLAYER_ATTACK_NODAMAGE, 1, 1);
        }
        return false;
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }
        if (player instanceof ServerPlayer serverPlayer) {
            Dialogues.open(serverPlayer, this);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void doPush(Entity entity) {
    }

    @Override
    public boolean canBeLeashed() {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distSqr) {
        return false;
    }

    @Override
    public void checkDespawn() {
        // 由 NpcSpawner 收走
    }

    // ---- 目标

    /** 挨了打：往远离打人的方向躲几步。 */
    private final class BackOffGoal extends Goal {
        BackOffGoal() {
            this.setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return AlienNpc.this.fleeTicks > 0 && AlienNpc.this.fleeFrom != null;
        }

        @Override
        public void start() {
            Vec3 target = DefaultRandomPos.getPosAway(AlienNpc.this, 5, 2, AlienNpc.this.fleeFrom);
            if (target != null) {
                AlienNpc.this.getNavigation().moveTo(target.x, target.y, target.z, 1.3);
            }
        }

        @Override
        public boolean canContinueToUse() {
            return AlienNpc.this.fleeTicks > 0 && !AlienNpc.this.getNavigation().isDone();
        }
    }

    /** 不在站位上（躲开了、被挤开了）就走回去，到了以后转回原来的朝向。 */
    private final class ReturnHomeGoal extends Goal {
        ReturnHomeGoal() {
            this.setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            Vec3 home = AlienNpc.this.home;
            return home != null && AlienNpc.this.fleeTicks <= 0 && AlienNpc.this.position().distanceToSqr(home) > 1.0;
        }

        @Override
        public void start() {
            Vec3 home = AlienNpc.this.home;
            if (home != null) {
                AlienNpc.this.getNavigation().moveTo(home.x, home.y, home.z, 0.7);
            }
        }

        @Override
        public boolean canContinueToUse() {
            return AlienNpc.this.fleeTicks <= 0 && !AlienNpc.this.getNavigation().isDone();
        }

        @Override
        public void stop() {
            Vec3 home = AlienNpc.this.home;
            if (home != null && AlienNpc.this.position().distanceToSqr(home) < 2.0) {
                AlienNpc.this.setYBodyRot(AlienNpc.this.homeYaw);
            }
        }
    }
}
