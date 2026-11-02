package io.github.sweetcornna.universe.gametest;

import io.github.sweetcornna.universe.block.OreKind;
import io.github.sweetcornna.universe.factory.RecipeMachineBlockEntity;
import io.github.sweetcornna.universe.factory.research.ResearchData;
import io.github.sweetcornna.universe.registry.UniverseBlocks;
import io.github.sweetcornna.universe.registry.UniverseItems;
import io.github.sweetcornna.universe.trade.ItemValues;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * M23 稀有资源的捷径（ADR-0040）的游戏内测试：三条捷径配方在制造台里做得出来，空间站的价钱按母星系的路线算。
 * 由 {@link UniverseGameTests} 登记。
 */
final class ShortcutGameTests {
    private ShortcutGameTests() {
    }

    /** 钛宝石：4 光学晶体 + 1 钛锭 → 4 钛晶石。 */
    static void titaniumSapphire(GameTestHelper helper) {
        ItemStack[] inputs = {new ItemStack(UniverseItems.oreDrop(OreKind.OPTICAL_CRYSTAL), 4),
                new ItemStack(UniverseItems.ORE_SMELTED.get(OreKind.TITANIUM).get(), 1)};
        assemble(helper, "structure_matrix", "titanium_crystal_from_optical_crystal", inputs, UniverseItems.TITANIUM_CRYSTAL.get(), 4);
    }

    /** 嬗变掺杂：8 电路板 + 4 硅 + 1 铀锭 → 4 处理器。 */
    static void dopedSiliconProcessor(GameTestHelper helper) {
        ItemStack[] inputs = {new ItemStack(UniverseItems.CIRCUIT_BOARD.get(), 8),
                new ItemStack(UniverseItems.ORE_SMELTED.get(OreKind.SILICON).get(), 4),
                new ItemStack(UniverseItems.ORE_SMELTED.get(OreKind.URANIUM).get(), 1)};
        assemble(helper, "information_matrix", "processor_from_doped_silicon", inputs, UniverseItems.PROCESSOR.get(), 4);
    }

    /** 超导磁阱：2 铜锭 + 2 石墨烯 + 4 氦 → 1 粒子容器。 */
    static void superconductingTrap(GameTestHelper helper) {
        ItemStack[] inputs = {new ItemStack(Items.COPPER_INGOT, 2), new ItemStack(UniverseItems.GRAPHENE.get(), 2),
                new ItemStack(UniverseItems.HELIUM.get(), 4)};
        assemble(helper, "gravity_matrix", "particle_container_superconducting", inputs, UniverseItems.PARTICLE_CONTAINER.get(), 1);
    }

    /**
     * 空间站的价钱不按捷径算：处理器还是两块电路板加两个微晶元件的价钱，粒子容器还是比两台电磁涡轮贵，钛晶石比三块钛锭贵。
     * 按捷径算的话处理器只有两个微晶元件的七成，粒子容器不到一台涡轮的一半。
     */
    static void pricesIgnoreShortcuts(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        double processor = value(helper, UniverseItems.PROCESSOR.get());
        double micro = value(helper, UniverseItems.MICROCRYSTALLINE_COMPONENT.get());
        double board = value(helper, UniverseItems.CIRCUIT_BOARD.get());
        helper.assertTrue(processor > 2 * micro + 2 * board, "处理器 " + processor + " 不到两个微晶元件加两块电路板 " + (2 * micro + 2 * board));
        double container = value(helper, UniverseItems.PARTICLE_CONTAINER.get());
        double turbine = value(helper, UniverseItems.ELECTROMAGNETIC_TURBINE.get());
        helper.assertTrue(container > 2 * turbine, "粒子容器 " + container + " 不到两台电磁涡轮 " + 2 * turbine);
        double crystal = value(helper, UniverseItems.TITANIUM_CRYSTAL.get());
        double ingot = value(helper, UniverseItems.ORE_SMELTED.get(OreKind.TITANIUM).get());
        helper.assertTrue(crystal > 3 * ingot, "钛晶石 " + crystal + " 不到三块钛锭 " + 3 * ingot);
        helper.assertTrue(ItemValues.value(server, UniverseItems.ORE_SMELTED.get(OreKind.URANIUM).get()).isPresent(), "铀锭没有价钱");
        helper.succeed();
    }

    private static double value(GameTestHelper helper, Item item) {
        var value = ItemValues.value(helper.getLevel().getServer(), item);
        helper.assertTrue(value.isPresent(), item + " 没有价钱");
        return value.getAsDouble();
    }

    /** 研究完这一档的矩阵科技，通电的制造台按选定的配方做一批，原料用完、产物数对。 */
    private static void assemble(GameTestHelper helper, String research, String recipe, ItemStack[] inputs, Item result, int count) {
        ResearchData.get(helper.getLevel().getServer()).state().complete(research);
        LinkageGameTests.power(helper);
        BlockPos pos = new BlockPos(3, 1, 3);
        helper.setBlock(pos, UniverseBlocks.ASSEMBLER.get());
        RecipeMachineBlockEntity assembler = helper.getBlockEntity(pos, RecipeMachineBlockEntity.class);
        CompoundTag settings = new CompoundTag();
        settings.putString("Recipe", "universe:assembling/" + recipe);
        assembler.applyBlueprintSettings(settings);
        for (int i = 0; i < inputs.length; i++) {
            assembler.setItem(i, inputs[i]);
        }
        Item last = inputs[inputs.length - 1].getItem();
        helper.succeedWhen(() -> {
            helper.assertValueEqual(assembler.countItem(result), count, "做出的" + result);
            helper.assertValueEqual(assembler.countItem(last), 0, "剩下的" + last);
        });
    }
}
