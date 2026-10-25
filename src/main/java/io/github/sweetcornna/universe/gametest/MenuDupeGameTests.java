package io.github.sweetcornna.universe.gametest;

import java.util.List;

import io.github.sweetcornna.universe.block.ship.HelmBlockEntity;
import io.github.sweetcornna.universe.core.ship.ShipTech;
import io.github.sweetcornna.universe.core.survival.MultitoolClass;
import io.github.sweetcornna.universe.core.survival.MultitoolSpec;
import io.github.sweetcornna.universe.core.tech.Grade;
import io.github.sweetcornna.universe.core.tech.ModuleType;
import io.github.sweetcornna.universe.core.tech.TechGrid;
import io.github.sweetcornna.universe.core.tech.TechModule;
import io.github.sweetcornna.universe.entity.ship.ShipAssembly;
import io.github.sweetcornna.universe.entity.ship.ShipCargo;
import io.github.sweetcornna.universe.entity.ship.ShipCargoContainer;
import io.github.sweetcornna.universe.entity.ship.ShipCargoMenu;
import io.github.sweetcornna.universe.entity.ship.ShipDealer;
import io.github.sweetcornna.universe.entity.ship.ShipEntity;
import io.github.sweetcornna.universe.entity.ship.ShipPanel;
import io.github.sweetcornna.universe.entity.ship.ShipStructure;
import io.github.sweetcornna.universe.fishing.BaitMenu;
import io.github.sweetcornna.universe.fishing.Fishing;
import io.github.sweetcornna.universe.registry.UniverseBlocks;
import io.github.sweetcornna.universe.registry.UniverseDataComponents;
import io.github.sweetcornna.universe.registry.UniverseItems;
import io.github.sweetcornna.universe.survival.MultitoolItem;
import io.github.sweetcornna.universe.tech.TechMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;

/**
 * 界面刷物品（1.0 发布前的复查）的游戏内测试：饵格、技术配置界面按数字键换不走锁住的工具，界面认准打开时的那一把；
 * 两个人同时开着一艘船的货舱看到的是同一份；0.14 组装、一直飞着的船打开面板时定下超频格。
 * 由 {@link UniverseGameTests} 登记。
 */
final class MenuDupeGameTests {
    private MenuDupeGameTests() {
    }

    /**
     * 饵格开着的时候，在背包里另一把工具上按工具那一格的数字键：换不过来。取出饵以后两把工具上都没有饵；
     * 绕过界面把主手换成另一把，界面失效，饵也只写回原来那把。
     */
    static void baitMenuSwapNoDupe(GameTestHelper helper) {
        ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
        Inventory inventory = player.getInventory();
        ItemStack toolA = new ItemStack(UniverseItems.MULTITOOL.get());
        Fishing.setBait(toolA, new ItemStack(Items.COOKED_COD, 16));
        ItemStack toolB = new ItemStack(UniverseItems.MULTITOOL.get());
        inventory.setItem(0, toolA);
        inventory.setItem(9, toolB);
        inventory.setSelectedSlot(0);
        BaitMenu menu = BaitMenu.server(0, inventory, 0);

        // 菜单第 0 格是饵，1–27 是背包（背包第 9 格在第 1 格），28–36 是快捷栏
        menu.clicked(1, 0, ContainerInput.SWAP, player);
        helper.assertTrue(inventory.getItem(0) == toolA && inventory.getItem(9) == toolB, "按数字键把锁住的工具换走了");
        menu.clicked(28, 0, ContainerInput.QUICK_MOVE, player);
        menu.clicked(28, 0, ContainerInput.PICKUP, player);
        helper.assertTrue(inventory.getItem(0) == toolA && menu.getCarried().isEmpty(), "锁住的工具被拿走了");
        helper.assertTrue(menu.stillValid(player), "什么都没换界面就失效了");

        menu.clicked(0, 0, ContainerInput.PICKUP, player);
        helper.assertValueEqual(menu.getCarried().getCount(), 16, "取出来的饵");
        helper.assertTrue(Fishing.bait(toolA).isEmpty() && Fishing.bait(toolB).isEmpty(), "取出饵以后工具上还留着一份");

        inventory.setItem(0, toolB);
        inventory.setItem(9, toolA);
        helper.assertFalse(menu.stillValid(player), "主手换了一把工具，界面还开着");
        menu.getSlot(0).set(new ItemStack(Items.BREAD, 2));
        helper.assertTrue(Fishing.bait(toolB).isEmpty(), "饵写到了换上来的那把工具上");
        helper.assertValueEqual(Fishing.bait(toolA).getCount(), 2, "原来那把工具上的饵");
        helper.succeed();
    }

    /** 技术配置界面同上：数字键换不走主手的工具，取出模块以后两把工具上都没有，界面认准打开时的那一把。 */
    static void techMenuSwapNoDupe(GameTestHelper helper) {
        ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
        Inventory inventory = player.getInventory();
        MultitoolSpec spec = new MultitoolSpec(MultitoolClass.PISTOL, Grade.C, 0);
        ItemStack toolA = MultitoolItem.create(spec);
        toolA.set(UniverseDataComponents.TOOL_MODULES.get(),
                ItemContainerContents.fromItems(List.of(ShipDealer.moduleStack(TechModule.typical(ModuleType.BEAM_FOCUS, Grade.B)))));
        ItemStack toolB = MultitoolItem.create(spec);
        inventory.setItem(0, toolA);
        inventory.setItem(9, toolB);
        inventory.setSelectedSlot(0);
        TechMenu menu = TechMenu.server(0, inventory, player);
        int hotbar = TechMenu.INVENTORY_START + 27;

        menu.clicked(TechMenu.INVENTORY_START, 0, ContainerInput.SWAP, player);
        helper.assertTrue(inventory.getItem(0) == toolA && inventory.getItem(9) == toolB, "按数字键把正在配置的工具换走了");
        menu.clicked(hotbar, 0, ContainerInput.QUICK_MOVE, player);
        menu.clicked(hotbar, 0, ContainerInput.PICKUP, player);
        helper.assertTrue(inventory.getItem(0) == toolA && menu.getCarried().isEmpty(), "锁住的工具被拿走了");
        helper.assertTrue(menu.stillValid(player), "什么都没换界面就失效了");

        menu.clicked(TechMenu.TOOL_START, 0, ContainerInput.PICKUP, player);
        helper.assertTrue(menu.getCarried().getItem() == UniverseItems.MODULES.get(ModuleType.BEAM_FOCUS).get(), "没取出模块");
        helper.assertTrue(modules(toolA) == 0 && modules(toolB) == 0, "取出模块以后工具上还留着一份");

        inventory.setItem(0, toolB);
        inventory.setItem(9, toolA);
        helper.assertFalse(menu.stillValid(player), "主手换了一把工具，界面还开着");
        menu.getSlot(TechMenu.TOOL_START).set(menu.getCarried().copy());
        helper.assertTrue(modules(toolB) == 0, "模块写到了换上来的那把工具上");
        helper.assertTrue(modules(toolA) == 1, "模块没写回原来那把工具");
        helper.succeed();
    }

    private static long modules(ItemStack tool) {
        return tool.getOrDefault(UniverseDataComponents.TOOL_MODULES.get(), ItemContainerContents.EMPTY).nonEmptyItemCopyStream().count();
    }

    /**
     * 两个人同时开着一艘船的货舱（各读了一份也一样）：A 拿走钛合金，B 那边就空了、拿不到第二份；
     * 别处往货舱里装的东西两边都看得见，之后谁写回去都不会冲掉。
     */
    static void shipCargoViewersShare(GameTestHelper helper) {
        BlockPos helm = new BlockPos(3, 2, 3);
        helper.setBlock(helm, UniverseBlocks.SHIP_HELM.get());
        helper.setBlock(new BlockPos(2, 2, 3), UniverseBlocks.SHIP_THRUSTER.get());
        helper.setBlock(new BlockPos(4, 2, 3), UniverseBlocks.FUEL_TANK.get());
        BlockPos chest = new BlockPos(3, 2, 4);
        helper.setBlock(chest, Blocks.CHEST);
        helper.getBlockEntity(chest, ChestBlockEntity.class).setItem(0, new ItemStack(UniverseItems.TITANIUM_ALLOY.get(), 5));
        ShipEntity ship = ShipAssembly.assemble(helper.getLevel(), helper.absolutePos(helm), null, message -> {
        });
        helper.assertTrue(ship != null && ship.structure() != null, "没有组装出飞船");
        RegistryAccess registries = helper.getLevel().registryAccess();
        ServerPlayer a = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
        ServerPlayer b = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
        ShipCargoContainer cargoA = ShipCargoContainer.of(ship, registries);
        ShipCargoContainer cargoB = ShipCargoContainer.of(ship, registries);
        ShipCargoMenu menuA = ShipPanel.cargoMenu(1, a.getInventory(), ship, cargoA);
        ShipCargoMenu menuB = ShipPanel.cargoMenu(2, b.getInventory(), ship, cargoB);
        int slot = -1;
        for (int i = 0; i < menuA.gridSlots(); i++) {
            if (menuA.getSlot(i).getItem().is(UniverseItems.TITANIUM_ALLOY.get())) {
                slot = i;
            }
        }
        helper.assertTrue(slot >= 0, "货舱里找不到钛合金");

        menuA.clicked(slot, 0, ContainerInput.PICKUP, a);
        helper.assertValueEqual(menuA.getCarried().getCount(), 5, "A 拿到的钛合金");
        helper.assertTrue(menuB.getSlot(slot).getItem().isEmpty(), "A 拿走以后 B 还看得见钛合金");
        menuB.clicked(slot, 0, ContainerInput.PICKUP, b);
        helper.assertTrue(menuB.getCarried().isEmpty(), "B 又拿到一份钛合金");

        ShipCargo.insert(ship, registries, new ItemStack(Items.IRON_INGOT, 4));
        helper.assertTrue(cargoA.countItem(Items.IRON_INGOT) == 4 && cargoB.countItem(Items.IRON_INGOT) == 4, "开着的货舱看不到别处装进来的铁锭");
        int empty = -1;
        for (int i = menuA.gridSlots() - 1; i >= 0; i--) {
            if (menuA.getSlot(i).isActive() && !menuA.getSlot(i).hasItem()) {
                empty = i;
            }
        }
        helper.assertTrue(empty >= 0, "货舱里没有空格");
        menuA.clicked(empty, 0, ContainerInput.PICKUP, a);
        helper.assertTrue(menuA.getCarried().isEmpty(), "A 没把钛合金放回去");
        menuB.removed(b);
        menuA.removed(a);
        ShipCargoContainer reopened = ShipCargoContainer.of(ship, registries);
        helper.assertValueEqual(reopened.countItem(UniverseItems.TITANIUM_ALLOY.get()), 5, "船上的钛合金");
        helper.assertValueEqual(reopened.countItem(Items.IRON_INGOT), 4, "船上的铁锭");
        helper.succeed();
    }

    /**
     * 0.14 组装、一直飞着的船（驾驶台数据里没有超频格的种子，摘要里没有超频格）：打开面板时定下种子写回去，
     * 面板看到的超频格和算性能的摘要一致；再开一次不再重写，停到别处读出来还是那几格；先开着的货舱写回去也不会把它冲掉。
     */
    static void oldShipSuperchargeSettles(GameTestHelper helper) {
        BlockPos helmPos = new BlockPos(3, 2, 3);
        helper.setBlock(helmPos, UniverseBlocks.SHIP_HELM.get());
        helper.setBlock(new BlockPos(2, 2, 3), UniverseBlocks.SHIP_THRUSTER.get());
        helper.setBlock(new BlockPos(4, 2, 3), UniverseBlocks.FUEL_TANK.get());
        helper.setBlock(new BlockPos(3, 2, 4), Blocks.CHEST);
        HelmBlockEntity placed = helper.getBlockEntity(helmPos, HelmBlockEntity.class);
        for (int i = 0; i < HelmBlockEntity.SLOTS; i++) {
            ModuleType type = i < HelmBlockEntity.SLOTS / 2 ? ModuleType.HULL_REINFORCEMENT : ModuleType.DEFLECTOR;
            placed.items().set(i, ShipDealer.moduleStack(TechModule.typical(type, Grade.A)));
        }
        placed.setChanged();
        ShipEntity ship = ShipAssembly.assemble(helper.getLevel(), helper.absolutePos(helmPos), null, message -> {
        });
        helper.assertTrue(ship != null && ship.structure() != null, "没有组装出飞船");
        RegistryAccess registries = helper.getLevel().registryAccess();

        // 做成 0.14 存下来的样子：驾驶台数据里去掉种子，摘要里去掉每格的超频标记，再读回这艘船
        TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, registries);
        ship.saveWithoutId(output);
        CompoundTag saved = output.buildResult();
        for (Tag block : saved.getListOrEmpty("Blocks")) {
            CompoundTag data = block instanceof CompoundTag tag ? tag.getCompoundOrEmpty("Data") : new CompoundTag();
            if (data.contains(HelmBlockEntity.SUPERCHARGE)) {
                data.remove(HelmBlockEntity.SUPERCHARGE);
                for (Tag slot : data.getListOrEmpty(HelmBlockEntity.TECH)) {
                    if (slot instanceof CompoundTag entry) {
                        entry.remove("supercharged");
                    }
                }
            }
        }
        ship.load(TagValueInput.create(ProblemReporter.DISCARDING, registries, saved));
        ShipStructure old = ship.structure();
        helper.assertTrue(old != null && !HelmBlockEntity.hasSupercharge(old.helm().blockEntity()), "没做成旧船的样子");
        helper.assertTrue(HelmBlockEntity.readSummary(old.helm().blockEntity()).stream().noneMatch(ShipTech.Slot::supercharged), "旧摘要里有超频格");
        ShipCargoContainer cargo = ShipCargoContainer.of(ship, registries);

        ShipPanel.seedSupercharge(ship);
        ShipStructure seeded = ship.structure();
        CompoundTag data = seeded.helm().blockEntity();
        helper.assertTrue(HelmBlockEntity.hasSupercharge(data), "打开面板没定下超频格的种子");
        ShipStructure.ShipBlock helm = seeded.helm();
        int panel = helmAt(seeded, new BlockPos(helm.x(), helm.y(), helm.z()), registries).superchargedSlots();
        helper.assertTrue(Integer.bitCount(panel) == TechGrid.SHIP.supercharge(), "面板的超频格不对：" + Integer.toBinaryString(panel));
        List<ShipTech.Slot> summary = HelmBlockEntity.readSummary(data);
        helper.assertValueEqual(summary.size(), HelmBlockEntity.SLOTS, "摘要的格数");
        for (ShipTech.Slot slot : summary) {
            helper.assertTrue(slot.supercharged() == TechGrid.supercharged(panel, slot.index()), "面板和性能的超频格对不上：第 " + slot.index() + " 格");
        }

        ShipPanel.seedSupercharge(ship);
        helper.assertTrue(ship.structure() == seeded, "第二次打开面板又重写了驾驶台");
        helper.assertValueEqual(helmAt(seeded, new BlockPos(1000, 70, -1000), registries).superchargedSlots(), panel, "停到别处以后的超频格");

        cargo.setItem(0, new ItemStack(Items.GOLD_INGOT));
        cargo.setChanged();
        helper.assertTrue(HelmBlockEntity.hasSupercharge(ship.structure().helm().blockEntity()), "先开着的货舱写回去，把驾驶台的种子冲掉了");
        helper.assertTrue(ShipCargoContainer.of(ship, registries).countItem(Items.GOLD_INGOT) == 1, "放进货舱的金锭没写回船上");
        helper.succeed();
    }

    /** 按 {@code pos} 读出船上的驾驶台（面板用船上的位置，停船用落地的位置）。 */
    private static HelmBlockEntity helmAt(ShipStructure structure, BlockPos pos, RegistryAccess registries) {
        ShipStructure.ShipBlock block = structure.helm();
        return (HelmBlockEntity) BlockEntity.loadStatic(pos, block.state(), block.blockEntity(), registries);
    }
}
