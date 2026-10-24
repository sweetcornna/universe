package io.github.sweetcornna.universe.civ;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

import io.github.sweetcornna.universe.core.civ.NpcRole;
import io.github.sweetcornna.universe.core.civ.Race;
import io.github.sweetcornna.universe.core.fossil.DigSiteLayout;
import io.github.sweetcornna.universe.core.poi.Landmark;
import io.github.sweetcornna.universe.core.poi.LandmarkLayout;
import io.github.sweetcornna.universe.core.poi.LandmarkType;
import io.github.sweetcornna.universe.core.poi.SettlementLayout;
import io.github.sweetcornna.universe.core.procgen.NameGenerator;
import io.github.sweetcornna.universe.core.procgen.Rng;
import io.github.sweetcornna.universe.core.procgen.Seeds;
import io.github.sweetcornna.universe.core.space.StationLayout;
import io.github.sweetcornna.universe.entity.civ.AlienNpc;
import io.github.sweetcornna.universe.perf.ServerPerf;
import io.github.sweetcornna.universe.poi.Landmarks;
import io.github.sweetcornna.universe.registry.UniverseBlocks;
import io.github.sweetcornna.universe.registry.UniverseEntities;
import io.github.sweetcornna.universe.station.Stations;
import io.github.sweetcornna.universe.world.planet.Planets;
import io.github.sweetcornna.universe.world.space.SpaceDimensions;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * 外星 NPC 的管理器（ADR-0027 §三）：玩家走到 {@value #SPAWN_RANGE} 格以内时按站位放 NPC，{@value #KEEP_RANGE} 格内没人了收走。
 * NPC 不存档，种族、名字都按站位的种子算（访客的种子加上日期，每天换人），所以重进世界还是那几个人，一个站位只放一个。
 *
 * <ul>
 *   <li>空间站：大厅里 4 个（{@link StationLayout#NPC_SPOTS}）：主导种族的访客、随机种族的访客、技术商、公会使者。</li>
 *   <li>交易站：交易终端旁边 1 个访客。</li>
 *   <li>聚落：2 个访客、1 个技术商。</li>
 *   <li>化石收藏家（ADR-0034 §三）：空间站大厅一个，古生物发掘场收藏家帐篷门口一个。</li>
 * </ul>
 * 种族都是这个恒星系的主导种族，只有空间站的第二个访客是随机的。收藏家四处收化石，种族按站位的种子挑，
 * 没有主导种族的恒星系里也有。
 */
public final class NpcSpawner {
    private static final int SPAWN_RANGE = 48;
    private static final int KEEP_RANGE = 64;
    /** 空间站里化石收藏家站的地方（本地 x、z）：大厅靠机库那一侧，避开柱子和设备。 */
    static final int[] STATION_COLLECTOR = {-4, 13};
    /** 交易站的站位离交易终端这么近以内才算地标真的盖起来了（中心落在湖里的地标不放）。 */
    private static final int TERMINAL_REACH = 3;
    /** 放着的 NPC，按维度、站位的键。 */
    private static final Map<ResourceKey<Level>, Map<String, AlienNpc>> LIVE = new HashMap<>();

    /** 一个站位：脚下的位置、朝向、站的是谁。 */
    record Spot(String key, Vec3 pos, float yaw, Race race, NpcRole role, long seed) {
    }

    private NpcSpawner() {
    }

    public static void registerEvents(IEventBus gameBus) {
        gameBus.addListener(ServerTickEvent.Post.class, event -> ServerPerf.time("npcs", () -> tick(event.getServer())));
        gameBus.addListener(ServerStoppedEvent.class, event -> LIVE.clear());
    }

    private static void tick(MinecraftServer server) {
        int now = server.getTickCount();
        if (now % 40 == 0) {
            sweep(server);
        }
        if (now % 20 != 0) {
            return;
        }
        long day = Dialogues.day(server);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.isSpectator() || !(player.level() instanceof ServerLevel level)) {
                continue;
            }
            List<Spot> spots = SpaceDimensions.isSpace(level.dimension()) ? stationSpots(level, player.position(), day)
                    : landmarkSpots(level, player.position(), day);
            for (Spot spot : spots) {
                ensure(level, spot);
            }
        }
    }

    // ---- 站位

    /** 空间站大厅里离 {@code near} 够近的站位。 */
    static List<Spot> stationSpots(ServerLevel level, Vec3 near, long day) {
        OptionalInt system = SpaceDimensions.system(level.dimension());
        if (system.isEmpty()) {
            return List.of();
        }
        MinecraftServer server = level.getServer();
        Optional<StationLayout.Site> found = Stations.site(Planets.galaxy(server), system.getAsInt());
        if (found.isEmpty()) {
            return List.of();
        }
        StationLayout.Site site = found.get();
        double reach = SPAWN_RANGE + StationLayout.RADIUS;
        if (near.distanceToSqr(site.x() + 0.5, site.y(), site.z() + 0.5) > reach * reach) {
            return List.of();
        }
        Optional<Race> dominant = Civ.dominant(level);
        long galaxySeed = Planets.galaxySeed(server);
        int[] facing = site.toWorld(-4, 1, 0);
        List<Spot> spots = new ArrayList<>();
        stationCollector(level, site, near, galaxySeed, system.getAsInt(), facing).ifPresent(spots::add);
        if (dominant.isEmpty()) {
            return spots;
        }
        for (int slot = 0; slot < StationLayout.NPC_SPOTS.length; slot++) {
            int[] local = StationLayout.NPC_SPOTS[slot];
            int[] world = site.toWorld(local[0], 1, local[1]);
            Vec3 pos = new Vec3(world[0] + 0.5, world[1], world[2] + 0.5);
            BlockPos floor = new BlockPos(world[0], world[1] - 1, world[2]);
            if (pos.distanceToSqr(near) > SPAWN_RANGE * SPAWN_RANGE || !level.isLoaded(floor) || level.getBlockState(floor).isAir()) {
                continue;
            }
            NpcRole role = switch (slot) {
                case 0, 1 -> NpcRole.VISITOR;
                case 2 -> NpcRole.TECH_MERCHANT;
                default -> NpcRole.GUILD_ENVOY;
            };
            long seed = seed(Seeds.derive(galaxySeed, "npc/station/" + system.getAsInt() + "/" + slot), role, day);
            Race race = slot == 1 ? Rng.of(Seeds.derive(seed, "race")).pick(Race.values()) : dominant.get();
            spots.add(new Spot("station:" + system.getAsInt() + ":" + slot, pos, yaw(pos, facing[0] + 0.5, facing[2] + 0.5), race, role, seed));
        }
        return spots;
    }

    /** 空间站大厅里的化石收藏家。 */
    private static Optional<Spot> stationCollector(ServerLevel level, StationLayout.Site site, Vec3 near, long galaxySeed, int system,
            int[] facing) {
        int[] world = site.toWorld(STATION_COLLECTOR[0], 1, STATION_COLLECTOR[1]);
        Vec3 pos = new Vec3(world[0] + 0.5, world[1], world[2] + 0.5);
        BlockPos floor = new BlockPos(world[0], world[1] - 1, world[2]);
        if (pos.distanceToSqr(near) > SPAWN_RANGE * SPAWN_RANGE || !level.isLoaded(floor) || level.getBlockState(floor).isAir()) {
            return Optional.empty();
        }
        long seed = Seeds.derive(galaxySeed, "npc/station/" + system + "/collector");
        Race race = Rng.of(Seeds.derive(seed, "race")).pick(Race.values());
        return Optional.of(new Spot("station:" + system + ":collector", pos, yaw(pos, facing[0] + 0.5, facing[2] + 0.5), race,
                NpcRole.FOSSIL_COLLECTOR, seed));
    }

    /** 星球上离 {@code near} 够近的交易站、聚落、发掘场的站位。 */
    static List<Spot> landmarkSpots(ServerLevel level, Vec3 near, long day) {
        Optional<LandmarkLayout> layout = Landmarks.layout(level);
        if (layout.isEmpty()) {
            return List.of();
        }
        Optional<Race> dominant = Civ.dominant(level);
        List<Spot> spots = new ArrayList<>();
        double reach = SPAWN_RANGE + Math.max(landmark(LandmarkType.SETTLEMENT), landmark(LandmarkType.DIG_SITE));
        for (Landmark landmark : layout.get().within(near.x, near.z, reach)) {
            if (landmark.type() == LandmarkType.DIG_SITE) {
                digSiteCollector(level, landmark, near, dominant).ifPresent(spots::add);
                continue;
            }
            if (dominant.isEmpty()) {
                continue;
            }
            boolean post = landmark.type() == LandmarkType.TRADING_POST;
            if (!post && landmark.type() != LandmarkType.SETTLEMENT) {
                continue;
            }
            List<int[]> columns = post ? List.of(SettlementLayout.tradingPostSpot(landmark)) : SettlementLayout.npcSpots(landmark);
            for (int slot = 0; slot < columns.size(); slot++) {
                int[] column = columns.get(slot);
                double dx = column[0] + 0.5 - near.x;
                double dz = column[1] + 0.5 - near.z;
                if (dx * dx + dz * dz > SPAWN_RANGE * SPAWN_RANGE) {
                    continue;
                }
                NpcRole role = !post && slot >= 2 ? NpcRole.TECH_MERCHANT : NpcRole.VISITOR;
                long seed = seed(Seeds.derive(landmark.seed(), "npc/" + slot), role, day);
                String key = "landmark:" + landmark.type().id() + ":" + landmark.key() + ":" + slot;
                // 已经站着今天该站的人：不用再找地面、找终端
                if (present(level, key, seed, dominant.get())) {
                    continue;
                }
                OptionalInt y = standY(level, column[0], column[1]);
                if (y.isEmpty() || post && !nearTerminal(level, new BlockPos(column[0], y.getAsInt(), column[1]))) {
                    continue;
                }
                Vec3 pos = new Vec3(column[0] + 0.5, y.getAsInt(), column[1] + 0.5);
                spots.add(new Spot(key, pos, yaw(pos, landmark.x() + 0.5, landmark.z() + 0.5), dominant.get(), role, seed));
            }
        }
        return spots;
    }

    /** 发掘场收藏家帐篷门口的收藏家：发掘场真的盖起来了（中心不在水里）才放。 */
    private static Optional<Spot> digSiteCollector(ServerLevel level, Landmark landmark, Vec3 near, Optional<Race> dominant) {
        int[] column = DigSiteLayout.collectorSpot(landmark);
        double dx = column[0] + 0.5 - near.x;
        double dz = column[1] + 0.5 - near.z;
        if (dx * dx + dz * dz > SPAWN_RANGE * SPAWN_RANGE) {
            return Optional.empty();
        }
        long seed = Seeds.derive(landmark.seed(), "npc/collector");
        Race race = dominant.orElseGet(() -> Rng.of(Seeds.derive(seed, "race")).pick(Race.values()));
        String key = "landmark:" + landmark.type().id() + ":" + landmark.key() + ":collector";
        if (present(level, key, seed, race)) {
            return Optional.empty();
        }
        LevelChunk center = level.getChunkSource().getChunkNow(landmark.x() >> 4, landmark.z() >> 4);
        if (center == null) {
            return Optional.empty();
        }
        int top = center.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, landmark.x() & 15, landmark.z() & 15);
        if (!level.getFluidState(new BlockPos(landmark.x(), top - 1, landmark.z())).isEmpty()) {
            return Optional.empty();
        }
        OptionalInt y = standY(level, column[0], column[1]);
        if (y.isEmpty() || !level.getFluidState(new BlockPos(column[0], y.getAsInt(), column[1])).isEmpty()) {
            return Optional.empty();
        }
        Vec3 pos = new Vec3(column[0] + 0.5, y.getAsInt(), column[1] + 0.5);
        // 面朝探方
        return Optional.of(new Spot(key, pos, yaw(pos, landmark.x() + 0.5, landmark.z() + 0.5), race, NpcRole.FOSSIL_COLLECTOR, seed));
    }

    private static int landmark(LandmarkType type) {
        return type.radius() + 8;
    }

    /** 访客每天换人：种子加上日期；技术商、公会使者一直是那一个。 */
    private static long seed(long spot, NpcRole role, long day) {
        return role == NpcRole.VISITOR ? Seeds.derive(spot, day) : spot;
    }

    /** 从站位朝 (tx, tz) 看的朝向；离得太近时朝南。 */
    private static float yaw(Vec3 from, double tx, double tz) {
        double dx = tx - from.x;
        double dz = tz - from.z;
        if (dx * dx + dz * dz < 1) {
            return 0;
        }
        return (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90;
    }

    /**
     * 站位脚下的高度：区块没加载时为空。最高的方块下面是空的（站位在屋里），就往下找地板；否则站在最高的方块上。
     * 要有两格高的空当。
     */
    static OptionalInt standY(ServerLevel level, int x, int z) {
        LevelChunk chunk = level.getChunkSource().getChunkNow(x >> 4, z >> 4);
        if (chunk == null) {
            return OptionalInt.empty();
        }
        int top = chunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x & 15, z & 15);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos(x, top - 1, z);
        int feet = top + 1;
        if (!solid(level, cursor)) {
            // 屋顶下面：往下找地板
            feet = Integer.MIN_VALUE;
            for (int y = top - 1; y > top - 12 && y > level.getMinY(); y--) {
                if (solid(level, cursor.setY(y - 1))) {
                    feet = y;
                    break;
                }
            }
            if (feet == Integer.MIN_VALUE) {
                return OptionalInt.empty();
            }
        }
        if (solid(level, cursor.setY(feet)) || solid(level, cursor.setY(feet + 1))) {
            return OptionalInt.empty();
        }
        return OptionalInt.of(feet);
    }

    private static boolean solid(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return !state.getCollisionShape(level, pos).isEmpty();
    }

    private static boolean nearTerminal(ServerLevel level, BlockPos pos) {
        for (BlockPos at : BlockPos.betweenClosed(pos.offset(-TERMINAL_REACH, -1, -TERMINAL_REACH), pos.offset(TERMINAL_REACH, 2, TERMINAL_REACH))) {
            if (level.getBlockState(at).is(UniverseBlocks.OUTPOST_TRADE_TERMINAL.get())) {
                return true;
            }
        }
        return false;
    }

    // ---- 放和收

    /** 站位上已经站着今天该站的人。 */
    private static boolean present(ServerLevel level, String key, long seed, Race race) {
        Map<String, AlienNpc> live = LIVE.get(level.dimension());
        AlienNpc npc = live == null ? null : live.get(key);
        return npc != null && !npc.isRemoved() && npc.seed() == seed && npc.race() == race;
    }

    /** 站位上没人就放一个；站着的不是今天该站的人（访客换了一天）就换掉。 */
    private static void ensure(ServerLevel level, Spot spot) {
        if (present(level, spot.key(), spot.seed(), spot.race())) {
            return;
        }
        Map<String, AlienNpc> live = LIVE.computeIfAbsent(level.dimension(), key -> new HashMap<>());
        AlienNpc existing = live.get(spot.key());
        if (existing != null && !existing.isRemoved()) {
            existing.discard();
        }
        live.remove(spot.key());
        AlienNpc npc = UniverseEntities.ALIEN_NPC.get().create(level, EntitySpawnReason.EVENT);
        if (npc == null) {
            return;
        }
        NameGenerator.Name name = Civ.tongue(level.getServer(), spot.race()).name(spot.seed());
        npc.setup(spot.race(), spot.role(), spot.seed(), spot.key(), name, spot.pos(), spot.yaw());
        if (level.addFreshEntity(npc)) {
            live.put(spot.key(), npc);
        }
    }

    /**
     * 附近 {@value #KEEP_RANGE} 格没人的 NPC 收走。区块卸载时不存档的实体不会被移出世界（只是不再更新），NPC 自己检查不到，
     * 所以这里按表统一清。
     */
    private static void sweep(MinecraftServer server) {
        LIVE.forEach((dimension, live) -> {
            ServerLevel level = server.getLevel(dimension);
            live.values().removeIf(npc -> {
                if (npc.isRemoved()) {
                    return true;
                }
                if (level == null || level.getNearestPlayer(npc.getX(), npc.getY(), npc.getZ(), KEEP_RANGE, false) == null) {
                    npc.discard();
                    return true;
                }
                return false;
            });
        });
    }
}
