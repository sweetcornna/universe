package io.github.sweetcornna.universe.gametest;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import io.github.sweetcornna.universe.core.chem.Gas;
import io.github.sweetcornna.universe.core.galaxy.Galaxy;
import io.github.sweetcornna.universe.core.galaxy.Planet;
import io.github.sweetcornna.universe.core.microbe.EarthMicrobes;
import io.github.sweetcornna.universe.core.microbe.Gene;
import io.github.sweetcornna.universe.core.microbe.Organism;
import io.github.sweetcornna.universe.core.survival.Environment;
import io.github.sweetcornna.universe.core.terraform.Biosphere;
import io.github.sweetcornna.universe.core.terraform.EffectiveEnvironment;
import io.github.sweetcornna.universe.core.terraform.Pioneer;
import io.github.sweetcornna.universe.core.terraform.Terraform;
import io.github.sweetcornna.universe.core.terraform.TerraformState;
import io.github.sweetcornna.universe.factory.TallMachineBlock;
import io.github.sweetcornna.universe.factory.power.FuelGeneratorBlockEntity;
import io.github.sweetcornna.universe.registry.UniverseBlocks;
import io.github.sweetcornna.universe.registry.UniverseDataComponents;
import io.github.sweetcornna.universe.registry.UniverseItems;
import io.github.sweetcornna.universe.terraform.EcologySeederBlockEntity;
import io.github.sweetcornna.universe.terraform.Greening;
import io.github.sweetcornna.universe.terraform.ReleaseTowerBlockEntity;
import io.github.sweetcornna.universe.terraform.TerraformData;
import io.github.sweetcornna.universe.terraform.Terraforming;
import io.github.sweetcornna.universe.world.planet.Planets;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/** 星球改造（ADR-0036 §六）的游戏内测试，由 {@link UniverseGameTests} 登记。测试世界是主世界，改的星球用完都还原。 */
final class TerraformGameTests {
    private TerraformGameTests() {
    }

    /**
     * 接上电的释放塔把三个二氧化碳一个个放进星球的大气：放一个少一个物品，星球的状态里二氧化碳多一个单位；
     * 不是气体的东西放不进去。
     */
    static void releaseTowerReleasesGas(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        Planet planet = untouched(server).orElseThrow();
        TerraformState before = Terraforming.state(server, planet.id());
        double carbonBefore = Terraforming.environment(server, planet).available(Gas.CARBON_DIOXIDE);
        powerPlant(helper);
        BlockPos pos = new BlockPos(3, 1, 3);
        helper.setBlock(pos, UniverseBlocks.ATMOSPHERE_RELEASE_TOWER.get());
        ReleaseTowerBlockEntity tower = helper.getBlockEntity(pos, ReleaseTowerBlockEntity.class);
        helper.assertFalse(tower.canPlaceItem(1, new ItemStack(Items.DIRT)), "泥土也放得进释放塔");
        helper.assertTrue(tower.canPlaceItem(1, new ItemStack(UniverseItems.NITROGEN.get())), "氮气放不进释放塔");
        tower.forceTarget(planet.id());
        tower.setItem(0, new ItemStack(UniverseItems.CARBON_DIOXIDE.get(), 3));
        helper.succeedWhen(() -> {
            helper.assertTrue(tower.released() == 3, "释放塔只放了 " + tower.released() + " 个");
            helper.assertTrue(tower.getItem(0).isEmpty(), "原料格里还剩 " + tower.getItem(0).getCount() + " 个");
            TerraformState after = Terraforming.state(server, planet.id());
            helper.assertTrue(Math.abs(after.delta(Gas.CARBON_DIOXIDE) - before.delta(Gas.CARBON_DIOXIDE) - 3) < 1e-9,
                    "星球的二氧化碳多了 " + (after.delta(Gas.CARBON_DIOXIDE) - before.delta(Gas.CARBON_DIOXIDE)));
            double carbonAfter = Terraforming.environment(server, planet).available(Gas.CARBON_DIOXIDE);
            helper.assertTrue(Math.abs(carbonAfter - carbonBefore - 3) < 1e-6, "现用环境里的二氧化碳没跟着变：多了 " + (carbonAfter - carbonBefore));
            Terraforming.update(server, planet.id(), state -> before);
        });
    }

    /**
     * 没改造过的星球和以前一模一样：现用的星球就是星球本来的那个对象，生存用的危害和强度也一样。
     * 另一颗星球改了不影响它们。
     */
    static void untouchedPlanetsUnchanged(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        Galaxy galaxy = Planets.galaxy(server);
        TerraformData data = TerraformData.get(server);
        Planet changed = untouched(server).orElseThrow();
        TerraformState before = Terraforming.state(server, changed.id());
        Terraforming.addGas(server, changed.id(), Gas.NITROGEN, 1000);
        try {
            int checked = 0;
            for (var system : galaxy.systems()) {
                for (Planet planet : system.bodies()) {
                    if (!planet.landable() || planet == changed || data.states().containsKey(planet.id().path())) {
                        continue;
                    }
                    EffectiveEnvironment environment = Terraforming.environment(server, planet);
                    helper.assertTrue(environment.planet() == planet, "没改造的 " + planet.id().path() + " 换了一个星球对象");
                    helper.assertTrue(Terraforming.effective(server, planet) == planet, "没改造的 " + planet.id().path() + " 现用的星球变了");
                    helper.assertValueEqual(Terraforming.hazards(environment), Environment.hazardsOf(planet), planet.id().path() + " 的危害");
                    checked++;
                }
            }
            helper.assertTrue(checked > 10, "只查了 " + checked + " 颗星球");
            helper.assertTrue(Terraforming.effective(server, changed) != changed, "放了一千单位氮气，现用的星球没变");
        } finally {
            Terraforming.update(server, changed.id(), state -> before);
        }
        helper.assertTrue(Terraforming.effective(server, changed) == changed, "还原以后现用的星球没换回来");
        helper.succeed();
    }

    /** 轨道反射镜的登记：放一面多一面镜子，切成遮阳板换一面，拆掉少一面；同一格登记两次只算一次。 */
    static void mirrorRegistryCounts(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        Planet planet = untouched(server).orElseThrow();
        TerraformState before = Terraforming.state(server, planet.id());
        BlockPos pos = helper.absolutePos(new BlockPos(2, 1, 2));
        try {
            Terraforming.addMirror(level, pos, planet.id(), false);
            Terraforming.addMirror(level, pos, planet.id(), false);
            TerraformState placed = Terraforming.state(server, planet.id());
            helper.assertTrue(placed.mirrors() == before.mirrors() + 1 && placed.shades() == before.shades(), "放下以后反射镜数不对：" + placed);
            double warmer = Terraforming.environment(server, planet).temperature() - Terraform.effective(galaxyOf(server), planet, before).temperature();
            helper.assertTrue(Math.abs(warmer - Terraform.MIRROR_KELVIN) < 1e-6, "一面反射镜暖了 " + warmer + " K");
            Terraforming.setShade(level, pos, true);
            TerraformState shaded = Terraforming.state(server, planet.id());
            helper.assertTrue(shaded.mirrors() == before.mirrors() && shaded.shades() == before.shades() + 1, "切成遮阳板以后数不对：" + shaded);
            Terraforming.removeMirror(level, pos);
            TerraformState removed = Terraforming.state(server, planet.id());
            helper.assertTrue(removed.mirrors() == before.mirrors() && removed.shades() == before.shades(), "拆掉以后数不对：" + removed);
            helper.assertTrue(Terraforming.mirror(level, pos).isEmpty(), "拆掉以后还登记着");
        } finally {
            Terraforming.removeMirror(level, pos);
            Terraforming.update(server, planet.id(), state -> before);
        }
        helper.succeed();
    }

    /**
     * 生态播种站种得活：挑一颗能改造的星球，给聚球藻（产氧光合）加上扛住那里每一样条件的基因，放进接上电、加了水的站里。
     * 播完一份消耗一管，星球记下这组基因，生物量至少加了一份；星球本来没有水的，这座站在给它浇水。
     */
    static void seederPlantsHardyStrain(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        Planet planet = null;
        Organism organism = null;
        TerraformData data = TerraformData.get(server);
        for (var system : Planets.galaxy(server).systems()) {
            for (Planet candidate : system.bodies()) {
                if (planet == null && Terraform.terraformable(candidate) && !data.states().containsKey(candidate.id().path())) {
                    organism = hardy(Terraforming.environment(server, candidate)).orElse(null);
                    planet = organism == null ? null : candidate;
                }
            }
        }
        helper.assertTrue(planet != null && organism != null, "找不到能种活聚球藻的星球");
        Planet target = planet;
        Organism strain = organism;
        TerraformState before = Terraforming.state(server, target.id());
        boolean dry = !Terraforming.environment(server, target).naturalWater();
        powerPlant(helper);
        BlockPos pos = new BlockPos(3, 1, 3);
        helper.setBlock(pos, UniverseBlocks.ECOLOGY_SEEDER.get());
        EcologySeederBlockEntity seeder = helper.getBlockEntity(pos, EcologySeederBlockEntity.class);
        helper.assertFalse(seeder.canPlaceItem(EcologySeederBlockEntity.SEED_SLOT, new ItemStack(Items.WHEAT_SEEDS)), "小麦种子也放得进播种站");
        seeder.forceTarget(target.id());
        seeder.setItem(EcologySeederBlockEntity.SEED_SLOT, tube(strain, 1));
        seeder.setItem(EcologySeederBlockEntity.WATER_SLOT, new ItemStack(UniverseItems.WATER.get(), 2));
        helper.succeedWhen(() -> {
            helper.assertTrue(seeder.seeded() == 1, "播种站只播了 " + seeder.seeded() + " 份");
            helper.assertTrue(seeder.getItem(EcologySeederBlockEntity.SEED_SLOT).isEmpty(), "菌种管没用掉");
            TerraformState after = Terraforming.state(server, target.id());
            helper.assertTrue(after.pioneers().contains(strain.genes()), "星球没记下这组基因：" + after.pioneers());
            helper.assertTrue(after.biomass() >= before.biomass() + Biosphere.SEED_BIOMASS * 0.99, "生物量只有 " + after.biomass());
            helper.assertTrue(!dry || seeder.irrigating() && after.irrigation() > 0, "星球没有水，播种站却没在浇：" + after.irrigation());
            helper.setBlock(pos, Blocks.AIR);
            helper.assertTrue(Terraforming.seeders(server, target.id()) == 0, "拆掉以后还算在浇水");
            Terraforming.update(server, target.id(), state -> before);
        });
    }

    /** 种不活：没有产氧光合的根瘤菌放进去，站不播、不消耗，界面上是"种不活"，星球的状态不变。 */
    static void seederRejectsUnfitStrain(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        Planet planet = untouched(server).orElseThrow();
        TerraformState before = Terraforming.state(server, planet.id());
        powerPlant(helper);
        BlockPos pos = new BlockPos(3, 1, 3);
        helper.setBlock(pos, UniverseBlocks.ECOLOGY_SEEDER.get());
        EcologySeederBlockEntity seeder = helper.getBlockEntity(pos, EcologySeederBlockEntity.class);
        seeder.forceTarget(planet.id());
        seeder.setItem(EcologySeederBlockEntity.SEED_SLOT, tube(Organism.wild(EarthMicrobes.RHIZOBIUM), 1));
        helper.startSequence().thenExecuteAfter(260, () -> {
            helper.assertTrue(seeder.data().get(EcologySeederBlockEntity.STATUS) == EcologySeederBlockEntity.CANNOT_SURVIVE,
                    "状态是 " + seeder.data().get(EcologySeederBlockEntity.STATUS));
            helper.assertTrue(seeder.seeded() == 0 && seeder.getItem(EcologySeederBlockEntity.SEED_SLOT).getCount() == 1, "种不活的菌也播了");
            TerraformState after = Terraforming.state(server, planet.id());
            helper.assertTrue(after.pioneers().equals(before.pioneers()) && after.biomass() == before.biomass(), "星球的状态变了：" + after);
        }).thenSucceed();
    }

    /**
     * 绿化：石头、土上先长地衣，生物量够了以后土变成苔藓块，平滑石头上不长。测试场地头顶有一层屏障，挑格子用的高度图
     * 够不着地面，这里直接对每一格长几次。
     */
    static void greeningCoversRockAndSoil(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        for (int x = 1; x <= 5; x++) {
            for (int z = 1; z <= 5; z++) {
                helper.setBlock(new BlockPos(x, 1, z), z <= 2 ? Blocks.STONE : z >= 4 ? Blocks.DIRT : Blocks.SMOOTH_STONE);
            }
        }
        for (int round = 0; round < 3; round++) {
            for (int x = 1; x <= 5; x++) {
                for (int z = 1; z <= 5; z++) {
                    Greening.grow(level, helper.absolutePos(new BlockPos(x, 1, z)), 0.001, level.getRandom());
                }
            }
        }
        for (int x = 1; x <= 5; x++) {
            for (int z = 1; z <= 5; z++) {
                var cover = helper.getBlockState(new BlockPos(x, 2, z));
                boolean lichen = cover.is(UniverseBlocks.GRAY_LICHEN.get()) || cover.is(UniverseBlocks.ORANGE_LICHEN.get());
                helper.assertTrue(lichen == (z != 3), (z == 3 ? "平滑石头上也长了地衣：" : "没长地衣：") + x + ", " + z);
            }
        }
        helper.assertFalse(Greening.grow(level, helper.absolutePos(new BlockPos(1, 1, 4)), 0.001, level.getRandom()),
                "生物量不够也长成了苔藓");
        int moss = 0;
        for (int round = 0; round < 40; round++) {
            for (int x = 1; x <= 5; x++) {
                Greening.grow(level, helper.absolutePos(new BlockPos(x, 1, 4)), 0.05, level.getRandom());
            }
        }
        for (int x = 1; x <= 5; x++) {
            if (helper.getBlockState(new BlockPos(x, 1, 4)).is(Blocks.MOSS_BLOCK)) {
                moss++;
            }
        }
        helper.assertTrue(moss == 5, "生物量够了，只有 " + moss + " 格土变成苔藓");
        helper.succeed();
    }

    /** 聚球藻加上扛住这个环境每一样条件的基因；有扛不住的条件（太冷、太热）或者要加的基因太多为空。按有人浇水算。 */
    private static Optional<Organism> hardy(EffectiveEnvironment environment) {
        Set<Gene> all = EnumSet.of(Gene.OXYGENIC_PHOTOSYNTHESIS, Gene.ANTIFREEZE_PROTEIN, Gene.THERMOSTABLE_ENZYMES, Gene.DNA_REPAIR,
                Gene.PROTON_PUMP, Gene.UV_SHIELD, Gene.COMPATIBLE_SOLUTES);
        EnumSet<Gene> added = EnumSet.noneOf(Gene.class);
        for (Pioneer.Requirement requirement : Pioneer.requirements(all, environment, 1)) {
            Gene gene = requirement.condition().gene();
            if (gene == null) {
                return Optional.empty();
            }
            if (!EarthMicrobes.SYNECHOCOCCUS.has(gene)) {
                added.add(gene);
            }
        }
        if (added.size() > Gene.MAX_ADDED) {
            return Optional.empty();
        }
        return Optional.of(new Organism(EarthMicrobes.SYNECHOCOCCUS, List.copyOf(added), 0));
    }

    private static ItemStack tube(Organism organism, int count) {
        ItemStack stack = new ItemStack(UniverseItems.STRAIN_TUBE.get(), count);
        stack.set(UniverseDataComponents.ORGANISM.get(), organism);
        return stack;
    }

    private static Galaxy galaxyOf(MinecraftServer server) {
        return Planets.galaxy(server);
    }

    /** 一颗能改造、还没动过的星球。 */
    private static Optional<Planet> untouched(MinecraftServer server) {
        TerraformData data = TerraformData.get(server);
        return Planets.galaxy(server).systems().stream()
                .flatMap(system -> system.bodies().stream())
                .filter(Terraform::terraformable)
                .filter(planet -> !data.states().containsKey(planet.id().path()))
                .findFirst();
    }

    /** 场地西北角一座感应塔，东北角一台装了燃料棒的人造恒星，中间的设备都在塔的覆盖范围里。 */
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
