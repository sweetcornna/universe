package io.github.sweetcornna.universe.entity.ship;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import io.github.sweetcornna.universe.core.galaxy.Galaxy;
import io.github.sweetcornna.universe.core.galaxy.StarSystem;
import io.github.sweetcornna.universe.core.space.BlackHoles;
import io.github.sweetcornna.universe.core.space.Warp;
import io.github.sweetcornna.universe.registry.UniverseAttachments;
import io.github.sweetcornna.universe.world.planet.Planets;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * 太空里的黑洞（ADR-0024）：开船飞进视界，就被甩到星团里离这里最远的那一片恒星系中的一个。不用翘曲器、不看驱动档次，
 * 代价是船体掉上限的三成、护盾清空。同一个人每次被甩到的地方都不一样（按他被甩过几次算）。
 */
public final class ShipBlackHole {
    private static final Map<Integer, Optional<BlackHoles.BlackHole>> HOLES = new HashMap<>();
    /** 被黑洞甩出来、还没到的船（实体的 UUID，跨维度传送不变）：到了以后换一句提示。 */
    private static final Set<UUID> FLUNG = new HashSet<>();

    private ShipBlackHole() {
    }

    /** 这个恒星系里的黑洞（星团由世界种子决定，按恒星系缓存）。 */
    public static Optional<BlackHoles.BlackHole> in(MinecraftServer server, int system) {
        return HOLES.computeIfAbsent(system, index -> Planets.galaxy(server).system(index).flatMap(BlackHoles::in));
    }

    /** 太空里每刻查一次：进了视界就登记去目标恒星系，返回 true。 */
    static boolean check(ShipEntity ship, ServerLevel level, int system) {
        MinecraftServer server = level.getServer();
        Optional<BlackHoles.BlackHole> hole = in(server, system);
        if (hole.isEmpty()) {
            return false;
        }
        Galaxy galaxy = Planets.galaxy(server);
        StarSystem here = galaxy.system(system).orElseThrow();
        if (hole.get().distanceTo(ship.getX(), ship.getY(), ship.getZ()) > BlackHoles.horizon(here)) {
            return false;
        }
        Player pilot = ship.pilot();
        UUID who = pilot != null ? pilot.getUUID() : ship.getUUID();
        int jumps = pilot != null ? pilot.getData(UniverseAttachments.BLACK_HOLE_JUMPS) : 0;
        int target = BlackHoles.destination(galaxy, system, who.getMostSignificantBits() ^ who.getLeastSignificantBits(), jumps);
        if (pilot != null) {
            pilot.setData(UniverseAttachments.BLACK_HOLE_JUMPS, jumps + 1);
        }
        ship.swallowed(BlackHoles.HULL_LOSS);
        StarSystem there = galaxy.system(target).orElseThrow();
        double[] arrival = Warp.arrival(here, there, ShipTransit.layout(server, target));
        FLUNG.add(ship.getUUID());
        ShipTransit.warp(ship, target, new Vec3(arrival[0], arrival[1], arrival[2]), (float) arrival[3]);
        return true;
    }

    /** 到了：这艘船是不是被黑洞甩过来的（问过一次就忘掉）。 */
    static boolean landed(UUID ship) {
        return FLUNG.remove(ship);
    }

    static void clear() {
        HOLES.clear();
        FLUNG.clear();
    }
}
