package io.github.sweetcornna.universe.gametest;

import java.util.List;

import io.github.sweetcornna.universe.block.ship.HelmBlockEntity;
import io.github.sweetcornna.universe.core.deepspace.Integrity;
import io.github.sweetcornna.universe.core.deepspace.Layout;
import io.github.sweetcornna.universe.core.deepspace.Part;
import io.github.sweetcornna.universe.core.ship.ShipTech;
import io.github.sweetcornna.universe.core.survival.MultitoolClass;
import io.github.sweetcornna.universe.core.survival.MultitoolSpec;
import io.github.sweetcornna.universe.core.tech.Grade;
import io.github.sweetcornna.universe.core.tech.ModuleType;
import io.github.sweetcornna.universe.core.tech.TechGrid;
import io.github.sweetcornna.universe.core.tech.TechModule;
import io.github.sweetcornna.universe.deepspace.DerelictLightBlock;
import io.github.sweetcornna.universe.deepspace.Hulks;
import io.github.sweetcornna.universe.deepspace.Placement;
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
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;

/**
 * 界面刷物品（1.0 发布前的复查）的游戏内测试：饵格、技术配置界面按数字键换不走锁住的工具，界面认准打开时的那一把；
 * 两个人同时开着一艘船的货舱看到的是同一份，原版界面就地改了货舱里的东西也写回船上；饵格开着时扣饵两边一起扣；
 * 0.14 组装、一直飞着的船打开面板时定下超频格；残骸报警时没换成的灯之后补上。
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
     * 原版界面直接改货舱里的那一叠（不经过 setItem，只调格子的 setChanged）也写回船上：背包只剩一叠没满的铁锭时
     * Shift 点击货舱里的一组铁锭，只挪过去 4 个，关掉再开货舱里是剩下的 60 个；拿着金锭点货舱里的收纳袋装进去，
     * 再开收纳袋里还是那 8 个。
     */
    static void shipCargoInPlaceEditsPersist(GameTestHelper helper) {
        BlockPos helm = new BlockPos(3, 2, 3);
        helper.setBlock(helm, UniverseBlocks.SHIP_HELM.get());
        helper.setBlock(new BlockPos(2, 2, 3), UniverseBlocks.SHIP_THRUSTER.get());
        helper.setBlock(new BlockPos(4, 2, 3), UniverseBlocks.FUEL_TANK.get());
        BlockPos chest = new BlockPos(3, 2, 4);
        helper.setBlock(chest, Blocks.CHEST);
        ChestBlockEntity box = helper.getBlockEntity(chest, ChestBlockEntity.class);
        box.setItem(0, new ItemStack(Items.IRON_INGOT, 64));
        box.setItem(1, new ItemStack(Items.BUNDLE));
        ShipEntity ship = ShipAssembly.assemble(helper.getLevel(), helper.absolutePos(helm), null, message -> {
        });
        helper.assertTrue(ship != null && ship.structure() != null, "没有组装出飞船");
        RegistryAccess registries = helper.getLevel().registryAccess();
        ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
        Inventory inventory = player.getInventory();
        for (int i = 0; i < 36; i++) {
            inventory.setItem(i, new ItemStack(Items.STONE, 64));
        }
        inventory.setItem(8, new ItemStack(Items.IRON_INGOT, 60));
        ShipCargoMenu menu = ShipPanel.cargoMenu(1, inventory, ship, ShipCargoContainer.of(ship, registries));
        int iron = slotOf(menu, Items.IRON_INGOT);
        int bundle = slotOf(menu, Items.BUNDLE);
        helper.assertTrue(iron >= 0 && bundle >= 0, "货舱里找不到铁锭和收纳袋");

        // 收纳袋装完东西会刷新玩家开着的界面：开着的是背包的话要往连接上发合成格，假玩家没有连接
        player.containerMenu = menu;
        menu.clicked(iron, 0, ContainerInput.QUICK_MOVE, player);
        helper.assertValueEqual(inventory.getItem(8).getCount(), 64, "Shift 点击以后背包里的铁锭");
        helper.assertValueEqual(menu.getSlot(iron).getItem().getCount(), 60, "Shift 点击以后货舱里的铁锭");
        menu.setCarried(new ItemStack(Items.GOLD_INGOT, 8));
        menu.clicked(bundle, 0, ContainerInput.PICKUP, player);
        helper.assertTrue(menu.getCarried().isEmpty(), "金锭没装进收纳袋");
        menu.removed(player);
        player.containerMenu = player.inventoryMenu;

        ShipCargoContainer reopened = ShipCargoContainer.of(ship, registries);
        helper.assertValueEqual(reopened.countItem(Items.IRON_INGOT), 60, "关掉再开，货舱里的铁锭");
        int packed = 0;
        for (int i = 0; i < reopened.getContainerSize(); i++) {
            ItemStack stack = reopened.getItem(i);
            if (stack.is(Items.BUNDLE)) {
                packed += stack.getOrDefault(DataComponents.BUNDLE_CONTENTS, BundleContents.EMPTY).itemCopies().mapToInt(ItemStack::getCount).sum();
            }
        }
        helper.assertValueEqual(packed, 8, "关掉再开，收纳袋里的金锭");
        helper.succeed();
    }

    /** 货舱界面上放着 {@code item} 的第一格；没有是 −1。 */
    private static int slotOf(ShipCargoMenu menu, Item item) {
        for (int i = 0; i < menu.gridSlots(); i++) {
            if (menu.getSlot(i).getItem().is(item)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 饵格开着的时候钩住了鱼（和平难度咬钩就钓上来）：饵格那一格和工具上一起少一个，取出来的是扣过的数量。
     * 开着饵格右键（改过的客户端发得出来）不甩竿。
     */
    static void baitConsumedWhileMenuOpen(GameTestHelper helper) {
        ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
        Inventory inventory = player.getInventory();
        ItemStack tool = new ItemStack(UniverseItems.MULTITOOL.get());
        Fishing.setBait(tool, new ItemStack(Items.COOKED_COD, 16));
        inventory.setItem(0, tool);
        inventory.setSelectedSlot(0);
        BaitMenu menu = BaitMenu.server(0, inventory, 0);
        player.containerMenu = menu;

        helper.assertTrue(Fishing.use(player.level(), player, InteractionHand.MAIN_HAND) == InteractionResult.FAIL, "饵格开着右键没被拦下");
        helper.assertTrue(Fishing.bobber(player) == null, "饵格开着甩出了浮标");
        Fishing.consumeBait(player);
        helper.assertValueEqual(menu.bait().getCount(), 15, "扣饵以后饵格里的饵");
        helper.assertValueEqual(Fishing.bait(tool).getCount(), 15, "扣饵以后工具上的饵");
        menu.clicked(0, 0, ContainerInput.PICKUP, player);
        helper.assertValueEqual(menu.getCarried().getCount(), 15, "取出来的饵");
        helper.assertTrue(Fishing.bait(tool).isEmpty(), "取出饵以后工具上还留着一份");
        player.containerMenu = player.inventoryMenu;
        helper.succeed();
    }

    /**
     * 残骸进了报警、那一刻灯没换成警报灯（远处那头的区块还没加载）：之后区块加载好了把灯换完。这里绕过拆方块直接扣完整度，
     * 报警那一刻就没换过灯。
     */
    static void hulkAlarmLightsCatchUp(GameTestHelper helper) {
        Layout.Builder b = new Layout.Builder();
        for (int x = 0; x < 5; x++) {
            for (int z = 0; z < 3; z++) {
                b.set(x, 0, z, Part.PLATING);
            }
        }
        b.set(0, 1, 1, Part.LIGHT);
        b.set(4, 1, 1, Part.LIGHT);
        Placement placement = new Placement(helper.absolutePos(new BlockPos(1, 1, 2)), 0);
        Hulks.Wreck wreck = Hulks.place(helper.getLevel(), placement, b.build(), 2L, 0, null);
        for (int i = 0; i < 9; i++) {
            wreck.integrity().remove(Part.CONDUIT);
        }
        helper.assertTrue(wreck.integrity().phase() == Integrity.Phase.ALARM, "扣到 28 没进报警");
        helper.assertFalse(helper.getBlockState(new BlockPos(1, 2, 3)).getValue(DerelictLightBlock.ALARM), "灯一放下就是警报灯");
        helper.succeedWhen(() -> {
            helper.assertTrue(helper.getBlockState(new BlockPos(1, 2, 3)).getValue(DerelictLightBlock.ALARM)
                    && helper.getBlockState(new BlockPos(5, 2, 3)).getValue(DerelictLightBlock.ALARM), "报警以后灯没换成警报灯");
            Hulks.forget(wreck);
        });
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
