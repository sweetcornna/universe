package io.github.sweetcornna.universe.hazard;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import io.github.sweetcornna.universe.core.hazard.Vortex;
import io.github.sweetcornna.universe.core.procgen.Rng;
import io.github.sweetcornna.universe.entity.ship.ShipEntity;
import io.github.sweetcornna.universe.registry.UniverseEntities;
import io.github.sweetcornna.universe.registry.sound.HazardSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 尘卷风（ADR-0034 §六）：荒漠、盆地里旋起来的一根尘柱，底下半径 {@value Vortex#RADIUS} 格、{@value Vortex#HEIGHT} 格高，
 * 慢慢游走 30–60 秒。掉落物、生物、人进了柱子就被卷着转、往上带，转几圈或者到顶就甩出去；不直接伤人，摔下来照算。
 *
 * <p>人的移动在客户端，所以卷人的那一份由客户端照同样的算法做（{@code client.hazard.HazardClient}），服务端卷掉落物和生物。
 * 头目、太大的东西（宽过 {@value Vortex#MAX_WIDTH} 格）、坐着载具的不卷。不存档。
 */
public class DustDevil extends Entity {
    private static final EntityDataAccessor<Long> DATA_BORN = SynchedEntityData.defineId(DustDevil.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Integer> DATA_LIFE = SynchedEntityData.defineId(DustDevil.class, EntityDataSerializers.INT);
    /** 起风、停风要几刻：柱子先细后粗，最后散开。 */
    public static final int FADE = 60;
    /** 多近的玩家看得到"尘卷风靠近了"的提示。 */
    private static final double WARN = 14;

    private double heading;
    private Rng rng = Rng.of(0);
    /** 卷着的东西：实体 ID → 卷了几刻、到几刻甩出去。 */
    private final Map<Integer, int[]> caught = new HashMap<>();
    /** 刚甩出去的东西：实体 ID → 到哪一刻之前不再卷。 */
    private final Map<Integer, Long> cooldown = new HashMap<>();
    /** 已经提示过的玩家：实体 ID。 */
    private final java.util.Set<Integer> warned = new java.util.HashSet<>();

    public DustDevil(EntityType<? extends DustDevil> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    /** 在 {@code at}（地面）起一个尘卷风，朝 {@code heading} 走，已经加进世界。 */
    public static DustDevil spawn(ServerLevel level, Vec3 at, double heading, Rng rng) {
        DustDevil devil = new DustDevil(UniverseEntities.DUST_DEVIL.get(), level);
        devil.setPos(at);
        devil.heading = heading;
        devil.rng = rng;
        devil.entityData.set(DATA_BORN, level.getGameTime());
        devil.entityData.set(DATA_LIFE, rng.range(Vortex.LIFE_MIN, Vortex.LIFE_MAX));
        return level.addFreshEntity(devil) ? devil : null;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_BORN, 0L);
        builder.define(DATA_LIFE, Vortex.LIFE_MIN);
    }

    public int age() {
        return (int) (this.level().getGameTime() - this.entityData.get(DATA_BORN));
    }

    public int life() {
        return this.entityData.get(DATA_LIFE);
    }

    /** 现在有多猛：起风、停风时小，0–1。卷东西按这个打折。 */
    public double strength() {
        int age = this.age();
        int life = this.life();
        if (age < 0 || age >= life) {
            return 0;
        }
        return Math.min(1, Math.min((age + 1) / (double) FADE, (life - age) / (double) FADE));
    }

    /** 快点停下（走进了基地、调试命令）。 */
    public void dissipate() {
        int age = this.age();
        if (this.life() - age > FADE) {
            this.entityData.set(DATA_LIFE, age + FADE);
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide()) {
            this.dust();
            return;
        }
        ServerLevel level = (ServerLevel) this.level();
        int age = this.age();
        if (age >= this.life() || age < 0) {
            this.discard();
            return;
        }
        this.wander(level);
        if (age % 40 == 0 && (Hazards.exempt(level, this.blockPosition()) || !Hazards.enabled(level))) {
            this.dissipate();
        }
        if (age % 50 == 0) {
            level.playSound(null, this.getX(), this.getY() + 3, this.getZ(), HazardSounds.DUST_DEVIL.get(), SoundSource.AMBIENT,
                    (float) (1.4 * this.strength()) + 0.2F, 0.85F + this.random.nextFloat() * 0.3F);
        }
        double strength = this.strength();
        if (strength > 0.3) {
            this.carry(level, strength);
        }
        for (ServerPlayer player : level.players()) {
            if (player.isSpectator()) {
                continue;
            }
            if (player.distanceTo(this) < WARN && this.warned.add(player.getId())) {
                Hazards.hint(player, "hud.universe.hazard.dust_devil", ChatFormatting.YELLOW);
            }
            // 人在客户端卷着飞，服务端别当成作弊飞行踢掉（专用服务器不开 allow-flight 时）
            if (Vortex.reaches(player.getX() - this.getX(), player.getY() - this.getY(), player.getZ() - this.getZ())) {
                player.connection.resetFlyingTicks();
            }
        }
    }

    /** 慢慢游走，贴着地面；前面是陡坡就掉头。 */
    private void wander(ServerLevel level) {
        this.heading = Vortex.wander(this.heading, this.rng);
        double x = this.getX() + Math.cos(this.heading) * Vortex.SPEED;
        double z = this.getZ() + Math.sin(this.heading) * Vortex.SPEED;
        int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(x), (int) Math.floor(z));
        if (Math.abs(ground - this.getY()) > 3 || !level.getFluidState(BlockPos.containing(x, ground - 1, z)).isEmpty()) {
            this.heading += Math.PI * (0.6 + this.rng.nextDouble() * 0.8);
            return;
        }
        this.setPos(x, this.getY() + (ground - this.getY()) * 0.2, z);
    }

    /** 服务端卷掉落物和生物（人在客户端卷）。 */
    private void carry(ServerLevel level, double strength) {
        long time = level.getGameTime();
        AABB box = new AABB(this.getX(), this.getY() - 1, this.getZ(), this.getX(), this.getY() + Vortex.HEIGHT, this.getZ())
                .inflate(Vortex.radiusAt(Vortex.HEIGHT), 0, Vortex.radiusAt(Vortex.HEIGHT));
        java.util.Set<Integer> seen = new java.util.HashSet<>();
        for (Entity entity : level.getEntities(this, box, DustDevil::liftable)) {
            Long until = this.cooldown.get(entity.getId());
            if (until != null && time < until) {
                continue;
            }
            double dx = entity.getX() - this.getX();
            double dy = entity.getY() - this.getY();
            double dz = entity.getZ() - this.getZ();
            if (!Vortex.reaches(dx, dy, dz)) {
                continue;
            }
            seen.add(entity.getId());
            int[] state = this.caught.computeIfAbsent(entity.getId(), id -> new int[] {0, Vortex.catchTicks(this.rng)});
            double weight = weight(entity);
            double[] v = Vortex.flings(state[0], state[1], dy) ? Vortex.fling(dx, dz, weight) : Vortex.carry(dx, dy, dz, weight);
            if (Vortex.flings(state[0], state[1], dy)) {
                this.caught.remove(entity.getId());
                this.cooldown.put(entity.getId(), time + Vortex.COOLDOWN);
            } else {
                state[0]++;
            }
            entity.setDeltaMovement(v[0] * strength, v[1] * strength, v[2] * strength);
            entity.needsSync = true;
            if (entity instanceof ItemEntity item) {
                item.setPickUpDelay(10);
            }
        }
        this.caught.keySet().removeIf(id -> !seen.contains(id));
        for (Iterator<Map.Entry<Integer, Long>> it = this.cooldown.entrySet().iterator(); it.hasNext();) {
            if (it.next().getValue() < time) {
                it.remove();
            }
        }
    }

    /** 服务端卷得动的：掉落物、不太大的生物；人在客户端卷，头目、载具、坐着东西的不卷。 */
    public static boolean liftable(Entity entity) {
        if (entity instanceof Player || entity.isPassenger() || entity.isVehicle() || entity instanceof ShipEntity || entity.isSpectator()
                || entity instanceof DustDevil || entity instanceof SporeCloud) {
            return false;
        }
        if (entity instanceof ItemEntity) {
            return true;
        }
        if (!(entity instanceof LivingEntity living) || living.getMaxHealth() > 100 || living.isNoGravity()) {
            return false;
        }
        return Vortex.liftable(entity.getBbWidth(), entity.getBbHeight());
    }

    /** 东西的重。 */
    public static double weight(Entity entity) {
        return Vortex.weight(entity.getBbWidth() * entity.getBbWidth() * entity.getBbHeight(), entity instanceof ItemEntity);
    }

    /** 客户端：柱子里转着的尘，脚下卷起来的沙土。尘柱本身由渲染器画。 */
    private void dust() {
        double strength = this.strength();
        if (strength <= 0.02) {
            return;
        }
        int sand = HazardLook.sand(this.level());
        int count = (int) Math.ceil(5 * strength);
        for (int i = 0; i < count; i++) {
            double h = Math.pow(this.random.nextDouble(), 1.4) * Vortex.HEIGHT;
            double angle = this.random.nextDouble() * Math.PI * 2;
            double r = Vortex.radiusAt(h) * (0.4 + this.random.nextDouble() * 0.6);
            double x = this.getX() + Math.cos(angle) * r;
            double z = this.getZ() + Math.sin(angle) * r;
            // 逆时针的切向速度
            double speed = 0.35 * strength;
            this.level().addParticle(new DustParticleOptions(sand, 1.2F + this.random.nextFloat() * 1.3F), x, this.getY() + h, z,
                    -Math.sin(angle) * speed, 0.04, Math.cos(angle) * speed);
        }
        BlockState ground = this.level().getBlockState(this.blockPosition().below());
        if (!ground.isAir() && this.random.nextInt(2) == 0) {
            double angle = this.random.nextDouble() * Math.PI * 2;
            this.level().addParticle(new BlockParticleOption(ParticleTypes.BLOCK, ground), this.getX() + Math.cos(angle) * 2.5,
                    this.getY() + 0.1, this.getZ() + Math.sin(angle) * 2.5, -Math.sin(angle) * 0.3, 0.2, Math.cos(angle) * 0.3);
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
        return distance < 160 * 160;
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
    }
}
