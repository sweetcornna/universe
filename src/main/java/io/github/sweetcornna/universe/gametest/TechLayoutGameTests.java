package io.github.sweetcornna.universe.gametest;

import java.util.List;

import io.github.sweetcornna.universe.block.ship.HelmBlockEntity;
import io.github.sweetcornna.universe.core.ship.ShipTech;
import io.github.sweetcornna.universe.core.survival.MultitoolClass;
import io.github.sweetcornna.universe.core.survival.MultitoolSpec;
import io.github.sweetcornna.universe.core.tech.Grade;
import io.github.sweetcornna.universe.core.tech.ModuleType;
import io.github.sweetcornna.universe.core.tech.Stat;
import io.github.sweetcornna.universe.core.tech.SuitSlots;
import io.github.sweetcornna.universe.core.tech.TechGrid;
import io.github.sweetcornna.universe.core.tech.TechModule;
import io.github.sweetcornna.universe.registry.UniverseBlocks;
import io.github.sweetcornna.universe.registry.UniverseDataComponents;
import io.github.sweetcornna.universe.registry.UniverseItems;
import io.github.sweetcornna.universe.survival.MultitoolItem;
import io.github.sweetcornna.universe.tech.SuitTech;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;

/** 科技的相邻和超频（ADR-0034 §五）的游戏内测试，由 {@link UniverseGameTests} 登记。 */
final class TechLayoutGameTests {
    private static final double EPSILON = 1e-9;

    private TechLayoutGameTests() {
    }

    /**
     * 多功能工具：没定种子的旧工具没有超频格；打开界面定下种子以后有两个，落在这档工具有的格子上，复制了也不变。
     * 模块放进超频格数值 ×1.25；两个同种模块挨着放各 +5%，分开放没有。
     */
    static void multitoolLayoutBoosts(GameTestHelper helper) {
        MultitoolSpec spec = new MultitoolSpec(MultitoolClass.PISTOL, Grade.S, 0);
        ItemStack tool = MultitoolItem.create(spec);
        helper.assertTrue(SuitTech.toolSupercharged(tool) == 0, "还没定种子就有超频格");
        SuitTech.seedTool(tool, RandomSource.create(20261024L));
        int mask = SuitTech.toolSupercharged(tool);
        helper.assertTrue(Integer.bitCount(mask) == TechGrid.MULTITOOL.supercharge(), "超频格不是两个：" + Integer.toBinaryString(mask));
        helper.assertTrue(mask >>> spec.slots() == 0, "超频格落在工具没有的格子上：" + Integer.toBinaryString(mask));
        helper.assertTrue(SuitTech.toolSupercharged(tool.copy()) == mask, "复制以后超频格变了");

        int charged = Integer.numberOfTrailingZeros(mask);
        int[] pair = null;
        int[] apart = null;
        for (int a = 0; a < spec.slots(); a++) {
            for (int b = a + 1; b < spec.slots(); b++) {
                if (TechGrid.supercharged(mask, a) || TechGrid.supercharged(mask, b)) {
                    continue;
                }
                if (TechGrid.MULTITOOL.neighbors(a).contains(b)) {
                    pair = pair == null ? new int[] {a, b} : pair;
                } else {
                    apart = apart == null ? new int[] {a, b} : apart;
                }
            }
        }
        helper.assertTrue(pair != null && apart != null, "找不到不超频的格子来比");
        double base = mining(tool);
        double plain = mining(tool, apart[0]) - base;
        double boosted = mining(tool, charged) - base;
        helper.assertTrue(plain > 0, "光束聚焦没加采矿");
        helper.assertTrue(Math.abs(boosted - plain * TechGrid.SUPERCHARGE) < EPSILON, "超频格没 ×1.25：" + plain + " -> " + boosted);
        double together = mining(tool, pair) - base;
        double separate = mining(tool, apart) - base;
        helper.assertTrue(Math.abs(together - separate * (1 + TechGrid.ADJACENT)) < EPSILON,
                "挨着放没 +5%：" + separate + " -> " + together);
        helper.succeed();
    }

    /** 装了光束聚焦（A 级）的这些格子以后，工具的采矿加成。 */
    private static double mining(ItemStack tool, int... slots) {
        ItemStack copy = tool.copy();
        NonNullList<ItemStack> items = NonNullList.withSize(SuitSlots.TOOL_SLOTS, ItemStack.EMPTY);
        for (int slot : slots) {
            items.set(slot, module(ModuleType.BEAM_FOCUS));
        }
        copy.set(UniverseDataComponents.TOOL_MODULES.get(), ItemContainerContents.fromItems(items));
        return SuitTech.tool(copy).get(Stat.MINING);
    }

    private static ItemStack module(ModuleType type) {
        ItemStack stack = new ItemStack(UniverseItems.MODULES.get(type).get());
        stack.set(UniverseDataComponents.TECH_MODULE.get(), TechModule.typical(type, Grade.A));
        return stack;
    }

    /**
     * 驾驶台：超频格按位置定下两个，每次问都一样；船体加固放进超频格，飞船的船体加成 ×1.25；
     * 存档里的摘要记着这格超频（组装飞船时读的就是它）。
     */
    static void helmSuperchargeTunesShip(GameTestHelper helper) {
        BlockPos pos = new BlockPos(3, 1, 3);
        helper.setBlock(pos, UniverseBlocks.SHIP_HELM.get());
        HelmBlockEntity helm = helper.getBlockEntity(pos, HelmBlockEntity.class);
        int mask = helm.superchargedSlots();
        helper.assertTrue(Integer.bitCount(mask) == TechGrid.SHIP.supercharge() && mask >>> HelmBlockEntity.SLOTS == 0,
                "驾驶台的超频格不对：" + Integer.toBinaryString(mask));
        helper.assertTrue(helm.superchargedSlots() == mask, "超频格每次问都不一样");
        int charged = Integer.numberOfTrailingZeros(mask);
        int plain = Integer.numberOfTrailingZeros(~mask);

        NonNullList<ItemStack> items = NonNullList.withSize(HelmBlockEntity.SLOTS, ItemStack.EMPTY);
        items.set(plain, module(ModuleType.HULL_REINFORCEMENT));
        List<ShipTech.Slot> plainSlots = HelmBlockEntity.summary(items, mask);
        helper.assertFalse(plainSlots.getFirst().supercharged(), "普通格记成了超频格");
        double hull = ShipTech.of(plainSlots, HelmBlockEntity.SLOTS).bonuses().get(Stat.HULL);

        items.set(plain, ItemStack.EMPTY);
        items.set(charged, module(ModuleType.HULL_REINFORCEMENT));
        List<ShipTech.Slot> chargedSlots = HelmBlockEntity.summary(items, mask);
        helper.assertTrue(chargedSlots.getFirst().supercharged(), "超频格没记上");
        double boosted = ShipTech.of(chargedSlots, HelmBlockEntity.SLOTS).bonuses().get(Stat.HULL);
        helper.assertTrue(hull > 0 && Math.abs(boosted - hull * TechGrid.SUPERCHARGE) < EPSILON, "超频格没 ×1.25：" + hull + " -> " + boosted);

        helm.items().set(charged, module(ModuleType.HULL_REINFORCEMENT));
        helm.setChanged();
        CompoundTag saved = helm.saveWithoutMetadata(helper.getLevel().registryAccess());
        List<ShipTech.Slot> read = HelmBlockEntity.readSummary(saved);
        helper.assertTrue(read.size() == 1 && read.getFirst().supercharged(), "存档里的摘要没记超频格：" + read);
        helper.succeed();
    }
}
