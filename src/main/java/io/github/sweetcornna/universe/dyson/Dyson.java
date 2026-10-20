package io.github.sweetcornna.universe.dyson;

import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.WeakHashMap;

import io.github.sweetcornna.universe.core.dyson.DysonGrid;
import io.github.sweetcornna.universe.core.dyson.DysonSphere;
import io.github.sweetcornna.universe.core.galaxy.BodyId;
import io.github.sweetcornna.universe.core.galaxy.GalaxyGenerator;
import io.github.sweetcornna.universe.core.galaxy.StarSystem;
import io.github.sweetcornna.universe.factory.research.Research;
import io.github.sweetcornna.universe.network.DysonEditPayload;
import io.github.sweetcornna.universe.network.DysonSyncPayload;
import io.github.sweetcornna.universe.story.Story;
import io.github.sweetcornna.universe.world.planet.Planets;
import io.github.sweetcornna.universe.world.space.SpaceDimensions;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 戴森球的服务端入口（ADR-0016）：每刻开头结算接收站的分电，每秒推进所有戴森球并存盘，把玩家所在恒星系的戴森球
 * 同步给他（换了恒星系马上发，之后有变化时每 5 秒最多发一次），处理编辑器的修改。
 */
public final class Dyson {
    /** 应力系统的两级科技。 */
    public static final String STRESS_1 = "dyson_stress_1";
    public static final String STRESS_2 = "dyson_stress_2";
    private static final int ADVANCE_INTERVAL = 20;
    private static final int SYNC_INTERVAL = 100;
    private static final Map<ServerPlayer, Sent> SENT = new WeakHashMap<>();

    /** 给玩家发过的：哪个恒星系、什么内容（签名）、什么时候发的。 */
    private record Sent(int system, long signature, long tick) {
    }

    private Dyson() {
    }

    public static void registerEvents(IEventBus gameBus) {
        gameBus.addListener(ServerTickEvent.Pre.class, event -> {
            for (DysonSphere sphere : DysonData.get(event.getServer()).spheres().values()) {
                sphere.roll();
            }
        });
        gameBus.addListener(ServerTickEvent.Post.class, event -> tick(event.getServer()));
        gameBus.addListener(ServerStoppedEvent.class, event -> SENT.clear());
    }

    private static void tick(MinecraftServer server) {
        long now = server.overworld().getGameTime();
        if (now % ADVANCE_INTERVAL != 0) {
            return;
        }
        DysonData data = DysonData.get(server);
        if (!data.spheres().isEmpty()) {
            for (DysonSphere sphere : data.spheres().values()) {
                sphere.advance(now);
            }
            data.setDirty();
            Story.checkShells(server);
        }
        sync(server, now, false);
    }

    /** 维度所在的恒星系：星球、太空按它们的恒星系，主世界是母星系，下界、末地这些没有。 */
    public static OptionalInt systemOf(ResourceKey<Level> dimension) {
        OptionalInt space = SpaceDimensions.system(dimension);
        if (space.isPresent()) {
            return space;
        }
        if (dimension == Level.OVERWORLD) {
            return OptionalInt.of(GalaxyGenerator.HOME_SYSTEM);
        }
        Optional<BodyId> body = Planets.bodyId(dimension);
        return body.isPresent() ? OptionalInt.of(body.get().system()) : OptionalInt.empty();
    }

    /** 恒星系的戴森球，没有就新建一个（自带一条戴森云轨道）。 */
    public static DysonSphere sphere(MinecraftServer server, int system) {
        DysonData data = DysonData.get(server);
        DysonSphere sphere = data.spheres().get(system);
        if (sphere == null) {
            sphere = fresh(server, system);
            sphere.advance(server.overworld().getGameTime());
            data.spheres().put(system, sphere);
            data.setDirty();
        }
        return sphere;
    }

    /** 已经有的戴森球，不新建。 */
    public static Optional<DysonSphere> existing(MinecraftServer server, int system) {
        return Optional.ofNullable(DysonData.get(server).spheres().get(system));
    }

    private static DysonSphere fresh(MinecraftServer server, int system) {
        StarSystem star = Planets.galaxy(server).systems().get(system);
        double innermost = star.planets().isEmpty() ? 0.3 : star.planets().getFirst().orbit().radius();
        for (var planet : star.planets()) {
            innermost = Math.min(innermost, planet.orbit().radius());
        }
        return DysonSphere.create(DysonSphere.lightFactor(star.star().luminosity()), DysonSphere.maxRadius(innermost));
    }

    /** 现在能建的纬度（全服共享的研究进度）。 */
    public static double latitudeLimit(MinecraftServer server) {
        Level level = server.overworld();
        int stress = Research.isUnlocked(level, STRESS_2) ? 2 : Research.isUnlocked(level, STRESS_1) ? 1 : 0;
        return DysonSphere.latitudeLimit(stress);
    }

    // ---- 同步

    private static long signature(DysonSphere sphere, double limit) {
        return sphere.revision() * 31 + sphere.sails() * 17 + sphere.rockets().size() * 7 + Math.round(sphere.lastDrawn()) * 3
                + Double.hashCode(limit);
    }

    /**
     * @param force 不管多久前发过、内容变没变，都发
     */
    static void sync(MinecraftServer server, long now, boolean force) {
        double limit = latitudeLimit(server);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            OptionalInt system = systemOf(player.level().dimension());
            if (system.isEmpty()) {
                SENT.remove(player);
                continue;
            }
            int index = system.getAsInt();
            Optional<DysonSphere> existing = existing(server, index);
            DysonSphere sphere = existing.orElseGet(() -> fresh(server, index));
            long signature = existing.isPresent() ? signature(sphere, limit) : Long.MIN_VALUE + Double.hashCode(limit);
            Sent sent = SENT.get(player);
            boolean newSystem = sent == null || sent.system != index;
            if (!force && !newSystem && (sent.signature == signature || now - sent.tick < SYNC_INTERVAL)) {
                continue;
            }
            SENT.put(player, new Sent(index, signature, now));
            PacketDistributor.sendToPlayer(player, DysonSyncPayload.of(index, sphere, limit));
        }
    }

    // ---- 编辑器

    public static void handleEdit(DysonEditPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        OptionalInt system = systemOf(player.level().dimension());
        if (system.isEmpty()) {
            return;
        }
        MinecraftServer server = player.level().getServer();
        DysonSphere sphere = sphere(server, system.getAsInt());
        DysonGrid.Template template = DysonGrid.Template.byOrdinal(payload.template());
        boolean changed = switch (payload.action()) {
            case ADD_ORBIT -> sphere.addOrbit(payload.radius(), payload.inclination(), payload.ascendingNode()) > 0;
            case UPDATE_ORBIT -> sphere.updateOrbit(payload.id(), payload.radius(), payload.inclination(), payload.ascendingNode());
            case REMOVE_ORBIT -> sphere.removeOrbit(payload.id());
            case ADD_LAYER -> sphere.addLayer(payload.radius(), payload.inclination(), payload.ascendingNode(), template, payload.density()) > 0;
            case UPDATE_LAYER -> sphere.updateLayer(payload.id(), payload.radius(), payload.inclination(), payload.ascendingNode(), template,
                    payload.density());
            case REMOVE_LAYER -> sphere.removeLayer(payload.id());
        };
        if (changed) {
            DysonData.get(server).setDirty();
        }
        // 改不成（比如已经开始建的层改半径）也回一份，让编辑器回到服务端的状态
        sync(server, server.overworld().getGameTime(), true);
    }
}
