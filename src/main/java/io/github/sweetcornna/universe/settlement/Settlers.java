package io.github.sweetcornna.universe.settlement;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.github.sweetcornna.universe.civ.Civ;
import io.github.sweetcornna.universe.core.civ.NpcRole;
import io.github.sweetcornna.universe.core.poi.SettlementLayout;
import io.github.sweetcornna.universe.core.procgen.NameGenerator;
import io.github.sweetcornna.universe.core.procgen.Seeds;
import io.github.sweetcornna.universe.core.settlement.BuildingType;
import io.github.sweetcornna.universe.core.settlement.Plot;
import io.github.sweetcornna.universe.core.settlement.Settlement;
import io.github.sweetcornna.universe.entity.civ.AlienNpc;
import io.github.sweetcornna.universe.registry.UniverseEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * 聚落的居民（ADR-0034 §二）：人口多起来，聚落里站着的外星人跟着多。原来那三个（两个访客、一个技术商）由 {@code NpcSpawner} 放，
 * 这里放其余的：先站在开着的建筑门口，再站到广场边上，站满预留的位置为止。开着的市场门口另外站一位公会使节，不算人口。
 * 和 {@code NpcSpawner} 一样不存档，玩家走近了放、走远了收。
 */
final class Settlers {
    private static final double SPAWN_RANGE = 56;
    private static final double KEEP_RANGE = 72;
    /** 原来就有的那几个算进人口里。 */
    private static final int EXISTING = 2;
    /** 广场边上的站位（本地坐标）。 */
    private static final int[][] PLAZA = {{-4, -3}, {4, -3}, {-5, 2}, {5, 2}, {-2, -5}, {2, -5}, {-6, 0}, {6, 0}};
    /** 放着的居民：聚落编号#序号 → NPC。 */
    private static final Map<String, AlienNpc> LIVE = new HashMap<>();

    private Settlers() {
    }

    static void clear() {
        LIVE.values().forEach(AlienNpc::discard);
        LIVE.clear();
    }

    static void forget(String settlement) {
        LIVE.entrySet().removeIf(e -> {
            if (e.getKey().startsWith(settlement + "#")) {
                e.getValue().discard();
                return true;
            }
            return false;
        });
    }

    /** 一个站位：脚下的位置和朝向。 */
    private record Spot(Vec3 pos, float yaw) {
    }

    static void tick(MinecraftServer server) {
        sweep(server);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!(player.level() instanceof ServerLevel level) || player.isSpectator()) {
                continue;
            }
            for (SettlementData.Entry entry : SettlementData.get(server).in(Settlements.dimension(level))) {
                Settlement s = entry.settlement();
                if (!s.owned() || s.distance(player.getX(), player.getZ()) > SPAWN_RANGE) {
                    continue;
                }
                List<Spot> spots = spots(level, entry);
                int count = Math.min(spots.size(), Math.max(0, s.population() - EXISTING));
                for (int k = 0; k < spots.size(); k++) {
                    String key = s.id() + "#" + k;
                    AlienNpc npc = LIVE.get(key);
                    if (k >= count) {
                        // 走了的人
                        if (npc != null) {
                            npc.discard();
                            LIVE.remove(key);
                        }
                        continue;
                    }
                    if (npc != null && !npc.isRemoved()) {
                        continue;
                    }
                    spawn(level, s, key, k, spots.get(k));
                }
                envoys(level, entry);
            }
        }
    }

    /** 开着的市场门口的公会使节（ADR-0034 §二）：站在门的另一边，和居民的站位错开。 */
    private static void envoys(ServerLevel level, SettlementData.Entry entry) {
        Settlement s = entry.settlement();
        for (int i = 0; i < s.plotCount(); i++) {
            Plot plot = s.plot(i);
            String key = s.id() + "#envoy" + i;
            boolean market = plot.active() && plot.type() == BuildingType.MARKET && entry.plotY()[i] != Integer.MIN_VALUE;
            AlienNpc npc = LIVE.get(key);
            if (!market) {
                if (npc != null) {
                    npc.discard();
                    LIVE.remove(key);
                }
                continue;
            }
            if (npc != null && !npc.isRemoved()) {
                continue;
            }
            BlockPos at = SettlementBuilder.frame(entry, i).at(1, 1, -SettlementLayout.PLOT_HALF - 1);
            int x = at.getX();
            int z = at.getZ();
            int index = i;
            stand(level, x, z).ifPresent(y -> {
                AlienNpc envoy = UniverseEntities.ALIEN_NPC.get().create(level, EntitySpawnReason.EVENT);
                if (envoy == null) {
                    return;
                }
                long seed = Seeds.derive(s.seed(), "envoy/" + index);
                NameGenerator.Name name = Civ.tongue(level.getServer(), s.race()).name(seed);
                envoy.setup(s.race(), NpcRole.GUILD_ENVOY, seed, "settler:" + key, name, new Vec3(x + 0.5, y, z + 0.5),
                        yaw(x + 0.5, z + 0.5, s.x() + 0.5, s.z() + 0.5));
                if (level.addFreshEntity(envoy)) {
                    LIVE.put(key, envoy);
                }
            });
        }
    }

    /** 站位：开着的建筑门口一个，再是广场边上。 */
    private static List<Spot> spots(ServerLevel level, SettlementData.Entry entry) {
        Settlement s = entry.settlement();
        List<Spot> spots = new ArrayList<>();
        for (int i = 0; i < s.plotCount(); i++) {
            Plot plot = s.plot(i);
            if (!plot.active() || entry.plotY()[i] == Integer.MIN_VALUE) {
                continue;
            }
            SettlementBuilder.Frame frame = SettlementBuilder.frame(entry, i);
            BlockPos at = frame.at(-1, 1, -SettlementLayout.PLOT_HALF - 1);
            stand(level, at.getX(), at.getZ()).ifPresent(y -> spots.add(new Spot(new Vec3(at.getX() + 0.5, y, at.getZ() + 0.5),
                    yaw(at.getX() + 0.5, at.getZ() + 0.5, s.x() + 0.5, s.z() + 0.5))));
        }
        for (int[] local : PLAZA) {
            int[] w = SettlementLayout.world(s.landmark(), local[0], local[1]);
            stand(level, w[0], w[1]).ifPresent(y -> spots.add(new Spot(new Vec3(w[0] + 0.5, y, w[1] + 0.5),
                    yaw(w[0] + 0.5, w[1] + 0.5, s.x() + 0.5, s.z() + 0.5))));
        }
        return spots;
    }

    /** 站得住的高度：区块加载了、脚下实心、头上两格空。 */
    private static java.util.OptionalInt stand(ServerLevel level, int x, int z) {
        BlockPos column = new BlockPos(x, 0, z);
        if (!level.isLoaded(column)) {
            return java.util.OptionalInt.empty();
        }
        int feet = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        BlockPos pos = new BlockPos(x, feet, z);
        if (!level.getBlockState(pos).getCollisionShape(level, pos).isEmpty() || !level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty()) {
            return java.util.OptionalInt.empty();
        }
        return java.util.OptionalInt.of(feet);
    }

    private static float yaw(double x, double z, double tx, double tz) {
        double dx = tx - x;
        double dz = tz - z;
        if (dx * dx + dz * dz < 1) {
            return 0;
        }
        return (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90;
    }

    private static void spawn(ServerLevel level, Settlement s, String key, int slot, Spot spot) {
        AlienNpc npc = UniverseEntities.ALIEN_NPC.get().create(level, EntitySpawnReason.EVENT);
        if (npc == null) {
            return;
        }
        long seed = Seeds.derive(s.seed(), "settler/" + slot);
        NameGenerator.Name name = Civ.tongue(level.getServer(), s.race()).name(seed);
        npc.setup(s.race(), NpcRole.VISITOR, seed, "settler:" + key, name, spot.pos(), spot.yaw());
        if (level.addFreshEntity(npc)) {
            LIVE.put(key, npc);
        }
    }

    /** 附近没人了的收走。 */
    private static void sweep(MinecraftServer server) {
        LIVE.values().removeIf(npc -> {
            if (npc.isRemoved()) {
                return true;
            }
            if (!(npc.level() instanceof ServerLevel level) || level.getNearestPlayer(npc.getX(), npc.getY(), npc.getZ(), KEEP_RANGE, false) == null) {
                npc.discard();
                return true;
            }
            return false;
        });
    }
}
