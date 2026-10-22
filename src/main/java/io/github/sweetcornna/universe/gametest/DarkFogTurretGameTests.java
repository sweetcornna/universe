package io.github.sweetcornna.universe.gametest;

import io.github.sweetcornna.universe.core.darkfog.UnitKind;
import io.github.sweetcornna.universe.darkfog.entity.DarkFogUnit;
import io.github.sweetcornna.universe.darkfog.turret.TurretBlockEntity;
import io.github.sweetcornna.universe.factory.TallMachineBlock;
import io.github.sweetcornna.universe.factory.power.FuelGeneratorBlockEntity;
import io.github.sweetcornna.universe.registry.UniverseBlocks;
import io.github.sweetcornna.universe.registry.UniverseItems;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.Vec3;

/** 炮塔（ADR-0028 §五）的游戏内测试，由 {@link UniverseGameTests} 登记，场地是 17×8×17 的机库。 */
final class DarkFogTurretGameTests {
    /** 炮塔放在场地中间，感应塔、人造恒星在它西边。 */
    private static final BlockPos TURRET = new BlockPos(8, 1, 8);

    private DarkFogTurretGameTests() {
    }

    /** 通电、装了机枪弹箱的高斯炮塔打死一个突袭者，用掉弹药，记一次击毁。 */
    static void gaussKillsRaider(GameTestHelper helper) {
        power(helper);
        TurretBlockEntity turret = turret(helper, UniverseBlocks.GAUSS_TURRET.get());
        turret.setItem(TurretBlockEntity.AMMO_SLOT, new ItemStack(UniverseItems.MAGNUM_AMMO_BOX.get(), 4));
        DarkFogUnit raider = spawn(helper, UnitKind.RAIDER, new BlockPos(8, 1, 14));
        helper.succeedWhen(() -> {
            helper.assertTrue(!raider.isAlive(), "突袭者还活着：" + raider.getHealth());
            helper.assertTrue(turret.kills() >= 1, "没记击毁");
            helper.assertTrue(turret.getItem(TurretBlockEntity.AMMO_SLOT).getCount() < 4 || turret.rounds() < 40, "没用掉弹药");
        });
    }

    /** 激光炮塔不要弹药：通电就打。 */
    static void laserNeedsNoAmmo(GameTestHelper helper) {
        power(helper);
        TurretBlockEntity turret = turret(helper, UniverseBlocks.LASER_TURRET.get());
        helper.assertTrue(turret.getContainerSize() == 0, "激光炮塔有弹药格");
        DarkFogUnit raider = spawn(helper, UnitKind.RAIDER, new BlockPos(8, 1, 13));
        float full = raider.getMaxHealth();
        helper.succeedWhen(() -> helper.assertTrue(!raider.isAlive() || raider.getHealth() < full, "激光炮塔没打"));
    }

    /** 没电的炮塔不打，弹药不动。 */
    static void unpoweredHoldsFire(GameTestHelper helper) {
        TurretBlockEntity turret = turret(helper, UniverseBlocks.GAUSS_TURRET.get());
        turret.setItem(TurretBlockEntity.AMMO_SLOT, new ItemStack(UniverseItems.MAGNUM_AMMO_BOX.get(), 2));
        DarkFogUnit raider = spawn(helper, UnitKind.RAIDER, new BlockPos(8, 1, 13));
        float full = raider.getMaxHealth();
        helper.runAfterDelay(60, () -> {
            helper.assertTrue(raider.getHealth() >= full, "没电还在打：" + raider.getHealth());
            helper.assertTrue(turret.rounds() == 0 || turret.rounds() == 40, "没电还在开火：剩 " + turret.rounds() + " 发");
            helper.succeed();
        });
    }

    /** 聚爆炮只打地面：飞着的游骑兵不打，炮弹组不动。 */
    static void cannonIgnoresFlyers(GameTestHelper helper) {
        power(helper);
        TurretBlockEntity turret = turret(helper, UniverseBlocks.IMPLOSION_CANNON.get());
        turret.setItem(TurretBlockEntity.AMMO_SLOT, new ItemStack(UniverseItems.SHELL_SET.get(), 3));
        DarkFogUnit ranger = spawn(helper, UnitKind.RANGER, new BlockPos(8, 4, 13));
        float full = ranger.getMaxHealth();
        helper.runAfterDelay(60, () -> {
            helper.assertTrue(ranger.getHealth() >= full, "聚爆炮打了飞着的：" + ranger.getHealth());
            helper.assertTrue(turret.getItem(TurretBlockEntity.AMMO_SLOT).getCount() == 3 && turret.rounds() == 0, "聚爆炮开火了");
            helper.succeed();
        });
    }

    /** 炮塔西边一座电力感应塔，再往西一座烧反物质燃料棒的人造恒星。 */
    private static void power(GameTestHelper helper) {
        tall(helper, new BlockPos(5, 1, 8), UniverseBlocks.TESLA_TOWER.get().defaultBlockState());
        BlockPos star = new BlockPos(2, 1, 8);
        tall(helper, star, UniverseBlocks.ARTIFICIAL_STAR.get().defaultBlockState());
        helper.getBlockEntity(star, FuelGeneratorBlockEntity.class)
                .setItem(FuelGeneratorBlockEntity.FUEL_SLOT, new ItemStack(UniverseItems.ANTIMATTER_FUEL_ROD.get(), 4));
    }

    private static TurretBlockEntity turret(GameTestHelper helper, Block block) {
        helper.setBlock(TURRET, block);
        return helper.getBlockEntity(TURRET, TurretBlockEntity.class);
    }

    private static DarkFogUnit spawn(GameTestHelper helper, UnitKind kind, BlockPos at) {
        BlockPos pos = helper.absolutePos(at);
        return DarkFogUnit.spawn(helper.getLevel(), kind, -1, 0, Vec3.atBottomCenterOf(pos), pos, DarkFogUnit.Mode.DEFEND, null);
    }

    private static void tall(GameTestHelper helper, BlockPos pos, BlockState state) {
        helper.setBlock(pos, state.setValue(TallMachineBlock.HALF, DoubleBlockHalf.LOWER));
        helper.setBlock(pos.above(), state.setValue(TallMachineBlock.HALF, DoubleBlockHalf.UPPER));
    }
}
