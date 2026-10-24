package io.github.sweetcornna.universe.mecha.fleet;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.core.darkfog.SpaceFlight;
import io.github.sweetcornna.universe.core.mecha.fleet.FleetUnitKind;
import io.github.sweetcornna.universe.core.mecha.fleet.Squadrons;
import io.github.sweetcornna.universe.core.ship.ShipCombat;
import io.github.sweetcornna.universe.darkfog.DarkFogEnemy;
import io.github.sweetcornna.universe.darkfog.entity.DarkFogDamage;
import io.github.sweetcornna.universe.darkfog.space.DarkFogLancer;
import io.github.sweetcornna.universe.mecha.combat.PlasmaShot;
import io.github.sweetcornna.universe.mecha.combat.Strikes;
import io.github.sweetcornna.universe.registry.UniverseSounds;
import io.github.sweetcornna.universe.registry.sound.HostileSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 出动着的舰队单位（ADR-0033 §一）：机甲编队格子里的一件物品在外面的样子。不存档，物品一直在格子里，
 * 召回、下线、区块卸载时实体没了，物品还在；只有被打掉时物品才跟着没了（{@link MechaFleet#onUnitLost}）。
 *
 * <ul>
 *   <li>跟着机甲飞，排在编队的位置上（{@link Squadrons#formation}）；三维自由飞，不受重力，撞墙卡住了就穿过去一会儿。</li>
 *   <li>每几刻挑一次目标：机甲附近的黑雾，机甲正在打的优先（{@link Squadrons#pick}）；离机甲超过拴绳就不追了，回来。</li>
 *   <li>待命、开火的电从主人的机甲核心扣；没电时不开火，飞回机甲身边。</li>
 *   <li>只吃黑雾的伤害（和 {@code /kill}），玩家、别人的炮塔打不着；掉的血写回物品上。</li>
 * </ul>
 */
public class FleetUnit extends Mob {
    private static final EntityDataAccessor<Integer> DATA_OWNER = SynchedEntityData.defineId(FleetUnit.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_TARGET = SynchedEntityData.defineId(FleetUnit.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_SHOTS = SynchedEntityData.defineId(FleetUnit.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_PD_TARGET = SynchedEntityData.defineId(FleetUnit.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_PD_SHOTS = SynchedEntityData.defineId(FleetUnit.class, EntityDataSerializers.INT);
    private static final int THINK_INTERVAL = 10;
    private static final int DEATH_TICKS = 8;
    /** 激光画几刻。 */
    public static final int BEAM_TICKS = 4;
    /** 卡住这么多刻就穿墙飞一会儿。 */
    private static final int STUCK_TICKS = 30;
    private static final int PHASE_TICKS = 20;
    private static final int LASER_COLOR = 0x7FE6FF;
    private static final int PD_COLOR = 0xFFD27A;

    private final FleetUnitKind kind;
    private @Nullable UUID ownerId;
    private @Nullable Player ownerRef;
    private int squadron = -1;
    private int slot = -1;
    private @Nullable LivingEntity target;
    private int thinkCooldown;
    private int fireCooldown;
    private int pdCooldown;
    /** 每架绕着目标错开的角度。 */
    private final double orbit;
    private boolean starving;
    private @Nullable Vec3 stuckFrom;
    private int stuckTicks;
    private int phaseTicks;
    /** GameTest 收窄的交战距离（格）；0 是不收。 */
    private double testRange;
    // ---- 客户端：光束和侧倾
    private int seenShots;
    private int beamTicks;
    private int seenPdShots;
    private int pdBeamTicks;
    private float roll;
    private float rollO;

    public FleetUnit(EntityType<? extends FleetUnit> type, Level level, FleetUnitKind kind) {
        super(type, level);
        this.kind = kind;
        this.setNoGravity(true);
        this.xpReward = 0;
        this.orbit = this.random.nextDouble() * Math.PI * 2;
        this.fireCooldown = this.random.nextInt(Math.max(1, kind.interval()));
    }

    public static AttributeSupplier.Builder createAttributes(FleetUnitKind kind) {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, kind.maxHealth())
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.FLYING_SPEED, kind.speed())
                .add(Attributes.FOLLOW_RANGE, Squadrons.engage(kind.space()))
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
    }

    /** 造一架（还没加进世界）：生命照物品上记的。 */
    static FleetUnit create(ServerLevel level, EntityType<? extends FleetUnit> type, Player owner, int squadron, int slot, int health, Vec3 at) {
        FleetUnit unit = type.create(level, net.minecraft.world.entity.EntitySpawnReason.EVENT);
        if (unit == null) {
            throw new IllegalStateException("can't create " + type);
        }
        unit.ownerId = owner.getUUID();
        unit.ownerRef = owner;
        unit.squadron = squadron;
        unit.slot = slot;
        unit.entityData.set(DATA_OWNER, owner.getId());
        unit.setHealth(Math.clamp(health, 1, unit.kind.maxHealth()));
        unit.snapTo(at.x, at.y, at.z, owner.getYRot(), 0);
        return unit;
    }

    public FleetUnitKind kind() {
        return this.kind;
    }

    public int squadron() {
        return this.squadron;
    }

    public int slot() {
        return this.slot;
    }

    public @Nullable UUID ownerId() {
        return this.ownerId;
    }

    /** 放出它的玩家的实体 ID（客户端用来认自己的单位）。 */
    public int ownerEntityId() {
        return this.entityData.get(DATA_OWNER);
    }

    /** 现在打的目标（服务端，GameTest 和调试用）。 */
    public @Nullable LivingEntity target() {
        return this.target;
    }

    /** 主人：放出来时记下的那个，不在了按 UUID 找。 */
    public @Nullable Player owner() {
        if (this.ownerRef != null && !this.ownerRef.isRemoved()) {
            return this.ownerRef;
        }
        if (this.ownerId != null && !this.level().isClientSide()) {
            this.ownerRef = this.level().getPlayerByUUID(this.ownerId);
            return this.ownerRef;
        }
        return null;
    }

    public boolean starving() {
        return this.starving;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_OWNER, -1);
        builder.define(DATA_TARGET, -1);
        builder.define(DATA_SHOTS, 0);
        builder.define(DATA_PD_TARGET, -1);
        builder.define(DATA_PD_SHOTS, 0);
    }

    // ---- 客户端：光束

    /** 这一刻要画的激光打到谁；没有时是 -1。 */
    public int beamTarget() {
        return this.beamTicks > 0 ? this.entityData.get(DATA_TARGET) : -1;
    }

    /** 激光还要画几刻（越来越淡）。 */
    public int beamTicks() {
        return this.beamTicks;
    }

    public int pointDefenseTarget() {
        return this.pdBeamTicks > 0 ? this.entityData.get(DATA_PD_TARGET) : -1;
    }

    public int pointDefenseTicks() {
        return this.pdBeamTicks;
    }

    public float roll(float partialTicks) {
        return Mth.lerp(partialTicks, this.rollO, this.roll);
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.level().isClientSide()) {
            return;
        }
        int shots = this.entityData.get(DATA_SHOTS);
        if (shots != this.seenShots) {
            this.seenShots = shots;
            this.beamTicks = BEAM_TICKS;
        } else if (this.beamTicks > 0) {
            this.beamTicks--;
        }
        int pd = this.entityData.get(DATA_PD_SHOTS);
        if (pd != this.seenPdShots) {
            this.seenPdShots = pd;
            this.pdBeamTicks = BEAM_TICKS;
        } else if (this.pdBeamTicks > 0) {
            this.pdBeamTicks--;
        }
        float max = this.kind == FleetUnitKind.DESTROYER ? 12 : 35;
        float wanted = Mth.clamp(-Mth.wrapDegrees(this.getYRot() - this.yRotO) * 4, -max, max);
        this.rollO = this.roll;
        this.roll += (wanted - this.roll) * 0.2F;
        // 推进器喷一点光
        if (this.tickCount % 3 == 0 && this.getDeltaMovement().lengthSqr() > 0.01) {
            Vec3 back = this.position().add(0, this.getBbHeight() * 0.5, 0).subtract(this.getDeltaMovement().normalize().scale(this.getBbWidth() * 0.6));
            this.level().addParticle(new DustParticleOptions(0x7FD8FF, this.kind.space() ? 1.2F : 0.5F), back.x, back.y, back.z, 0, 0, 0);
        }
    }

    // ---- 飞

    /** 速度由 AI 每刻算好，这里原样走：不受重力，没有空气阻力。 */
    @Override
    public void travel(Vec3 input) {
        this.move(MoverType.SELF, this.getDeltaMovement());
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        Player owner = this.owner();
        if (owner == null || !owner.isAlive() || owner.level() != level || this.squadron < 0) {
            // 主人不在了、换了维度：物品在格子里，实体收掉
            this.discard();
            return;
        }
        boolean space = this.kind.space();
        double leash = Squadrons.leash(space);
        this.starving = !MechaFleet.draw(owner, this.kind.idleJoules());
        if (this.starving) {
            MechaFleet.starved(owner);
        }
        if (--this.thinkCooldown <= 0) {
            this.think(level, owner);
            this.thinkCooldown = THINK_INTERVAL + this.random.nextInt(3);
        }
        LivingEntity target = this.target;
        if (target != null && (!target.isAlive() || target.level() != level || this.starving
                || Strikes.aimPoint(target).distanceTo(owner.position()) > leash)) {
            target = this.target = null;
        }
        Vec3 home = MechaFleet.formationPoint(owner, this.squadron, this.slot, this.orbit);
        if (this.position().distanceTo(owner.position()) > leash * Squadrons.TELEPORT_FACTOR) {
            // 掉队太远（主人传送、飞得太快）：直接回到队里
            this.snapTo(home.x, home.y, home.z, owner.getYRot(), 0);
            this.setDeltaMovement(Vec3.ZERO);
            this.target = null;
            return;
        }
        Vec3 goal;
        double speed = this.kind.speed();
        if (target != null) {
            double stand = this.reach(this.kind.range()) * (space ? 0.55 : 0.6);
            double lift = space ? Math.sin(this.orbit) * 8 : 2.5 + Math.sin(this.orbit) * 1.5;
            double angle = this.orbit + this.tickCount * (space ? 0.004 : 0.012);
            double[] point = Squadrons.engagePoint(arr(owner.position()), arr(Strikes.aimPoint(target)), stand, angle, lift, leash);
            goal = vec(point);
        } else {
            goal = home;
            speed = Squadrons.catchUp(speed, this.position().distanceTo(home));
        }
        double[] wanted = SpaceFlight.seek(arr(this.position()), arr(goal), speed, space ? 12 : 4);
        this.setDeltaMovement(vec(SpaceFlight.approach(arr(this.getDeltaMovement()), wanted, this.kind.accel() * (target == null ? 1.5 : 1))));
        this.unstick(goal);
        this.face(owner, target);
        if (target != null) {
            this.fire(level, owner, target);
        }
        if (this.kind == FleetUnitKind.DESTROYER && !this.starving) {
            this.pointDefense(level, owner);
        }
    }

    /** 撞在墙上、地形里卡住了：穿过去飞一会儿（跟着机甲进屋、钻洞时）。 */
    private void unstick(Vec3 goal) {
        if (this.phaseTicks > 0) {
            if (--this.phaseTicks == 0) {
                this.noPhysics = false;
            }
            return;
        }
        boolean blocked = (this.horizontalCollision || this.verticalCollision) && this.position().distanceTo(goal) > 2;
        if (!blocked || this.stuckFrom == null || this.position().distanceToSqr(this.stuckFrom) > 1) {
            this.stuckFrom = this.position();
            this.stuckTicks = 0;
            return;
        }
        if (++this.stuckTicks >= STUCK_TICKS) {
            this.noPhysics = true;
            this.phaseTicks = PHASE_TICKS;
            this.stuckTicks = 0;
            this.stuckFrom = null;
        }
    }

    /** 有目标时机头对着目标，没有时顺着飞的方向，停着时和主人朝一个方向。 */
    private void face(Player owner, @Nullable LivingEntity target) {
        double[] look;
        if (target != null) {
            look = arr(Strikes.aimPoint(target).subtract(this.getBoundingBox().getCenter()));
        } else if (this.getDeltaMovement().lengthSqr() > 0.004) {
            look = arr(this.getDeltaMovement());
        } else {
            float yaw = Mth.approachDegrees(this.getYRot(), owner.getYRot(), 6);
            this.setYRot(yaw);
            this.yBodyRot = yaw;
            this.yHeadRot = yaw;
            this.setXRot(this.getXRot() * 0.8F);
            return;
        }
        float yaw = Mth.approachDegrees(this.getYRot(), SpaceFlight.yaw(look), this.kind.space() ? 8 : 20);
        this.setYRot(yaw);
        this.setXRot(Mth.clamp(SpaceFlight.pitch(look), -45, 45));
        this.yBodyRot = yaw;
        this.yHeadRot = yaw;
    }

    /** 想一下打谁：机甲附近的黑雾，机甲正在打的优先，建筑看编队的开关。 */
    private void think(ServerLevel level, Player owner) {
        if (this.starving) {
            this.target = null;
            return;
        }
        boolean space = this.kind.space();
        double engage = this.reach(Squadrons.engage(space));
        Vec3 ownerPos = owner.position();
        List<LivingEntity> found = level.getEntitiesOfClass(LivingEntity.class, owner.getBoundingBox().inflate(engage + 8), Strikes::darkFog);
        if (found.isEmpty()) {
            this.target = null;
            return;
        }
        int recent = MechaFleet.recentTarget(owner);
        Vec3 pos = this.getBoundingBox().getCenter();
        List<Squadrons.Candidate> candidates = new ArrayList<>(found.size());
        for (LivingEntity entity : found) {
            AABB box = entity.getBoundingBox();
            candidates.add(new Squadrons.Candidate(Math.sqrt(box.distanceToSqr(ownerPos)), Math.sqrt(box.distanceToSqr(pos)), Strikes.building(entity),
                    entity.getId() == recent));
        }
        int pick = Squadrons.pick(candidates, engage, MechaFleet.attacksBuildings(owner, this.squadron));
        this.target = pick < 0 ? null : found.get(pick);
    }

    /** 枪口：机身前面一点、偏下。 */
    private Vec3 muzzle() {
        Vec3 center = this.getBoundingBox().getCenter();
        Vec3 forward = Vec3.directionFromRotation(this.getXRot(), this.getYRot());
        return center.add(forward.scale(this.getBbWidth() * 0.5)).add(0, -this.getBbHeight() * 0.15, 0);
    }

    private void fire(ServerLevel level, Player owner, LivingEntity target) {
        if (this.fireCooldown > 0) {
            this.fireCooldown--;
            return;
        }
        Vec3 muzzle = this.muzzle();
        Vec3 aim = Strikes.aimPoint(target);
        double distance = Math.sqrt(target.getBoundingBox().distanceToSqr(muzzle));
        if (distance > this.kind.range() || !Strikes.clear(level, muzzle, aim, this)) {
            return;
        }
        if (!MechaFleet.draw(owner, this.kind.shotJoules())) {
            this.starving = true;
            return;
        }
        this.fireCooldown = this.kind.interval();
        switch (this.kind.weapon()) {
            case LASER, TWIN_LASER -> {
                Strikes.hit(level, target, this.kind.damage(), this, owner);
                this.entityData.set(DATA_TARGET, target.getId());
                this.entityData.set(DATA_SHOTS, this.entityData.get(DATA_SHOTS) + 1);
                level.sendParticles(new DustParticleOptions(LASER_COLOR, 0.8F), aim.x, aim.y, aim.z, 3, 0.15, 0.15, 0.15, 0);
                level.playSound(null, muzzle.x, muzzle.y, muzzle.z, HostileSounds.LASER.get(), SoundSource.PLAYERS,
                        this.kind.space() ? 0.7F : 0.35F, (this.kind.space() ? 1.0F : 1.5F) + this.random.nextFloat() * 0.1F);
            }
            case PLASMA -> {
                double speed = this.kind.projectileSpeed();
                double[] direction = ShipCombat.lead(arr(aim.subtract(muzzle)), arr(target.getDeltaMovement()), speed);
                Vec3 at = muzzle.add(vec(direction).scale(distance));
                PlasmaShot.fire(level, owner, this, muzzle, at, this.kind.damage(), this.kind.splash(), speed,
                        this.kind == FleetUnitKind.DESTROYER ? PlasmaShot.HEAVY : PlasmaShot.FLEET);
                level.playSound(null, muzzle.x, muzzle.y, muzzle.z, HostileSounds.PLASMA.get(), SoundSource.PLAYERS,
                        this.kind == FleetUnitKind.DESTROYER ? 1.0F : 0.4F, this.kind == FleetUnitKind.DESTROYER ? 0.8F : 1.3F);
            }
        }
    }

    /** 驱逐舰的近防激光：打 48 格内最近的枪骑。 */
    private void pointDefense(ServerLevel level, Player owner) {
        if (this.pdCooldown > 0) {
            this.pdCooldown--;
            return;
        }
        Vec3 pos = this.getBoundingBox().getCenter();
        DarkFogLancer best = null;
        double bestDistance = FleetUnitKind.POINT_DEFENSE_RANGE;
        for (DarkFogLancer lancer : level.getEntitiesOfClass(DarkFogLancer.class, this.getBoundingBox().inflate(FleetUnitKind.POINT_DEFENSE_RANGE),
                DarkFogLancer::isAlive)) {
            double distance = Math.sqrt(lancer.getBoundingBox().distanceToSqr(pos));
            if (distance < bestDistance && Strikes.clear(level, pos, Strikes.aimPoint(lancer), this)) {
                bestDistance = distance;
                best = lancer;
            }
        }
        if (best == null || !MechaFleet.draw(owner, FleetUnitKind.POINT_DEFENSE_JOULES)) {
            this.pdCooldown = 5;
            return;
        }
        this.pdCooldown = FleetUnitKind.POINT_DEFENSE_INTERVAL;
        Strikes.hit(level, best, FleetUnitKind.POINT_DEFENSE_DAMAGE, this, owner);
        this.entityData.set(DATA_PD_TARGET, best.getId());
        this.entityData.set(DATA_PD_SHOTS, this.entityData.get(DATA_PD_SHOTS) + 1);
        level.playSound(null, pos.x, pos.y, pos.z, HostileSounds.LASER.get(), SoundSource.PLAYERS, 0.5F, 1.7F);
    }

    public static int laserColor() {
        return LASER_COLOR;
    }

    public static int pointDefenseColor() {
        return PD_COLOR;
    }

    // ---- 受击、死亡

    /** 只吃黑雾的伤害（和 {@code /kill}、掉出世界）：玩家、守卫机、别的炮塔打不着，摔、淹、闷都不算。 */
    static boolean harmedBy(DamageSource source) {
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return true;
        }
        if (source.is(DarkFogDamage.KEY)) {
            return true;
        }
        return source.getEntity() instanceof DarkFogEnemy || source.getDirectEntity() instanceof DarkFogEnemy;
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        if (!harmedBy(source)) {
            return false;
        }
        boolean hurt = super.hurtServer(level, source, damage);
        if (hurt && this.isAlive()) {
            MechaFleet.onUnitHurt(this);
        }
        return hurt;
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (this.level() instanceof ServerLevel level) {
            Vec3 c = this.getBoundingBox().getCenter();
            double spread = this.getBbWidth() * 0.4;
            level.sendParticles(ParticleTypes.EXPLOSION, c.x, c.y, c.z, this.kind == FleetUnitKind.DESTROYER ? 5 : 1, spread, spread, spread, 0);
            level.sendParticles(ParticleTypes.LARGE_SMOKE, c.x, c.y, c.z, 10, spread, spread, spread, 0.03);
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, c.x, c.y, c.z, 16, spread, spread, spread, 0.4);
            level.playSound(null, c.x, c.y, c.z, (this.kind == FleetUnitKind.DESTROYER ? UniverseSounds.EXPLOSION_LARGE : UniverseSounds.EXPLOSION_SMALL).get(),
                    SoundSource.PLAYERS, this.kind.space() ? 1.0F : 0.6F, this.kind.space() ? 0.9F : 1.3F);
            MechaFleet.onUnitLost(this);
        }
    }

    /** 召回：一团光，实体收掉（物品在格子里，生命写回去了）。 */
    void vanish(ServerLevel level) {
        Vec3 c = this.getBoundingBox().getCenter();
        level.sendParticles(ParticleTypes.END_ROD, c.x, c.y, c.z, this.kind.space() ? 12 : 4, this.getBbWidth() * 0.3, 0.2, this.getBbWidth() * 0.3,
                0.05);
        this.discard();
    }

    @Override
    protected void tickDeath() {
        this.deathTime++;
        if (this.deathTime >= DEATH_TICKS && !this.level().isClientSide() && !this.isRemoved()) {
            this.remove(RemovalReason.KILLED);
        }
    }

    // ---- 和原版的交互：不挡准星、不被推、不能拴

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean canBeHitByProjectile() {
        return this.isAlive();
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean isPushedByFluid() {
        return false;
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
    }

    @Override
    protected void checkFallDamage(double ya, boolean onGround, BlockState onState, BlockPos pos) {
    }

    @Override
    public boolean causeFallDamage(double fallDistance, float damageModifier, DamageSource source) {
        return false;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        double range = this.kind.space() ? 320 : 128;
        return distance < range * range;
    }

    @Override
    protected @Nullable SoundEvent getAmbientSound() {
        return null;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return UniverseSounds.IMPACT_ENERGY.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return UniverseSounds.EXPLOSION_SMALL.get();
    }

    @Override
    protected float getSoundVolume() {
        return this.kind.space() ? 1.0F : 0.5F;
    }

    static double[] arr(Vec3 v) {
        return new double[] {v.x, v.y, v.z};
    }

    static Vec3 vec(double[] a) {
        return new Vec3(a[0], a[1], a[2]);
    }

    /** 给 GameTest 用：交战距离和站位收到 {@code range} 格以内，免得打到隔壁测试的黑雾。 */
    public void restrictRange(double range) {
        this.testRange = range;
    }

    private double reach(double range) {
        return this.testRange > 0 ? Math.min(range, this.testRange) : range;
    }

    /** 给 GameTest 用：放出来以后立刻想一次打谁。 */
    public void thinkNow() {
        this.thinkCooldown = 0;
    }

    /** 已经知道是不是同一个主人的单位。 */
    public boolean ownedBy(Entity entity) {
        return this.ownerId != null && this.ownerId.equals(entity.getUUID());
    }
}
