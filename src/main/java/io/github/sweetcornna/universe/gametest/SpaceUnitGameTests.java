package io.github.sweetcornna.universe.gametest;

import io.github.sweetcornna.universe.core.darkfog.ShieldMath;
import io.github.sweetcornna.universe.core.darkfog.SpaceUnitKind;
import io.github.sweetcornna.universe.darkfog.Facilities;
import io.github.sweetcornna.universe.darkfog.entity.DarkFogBolt;
import io.github.sweetcornna.universe.darkfog.shield.PlanetaryShieldBlockEntity;
import io.github.sweetcornna.universe.darkfog.space.DarkFogSpaceUnit;
import io.github.sweetcornna.universe.darkfog.space.LancerBomb;
import io.github.sweetcornna.universe.darkfog.space.LancerRaids;
import io.github.sweetcornna.universe.factory.TallMachineBlock;
import io.github.sweetcornna.universe.registry.UniverseBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 太空单位和枪骑轰炸（ADR-0028 §八）的游戏内测试，由 {@link UniverseGameTests} 登记。都在机库场地（17×8×17）里跑，上面不封顶：
 * 单位、炸弹从场地正上方几十格来（同一列区块都在走实体，出了场地的区块不保证）。
 */
final class SpaceUnitGameTests {
    /** 不算哪座巢穴的：打完的回调不碰真的巢穴。 */
    private static final int NO_HIVE = -1;

    private SpaceUnitGameTests() {
    }

    /** 守巢的枪骑被人打了，从上面俯冲下来打那个人：打出激光弹。 */
    static void lancerFiresAtAttacker(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = player(helper);
        Vec3 start = helper.absoluteVec(new Vec3(8.5, 40, 8.5));
        DarkFogSpaceUnit lancer = DarkFogSpaceUnit.spawn(level, SpaceUnitKind.LANCER, NO_HIVE, 0, start, start, DarkFogSpaceUnit.Mode.DEFEND);
        lancer.setLastHurtByMob(player);
        AABB area = new AABB(helper.absoluteVec(Vec3.ZERO), helper.absoluteVec(new Vec3(17, 48, 17))).inflate(16);
        helper.succeedWhen(() -> {
            helper.assertTrue(lancer.focus() == player, "枪骑没去打人：在 " + lancer.position() + "，返航 " + lancer.returning());
            helper.assertFalse(level.getEntitiesOfClass(DarkFogBolt.class, area, bolt -> bolt.getOwner() == lancer).isEmpty(),
                    "枪骑没开火：在 " + lancer.position() + "，速度 " + lancer.getDeltaMovement());
            lancer.discard();
        });
    }

    /** 守巢的座头鲸被人打了，隔着几十格开炮。 */
    static void humpbackShellsAttacker(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = player(helper);
        Vec3 start = helper.absoluteVec(new Vec3(8.5, 36, 8.5));
        DarkFogSpaceUnit humpback = DarkFogSpaceUnit.spawn(level, SpaceUnitKind.HUMPBACK, NO_HIVE, 0, start, start, DarkFogSpaceUnit.Mode.DEFEND);
        helper.assertTrue(humpback.getMaxHealth() == SpaceUnitKind.HUMPBACK.baseHealth(), "座头鲸的血量不对：" + humpback.getMaxHealth());
        humpback.setLastHurtByMob(player);
        AABB area = new AABB(helper.absoluteVec(Vec3.ZERO), helper.absoluteVec(new Vec3(17, 48, 17))).inflate(16);
        helper.succeedWhen(() -> {
            helper.assertTrue(humpback.focus() == player, "座头鲸没去打人");
            helper.assertFalse(level.getEntitiesOfClass(DarkFogBolt.class, area, bolt -> bolt.getOwner() == humpback).isEmpty(),
                    "座头鲸没开炮：在 " + humpback.position());
            humpback.discard();
        });
    }

    /**
     * 一架枪骑轰炸场地中间的电弧熔炉：从 70 格高处降下来，飞到熔炉正上方扔两颗炸弹，熔炉掉耐久；
     * 炸弹扔完往上爬着撤走，轰炸结束，回调了巢穴（结束的次数加一）。
     */
    static void raidBombsAndEnds(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos smelter = helper.absolutePos(new BlockPos(8, 1, 8));
        level.setBlockAndUpdate(smelter, UniverseBlocks.ARC_SMELTER.get().defaultBlockState());
        float max = Facilities.maxHealth(level.getBlockState(smelter));
        int before = LancerRaids.finished();
        int raid = LancerRaids.launchNow(level, smelter, 1, 0, NO_HIVE, new LancerRaids.Options(8, 0, 2));
        helper.succeedWhen(() -> {
            float health = health(level, smelter);
            helper.assertTrue(health < max, "熔炉没掉耐久：" + health + "/" + max + "，轰炸还在 " + LancerRaids.running(level, raid));
            helper.assertFalse(LancerRaids.running(level, raid), "炸完了轰炸还没结束");
            helper.assertTrue(LancerRaids.finished() > before, "轰炸结束了没回调");
        });
    }

    /**
     * 展开的护盾在穹顶上截住炸弹，伤害从护盾的能量里扣，底下的熔炉不掉耐久；
     * 护盾吃不下的炸弹把穹顶打塌，剩下的伤害照样落到熔炉上。测完把能量清零，免得罩住隔壁的测试。
     */
    static void shieldStopsBombs(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = new BlockPos(8, 1, 3);
        tall(helper, pos, UniverseBlocks.PLANETARY_SHIELD_GENERATOR.get().defaultBlockState());
        PlanetaryShieldBlockEntity generator = helper.getBlockEntity(pos, PlanetaryShieldBlockEntity.class);
        BlockPos smelter = helper.absolutePos(new BlockPos(8, 1, 10));
        level.setBlockAndUpdate(smelter, UniverseBlocks.ARC_SMELTER.get().defaultBlockState());
        float max = Facilities.maxHealth(level.getBlockState(smelter));
        Vec3 drop = Vec3.atBottomCenterOf(smelter).add(0, 50, 0);
        double[] energy = new double[1];
        LancerBomb[] bomb = new LancerBomb[1];
        helper.startSequence().thenExecuteAfter(2, () -> {
            energy[0] = ShieldMath.RAISE_ENERGY + 20_000;
            generator.setEnergy(energy[0], true);
            bomb[0] = LancerBomb.drop(level, null, drop, 200);
        }).thenWaitUntil(() -> helper.assertTrue(bomb[0].isRemoved(), "炸弹还在掉：在 " + bomb[0].position())).thenExecute(() -> {
            helper.assertTrue(health(level, smelter) >= max, "护盾底下的熔炉挨炸了：" + health(level, smelter) + "/" + max);
            helper.assertTrue(generator.raised(), "挡一颗炸弹穹顶就塌了");
            helper.assertTrue(energy[0] - generator.energy() > 150_000, "炸弹的伤害没从护盾里扣：少了 " + (energy[0] - generator.energy()));
            // 第二颗比护盾剩的能量还猛
            bomb[0] = LancerBomb.drop(level, null, drop, (float) (generator.energy() / ShieldMath.JOULES_PER_DAMAGE) + 400);
        }).thenWaitUntil(() -> helper.assertTrue(bomb[0].isRemoved(), "第二颗炸弹还在掉：在 " + bomb[0].position())).thenExecute(() -> {
            helper.assertFalse(generator.raised(), "吃不下的炸弹没把穹顶打塌");
            helper.assertTrue(health(level, smelter) < max, "护盾没挡住的伤害没落到熔炉上");
        }).thenExecute(() -> generator.setEnergy(0, false)).thenSucceed();
    }

    private static ServerPlayer player(GameTestHelper helper) {
        ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
        Vec3 at = helper.absoluteVec(new Vec3(8.5, 1, 8.5));
        player.snapTo(at.x, at.y, at.z, 0, 0);
        return player;
    }

    /** 设施的耐久；炸没了（不再是设施）算 0。 */
    private static float health(ServerLevel level, BlockPos pos) {
        return Facilities.isFacility(level.getBlockState(pos)) ? Facilities.health(level, pos) : 0;
    }

    private static void tall(GameTestHelper helper, BlockPos pos, BlockState state) {
        helper.setBlock(pos, state.setValue(TallMachineBlock.HALF, DoubleBlockHalf.LOWER));
        helper.setBlock(pos.above(), state.setValue(TallMachineBlock.HALF, DoubleBlockHalf.UPPER));
    }
}
