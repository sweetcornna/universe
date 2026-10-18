package io.github.sweetcornna.universe.entity.ship;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import io.github.sweetcornna.universe.core.galaxy.BodyId;
import io.github.sweetcornna.universe.core.galaxy.Planet;
import io.github.sweetcornna.universe.core.space.SpaceLayout;
import io.github.sweetcornna.universe.registry.UniverseAttachments;
import io.github.sweetcornna.universe.world.DimensionTemplate;
import io.github.sweetcornna.universe.world.RuntimeDimensions;
import io.github.sweetcornna.universe.world.planet.PlanetCommand;
import io.github.sweetcornna.universe.world.planet.Planets;
import io.github.sweetcornna.universe.world.space.SpaceDimensions;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * 飞船在星球和太空之间的切换（M1 原型 S4，ADR-0008）。
 *
 * <p>飞出星球维度顶部就进入本恒星系的太空，出现在这颗星球的大气球外；在太空里飞进一颗可着陆星球的大气球，
 * 就切换到它的维度，从高空开始下降。落点：之前从这颗星球起飞过就落回起飞的地方，否则按进入方向换算。
 *
 * <p>切换要创建维度，而维度 tick 期间不能增删维度，所以 {@link #check} 只登记，到服务器 tick 末尾再传送。
 * 原版的跨维度传送会把乘客一起带过去、重新坐上新的飞船实体。
 */
public final class ShipTransit {
    private static final Logger LOGGER = LogUtils.getLogger();
    /** 第一次降落时离星球着陆中心的最大距离（格）。 */
    private static final double LANDING_SPREAD = 400;

    /**
     * 一次登记好的切换。
     *
     * @param yaw  到了以后的船头偏航；NaN 表示保持原来的
     * @param warp 跃迁的目标恒星系；普通的进出大气是 -1
     */
    private record Pending(ShipEntity ship, ResourceKey<Level> target, Optional<BodyId> landingOn, Vec3 position, float yaw, int warp) {
    }

    private static final List<Pending> PENDING = new ArrayList<>();
    private static final Map<Integer, List<SpaceLayout.Body>> LAYOUTS = new HashMap<>();
    private static final Map<Integer, Double> STAR_RADII = new HashMap<>();

    private ShipTransit() {
    }

    public static void registerEvents(IEventBus gameBus) {
        gameBus.addListener(EventPriority.HIGH, ServerTickEvent.Post.class, event -> process(event.getServer()));
        gameBus.addListener(ServerStoppedEvent.class, event -> {
            PENDING.clear();
            LAYOUTS.clear();
            STAR_RADII.clear();
        });
    }

    /** 恒星系在太空里的布局（按恒星系缓存，星团由世界种子决定，同一个存档里不变）。 */
    public static List<SpaceLayout.Body> layout(MinecraftServer server, int system) {
        return LAYOUTS.computeIfAbsent(system, index -> Planets.galaxy(server).system(index).map(SpaceLayout::of).orElse(List.of()));
    }

    /** 飞船每刻调用：该切换维度了就登记。 */
    static void check(ShipEntity ship, ServerLevel level) {
        for (Pending pending : PENDING) {
            if (pending.ship() == ship) {
                return;
            }
        }
        OptionalInt space = SpaceDimensions.system(level.dimension());
        if (space.isPresent()) {
            checkSpace(ship, level, space.getAsInt());
            return;
        }
        if (ship.getY() < level.getMaxY() + ShipEntity.SPACE_ALTITUDE) {
            return;
        }
        Optional<BodyId> id = Planets.bodyId(level.dimension());
        if (id.isEmpty()) {
            return;
        }
        int system = id.get().system();
        Optional<SpaceLayout.Body> body = SpaceLayout.find(layout(level.getServer(), system), id.get());
        if (body.isEmpty()) {
            return;
        }
        long departure = BlockPos.asLong((int) Math.floor(ship.getX()), 0, (int) Math.floor(ship.getZ()));
        ship.departures().put(level.dimension().identifier(), departure);
        // 船上的玩家也记一份：停船后重新组装的船从玩家身上拿回来
        for (Entity passenger : ship.getPassengers()) {
            if (passenger instanceof Player player) {
                Map<Identifier, Long> departures = new HashMap<>(player.getData(UniverseAttachments.DEPARTURES));
                departures.put(level.dimension().identifier(), departure);
                player.setData(UniverseAttachments.DEPARTURES, Map.copyOf(departures));
            }
        }
        double[] exit = SpaceLayout.exitPoint(body.get(), ship.getYRot());
        PENDING.add(new Pending(ship, SpaceDimensions.key(system), Optional.empty(), new Vec3(exit[0], exit[1], exit[2]), Float.NaN, -1));
    }

    private static void checkSpace(ShipEntity ship, ServerLevel level, int system) {
        // 恒星挡住飞船
        double ds = Math.sqrt(ship.getX() * ship.getX() + ship.getY() * ship.getY() + ship.getZ() * ship.getZ());
        double starRadius = STAR_RADII.computeIfAbsent(system,
                index -> Planets.galaxy(level.getServer()).system(index).map(SpaceLayout::starRadius).orElse(400.0));
        if (ds < starRadius) {
            pushOut(ship, 0, 0, 0, starRadius, ds);
            notifyPilot(ship, Component.translatable("message.universe.ship.too_hot"));
            return;
        }
        Optional<SpaceLayout.Body> inside = SpaceLayout.inside(layout(level.getServer(), system), ship.getX(), ship.getY(), ship.getZ());
        if (inside.isEmpty()) {
            return;
        }
        SpaceLayout.Body body = inside.get();
        Planet planet = body.planet();
        if (!planet.landable()) {
            pushOut(ship, body.x(), body.y(), body.z(), body.radius(), body.distanceTo(ship.getX(), ship.getY(), ship.getZ()));
            notifyPilot(ship, Component.translatable("message.universe.ship.cannot_land", planet.name().zh(), planet.name().en()));
            return;
        }
        ResourceKey<Level> target = Planets.dimension(body.id());
        Long departure = ship.departures().get(target.identifier());
        double x;
        double z;
        if (departure != null) {
            x = BlockPos.getX(departure) + 0.5;
            z = BlockPos.getZ(departure) + 0.5;
        } else {
            double[] offset = SpaceLayout.landingPoint(body, ship.getX(), ship.getY(), ship.getZ(), LANDING_SPREAD);
            x = offset[0];
            z = offset[1];
        }
        PENDING.add(new Pending(ship, target, Optional.of(body.id()), new Vec3(x, Double.NaN, z), Float.NaN, -1));
    }

    /** 跃迁充能满了：登记去目标恒星系的太空（ADR-0012）。 */
    static void warp(ShipEntity ship, int system, Vec3 position, float yaw) {
        PENDING.removeIf(pending -> pending.ship() == ship);
        PENDING.add(new Pending(ship, SpaceDimensions.key(system), Optional.empty(), position, yaw, system));
    }

    /** 把飞船推回球面外，朝球心的速度清零。 */
    private static void pushOut(ShipEntity ship, double cx, double cy, double cz, double radius, double distance) {
        double dx = ship.getX() - cx;
        double dy = ship.getY() - cy;
        double dz = ship.getZ() - cz;
        double d = Math.max(1e-6, distance);
        double scale = (radius + 0.5) / d;
        ship.setPos(cx + dx * scale, cy + dy * scale, cz + dz * scale);
        ship.flight().restore(0, 0, 0, ship.getYRot());
    }

    private static void notifyPilot(ShipEntity ship, Component message) {
        Player pilot = ship.pilot();
        if (pilot != null && ship.tickCount % 20 == 0) {
            pilot.sendOverlayMessage(message);
        }
    }

    private static void process(MinecraftServer server) {
        if (PENDING.isEmpty()) {
            return;
        }
        List<Pending> batch = List.copyOf(PENDING);
        PENDING.clear();
        for (Pending pending : batch) {
            ShipEntity ship = pending.ship();
            if (ship.isRemoved()) {
                continue;
            }
            try {
                travel(server, pending);
            } catch (RuntimeException e) {
                LOGGER.error("Ship transit to {} failed", pending.target().identifier(), e);
            }
        }
    }

    private static void travel(MinecraftServer server, Pending pending) {
        ShipEntity ship = pending.ship();
        ResourceKey<Level> key = pending.target();
        ServerLevel target;
        if (key == Level.OVERWORLD) {
            target = server.overworld();
        } else if (SpaceDimensions.isSpace(key)) {
            target = SpaceDimensions.getOrCreate(server, SpaceDimensions.system(key).getAsInt());
        } else {
            target = RuntimeDimensions.getOrCreate(server, key, DimensionTemplate.PLANET);
        }
        Vec3 pos = pending.position();
        if (Double.isNaN(pos.y)) {
            // 降落：没起飞过的星球以着陆中心为基准
            double x = pos.x;
            double z = pos.z;
            if (!ship.departures().containsKey(key.identifier())) {
                int[] center = landingCenter(server, key, target);
                x += center[0];
                z += center[1];
            }
            pos = new Vec3(x, target.getMaxY() + 8, z);
        }
        Vec3 velocity = ship.getDeltaMovement();
        float yaw = Float.isNaN(pending.yaw()) ? ship.getYRot() : pending.yaw();
        // 进出大气时把速度降下来，免得一头扎进地里或者冲出太远；跃迁出来时朝着恒星滑行一段
        Vec3 calmed = pending.warp() >= 0
                ? new Vec3(-Math.sin(Math.toRadians(yaw)) * 2.5, 0, Math.cos(Math.toRadians(yaw)) * 2.5)
                : new Vec3(velocity.x * 0.2, 0, velocity.z * 0.2);
        Entity moved = ship.teleport(new TeleportTransition(target, pos, calmed, yaw, 0, TeleportTransition.DO_NOTHING));
        if (moved instanceof ShipEntity arrived) {
            arrived.flight().restore(calmed.x, 0, calmed.z, yaw);
            arrived.latchVertical();
            if (pending.warp() >= 0) {
                Planets.galaxy(server).system(pending.warp()).ifPresent(system -> arrived.noticeLater(
                        Component.translatable("message.universe.warp.arrived", system.name().zh(), system.name().en())));
                ShipWarp.arrived(arrived, pending.warp());
                return;
            }
            Component where = Planets.bodyId(key).flatMap(id -> Planets.galaxy(server).body(id))
                    .map(planet -> Component.translatable("message.universe.ship.entered_planet", planet.name().zh(), planet.name().en()))
                    .orElseGet(() -> Component.translatable("message.universe.ship.entered_space"));
            arrived.noticeLater(where);
        }
    }

    private static int[] landingCenter(MinecraftServer server, ResourceKey<Level> key, ServerLevel level) {
        if (key == Level.OVERWORLD) {
            BlockPos spawn = level.getRespawnData().pos();
            return new int[] {spawn.getX(), spawn.getZ()};
        }
        return Planets.generatedPlanet(Planets.galaxy(server), key).map(PlanetCommand::landingColumn).orElse(new int[] {0, 0});
    }
}
