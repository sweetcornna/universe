package io.github.sweetcornna.universe.gametest;

import io.github.sweetcornna.universe.core.darkfog.FacilityHealth;
import io.github.sweetcornna.universe.core.darkfog.PickupFilter;
import io.github.sweetcornna.universe.darkfog.Facilities;
import io.github.sweetcornna.universe.darkfog.FacilityData;
import io.github.sweetcornna.universe.darkfog.facility.BattlefieldBaseBlockEntity;
import io.github.sweetcornna.universe.darkfog.facility.FacilityGhostBlockEntity;
import io.github.sweetcornna.universe.factory.MachineBlock;
import io.github.sweetcornna.universe.factory.RecipeMachineBlockEntity;
import io.github.sweetcornna.universe.factory.TallMachineBlock;
import io.github.sweetcornna.universe.factory.power.FuelGeneratorBlockEntity;
import io.github.sweetcornna.universe.registry.UniverseBlocks;
import io.github.sweetcornna.universe.registry.UniverseItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** 设施的耐久、残影和战场分析基地（ADR-0028 §四、§五）的游戏内测试，由 {@link UniverseGameTests} 登记，场地是 7×4×7 的平台。 */
final class DarkFogFacilityGameTests {
    private DarkFogFacilityGameTests() {
    }

    /**
     * 打掉血的设施记着耐久；打光了换成残影，里面的东西掉出来，设施本身不掉；
     * 拿别的方块右键残影不放，拿同一种方块右键照原来的朝向放回去，用掉一个，耐久是满的。
     */
    static void ghostKeepsAndRestores(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = new BlockPos(3, 1, 3);
        helper.setBlock(pos, UniverseBlocks.ASSEMBLER.get().defaultBlockState().setValue(MachineBlock.FACING, Direction.EAST));
        helper.getBlockEntity(pos, RecipeMachineBlockEntity.class).setItem(0, new ItemStack(Items.IRON_INGOT, 16));
        BlockPos abs = helper.absolutePos(pos);
        BlockState original = level.getBlockState(abs);
        float max = Facilities.maxHealth(original);
        helper.assertTrue(max == FacilityHealth.NORMAL, "一型制造台的耐久不是 " + FacilityHealth.NORMAL + "：" + max);

        helper.assertFalse(Facilities.damage(level, abs, 100, null), "打 100 就坏了");
        helper.assertTrue(Facilities.health(level, abs) == max - 100, "耐久没记下：" + Facilities.health(level, abs));
        helper.assertTrue(Facilities.damage(level, abs, 1000, null), "打光了没坏");
        helper.assertBlockPresent(UniverseBlocks.FACILITY_GHOST.get(), pos);
        FacilityGhostBlockEntity ghost = helper.getBlockEntity(pos, FacilityGhostBlockEntity.class);
        helper.assertTrue(ghost.original().equals(original), "残影记的方块不对：" + ghost.original());
        helper.assertTrue(FacilityData.get(level).ghosts().contains(abs.asLong()), "残影没进索引");
        // 掉落物有拾取延迟，这一刻战场分析基地捡不走
        helper.assertItemEntityCountIs(Items.IRON_INGOT, pos, 2, 16);
        helper.assertItemEntityNotPresent(UniverseItems.ASSEMBLER.get(), pos, 2);

        ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false);
        BlockState ghostState = level.getBlockState(abs);
        InteractionResult wrong = ghostState.useItemOn(new ItemStack(UniverseItems.ARC_SMELTER.get()), level, player, InteractionHand.MAIN_HAND, hit);
        helper.assertTrue(wrong instanceof InteractionResult.TryEmptyHandInteraction, "拿别的方块也接了：" + wrong);
        helper.assertBlockPresent(UniverseBlocks.FACILITY_GHOST.get(), pos);

        ItemStack hand = new ItemStack(UniverseItems.ASSEMBLER.get(), 2);
        player.setItemInHand(InteractionHand.MAIN_HAND, hand);
        ghostState.useItemOn(hand, level, player, InteractionHand.MAIN_HAND, hit);
        helper.assertBlockState(pos, state -> state.is(UniverseBlocks.ASSEMBLER.get()) && state.getValue(MachineBlock.FACING) == Direction.EAST,
                state -> Component.literal("没照原样放回去：" + state));
        helper.assertTrue(hand.getCount() == 1, "放回去没用掉方块：" + hand.getCount());
        helper.assertTrue(Facilities.health(level, abs) == max, "放回去耐久不满：" + Facilities.health(level, abs));
        helper.assertFalse(FacilityData.get(level).ghosts().contains(abs.asLong()), "放回去残影还在索引里");
        helper.succeed();
    }

    /**
     * 通电的战场分析基地修好旁边掉血的物流仓储；储物格里有电弧熔炉，就按残影原样重建，用掉那一个；
     * 设成只捡已存的东西，捡回地上的回响碎片，不捡泥土。
     */
    static void baseRepairsAndRebuilds(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        power(helper);
        BlockPos basePos = new BlockPos(3, 1, 3);
        helper.setBlock(basePos, UniverseBlocks.BATTLEFIELD_ANALYSIS_BASE.get());
        BattlefieldBaseBlockEntity base = helper.getBlockEntity(basePos, BattlefieldBaseBlockEntity.class);
        base.setItem(0, new ItemStack(UniverseItems.ARC_SMELTER.get()));
        base.setItem(1, new ItemStack(Items.ECHO_SHARD));
        // 只捡已存的：测试是并排跑的，别把旁边测试掉在地上的东西捡走
        ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
        while (base.filter() != PickupFilter.STORED) {
            base.clickButton(player, BattlefieldBaseBlockEntity.BUTTON_FILTER);
        }

        BlockPos depot = new BlockPos(1, 1, 5);
        helper.setBlock(depot, UniverseBlocks.STORAGE_DEPOT.get());
        BlockPos depotAbs = helper.absolutePos(depot);
        Facilities.damage(level, depotAbs, 200, null);
        float depotMax = Facilities.maxHealth(level.getBlockState(depotAbs));
        helper.assertTrue(Facilities.health(level, depotAbs) == depotMax - 200, "物流仓储没掉血");

        BlockPos smelter = new BlockPos(5, 1, 5);
        helper.setBlock(smelter, UniverseBlocks.ARC_SMELTER.get().defaultBlockState().setValue(MachineBlock.FACING, Direction.WEST));
        Facilities.damage(level, helper.absolutePos(smelter), 100_000, null);
        helper.assertBlockPresent(UniverseBlocks.FACILITY_GHOST.get(), smelter);

        ItemEntity shard = helper.spawnItem(Items.ECHO_SHARD, new Vec3(3.5, 1.2, 5.5));
        ItemEntity dirt = helper.spawnItem(Items.DIRT, new Vec3(4.5, 1.2, 5.5));
        helper.succeedWhen(() -> {
            helper.assertTrue(Facilities.health(level, depotAbs) == depotMax, "物流仓储没修好：" + Facilities.health(level, depotAbs));
            helper.assertBlockState(smelter, state -> state.is(UniverseBlocks.ARC_SMELTER.get())
                    && state.getValue(MachineBlock.FACING) == Direction.WEST, state -> Component.literal("电弧熔炉没按原样重建：" + state));
            helper.assertTrue(count(base, UniverseItems.ARC_SMELTER.get()) == 0, "重建没用掉储物格里的电弧熔炉");
            helper.assertTrue(!shard.isAlive() && count(base, Items.ECHO_SHARD) == 2, "回响碎片没捡回来");
            helper.assertTrue(dirt.isAlive() && count(base, Items.DIRT) == 0, "只捡已存的却捡了泥土");
            // 测完拆掉，免得去修旁边测试的设施
            dirt.discard();
            helper.setBlock(basePos, Blocks.AIR);
        });
    }

    private static int count(BattlefieldBaseBlockEntity base, Item item) {
        int count = 0;
        for (int i = 0; i < BattlefieldBaseBlockEntity.SLOTS; i++) {
            if (base.getItem(i).is(item)) {
                count += base.getItem(i).getCount();
            }
        }
        return count;
    }

    /** 西北角一座电力感应塔，东北角一座烧反物质燃料棒的人造恒星，和 {@link UniverseGameTests} 的电站一样。 */
    private static void power(GameTestHelper helper) {
        tall(helper, new BlockPos(1, 1, 1), UniverseBlocks.TESLA_TOWER.get().defaultBlockState());
        BlockPos star = new BlockPos(5, 1, 1);
        tall(helper, star, UniverseBlocks.ARTIFICIAL_STAR.get().defaultBlockState());
        helper.getBlockEntity(star, FuelGeneratorBlockEntity.class)
                .setItem(FuelGeneratorBlockEntity.FUEL_SLOT, new ItemStack(UniverseItems.ANTIMATTER_FUEL_ROD.get(), 4));
    }

    private static void tall(GameTestHelper helper, BlockPos pos, BlockState state) {
        helper.setBlock(pos, state.setValue(TallMachineBlock.HALF, DoubleBlockHalf.LOWER));
        helper.setBlock(pos.above(), state.setValue(TallMachineBlock.HALF, DoubleBlockHalf.UPPER));
    }
}
