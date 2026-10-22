package io.github.sweetcornna.universe.gametest;

import java.util.Optional;

import io.github.sweetcornna.universe.core.darkfog.HiveLayout;
import io.github.sweetcornna.universe.core.darkfog.HiveState;
import io.github.sweetcornna.universe.core.darkfog.SpaceUnitKind;
import io.github.sweetcornna.universe.core.galaxy.GalaxyGenerator;
import io.github.sweetcornna.universe.core.galaxy.Planet;
import io.github.sweetcornna.universe.core.galaxy.StarSystem;
import io.github.sweetcornna.universe.core.darkfog.BaseState;
import io.github.sweetcornna.universe.darkfog.DarkFogBases;
import io.github.sweetcornna.universe.darkfog.DarkFogData;
import io.github.sweetcornna.universe.darkfog.hive.DarkFogHiveData;
import io.github.sweetcornna.universe.darkfog.hive.DarkFogHives;
import io.github.sweetcornna.universe.rules.UniverseRules;
import io.github.sweetcornna.universe.world.planet.Planets;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 黑雾太空巢穴（ADR-0028 §八）的游戏内测试，由 {@link UniverseGameTests} 登记。用的是"不自动结算、不放出来"的测试巢穴，
 * 原来那座先收着，测完放回去；只看存档里的状态，不要太空维度、不要建筑和单位的实体。
 */
final class DarkFogHiveGameTests {
    private DarkFogHiveGameTests() {
    }

    /** 有能登陆的外星球的恒星系（先看母星系），中继站落地的测试要用。 */
    private static Optional<Planet> alienPlanet(MinecraftServer server) {
        for (StarSystem system : Planets.galaxy(server).systems()) {
            for (Planet planet : system.bodies()) {
                if (planet.landable() && !planet.id().equals(GalaxyGenerator.HOME_PLANET_ID)) {
                    return Optional.of(planet);
                }
            }
        }
        return Optional.empty();
    }

    /** 巢穴的状态存档、读档后不变。 */
    static void hiveStateSurvivesSave(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        int system = GalaxyGenerator.HOME_SYSTEM;
        HiveState hive = DarkFogHives.createInert(server, system, 5);
        try {
            hive.setThreat(0.4);
            hive.setUnits(SpaceUnitKind.LANCER, 7);
            hive.setMatter(123);
            hive.relayFailed();
            Tag saved = DarkFogHiveData.CODEC.encodeStart(NbtOps.INSTANCE, DarkFogHiveData.get(server)).getOrThrow();
            DarkFogHiveData loaded = DarkFogHiveData.CODEC.parse(NbtOps.INSTANCE, saved).getOrThrow();
            HiveState copy = loaded.hive(system).orElseThrow();
            helper.assertTrue(copy.status() == HiveState.Status.ACTIVE && copy.level() == 5, "状态或等级没存对");
            helper.assertTrue(copy.x() == hive.x() && copy.y() == hive.y() && copy.z() == hive.z(), "位置没存对");
            helper.assertTrue(Math.abs(copy.threat() - 0.4) < 1e-9 && copy.units(SpaceUnitKind.LANCER) == 7 && Math.abs(copy.matter() - 123) < 1e-9,
                    "威胁、单位数或物质没存对");
            helper.assertTrue(java.util.Arrays.equals(copy.timers(), hive.timers()), "计时没存对");
            for (int slot = 0; slot < HiveLayout.size(); slot++) {
                helper.assertTrue(copy.alive(slot) == hive.alive(slot), "槽位 " + slot + " 没存对");
            }
            helper.assertTrue(DarkFogHives.hasHive(server, system), "有巢穴却说没有");
            helper.assertTrue(DarkFogHives.summaries(server).stream().anyMatch(s -> s.system() == system && s.alive() && s.level() == 5),
                    "星图的概况里没有它");
        } finally {
            DarkFogHives.removeInert(server, system);
        }
        helper.succeed();
    }

    /** 打掉核心：恒星系里还有中继站，就等着重建、降 3 级；中继站都没了，再打掉就彻底消灭。 */
    static void coreLossRebuildsOnlyWithRelays(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        Planet planet = alienPlanet(server).orElseThrow();
        int system = planet.id().system();
        ResourceKey<Level> dimension = Planets.dimension(planet.id());
        Vec3 at = Vec3.atCenterOf(helper.absolutePos(new BlockPos(1, 2, 1)));
        HiveState hive = DarkFogHives.createInert(server, system, 6);
        BaseState relay = null;
        try {
            int before = DarkFogHives.relays(server, system);
            relay = DarkFogBases.landRelay(server, dimension, 9000 + level.getRandom().nextInt(4000), 9000, 3).orElseThrow();
            helper.assertTrue(DarkFogHives.relays(server, system) == before + 1, "落地的中继站没算进去");
            DarkFogHives.destroyForTest(level, system, HiveLayout.CORE, at);
            helper.assertTrue(hive.status() == HiveState.Status.REBUILDING, "有中继站却没等着重建：" + hive.status());
            helper.assertTrue(hive.buildings() == 0 && DarkFogHives.hasHive(server, system), "瓦解得不对");
            hive.backdate((HiveState.RESURRECT_SECONDS + 2) * 20L);
            DarkFogHives.settle(server, system);
            helper.assertTrue(hive.status() == HiveState.Status.ACTIVE, "到点了没重建：" + hive.status());
            helper.assertTrue(hive.level() == 6 - HiveState.RESURRECT_LEVEL_LOSS && hive.alive(HiveLayout.CORE), "重建后的等级或核心不对");
            // 中继站收拾掉；恒星系里要是还有别的中继站（别处落的），这一步就测不了消灭
            removeBase(server, dimension, relay);
            relay = null;
            if (DarkFogHives.relays(server, system) == 0) {
                DarkFogHives.destroyForTest(level, system, HiveLayout.CORE, at);
                helper.assertTrue(hive.status() == HiveState.Status.GONE, "没有中继站却没消灭：" + hive.status());
                helper.assertTrue(!DarkFogHives.hasHive(server, system), "消灭了还说有巢穴");
                helper.assertTrue(DarkFogHives.summaries(server).stream().noneMatch(s -> s.system() == system), "消灭了星图上还有");
            }
            helper.assertTrue(!level.getEntitiesOfClass(ItemEntity.class, new AABB(at, at).inflate(3)).isEmpty(), "核心没掉东西");
        } finally {
            if (relay != null) {
                removeBase(server, dimension, relay);
            }
            level.getEntitiesOfClass(ItemEntity.class, new AABB(at, at).inflate(4)).forEach(ItemEntity::discard);
            DarkFogHives.removeInert(server, system);
        }
        helper.succeed();
    }

    private static void removeBase(MinecraftServer server, ResourceKey<Level> dimension, BaseState base) {
        DarkFogData data = DarkFogData.get(server);
        data.planet(dimension.identifier().toString()).ifPresent(planet -> planet.bases().removeIf(b -> b.id() == base.id()));
        data.setDirty();
    }

    /** 护卫舰清剿打掉单位和建筑（不碰核心）；中继站挨打，巢穴的威胁猛涨。 */
    static void strikeAndRelayThreat(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        int system = GalaxyGenerator.HOME_SYSTEM;
        HiveState hive = DarkFogHives.createInert(server, system, 2);
        try {
            hive.setUnits(SpaceUnitKind.LANCER, 5);
            DarkFogHives.Strike strike = DarkFogHives.strike(server, system, 2);
            helper.assertTrue(strike.found() && strike.unitsKilled() == 5 && strike.buildingsDestroyed() == 1 && strike.level() == 2,
                    "清剿的结果不对：" + strike);
            helper.assertTrue(hive.alive(HiveLayout.CORE) && hive.units(SpaceUnitKind.LANCER) == 0, "清剿碰了核心或者没扣单位");
            helper.assertTrue(!DarkFogHives.strike(server, 9999, 3).found(), "没有巢穴的恒星系也清剿到了");
            double per = HiveState.relayThreatPerDamage(UniverseRules.darkFog(server));
            DarkFogHives.onRelayHurt(server, system, 100);
            helper.assertTrue(Math.abs(hive.threat() - Math.min(1, 100 * per)) < 1e-9, "打中继站没涨威胁：" + hive.threat());
        } finally {
            DarkFogHives.removeInert(server, system);
        }
        helper.succeed();
    }
}
