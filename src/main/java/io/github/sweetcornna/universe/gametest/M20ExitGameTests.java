package io.github.sweetcornna.universe.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import io.github.sweetcornna.universe.block.ship.FuelTankBlockEntity;
import io.github.sweetcornna.universe.chem.reactor.ChemicalReactorBlockEntity;
import io.github.sweetcornna.universe.chem.reactor.ReactionBooks;
import io.github.sweetcornna.universe.chem.reactor.ReactorRecipes;
import io.github.sweetcornna.universe.core.chem.AtmosphereMix;
import io.github.sweetcornna.universe.core.chem.AtmosphereMixes;
import io.github.sweetcornna.universe.core.chem.Extraction;
import io.github.sweetcornna.universe.core.chem.Gas;
import io.github.sweetcornna.universe.core.chem.reaction.Reaction;
import io.github.sweetcornna.universe.core.ecology.ResourcePlant;
import io.github.sweetcornna.universe.core.galaxy.Planet;
import io.github.sweetcornna.universe.core.galaxy.PlanetType;
import io.github.sweetcornna.universe.core.microbe.EarthMicrobes;
import io.github.sweetcornna.universe.core.microbe.EngineeredPlant;
import io.github.sweetcornna.universe.core.microbe.Gene;
import io.github.sweetcornna.universe.core.microbe.Organism;
import io.github.sweetcornna.universe.core.microbe.PlantTolerance;
import io.github.sweetcornna.universe.core.microbe.SampleSource;
import io.github.sweetcornna.universe.core.microbe.TissueGenes;
import io.github.sweetcornna.universe.core.terraform.AtmosphericChemistry;
import io.github.sweetcornna.universe.core.terraform.EffectiveEnvironment;
import io.github.sweetcornna.universe.core.terraform.Terraform;
import io.github.sweetcornna.universe.core.terraform.TerraformState;
import io.github.sweetcornna.universe.factory.FuelRecipeMachineBlockEntity;
import io.github.sweetcornna.universe.factory.RecipeMachineBlockEntity;
import io.github.sweetcornna.universe.factory.TallMachineBlock;
import io.github.sweetcornna.universe.factory.power.FuelGeneratorBlockEntity;
import io.github.sweetcornna.universe.factory.research.ResearchData;
import io.github.sweetcornna.universe.microbe.BioMachineBlockEntity;
import io.github.sweetcornna.universe.microbe.EngineeredSeedlingItem;
import io.github.sweetcornna.universe.microbe.GeneEditorBlockEntity;
import io.github.sweetcornna.universe.microbe.GeneLibraries;
import io.github.sweetcornna.universe.microbe.IncubatorBlockEntity;
import io.github.sweetcornna.universe.microbe.Sample;
import io.github.sweetcornna.universe.microbe.SampleItem;
import io.github.sweetcornna.universe.microbe.Sampling;
import io.github.sweetcornna.universe.microbe.SequencerBlockEntity;
import io.github.sweetcornna.universe.microbe.StrainTubeItem;
import io.github.sweetcornna.universe.registry.UniverseBlocks;
import io.github.sweetcornna.universe.registry.UniverseItems;
import io.github.sweetcornna.universe.terraform.TerraformData;
import io.github.sweetcornna.universe.terraform.Terraforming;
import io.github.sweetcornna.universe.world.planet.Planets;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/**
 * M20 的退出标准整条走一遍（ADR-0039）：在一颗荒芜或冰冻的星球上，
 * <ol>
 *   <li>就地取二氧化碳、电解冰化的水、用萨巴蒂尔反应做甲烷液氧燃料加满燃料舱；</li>
 *   <li>采样培养出嗜冷的菌，测序后把抗冻基因编辑进氧囊草，种得到这颗星球上（先把大气加厚：稀薄大气的强紫外另要一个
 *       母星上没有的基因，加厚以后只剩寒冷这一道）；</li>
 *   <li>把这颗星球改造到不戴头盔也能呼吸。</li>
 * </ol>
 * 三段用的是同一颗星球（{@link #exitPlanet}）。燃料和生物两段用真的设备一台接一台地做；改造那段的气体量是几百万单位，
 * 按释放塔、提取器、反射镜做的事直接改星球的状态，再按服务端的规则读回来，看能不能呼吸，测完还原。
 * 测试世界是主世界，提取器取不到别的星球的气，所以第一步按那颗星球的大气算提取器的产量，取到的二氧化碳交给反应釜。
 * 由 {@link UniverseGameTests} 登记。
 */
final class M20ExitGameTests {
    /** 生物那一段：分离、测序、编辑三台设备各要的时间，加上余量。 */
    static final int BIOLOGY_TICKS = io.github.sweetcornna.universe.core.microbe.Isolation.ISOLATION_TICKS + SequencerBlockEntity.SEQUENCE_TICKS
            + GeneEditorBlockEntity.EDIT_TICKS + 400;
    /** 冰样本的温度：零下三度，北极冷杆菌长得了（263–301 K）。 */
    private static final int ICE_K = 270;

    private M20ExitGameTests() {
    }

    /**
     * 燃料：那颗星球的大气里取得到二氧化碳；八块冰在便携精炼机里化成水，电解槽分成氢和氧，反应釜按萨巴蒂尔反应把二氧化碳和氢
     * 做成甲烷，化工厂把甲烷和氧灌成一罐甲烷液氧，加进燃料舱。
     */
    static void fuel(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        Planet planet = exitPlanet(server).orElseThrow(() -> helper.assertionException("找不到合退出标准的荒芜或冰冻星球：" + report(server)));
        EffectiveEnvironment air = Terraform.effective(planet, compactStar(server, planet), TerraformState.EMPTY);
        double perMinute = Extraction.perMinute(air.mix(), Gas.CARBON_DIOXIDE, air.pressure());
        helper.assertTrue(perMinute > 0, planet.id().path() + " 的大气里取不到二氧化碳");
        var research = ResearchData.get(server).state();
        for (String tech : List.of("atmospheric_chemistry", "electrochemistry", "catalysis", "synthetic_fuels")) {
            research.complete(tech);
        }
        power(helper);

        BlockPos refinerPos = new BlockPos(1, 1, 3);
        helper.setBlock(refinerPos, UniverseBlocks.PORTABLE_REFINER.get());
        FuelRecipeMachineBlockEntity refiner = helper.getBlockEntity(refinerPos, FuelRecipeMachineBlockEntity.class);
        select(refiner, "universe:portable_refining/water");
        refiner.setItem(0, new ItemStack(Items.ICE, 8));
        refiner.setItem(refiner.fuelSlot(), new ItemStack(Items.COAL, 4));

        BlockPos cellPos = new BlockPos(3, 1, 3);
        helper.setBlock(cellPos, UniverseBlocks.ELECTROLYZER.get());
        RecipeMachineBlockEntity cell = helper.getBlockEntity(cellPos, RecipeMachineBlockEntity.class);
        select(cell, "universe:electrolysis/water");

        BlockPos reactorPos = new BlockPos(5, 1, 3);
        ReactionBooks.discover(server, Reaction.SABATIER, false);
        helper.setBlock(reactorPos, UniverseBlocks.CHEMICAL_REACTOR.get());
        ChemicalReactorBlockEntity reactor = helper.getBlockEntity(reactorPos, ChemicalReactorBlockEntity.class);
        reactor.setReaction(Reaction.SABATIER);
        reactor.setItem(ChemicalReactorBlockEntity.CATALYST_SLOT,
                new ItemStack(ReactorRecipes.item(Reaction.SABATIER.catalyst().item()), 1));

        BlockPos plantPos = new BlockPos(1, 1, 5);
        helper.setBlock(plantPos, UniverseBlocks.CHEMICAL_PLANT.get());
        RecipeMachineBlockEntity plant = helper.getBlockEntity(plantPos, RecipeMachineBlockEntity.class);
        select(plant, "universe:chemical/methalox_fuel");

        BlockPos tankPos = new BlockPos(3, 1, 5);
        helper.setBlock(tankPos, UniverseBlocks.FUEL_TANK_MK2.get());

        Item water = UniverseItems.WATER.get();
        Item hydrogen = UniverseItems.HYDROGEN.get();
        Item oxygen = UniverseItems.RESOURCES.get("oxygen").get();
        Item methane = UniverseItems.METHANE.get();
        Item carbonDioxide = UniverseItems.CARBON_DIOXIDE.get();
        int carbonIndex = inputIndex(Reaction.SABATIER, carbonDioxide);
        int hydrogenIndex = inputIndex(Reaction.SABATIER, hydrogen);
        helper.startSequence()
                // 冰化成水，交给电解槽
                .thenWaitUntil(() -> helper.assertTrue(refiner.countItem(water) >= 8, "冰只化出 " + refiner.countItem(water) + " 份水"))
                .thenExecute(() -> {
                    take(refiner, water, 8);
                    cell.setItem(0, new ItemStack(water, 8));
                })
                // 水分成氢和氧
                .thenWaitUntil(() -> helper.assertTrue(cell.countItem(hydrogen) >= 8 && cell.countItem(oxygen) >= 4,
                        "电解出氢 " + cell.countItem(hydrogen) + "、氧 " + cell.countItem(oxygen)))
                .thenExecute(() -> {
                    take(cell, hydrogen, 8);
                    take(cell, oxygen, 4);
                    // 提取器在那颗星球上取到的二氧化碳和电解出的氢进反应釜
                    reactor.setItem(carbonIndex, new ItemStack(carbonDioxide, 2));
                    reactor.setItem(hydrogenIndex, new ItemStack(hydrogen, 8));
                    plant.setItem(1, new ItemStack(oxygen, 4));
                })
                // 萨巴蒂尔反应出甲烷
                .thenWaitUntil(() -> helper.assertTrue(reactor.countItem(methane) >= 1, "反应釜还没做出甲烷"))
                .thenExecute(() -> {
                    take(reactor, methane, 1);
                    plant.setItem(0, new ItemStack(methane, 1));
                })
                // 甲烷和氧灌成一罐，加进燃料舱
                .thenWaitUntil(() -> helper.assertTrue(plant.countItem(UniverseItems.METHALOX_FUEL.get()) >= 1, "化工厂还没灌出甲烷液氧"))
                .thenExecute(() -> {
                    take(plant, UniverseItems.METHALOX_FUEL.get(), 1);
                    Player player = helper.makeMockPlayer(GameType.SURVIVAL);
                    player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(UniverseItems.METHALOX_FUEL.get()));
                    helper.useBlock(tankPos, player);
                    FuelTankBlockEntity tank = helper.getBlockEntity(tankPos, FuelTankBlockEntity.class);
                    helper.assertTrue(tank.fuel() >= 2500, "燃料舱只有 " + tank.fuel());
                })
                .thenSucceed();
    }

    /**
     * 生物：一份零下三度的冰样本分离出北极冷杆菌，测序把抗冻蛋白放进基因库，编辑进氧囊草。那颗星球没有生命、大气加厚以后
     * 还冷，没编辑的氧囊草种不下去，编辑过的种得上。
     */
    static void biology(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        Planet planet = exitPlanet(server).orElseThrow(() -> helper.assertionException("找不到合退出标准的荒芜或冰冻星球：" + report(server)));
        power(helper);
        BlockPos incubatorPos = new BlockPos(1, 1, 3);
        helper.setBlock(incubatorPos, UniverseBlocks.INCUBATOR.get());
        IncubatorBlockEntity incubator = helper.getBlockEntity(incubatorPos, IncubatorBlockEntity.class);
        incubator.setTemperature(ICE_K);
        Sample sample = Sample.of(Sampling.body(helper.getLevel()).orElseThrow().id(), SampleSource.ICE, ICE_K);
        incubator.setItem(BioMachineBlockEntity.INPUT, SampleItem.stack(sample));
        incubator.setItem(BioMachineBlockEntity.CONSUMABLE, new ItemStack(UniverseItems.CULTURE_MEDIUM.get(), 1));

        BlockPos sequencerPos = new BlockPos(3, 1, 3);
        helper.setBlock(sequencerPos, UniverseBlocks.SEQUENCER.get());
        SequencerBlockEntity sequencer = helper.getBlockEntity(sequencerPos, SequencerBlockEntity.class);
        sequencer.setItem(BioMachineBlockEntity.CONSUMABLE, new ItemStack(UniverseItems.SEQUENCING_CHIP.get(), 1));

        BlockPos editorPos = new BlockPos(5, 1, 3);
        helper.setBlock(editorPos, UniverseBlocks.GENE_EDITOR.get());
        GeneEditorBlockEntity editor = helper.getBlockEntity(editorPos, GeneEditorBlockEntity.class);
        editor.setItem(BioMachineBlockEntity.CONSUMABLE, new ItemStack(UniverseItems.EDITING_KIT.get(), 1));

        Planet thick = Terraform.effective(planet, compactStar(server, planet), thickened(planet)).planet();
        Set<Gene> wild = TissueGenes.plant(ResourcePlant.OXYGEN_BLADDER);
        helper.assertFalse(PlantTolerance.canPlant(ResourcePlant.OXYGEN_BLADDER, wild, true, thick),
                "没编辑的氧囊草就种得到加厚大气的 " + planet.id().path());
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(incubator.output() != null, "冰样本还没分离出菌"))
                .thenExecute(() -> {
                    Organism grown = incubator.output();
                    helper.assertValueEqual(grown.strain().key(), EarthMicrobes.PSYCHROBACTER.key(), "冰样本分离出的菌");
                    ItemStack tube = incubator.getItem(BioMachineBlockEntity.OUTPUT).copy();
                    incubator.setItem(BioMachineBlockEntity.OUTPUT, ItemStack.EMPTY);
                    sequencer.setItem(BioMachineBlockEntity.INPUT, tube);
                })
                // 基因库是全服的，别的测试也会测这株菌，所以等的是这台测序仪自己测完（菌种管进了产出格）
                .thenWaitUntil(() -> helper.assertTrue(StrainTubeItem.organism(sequencer.getItem(BioMachineBlockEntity.OUTPUT)) != null,
                        "测序仪还没测完"))
                .thenExecute(() -> {
                    helper.assertTrue(GeneLibraries.library(server).has(Gene.ANTIFREEZE_PROTEIN), "基因库里没有抗冻蛋白");
                    editor.setItem(BioMachineBlockEntity.INPUT, new ItemStack(UniverseItems.RESOURCES.get("oxygen").get()));
                    editor.select(Gene.ANTIFREEZE_PROTEIN);
                })
                .thenWaitUntil(() -> helper.assertTrue(EngineeredSeedlingItem.plant(editor.getItem(BioMachineBlockEntity.OUTPUT)) != null,
                        "还没编辑出种苗"))
                .thenExecute(() -> {
                    EngineeredPlant edited = EngineeredSeedlingItem.plant(editor.getItem(BioMachineBlockEntity.OUTPUT));
                    helper.assertValueEqual(edited.plant(), ResourcePlant.OXYGEN_BLADDER, "编辑的植物");
                    helper.assertValueEqual(edited.added(), List.of(Gene.ANTIFREEZE_PROTEIN), "编辑进的基因");
                    helper.assertTrue(PlantTolerance.canPlant(edited.plant(), edited.genes(), true, thick),
                            "编辑过抗冻蛋白的氧囊草还种不到加厚大气的 " + planet.id().path() + "：" + PlantTolerance.barriers(thick));
                })
                .thenSucceed();
    }

    /**
     * 改造：提取器把有毒的和多余的气体抽掉，释放塔补上氮和氧，反射镜把温度调进温和的范围，再等三个现实月让一氧化碳这些慢慢氧化掉。
     * 服务端读回来能直接呼吸、没有危害，测完还原。
     */
    static void terraform(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        Planet planet = exitPlanet(server).orElseThrow(() -> helper.assertionException("找不到合退出标准的荒芜或冰冻星球：" + report(server)));
        TerraformState before = Terraforming.state(server, planet.id());
        TerraformState plan = breathablePlan(planet, compactStar(server, planet)).orElseThrow();
        try {
            helper.assertFalse(Terraforming.environment(server, planet).breathable(), planet.id().path() + " 本来就能呼吸");
            Terraforming.update(server, planet.id(), state -> plan);
            EffectiveEnvironment after = Terraforming.environment(server, planet);
            helper.assertTrue(after.breathable(), "改造完还不能呼吸：" + Terraform.thresholds(after));
            helper.assertTrue(Terraform.reached(after).contains(Terraform.Stage.BREATHABLE), "没到能呼吸这一档");
            helper.assertTrue(Terraforming.hazards(after).isEmpty(), "还有危害：" + Terraforming.hazards(after));
            helper.assertTrue(Terraforming.effective(server, planet).atmosphere().breathable(), "现用的星球大气还不能呼吸");
        } finally {
            Terraforming.update(server, planet.id(), state -> before);
        }
        helper.succeed();
    }

    // ---- 挑星球

    /**
     * 退出标准要的星球：荒芜或冰冻、没有生命（有生命的星球上氧囊草本来就长）、没改造过、大气里取得到二氧化碳、
     * 大气加厚以后氧囊草只差抗冻基因、按 {@link #breathablePlan} 改得到能呼吸。星团由世界种子定，挑法每次一样，取第一颗。
     */
    static Optional<Planet> exitPlanet(MinecraftServer server) {
        TerraformData data = TerraformData.get(server);
        Set<Gene> wild = TissueGenes.plant(ResourcePlant.OXYGEN_BLADDER);
        Set<Gene> edited = new java.util.HashSet<>(wild);
        edited.add(Gene.ANTIFREEZE_PROTEIN);
        List<Planet> candidates = new ArrayList<>();
        for (var system : Planets.galaxy(server).systems()) {
            for (Planet planet : system.bodies()) {
                if ((planet.type() == PlanetType.FROZEN || planet.type() == PlanetType.BARREN) && Terraform.terraformable(planet)
                        && !data.states().containsKey(planet.id().path())) {
                    candidates.add(planet);
                }
            }
        }
        for (Planet planet : candidates) {
            boolean compact = compactStar(server, planet);
            EffectiveEnvironment air = Terraform.effective(planet, compact, TerraformState.EMPTY);
            if (Extraction.perMinute(air.mix(), Gas.CARBON_DIOXIDE, air.pressure()) <= 0) {
                continue;
            }
            Planet thick = Terraform.effective(planet, compact, thickened(planet)).planet();
            if (PlantTolerance.canPlant(ResourcePlant.OXYGEN_BLADDER, wild, true, thick)
                    || !PlantTolerance.canPlant(ResourcePlant.OXYGEN_BLADDER, edited, true, thick)) {
                continue;
            }
            if (breathablePlan(planet, compact).isPresent()) {
                return Optional.of(planet);
            }
        }
        return Optional.empty();
    }

    /** 挑不到星球时报每一步还剩几颗，看是哪个条件卡住了。 */
    private static String report(MinecraftServer server) {
        Set<Gene> wild = TissueGenes.plant(ResourcePlant.OXYGEN_BLADDER);
        Set<Gene> edited = new java.util.HashSet<>(wild);
        edited.add(Gene.ANTIFREEZE_PROTEIN);
        int candidates = 0;
        int carbon = 0;
        int lifeless = 0;
        int plantable = 0;
        int breathable = 0;
        for (var system : Planets.galaxy(server).systems()) {
            for (Planet planet : system.bodies()) {
                if ((planet.type() != PlanetType.FROZEN && planet.type() != PlanetType.BARREN) || !Terraform.terraformable(planet)) {
                    continue;
                }
                candidates++;
                boolean compact = compactStar(server, planet);
                EffectiveEnvironment air = Terraform.effective(planet, compact, TerraformState.EMPTY);
                if (Extraction.perMinute(air.mix(), Gas.CARBON_DIOXIDE, air.pressure()) <= 0) {
                    continue;
                }
                carbon++;
                Planet thick = Terraform.effective(planet, compact, thickened(planet)).planet();
                if (PlantTolerance.canPlant(ResourcePlant.OXYGEN_BLADDER, wild, true, thick)) {
                    continue;
                }
                lifeless++;
                if (!PlantTolerance.canPlant(ResourcePlant.OXYGEN_BLADDER, edited, true, thick)) {
                    continue;
                }
                plantable++;
                if (breathablePlan(planet, compact).isPresent()) {
                    breathable++;
                }
            }
        }
        return "荒芜和冰冻 " + candidates + "，取得到二氧化碳 " + carbon + "，没有生命 " + lifeless + "，只差抗冻 " + plantable + "，改得到能呼吸 " + breathable;
    }

    /**
     * 改到能呼吸的一套做法：除了氮、氧、惰性气体，别的抽掉九成九；氮补到 0.78、氧补到 0.21 个大气压；反射镜（或遮阳板）一面面加，
     * 加到温度进温和的范围；最后放三个现实月让大气自净。改不到返回空。
     */
    static Optional<TerraformState> breathablePlan(Planet planet, boolean compact) {
        TerraformState state = thickened(planet);
        TerraformState tuned = null;
        for (int count = 0; count <= Terraform.MAX_MIRRORS && tuned == null; count++) {
            for (TerraformState option : List.of(state.withMirrors(count), state.withShades(count))) {
                double temperature = Terraform.effective(planet, compact, option).temperature();
                if (tuned == null && temperature >= Terraform.TEMPERATE_MIN && temperature <= Terraform.TEMPERATE_MAX) {
                    tuned = option;
                }
            }
        }
        if (tuned == null) {
            return Optional.empty();
        }
        TerraformState settled = Terraform.advance(tuned, planet, compact, (long) (90 * AtmosphericChemistry.TICKS_PER_REAL_DAY));
        EffectiveEnvironment environment = Terraform.effective(planet, compact, settled);
        return environment.breathable() && environment.planet().hazards().isEmpty() ? Optional.of(settled) : Optional.empty();
    }

    /** 改造的头一步：除了氮、氧、惰性气体，别的抽掉九成九；氮补到 0.78、氧补到 0.21 个大气压。还没调温度。 */
    static TerraformState thickened(Planet planet) {
        AtmosphereMix mix = AtmosphereMixes.base(planet);
        double capacity = Terraform.capacity(planet);
        TerraformState state = TerraformState.EMPTY;
        for (Map.Entry<Gas, Double> entry : mix.fractions().entrySet()) {
            Gas gas = entry.getKey();
            if (gas == Gas.NITROGEN || gas == Gas.OXYGEN || gas == Gas.ARGON || gas == Gas.HELIUM) {
                continue;
            }
            state = Terraform.addGas(planet, mix, state, gas, -0.99 * Terraform.baseUnits(planet, mix, gas));
        }
        state = Terraform.addGas(planet, mix, state, Gas.NITROGEN, Math.max(0, 0.78 * capacity - Terraform.baseUnits(planet, mix, Gas.NITROGEN)));
        return Terraform.addGas(planet, mix, state, Gas.OXYGEN, Math.max(0, 0.21 * capacity - Terraform.baseUnits(planet, mix, Gas.OXYGEN)));
    }

    private static boolean compactStar(MinecraftServer server, Planet planet) {
        return Planets.galaxy(server).system(planet.id().system()).map(system -> system.star().starClass().isCompact()).orElse(false);
    }

    // ---- 设备

    private static int inputIndex(Reaction reaction, Item item) {
        for (int i = 0; i < reaction.inputs().size(); i++) {
            if (ReactorRecipes.item(reaction.inputs().get(i)) == item) {
                return i;
            }
        }
        throw new IllegalArgumentException(item + " is not an input of " + reaction);
    }

    /** 从设备里拿走几个这样东西（只拿产物，调用的地方保证它不是这台设备的原料）。 */
    private static void take(net.minecraft.world.Container machine, Item item, int count) {
        for (int slot = 0; slot < machine.getContainerSize() && count > 0; slot++) {
            ItemStack stack = machine.getItem(slot);
            if (stack.is(item)) {
                int taken = Math.min(count, stack.getCount());
                stack.shrink(taken);
                count -= taken;
            }
        }
        machine.setChanged();
    }

    private static void select(RecipeMachineBlockEntity machine, String recipe) {
        CompoundTag settings = new CompoundTag();
        settings.putString("Recipe", recipe);
        machine.applyBlueprintSettings(settings);
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
