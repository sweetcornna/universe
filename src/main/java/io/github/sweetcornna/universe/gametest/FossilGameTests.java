package io.github.sweetcornna.universe.gametest;

import java.util.List;

import io.github.sweetcornna.universe.core.fossil.Bone;
import io.github.sweetcornna.universe.core.fossil.BoneFamily;
import io.github.sweetcornna.universe.core.fossil.BonePart;
import io.github.sweetcornna.universe.core.fossil.CollectorTrade;
import io.github.sweetcornna.universe.core.fossil.Skeleton;
import io.github.sweetcornna.universe.core.fossil.StoneSentryRules;
import io.github.sweetcornna.universe.fossil.CollectorMenu;
import io.github.sweetcornna.universe.fossil.DormantSentryBlock;
import io.github.sweetcornna.universe.fossil.FossilExhibitItem;
import io.github.sweetcornna.universe.fossil.SkeletonDisplayBlockEntity;
import io.github.sweetcornna.universe.fossil.SkeletonDisplayMenu;
import io.github.sweetcornna.universe.fossil.StoneSentry;
import io.github.sweetcornna.universe.registry.UniverseBlocks;
import io.github.sweetcornna.universe.registry.UniverseEntities;
import io.github.sweetcornna.universe.registry.UniverseItems;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;

/**
 * 化石（ADR-0034 §三）的游戏内测试：骨架展台拼齐、封装、摆回去，收藏家三换一，挖化石土吵醒石哨兵。由 {@link UniverseGameTests} 登记。
 *
 * <p>假玩家用不进世界的 {@code makeMockPlayer}：服务端发给它的提示（{@code sendOverlayMessage}）什么也不做，不会因为没有连接出错。
 */
final class FossilGameTests {
    private static final BlockPos DISPLAY = new BlockPos(2, 1, 2);
    private static final BlockPos SENTRY = new BlockPos(1, 1, 1);

    private FossilGameTests() {
    }

    private static ItemStack bone(BoneFamily family, BonePart part) {
        return new ItemStack(UniverseItems.BONES.get(new Bone(family, part)).get());
    }

    private static int count(Player player, Bone bone) {
        int total = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.is(UniverseItems.BONES.get(bone).get())) {
                total += stack.getCount();
            }
        }
        return total;
    }

    private static ItemStack find(Player player, Item item) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (player.getInventory().getItem(i).is(item)) {
                return player.getInventory().getItem(i);
            }
        }
        return ItemStack.EMPTY;
    }

    /**
     * 骨架展台：手拿骨头右键一块块放上去，同一格放不了第二块；五格齐了是一副完整的骨架，名字是胸廓的类群加别的类群的修饰
     * （掠食兽的身子配长颈兽的脖子：长颈掠食兽）。界面里按"封装"变成一件展品进背包，展台清空；拿展品右键空展台，原样摆回去。
     */
    static void displayCompletesPacksAndRestores(GameTestHelper helper) {
        helper.setBlock(DISPLAY, UniverseBlocks.SKELETON_DISPLAY.get());
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        SkeletonDisplayBlockEntity display = helper.getBlockEntity(DISPLAY, SkeletonDisplayBlockEntity.class);

        List<ItemStack> bones = List.of(bone(BoneFamily.PREDATOR, BonePart.SKULL), bone(BoneFamily.LONGNECK, BonePart.NECK),
                bone(BoneFamily.PREDATOR, BonePart.RIBCAGE), bone(BoneFamily.PREDATOR, BonePart.LIMBS));
        for (ItemStack stack : bones) {
            player.setItemInHand(InteractionHand.MAIN_HAND, stack);
            helper.useBlock(DISPLAY, player);
            helper.assertTrue(player.getMainHandItem().isEmpty(), "骨头没有放上去：" + stack);
        }
        helper.assertValueEqual(display.skeleton().count(), 4, "放了四块以后的块数");
        helper.assertTrue(!display.skeleton().complete(), "四块就算完整了");

        // 同一个部位放不了第二块
        player.setItemInHand(InteractionHand.MAIN_HAND, bone(BoneFamily.TITAN, BonePart.SKULL));
        helper.useBlock(DISPLAY, player);
        helper.assertValueEqual(player.getMainHandItem().getCount(), 1, "头骨格已经有了还收");
        helper.assertValueEqual(display.skeleton().bone(BonePart.SKULL), new Bone(BoneFamily.PREDATOR, BonePart.SKULL), "头骨被换掉了");

        player.setItemInHand(InteractionHand.MAIN_HAND, bone(BoneFamily.PREDATOR, BonePart.TAIL));
        helper.useBlock(DISPLAY, player);
        Skeleton skeleton = display.skeleton();
        helper.assertTrue(skeleton.complete(), "五块齐了不算完整");
        helper.assertTrue(!skeleton.pure(), "混了长颈兽的脖子还算同一类群");
        Skeleton.Name name = skeleton.name();
        helper.assertValueEqual(name.noun(), BoneFamily.PREDATOR, "名字里的名词（胸廓的类群）");
        helper.assertValueEqual(name.modifiers(), List.of(BoneFamily.LONGNECK), "名字里的修饰");

        // 封装
        player.getInventory().clearContent();
        SkeletonDisplayMenu menu = new SkeletonDisplayMenu(1, player.getInventory(), display);
        helper.assertTrue(menu.clickMenuButton(player, SkeletonDisplayMenu.PACK), "封装按钮没反应");
        helper.assertTrue(display.skeleton().isEmpty(), "封装以后展台没清空");
        ItemStack exhibit = find(player, UniverseItems.FOSSIL_EXHIBIT.get()).copy();
        helper.assertTrue(!exhibit.isEmpty(), "封装以后背包里没有展品");
        helper.assertValueEqual(FossilExhibitItem.skeleton(exhibit), skeleton, "展品里存的骨架");
        helper.assertTrue(exhibit.getMaxStackSize() > 1, "展品不能堆叠");
        helper.assertTrue(CollectorTrade.exhibitPrice(skeleton) > 0, "完整的展品不值钱");
        // 空展台再按封装什么也不给
        menu.clickMenuButton(player, SkeletonDisplayMenu.PACK);
        helper.assertValueEqual(find(player, UniverseItems.FOSSIL_EXHIBIT.get()).getCount(), 1, "空展台封装出了东西");

        // 摆回去
        player.getInventory().clearContent();
        player.setItemInHand(InteractionHand.MAIN_HAND, exhibit);
        helper.useBlock(DISPLAY, player);
        helper.assertValueEqual(display.skeleton(), skeleton, "摆回去的骨架");
        helper.assertTrue(player.getMainHandItem().isEmpty(), "摆回去以后展品没用掉");

        // 有骨头的展台不收展品
        player.setItemInHand(InteractionHand.MAIN_HAND, FossilExhibitItem.of(skeleton));
        helper.useBlock(DISPLAY, player);
        helper.assertValueEqual(player.getMainHandItem().getCount(), 1, "有骨头的展台收了展品");
        helper.succeed();
    }

    /**
     * 收藏家换骨头：不够三块换不了；交上去的都是普通的换不到极稀有的奇异种；三块普通的换一块普通的长颈兽四肢，
     * 三格清空，换来的骨头进背包。
     */
    static void collectorSwap(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        CollectorMenu menu = new CollectorMenu(1, player.getInventory(), (Entity) null);
        int parts = BonePart.values().length;
        Bone wanted = new Bone(BoneFamily.LONGNECK, BonePart.LIMBS);
        menu.clickMenuButton(player, CollectorMenu.PICK + wanted.family().ordinal() * parts + wanted.part().ordinal());
        helper.assertValueEqual(menu.wanted(), wanted, "选中的骨头");

        menu.getSlot(1).set(bone(BoneFamily.TITAN, BonePart.SKULL));
        menu.getSlot(2).set(bone(BoneFamily.PREDATOR, BonePart.NECK));
        helper.assertValueEqual(menu.swapCheck(), CollectorTrade.Swap.NEED_THREE, "两块的检查结果");
        menu.clickMenuButton(player, CollectorMenu.SWAP);
        helper.assertValueEqual(count(player, wanted), 0, "两块也换出来了");
        menu.getSlot(3).set(bone(BoneFamily.LONGNECK, BonePart.TAIL));

        // 要极稀有的奇异种：交的都是普通的，不行
        Bone rare = new Bone(BoneFamily.EXOTIC, BonePart.SKULL);
        menu.clickMenuButton(player, CollectorMenu.PICK + rare.family().ordinal() * parts + rare.part().ordinal());
        helper.assertValueEqual(menu.swapCheck(), CollectorTrade.Swap.TOO_RARE, "普通换极稀有的检查结果");
        menu.clickMenuButton(player, CollectorMenu.SWAP);
        helper.assertValueEqual(count(player, rare), 0, "普通的骨头换到了奇异种");
        helper.assertTrue(menu.getSlot(1).hasItem() && menu.getSlot(2).hasItem() && menu.getSlot(3).hasItem(), "换不成也收走了骨头");

        // 换回想要的
        menu.clickMenuButton(player, CollectorMenu.PICK + wanted.family().ordinal() * parts + wanted.part().ordinal());
        helper.assertValueEqual(menu.swapCheck(), CollectorTrade.Swap.OK, "三块普通换普通的检查结果");
        menu.clickMenuButton(player, CollectorMenu.SWAP);
        helper.assertValueEqual(count(player, wanted), 1, "换来的骨头");
        helper.assertTrue(!menu.getSlot(1).hasItem() && !menu.getSlot(2).hasItem() && !menu.getSlot(3).hasItem(), "交上去的骨头没收走");
        helper.succeed();
    }

    /**
     * 挖化石土吵醒石哨兵：生存模式的玩家每挖开一块化石土，附近沉睡的石哨兵躁动一级（方块状态里看得到），第三块挖开时醒来，
     * 方块变成飞起来的石哨兵，盯上挖土的人。
     */
    static void digWakesSentry(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        helper.setBlock(SENTRY, UniverseBlocks.DORMANT_STONE_SENTRY.get());
        List<BlockPos> dirt = List.of(new BlockPos(4, 1, 4), new BlockPos(5, 1, 4), new BlockPos(4, 1, 5));
        for (BlockPos pos : dirt) {
            helper.setBlock(pos, UniverseBlocks.FOSSIL_DIRT.get());
        }
        for (int i = 0; i < dirt.size(); i++) {
            level.destroyBlock(helper.absolutePos(dirt.get(i)), true, player);
            if (i < StoneSentryRules.WAKE_DIGS - 1) {
                helper.assertBlockProperty(SENTRY, DormantSentryBlock.UNREST, i + 1);
            }
        }
        helper.assertBlockNotPresent(UniverseBlocks.DORMANT_STONE_SENTRY.get(), SENTRY);
        AABB around = new AABB(helper.absolutePos(SENTRY)).inflate(4);
        List<StoneSentry> sentries = level.getEntitiesOfClass(StoneSentry.class, around);
        helper.assertValueEqual(sentries.size(), 1, "醒来的石哨兵个数");
        StoneSentry sentry = sentries.getFirst();
        helper.assertValueEqual(sentry.getType(), UniverseEntities.STONE_SENTRY.get(), "醒来的实体");
        helper.assertTrue(sentry.rising() > 0, "刚醒的石哨兵没有在往上浮");
        helper.assertValueEqual(sentry.getTarget(), player, "石哨兵盯上的人");
        sentry.discard();
        helper.succeed();
    }
}
