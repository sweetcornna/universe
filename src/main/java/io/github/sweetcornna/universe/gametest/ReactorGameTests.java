package io.github.sweetcornna.universe.gametest;

import io.github.sweetcornna.universe.chem.reactor.ChemicalReactorBlockEntity;
import io.github.sweetcornna.universe.chem.reactor.ReactorKnowledge;
import io.github.sweetcornna.universe.chem.reactor.ReactorRecipes;
import io.github.sweetcornna.universe.core.chem.reaction.Catalyst;
import io.github.sweetcornna.universe.core.chem.reaction.ItemCount;
import io.github.sweetcornna.universe.core.chem.reaction.Reaction;
import io.github.sweetcornna.universe.core.chem.reaction.ReactorResult;
import io.github.sweetcornna.universe.core.chem.reaction.YieldGrid;
import io.github.sweetcornna.universe.factory.TallMachineBlock;
import io.github.sweetcornna.universe.factory.power.FuelGeneratorBlockEntity;
import io.github.sweetcornna.universe.factory.research.ResearchData;
import io.github.sweetcornna.universe.registry.UniverseBlocks;
import io.github.sweetcornna.universe.registry.UniverseItems;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/**
 * 化学反应釜（ADR-0036 §四）的游戏内测试：教科书工况下放了铁催化剂的合成氨在限时内出氨；不放催化剂慢得多；
 * 甲烷蒸汽重整（吸热、800 多度）比萨巴蒂尔反应费电；催化剂做满批数用掉一个。做成一批的工况记进全服的产量图，
 * 界面数据里也有。由 {@link UniverseGameTests} 登记。
 */
final class ReactorGameTests {
    /** 合成氨在教科书工况下约 11.5 批/分钟，一批约 105 刻；给到四倍。 */
    static final int HABER_TICKS = 420;
    /** 比较有无催化剂时等多久：放了的能做两三批，没放的一批也做不完（约 1700 刻一批）。 */
    static final int COMPARE_TICKS = 300;

    private ReactorGameTests() {
    }

    /** 教科书工况、放了铁催化剂的合成氨在限时内做出氨，做过的那一格记进全服的产量图。 */
    static void haberMakesAmmonia(GameTestHelper helper) {
        powerPlant(helper);
        BlockPos pos = reactor(helper, new BlockPos(3, 1, 3), Reaction.HABER, true, 1);
        helper.succeedWhen(() -> {
            ChemicalReactorBlockEntity reactor = helper.getBlockEntity(pos, ChemicalReactorBlockEntity.class);
            helper.assertTrue(reactor.countItem(UniverseItems.AMMONIA.get()) >= 2, "反应釜没有做出氨");
            helper.assertTrue(reactor.settings().equals(Reaction.HABER.defaults()), "新选的反应不在教科书工况上：" + reactor.settings());
            int cell = YieldGrid.cell(Reaction.HABER.defaults()).index();
            helper.assertTrue(ReactorKnowledge.get(helper.getLevel().getServer()).measured(Reaction.HABER, cell), "产量图上没记下这一格");
            helper.assertTrue((reactor.data().get(ChemicalReactorBlockEntity.MEASURED + cell / ReactorKnowledge.WORD_BITS)
                    >> cell % ReactorKnowledge.WORD_BITS & 1) != 0, "界面数据里没有这一格");
        });
    }

    /** 不放催化剂慢几十倍：同样的时间里放了的已经出氨，没放的一个也没有。 */
    static void uncatalyzedIsSlow(GameTestHelper helper) {
        powerPlant(helper);
        BlockPos fast = reactor(helper, new BlockPos(3, 1, 3), Reaction.HABER, true, 1);
        BlockPos slow = reactor(helper, new BlockPos(3, 1, 5), Reaction.HABER, false, 1);
        helper.runAfterDelay(COMPARE_TICKS, () -> {
            ChemicalReactorBlockEntity catalyzed = helper.getBlockEntity(fast, ChemicalReactorBlockEntity.class);
            ChemicalReactorBlockEntity bare = helper.getBlockEntity(slow, ChemicalReactorBlockEntity.class);
            ReactorResult with = catalyzed.result();
            ReactorResult without = bare.result();
            helper.assertTrue(with != null && without != null, "反应釜没有读数");
            helper.assertTrue(with.batchesPerMinute() > 10 * without.batchesPerMinute(),
                    "没放催化剂不够慢：" + with.batchesPerMinute() + " 对 " + without.batchesPerMinute());
            helper.assertTrue(catalyzed.countItem(UniverseItems.AMMONIA.get()) >= 2, "放了催化剂的没做出氨");
            helper.assertValueEqual(bare.countItem(UniverseItems.AMMONIA.get()), 0, "没放催化剂的氨");
            helper.assertValueEqual(bare.data().get(ChemicalReactorBlockEntity.STATUS), ChemicalReactorBlockEntity.RUNNING, "没放催化剂的状态");
            helper.succeed();
        });
    }

    /** 甲烷蒸汽重整要把釜烧到 850 °C 还要吸热，比 350 °C、放热的萨巴蒂尔反应费电得多。 */
    static void reformingDrawsMorePower(GameTestHelper helper) {
        powerPlant(helper);
        BlockPos reforming = reactor(helper, new BlockPos(3, 1, 3), Reaction.STEAM_REFORMING, true, 1);
        BlockPos sabatier = reactor(helper, new BlockPos(3, 1, 5), Reaction.SABATIER, true, 1);
        helper.runAfterDelay(20, () -> {
            ChemicalReactorBlockEntity hot = helper.getBlockEntity(reforming, ChemicalReactorBlockEntity.class);
            ChemicalReactorBlockEntity cool = helper.getBlockEntity(sabatier, ChemicalReactorBlockEntity.class);
            helper.assertValueEqual(hot.data().get(ChemicalReactorBlockEntity.STATUS), ChemicalReactorBlockEntity.RUNNING, "重整的状态");
            helper.assertValueEqual(cool.data().get(ChemicalReactorBlockEntity.STATUS), ChemicalReactorBlockEntity.RUNNING, "萨巴蒂尔的状态");
            helper.assertTrue(cool.demand() > 0 && hot.demand() > 3 * cool.demand(), "重整 " + hot.demand() + " J/刻，萨巴蒂尔 " + cool.demand() + " J/刻");
            helper.succeed();
        });
    }

    /** 催化剂做满 256 批用掉一个，磨损从头算。 */
    static void catalystWearsOut(GameTestHelper helper) {
        powerPlant(helper);
        BlockPos pos = reactor(helper, new BlockPos(3, 1, 3), Reaction.HABER, true, 2);
        ChemicalReactorBlockEntity reactor = helper.getBlockEntity(pos, ChemicalReactorBlockEntity.class);
        reactor.setWear(Catalyst.IRON.batches() - 1);
        helper.succeedWhen(() -> {
            ChemicalReactorBlockEntity current = helper.getBlockEntity(pos, ChemicalReactorBlockEntity.class);
            helper.assertTrue(current.countItem(UniverseItems.AMMONIA.get()) >= 2, "还没做完一批");
            helper.assertValueEqual(current.getItem(ChemicalReactorBlockEntity.CATALYST_SLOT).getCount(), 1, "催化剂个数");
            helper.assertValueEqual(current.wear(), 0, "换上的催化剂的磨损");
        });
    }

    /** 放一台反应釜：选好反应（教科书工况），每格原料放满，按需放催化剂。 */
    private static BlockPos reactor(GameTestHelper helper, BlockPos pos, Reaction reaction, boolean catalyst, int catalysts) {
        ResearchData research = ResearchData.get(helper.getLevel().getServer());
        research.state().complete(ReactorRecipes.research(reaction));
        research.setDirty();
        helper.setBlock(pos, UniverseBlocks.CHEMICAL_REACTOR.get());
        ChemicalReactorBlockEntity reactor = helper.getBlockEntity(pos, ChemicalReactorBlockEntity.class);
        reactor.setReaction(reaction);
        for (int i = 0; i < reaction.inputs().size(); i++) {
            ItemCount input = reaction.inputs().get(i);
            reactor.setItem(i, new ItemStack(ReactorRecipes.item(input), 64));
        }
        if (catalyst) {
            reactor.setItem(ChemicalReactorBlockEntity.CATALYST_SLOT, new ItemStack(ReactorRecipes.item(reaction.catalyst().item()), catalysts));
        }
        return pos;
    }

    /** 电力感应塔和一座烧反物质燃料棒的人造恒星，电管够。 */
    private static void powerPlant(GameTestHelper helper) {
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
