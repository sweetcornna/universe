package io.github.sweetcornna.universe.gametest;

import io.github.sweetcornna.universe.core.darkfog.BuildingKind;
import io.github.sweetcornna.universe.core.darkfog.Tactics;
import io.github.sweetcornna.universe.core.darkfog.UnitKind;
import io.github.sweetcornna.universe.darkfog.Facilities;
import io.github.sweetcornna.universe.darkfog.entity.DarkFogBolt;
import io.github.sweetcornna.universe.darkfog.entity.DarkFogBuilding;
import io.github.sweetcornna.universe.darkfog.entity.DarkFogUnit;
import io.github.sweetcornna.universe.registry.UniverseBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** 黑雾单位和建筑（ADR-0028 §二）的游戏内测试，由 {@link UniverseGameTests} 登记，都在机库场地（17×8×17）里跑。 */
final class DarkFogUnitGameTests {
    private DarkFogUnitGameTests() {
    }

    /** 进攻模式的突袭者往目标飞，贴上去用电弧打设施，设施掉耐久。 */
    static void raiderStrikesFacility(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos smelter = helper.absolutePos(new BlockPos(13, 1, 8));
        level.setBlockAndUpdate(smelter, UniverseBlocks.ARC_SMELTER.get().defaultBlockState());
        float max = Facilities.maxHealth(level.getBlockState(smelter));
        Vec3 start = helper.absoluteVec(new Vec3(2.5, 2.5, 8.5));
        DarkFogUnit raider = DarkFogUnit.spawn(level, UnitKind.RAIDER, -1, 0, start, smelter.offset(300, 0, 0), DarkFogUnit.Mode.ATTACK, smelter);
        helper.succeedWhen(() -> {
            helper.assertTrue(raider.isAlive(), "突袭者没了");
            float health = level.getBlockState(smelter).isAir() ? 0 : Facilities.health(level, smelter);
            helper.assertTrue(health < max, "设施没掉耐久，突袭者在 " + raider.position() + "，在做 " + raider.order());
            helper.assertTrue(raider.position().distanceTo(start) > 3, "突袭者没动");
        });
    }

    /** 目标在 20 格高的台子上：地面出发的突袭者往上飞，飞到台面那么高。 */
    static void raiderClimbsToRaisedTarget(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        // 场地是 17×8×17 的机库：台子高出地面 5 格，都在场地里面（出了场地的区块不保证在走实体）
        BlockPos floor = helper.absolutePos(new BlockPos(12, 5, 8));
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                level.setBlockAndUpdate(floor.offset(dx, 0, dz), UniverseBlocks.FOUNDATION.get().defaultBlockState());
            }
        }
        BlockPos smelter = floor.above();
        level.setBlockAndUpdate(smelter, UniverseBlocks.ARC_SMELTER.get().defaultBlockState());
        Vec3 start = helper.absoluteVec(new Vec3(1.5, 2.2, 8.5));
        DarkFogUnit raider = DarkFogUnit.spawn(level, UnitKind.RAIDER, -1, 0, start, smelter.offset(300, 0, 0), DarkFogUnit.Mode.ATTACK, smelter);
        Vec3 target = Vec3.atCenterOf(smelter);
        helper.succeedWhen(() -> helper.assertTrue(raider.getY() > floor.getY() + 0.5 && Math.hypot(raider.getX() - target.x, raider.getZ() - target.z) < 4,
                "突袭者没往上飞：在 " + raider.position() + "，在做 " + raider.order() + "，目标 " + smelter + " ticks " + raider.tickCount
                        + " entityTicking " + level.isPositionEntityTicking(raider.blockPosition()) + " ai " + raider.isEffectiveAi()
                        + " removed " + raider.isRemoved() + " alive " + raider.isAlive()));
    }

    /** 被人打了的游骑兵追着打回去：朝那个人打出激光弹。 */
    static void rangerShootsBack(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
        Vec3 at = helper.absoluteVec(new Vec3(8.5, 1, 8.5));
        player.snapTo(at.x, at.y, at.z, 0, 0);
        Vec3 start = helper.absoluteVec(new Vec3(3.5, 5, 3.5));
        DarkFogUnit ranger = DarkFogUnit.spawn(level, UnitKind.RANGER, -1, 0, start, BlockPos.containing(start), DarkFogUnit.Mode.DEFEND, null);
        ranger.setLastHurtByMob(player);
        AABB area = new AABB(helper.absoluteVec(Vec3.ZERO), helper.absoluteVec(new Vec3(17, 8, 17))).inflate(16);
        helper.succeedWhen(() -> {
            helper.assertTrue(ranger.order() == Tactics.Order.PLAYER, "游骑兵没去打人，在做 " + ranger.order());
            helper.assertTrue(!level.getEntitiesOfClass(DarkFogBolt.class, area, bolt -> bolt.getOwner() == ranger).isEmpty(),
                    "游骑兵没开火");
        });
    }

    /** 守卫者离家太远就往回飞。 */
    static void guardianReturnsHome(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Vec3 start = helper.absoluteVec(new Vec3(8.5, 5, 8.5));
        BlockPos home = BlockPos.containing(start).offset(-56, 0, 0);
        DarkFogUnit guardian = DarkFogUnit.spawn(level, UnitKind.GUARDIAN, -1, 0, start, home, DarkFogUnit.Mode.DEFEND, null);
        helper.succeedWhen(() -> {
            helper.assertTrue(guardian.order() == Tactics.Order.RETURN, "守卫者没回家，在做 " + guardian.order());
            helper.assertTrue(guardian.getX() < start.x - 3, "守卫者没往家飞：" + guardian.getX() + "，出发时 " + start.x);
        });
    }

    /** 没能量的激光塔不开火；通上能量就烧射程里的设施。 */
    static void unpoweredLaserTowerHoldsFire(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos smelter = helper.absolutePos(new BlockPos(12, 1, 8));
        level.setBlockAndUpdate(smelter, UniverseBlocks.ARC_SMELTER.get().defaultBlockState());
        float max = Facilities.maxHealth(level.getBlockState(smelter));
        DarkFogBuilding tower = DarkFogBuilding.create(level, BuildingKind.LASER_TOWER, -1, 0, helper.absoluteVec(new Vec3(4.5, 1, 8.5)));
        tower.setPowered(false);
        level.addFreshEntity(tower);
        helper.runAfterDelay(40, () -> {
            helper.assertTrue(Facilities.health(level, smelter) >= max, "没能量的激光塔开火了");
            helper.assertTrue(tower.aimedBlock().isEmpty(), "没能量的激光塔瞄准了");
            tower.setPowered(true);
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(tower.powered(), "还没通能量");
            float health = level.getBlockState(smelter).isAir() ? 0 : Facilities.health(level, smelter);
            helper.assertTrue(health < max, "通了能量的激光塔没烧设施");
        });
    }
}
