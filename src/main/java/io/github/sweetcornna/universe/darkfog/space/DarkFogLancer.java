package io.github.sweetcornna.universe.darkfog.space;

import java.util.Optional;

import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.core.darkfog.LancerRaid;
import io.github.sweetcornna.universe.core.darkfog.SpaceFlight;
import io.github.sweetcornna.universe.core.darkfog.SpaceUnitKind;
import io.github.sweetcornna.universe.core.darkfog.DarkFogLevels;
import io.github.sweetcornna.universe.core.ship.PirateFlight;
import io.github.sweetcornna.universe.darkfog.Facilities;
import io.github.sweetcornna.universe.darkfog.entity.DarkFogBolt;
import io.github.sweetcornna.universe.darkfog.shield.Shields;
import io.github.sweetcornna.universe.registry.sound.HostileSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * 枪骑（ADR-0028 §八）：快、灵活、血少。
 *
 * <ul>
 *   <li>守巢：飞法和海盗战机一样（{@link PirateFlight}），一轮一轮俯冲着切过来，机头对准了就打一梭激光弹。</li>
 *   <li>轰炸（{@link LancerRaids} 指挥）：从高空降到离地 {@link LancerRaid#BOMB_HEIGHT} 格，飞到目标一带的设施正上方扔炸弹，
 *       炸弹扔完或者接到撤退的命令就往上爬，爬到 {@link LancerRaid#LEAVE_HEIGHT} 格消失。离地够高时算高空目标。</li>
 * </ul>
 */
public class DarkFogLancer extends DarkFogSpaceUnit {
    /** 弹速（相对枪骑）、飞多少刻：太空里射程远。 */
    private static final double BOLT_SPEED = PirateFlight.BOLT_SPEED;
    private static final int BOLT_LIFETIME = 50;
    /** 机头偏离目标这么多度以内、这么近以内才开火。 */
    private static final double FIRE_CONE = 15;
    private static final double FIRE_RANGE = 100;
    /** 一梭几发、两发隔几刻；两梭之间冷却多少刻。 */
    private static final int BURST = 2;
    private static final int BURST_GAP = 4;
    private static final int COOLDOWN = 24;
    /** 轰炸时的速度：俯冲、在设施上空盘旋、撤走。 */
    private static final double DIVE_SPEED = 1.1;
    private static final double BOMB_SPEED = 0.9;
    private static final double LEAVE_SPEED = 1.6;
    /** 多久没收到轰炸的命令就当这队没了，自己撤。 */
    private static final int ORDER_TIMEOUT = 40;
    /** 挑中的投弹点多久没炸成就换一个。 */
    private static final int AIM_TIMEOUT = 120;

    private @Nullable PirateFlight flight;
    private int gunCooldown = 10;
    private int burstLeft;
    private int burstGap;
    // ---- 轰炸
    private BlockPos raidTarget = BlockPos.ZERO;
    private double raidRadius = LancerRaid.RAID_RADIUS;
    private double spread;
    private LancerRaid.Phase raidPhase = LancerRaid.Phase.DIVE;
    private long lastOrder = Long.MIN_VALUE;
    private int bombs;
    private int bombCooldown;
    private @Nullable Vec3 bombAim;
    private int aimAge;
    private boolean high;
    private boolean arrived;

    public DarkFogLancer(EntityType<? extends DarkFogLancer> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, SpaceUnitKind.LANCER.baseHealth())
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.FLYING_SPEED, 0.5)
                .add(Attributes.FOLLOW_RANGE, SpaceUnitKind.LANCER.sight())
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.ARMOR, 2);
    }

    @Override
    public SpaceUnitKind kind() {
        return SpaceUnitKind.LANCER;
    }

    @Override
    protected double patrolRadius() {
        return 30 + this.random.nextDouble() * 50;
    }

    // ---- 守巢

    @Override
    protected void engage(ServerLevel level, LivingEntity target, Vec3 aim, Vec3 targetVelocity, double clearance) {
        if (this.flight == null) {
            this.flight = new PirateFlight(this.getUUID().getLeastSignificantBits(), this.random.nextInt(6), 6,
                    this.kind().speed() / PirateFlight.SPEED);
        }
        Vec3 pos = this.getBoundingBox().getCenter();
        PirateFlight.Step step = this.flight.step(arr(pos), arr(this.getDeltaMovement()), arr(aim), arr(targetVelocity), clearance,
                this.getHealth() / this.getMaxHealth(), 1);
        this.setDeltaMovement(vec(step.velocity()).scale(this.jamFactor()));
        this.gun(level, pos, aim, targetVelocity);
    }

    /** 机头对准了、够得着就打一梭；被干扰时打得慢。 */
    private void gun(ServerLevel level, Vec3 pos, Vec3 aim, Vec3 targetVelocity) {
        if (this.gunCooldown > 0) {
            this.gunCooldown--;
        }
        if (this.burstGap > 0) {
            this.burstGap--;
        }
        Vec3 to = aim.subtract(pos);
        double distance = to.length();
        Vec3 nose = this.getDeltaMovement().lengthSqr() > 1e-3 ? this.getDeltaMovement() : this.getLookAngle();
        if (this.burstLeft == 0 && this.gunCooldown == 0 && distance > 3 && distance < FIRE_RANGE
                && SpaceFlight.inCone(arr(nose), arr(to), FIRE_CONE)) {
            this.burstLeft = BURST;
            this.burstGap = 0;
        }
        if (this.burstLeft > 0 && this.burstGap == 0) {
            this.burstLeft--;
            this.burstGap = BURST_GAP;
            if (this.burstLeft == 0) {
                this.gunCooldown = (int) Math.ceil((COOLDOWN + this.random.nextInt(12)) / Math.max(0.2, this.jamFactor()));
            }
            Vec3 from = pos.add(nose.normalize().scale(this.getBbWidth() * 0.6));
            this.shoot(level, from, aim, targetVelocity, DarkFogBolt.RANGER_COLOR, 0.7F, BOLT_SPEED, 2.0F, BOLT_LIFETIME);
            this.playSound(HostileSounds.LANCER_SHOT.get(), 0.8F, 0.95F + this.random.nextFloat() * 0.1F);
        }
    }

    // ---- 轰炸

    /** 编进一次轰炸（{@link LancerRaids} 放出来时调）：{@code spread} 是俯冲时在目标周围散开的角度。 */
    void joinRaid(BlockPos target, double radius, double spread, int bombs) {
        this.raidTarget = target.immutable();
        this.raidRadius = radius;
        this.spread = spread;
        this.bombs = bombs;
    }

    /** 这一刻轰炸到了哪个阶段（{@link LancerRaids} 每刻发）。 */
    void raidOrder(LancerRaid.Phase phase, long time) {
        this.raidPhase = phase;
        this.lastOrder = time;
    }

    /** 还剩几颗炸弹。 */
    public int bombs() {
        return this.bombs;
    }

    /** 到了轰炸的高度。 */
    public boolean arrived() {
        return this.arrived;
    }

    @Override
    public boolean highAltitude() {
        return this.mode() == Mode.RAID && this.high;
    }

    @Override
    public boolean attackingFacility() {
        return this.mode() == Mode.RAID && this.raidPhase == LancerRaid.Phase.BOMB && this.bombs > 0;
    }

    /** 在这一列扔炸弹的高度：有护盾的穹顶盖着就停在穹顶外面，钻不进去。 */
    private static double bombAltitude(ServerLevel level, double x, double z, double ground) {
        return LancerRaid.bombAltitude(ground, Shields.ceiling(level, x, z).orElse(Double.NaN));
    }

    /** 这一列的地面（{@code WORLD_SURFACE}，玩家的建筑也算）；区块没加载是空。 */
    private static @Nullable Double ground(ServerLevel level, double x, double z) {
        int bx = (int) Math.floor(x);
        int bz = (int) Math.floor(z);
        if (!level.hasChunk(SectionPos.blockToSectionCoord(bx), SectionPos.blockToSectionCoord(bz))) {
            return null;
        }
        return (double) level.getHeight(Heightmap.Types.WORLD_SURFACE, bx, bz);
    }

    @Override
    protected void raid(ServerLevel level) {
        if (this.bombCooldown > 0) {
            this.bombCooldown--;
        }
        boolean ordered = level.getGameTime() - this.lastOrder <= ORDER_TIMEOUT;
        LancerRaid.Phase phase = ordered ? this.raidPhase : LancerRaid.Phase.LEAVE;
        Double ground = ground(level, this.getX(), this.getZ());
        double above = ground == null ? LancerRaid.BOMB_HEIGHT : this.getY() - ground;
        this.high = LancerRaid.highAltitude(above);
        this.arrived = ground != null && Math.abs(this.getY() - bombAltitude(level, this.getX(), this.getZ(), ground)) <= LancerRaid.ARRIVED;
        double jam = this.jamFactor();
        Vec3 pos = this.position();
        double[] wanted;
        switch (phase) {
            case WARNING, DIVE -> {
                double r = Math.min(10, this.raidRadius * 0.5);
                double x = this.raidTarget.getX() + 0.5 + Math.cos(this.spread) * r;
                double z = this.raidTarget.getZ() + 0.5 + Math.sin(this.spread) * r;
                Double there = ground(level, x, z);
                double y = bombAltitude(level, x, z, there == null ? pos.y - above : there);
                wanted = SpaceFlight.seek(arr(pos), new double[] {x, y, z}, DIVE_SPEED * jam, 24);
                this.setAngry(false);
            }
            case BOMB -> wanted = this.bomb(level, pos, jam);
            default -> {
                // 撤走：背着目标往上爬，爬够了、或者飞出了加载的区块就消失
                if (ground == null || above >= LancerRaid.LEAVE_HEIGHT) {
                    this.vanish(level);
                    return;
                }
                Vec3 away = new Vec3(pos.x - this.raidTarget.getX() - 0.5, 0, pos.z - this.raidTarget.getZ() - 0.5);
                away = away.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : away.normalize();
                wanted = arr(away.scale(0.45).add(0, 0.9, 0).normalize().scale(LEAVE_SPEED * jam));
                this.setAngry(false);
            }
        }
        this.steer(wanted, this.kind().accel() * 0.8);
    }

    /** 轰炸：飞到挑中的设施正上方，够近就扔一颗，再挑下一个。 */
    private double[] bomb(ServerLevel level, Vec3 pos, double jam) {
        if (this.bombAim == null || ++this.aimAge > AIM_TIMEOUT) {
            this.pickAim(level);
        }
        Vec3 aim = this.bombAim;
        Double there = ground(level, aim.x, aim.z);
        double y = bombAltitude(level, aim.x, aim.z, there == null ? aim.y : there);
        double horizontal = Math.hypot(aim.x - pos.x, aim.z - pos.z);
        this.setAngry(this.bombs > 0);
        if (this.bombs > 0 && this.bombCooldown == 0 && horizontal <= LancerRaid.DROP_RADIUS) {
            this.dropBomb(level);
        }
        return SpaceFlight.seek(arr(pos), new double[] {aim.x, y, aim.z}, BOMB_SPEED * jam, 10);
    }

    /** 挑一个投弹点：目标一带随便一点附近的设施，没有就找离目标最近的，再没有就炸那一点。 */
    private void pickAim(ServerLevel level) {
        Vec3 center = Vec3.atCenterOf(this.raidTarget);
        double angle = this.random.nextDouble() * Math.PI * 2;
        double r = this.random.nextDouble() * this.raidRadius * 0.6;
        Vec3 probe = center.add(Math.cos(angle) * r, 0, Math.sin(angle) * r);
        Optional<BlockPos> facility = Facilities.nearest(level, probe, this.raidRadius * 0.5);
        if (facility.isEmpty()) {
            facility = Facilities.nearest(level, center, this.raidRadius);
        }
        this.bombAim = facility.map(Vec3::atCenterOf).orElse(probe);
        this.aimAge = 0;
    }

    private void dropBomb(ServerLevel level) {
        Vec3 at = this.position().add(0, -0.2, 0);
        float damage = (float) (LancerRaid.BOMB_DAMAGE * DarkFogLevels.scale(this.darkFogLevel()));
        LancerBomb.drop(level, this, at, damage);
        this.bombs--;
        this.bombCooldown = (int) Math.ceil(LancerRaid.BOMB_INTERVAL / Math.max(0.2, this.jamFactor()));
        this.bombAim = null;
        this.playSound(HostileSounds.BOMB_DROP.get(), 1.0F, 0.95F + this.random.nextFloat() * 0.1F);
    }
}
