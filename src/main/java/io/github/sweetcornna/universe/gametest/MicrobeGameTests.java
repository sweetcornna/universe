package io.github.sweetcornna.universe.gametest;

import java.util.List;

import io.github.sweetcornna.universe.block.HydroponicTrayBlockEntity;
import io.github.sweetcornna.universe.core.ecology.ResourcePlant;
import io.github.sweetcornna.universe.core.microbe.EarthMicrobes;
import io.github.sweetcornna.universe.core.microbe.EngineeredPlant;
import io.github.sweetcornna.universe.core.microbe.Gene;
import io.github.sweetcornna.universe.core.microbe.GeneEditing;
import io.github.sweetcornna.universe.core.microbe.Isolation;
import io.github.sweetcornna.universe.core.microbe.Organism;
import io.github.sweetcornna.universe.core.microbe.SampleSource;
import io.github.sweetcornna.universe.factory.TallMachineBlock;
import io.github.sweetcornna.universe.factory.power.FuelGeneratorBlockEntity;
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
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/**
 * M20 微生物和基因（ADR-0036 §五）的游戏内测试：培养箱从主世界的土壤样本分离出根瘤菌、把菌种扩增成两管，
 * 测序仪把基因记进基因库，基因编辑台把基因编辑进植物产物得到改造种苗，水培槽种改造种苗、施肥后长得快。
 * 由 {@link UniverseGameTests} 登记。
 */
final class MicrobeGameTests {
    /** 根瘤菌长得最好的温度。 */
    private static final int RHIZOBIUM_K = 301;

    private MicrobeGameTests() {
    }

    /** 主世界的土壤样本、28 °C、一份培养基：分离出豌豆根瘤菌，培养基用掉一份。 */
    static void incubatorIsolatesStrain(GameTestHelper helper) {
        power(helper);
        IncubatorBlockEntity incubator = incubator(helper);
        Sample sample = Sample.of(Sampling.body(helper.getLevel()).orElseThrow().id(), SampleSource.SOIL, RHIZOBIUM_K);
        incubator.setItem(BioMachineBlockEntity.INPUT, SampleItem.stack(sample));
        incubator.setItem(BioMachineBlockEntity.CONSUMABLE, new ItemStack(UniverseItems.CULTURE_MEDIUM.get(), 2));
        helper.succeedWhen(() -> {
            Organism grown = incubator.output();
            helper.assertTrue(grown != null, "没有分离出菌种");
            helper.assertValueEqual(grown.strain().key(), EarthMicrobes.RHIZOBIUM.key(), "分离出的菌");
            helper.assertValueEqual(incubator.getItem(BioMachineBlockEntity.CONSUMABLE).getCount(), 1, "剩下的培养基");
            helper.assertTrue(incubator.getItem(BioMachineBlockEntity.INPUT).isEmpty(), "样本没有用掉");
        });
    }

    /** 一管根瘤菌加一份培养基扩增成两管。 */
    static void incubatorExpandsStrain(GameTestHelper helper) {
        power(helper);
        IncubatorBlockEntity incubator = incubator(helper);
        incubator.setItem(BioMachineBlockEntity.INPUT, StrainTubeItem.stack(Organism.wild(EarthMicrobes.RHIZOBIUM), 1));
        incubator.setItem(BioMachineBlockEntity.CONSUMABLE, new ItemStack(UniverseItems.CULTURE_MEDIUM.get(), 1));
        helper.succeedWhen(() -> {
            ItemStack out = incubator.getItem(BioMachineBlockEntity.OUTPUT);
            helper.assertValueEqual(out.getCount(), Isolation.EXPAND_COPIES, "扩增出的管数");
            helper.assertValueEqual(StrainTubeItem.organism(out).strain().key(), EarthMicrobes.RHIZOBIUM.key(), "扩增出的菌");
        });
    }

    /** 测一管北极冷杆菌：抗冻蛋白进了基因库，菌株记为测过序，芯片用掉一块，菌种管原样放进产出格。 */
    static void sequencerFillsLibrary(GameTestHelper helper) {
        power(helper);
        BlockPos pos = new BlockPos(3, 1, 3);
        helper.setBlock(pos, UniverseBlocks.SEQUENCER.get());
        SequencerBlockEntity sequencer = helper.getBlockEntity(pos, SequencerBlockEntity.class);
        sequencer.setItem(BioMachineBlockEntity.INPUT, StrainTubeItem.stack(Organism.wild(EarthMicrobes.PSYCHROBACTER), 1));
        sequencer.setItem(BioMachineBlockEntity.CONSUMABLE, new ItemStack(UniverseItems.SEQUENCING_CHIP.get(), 2));
        helper.succeedWhen(() -> {
            var library = GeneLibraries.library(helper.getLevel().getServer());
            helper.assertTrue(library.has(Gene.ANTIFREEZE_PROTEIN), "基因库里没有抗冻蛋白");
            helper.assertTrue(library.sequenced(EarthMicrobes.PSYCHROBACTER.key()), "菌株没记成测过序");
            helper.assertValueEqual(sequencer.getItem(BioMachineBlockEntity.CONSUMABLE).getCount(), 1, "剩下的芯片");
            helper.assertTrue(StrainTubeItem.organism(sequencer.getItem(BioMachineBlockEntity.OUTPUT)) != null, "菌种管没进产出格");
        });
    }

    /** 基因库里有的一个基因编辑进产氧气的资源植物：得到带这个基因的改造种苗，编辑试剂用掉一份。 */
    static void geneEditorMakesSeedling(GameTestHelper helper) {
        power(helper);
        ItemStack crop = new ItemStack(UniverseItems.RESOURCES.get("oxygen").get());
        EngineeredPlant base = ((GeneEditorBlockEntity.PlantTarget) GeneEditorBlockEntity.target(crop).orElseThrow()).plant();
        Gene gene = List.of(Gene.values()).stream().filter(g -> base.canAdd(g) == GeneEditing.Result.OK).findFirst().orElseThrow();
        GeneLibraries.sequence(helper.getLevel().getServer(), null, List.of(gene));
        BlockPos pos = new BlockPos(3, 1, 3);
        helper.setBlock(pos, UniverseBlocks.GENE_EDITOR.get());
        GeneEditorBlockEntity editor = helper.getBlockEntity(pos, GeneEditorBlockEntity.class);
        editor.setItem(BioMachineBlockEntity.INPUT, crop);
        editor.setItem(BioMachineBlockEntity.CONSUMABLE, new ItemStack(UniverseItems.EDITING_KIT.get(), 2));
        editor.select(gene);
        helper.succeedWhen(() -> {
            EngineeredPlant edited = EngineeredSeedlingItem.plant(editor.getItem(BioMachineBlockEntity.OUTPUT));
            helper.assertTrue(edited != null, "没有改造种苗");
            helper.assertValueEqual(edited.added(), List.of(gene), "编辑进的基因");
            helper.assertValueEqual(editor.getItem(BioMachineBlockEntity.CONSUMABLE).getCount(), 1, "剩下的编辑试剂");
        });
    }

    /**
     * 两个水培槽：一个种普通的产氧植物，一个种没编辑过的改造种苗、拿硝酸铵右键施肥（一袋存三茬）。
     * 施了肥的那个长得快一半。
     */
    static void traySeedlingWithFertilizer(GameTestHelper helper) {
        power(helper);
        Item crop = UniverseItems.RESOURCES.get("oxygen").get();
        ResourcePlant plant = HydroponicTrayBlockEntity.farmable(new ItemStack(crop)).orElseThrow();
        BlockPos plainPos = new BlockPos(3, 1, 3);
        BlockPos fedPos = new BlockPos(3, 1, 5);
        helper.setBlock(plainPos, UniverseBlocks.HYDROPONIC_TRAY.get());
        helper.setBlock(fedPos, UniverseBlocks.HYDROPONIC_TRAY.get());
        HydroponicTrayBlockEntity plain = helper.getBlockEntity(plainPos, HydroponicTrayBlockEntity.class);
        HydroponicTrayBlockEntity fed = helper.getBlockEntity(fedPos, HydroponicTrayBlockEntity.class);
        helper.assertTrue(plain.sow(new ItemStack(crop)), "普通产物种不下去");
        helper.assertTrue(fed.sow(EngineeredSeedlingItem.stack(new EngineeredPlant(plant, List.of()), 1)), "改造种苗种不下去");
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(UniverseItems.AMMONIUM_NITRATE.get(), 2));
        helper.useBlock(fedPos, player);
        helper.assertValueEqual(fed.fertilizer(), HydroponicTrayBlockEntity.FERTILIZER_PER_BAG, "施肥以后存的肥");
        helper.assertValueEqual(player.getMainHandItem().getCount(), 1, "手里剩的化肥");
        helper.succeedWhen(() -> {
            helper.assertTrue(plain.growth() > 0.02, "普通的没有长：" + plain.growth());
            double ratio = fed.growth() / plain.growth();
            helper.assertTrue(ratio > 1.4 && ratio < 1.6, "施了肥的该快一半，实际是 " + ratio + " 倍");
        });
    }

    /**
     * 草方块、灰化土、苔藓块是土壤，泥是沉积物，水底的草方块也算沉积物；生存模式拿空采样管右键草方块，手里换成主世界的土壤样本。
     * 26.x 的 #dirt 不含草方块，只认这个标签时右键草方块什么也不发生。
     */
    static void sampleTubeReadsGrass(GameTestHelper helper) {
        BlockPos grass = new BlockPos(1, 1, 1);
        BlockPos podzol = new BlockPos(2, 1, 1);
        BlockPos moss = new BlockPos(3, 1, 1);
        BlockPos mud = new BlockPos(4, 1, 1);
        BlockPos underwater = new BlockPos(5, 1, 1);
        helper.setBlock(grass, net.minecraft.world.level.block.Blocks.GRASS_BLOCK);
        helper.setBlock(podzol, net.minecraft.world.level.block.Blocks.PODZOL);
        helper.setBlock(moss, net.minecraft.world.level.block.Blocks.PALE_MOSS_BLOCK);
        helper.setBlock(mud, net.minecraft.world.level.block.Blocks.MUD);
        helper.setBlock(underwater, net.minecraft.world.level.block.Blocks.GRASS_BLOCK);
        helper.setBlock(underwater.above(), net.minecraft.world.level.block.Blocks.WATER);
        helper.assertValueEqual(source(helper, grass), SampleSource.SOIL, "草方块");
        helper.assertValueEqual(source(helper, podzol), SampleSource.SOIL, "灰化土");
        helper.assertValueEqual(source(helper, moss), SampleSource.SOIL, "苍白苔藓块");
        helper.assertValueEqual(source(helper, mud), SampleSource.SEDIMENT, "泥");
        helper.assertValueEqual(source(helper, underwater), SampleSource.SEDIMENT, "水底的草方块");
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(UniverseItems.SAMPLE_TUBE.get()));
        helper.useBlock(grass, player);
        Sample sample = SampleItem.sample(player.getMainHandItem());
        helper.assertTrue(sample != null, "右键草方块没采到样本，手里是 " + player.getMainHandItem());
        helper.assertValueEqual(sample.source(), SampleSource.SOIL, "样本的来源");
        helper.assertValueEqual(sample.body(), Sampling.body(helper.getLevel()).orElseThrow().id().path(), "样本的天体");
        helper.succeed();
    }

    private static SampleSource source(GameTestHelper helper, BlockPos pos) {
        BlockPos absolute = helper.absolutePos(pos);
        return Sampling.source(helper.getLevel(), absolute, helper.getLevel().getBlockState(absolute));
    }

    private static IncubatorBlockEntity incubator(GameTestHelper helper) {
        BlockPos pos = new BlockPos(3, 1, 3);
        helper.setBlock(pos, UniverseBlocks.INCUBATOR.get());
        IncubatorBlockEntity incubator = helper.getBlockEntity(pos, IncubatorBlockEntity.class);
        incubator.setTemperature(RHIZOBIUM_K);
        return incubator;
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
