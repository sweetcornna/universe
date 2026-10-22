package io.github.sweetcornna.universe.gametest;

import io.github.sweetcornna.universe.core.darkfog.Support;
import io.github.sweetcornna.universe.core.darkfog.Tactics;
import io.github.sweetcornna.universe.core.darkfog.UnitKind;
import io.github.sweetcornna.universe.darkfog.Taunts;
import io.github.sweetcornna.universe.darkfog.defense.JammerTowerBlockEntity;
import io.github.sweetcornna.universe.darkfog.defense.SignalTowerBlockEntity;
import io.github.sweetcornna.universe.darkfog.defense.ThrownUnit;
import io.github.sweetcornna.universe.darkfog.entity.DarkFogUnit;
import io.github.sweetcornna.universe.factory.TallMachineBlock;
import io.github.sweetcornna.universe.factory.power.FuelGeneratorBlockEntity;
import io.github.sweetcornna.universe.registry.UniverseBlocks;
import io.github.sweetcornna.universe.registry.UniverseEntities;
import io.github.sweetcornna.universe.registry.UniverseItems;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.Vec3;

/** 信号塔、干扰塔、投掷物（ADR-0028 §五）的游戏内测试，由 {@link UniverseGameTests} 登记。 */
final class DarkFogSupportGameTests {
    private DarkFogSupportGameTests() {
    }

    /**
     * 干扰塔装了干扰胶囊、通了电，把范围里的突袭者减速到四成，用掉一个胶囊。
     * 突袭者关了 AI，免得飞出范围。
     */
    static void jammerSlowsRaider(GameTestHelper helper) {
        tall(helper, new BlockPos(1, 1, 1), UniverseBlocks.TESLA_TOWER.get().defaultBlockState());
        BlockPos star = new BlockPos(5, 1, 1);
        tall(helper, star, UniverseBlocks.ARTIFICIAL_STAR.get().defaultBlockState());
        helper.getBlockEntity(star, FuelGeneratorBlockEntity.class)
                .setItem(FuelGeneratorBlockEntity.FUEL_SLOT, new ItemStack(UniverseItems.ANTIMATTER_FUEL_ROD.get(), 4));
        BlockPos jammerPos = new BlockPos(3, 1, 4);
        tall(helper, jammerPos, UniverseBlocks.JAMMER_TOWER.get().defaultBlockState());
        JammerTowerBlockEntity jammer = helper.getBlockEntity(jammerPos, JammerTowerBlockEntity.class);
        jammer.setItem(JammerTowerBlockEntity.CAPSULE_SLOT, new ItemStack(UniverseItems.JAMMING_CAPSULE.get(), 2));
        DarkFogUnit raider = raider(helper, new Vec3(1.5, 1.5, 5.5));
        raider.setNoAi(true);
        helper.succeedWhen(() -> {
            Support.Jam jam = jammer.lastJam();
            helper.assertTrue(jam != null, "干扰塔还没发出干扰：状态 " + jammer.data().get(JammerTowerBlockEntity.STATUS));
            helper.assertTrue(jam.radius() == Support.CAPSULE.radius() && Math.abs(jam.factor() - Support.CAPSULE.factor()) < 1e-6,
                    "装了胶囊的干扰不对：" + jam);
            helper.assertTrue(Math.abs(raider.jamFactor() - Support.CAPSULE.factor()) < 1e-6, "突袭者的减速倍率：" + raider.jamFactor());
            helper.assertTrue(jammer.getItem(JammerTowerBlockEntity.CAPSULE_SLOT).getCount() == 1, "没用掉一个胶囊");
            helper.assertTrue(jammer.data().get(JammerTowerBlockEntity.CHARGE) < Support.CAPSULE_PULSES, "胶囊的次数没减");
        });
    }

    /**
     * 信号塔没电时不嘲讽；人造恒星装上燃料棒以后通了电，突袭者转去打信号塔。
     * 信号塔自己就是一座塔，旁边的人造恒星在它的覆盖范围里。
     */
    static void signalTowerDrawsDarkFog(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos towerPos = new BlockPos(1, 1, 1);
        tall(helper, towerPos, UniverseBlocks.SIGNAL_TOWER.get().defaultBlockState());
        BlockPos star = new BlockPos(5, 1, 1);
        tall(helper, star, UniverseBlocks.ARTIFICIAL_STAR.get().defaultBlockState());
        SignalTowerBlockEntity tower = helper.getBlockEntity(towerPos, SignalTowerBlockEntity.class);
        BlockPos absolute = helper.absolutePos(towerPos);
        DarkFogUnit raider = raider(helper, new Vec3(5.5, 2, 5.5));
        helper.startSequence().thenExecuteAfter(30, () -> {
            helper.assertFalse(tower.powered(), "没燃料也通了电");
            helper.assertFalse(Taunts.signalTowers(level).contains(absolute), "没电的信号塔也在嘲讽");
            helper.getBlockEntity(star, FuelGeneratorBlockEntity.class)
                    .setItem(FuelGeneratorBlockEntity.FUEL_SLOT, new ItemStack(UniverseItems.ANTIMATTER_FUEL_ROD.get(), 4));
        }).thenWaitUntil(() -> {
            helper.assertTrue(tower.powered(), "装了燃料还没通电");
            helper.assertTrue(Taunts.signalTowers(level).contains(absolute), "通了电的信号塔不在嘲讽的名单里");
            helper.assertTrue(Taunts.nearestTaunt(level, raider.position(), Taunts.RANGE).filter(absolute::equals).isPresent(),
                    "突袭者旁边找不到这座信号塔");
            helper.assertTrue(raider.order() == Tactics.Order.TAUNT, "突袭者的打法：" + raider.order());
        }).thenSucceed();
    }

    /** 爆破单元落在地上炸开：旁边的突袭者挨了伤害（16 以内，没死），紧挨着的玻璃和脚下的石头都还在。 */
    static void explosiveUnitSparesBlocks(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        // 场地的地板在测试世界里放不出来（脚下只有屏障），落点自己铺一块石头
        for (BlockPos pos : BlockPos.betweenClosed(2, 0, 2, 4, 0, 4)) {
            helper.setBlock(pos, Blocks.SMOOTH_STONE);
        }
        helper.setBlock(new BlockPos(2, 1, 3), Blocks.GLASS);
        DarkFogUnit raider = raider(helper, new Vec3(4.5, 1, 3.5));
        raider.setNoAi(true);
        float health = raider.getHealth();
        Player thrower = helper.makeMockPlayer(GameType.SURVIVAL);
        ThrownUnit unit = new ThrownUnit(UniverseEntities.THROWN_UNIT.get(), level);
        unit.setItem(new ItemStack(UniverseItems.EXPLOSIVE_UNIT.get()));
        unit.setOwner(thrower);
        Vec3 drop = helper.absoluteVec(new Vec3(3.5, 2.2, 3.5));
        unit.setPos(drop.x, drop.y, drop.z);
        unit.setDeltaMovement(0, -0.6, 0);
        level.addFreshEntity(unit);
        helper.succeedWhen(() -> {
            helper.assertTrue(unit.isRemoved(), "爆破单元还没炸");
            helper.assertTrue(raider.isAlive() && raider.getHealth() < health && raider.getHealth() >= health - Support.Charge.EXPLOSIVE.damage(0),
                    "突袭者的生命：" + health + " -> " + raider.getHealth());
            helper.assertBlockPresent(Blocks.GLASS, new BlockPos(2, 1, 3));
            helper.assertBlockPresent(Blocks.SMOOTH_STONE, new BlockPos(3, 0, 3));
        });
    }

    /** 一只 0 级、不属于任何基地的突袭者，守着自己出生的地方。 */
    private static DarkFogUnit raider(GameTestHelper helper, Vec3 pos) {
        Vec3 absolute = helper.absoluteVec(pos);
        return DarkFogUnit.spawn(helper.getLevel(), UnitKind.RAIDER, -1, 0, absolute, BlockPos.containing(absolute), DarkFogUnit.Mode.DEFEND, null);
    }

    private static void tall(GameTestHelper helper, BlockPos pos, BlockState state) {
        helper.setBlock(pos, state.setValue(TallMachineBlock.HALF, DoubleBlockHalf.LOWER));
        helper.setBlock(pos.above(), state.setValue(TallMachineBlock.HALF, DoubleBlockHalf.UPPER));
    }
}
