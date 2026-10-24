package io.github.sweetcornna.universe.deepspace;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import io.github.sweetcornna.universe.core.deepspace.DebrisField;
import io.github.sweetcornna.universe.core.deepspace.DeepSpaceSites.Kind;
import io.github.sweetcornna.universe.core.deepspace.DeepSpaceSites.Site;
import io.github.sweetcornna.universe.world.space.SpaceDimensions;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/**
 * 碎片带（ADR-0034 §四）：玩家飞近了，把还没打掉的碎片放出来；离所有玩家都远了碎片自己消失，下次飞近再放。
 * 打掉的记在 {@link DeepSpaceData} 里；整片打光以后过两天重新长出来（又一场事故的残骸漂过来了）。
 */
public final class DebrisFields {
    /** 碎片离最近的玩家这么近才放出来（格）。 */
    static final double SPAWN_RADIUS = 200;
    /** 整片打光以后多久重新长出来（刻）。 */
    static final long RESET_TICKS = 48000;
    private static final Map<String, DebrisChunk> LIVE = new HashMap<>();
    private static final Map<Long, List<DebrisField.Slot>> SLOTS = new HashMap<>();

    private DebrisFields() {
    }

    static void clear() {
        LIVE.clear();
        SLOTS.clear();
    }

    static List<DebrisField.Slot> slots(long seed) {
        return SLOTS.computeIfAbsent(seed, DebrisField::slots);
    }

    /** 每 10 刻：玩家附近的碎片带放碎片。 */
    static void tick(MinecraftServer server) {
        Iterator<DebrisChunk> live = LIVE.values().iterator();
        while (live.hasNext()) {
            if (live.next().isRemoved()) {
                live.remove();
            }
        }
        DeepSpaceData data = DeepSpaceData.get(server);
        for (ServerLevel level : server.getAllLevels()) {
            var system = SpaceDimensions.system(level.dimension());
            if (system.isEmpty() || level.players().isEmpty()) {
                continue;
            }
            for (Site site : DeepSpace.sites(server, system.getAsInt())) {
                if (site.kind() != Kind.DEBRIS
                        || level.getNearestPlayer(site.x(), site.y(), site.z(), site.kind().extent() + SPAWN_RADIUS, false) == null) {
                    continue;
                }
                String key = DeepSpaceData.key(system.getAsInt(), site.index());
                DeepSpaceData.State state = data.state(key, Kind.DEBRIS);
                if (state.resetAt > 0 && level.getGameTime() >= state.resetAt) {
                    state.cleared.clear();
                    state.resetAt = 0;
                    data.changed();
                }
                for (DebrisField.Slot slot : slots(site.seed())) {
                    String id = key + "#" + slot.index();
                    if (state.cleared.get(slot.index()) || LIVE.containsKey(id)) {
                        continue;
                    }
                    double x = site.x() + slot.dx();
                    double y = site.y() + slot.dy();
                    double z = site.z() + slot.dz();
                    if (level.getNearestPlayer(x, y, z, SPAWN_RADIUS, false) == null) {
                        continue;
                    }
                    DebrisChunk chunk = DebrisChunk.create(level, slot.seed(), slot.size(), new Vec3(x, y, z), key, slot.index());
                    if (level.addFreshEntity(chunk)) {
                        LIVE.put(id, chunk);
                    }
                }
            }
        }
    }

    /** 一块碎片没了（打碎了、被牵引光束吸走了）：属于碎片带的记下来。 */
    static void onGone(ServerLevel level, DebrisChunk chunk) {
        String field = chunk.field();
        if (field == null || chunk.slot() < 0) {
            return;
        }
        LIVE.remove(field + "#" + chunk.slot());
        DeepSpaceData data = DeepSpaceData.get(level.getServer());
        DeepSpaceData.State state = data.state(field, Kind.DEBRIS);
        state.cleared.set(chunk.slot());
        int total = data.find(field).map(s -> {
            String[] parts = field.split("/");
            if (parts.length != 2) {
                return 0;
            }
            try {
                int system = Integer.parseInt(parts[0]);
                int index = Integer.parseInt(parts[1]);
                List<Site> sites = DeepSpace.sites(level.getServer(), system);
                return index < sites.size() ? slots(sites.get(index).seed()).size() : 0;
            } catch (NumberFormatException e) {
                return 0;
            }
        }).orElse(0);
        if (total > 0 && state.cleared.cardinality() >= total && state.resetAt == 0) {
            state.resetAt = level.getGameTime() + RESET_TICKS;
        }
        data.changed();
    }

    /** 开发命令：在 {@code center} 放一片碎片带（不记进存档，打掉了也不记）。 */
    static int spawnLoose(ServerLevel level, Vec3 center, long seed) {
        int count = 0;
        for (DebrisField.Slot slot : DebrisField.slots(seed)) {
            // 开发用的碎片带缩小一半，免得飞半天
            Vec3 at = center.add(slot.dx() * 0.4, slot.dy() * 0.4, slot.dz() * 0.4);
            if (level.addFreshEntity(DebrisChunk.create(level, slot.seed(), slot.size(), at, null, -1))) {
                count++;
            }
        }
        return count;
    }
}
