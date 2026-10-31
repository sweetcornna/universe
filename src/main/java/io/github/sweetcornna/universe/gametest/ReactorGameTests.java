package io.github.sweetcornna.universe.gametest;

import io.github.sweetcornna.universe.chem.reactor.ChemicalReactorBlockEntity;
import io.github.sweetcornna.universe.chem.reactor.ReactionBooks;
import io.github.sweetcornna.universe.chem.reactor.ReactorKnowledge;
import io.github.sweetcornna.universe.chem.reactor.ReactorRecipes;
import io.github.sweetcornna.universe.core.chem.reaction.Catalyst;
import io.github.sweetcornna.universe.core.chem.reaction.ItemCount;
import io.github.sweetcornna.universe.core.chem.reaction.Reaction;
import io.github.sweetcornna.universe.core.chem.reaction.ReactorResult;
import io.github.sweetcornna.universe.core.chem.reaction.YieldGrid;
import io.github.sweetcornna.universe.factory.TallMachineBlock;
import io.github.sweetcornna.universe.factory.power.FuelGeneratorBlockEntity;
import io.github.sweetcornna.universe.registry.UniverseBlocks;
import io.github.sweetcornna.universe.registry.UniverseItems;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/**
 * 化学反应釜（ADR-0036 §四、ADR-0037）的游戏内测试：教科书工况下放了铁催化剂的合成氨在限时内出氨；不放催化剂慢得多；
 * 甲烷蒸汽重整（吸热、800 多度）比萨巴蒂尔反应费电；催化剂做满批数用掉一个。做成一批的工况记进全服的产量图，
 * 界面数据里也有。自由投料认出反应、做成一批就发现了；一氧化碳加氢要靠催化剂挑；没发现的反应选上了也不做。
 * 发现记录是全服的，各个测试用不同的反应，互不影响。由 {@link UniverseGameTests} 登记。
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

    /** 氢燃烧一批约 40 刻；给到五倍。 */
    static final int FREE_TICKS = 200;

    /** 自由投料：氢和氧放进原料格，调到教科书工况，釜认出氢燃烧，做成一批就记成发现了，聊天栏不报错。 */
    static void freeModeDiscoversReaction(GameTestHelper helper) {
        powerPlant(helper);
        Reaction reaction = Reaction.HYDROGEN_COMBUSTION;
        BlockPos pos = new BlockPos(3, 1, 3);
        helper.setBlock(pos, UniverseBlocks.CHEMICAL_REACTOR.get());
        ChemicalReactorBlockEntity reactor = helper.getBlockEntity(pos, ChemicalReactorBlockEntity.class);
        helper.assertTrue(reactor.freeMode(), "新放的反应釜不是自由投料");
        // 自由投料什么反应物都收，同一种可以分两格放
        helper.assertTrue(reactor.canPlaceItem(0, new ItemStack(UniverseItems.RESOURCES.get("oxygen").get())), "原料格不收氧");
        helper.assertFalse(reactor.canPlaceItem(0, new ItemStack(net.minecraft.world.item.Items.DIRT)), "原料格收了泥土");
        reactor.setItem(0, new ItemStack(UniverseItems.RESOURCES.get("oxygen").get(), 16));
        reactor.setItem(1, new ItemStack(UniverseItems.HYDROGEN.get(), 1));
        reactor.setItem(2, new ItemStack(UniverseItems.HYDROGEN.get(), 31));
        reactor.setSettings(reaction.defaults());
        helper.succeedWhen(() -> {
            ChemicalReactorBlockEntity current = helper.getBlockEntity(pos, ChemicalReactorBlockEntity.class);
            helper.assertValueEqual(current.active(), reaction, "认出的反应");
            helper.assertTrue(current.countItem(UniverseItems.WATER.get()) >= 2, "还没做出水");
            helper.assertTrue(ReactionBooks.book(helper.getLevel().getServer()).discovered(reaction), "做成一批没记成发现了");
            helper.assertTrue(current.reaction() == null, "自由投料做成以后换成了选定配方");
        });
    }

    /** 一氧化碳加氢对得上三条反应：没放催化剂不反应，界面上写对得上几条；放镍就是甲烷化。 */
    static void catalystPicksReaction(GameTestHelper helper) {
        powerPlant(helper);
        BlockPos bare = new BlockPos(3, 1, 3);
        BlockPos nickel = new BlockPos(3, 1, 5);
        for (BlockPos pos : new BlockPos[] {bare, nickel}) {
            helper.setBlock(pos, UniverseBlocks.CHEMICAL_REACTOR.get());
            ChemicalReactorBlockEntity reactor = helper.getBlockEntity(pos, ChemicalReactorBlockEntity.class);
            reactor.setItem(0, new ItemStack(UniverseItems.CARBON_MONOXIDE.get(), 16));
            reactor.setItem(1, new ItemStack(UniverseItems.HYDROGEN.get(), 48));
            reactor.setSettings(Reaction.CO_METHANATION.defaults());
        }
        helper.getBlockEntity(nickel, ChemicalReactorBlockEntity.class)
                .setItem(ChemicalReactorBlockEntity.CATALYST_SLOT, new ItemStack(UniverseItems.NICKEL_CATALYST.get()));
        helper.runAfterDelay(20, () -> {
            ChemicalReactorBlockEntity none = helper.getBlockEntity(bare, ChemicalReactorBlockEntity.class);
            ChemicalReactorBlockEntity ni = helper.getBlockEntity(nickel, ChemicalReactorBlockEntity.class);
            helper.assertValueEqual(none.data().get(ChemicalReactorBlockEntity.STATUS), ChemicalReactorBlockEntity.AMBIGUOUS, "没放催化剂的状态");
            helper.assertValueEqual(none.data().get(ChemicalReactorBlockEntity.CANDIDATES), 3, "对得上几条");
            helper.assertTrue(none.active() == null, "没放催化剂认出了反应");
            helper.assertValueEqual(ni.active(), Reaction.CO_METHANATION, "放了镍认出的反应");
            helper.assertValueEqual(ni.data().get(ChemicalReactorBlockEntity.ACTIVE), Reaction.CO_METHANATION.ordinal() + 1, "界面数据里的反应");
            helper.assertValueEqual(ni.data().get(ChemicalReactorBlockEntity.STATUS), ChemicalReactorBlockEntity.RUNNING, "放了镍的状态");
            helper.succeed();
        });
    }

    /** 蓝图带过来的反应在这个世界没发现：照样选上，界面上写未发现，不做，自动化也不往里送别的。 */
    static void undiscoveredReactionIsLocked(GameTestHelper helper) {
        powerPlant(helper);
        Reaction reaction = Reaction.DEACON;
        BlockPos pos = new BlockPos(3, 1, 3);
        helper.setBlock(pos, UniverseBlocks.CHEMICAL_REACTOR.get());
        ChemicalReactorBlockEntity reactor = helper.getBlockEntity(pos, ChemicalReactorBlockEntity.class);
        net.minecraft.nbt.CompoundTag settings = new net.minecraft.nbt.CompoundTag();
        settings.putString("Reaction", reaction.name());
        reactor.applyBlueprintSettings(settings);
        reactor.setItem(0, new ItemStack(UniverseItems.HYDROGEN_CHLORIDE.get(), 16));
        reactor.setItem(1, new ItemStack(UniverseItems.RESOURCES.get("oxygen").get(), 16));
        reactor.setItem(ChemicalReactorBlockEntity.CATALYST_SLOT, new ItemStack(UniverseItems.COPPER_CHLORIDE_CATALYST.get()));
        helper.runAfterDelay(20, () -> {
            ChemicalReactorBlockEntity current = helper.getBlockEntity(pos, ChemicalReactorBlockEntity.class);
            helper.assertValueEqual(current.reaction(), reaction, "蓝图选的反应");
            helper.assertValueEqual(current.data().get(ChemicalReactorBlockEntity.STATUS), ChemicalReactorBlockEntity.LOCKED, "状态");
            helper.assertValueEqual(current.countItem(UniverseItems.CHLORINE.get()), 0, "没发现的反应做出了氯");
            helper.succeed();
        });
    }

    /** 放一台反应釜：这条反应记成发现了，选好（教科书工况），每格原料放满，按需放催化剂。 */
    private static BlockPos reactor(GameTestHelper helper, BlockPos pos, Reaction reaction, boolean catalyst, int catalysts) {
        ReactionBooks.discover(helper.getLevel().getServer(), reaction, false);
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
