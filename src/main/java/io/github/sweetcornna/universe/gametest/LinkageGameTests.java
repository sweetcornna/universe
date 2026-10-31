package io.github.sweetcornna.universe.gametest;

import java.util.List;

import io.github.sweetcornna.universe.block.OreKind;
import io.github.sweetcornna.universe.block.ResourcePlantBlock;
import io.github.sweetcornna.universe.core.ecology.ResourcePlant;
import io.github.sweetcornna.universe.core.microbe.EngineeredPlant;
import io.github.sweetcornna.universe.core.microbe.Gene;
import io.github.sweetcornna.universe.core.tech.ModuleType;
import io.github.sweetcornna.universe.factory.RecipeMachineBlockEntity;
import io.github.sweetcornna.universe.factory.TallMachineBlock;
import io.github.sweetcornna.universe.factory.power.FuelGeneratorBlockEntity;
import io.github.sweetcornna.universe.factory.research.ResearchData;
import io.github.sweetcornna.universe.fleet.FleetTerminal;
import io.github.sweetcornna.universe.microbe.EngineeredSeedlingItem;
import io.github.sweetcornna.universe.microbe.PlantedSeedlings;
import io.github.sweetcornna.universe.registry.UniverseBlocks;
import io.github.sweetcornna.universe.registry.UniverseDataComponents;
import io.github.sweetcornna.universe.registry.UniverseItems;
import io.github.sweetcornna.universe.trade.ItemValues;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/**
 * M22 收尾和打通（ADR-0039）的游戏内测试：战斗残骸做模块、通用标签的原料和氩的钛合金、种下的改造种苗留住基因和发光、
 * 舰队终端认甲烷液氧、带记录的样本能卖。由 {@link UniverseGameTests} 登记。
 */
final class LinkageGameTests {
    private LinkageGameTests() {
    }

    /** 海盗残骸和守卫机残骸一样能做技术模块：制造台用两块海盗残骸做出一个伺服模块。 */
    static void pirateScrapMakesModule(GameTestHelper helper) {
        ResearchData.get(helper.getLevel().getServer()).state().complete("tech_modules");
        power(helper);
        BlockPos pos = new BlockPos(3, 1, 3);
        helper.setBlock(pos, UniverseBlocks.ASSEMBLER.get());
        RecipeMachineBlockEntity assembler = helper.getBlockEntity(pos, RecipeMachineBlockEntity.class);
        CompoundTag settings = new CompoundTag();
        settings.putString("Recipe", "universe:assembling/module_servo");
        assembler.applyBlueprintSettings(settings);
        assembler.setItem(0, new ItemStack(UniverseItems.PIRATE_SCRAP.get(), 2));
        assembler.setItem(1, new ItemStack(UniverseItems.SURVEY_DATA.get(), 2));
        assembler.setItem(2, new ItemStack(UniverseItems.ELECTRIC_MOTOR.get(), 1));
        assembler.setItem(3, new ItemStack(UniverseItems.GEAR.get(), 1));
        helper.succeedWhen(() -> {
            helper.assertValueEqual(assembler.countItem(UniverseItems.MODULES.get(ModuleType.SERVO).get()), 1, "做出的伺服模块");
            helper.assertValueEqual(assembler.countItem(UniverseItems.PIRATE_SCRAP.get()), 0, "剩下的海盗残骸");
        });
    }

    /**
     * 钛锭、钢的原料格换成了通用标签（c:ingots/titanium、c:ingots/steel），本模组的锭照样认；一份氩顶八份硫酸，
     * 电弧熔炉做出四块钛合金。
     */
    static void argonShieldsTitaniumAlloy(GameTestHelper helper) {
        ResearchData.get(helper.getLevel().getServer()).state().complete("titanium_alloy");
        power(helper);
        BlockPos pos = new BlockPos(3, 1, 3);
        helper.setBlock(pos, UniverseBlocks.ARC_SMELTER.get());
        RecipeMachineBlockEntity smelter = helper.getBlockEntity(pos, RecipeMachineBlockEntity.class);
        CompoundTag settings = new CompoundTag();
        settings.putString("Recipe", "universe:smelting/titanium_alloy_under_argon");
        smelter.applyBlueprintSettings(settings);
        smelter.setItem(0, new ItemStack(UniverseItems.ORE_SMELTED.get(OreKind.TITANIUM).get(), 4));
        smelter.setItem(1, new ItemStack(UniverseItems.STEEL.get(), 4));
        smelter.setItem(2, new ItemStack(UniverseItems.ARGON.get(), 1));
        helper.succeedWhen(() -> {
            helper.assertValueEqual(smelter.countItem(UniverseItems.TITANIUM_ALLOY.get()), 4, "做出的钛合金");
            helper.assertValueEqual(smelter.countItem(UniverseItems.ARGON.get()), 0, "剩下的氩");
        });
    }

    /**
     * 种在地上的改造种苗记着基因：带荧光素酶的亮着（亮度 {@link ResourcePlantBlock#GLOW_LIGHT}），拆掉以后掉回同样的种苗，
     * 区块上的记录跟着清掉。
     */
    static void plantedSeedlingKeepsGenes(GameTestHelper helper) {
        EngineeredPlant plant = new EngineeredPlant(ResourcePlant.OXYGEN_BLADDER, List.of(Gene.LUCIFERASE));
        BlockPos soil = new BlockPos(3, 1, 3);
        BlockPos pos = soil.above();
        helper.setBlock(soil, Blocks.GRASS_BLOCK);
        BlockState state = PlantedSeedlings.planted(UniverseBlocks.RESOURCE_PLANTS.get(plant.plant()).get().defaultBlockState()
                .setValue(ResourcePlantBlock.RIPE, false), plant);
        helper.setBlock(pos, state);
        BlockPos absolute = helper.absolutePos(pos);
        PlantedSeedlings.record(helper.getLevel(), absolute, plant);
        helper.assertTrue(helper.getBlockState(pos).getValue(ResourcePlantBlock.GLOWING), "带荧光素酶的没有亮");
        helper.assertValueEqual(helper.getLevel().getLightEmission(absolute), ResourcePlantBlock.GLOW_LIGHT, "亮度");
        helper.assertValueEqual(PlantedSeedlings.at(helper.getLevel(), absolute), plant, "区块上记的种苗");
        helper.destroyBlock(pos);
        helper.assertTrue(PlantedSeedlings.at(helper.getLevel(), absolute) == null, "拆掉以后记录还在");
        helper.succeedWhen(() -> {
            List<ItemEntity> drops = helper.getEntities(EntityTypes.ITEM, pos, 2);
            helper.assertTrue(drops.stream().anyMatch(drop -> plant.equals(EngineeredSeedlingItem.plant(drop.getItem()))),
                    "没有掉回同样的种苗：" + drops.stream().map(drop -> drop.getItem().toString()).toList());
        });
    }

    /** 舰队终端认甲烷液氧：一份发射燃料加两罐甲烷液氧算 6 份，要 4 份时先扣发射燃料，再扣两罐甲烷液氧。 */
    static void fleetTerminalBurnsMethalox(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        Inventory inventory = player.getInventory();
        inventory.clearContent();
        inventory.setItem(0, new ItemStack(UniverseItems.LAUNCH_FUEL.get(), 1));
        inventory.setItem(1, new ItemStack(UniverseItems.METHALOX_FUEL.get(), 2));
        helper.assertValueEqual(FleetTerminal.fuel(inventory), 6, "折成的发射燃料");
        FleetTerminal.takeFuel(inventory, 4);
        helper.assertValueEqual(inventory.getItem(0).getCount(), 0, "剩下的发射燃料");
        helper.assertValueEqual(inventory.getItem(1).getCount(), 0, "剩下的甲烷液氧");
        helper.succeed();
    }

    /** 改造种苗、镍锭、铂锭能卖：种苗带着记录不再挡着卖，镍和铂有了价钱。 */
    static void recordsAndMetalsSell(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        ItemStack seedling = EngineeredSeedlingItem.stack(new EngineeredPlant(ResourcePlant.OXYGEN_BLADDER, List.of(Gene.LUCIFERASE)), 1);
        helper.assertTrue(seedling.has(UniverseDataComponents.ENGINEERED_PLANT.get()), "种苗没带记录");
        helper.assertTrue(ItemValues.sellable(server, seedling), "带记录的种苗卖不掉");
        helper.assertTrue(ItemValues.sellable(server, new ItemStack(UniverseItems.NICKEL_INGOT.get())), "镍锭卖不掉");
        helper.assertTrue(ItemValues.sellable(server, new ItemStack(UniverseItems.PLATINUM_INGOT.get())), "铂锭卖不掉");
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
