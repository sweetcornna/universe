package io.github.sweetcornna.universe.darkfog.space;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.core.darkfog.LancerRaid;
import io.github.sweetcornna.universe.core.darkfog.SpaceUnitKind;
import io.github.sweetcornna.universe.darkfog.hive.DarkFogHives;
import io.github.sweetcornna.universe.darkfog.shield.Shields;
import io.github.sweetcornna.universe.network.LancerPayloads;
import io.github.sweetcornna.universe.rules.UniverseRules;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 枪骑轰炸星球（ADR-0028 §八）：巢穴的威胁满了，一队枪骑在玩家所在星球的高空出现，从上往下轰炸设施，炸完飞走。
 * 护盾（{@code Shields}）挡得住炸弹；导弹炮塔、等离子炮塔打得到它们。
 *
 * <p>接口（签名别改）：巢穴（{@code DarkFogHives}）调 {@link #launch}，什么时候派、派到哪、派几架归巢穴管；
 * 预警、放出来、轰炸、撤走归这里管。打完调 {@code DarkFogHives.onRaidOver}。
 *
 * <p>每刻推进一次每次轰炸（{@link LancerRaid}），给每架枪骑发这一刻的阶段；枪骑自己飞、自己找设施扔炸弹。
 * 轰炸不存档：服务器停了、星球卸载了，这一队就当飞回去了。
 */
public final class LancerRaids {
    /** 预警每隔多少刻重发一次（新上星球的人也看得到）。 */
    private static final int WARN_INTERVAL = 100;
    /** 一次最多派几架。 */
    private static final int MAX_COUNT = 24;

    /**
     * 一次轰炸的参数。
     *
     * @param radius        在目标周围多远找设施
     * @param spawnDistance 放出来的地方离目标多远（水平）
     * @param bombs         每架带几颗炸弹
     */
    public record Options(double radius, double spawnDistance, int bombs) {
        public static final Options DEFAULT = new Options(LancerRaid.RAID_RADIUS, LancerRaid.SPAWN_DISTANCE, LancerRaid.BOMBS);
    }

    private static final Map<ResourceKey<Level>, List<Raid>> RAIDS = new HashMap<>();
    private static int nextId;
    /** 结束了几次（游戏内测试看）。 */
    private static int finished;
    /** 上一次结束时活着回去了几架（游戏内测试看）。 */
    private static int lastSurvivors = -1;

    private static final class Raid {
        final int id;
        final int system;
        final int darkFogLevel;
        final BlockPos target;
        final BlockPos from;
        final Options options;
        final LancerRaid state;
        final List<DarkFogLancer> lancers = new ArrayList<>();
        int killed;
        int survivors;
        /** 预警完放出来了。 */
        boolean released;

        Raid(int id, int system, int darkFogLevel, BlockPos target, BlockPos from, int count, Options options) {
            this.id = id;
            this.system = system;
            this.darkFogLevel = darkFogLevel;
            this.target = target;
            this.from = from;
            this.options = options;
            this.state = new LancerRaid(count);
        }
    }

    private LancerRaids() {
    }

    /**
     * 派一队枪骑去轰炸 {@code target} 一带：先预警，再在高空放出来。
     *
     * @return 计划派几架；这颗星球上已经有一队在轰炸、或者放不出来时是 0（巢穴别扣单位）
     */
    public static int launch(ServerLevel planet, BlockPos target, int count, int darkFogLevel, int system) {
        if (count <= 0 || active(planet) || !UniverseRules.darkFog(planet.getServer()).enabled()) {
            return 0;
        }
        Raid raid = start(planet, target, Math.min(count, MAX_COUNT), darkFogLevel, system, Options.DEFAULT);
        warn(planet, raid, raid.state.warningLeft());
        return raid.state.launched();
    }

    /** 这颗星球上有没有正在预警或者轰炸的枪骑。 */
    public static boolean active(ServerLevel planet) {
        List<Raid> raids = RAIDS.get(planet.dimension());
        return raids != null && !raids.isEmpty();
    }

    /**
     * 跳过预警，下一刻就放出来（调试命令、游戏内测试用）；不管这颗星球上是不是已经有一队。
     *
     * @return 这次轰炸的编号
     */
    public static int launchNow(ServerLevel planet, BlockPos target, int count, int darkFogLevel, int system, Options options) {
        Raid raid = start(planet, target, Math.max(1, Math.min(count, MAX_COUNT)), darkFogLevel, system, options);
        raid.state.skipWarning();
        return raid.id;
    }

    /** 这次轰炸还在不在（游戏内测试用）。 */
    public static boolean running(ServerLevel planet, int id) {
        List<Raid> raids = RAIDS.get(planet.dimension());
        return raids != null && raids.stream().anyMatch(raid -> raid.id == id);
    }

    /** 结束了几次轰炸（游戏内测试用）。 */
    public static int finished() {
        return finished;
    }

    /** 上一次结束的轰炸活着回去了几架（游戏内测试用），还没有结束过是 -1。 */
    public static int lastSurvivors() {
        return lastSurvivors;
    }

    /** 这颗星球上的轰炸：阶段、架数（调试命令用）。 */
    static List<Component> describe(ServerLevel planet) {
        List<Component> lines = new ArrayList<>();
        for (Raid raid : RAIDS.getOrDefault(planet.dimension(), List.of())) {
            Component phase = Component.translatable("commands.universe.darkfog.lancers.phase." + raid.state.phase().name().toLowerCase(Locale.ROOT));
            lines.add(Component.translatable("commands.universe.darkfog.lancers.line", raid.id, phase, raid.lancers.size(), raid.state.launched(),
                    raid.target.toShortString()));
        }
        return lines;
    }

    /** 这颗星球上的轰炸都撤走（调试命令用）。 */
    static int abortAll(ServerLevel planet) {
        List<Raid> raids = RAIDS.getOrDefault(planet.dimension(), List.of());
        raids.forEach(raid -> raid.state.abort());
        return raids.size();
    }

    private static Raid start(ServerLevel planet, BlockPos target, int count, int darkFogLevel, int system, Options options) {
        double angle = planet.getRandom().nextDouble() * Math.PI * 2;
        int x = (int) Math.floor(target.getX() + 0.5 + Math.cos(angle) * options.spawnDistance());
        int z = (int) Math.floor(target.getZ() + 0.5 + Math.sin(angle) * options.spawnDistance());
        int ground = planet.hasChunk(SectionPos.blockToSectionCoord(x), SectionPos.blockToSectionCoord(z))
                ? planet.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) : target.getY();
        // 底下有穹顶就在穹顶外面出现
        double dome = Shields.ceiling(planet, x + 0.5, z + 0.5).orElse(Double.NaN);
        double spawn = Double.isNaN(dome) ? ground + LancerRaid.SPAWN_HEIGHT : Math.max(ground + LancerRaid.SPAWN_HEIGHT, dome + LancerRaid.DOME_CLEARANCE * 3);
        BlockPos from = new BlockPos(x, (int) spawn, z);
        Raid raid = new Raid(nextId++, system, darkFogLevel, target.immutable(), from, count, options);
        RAIDS.computeIfAbsent(planet.dimension(), key -> new ArrayList<>()).add(raid);
        return raid;
    }

    // ---- 每刻

    static void tick(MinecraftServer server) {
        if (RAIDS.isEmpty() || !server.tickRateManager().runsNormally()) {
            return;
        }
        boolean enabled = UniverseRules.darkFog(server).enabled();
        Iterator<Map.Entry<ResourceKey<Level>, List<Raid>>> levels = RAIDS.entrySet().iterator();
        while (levels.hasNext()) {
            Map.Entry<ResourceKey<Level>, List<Raid>> entry = levels.next();
            ServerLevel level = server.getLevel(entry.getKey());
            Iterator<Raid> raids = entry.getValue().iterator();
            while (raids.hasNext()) {
                Raid raid = raids.next();
                if (level == null) {
                    // 星球卸载了：没被打下来的当飞回去了
                    raid.survivors = raid.state.launched() - raid.killed;
                    over(server, null, raid);
                    raids.remove();
                    continue;
                }
                if (!enabled) {
                    raid.state.abort();
                }
                if (tickRaid(level, raid)) {
                    raids.remove();
                }
            }
            if (entry.getValue().isEmpty()) {
                levels.remove();
            }
        }
    }

    /** @return 这次轰炸结束了 */
    private static boolean tickRaid(ServerLevel level, Raid raid) {
        raid.lancers.removeIf(lancer -> {
            if (lancer.isAlive() && !lancer.isRemoved()) {
                // 撤走的飞出了走实体的区块就收回来，不然它在那里停着不动
                if (raid.state.phase() == LancerRaid.Phase.LEAVE && !level.isPositionEntityTicking(lancer.blockPosition())) {
                    lancer.discard();
                    raid.survivors++;
                    return true;
                }
                return false;
            }
            if (lancer.getHealth() > 0) {
                raid.survivors++;
            } else {
                raid.killed++;
            }
            return true;
        });
        int alive = raid.lancers.size();
        int arrived = 0;
        int bombs = 0;
        for (DarkFogLancer lancer : raid.lancers) {
            arrived += lancer.arrived() ? 1 : 0;
            bombs += lancer.bombs();
        }
        LancerRaid.Phase before = raid.state.phase();
        LancerRaid.Phase phase = raid.state.tick(alive, arrived, bombs);
        if (before == LancerRaid.Phase.WARNING) {
            if (phase == LancerRaid.Phase.DIVE) {
                release(level, raid);
                warn(level, raid, 0);
            } else if (phase == LancerRaid.Phase.WARNING && raid.state.warningLeft() % WARN_INTERVAL == 0) {
                warn(level, raid, raid.state.warningLeft());
            }
        }
        if (phase == LancerRaid.Phase.OVER) {
            for (DarkFogLancer lancer : raid.lancers) {
                lancer.discard();
                raid.survivors++;
            }
            raid.lancers.clear();
            if (!raid.released) {
                // 预警时就叫回去了：一架都没放出来，全数还给巢穴
                raid.survivors = raid.state.launched();
            }
            over(level.getServer(), level, raid);
            return true;
        }
        long time = level.getGameTime();
        for (DarkFogLancer lancer : raid.lancers) {
            lancer.raidOrder(phase, time);
        }
        return false;
    }

    /** 预警结束：在目标上空放出来，围着出现的地方散开一点。 */
    private static void release(ServerLevel level, Raid raid) {
        int count = raid.state.launched();
        Vec3 home = Vec3.atCenterOf(raid.target);
        for (int i = 0; i < count; i++) {
            double angle = level.getRandom().nextDouble() * Math.PI * 2;
            double r = 2 + level.getRandom().nextDouble() * 5;
            Vec3 at = new Vec3(raid.from.getX() + 0.5 + Math.cos(angle) * r, raid.from.getY() + level.getRandom().nextDouble() * 6,
                    raid.from.getZ() + 0.5 + Math.sin(angle) * r);
            if (!level.isPositionEntityTicking(BlockPos.containing(at))) {
                continue;
            }
            DarkFogSpaceUnit unit = DarkFogSpaceUnit.create(level, SpaceUnitKind.LANCER, raid.system, raid.darkFogLevel, at, home,
                    DarkFogSpaceUnit.Mode.RAID);
            if (unit instanceof DarkFogLancer lancer) {
                lancer.joinRaid(raid.target, raid.options.radius(), Math.PI * 2 * i / count + level.getRandom().nextDouble() * 0.5,
                        raid.options.bombs());
                lancer.raidOrder(LancerRaid.Phase.DIVE, level.getGameTime());
                level.addFreshEntity(lancer);
                raid.lancers.add(lancer);
            }
        }
        // 放不出来的（那里的区块没在走）当没派过
        raid.survivors += count - raid.lancers.size();
        raid.released = true;
    }

    /** 预警发给这颗星球上的人：客户端第一次收到时在聊天栏写一句，HUD 上标出它们出现的地方。 */
    private static void warn(ServerLevel level, Raid raid, int ticks) {
        LancerPayloads.Warning warning = new LancerPayloads.Warning(raid.target, raid.from, raid.state.launched(), ticks);
        for (ServerPlayer player : level.players()) {
            PacketDistributor.sendToPlayer(player, warning);
        }
    }

    /** 结束：清掉 HUD，告诉这颗星球上的人结果，活着的架数还给巢穴。 */
    private static void over(MinecraftServer server, @Nullable ServerLevel level, Raid raid) {
        if (level != null) {
            Component message;
            if (raid.killed >= raid.state.launched() && raid.killed > 0) {
                message = Component.translatable("message.universe.dark_fog.lancers.destroyed").withStyle(ChatFormatting.GOLD);
            } else if (raid.killed > 0) {
                message = Component.translatable("message.universe.dark_fog.lancers.repelled", raid.killed).withStyle(ChatFormatting.GREEN);
            } else {
                message = Component.translatable("message.universe.dark_fog.lancers.left").withStyle(ChatFormatting.GRAY);
            }
            for (ServerPlayer player : level.players()) {
                PacketDistributor.sendToPlayer(player, LancerPayloads.Warning.CLEAR);
                if (raid.released) {
                    player.sendSystemMessage(message);
                }
            }
        }
        finished++;
        lastSurvivors = raid.survivors;
        DarkFogHives.onRaidOver(server, raid.system, raid.survivors);
    }

    static void clear() {
        RAIDS.clear();
    }
}
