package io.github.sweetcornna.universe.gametest;

import io.github.sweetcornna.universe.block.ship.FuelTankBlockEntity;
import io.github.sweetcornna.universe.block.ship.ShipFuel;
import io.github.sweetcornna.universe.chem.AtmosphericExtractorBlockEntity;
import io.github.sweetcornna.universe.core.chem.Gas;
import io.github.sweetcornna.universe.factory.RecipeMachineBlockEntity;
import io.github.sweetcornna.universe.factory.TallMachineBlock;
import io.github.sweetcornna.universe.factory.power.FuelGeneratorBlockEntity;
import io.github.sweetcornna.universe.factory.research.ResearchData;
import io.github.sweetcornna.universe.registry.UniverseBlocks;
import io.github.sweetcornna.universe.registry.UniverseItems;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/**
 * M20 化学（ADR-0036 §三、§四）的游戏内测试：电解槽把水分成氢和氧，主世界的大气提取器取到氮气、头顶挡住就停，
 * 甲烷液氧给燃料舱加注。由 {@link UniverseGameTests} 登记。
 */
final class ChemistryGameTests {
    private ChemistryGameTests() {
    }

    /** 研究完大气化学，电解槽接上电，两个水电解出两个氢、一个氧。 */
    static void electrolyzerSplitsWater(GameTestHelper helper) {
        ResearchData.get(helper.getLevel().getServer()).state().complete("atmospheric_chemistry");
        power(helper);
        BlockPos pos = new BlockPos(3, 1, 3);
        helper.setBlock(pos, UniverseBlocks.ELECTROLYZER.get());
        RecipeMachineBlockEntity cell = helper.getBlockEntity(pos, RecipeMachineBlockEntity.class);
        CompoundTag settings = new CompoundTag();
        settings.putString("Recipe", "universe:electrolysis/water");
        cell.applyBlueprintSettings(settings);
        cell.setItem(0, new ItemStack(UniverseItems.WATER.get(), 2));
        helper.succeedWhen(() -> {
            helper.assertValueEqual(cell.countItem(UniverseItems.HYDROGEN.get()), 2, "氢的数量");
            helper.assertValueEqual(cell.countItem(UniverseItems.RESOURCES.get("oxygen").get()), 1, "氧的数量");
            helper.assertValueEqual(cell.countItem(UniverseItems.WATER.get()), 0, "剩下的水");
        });
    }

    /** 主世界的大气照地球：提取器默认取最多的氮气。旁边一台头顶压着石头的停在"头顶被挡住"，什么也不出。 */
    static void extractorCollectsNitrogen(GameTestHelper helper) {
        power(helper);
        BlockPos open = new BlockPos(3, 1, 3);
        BlockPos covered = new BlockPos(5, 1, 5);
        helper.setBlock(open, UniverseBlocks.ATMOSPHERIC_EXTRACTOR.get());
        helper.setBlock(covered, UniverseBlocks.ATMOSPHERIC_EXTRACTOR.get());
        helper.setBlock(covered.above(), Blocks.STONE);
        AtmosphericExtractorBlockEntity extractor = helper.getBlockEntity(open, AtmosphericExtractorBlockEntity.class);
        AtmosphericExtractorBlockEntity blocked = helper.getBlockEntity(covered, AtmosphericExtractorBlockEntity.class);
        helper.succeedWhen(() -> {
            helper.assertTrue(extractor.gas().filter(gas -> gas == Gas.NITROGEN).isPresent(), "默认取的气体不是氮气：" + extractor.gas());
            helper.assertTrue(extractor.countItem(UniverseItems.NITROGEN.get()) > 0, "没取到氮气");
            helper.assertValueEqual(blocked.data().get(AtmosphericExtractorBlockEntity.STATUS), AtmosphericExtractorBlockEntity.NO_SKY,
                    "头顶挡住的提取器的状态");
            helper.assertValueEqual(blocked.countItem(UniverseItems.NITROGEN.get()), 0, "头顶挡住还出了氮气");
        });
    }

    /** 一罐甲烷液氧给燃料舱加 2500，是发射燃料的 2.5 倍。 */
    static void methaloxFuelsTank(GameTestHelper helper) {
        BlockPos pos = new BlockPos(3, 1, 3);
        helper.setBlock(pos, UniverseBlocks.FUEL_TANK_MK2.get());
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(UniverseItems.METHALOX_FUEL.get(), 2));
        helper.useBlock(pos, player);
        FuelTankBlockEntity tank = helper.getBlockEntity(pos, FuelTankBlockEntity.class);
        helper.assertValueEqual(tank.fuel(), 2500.0, "加注一罐以后的燃料");
        helper.assertValueEqual(player.getMainHandItem().getCount(), 1, "手里剩的罐数");
        helper.assertValueEqual(ShipFuel.value(new ItemStack(UniverseItems.METHALOX_FUEL.get())),
                2.5 * ShipFuel.value(new ItemStack(UniverseItems.LAUNCH_FUEL.get())), "甲烷液氧和发射燃料的比");
        helper.succeed();
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
