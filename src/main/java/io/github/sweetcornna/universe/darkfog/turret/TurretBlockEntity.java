package io.github.sweetcornna.universe.darkfog.turret;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.core.darkfog.AmmoKind;
import io.github.sweetcornna.universe.core.darkfog.Ballistics;
import io.github.sweetcornna.universe.core.darkfog.TurretKind;
import io.github.sweetcornna.universe.core.darkfog.TurretTargeting;
import io.github.sweetcornna.universe.core.power.PowerSystem;
import io.github.sweetcornna.universe.darkfog.DarkFogEnemy;
import io.github.sweetcornna.universe.darkfog.Taunts;
import io.github.sweetcornna.universe.factory.MachineBlockEntity;
import io.github.sweetcornna.universe.factory.MachineMenu;
import io.github.sweetcornna.universe.factory.power.LevelPower;
import io.github.sweetcornna.universe.registry.UniverseMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

/**
 * 炮塔（ADR-0028 §五）：接电网，有一格弹药（激光炮塔没有）。每 10 刻在射程里找一次黑雾，先打正在打设施的，再打最近的；
 * 交战时按交战的用电算，供电不足按满足率放慢射速，没电不打。
 *
 * <p>高斯炮塔、激光炮塔直接命中（要视线）；聚爆炮打出走弧线的炮弹，导弹炮塔打出会追的导弹。开火和换目标用方块事件告诉
 * 附近的客户端，客户端据此转炮塔头、画曳光和激光。
 */
public class TurretBlockEntity extends MachineBlockEntity {
    public static final int AMMO_SLOT = 0;
    /** 界面数据：状态（{@link #SEARCHING}、{@link #ENGAGING}、{@link #NO_AMMO}）。 */
    public static final int STATUS = BASE_DATA;
    /** 界面数据：装着的弹药还能打几发。 */
    public static final int ROUNDS = BASE_DATA + 1;
    /** 界面数据：装着哪种弹药（{@link AmmoKind} 的序号加一，0 是没装）。 */
    public static final int LOADED = BASE_DATA + 2;
    /** 界面数据：击毁数（两格）。 */
    public static final int KILLS = BASE_DATA + 3;
    public static final int DATA_COUNT = BASE_DATA + 5;
    public static final int SEARCHING = 0;
    public static final int ENGAGING = 1;
    public static final int NO_AMMO = 2;
    /** 方块事件：换了目标（参数是目标的实体编号，-1 是没有）。 */
    static final int EVENT_AIM = 1;
    /** 方块事件：开了一炮（参数是目标的实体编号）。 */
    static final int EVENT_FIRE = 2;
    /** 炮塔头的转轴：方块里 11 像素高。 */
    public static final double PIVOT_Y = 11 / 16.0;
    /** 炮口离转轴多远。 */
    public static final double MUZZLE = 0.75;
    private static final int SEARCH_INTERVAL = 10;
    /** 视线检查最多查几个最近的候选。 */
    private static final int MAX_SIGHT_CHECKS = 16;

    private final TurretKind kind;
    private int rounds;
    private @Nullable AmmoKind loaded;
    private long kills;
    private double charge;
    private int searchCooldown;
    private int targetId = -1;
    private int status = SEARCHING;
    // ---- 客户端
    private int clientTarget = -1;
    private long lastShot = Long.MIN_VALUE;
    private float yaw;
    private float pitch = 5;
    private float prevYaw;
    private float prevPitch = 5;

    public TurretBlockEntity(BlockPos pos, BlockState state) {
        super(((TurretBlock) state.getBlock()).blockEntityType(), pos, state, ((TurretBlock) state.getBlock()).kind().usesAmmo() ? 1 : 0);
        this.kind = ((TurretBlock) state.getBlock()).kind();
    }

    public TurretKind kind() {
        return this.kind;
    }

    public int rounds() {
        return this.rounds;
    }

    public @Nullable AmmoKind loaded() {
        return this.loaded;
    }

    public long kills() {
        return this.kills;
    }

    /** 现在的目标（服务端）。 */
    public @Nullable Entity target() {
        return this.level == null || this.targetId < 0 ? null : this.level.getEntity(this.targetId);
    }

    // ---- 电

    @Override
    public long demand() {
        return this.targetId >= 0 ? this.kind.activePower() : this.kind.idlePower();
    }

    // ---- 物品栏

    @Override
    protected boolean isInput(int slot) {
        return slot == AMMO_SLOT && this.kind.usesAmmo();
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return this.isInput(slot) && Turrets.ammoOf(stack).filter(ammo -> ammo.turret() == this.kind).isPresent();
    }

    // ---- 干活

    @Override
    protected boolean tickMachine() {
        if (!(this.level instanceof ServerLevel level)) {
            return false;
        }
        Mob target = this.currentTarget(level);
        if (--this.searchCooldown <= 0 || target == null && level.getGameTime() % 4 == 0) {
            this.searchCooldown = SEARCH_INTERVAL;
            target = this.search(level, target);
        }
        this.setTarget(level, target);
        if (target == null) {
            this.status = SEARCHING;
            // 没有目标时攒着一发，敌人进了射程马上能打
            this.charge = Math.min(this.kind.ticksPerShot(), this.charge + this.satisfaction);
            return false;
        }
        boolean powered = this.satisfaction > 0 && this.connected();
        // 没电时不装弹，免得白白拆开一箱
        if (this.kind.usesAmmo() && this.rounds <= 0 && !(powered ? this.reload() : this.ammoInSlot())) {
            this.status = NO_AMMO;
            return false;
        }
        this.status = ENGAGING;
        if (!powered) {
            return false;
        }
        this.charge += this.satisfaction;
        if (this.charge >= this.kind.ticksPerShot()) {
            this.charge -= this.kind.ticksPerShot();
            this.fire(level, target);
        }
        return true;
    }

    /** 现在的目标还在不在、能不能接着打（服务端每刻查一下死没死、走没走远）。 */
    private @Nullable Mob currentTarget(ServerLevel level) {
        if (this.targetId < 0 || !(level.getEntity(this.targetId) instanceof Mob mob) || !mob.isAlive() || !(mob instanceof DarkFogEnemy)) {
            return null;
        }
        double reach = this.reach(level, mob);
        return reach > 0 ? mob : null;
    }

    private boolean ammoInSlot() {
        return Turrets.ammoOf(this.getItem(AMMO_SLOT)).filter(kind -> kind.turret() == this.kind).isPresent();
    }

    /** 装一个弹药物品：没有能用的就返回 false。 */
    private boolean reload() {
        ItemStack stack = this.getItem(AMMO_SLOT);
        AmmoKind ammo = Turrets.ammoOf(stack).filter(kind -> kind.turret() == this.kind).orElse(null);
        if (ammo == null) {
            return false;
        }
        this.removeItem(AMMO_SLOT, 1);
        this.loaded = ammo;
        this.rounds = ammo.rounds();
        this.setChanged();
        return true;
    }

    /** 炮塔头的转轴在世界里的位置。 */
    public Vec3 pivot() {
        return Vec3.atLowerCornerOf(this.worldPosition).add(0.5, PIVOT_Y, 0.5);
    }

    /** 瞄准一个实体的哪里：碰撞箱的中心。 */
    static Vec3 aimPoint(Entity entity) {
        return entity.getBoundingBox().getCenter();
    }

    /**
     * 这个目标能不能打：在射程（导弹炮塔加上同一电网里信号塔的范围）里、看得见。
     *
     * @return 离炮塔多远；打不了是 -1
     */
    private double reach(ServerLevel level, Mob mob) {
        Vec3 pivot = this.pivot();
        Vec3 aim = aimPoint(mob);
        double distance = aim.distanceTo(pivot);
        if (this.kind.groundOnly() && !((DarkFogEnemy) mob).grounded()) {
            return -1;
        }
        if (distance <= this.kind.range()) {
            return visible(level, pivot, aim) ? distance : -1;
        }
        if (this.kind == TurretKind.MISSILE) {
            for (BlockPos tower : this.signalTowers(level)) {
                Vec3 top = Taunts.center(tower);
                if (aim.distanceTo(top) <= Taunts.RANGE && visible(level, top, aim)) {
                    return distance;
                }
            }
        }
        return -1;
    }

    /** 同一个电网里通电的信号塔（导弹炮塔借它们的范围）。 */
    private List<BlockPos> signalTowers(ServerLevel level) {
        List<BlockPos> towers = Taunts.signalTowers(level);
        LevelPower power = LevelPower.existing(level);
        if (towers.isEmpty() || power == null) {
            return List.of();
        }
        PowerSystem.Grid grid = power.gridOf(this.worldPosition);
        if (grid == null) {
            return List.of();
        }
        List<BlockPos> same = new ArrayList<>();
        for (BlockPos tower : towers) {
            PowerSystem.Grid other = power.gridOf(tower);
            if (other == null) {
                other = power.gridOfTower(tower);
            }
            if (other == grid) {
                same.add(tower);
            }
        }
        return same;
    }

    /** 从 {@code from} 能不能看到 {@code to}：起点往外挪出炮塔自己的方块，只看会挡住的方块。 */
    private static boolean visible(Level level, Vec3 from, Vec3 to) {
        Vec3 direction = to.subtract(from);
        double length = direction.length();
        if (length < 1) {
            return true;
        }
        Vec3 start = from.add(direction.scale(0.9 / length));
        BlockHitResult hit = level.clip(new ClipContext(start, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()));
        return hit.getType() == HitResult.Type.MISS;
    }

    /** 在射程里找一个目标。 */
    private @Nullable Mob search(ServerLevel level, @Nullable Mob current) {
        Vec3 pivot = this.pivot();
        Set<Mob> found = new LinkedHashSet<>(enemies(level, new AABB(pivot, pivot).inflate(this.kind.range())));
        if (this.kind == TurretKind.MISSILE) {
            for (BlockPos tower : this.signalTowers(level)) {
                Vec3 top = Taunts.center(tower);
                found.addAll(enemies(level, new AABB(top, top).inflate(Taunts.RANGE)));
            }
        }
        if (found.isEmpty()) {
            return null;
        }
        List<Mob> mobs = new ArrayList<>(found);
        mobs.sort(Comparator.comparingDouble(mob -> aimPoint(mob).distanceToSqr(pivot)));
        List<TurretTargeting.Candidate> candidates = new ArrayList<>();
        List<Mob> checked = new ArrayList<>();
        for (Mob mob : mobs) {
            if (checked.size() >= MAX_SIGHT_CHECKS) {
                break;
            }
            DarkFogEnemy enemy = (DarkFogEnemy) mob;
            double reach = this.reach(level, mob);
            candidates.add(new TurretTargeting.Candidate(Math.max(0, reach), reach >= 0, reach >= 0, enemy.grounded(), enemy.attackingFacility(),
                    mob == current));
            checked.add(mob);
        }
        int chosen = TurretTargeting.choose(this.kind, candidates);
        return chosen < 0 ? null : checked.get(chosen);
    }

    static List<Mob> enemies(Level level, AABB box) {
        return level.getEntitiesOfClass(Mob.class, box, mob -> mob instanceof DarkFogEnemy && mob.isAlive() && !mob.isRemoved());
    }

    private void setTarget(ServerLevel level, @Nullable Mob target) {
        int id = target == null ? -1 : target.getId();
        if (id != this.targetId) {
            this.targetId = id;
            level.blockEvent(this.worldPosition, this.getBlockState().getBlock(), EVENT_AIM, id);
        }
    }

    private void fire(ServerLevel level, Mob target) {
        Vec3 pivot = this.pivot();
        Vec3 aim = aimPoint(target);
        Vec3 direction = aim.subtract(pivot).normalize();
        Vec3 muzzle = pivot.add(direction.scale(MUZZLE));
        switch (this.kind) {
            case GAUSS -> {
                this.rounds--;
                AmmoKind ammo = this.loaded == null ? AmmoKind.MAGNUM : this.loaded;
                if (level.getRandom().nextDouble() < this.kind.hitChance(aim.distanceTo(pivot))) {
                    this.hit(level, target, ammo.damage());
                }
                level.playSound(null, muzzle.x, muzzle.y, muzzle.z, SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.BLOCKS, 0.45F,
                        1.7F + level.getRandom().nextFloat() * 0.3F);
            }
            case LASER -> {
                this.hit(level, target, TurretKind.LASER_DAMAGE);
                level.playSound(null, muzzle.x, muzzle.y, muzzle.z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 0.5F, 2.0F);
            }
            case IMPLOSION -> {
                this.rounds--;
                AmmoKind ammo = this.loaded == null ? AmmoKind.SHELL : this.loaded;
                Vec3 lead = aim.add(target.getDeltaMovement().multiply(1, 0, 1).scale(10));
                TurretShell shell = TurretShell.launch(level, this.worldPosition, muzzle, lead, ammo);
                level.addFreshEntity(shell);
                level.playSound(null, muzzle.x, muzzle.y, muzzle.z, SoundEvents.BLAZE_SHOOT, SoundSource.BLOCKS, 0.8F, 0.55F);
            }
            case MISSILE -> {
                this.rounds--;
                AmmoKind ammo = this.loaded == null ? AmmoKind.MISSILE : this.loaded;
                TurretMissile missile = TurretMissile.launch(level, this.worldPosition, pivot.add(0, 0.4, 0), target, ammo);
                level.addFreshEntity(missile);
                level.playSound(null, muzzle.x, muzzle.y, muzzle.z, SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.BLOCKS, 0.9F, 0.8F);
            }
        }
        level.blockEvent(this.worldPosition, this.getBlockState().getBlock(), EVENT_FIRE, target.getId());
        if (this.kind.usesAmmo() && this.rounds <= 0) {
            this.loaded = null;
            this.rounds = 0;
        }
        this.setChanged();
    }

    /** 直接命中：只伤黑雾，打死了记一个。 */
    private void hit(ServerLevel level, Mob target, float damage) {
        if (Turrets.damage(level, target, damage) && !target.isAlive()) {
            this.kills++;
        }
    }

    /** 炮弹、导弹打死了东西，记在开火的炮塔上。 */
    void creditKill() {
        this.kills++;
        this.setChanged();
    }

    // ---- 界面

    @Override
    protected int dataCount() {
        return DATA_COUNT;
    }

    @Override
    protected int dataValue(int index) {
        return switch (index) {
            case STATUS -> this.status;
            case ROUNDS -> this.rounds;
            case LOADED -> this.loaded == null ? 0 : this.loaded.ordinal() + 1;
            case KILLS -> MachineMenu.low(this.kills);
            case KILLS + 1 -> MachineMenu.high(this.kills);
            default -> super.dataValue(index);
        };
    }

    @Override
    protected MachineMenu.Type menuType() {
        return switch (this.kind) {
            case GAUSS -> UniverseMenus.GAUSS_TURRET;
            case LASER -> UniverseMenus.LASER_TURRET;
            case IMPLOSION -> UniverseMenus.IMPLOSION_CANNON;
            case MISSILE -> UniverseMenus.MISSILE_TURRET;
        };
    }

    // ---- 存档和同步

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.rounds = input.getIntOr("Rounds", 0);
        this.loaded = AmmoKind.byId(input.getStringOr("Loaded", "")).filter(ammo -> ammo.turret() == this.kind).orElse(null);
        this.kills = input.getLongOr("Kills", 0);
        if (this.loaded == null) {
            this.rounds = 0;
        }
        this.clientTarget = input.getIntOr("Target", -1);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("Rounds", this.rounds);
        if (this.loaded != null) {
            output.putString("Loaded", this.loaded.id());
        }
        output.putLong("Kills", this.kills);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Target", this.targetId);
        return tag;
    }

    @Override
    public boolean triggerEvent(int event, int param) {
        if (event != EVENT_AIM && event != EVENT_FIRE) {
            return super.triggerEvent(event, param);
        }
        if (this.level != null && this.level.isClientSide()) {
            this.clientTarget = param;
            if (event == EVENT_FIRE) {
                this.lastShot = this.level.getGameTime();
                this.fireEffects(this.level, param);
            }
        }
        return true;
    }

    // ---- 客户端

    public int clientTarget() {
        return this.clientTarget;
    }

    public long lastShot() {
        return this.lastShot;
    }

    public float yaw(float partialTicks) {
        return Mth.rotLerp(partialTicks, this.prevYaw, this.yaw);
    }

    public float pitch(float partialTicks) {
        return Mth.lerp(partialTicks, this.prevPitch, this.pitch);
    }

    /** 炮塔头每刻转向目标，转得最快的是高斯炮塔；没目标时慢慢转着巡视。 */
    void clientTick() {
        this.prevYaw = this.yaw;
        this.prevPitch = this.pitch;
        Entity target = this.level == null || this.clientTarget < 0 ? null : this.level.getEntity(this.clientTarget);
        float speed = switch (this.kind) {
            case GAUSS -> 22;
            case LASER -> 26;
            case IMPLOSION -> 9;
            case MISSILE -> 14;
        };
        if (target == null || !target.isAlive()) {
            this.yaw = Mth.wrapDegrees(this.yaw + 0.8F);
            this.pitch += Mth.clamp(5 - this.pitch, -2, 2);
            return;
        }
        Vec3 aim = aimPoint(target).subtract(this.pivot());
        float wantYaw = (float) Math.toDegrees(Math.atan2(aim.x, aim.z));
        float wantPitch = (float) Math.toDegrees(Math.atan2(aim.y, Math.hypot(aim.x, aim.z)));
        if (this.kind == TurretKind.IMPLOSION) {
            // 炮弹走弧线，炮管抬得高一些
            wantPitch = Math.max(wantPitch + 25, 20);
        } else if (this.kind == TurretKind.MISSILE) {
            wantPitch = Math.max(wantPitch + 15, 15);
        }
        wantPitch = Mth.clamp(wantPitch, -20, 75);
        this.yaw = Mth.wrapDegrees(this.yaw + Mth.clamp(Mth.wrapDegrees(wantYaw - this.yaw), -speed, speed));
        this.pitch += Mth.clamp(wantPitch - this.pitch, -speed, speed);
    }

    /** 开火时客户端的粒子：高斯炮的曳光、激光打中的火花、炮口的烟。 */
    private void fireEffects(Level level, int targetId) {
        Entity target = level.getEntity(targetId);
        Vec3 pivot = this.pivot();
        Vec3 aim = target == null ? pivot.add(0, 0, 1) : aimPoint(target);
        Vec3 direction = aim.subtract(pivot).normalize();
        Vec3 muzzle = pivot.add(direction.scale(MUZZLE));
        switch (this.kind) {
            case GAUSS -> {
                DustParticleOptions tracer = new DustParticleOptions(0xFFD27A, 0.55F);
                double length = aim.distanceTo(muzzle);
                for (double d = 0.3; d < length; d += 0.7) {
                    Vec3 p = muzzle.add(direction.scale(d));
                    level.addParticle(tracer, p.x, p.y, p.z, 0, 0, 0);
                }
                level.addParticle(ParticleTypes.ELECTRIC_SPARK, aim.x, aim.y, aim.z, 0, 0, 0);
            }
            case LASER -> {
                for (int i = 0; i < 3; i++) {
                    level.addParticle(ParticleTypes.ELECTRIC_SPARK, aim.x, aim.y, aim.z, (level.getRandom().nextDouble() - 0.5) * 0.2,
                            level.getRandom().nextDouble() * 0.15, (level.getRandom().nextDouble() - 0.5) * 0.2);
                }
            }
            case IMPLOSION -> {
                for (int i = 0; i < 6; i++) {
                    level.addParticle(ParticleTypes.LARGE_SMOKE, muzzle.x, muzzle.y + 0.3, muzzle.z, direction.x * 0.05, 0.03, direction.z * 0.05);
                }
                for (int i = 0; i < 4; i++) {
                    level.addParticle(ParticleTypes.FLAME, muzzle.x, muzzle.y + 0.3, muzzle.z, direction.x * 0.12, direction.y * 0.12 + 0.02,
                            direction.z * 0.12);
                }
            }
            case MISSILE -> {
                for (int i = 0; i < 8; i++) {
                    level.addParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, pivot.x, pivot.y + 0.2, pivot.z,
                            (level.getRandom().nextDouble() - 0.5) * 0.08, 0.02, (level.getRandom().nextDouble() - 0.5) * 0.08);
                }
            }
        }
    }
}
