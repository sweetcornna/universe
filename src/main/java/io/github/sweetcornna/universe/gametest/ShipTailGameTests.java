package io.github.sweetcornna.universe.gametest;

import io.github.sweetcornna.universe.block.FloraBlocks;
import io.github.sweetcornna.universe.block.PlanetPlantBlock;
import io.github.sweetcornna.universe.core.ecology.PlantForm;
import io.github.sweetcornna.universe.entity.ship.ShipAssembly;
import io.github.sweetcornna.universe.entity.ship.ShipCargoContainer;
import io.github.sweetcornna.universe.entity.ship.ShipEntity;
import io.github.sweetcornna.universe.registry.UniverseBlocks;
import io.github.sweetcornna.universe.registry.UniverseItems;
import io.github.sweetcornna.universe.survival.EntityGravity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * 飞船、外骨骼和星球的尾巴（ADR-0030 §六）的游戏内测试：飞着的船上的货舱、不是生物的实体按星球重力下落、
 * 放下的星球植物按所在星球的物种显示。由 {@link UniverseGameTests} 登记。
 */
final class ShipTailGameTests {
    private ShipTailGameTests() {
    }

    /**
     * 组装起来的船（箱子里放了东西）飞着的时候，货舱把船上所有箱子的格子拼成一片：读得到箱子里的东西，
     * 往里放再读回来也还在（写回了船的结构，不是只改了读出来的副本）。
     */
    static void flyingShipCargo(GameTestHelper helper) {
        BlockPos helm = new BlockPos(3, 2, 3);
        helper.setBlock(helm, UniverseBlocks.SHIP_HELM.get());
        helper.setBlock(new BlockPos(2, 2, 3), UniverseBlocks.SHIP_THRUSTER.get());
        helper.setBlock(new BlockPos(4, 2, 3), UniverseBlocks.FUEL_TANK.get());
        BlockPos chest = new BlockPos(3, 2, 4);
        helper.setBlock(chest, Blocks.CHEST);
        helper.getBlockEntity(chest, ChestBlockEntity.class).setItem(0, new ItemStack(UniverseItems.TITANIUM_ALLOY.get(), 5));
        BlockPos barrel = new BlockPos(3, 2, 2);
        helper.setBlock(barrel, Blocks.BARREL);

        ShipEntity ship = ShipAssembly.assemble(helper.getLevel(), helper.absolutePos(helm), null, message -> {
        });
        helper.assertTrue(ship != null && ship.structure() != null, "没有组装出飞船");
        Container cargo = ShipCargoContainer.of(ship, helper.getLevel().registryAccess());
        // 箱子 27 格 + 木桶 27 格
        helper.assertValueEqual(cargo.getContainerSize(), 54, "货舱格数");
        helper.assertTrue(cargo.countItem(UniverseItems.TITANIUM_ALLOY.get()) == 5, "货舱里读不到箱子里的钛合金");

        cargo.setItem(30, new ItemStack(Items.IRON_INGOT, 3));
        cargo.setChanged();
        Container reopened = ShipCargoContainer.of(ship, helper.getLevel().registryAccess());
        helper.assertTrue(reopened.countItem(Items.IRON_INGOT) == 3, "放进货舱的铁锭没写回船上");
        helper.assertTrue(reopened.countItem(UniverseItems.TITANIUM_ALLOY.get()) == 5, "原来的钛合金丢了");
        helper.succeed();
    }

    /** 船上一个货柜都没有：货舱开得起来，格数是 0（界面里写"船上没有货柜"）。 */
    static void shipWithoutHolds(GameTestHelper helper) {
        BlockPos helm = new BlockPos(3, 2, 3);
        helper.setBlock(helm, UniverseBlocks.SHIP_HELM.get());
        helper.setBlock(new BlockPos(2, 2, 3), UniverseBlocks.SHIP_THRUSTER.get());
        helper.setBlock(new BlockPos(3, 2, 4), UniverseBlocks.FUEL_TANK.get());
        ShipEntity ship = ShipAssembly.assemble(helper.getLevel(), helper.absolutePos(helm), null, message -> {
        });
        helper.assertTrue(ship != null, "没有组装出飞船");
        helper.assertValueEqual(ShipCargoContainer.of(ship, helper.getLevel().registryAccess()).getContainerSize(), 0, "货舱格数");
        helper.succeed();
    }

    /**
     * 不是生物的实体按星球重力下落（ADR-0030 §六）：测试场地在主世界（母星，重力 1），掉落物的重力不变；
     * 换算本身在 {@code ProjectileGravity}（单元测试盯着），这里盯住 mixin 真的走到了、而且生物不受影响。
     */
    static void entityGravityFollowsPlanet(GameTestHelper helper) {
        helper.assertTrue(EntityGravity.factor(helper.getLevel()) > 0, "查不到这个世界的重力");
        var item = helper.spawnItem(Items.IRON_INGOT, 3, 3, 3);
        double factor = EntityGravity.factor(helper.getLevel());
        helper.assertTrue(Math.abs(item.getGravity() - 0.04 * factor) < 1e-9,
                "掉落物的重力是 " + item.getGravity() + "，该是 " + 0.04 * factor);
        // 生物的重力走属性，不该被这段 mixin 再乘一遍
        var cow = helper.spawn(net.minecraft.world.entity.EntityTypes.COW, 3, 3, 4);
        helper.assertTrue(Math.abs(cow.getGravity() - cow.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.GRAVITY)) < 1e-9,
                "生物的重力被改了：" + cow.getGravity());
        helper.succeed();
    }

    /**
     * 放下的星球植物按所在星球挑物种（ADR-0030 §六）：放一株星球植物下去，方块状态里的变体号指到这颗星球上
     * 这个形态的一个物种，同一格放两次挑出来的是同一种。这颗星球上没有这个形态的物种时跳过。
     */
    static void placedPlantPicksLocalSpecies(GameTestHelper helper) {
        BlockPos pos = new BlockPos(3, 2, 3);
        helper.setBlock(pos.below(), Blocks.DIRT);
        PlanetPlantBlock plant = UniverseBlocks.PLANTS.get(PlantForm.FERN).get();
        ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(plant));
        BlockPlaceContext context = new BlockPlaceContext(new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(helper.absolutePos(pos)), Direction.UP, helper.absolutePos(pos.below()), false)));
        BlockState placed = plant.getStateForPlacement(context);
        if (placed == null) {
            // 这颗星球上没有这个形态的物种：这条就不测了
            helper.succeed();
            return;
        }
        BlockState again = plant.getStateForPlacement(context);
        helper.assertValueEqual(again.getValue(FloraBlocks.VARIANT), placed.getValue(FloraBlocks.VARIANT), "同一格两次挑的物种");
        helper.succeed();
    }
}
