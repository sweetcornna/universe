package io.github.sweetcornna.universe.gametest;

import java.util.List;

import io.github.sweetcornna.universe.core.fishing.Bait;
import io.github.sweetcornna.universe.core.fishing.BiteTimer;
import io.github.sweetcornna.universe.core.fishing.Catch;
import io.github.sweetcornna.universe.core.fishing.CatchRoll;
import io.github.sweetcornna.universe.core.fishing.FishSpecies;
import io.github.sweetcornna.universe.core.fishing.FishTable;
import io.github.sweetcornna.universe.core.fishing.FishingLog;
import io.github.sweetcornna.universe.core.fishing.Habitat;
import io.github.sweetcornna.universe.core.fishing.Reeling;
import io.github.sweetcornna.universe.core.survival.ToolMode;
import io.github.sweetcornna.universe.core.survival.ToolWeapon;
import io.github.sweetcornna.universe.fishing.FishTrapBlock;
import io.github.sweetcornna.universe.fishing.FishTrapBlockEntity;
import io.github.sweetcornna.universe.fishing.Fishing;
import io.github.sweetcornna.universe.fishing.FishingBobber;
import io.github.sweetcornna.universe.registry.UniverseBlocks;
import io.github.sweetcornna.universe.registry.UniverseDataComponents;
import io.github.sweetcornna.universe.registry.UniverseItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.clock.WorldClock;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * 钓鱼（ADR-0034 §一）的游戏内测试：水域和条件、收线钓上来记进图鉴、钩住时饵用掉、放生的加成、捕鱼笼出鱼。由
 * {@link UniverseGameTests} 登记。
 *
 * <p>钓上来的东西用 {@link FishingBobber#forceBite} 直接定下，不等鱼；假玩家不在世界里，按、松右键由测试每刻替它做。
 */
final class FishingGameTests {
    /** 场地中间挖一个 3×3、3 格深的水池，浮标放在中间那一格的水面上。 */
    private static final BlockPos POOL = new BlockPos(3, 3, 3);

    private FishingGameTests() {
    }

    /**
     * 水域和条件：场地里的水看不到天，算暗河，水深按往下数的水格算；白天、半夜分得清；条件不对的鱼不咬钩（白天的夜鱼、
     * 不下风暴的风暴鱼权重是 0），夜光饵让夜鱼白天也咬。聚落用的鱼塘出得了东西。
     */
    static void conditionsAndHabitat(GameTestHelper helper) {
        pool(helper);
        // 水池上面盖一层石头：头顶看不到天，算暗河（光照更新要等一两刻）
        for (int x = 2; x <= 4; x++) {
            for (int z = 2; z <= 4; z++) {
                helper.setBlock(new BlockPos(x, 4, z), Blocks.STONE);
            }
        }
        helper.startSequence().thenExecuteAfter(5, () -> conditions(helper)).thenSucceed();
    }

    private static void conditions(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos surface = helper.absolutePos(POOL);
        Fishing.Water water = Fishing.water(level, surface).orElse(null);
        helper.assertTrue(water != null, "水池里的水不算能钓鱼的水");
        helper.assertValueEqual(water.habitat(), Habitat.CAVE, "看不到天的水的水域");
        helper.assertValueEqual(water.depth(), 3, "水深");
        helper.assertTrue(Fishing.water(level, helper.absolutePos(new BlockPos(0, 3, 0))).isEmpty(), "空气也算水");

        setTime(helper, 6000);
        helper.assertTrue(Fishing.situation(level, surface, 3).day(), "正午不算白天");
        setTime(helper, 18000);
        CatchRoll.Situation night = Fishing.situation(level, surface, 3);
        helper.assertTrue(!night.day(), "半夜算白天");

        FishSpecies nightFish = FishTable.of(Habitat.CAVE).stream().filter(s -> s.time() == FishSpecies.Time.NIGHT).findFirst().orElseThrow();
        FishSpecies stormFish = FishTable.of(Habitat.CAVE).stream().filter(s -> s.weather() == FishSpecies.Weather.STORM).findFirst().orElseThrow();
        CatchRoll.Situation noon = new CatchRoll.Situation(true, false, 3);
        helper.assertTrue(CatchRoll.weight(nightFish, noon, Bait.NONE, false, CatchRoll.Odds.ROD) == 0, "白天钓得到夜里的鱼");
        helper.assertTrue(CatchRoll.weight(nightFish, noon, Bait.GLOW, false, CatchRoll.Odds.ROD) > 0, "夜光饵白天钓不到夜里的鱼");
        helper.assertTrue(CatchRoll.weight(nightFish, night, Bait.NONE, false, CatchRoll.Odds.ROD) > 0, "半夜钓不到夜里的鱼");
        helper.assertTrue(CatchRoll.weight(stormFish, noon, Bait.NONE, false, CatchRoll.Odds.ROD) == 0, "不下风暴钓得到风暴里的鱼");

        for (int grade = 0; grade <= 3; grade++) {
            helper.assertTrue(!Fishing.pondCatch(level, surface, level.getRandom(), grade).isEmpty(), "鱼塘 " + grade + " 级什么也没出");
        }
    }

    /** 收线：钩住一条普通的小鱼，照张力按、松右键，几秒以后钓上来，进了背包，图鉴记上一条、记下多重和在哪钓的。 */
    static void reelingLandsFish(GameTestHelper helper) {
        pool(helper);
        ServerPlayer player = angler(helper);
        FishSpecies species = FishTable.byId("pale_cave_barb").orElseThrow();
        FishingBobber bobber = castInto(helper, player);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertValueEqual(bobber.phase(), FishingBobber.Phase.FLOATING, "浮标没浮在水上"))
                .thenExecute(() -> {
                    bobber.forceBite(Catch.fish(species, 0.4));
                    // 和平难度咬钩就钓上来，别的难度要钩住再收线
                    if (!bobber.isRemoved()) {
                        helper.assertValueEqual(bobber.phase(), FishingBobber.Phase.BITE, "没进入咬钩");
                        Fishing.use(player.level(), player, InteractionHand.MAIN_HAND);
                    }
                    helper.onEachTick(() -> steer(player, bobber));
                })
                .thenWaitUntil(() -> helper.assertTrue(Fishing.log(player).caught(species), "图鉴里没记上"))
                .thenExecute(() -> {
                    FishingLog.Entry entry = Fishing.log(player).entry(species).orElseThrow();
                    helper.assertValueEqual(entry.count(), 1, "图鉴记的条数");
                    helper.assertTrue(Math.abs(entry.heaviest() - 0.4) < 1e-6, "图鉴记的重量 " + entry.heaviest());
                    helper.assertTrue(!entry.planetEn().isEmpty(), "图鉴没记在哪钓的");
                    helper.assertTrue(player.getInventory().contains(new ItemStack(UniverseItems.FISH.get(species).get())), "鱼没进背包");
                    helper.assertTrue(bobber.isRemoved(), "钓上来以后浮标还在");
                })
                .thenSucceed();
    }

    /** 饵：甩竿时记下用的是腥饵，等鱼的时间比不放饵短；钩住时用掉一个。 */
    static void baitUsedOnHook(GameTestHelper helper) {
        pool(helper);
        ServerPlayer player = angler(helper);
        Fishing.setBait(player.getMainHandItem(), new ItemStack(UniverseItems.BAITS.get(Bait.STINK).get(), 2));
        FishingBobber bobber = castInto(helper, player);
        helper.assertValueEqual(bobber.bait(), Bait.STINK, "这一竿用的饵");
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertValueEqual(bobber.phase(), FishingBobber.Phase.FLOATING, "浮标没浮在水上"))
                .thenExecute(() -> {
                    helper.assertTrue(bobber.waitLeft() <= BiteTimer.MAX_WAIT * Bait.STINK.waitFactor(), "腥饵没缩短等鱼的时间：" + bobber.waitLeft());
                    // 钩住一团藻：不是鱼，直接拉上来，饵照样用掉
                    bobber.forceBite(Catch.junk(io.github.sweetcornna.universe.core.fishing.Junk.TANGLED_WEED));
                    if (!bobber.isRemoved()) {
                        Fishing.use(player.level(), player, InteractionHand.MAIN_HAND);
                    }
                })
                .thenWaitUntil(() -> helper.assertTrue(bobber.isRemoved(), "拉上来以后浮标还在"))
                .thenExecute(() -> {
                    helper.assertValueEqual(Fishing.bait(player.getMainHandItem()).getCount(), 1, "饵格里剩下的饵");
                    helper.assertTrue(player.getInventory().contains(new ItemStack(UniverseItems.JUNK
                            .get(io.github.sweetcornna.universe.core.fishing.Junk.TANGLED_WEED).get())), "藻团没进背包");
                })
                .thenSucceed();
    }

    /** 放生：鱼回到水里，图鉴记一条放生；接下来三竿有加成，甩一竿少一次，等鱼的时间减半。 */
    static void releaseBlessesNextCasts(GameTestHelper helper) {
        pool(helper);
        ServerPlayer player = angler(helper);
        FishSpecies species = FishTable.byId("blind_loach").orElseThrow();
        ItemStack fish = new ItemStack(UniverseItems.FISH.get(species).get(), 2);
        helper.assertTrue(!Fishing.release(player, fish, helper.absolutePos(new BlockPos(0, 3, 0))), "对着空气也放生了");
        helper.assertTrue(Fishing.release(player, fish, helper.absolutePos(POOL)), "对着水放不了生");
        helper.assertValueEqual(fish.getCount(), 1, "放生以后手里剩下的鱼");
        FishingLog log = Fishing.log(player);
        helper.assertValueEqual(log.released(), 1, "图鉴记的放生条数");
        helper.assertValueEqual(log.releaseCasts(), BiteTimer.RELEASE_CASTS, "放生以后有加成的甩竿次数");
        FishingBobber bobber = castInto(helper, player);
        helper.assertTrue(bobber.blessed(), "放生以后的第一竿没有加成");
        helper.assertValueEqual(Fishing.log(player).releaseCasts(), BiteTimer.RELEASE_CASTS - 1, "甩了一竿以后剩下的加成");
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertValueEqual(bobber.phase(), FishingBobber.Phase.FLOATING, "浮标没浮在水上"))
                .thenExecute(() -> helper.assertTrue(bobber.waitLeft() <= Math.round(BiteTimer.MAX_WAIT * BiteTimer.RELEASE_WAIT),
                        "放生的加成没缩短等鱼的时间：" + bobber.waitLeft()))
                .thenSucceed();
    }

    /** 捕鱼笼：泡在水里的出鱼（或杂物），露在外面的不干活。 */
    static void trapCatchesUnderWater(GameTestHelper helper) {
        BlockPos wet = new BlockPos(1, 1, 1);
        BlockPos dry = new BlockPos(5, 1, 5);
        helper.setBlock(wet, UniverseBlocks.FISH_TRAP.get().defaultBlockState().setValue(FishTrapBlock.WATERLOGGED, true));
        helper.setBlock(wet.above(), Blocks.WATER);
        helper.setBlock(dry, UniverseBlocks.FISH_TRAP.get());
        FishTrapBlockEntity trap = helper.getBlockEntity(wet, FishTrapBlockEntity.class);
        FishTrapBlockEntity dryTrap = helper.getBlockEntity(dry, FishTrapBlockEntity.class);
        trap.setProgress(FishTrapBlockEntity.CATCH_TICKS - 20);
        dryTrap.setProgress(FishTrapBlockEntity.CATCH_TICKS - 20);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(!outputs(trap).isEmpty(), "泡在水里的捕鱼笼没出东西"))
                .thenExecute(() -> {
                    helper.assertValueEqual(trap.status(), FishTrapBlockEntity.Status.WORKING, "泡在水里的捕鱼笼的状态");
                    helper.assertValueEqual(dryTrap.status(), FishTrapBlockEntity.Status.DRY, "露在外面的捕鱼笼的状态");
                    helper.assertTrue(outputs(dryTrap).isEmpty(), "露在外面的捕鱼笼也出东西");
                })
                .thenSucceed();
    }

    // ---- 工具

    /** 场地中间挖一个 3×3、3 格深的水池。场地的地板在测试世界里放不出来（脚下只有屏障），池底自己铺。 */
    private static void pool(GameTestHelper helper) {
        for (BlockPos pos : BlockPos.betweenClosed(1, 0, 1, 5, 0, 5)) {
            helper.setBlock(pos, Blocks.STONE);
        }
        for (int x = 2; x <= 4; x++) {
            for (int z = 2; z <= 4; z++) {
                for (int y = 1; y <= 3; y++) {
                    helper.setBlock(new BlockPos(x, y, z), Blocks.WATER);
                }
            }
        }
        // 四周围一圈石头，水不往外流
        for (int x = 1; x <= 5; x++) {
            for (int z = 1; z <= 5; z++) {
                if (x == 1 || x == 5 || z == 1 || z == 5) {
                    for (int y = 1; y <= 3; y++) {
                        helper.setBlock(new BlockPos(x, y, z), Blocks.STONE);
                    }
                }
            }
        }
    }

    /** 站在池边、拿着装了钓鱼器、切到钓鱼模式的多功能工具的假玩家。 */
    private static ServerPlayer angler(GameTestHelper helper) {
        ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
        Vec3 at = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(3, 4, 1)));
        player.snapTo(at.x, at.y, at.z, 0, 45);
        ItemStack tool = new ItemStack(UniverseItems.MULTITOOL.get());
        tool.set(UniverseDataComponents.TOOL_MODULES.get(),
                ItemContainerContents.fromItems(List.of(new ItemStack(UniverseItems.WEAPONS.get(ToolWeapon.FISHING_RIG).get()))));
        tool.set(UniverseDataComponents.TOOL_MODE.get(), ToolMode.FISHING);
        player.setItemInHand(InteractionHand.MAIN_HAND, tool);
        helper.assertTrue(Fishing.fishing(player.getMainHandItem()), "工具没在钓鱼模式");
        return player;
    }

    /** 甩竿，然后把浮标直接放到水池中间的水面上。 */
    private static FishingBobber castInto(GameTestHelper helper, ServerPlayer player) {
        FishingBobber bobber = Fishing.cast(player);
        Vec3 surface = Vec3.atBottomCenterOf(helper.absolutePos(POOL)).add(0, 0.85, 0);
        bobber.setPos(surface.x, surface.y, surface.z);
        bobber.setDeltaMovement(Vec3.ZERO);
        return bobber;
    }

    /** 照着张力按、松右键：张力低就按着，快到红区、鱼要挣或者在挣就松开。 */
    private static void steer(ServerPlayer player, FishingBobber bobber) {
        Reeling reeling = bobber.reeling();
        if (bobber.isRemoved() || reeling == null) {
            return;
        }
        boolean release = reeling.dashing() || reeling.warning() || reeling.tension() > 0.68;
        if (release && player.isUsingItem()) {
            player.stopUsingItem();
        } else if (!release && reeling.tension() < 0.4 && !player.isUsingItem()) {
            player.startUsingItem(InteractionHand.MAIN_HAND);
        }
    }

    private static List<ItemStack> outputs(FishTrapBlockEntity trap) {
        return java.util.stream.IntStream.range(1, FishTrapBlockEntity.SLOTS).mapToObj(trap::getItem).filter(stack -> !stack.isEmpty()).toList();
    }

    private static void setTime(GameTestHelper helper, long ticks) {
        ServerLevel level = helper.getLevel();
        Holder<WorldClock> clock = level.dimensionTypeRegistration().value().defaultClock().orElse(null);
        helper.assertTrue(clock != null, "这个维度没有默认时钟");
        level.clockManager().setTotalTicks(clock, ticks);
    }
}
