package io.github.sweetcornna.universe.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import io.github.sweetcornna.universe.core.darkfog.BaseLayout;
import io.github.sweetcornna.universe.core.darkfog.BaseState;
import io.github.sweetcornna.universe.core.darkfog.Flight;
import io.github.sweetcornna.universe.core.darkfog.HiveState;
import io.github.sweetcornna.universe.core.darkfog.ShieldMath;
import io.github.sweetcornna.universe.core.galaxy.GalaxyGenerator;
import io.github.sweetcornna.universe.core.galaxy.Planet;
import io.github.sweetcornna.universe.core.galaxy.StarSystem;
import io.github.sweetcornna.universe.darkfog.DarkFogBases;
import io.github.sweetcornna.universe.darkfog.DarkFogData;
import io.github.sweetcornna.universe.darkfog.entity.DarkFogDamage;
import io.github.sweetcornna.universe.darkfog.flight.DarkFogCraft;
import io.github.sweetcornna.universe.darkfog.flight.DarkFogFlightData;
import io.github.sweetcornna.universe.darkfog.flight.DarkFogFlights;
import io.github.sweetcornna.universe.darkfog.hive.DarkFogHiveData;
import io.github.sweetcornna.universe.darkfog.hive.DarkFogHives;
import io.github.sweetcornna.universe.darkfog.shield.ShieldData;
import io.github.sweetcornna.universe.world.planet.Planets;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 巢穴派出去的中继站和种子（ADR-0028 §八）的游戏内测试，由 {@link UniverseGameTests} 登记。只看存档里的飞行和基地，
 * 星球、太空维度不用加载：用调试命令那一套直接跳到到达。
 */
final class DarkFogFlightGameTests {
    private DarkFogFlightGameTests() {
    }

    /** 一颗能登陆的外星球，所在的恒星系里没有丢了中继站的基地（不然中继站先去补那里）。 */
    private static Optional<Planet> landingPlanet(MinecraftServer server) {
        for (StarSystem system : Planets.galaxy(server).systems()) {
            if (!lostRelays(server, system.index()).isEmpty()) {
                continue;
            }
            for (Planet planet : system.bodies()) {
                if (planet.landable() && !planet.id().equals(GalaxyGenerator.HOME_PLANET_ID)) {
                    return Optional.of(planet);
                }
            }
        }
        return Optional.empty();
    }

    /** 恒星系里丢了中继站、等着补的基地。 */
    private static List<BaseState> lostRelays(MinecraftServer server, int system) {
        return DarkFogBases.bases(server, system).stream()
                .filter(ref -> ref.dimension() != Level.OVERWORLD && ref.base().status() == BaseState.Status.ACTIVE && !ref.base().relayAlive())
                .map(DarkFogBases.BaseRef::base)
                .toList();
    }

    private static Set<Integer> baseIds(MinecraftServer server, ResourceKey<Level> dimension) {
        return DarkFogData.get(server).planet(dimension.identifier().toString())
                .map(planet -> planet.bases().stream().map(BaseState::id).collect(Collectors.toSet())).orElse(Set.of());
    }

    private static List<BaseState> newBases(MinecraftServer server, ResourceKey<Level> dimension, Set<Integer> before) {
        return DarkFogData.get(server).planet(dimension.identifier().toString())
                .map(planet -> planet.bases().stream().filter(base -> !before.contains(base.id())).toList()).orElse(List.of());
    }

    private static void removeBases(MinecraftServer server, ResourceKey<Level> dimension, Set<Integer> before) {
        DarkFogData data = DarkFogData.get(server);
        data.planet(dimension.identifier().toString()).ifPresent(planet -> planet.bases().removeIf(base -> !before.contains(base.id())));
        data.setDirty();
    }

    /** 中继站到了：在外星球上落地，多出一处同等级、带着中继站的基地；这次飞行没了。 */
    static void relayLandsNewBase(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        Planet planet = landingPlanet(server).orElseThrow();
        ResourceKey<Level> dimension = Planets.dimension(planet.id());
        String key = dimension.identifier().toString();
        Set<Integer> before = baseIds(server, dimension);
        DarkFogFlightData data = DarkFogFlightData.get(server);
        try {
            data.setFacility(key, 20000 + helper.getLevel().getRandom().nextInt(2000), 20000);
            Flight flight = DarkFogFlights.launchRelayTo(server, planet.id().system(), 4, Vec3.ZERO, dimension).orElseThrow();
            helper.assertTrue(DarkFogFlights.relaysInFlight(server, planet.id().system()) >= 1, "派出去的中继站没算进去");
            helper.assertTrue(DarkFogFlights.launchRelayTo(server, planet.id().system(), 4, Vec3.ZERO, Level.OVERWORLD).isEmpty(), "往母星派了中继站");
            DarkFogFlights.skip(server, flight.id(), Flight.Phase.DONE);
            helper.assertTrue(data.flight(flight.id()).isEmpty(), "落地了飞行还在：" + flight.phase(server.overworld().getGameTime()));
            List<BaseState> landed = newBases(server, dimension, before);
            helper.assertTrue(landed.size() == 1, "多出来的基地不是一处：" + landed.size());
            BaseState base = landed.getFirst();
            helper.assertTrue(base.status() == BaseState.Status.ACTIVE && base.relayAlive() && base.level() == 4, "落地的基地不对");
            double fromFacility = Math.hypot(base.x() - data.facility(key).orElseThrow()[0], base.z() - 20000);
            helper.assertTrue(fromFacility >= 200 && fromFacility <= 500, "落点离玩家的设施 " + Math.round(fromFacility) + " 格");
        } finally {
            removeBases(server, dimension, before);
            data.removeFacility(key);
        }
        helper.succeed();
    }

    /** 基地的中继站被打掉了：派来的中继站去补上，不另建基地。 */
    static void relayRestoresLostRelay(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        Planet planet = landingPlanet(server).orElseThrow();
        ResourceKey<Level> dimension = Planets.dimension(planet.id());
        Set<Integer> before = baseIds(server, dimension);
        try {
            BaseState base = DarkFogBases.landRelay(server, dimension, -24000 - helper.getLevel().getRandom().nextInt(2000), -24000, 2).orElseThrow();
            base.destroy(BaseLayout.RELAY);
            helper.assertFalse(base.relayAlive(), "中继站没打掉");
            Flight flight = DarkFogFlights.launchRelayTo(server, planet.id().system(), 2, Vec3.ZERO, dimension).orElseThrow();
            DarkFogFlights.skip(server, flight.id(), Flight.Phase.DONE);
            helper.assertTrue(base.relayAlive(), "丢了中继站的基地没补上");
            helper.assertTrue(newBases(server, dimension, before).size() == 1, "补中继站时又建了基地");
            helper.assertTrue(DarkFogFlightData.get(server).flight(flight.id()).isEmpty(), "补完了飞行还在");
        } finally {
            removeBases(server, dimension, before);
        }
        helper.succeed();
    }

    /** 落点全被护盾盖着：不落，在轨道上等一阵（还在飞、试过一次）；护盾撤了再到就落下。 */
    static void relayAvoidsShields(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        Planet planet = landingPlanet(server).orElseThrow();
        ResourceKey<Level> dimension = Planets.dimension(planet.id());
        String key = dimension.identifier().toString();
        Set<Integer> before = baseIds(server, dimension);
        DarkFogFlightData data = DarkFogFlightData.get(server);
        ShieldData shields = ShieldData.get(server);
        List<BlockPos> placed = new ArrayList<>();
        int cx = 40000;
        int cz = 40000;
        try {
            // 每 200 格一台满能量的护盾，盖住玩家设施周围 600 格
            for (int dx = -600; dx <= 600; dx += 200) {
                for (int dz = -600; dz <= 600; dz += 200) {
                    BlockPos pos = new BlockPos(cx + dx, 64, cz + dz);
                    shields.set(shields.register(dimension, pos), new ShieldMath.State(ShieldMath.FULL_ENERGY, true));
                    placed.add(pos);
                }
            }
            data.setFacility(key, cx, cz);
            Flight flight = DarkFogFlights.launchRelayTo(server, planet.id().system(), 3, Vec3.ZERO, dimension).orElseThrow();
            DarkFogFlights.skip(server, flight.id(), Flight.Phase.DONE);
            long now = server.overworld().getGameTime();
            helper.assertTrue(data.flight(flight.id()).isPresent(), "护盾盖着也落下了，或者直接放弃了");
            helper.assertTrue(flight.attempts() == 1 && flight.phase(now) == Flight.Phase.CRUISE, "没在轨道上等：" + flight.phase(now));
            helper.assertTrue(newBases(server, dimension, before).isEmpty(), "护盾盖着还建了基地");
            placed.forEach(pos -> shields.remove(dimension, pos));
            placed.clear();
            DarkFogFlights.skip(server, flight.id(), Flight.Phase.DONE);
            helper.assertTrue(data.flight(flight.id()).isEmpty() && newBases(server, dimension, before).size() == 1, "护盾撤了还是没落下");
        } finally {
            placed.forEach(pos -> shields.remove(dimension, pos));
            removeBases(server, dimension, before);
            data.removeFacility(key);
        }
        helper.succeed();
    }

    /** 种子到了没有巢穴的恒星系：那里长出一座巢穴，这次飞行没了。 */
    static void seedFoundsHive(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        int from = GalaxyGenerator.HOME_SYSTEM;
        int target = -1;
        for (StarSystem system : Planets.galaxy(server).systems()) {
            if (system.index() != from && DarkFogHiveData.get(server).hive(system.index()).isEmpty()) {
                target = system.index();
                break;
            }
        }
        helper.assertTrue(target >= 0, "找不到没有巢穴的恒星系");
        int to = target;
        Flight flight = DarkFogFlights.launchSeedTo(server, from, to, 9, Vec3.ZERO).orElseThrow();
        try {
            helper.assertTrue(DarkFogFlights.seedsInFlight(server, from) >= 1, "放出的种子没算进去");
            helper.assertTrue(DarkFogFlights.map(server).seeds().stream().anyMatch(seed -> seed.id() == flight.id() && seed.to() == to),
                    "星图的数据里没有这颗种子");
            DarkFogFlights.skip(server, flight.id(), Flight.Phase.DONE);
            helper.assertTrue(DarkFogFlightData.get(server).flight(flight.id()).isEmpty(), "种子到了飞行还在");
            helper.assertTrue(DarkFogHives.hasHive(server, to), "种子到了没长出巢穴");
            helper.assertTrue(DarkFogHives.summaries(server).stream().anyMatch(s -> s.system() == to && s.level() == 9), "新巢穴的等级不对");
        } finally {
            DarkFogFlights.remove(server, flight.id());
            DarkFogHives.removeInert(server, to);
        }
        helper.succeed();
    }

    /** 飞着的中继站挨打，巢穴的威胁涨；打掉了，这次飞行就没了，掉东西。黑雾自己的伤害不吃。 */
    static void craftShotDownEndsFlight(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        Planet planet = landingPlanet(server).orElseThrow();
        int system = planet.id().system();
        Vec3 at = Vec3.atCenterOf(helper.absolutePos(new BlockPos(1, 4, 1)));
        HiveState hive = DarkFogHives.createInert(server, system, 3);
        Flight flight = DarkFogFlights.launchRelayTo(server, system, 3, Vec3.ZERO, Planets.dimension(planet.id())).orElseThrow();
        try {
            DarkFogCraft craft = DarkFogFlights.spawnForTest(level, flight, at);
            helper.assertTrue(!craft.seed() && !craft.highAltitude(), "起飞的中继站种类不对");
            float health = craft.getHealth();
            craft.hurtServer(level, DarkFogDamage.source(level, craft, null), 50);
            helper.assertTrue(craft.getHealth() == health, "黑雾自己的伤害也吃");
            double threat = hive.threat();
            craft.hurtServer(level, level.damageSources().generic(), 40);
            helper.assertTrue(craft.getHealth() < health && flight.health() < 1, "挨打没掉血");
            helper.assertTrue(hive.threat() > threat, "中继站挨打，巢穴的威胁没涨");
            craft.kill(level);
            helper.assertTrue(DarkFogFlightData.get(server).flight(flight.id()).isEmpty(), "打掉了飞行还在");
            helper.assertTrue(!level.getEntitiesOfClass(ItemEntity.class, new AABB(at, at).inflate(4)).isEmpty(), "打掉了没掉东西");
        } finally {
            DarkFogFlights.remove(server, flight.id());
            level.getEntitiesOfClass(ItemEntity.class, new AABB(at, at).inflate(6)).forEach(ItemEntity::discard);
            DarkFogHives.removeInert(server, system);
        }
        helper.succeed();
    }
}
