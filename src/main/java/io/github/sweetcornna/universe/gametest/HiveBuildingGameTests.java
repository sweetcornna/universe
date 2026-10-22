package io.github.sweetcornna.universe.gametest;

import io.github.sweetcornna.universe.core.darkfog.HiveBuildingKind;
import io.github.sweetcornna.universe.darkfog.entity.DarkFogBolt;
import io.github.sweetcornna.universe.darkfog.entity.DarkFogDamage;
import io.github.sweetcornna.universe.darkfog.hive.HiveBuilding;
import io.github.sweetcornna.universe.darkfog.hive.HivePlasmaShell;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 太空巢穴建筑（ADR-0028 §八）的游戏内测试，由 {@link UniverseGameTests} 登记，都在机库场地（17×8×17）里跑。
 * 建筑直接 {@link HiveBuilding#create} 出来加进世界，不经过巢穴的管理；塔的射程收到 16 格，免得打到隔壁的测试。
 * 假玩家不在世界的玩家列表里，用 {@link HiveBuilding#watchForTest} 交给塔。
 */
final class HiveBuildingGameTests {
    private static final double TEST_RANGE = 16;

    private HiveBuildingGameTests() {
    }

    /** 玩家打得动巢穴的建筑，掉的是真实的血量；黑雾自己的伤害打不动。碰撞箱按种类，升级时血量的比例不变。 */
    static void takesOnlyPlayerDamage(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        Vec3 at = helper.absoluteVec(new Vec3(8.5, 1, 8.5));
        player.snapTo(at.x - 5, at.y, at.z, 0, 0);
        HiveBuilding core = HiveBuilding.create(level, HiveBuildingKind.CORE, -1, 0, 10, at);
        core.setPowered(false);
        level.addFreshEntity(core);
        helper.assertTrue(Math.abs(core.getBbWidth() - 6) < 1e-3 && Math.abs(core.getBbHeight() - 5) < 1e-3, "核心的碰撞箱不对：" + core.getBbWidth());
        float max = core.maxStructure();
        helper.assertTrue(max > 1024 && Math.abs(core.structure() - max) < 1, "核心的血量不对：" + core.structure() + " / " + max);
        HiveBuilding laser = HiveBuilding.create(level, HiveBuildingKind.PHASE_LASER, -1, 1, 0, at.add(4, 0, 4));
        core.hurtServer(level, DarkFogDamage.source(level, laser, laser), 500);
        helper.assertTrue(Math.abs(core.structure() - max) < 1, "黑雾自己的伤害打掉了血：" + core.structure());
        core.hurtServer(level, level.damageSources().playerAttack(player), 600);
        float lost = max - core.structure();
        helper.assertTrue(Math.abs(lost - 600) < 1, "玩家打了 600，真实血量掉了 " + lost);
        float fraction = core.getHealth() / core.getMaxHealth();
        core.setDarkFogLevel(20);
        helper.assertTrue(Math.abs(core.getHealth() / core.getMaxHealth() - fraction) < 1e-3, "升级以后血量的比例变了");
        helper.assertTrue(core.maxStructure() > max, "升级以后血量上限没变");
        core.discard();
        helper.succeed();
    }

    /** 没能量的相位激光塔不瞄不打；通上能量就朝附近的玩家打激光弹。 */
    static void laserFiresOnlyWhenPowered(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        Vec3 at = helper.absoluteVec(new Vec3(12.5, 1, 8.5));
        player.snapTo(at.x, at.y, at.z, 0, 0);
        HiveBuilding tower = HiveBuilding.create(level, HiveBuildingKind.PHASE_LASER, -1, 2, 0, helper.absoluteVec(new Vec3(3.5, 1, 8.5)));
        tower.restrictRange(TEST_RANGE);
        tower.watchForTest(player);
        tower.setPowered(false);
        level.addFreshEntity(tower);
        AABB area = new AABB(helper.absoluteVec(Vec3.ZERO), helper.absoluteVec(new Vec3(17, 8, 17))).inflate(16);
        helper.runAfterDelay(40, () -> {
            helper.assertTrue(tower.aimedEntity() < 0, "没能量的塔瞄准了");
            helper.assertTrue(level.getEntitiesOfClass(DarkFogBolt.class, area, bolt -> bolt.getOwner() == tower).isEmpty(), "没能量的塔开火了");
            tower.setPowered(true);
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(tower.powered(), "还没通能量");
            helper.assertTrue(tower.aimedEntity() == player.getId(), "塔没瞄准玩家");
            helper.assertTrue(!level.getEntitiesOfClass(DarkFogBolt.class, area, bolt -> bolt.getOwner() == tower).isEmpty(), "塔没开火");
        });
    }

    /** 等离子塔不理离得更近的创造模式玩家，朝生存模式的玩家打炮弹；炮弹在玩家身边炸开，玩家掉血。 */
    static void plasmaTowerHitsPlayer(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Player watcher = helper.makeMockPlayer(GameType.CREATIVE);
        Vec3 near = helper.absoluteVec(new Vec3(7.5, 1, 8.5));
        watcher.snapTo(near.x, near.y, near.z, 0, 0);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        Vec3 at = helper.absoluteVec(new Vec3(13.5, 1, 8.5));
        player.snapTo(at.x, at.y, at.z, 0, 0);
        float health = player.getHealth();
        HiveBuilding tower = HiveBuilding.create(level, HiveBuildingKind.PLASMA_TOWER, -1, 3, 0, helper.absoluteVec(new Vec3(3.5, 1, 8.5)));
        tower.restrictRange(TEST_RANGE);
        tower.watchForTest(watcher);
        tower.watchForTest(player);
        level.addFreshEntity(tower);
        AABB area = new AABB(helper.absoluteVec(Vec3.ZERO), helper.absoluteVec(new Vec3(17, 8, 17))).inflate(16);
        helper.onEachTick(() -> helper.assertTrue(tower.aimedEntity() != watcher.getId(), "塔瞄准了创造模式的玩家"));
        helper.succeedWhen(() -> {
            helper.assertTrue(tower.aimedEntity() == player.getId(), "塔没瞄准玩家");
            helper.assertTrue(player.getHealth() < health, "玩家没掉血，炮弹还有 "
                    + level.getEntitiesOfClass(HivePlasmaShell.class, area, shell -> shell.getOwner() == tower).size() + " 发在飞");
        });
    }
}
