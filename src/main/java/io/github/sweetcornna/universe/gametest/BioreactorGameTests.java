package io.github.sweetcornna.universe.gametest;

import io.github.sweetcornna.universe.bio.BioRecipes;
import io.github.sweetcornna.universe.bio.BioreactorBlockEntity;
import io.github.sweetcornna.universe.core.microbe.BioProcess;
import io.github.sweetcornna.universe.core.microbe.Culture;
import io.github.sweetcornna.universe.core.microbe.EarthMicrobes;
import io.github.sweetcornna.universe.core.microbe.MicrobeStrain;
import io.github.sweetcornna.universe.core.microbe.Organism;
import io.github.sweetcornna.universe.factory.RecipeMachineBlockEntity;
import io.github.sweetcornna.universe.factory.TallMachineBlock;
import io.github.sweetcornna.universe.factory.power.FuelGeneratorBlockEntity;
import io.github.sweetcornna.universe.factory.power.ThermalPlantBlockEntity;
import io.github.sweetcornna.universe.factory.research.ResearchData;
import io.github.sweetcornna.universe.registry.UniverseBlocks;
import io.github.sweetcornna.universe.registry.UniverseDataComponents;
import io.github.sweetcornna.universe.registry.UniverseItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CookingFuel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.storage.loot.providers.number.ints.ResolvableInt;

/**
 * 生物反应器（ADR-0036 §五）的游戏内测试：满罐的产甲烷菌把 CO₂ 和 H₂ 做成甲烷和水，罐温控在菌的最适温度；
 * 菌没有工艺的基因时不出货、不算有料；断料的一罐死光以后从菌种格里重新接种；看不到天时藻类开补光灯，
 * 补光灯每批的电比做出来的藻类生物质烧掉换回的电多（不能拿电换电）。由 {@link UniverseGameTests} 登记。
 */
final class BioreactorGameTests {
    /** 满罐、产率 1、最适温度时生物甲烷化 120 刻一批；给到三批多。 */
    static final int METHANE_TICKS = 400;
    /** 藻类 200 刻一批。 */
    static final int ALGAE_TICKS = 400;
    /** 从 1.05% 断料死到 1% 以下约 85 刻，再接种一刻。 */
    static final int REINOCULATE_TICKS = 300;
    /** 补光灯的电至少是藻类生物质烧掉换回的电的这么多倍。 */
    private static final double LIGHT_MARGIN = 1.4;

    private BioreactorGameTests() {
    }

    /** 满罐的巴氏甲烷八叠球菌做出甲烷和水，罐温在菌的最适温度上。 */
    static void methanogenMakesMethane(GameTestHelper helper) {
        powerPlant(helper);
        BlockPos pos = bioreactor(helper, new BlockPos(3, 1, 3), BioProcess.BIOMETHANATION, EarthMicrobes.METHANOSARCINA);
        BioreactorBlockEntity reactor = helper.getBlockEntity(pos, BioreactorBlockEntity.class);
        reactor.setItem(0, new ItemStack(UniverseItems.CARBON_DIOXIDE.get(), 64));
        reactor.setItem(1, new ItemStack(UniverseItems.HYDROGEN.get(), 64));
        long opt = Math.round(EarthMicrobes.METHANOSARCINA.optK());
        helper.succeedWhen(() -> {
            BioreactorBlockEntity current = helper.getBlockEntity(pos, BioreactorBlockEntity.class);
            helper.assertTrue(current.countItem(UniverseItems.METHANE.get()) >= 2, "没有做出甲烷");
            helper.assertTrue(current.countItem(UniverseItems.WATER.get()) >= 4, "没有做出水");
            helper.assertValueEqual(current.culture().state(), Culture.State.GROWING, "菌的状态");
            helper.assertValueEqual((long) current.data().get(BioreactorBlockEntity.TANK), opt, "罐温");
        });
    }

    /** 产甲烷菌做不了藻类培养：不出货，状态是菌不对，也不算有料（断料计时）。 */
    static void wrongStrainStarves(GameTestHelper helper) {
        powerPlant(helper);
        BlockPos pos = bioreactor(helper, new BlockPos(3, 1, 3), BioProcess.ALGAE, EarthMicrobes.METHANOSARCINA);
        BioreactorBlockEntity reactor = helper.getBlockEntity(pos, BioreactorBlockEntity.class);
        reactor.setItem(0, new ItemStack(UniverseItems.CARBON_DIOXIDE.get(), 64));
        reactor.setItem(1, new ItemStack(UniverseItems.WATER.get(), 64));
        helper.runAfterDelay(40, () -> {
            BioreactorBlockEntity current = helper.getBlockEntity(pos, BioreactorBlockEntity.class);
            helper.assertValueEqual(current.data().get(RecipeMachineBlockEntity.STATUS), BioreactorBlockEntity.WRONG_STRAIN, "状态");
            helper.assertValueEqual(current.countItem(UniverseItems.ALGAL_BIOMASS.get()), 0, "藻类生物质");
            helper.assertValueEqual(current.culture().state(), Culture.State.STARVING, "菌的状态");
            helper.assertValueEqual(current.data().get(BioreactorBlockEntity.FLAGS) & 4, 0, "做得了的标记");
            helper.assertValueEqual(current.data().get(BioreactorBlockEntity.PROCESSES), 1 << BioProcess.BIOMETHANATION.ordinal(), "能做的工艺");
            helper.succeed();
        });
    }

    /** 断料过了宽限期的一罐死到 1% 以下就没了，菌种格里的一管整管倒进去重新接种。 */
    static void deadTankReinoculates(GameTestHelper helper) {
        powerPlant(helper);
        BlockPos pos = bioreactor(helper, new BlockPos(3, 1, 3), BioProcess.BIOLEACHING, EarthMicrobes.ACIDITHIOBACILLUS);
        BioreactorBlockEntity reactor = helper.getBlockEntity(pos, BioreactorBlockEntity.class);
        Organism dying = Organism.wild(EarthMicrobes.ACIDITHIOBACILLUS);
        reactor.setDensity(Culture.LOST_BELOW * 1.05);
        reactor.culture().step(dying, Culture.STARVE_GRACE_TICKS, false, EarthMicrobes.ACIDITHIOBACILLUS.optK());
        Organism next = Organism.wild(EarthMicrobes.IDEONELLA);
        ItemStack tube = new ItemStack(UniverseItems.STRAIN_TUBE.get());
        tube.set(UniverseDataComponents.ORGANISM.get(), next);
        reactor.setItem(BioreactorBlockEntity.STRAIN_SLOT, tube);
        helper.succeedWhen(() -> {
            BioreactorBlockEntity current = helper.getBlockEntity(pos, BioreactorBlockEntity.class);
            helper.assertTrue(current.getItem(BioreactorBlockEntity.STRAIN_SLOT).isEmpty(), "菌种管没有用掉");
            helper.assertValueEqual(current.organism(), next, "罐里的菌");
            helper.assertTrue(Math.abs(current.culture().density() - Culture.INOCULUM) < 0.005, "接种的密度 " + current.culture().density());
        });
    }

    /** 封了顶看不到天，藻类开补光灯：电比别的工艺多，每批的电又比藻类生物质烧掉换回的多，还是做得出藻类和氧。 */
    static void growLightsCostMoreThanBiomass(GameTestHelper helper) {
        CookingFuel fuel = new ItemStack(UniverseItems.ALGAL_BIOMASS.get()).get(DataComponents.COOKING_FUEL);
        helper.assertTrue(fuel != null && fuel.burnTime() instanceof ResolvableInt.Constant, "藻类生物质不是燃料");
        int burn = ((ResolvableInt.Constant) fuel.burnTime()).value();
        double back = burn * (double) ThermalPlantBlockEntity.JOULES_PER_BURN_TICK;
        helper.assertTrue(BioreactorBlockEntity.LIGHT_JOULES_PER_BATCH >= LIGHT_MARGIN * back,
                "补光灯一批 " + BioreactorBlockEntity.LIGHT_JOULES_PER_BATCH + " J，藻类烧掉换回 " + back + " J");
        powerPlant(helper);
        BlockPos pos = bioreactor(helper, new BlockPos(3, 1, 3), BioProcess.ALGAE, EarthMicrobes.SYNECHOCOCCUS);
        BioreactorBlockEntity reactor = helper.getBlockEntity(pos, BioreactorBlockEntity.class);
        reactor.setItem(0, new ItemStack(UniverseItems.CARBON_DIOXIDE.get(), 64));
        reactor.setItem(1, new ItemStack(UniverseItems.WATER.get(), 64));
        double lights = BioreactorBlockEntity.LIGHT_JOULES_PER_BATCH / 200;
        helper.succeedWhen(() -> {
            BioreactorBlockEntity current = helper.getBlockEntity(pos, BioreactorBlockEntity.class);
            int flags = current.data().get(BioreactorBlockEntity.FLAGS);
            helper.assertValueEqual(flags & 1, 0, "封了顶还算有阳光");
            helper.assertValueEqual(flags & 2, 2, "补光灯没开");
            helper.assertTrue(current.demand() > lights, "补光灯开着只要 " + current.demand() + " J/刻");
            helper.assertTrue(current.countItem(UniverseItems.ALGAL_BIOMASS.get()) >= 1, "没有做出藻类生物质");
        });
    }

    /** 放一台生物反应器：研究做完，选好工艺的配方，接种一罐长满的野生菌。 */
    private static BlockPos bioreactor(GameTestHelper helper, BlockPos pos, BioProcess process, MicrobeStrain strain) {
        ResearchData research = ResearchData.get(helper.getLevel().getServer());
        research.state().complete(BioRecipes.RESEARCH);
        research.setDirty();
        helper.setBlock(pos, UniverseBlocks.BIOREACTOR.get());
        BioreactorBlockEntity reactor = helper.getBlockEntity(pos, BioreactorBlockEntity.class);
        String recipe = process == BioProcess.BIOLEACHING ? "bioleaching_copper" : process.id();
        CompoundTag settings = new CompoundTag();
        settings.putString("Recipe", "universe:" + BioRecipes.DIR + "/" + recipe);
        reactor.applyBlueprintSettings(settings);
        helper.assertValueEqual(reactor.process(), process, "选的工艺");
        reactor.inoculate(Organism.wild(strain));
        reactor.setDensity(1);
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
