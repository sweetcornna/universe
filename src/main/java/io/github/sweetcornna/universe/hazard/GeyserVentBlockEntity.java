package io.github.sweetcornna.universe.hazard;

import java.util.List;

import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.core.galaxy.Hazard;
import io.github.sweetcornna.universe.core.galaxy.Planet;
import io.github.sweetcornna.universe.core.hazard.GeyserCycle;
import io.github.sweetcornna.universe.core.hazard.GeyserKind;
import io.github.sweetcornna.universe.core.procgen.Seeds;
import io.github.sweetcornna.universe.entity.ship.ShipEntity;
import io.github.sweetcornna.universe.registry.UniverseBlockEntities;
import io.github.sweetcornna.universe.registry.sound.HazardSounds;
import io.github.sweetcornna.universe.world.planet.Planets;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 间歇泉喷口的节奏和效果（ADR-0034 §六）。节奏照 {@link GeyserCycle}：喷口的种子由方块位置派生，服务端和客户端
 * 照世界时间各算各的，不用同步。
 *
 * <ul>
 *   <li>服务端：咕嘟、喷发时放声音，附近的人 HUD 上提示一句；喷的时候柱子里的掉落物、生物往上抛，柱子里的人按星球
 *       扣防护（{@link Hazards#steam}）。基地信标旁边、世界设置关掉了局部危害时喷口不喷。</li>
 *   <li>客户端：咕嘟时冒泡、地面轻震；喷的时候画水汽柱；人在柱子里时往上抛（人的移动在客户端）。</li>
 * </ul>
 *
 * <p>调试命令和 GameTest 可以让它马上喷（{@link #erupt}），这一下同步给客户端。
 */
public class GeyserVentBlockEntity extends BlockEntity {
    private static final String FORCED = "Forced";
    /** 多近的人觉得到地面在震、看得到提示。 */
    private static final double NEAR = 8;

    /** 强制喷发从哪一刻开始响；没有为 {@link Long#MIN_VALUE}。 */
    private long forced = Long.MIN_VALUE;
    private @Nullable GeyserCycle cycle;
    /** 这一轮不喷（基地旁边、规则关了）：每轮开始响的时候查一次。 */
    private boolean dormant;

    public GeyserVentBlockEntity(BlockPos pos, BlockState state) {
        super(UniverseBlockEntities.GEYSER_VENT.get(), pos, state);
    }

    public GeyserKind kind() {
        return this.getBlockState().getBlock() instanceof GeyserVentBlock vent ? vent.kind() : GeyserKind.HOT;
    }

    public GeyserCycle cycle() {
        if (this.cycle == null) {
            this.cycle = GeyserCycle.of(Seeds.derive(this.worldPosition.asLong(), "vent"));
        }
        return this.cycle;
    }

    public GeyserCycle.Phase phase(long time) {
        long since = time - this.forced;
        if (this.forced != Long.MIN_VALUE && since >= 0 && since < GeyserCycle.RUMBLE + GeyserCycle.ERUPT) {
            return since < GeyserCycle.RUMBLE ? GeyserCycle.Phase.RUMBLE : GeyserCycle.Phase.ERUPT;
        }
        return this.cycle().phase(time);
    }

    public int intoPhase(long time) {
        long since = time - this.forced;
        if (this.forced != Long.MIN_VALUE && since >= 0 && since < GeyserCycle.RUMBLE + GeyserCycle.ERUPT) {
            return (int) (since < GeyserCycle.RUMBLE ? since : since - GeyserCycle.RUMBLE);
        }
        return this.cycle().intoPhase(time);
    }

    /** 这一刻水汽柱多高。 */
    public double column(long time) {
        return this.dormant ? 0 : GeyserCycle.columnAt(this.phase(time), this.intoPhase(time));
    }

    /** 马上喷：{@code now} 为真时跳过咕嘟的三秒。服务端调。 */
    public void erupt(boolean now) {
        if (this.level == null) {
            return;
        }
        this.forced = this.level.getGameTime() - (now ? GeyserCycle.RUMBLE : 0);
        this.dormant = false;
        this.setChanged();
        this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 3);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, GeyserVentBlockEntity vent) {
        long time = level.getGameTime();
        GeyserCycle.Phase phase = vent.phase(time);
        if (phase == GeyserCycle.Phase.QUIET || !(level instanceof ServerLevel server)) {
            return;
        }
        int into = vent.intoPhase(time);
        if (phase == GeyserCycle.Phase.RUMBLE && into == 0) {
            boolean forced = time - vent.forced < GeyserCycle.RUMBLE + GeyserCycle.ERUPT;
            vent.dormant = !forced && (!Hazards.enabled(server) || Hazards.exempt(server, pos));
            if (!vent.dormant) {
                server.playSound(null, pos, HazardSounds.GEYSER_RUMBLE.get(), SoundSource.BLOCKS, 1.0F, 0.9F + server.getRandom().nextFloat() * 0.2F);
                for (ServerPlayer player : server.getPlayers(p -> p.position().distanceTo(Vec3.atCenterOf(pos)) < NEAR)) {
                    Hazards.hint(player, "hud.universe.hazard.geyser", ChatFormatting.AQUA);
                }
            }
        }
        if (vent.dormant) {
            return;
        }
        if (phase == GeyserCycle.Phase.ERUPT) {
            if (into == 0) {
                server.playSound(null, pos, HazardSounds.GEYSER_ERUPT.get(), SoundSource.BLOCKS, 2.0F, 0.9F + server.getRandom().nextFloat() * 0.2F);
            }
            vent.lift(server, time, vent.column(time));
        }
    }

    /** 服务端：柱子里的掉落物、生物往上抛；人只记一笔防护（人往上抛在客户端）。 */
    private void lift(ServerLevel level, long time, double column) {
        if (column <= 0) {
            return;
        }
        double cx = this.worldPosition.getX() + 0.5;
        double cz = this.worldPosition.getZ() + 0.5;
        double base = this.worldPosition.getY() + 1;
        double r = GeyserCycle.RADIUS;
        AABB box = new AABB(cx - r, base - 0.5, cz - r, cx + r, base + column, cz + r);
        Hazard hazard = this.kind().hazard(Planets.planet(level).map(Planet::hazards).orElse(List.of()));
        for (Entity entity : level.getEntities((Entity) null, box, GeyserVentBlockEntity::liftable)) {
            if (!GeyserCycle.inColumn(Math.hypot(entity.getX() - cx, entity.getZ() - cz))) {
                continue;
            }
            double lift = GeyserCycle.lift(entity.getY() - base, column);
            if (lift <= 0) {
                continue;
            }
            if (entity instanceof Player player) {
                if (player instanceof ServerPlayer serverPlayer) {
                    // 人在客户端被托着飘，服务端别当成作弊飞行踢掉（专用服务器不开 allow-flight 时）
                    serverPlayer.connection.resetFlyingTicks();
                    if (!player.isCreative()) {
                        Hazards.steam(serverPlayer, hazard, time);
                    }
                }
                continue;
            }
            push(entity, lift);
            entity.needsSync = true;
        }
    }

    private static boolean liftable(Entity entity) {
        return !entity.isSpectator() && !entity.isPassenger() && !(entity instanceof ShipEntity) && !entity.isNoGravity();
    }

    /** 往上推：竖直速度至少是 {@code lift}，水平方向收一点，好待在柱子里。 */
    private static void push(Entity entity, double lift) {
        Vec3 v = entity.getDeltaMovement();
        entity.setDeltaMovement(v.x * 0.85, Math.max(v.y, lift), v.z * 0.85);
    }

    public static void clientTick(Level level, BlockPos pos, BlockState state, GeyserVentBlockEntity vent) {
        long time = level.getGameTime();
        GeyserCycle.Phase phase = vent.phase(time);
        if (phase == GeyserCycle.Phase.QUIET) {
            return;
        }
        RandomSource random = level.getRandom();
        double cx = pos.getX() + 0.5;
        double cz = pos.getZ() + 0.5;
        double base = pos.getY() + 1;
        GeyserKind kind = vent.kind();
        if (phase == GeyserCycle.Phase.RUMBLE) {
            rumble(level, pos, random, cx, cz, base, vent.intoPhase(time));
            for (Player player : level.players()) {
                if (player.isLocalPlayer() && player.position().distanceTo(Vec3.atCenterOf(pos)) < NEAR) {
                    HazardLook.shake.shake(0.35F, 3);
                }
            }
            return;
        }
        double column = GeyserCycle.columnAt(phase, vent.intoPhase(time));
        if (column <= 0) {
            return;
        }
        steam(level, random, kind, cx, cz, base, column);
        // 人在客户端走：自己在柱子里就往上抛
        for (Player player : level.players()) {
            if (player.isLocalPlayer() && !player.isSpectator() && liftable(player)
                    && GeyserCycle.inColumn(Math.hypot(player.getX() - cx, player.getZ() - cz))) {
                double lift = GeyserCycle.lift(player.getY() - base, column);
                if (lift > 0) {
                    push(player, lift);
                }
            }
        }
    }

    /** 咕嘟：泉眼冒泡、溅水，地面掉渣，越到后面越凶。 */
    private static void rumble(Level level, BlockPos pos, RandomSource random, double cx, double cz, double base, int into) {
        float rise = into / (float) GeyserCycle.RUMBLE;
        if (random.nextFloat() < 0.4F + rise) {
            level.addParticle(ParticleTypes.BUBBLE_POP, cx + (random.nextDouble() - 0.5) * 0.6, base + 0.05,
                    cz + (random.nextDouble() - 0.5) * 0.6, 0, 0.02, 0);
        }
        if (random.nextFloat() < 0.3F + rise) {
            level.addParticle(ParticleTypes.SPLASH, cx + (random.nextDouble() - 0.5) * 0.5, base + 0.1, cz + (random.nextDouble() - 0.5) * 0.5,
                    0, 0.15, 0);
        }
        if (random.nextFloat() < 0.15F + rise * 0.5F) {
            level.addParticle(ParticleTypes.CLOUD, cx, base + 0.2, cz, (random.nextDouble() - 0.5) * 0.03, 0.06 + rise * 0.1,
                    (random.nextDouble() - 0.5) * 0.03);
        }
        if (random.nextInt(6) == 0) {
            BlockPos ground = pos.offset(random.nextInt(5) - 2, 0, random.nextInt(5) - 2);
            BlockState ground0 = level.getBlockState(ground);
            if (!ground0.isAir()) {
                level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK, ground0), ground.getX() + random.nextDouble(), base + 0.05,
                        ground.getZ() + random.nextDouble(), 0, 0.1, 0);
            }
        }
    }

    /** 水汽柱：整根柱子都有往上冲的汽，顶上往外翻卷，四周落下水珠（冰冻星球是冰屑，奇异星球带着闪光）。 */
    private static void steam(Level level, RandomSource random, GeyserKind kind, double cx, double cz, double base, double column) {
        for (int i = 0; i < 6; i++) {
            double y = base + random.nextDouble() * column * 0.9;
            level.addAlwaysVisibleParticle(ParticleTypes.CLOUD, cx + (random.nextDouble() - 0.5) * 0.7, y, cz + (random.nextDouble() - 0.5) * 0.7,
                    (random.nextDouble() - 0.5) * 0.06, 0.45 + random.nextDouble() * 0.35, (random.nextDouble() - 0.5) * 0.06);
        }
        ParticleOptions tint = new DustParticleOptions(kind.steamColor(), 3.5F);
        for (int i = 0; i < 2; i++) {
            double y = base + random.nextDouble() * column;
            level.addAlwaysVisibleParticle(tint, cx + (random.nextDouble() - 0.5) * 1.2, y, cz + (random.nextDouble() - 0.5) * 1.2, 0, 0.2, 0);
        }
        // 柱顶翻卷
        double top = base + column;
        for (int i = 0; i < 3; i++) {
            double angle = random.nextDouble() * Math.PI * 2;
            level.addAlwaysVisibleParticle(ParticleTypes.CLOUD, cx, top - random.nextDouble(), cz, Math.cos(angle) * 0.18, 0.05,
                    Math.sin(angle) * 0.18);
        }
        if (column >= GeyserCycle.HEIGHT * 0.6) {
            level.addAlwaysVisibleParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, cx + (random.nextDouble() - 0.5) * 2, top,
                    cz + (random.nextDouble() - 0.5) * 2, 0, 0.05, 0);
        }
        // 落下来的
        ParticleOptions fall = switch (kind) {
            case COLD -> ParticleTypes.SNOWFLAKE;
            case STRANGE -> random.nextInt(3) == 0 ? ParticleTypes.END_ROD : ParticleTypes.FALLING_WATER;
            default -> ParticleTypes.FALLING_WATER;
        };
        for (int i = 0; i < 3; i++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double r = 0.8 + random.nextDouble() * 2.5;
            level.addParticle(fall, cx + Math.cos(angle) * r, base + column * (0.4 + random.nextDouble() * 0.5), cz + Math.sin(angle) * r,
                    0, -0.05, 0);
        }
        // 喷口四周溅开
        level.addParticle(ParticleTypes.SPLASH, cx + (random.nextDouble() - 0.5) * 1.6, base + 0.1, cz + (random.nextDouble() - 0.5) * 1.6,
                0, 0.2, 0);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.forced = input.getLongOr(FORCED, Long.MIN_VALUE);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (this.forced != Long.MIN_VALUE) {
            output.putLong(FORCED, this.forced);
        }
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return this.saveCustomOnly(registries);
    }
}
