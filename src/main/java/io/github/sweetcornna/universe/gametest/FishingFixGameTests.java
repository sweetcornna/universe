package io.github.sweetcornna.universe.gametest;

import java.util.List;

import io.github.sweetcornna.universe.core.fishing.Bait;
import io.github.sweetcornna.universe.core.fishing.Catch;
import io.github.sweetcornna.universe.core.fishing.Junk;
import io.github.sweetcornna.universe.core.survival.ToolMode;
import io.github.sweetcornna.universe.core.survival.ToolWeapon;
import io.github.sweetcornna.universe.fishing.Fishing;
import io.github.sweetcornna.universe.fishing.FishingBobber;
import io.github.sweetcornna.universe.registry.UniverseDataComponents;
import io.github.sweetcornna.universe.registry.UniverseItems;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * 1.0.0 发布前钓鱼的修正：浮标认准甩竿的那一把工具。快捷栏里两把钓鱼模式的工具，拿装了专门的饵的那把甩竿，换到另一把
 * 再钩住，原来专门的饵的概率照算、饵却一个不扣（扣的是换上来那把的）。由 {@link UniverseGameTests} 登记。
 *
 * <p>假玩家不在世界里，换格、右键由测试替它做；钓上来的东西用 {@link FishingBobber#forceBite} 直接定下。
 */
final class FishingFixGameTests {
    /** 场地中间挖一个 3×3、3 格深的水池，浮标放在中间那一格的水面上。 */
    private static final BlockPos POOL = new BlockPos(3, 3, 3);
    private static final int SPECIAL = 2;
    private static final int FOOD = 4;

    private FishingFixGameTests() {
    }

    /** 拿 A（夜光饵）甩竿，换到 B（熟鳕鱼）：下一刻浮标就收回，两把的饵都不动。 */
    static void swappedToolDropsBobber(GameTestHelper helper) {
        pool(helper);
        ServerPlayer player = angler(helper);
        Inventory inventory = player.getInventory();
        ItemStack toolA = inventory.getItem(0);
        ItemStack toolB = inventory.getItem(1);
        FishingBobber bobber = castInto(helper, player);
        helper.assertValueEqual(bobber.bait(), Bait.GLOW, "这一竿用的饵");
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertValueEqual(bobber.phase(), FishingBobber.Phase.FLOATING, "浮标没浮在水上"))
                .thenExecute(() -> inventory.setSelectedSlot(1))
                .thenExecuteAfter(2, () -> {
                    helper.assertTrue(bobber.isRemoved(), "换了一把工具浮标还在");
                    helper.assertTrue(Fishing.bobber(player) == null, "换了一把工具还记着浮标");
                    assertBait(helper, toolA, toolB);
                })
                .thenSucceed();
    }

    /**
     * 拿 A 甩竿，咬钩的那一刻先换到 B 再右键（浮标还没来得及收回）：这一竿作废，两把的饵都不动，也没钓上东西。
     * 和平难度咬钩就钩住，别的难度要右键。
     */
    static void swapThenHookKeepsBait(GameTestHelper helper) {
        pool(helper);
        ServerPlayer player = angler(helper);
        Inventory inventory = player.getInventory();
        ItemStack toolA = inventory.getItem(0);
        ItemStack toolB = inventory.getItem(1);
        FishingBobber bobber = castInto(helper, player);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertValueEqual(bobber.phase(), FishingBobber.Phase.FLOATING, "浮标没浮在水上"))
                .thenExecute(() -> {
                    inventory.setSelectedSlot(1);
                    bobber.forceBite(Catch.junk(Junk.TANGLED_WEED));
                    if (!bobber.isRemoved()) {
                        Fishing.use(player.level(), player, InteractionHand.MAIN_HAND);
                    }
                    helper.assertTrue(bobber.isRemoved(), "换了一把工具钩住以后浮标还在");
                    assertBait(helper, toolA, toolB);
                    helper.assertTrue(!inventory.contains(new ItemStack(UniverseItems.JUNK.get(Junk.TANGLED_WEED).get())), "换了一把工具还钓上了东西");
                })
                .thenSucceed();
    }

    /** 拿 A 甩竿、拿 A 钩住：A 的饵用掉一个，B 的不动，钓上来的东西进背包。 */
    static void sameToolSpendsBait(GameTestHelper helper) {
        pool(helper);
        ServerPlayer player = angler(helper);
        Inventory inventory = player.getInventory();
        ItemStack toolA = inventory.getItem(0);
        ItemStack toolB = inventory.getItem(1);
        FishingBobber bobber = castInto(helper, player);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertValueEqual(bobber.phase(), FishingBobber.Phase.FLOATING, "浮标没浮在水上"))
                .thenExecute(() -> {
                    bobber.forceBite(Catch.junk(Junk.TANGLED_WEED));
                    if (!bobber.isRemoved()) {
                        Fishing.use(player.level(), player, InteractionHand.MAIN_HAND);
                    }
                })
                .thenWaitUntil(() -> helper.assertTrue(bobber.isRemoved(), "拉上来以后浮标还在"))
                .thenExecute(() -> {
                    helper.assertValueEqual(Fishing.bait(toolA).getCount(), SPECIAL - 1, "甩竿那把工具剩下的饵");
                    helper.assertValueEqual(Fishing.bait(toolB).getCount(), FOOD, "另一把工具的饵");
                    helper.assertTrue(inventory.contains(new ItemStack(UniverseItems.JUNK.get(Junk.TANGLED_WEED).get())), "藻团没进背包");
                })
                .thenSucceed();
    }

    // ---- 工具

    private static void assertBait(GameTestHelper helper, ItemStack toolA, ItemStack toolB) {
        helper.assertValueEqual(Fishing.bait(toolA).getCount(), SPECIAL, "甩竿那把工具的饵");
        helper.assertValueEqual(Fishing.bait(toolB).getCount(), FOOD, "换上来那把工具的饵");
    }

    /** 场地中间挖一个 3×3、3 格深的水池，池底和四周铺石头（和 {@link FishingGameTests} 一样）。 */
    private static void pool(GameTestHelper helper) {
        for (BlockPos pos : BlockPos.betweenClosed(1, 0, 1, 5, 0, 5)) {
            helper.setBlock(pos, Blocks.STONE);
        }
        for (int x = 1; x <= 5; x++) {
            for (int z = 1; z <= 5; z++) {
                boolean edge = x == 1 || x == 5 || z == 1 || z == 5;
                for (int y = 1; y <= 3; y++) {
                    helper.setBlock(new BlockPos(x, y, z), edge ? Blocks.STONE : Blocks.WATER);
                }
            }
        }
    }

    /** 站在池边的假玩家：快捷栏第 0 格是装了夜光饵的 A、第 1 格是装了熟鳕鱼的 B，都切在钓鱼模式，拿着 A。 */
    private static ServerPlayer angler(GameTestHelper helper) {
        ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
        Vec3 at = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(3, 4, 1)));
        player.snapTo(at.x, at.y, at.z, 0, 45);
        ItemStack toolA = fishingTool();
        Fishing.setBait(toolA, new ItemStack(UniverseItems.BAITS.get(Bait.GLOW).get(), SPECIAL));
        ItemStack toolB = fishingTool();
        Fishing.setBait(toolB, new ItemStack(Items.COOKED_COD, FOOD));
        Inventory inventory = player.getInventory();
        inventory.setItem(0, toolA);
        inventory.setItem(1, toolB);
        inventory.setSelectedSlot(0);
        helper.assertTrue(Fishing.fishing(toolA) && Fishing.fishing(toolB), "工具没在钓鱼模式");
        return player;
    }

    private static ItemStack fishingTool() {
        ItemStack tool = new ItemStack(UniverseItems.MULTITOOL.get());
        tool.set(UniverseDataComponents.TOOL_MODULES.get(),
                ItemContainerContents.fromItems(List.of(new ItemStack(UniverseItems.WEAPONS.get(ToolWeapon.FISHING_RIG).get()))));
        tool.set(UniverseDataComponents.TOOL_MODE.get(), ToolMode.FISHING);
        return tool;
    }

    /** 甩竿，然后把浮标直接放到水池中间的水面上。 */
    private static FishingBobber castInto(GameTestHelper helper, ServerPlayer player) {
        FishingBobber bobber = Fishing.cast(player);
        Vec3 surface = Vec3.atBottomCenterOf(helper.absolutePos(POOL)).add(0, 0.85, 0);
        bobber.setPos(surface.x, surface.y, surface.z);
        bobber.setDeltaMovement(Vec3.ZERO);
        return bobber;
    }
}
