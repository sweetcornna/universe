package io.github.sweetcornna.universe.darkfog.shield;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.core.darkfog.ShieldMath;
import io.github.sweetcornna.universe.registry.sound.MachineSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 行星护盾（ADR-0028 §五、§八）：充满能量的发生器展开一个半径 160 格、高 64 格的穹顶，挡高空来的伤害、挡中继站降落；
 * 挡下的伤害从存的能量里扣，扣光了穹顶收起。挡不了地面单位。
 *
 * <p>接口（签名别改）：枪骑轰炸调 {@link #intercept}、{@link #absorb}；中继站落地前调 {@link #covers}。
 * 几个穹顶叠在一起时，盖着那一点、能量最多的那台先挡；它扣光了，剩下的伤害由下一台接着挡。
 *
 * <p>穹顶的状态每秒看一次，变了才发给那个维度里的人；挨打时在打中的地方闪一下。
 */
public final class Shields {
    private static final int SYNC_INTERVAL = 20;
    /** 隔多久查一次记着的发生器还在不在（被不带副作用地换掉的方块收不到移除的回调）。 */
    private static final int SWEEP_INTERVAL = 100;
    /** 挨打的闪光发给多远内的人。 */
    private static final double HIT_RANGE = 384;
    /** 每个维度上次发出去的穹顶，没变就不重发。 */
    private static final Map<ResourceKey<Level>, List<ShieldPayloads.Dome>> SENT = new HashMap<>();
    /** 有穹顶展开或者收起了的维度：下一次同步时直接发。 */
    private static final Set<ResourceKey<Level>> DIRTY = new HashSet<>();
    /** 判断"在穹顶里"时外壳往外放宽多少格。 */
    private static final double SHELL_TOLERANCE = 0.05;

    private Shields() {
    }

    public static void registerEvents(IEventBus gameBus) {
        gameBus.addListener(ServerTickEvent.Post.class, event -> tick(event.getServer()));
        gameBus.addListener(PlayerEvent.PlayerLoggedInEvent.class, event -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                sendTo(player);
            }
        });
        gameBus.addListener(PlayerEvent.PlayerChangedDimensionEvent.class, event -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                sendTo(player);
            }
        });
        gameBus.addListener(PlayerEvent.PlayerRespawnEvent.class, event -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                sendTo(player);
            }
        });
        gameBus.addListener(ServerStoppedEvent.class, event -> {
            SENT.clear();
            DIRTY.clear();
        });
    }

    // ---- 查询

    /** 这一点在不在某个展开的穹顶里（大的穹顶是压扁的，见 {@link ShieldMath#height}）。 */
    public static boolean covers(ServerLevel level, Vec3 at) {
        return coverer(ShieldData.get(level.getServer()), level.dimension(), at) != null;
    }

    /** 星球没加载也能查：({@code x}, {@code z}) 这一列有没有展开的穹顶盖着（只看水平距离）。中继站落地前用。 */
    public static boolean covers(MinecraftServer server, ResourceKey<Level> dimension, double x, double z) {
        for (ShieldData.Generator generator : ShieldData.get(server).raised(dimension)) {
            Vec3 center = generator.center();
            double r = generator.radius();
            double dx = x - center.x;
            double dz = z - center.z;
            if (dx * dx + dz * dz <= r * r) {
                return true;
            }
        }
        return false;
    }

    /** 盖着这一点、能量最多的那台。 */
    private static ShieldData.@Nullable Generator coverer(ShieldData data, ResourceKey<Level> dimension, Vec3 at) {
        ShieldData.Generator best = null;
        for (ShieldData.Generator generator : data.raised(dimension)) {
            Vec3 center = generator.center();
            // 放宽一点：intercept 给的点正好在外壳上，舍入误差会让它算到外面去
            double r = generator.radius();
            if (ShieldMath.inside(at.x - center.x, at.y - center.y, at.z - center.z, r + SHELL_TOLERANCE, ShieldMath.height(r) + SHELL_TOLERANCE)
                    && (best == null || generator.energy() > best.energy())) {
                best = generator;
            }
        }
        return best;
    }

    // ---- 挡伤害

    /**
     * 高空来的伤害打在 {@code at}：盖着它的穹顶挡下，从存的能量里扣。
     *
     * @return 没挡住的伤害（全挡住是 0）
     */
    public static float absorb(ServerLevel level, Vec3 at, float damage) {
        if (damage <= 0) {
            return 0;
        }
        ShieldData data = ShieldData.get(level.getServer());
        float left = damage;
        List<ShieldData.Generator> tried = new ArrayList<>();
        while (left > 0) {
            ShieldData.Generator generator = coverer(data, level.dimension(), at);
            if (generator == null || tried.contains(generator)) {
                break;
            }
            tried.add(generator);
            ShieldMath.Absorbed absorbed = ShieldMath.absorb(generator.energy(), left);
            boolean collapsed = absorbed.energy() <= 0;
            data.set(generator, new ShieldMath.State(absorbed.energy(), !collapsed));
            float stopped = left - absorbed.leftover();
            left = absorbed.leftover();
            flash(level, generator, at, stopped, collapsed);
            if (collapsed) {
                changed(level);
            }
        }
        return left;
    }

    /** ({@code x}, {@code z}) 这一列上展开着的穹顶，最高的外壳在什么高度；没有穹顶盖着是空。枪骑按这个停在穹顶外面扔炸弹。 */
    public static OptionalDouble ceiling(ServerLevel level, double x, double z) {
        double best = Double.NEGATIVE_INFINITY;
        for (ShieldData.Generator generator : ShieldData.get(level.getServer()).raised(level.dimension())) {
            Vec3 center = generator.center();
            double r = generator.radius();
            double top = ShieldMath.surface(x - center.x, z - center.z, r, ShieldMath.height(r));
            if (!Double.isNaN(top)) {
                best = Math.max(best, center.y + top);
            }
        }
        return best == Double.NEGATIVE_INFINITY ? OptionalDouble.empty() : OptionalDouble.of(best);
    }

    /** 从 {@code from} 飞到 {@code to} 的东西在哪里从外面碰到穹顶的外壳；没碰到是空。碰到好几个时取最先碰到的。 */
    public static Optional<Vec3> intercept(ServerLevel level, Vec3 from, Vec3 to) {
        double best = 2;
        for (ShieldData.Generator generator : ShieldData.get(level.getServer()).raised(level.dimension())) {
            Vec3 c = generator.center();
            double r = generator.radius();
            double t = ShieldMath.entry(from.x - c.x, from.y - c.y, from.z - c.z, to.x - c.x, to.y - c.y, to.z - c.z, r, ShieldMath.height(r));
            if (t >= 0 && t < best) {
                best = t;
            }
        }
        return best <= 1 ? Optional.of(from.lerp(to, best)) : Optional.empty();
    }

    /** 挨打的地方：火花、声音，再告诉附近的客户端闪一下。 */
    private static void flash(ServerLevel level, ShieldData.Generator generator, Vec3 at, float stopped, boolean collapsed) {
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, 12, 0.8, 0.8, 0.8, 0.2);
        level.playSound(null, at.x, at.y, at.z, MachineSounds.SHIELD_HIT, SoundSource.BLOCKS, 1.0F, 0.9F + level.getRandom().nextFloat() * 0.2F);
        if (collapsed) {
            Vec3 center = generator.center();
            level.playSound(null, center.x, center.y, center.z, MachineSounds.SHIELD_COLLAPSE, SoundSource.BLOCKS, 1.0F, 1.0F);
        }
        ShieldPayloads.Hit hit = new ShieldPayloads.Hit(generator.pos(), at.x, at.y, at.z, stopped, collapsed);
        for (ServerPlayer player : level.players()) {
            if (player.position().distanceToSqr(at) <= HIT_RANGE * HIT_RANGE) {
                PacketDistributor.sendToPlayer(player, hit);
            }
        }
    }

    // ---- 同步和清理

    /** 有穹顶展开或者收起了：下一次同步时不比较，直接发。 */
    static void changed(ServerLevel level) {
        DIRTY.add(level.dimension());
    }

    private static void tick(MinecraftServer server) {
        long time = server.overworld().getGameTime();
        if (time % SYNC_INTERVAL != 0) {
            return;
        }
        ShieldData data = ShieldData.get(server);
        boolean sweep = time % SWEEP_INTERVAL == 0;
        for (ServerLevel level : server.getAllLevels()) {
            if (sweep) {
                sweep(level, data);
            }
            List<ShieldPayloads.Dome> domes = domes(data, level.dimension());
            boolean dirty = DIRTY.remove(level.dimension());
            if (!dirty && similar(SENT.getOrDefault(level.dimension(), List.of()), domes)) {
                continue;
            }
            SENT.put(level.dimension(), domes);
            ShieldPayloads.Domes payload = new ShieldPayloads.Domes(domes);
            level.players().forEach(player -> PacketDistributor.sendToPlayer(player, payload));
        }
    }

    /** 记着、区块也加载着、方块却不在了的发生器，从存档里删掉。 */
    private static void sweep(ServerLevel level, ShieldData data) {
        List<BlockPos> gone = new ArrayList<>();
        for (ShieldData.Generator generator : data.generators(level.dimension())) {
            BlockPos pos = generator.pos();
            if (level.isLoaded(pos) && !(level.getBlockEntity(pos) instanceof PlanetaryShieldBlockEntity)) {
                gone.add(pos);
            }
        }
        if (!gone.isEmpty()) {
            gone.forEach(pos -> data.remove(level.dimension(), pos));
            changed(level);
        }
    }

    static List<ShieldPayloads.Dome> domes(ShieldData data, ResourceKey<Level> dimension) {
        List<ShieldPayloads.Dome> domes = new ArrayList<>();
        for (ShieldData.Generator generator : data.raised(dimension)) {
            domes.add(new ShieldPayloads.Dome(generator.pos(), (float) generator.radius(), (float) ShieldMath.charge(generator.energy())));
        }
        return domes;
    }

    /** 两份穹顶差不多：一样多、位置一样，半径差不到半格，能量差不到 2%。 */
    private static boolean similar(List<ShieldPayloads.Dome> a, List<ShieldPayloads.Dome> b) {
        if (a.size() != b.size()) {
            return false;
        }
        for (int i = 0; i < a.size(); i++) {
            ShieldPayloads.Dome x = a.get(i);
            ShieldPayloads.Dome y = b.get(i);
            if (!x.generator().equals(y.generator()) || Math.abs(x.radius() - y.radius()) > 0.5F || Math.abs(x.charge() - y.charge()) > 0.02F) {
                return false;
            }
        }
        return true;
    }

    private static void sendTo(ServerPlayer player) {
        if (player.level() instanceof ServerLevel level) {
            PacketDistributor.sendToPlayer(player, new ShieldPayloads.Domes(domes(ShieldData.get(level.getServer()), level.dimension())));
        }
    }
}
