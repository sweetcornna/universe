package io.github.sweetcornna.universe.gametest;

import java.util.List;

import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.civ.Civ;
import io.github.sweetcornna.universe.core.civ.Race;
import io.github.sweetcornna.universe.core.poi.SettlementLayout;
import io.github.sweetcornna.universe.core.settlement.BuildingType;
import io.github.sweetcornna.universe.core.settlement.Plot;
import io.github.sweetcornna.universe.core.settlement.Settlement;
import io.github.sweetcornna.universe.core.settlement.SettlementRules;
import io.github.sweetcornna.universe.pirate.PirateFighter;
import io.github.sweetcornna.universe.registry.UniverseBlocks;
import io.github.sweetcornna.universe.registry.UniverseItems;
import io.github.sweetcornna.universe.settlement.SettlementData;
import io.github.sweetcornna.universe.settlement.SettlementPayloads;
import io.github.sweetcornna.universe.settlement.SettlementRaids;
import io.github.sweetcornna.universe.settlement.Settlements;
import io.github.sweetcornna.universe.trade.Credits;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * 聚落（ADR-0034 §二）的游戏内测试：接管要声望和建设物资、施工三段一段段盖起来再开张升级、决定生效、海盗来袭总督不在场按防御值算输赢、
 * 来袭的战机在星球上贴着地面扫射又不钻进地里。由 {@link UniverseGameTests} 登记。
 *
 * <p>测试世界里没有聚落地标：聚落立在场地中间（{@link Settlements#testSite}），广场就是场地。地基离中间十几到三十格，
 * 要整平的测试先把周围的区块强制加载。假玩家不在玩家表里，算"不在场"。
 */
final class SettlementGameTests {
    /** 场地中间的地面。 */
    private static final BlockPos CENTER = new BlockPos(3, 0, 3);
    /** 地基都在 ±30 格以内，强制加载这么大一片。 */
    private static final int SITE_RADIUS = 40;
    /** 战机拉开 55 格再绕 35 格的弯回来：扫射的测试要这么大一片空地，场地四周的屏障围不下。 */
    private static final int AIRSPACE_RADIUS = 112;

    private SettlementGameTests() {
    }

    private record Fixture(ServerPlayer player, SettlementData.Entry entry, Settlement s, BlockPos center) {
    }

    /** 立一个聚落交给新的假玩家；{@code prepare} 时照接管的流程整平地基（不收材料）。 */
    private static Fixture claimed(GameTestHelper helper, Race race, boolean prepare, int salt) {
        return claimed(helper, helper.absolutePos(CENTER), race, prepare, salt);
    }

    private static Fixture claimed(GameTestHelper helper, BlockPos center, Race race, boolean prepare, int salt) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        Settlement site = Settlements.testSite(level, center, center.asLong() * 31 + salt, race);
        ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
        player.setPos(center.getX() + 0.5, center.getY() + 1, center.getZ() + 0.5);
        if (prepare) {
            chunks(helper, center, true);
            Component problem = Settlements.claim(player, level, site, true);
            helper.assertTrue(problem == null, "接管不了：" + (problem == null ? "" : problem.getString()));
        } else {
            Settlements.register(server, site);
            site.claim(player.getUUID(), "test", server.overworld().getGameTime());
        }
        SettlementData.Entry entry = SettlementData.get(server).entry(site.id()).orElseThrow();
        return new Fixture(player, entry, entry.settlement(), center);
    }

    private static void cleanup(GameTestHelper helper, Fixture f) {
        cleanup(helper, f, SITE_RADIUS);
    }

    private static void cleanup(GameTestHelper helper, Fixture f, int radius) {
        Settlements.forget(helper.getLevel().getServer(), f.s().id());
        chunks(helper, f.center(), false, radius);
    }

    private static void chunks(GameTestHelper helper, BlockPos center, boolean forced) {
        chunks(helper, center, forced, SITE_RADIUS);
    }

    /** {@code center} 周围 {@code radius} 格内的区块强制加载（或者放掉）。 */
    private static void chunks(GameTestHelper helper, BlockPos center, boolean forced, int radius) {
        ServerLevel level = helper.getLevel();
        for (int cx = (center.getX() - radius) >> 4; cx <= (center.getX() + radius) >> 4; cx++) {
            for (int cz = (center.getZ() - radius) >> 4; cz <= (center.getZ() + radius) >> 4; cz++) {
                level.setChunkForced(cx, cz, forced);
            }
        }
    }

    /** 直接把一块地基变成开着的建筑。 */
    private static void built(Settlement s, int index, BuildingType type, int grade) {
        s.plot(index).restore(type, BuildingType.STAGES, grade, false, 0, 0, true, false, java.util.Map.of());
    }

    private static String key(@Nullable Component component) {
        return component != null && component.getContents() instanceof TranslatableContents contents ? contents.getKey() : "";
    }

    private static int count(ServerPlayer player, Item item) {
        int total = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.is(item)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    /** 地基上（地面以上）不是空气的方块有几个。 */
    private static int blocks(ServerLevel level, SettlementData.Entry entry, int plot) {
        int half = SettlementLayout.PLOT_HALF;
        int count = 0;
        for (int bx = -half; bx <= half; bx++) {
            for (int bz = -half; bz <= half; bz++) {
                for (int y = 1; y <= 18; y++) {
                    if (!level.getBlockState(Settlements.testPlotPos(entry, plot, bx, y, bz)).isAir()) {
                        count++;
                    }
                }
            }
        }
        return count;
    }

    // ---- 接管

    /** 和种族的关系不到"相识"接管不了；到了还要交齐建设物资；交齐了成为总督，材料收走，地基整平、铺了边。 */
    static void claimNeedsStandingAndMaterials(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos center = helper.absolutePos(CENTER);
        chunks(helper, center, true);
        Settlement site = Settlements.testSite(level, center, center.asLong() * 31 + 7, Race.TRADER);
        ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
        player.setPos(center.getX() + 0.5, center.getY() + 1, center.getZ() + 0.5);

        helper.assertValueEqual(key(Settlements.claim(player, level, site)), "message.universe.settlement.standing", "陌生人接管的结果");
        Civ.changeStanding(player, Race.TRADER, 20);
        helper.assertValueEqual(key(Settlements.claim(player, level, site)), "message.universe.settlement.claim_materials", "没带材料接管的结果");
        helper.assertTrue(!site.owned(), "没交材料就成了总督");

        player.getInventory().add(new ItemStack(UniverseItems.STEEL.get(), 30));
        player.getInventory().add(new ItemStack(UniverseItems.CARBON_NANOTUBE.get(), 8));
        player.getInventory().add(new ItemStack(UniverseItems.CIRCUIT_BOARD.get(), 8));
        Component problem = Settlements.claim(player, level, site);
        helper.assertTrue(problem == null, "带齐了也接管不了：" + (problem == null ? "" : problem.getString()));
        helper.assertTrue(site.ownedBy(player.getUUID()), "接管以后总督不是这个人");
        helper.assertValueEqual(count(player, UniverseItems.STEEL.get()), 0, "钢没收走");
        helper.assertValueEqual(count(player, UniverseItems.CIRCUIT_BOARD.get()), 0, "电路板没收走");

        SettlementData.Entry entry = SettlementData.get(level.getServer()).entry(site.id()).orElseThrow();
        helper.assertTrue(entry.prepared(), "地基没有整平");
        int half = SettlementLayout.PLOT_HALF;
        for (int i = 0; i < site.plotCount(); i++) {
            BlockPos corner = Settlements.testPlotPos(entry, i, half, 0, half);
            helper.assertTrue(!level.getBlockState(corner).isAir(), "第 " + (i + 1) + " 块地基的边上是空的");
            helper.assertTrue(level.getBlockState(corner.above()).isAir(), "第 " + (i + 1) + " 块地基上没清空");
        }
        helper.assertValueEqual(key(Settlements.claim(player, level, site)), "message.universe.settlement.already_yours", "再接管一次的结果");
        Settlements.forget(level.getServer(), site.id());
        chunks(helper, center, false);
        helper.succeed();
    }

    // ---- 施工

    /** 交一段的材料，跳过等待。 */
    private static void deliver(GameTestHelper helper, Fixture f, int plot) {
        Component notice = Settlements.testAct(f.player(), f.entry(), SettlementPayloads.Action.DELIVER, plot, 0);
        helper.assertTrue(f.s().plot(plot).readyAt() > 0, "交了材料还没开始等：" + (notice == null ? "" : notice.getString()));
        f.s().plot(plot).skipWait(helper.getLevel().getServer().overworld().getGameTime());
    }

    /**
     * 在 1 号地基上盖总督办公室：开工立起施工终端；三段各交材料、等完，每一段地基上的方块都和上一段不一样；盖完开张起作用；
     * 再升一档，外观又变了。
     */
    static void constructionThreeStages(GameTestHelper helper) {
        Fixture f = claimed(helper, Race.SCHOLAR, true, 1);
        ServerLevel level = helper.getLevel();
        ServerPlayer player = f.player();
        for (Item item : List.of(Items.COBBLESTONE, Items.COBBLESTONE, Items.OAK_PLANKS, Items.OAK_PLANKS, Items.GLASS)) {
            player.getInventory().add(new ItemStack(item, 64));
        }
        player.getInventory().add(new ItemStack(UniverseItems.STEEL.get(), 64));
        player.getInventory().add(new ItemStack(UniverseItems.CIRCUIT_BOARD.get(), 32));

        helper.assertValueEqual(key(Settlements.testAct(player, f.entry(), SettlementPayloads.Action.START, 0, BuildingType.HABITATION.ordinal())),
                "message.universe.settlement.office_first", "没有办公室先盖住宅的结果");
        Settlements.testAct(player, f.entry(), SettlementPayloads.Action.START, 0, BuildingType.OFFICE.ordinal());
        helper.assertValueEqual(f.s().plot(0).type(), BuildingType.OFFICE, "1 号地基上的建筑");
        helper.assertTrue(level.getBlockState(Settlements.testTerminal(f.entry(), 0)).is(UniverseBlocks.SETTLEMENT_TERMINAL.get()), "没立施工终端");
        helper.assertValueEqual(key(Settlements.testAct(player, f.entry(), SettlementPayloads.Action.START, 1, BuildingType.OFFICE.ordinal())),
                "message.universe.settlement.only_one", "第二座办公室的结果");
        int[] counts = new int[6];
        counts[0] = blocks(level, f.entry(), 0);
        helper.startSequence()
                .thenExecute(() -> deliver(helper, f, 0))
                .thenExecuteAfter(3, () -> {
                    helper.assertValueEqual(f.s().plot(0).stage(), 1, "第一段等完以后的段数");
                    counts[1] = blocks(level, f.entry(), 0);
                    helper.assertTrue(counts[1] != counts[0], "打完地基看不出变化");
                    deliver(helper, f, 0);
                })
                .thenExecuteAfter(3, () -> {
                    helper.assertValueEqual(f.s().plot(0).stage(), 2, "第二段等完以后的段数");
                    counts[2] = blocks(level, f.entry(), 0);
                    helper.assertTrue(counts[2] != counts[1], "起了框架看不出变化");
                    deliver(helper, f, 0);
                })
                .thenExecuteAfter(3, () -> {
                    Plot plot = f.s().plot(0);
                    helper.assertValueEqual(plot.phase(), Plot.Phase.OPENING, "三段盖完以后");
                    counts[3] = blocks(level, f.entry(), 0);
                    helper.assertTrue(counts[3] != counts[2], "完工看不出变化");
                    helper.assertTrue(!f.s().has(BuildingType.OFFICE), "没开张就起作用了");
                    Settlements.testAct(player, f.entry(), SettlementPayloads.Action.OPEN, 0, 0);
                    helper.assertTrue(f.s().has(BuildingType.OFFICE), "开张了没起作用");
                    counts[4] = blocks(level, f.entry(), 0);
                    Settlements.testAct(player, f.entry(), SettlementPayloads.Action.UPGRADE, 0, 0);
                    helper.assertTrue(f.s().plot(0).upgrading(), "没开始升级");
                    deliver(helper, f, 0);
                })
                .thenExecuteAfter(3, () -> {
                    helper.assertValueEqual(f.s().plot(0).grade(), 1, "升级以后的档次");
                    counts[5] = blocks(level, f.entry(), 0);
                    helper.assertTrue(counts[5] > counts[4], "升了一档外观没多东西：" + counts[4] + " → " + counts[5]);
                    cleanup(helper, f);
                })
                .thenSucceed();
    }

    // ---- 决定

    /** 今天的决定：选项给信用点、扣信用点改幸福、给物品；钱不够的选项选不了，事情还留着。 */
    static void decisionApplies(GameTestHelper helper) {
        Fixture f = claimed(helper, Race.TRADER, false, 2);
        ServerPlayer player = f.player();
        Settlement s = f.s();
        built(s, 0, BuildingType.OFFICE, 0);

        s.forceDecision("peddler");
        long before = Credits.get(player);
        Settlements.testAct(player, f.entry(), SettlementPayloads.Action.DECIDE, -1, 0);
        helper.assertValueEqual(Credits.get(player), before + 400, "货郎给的信用点");
        helper.assertTrue(s.decision().isEmpty(), "定了以后事情还在");

        s.forceDecision("well");
        Credits.spend(player, Credits.get(player));
        helper.assertValueEqual(key(Settlements.testAct(player, f.entry(), SettlementPayloads.Action.DECIDE, -1, 0)),
                "message.universe.settlement.cannot_afford", "没钱打井的结果");
        helper.assertTrue(s.decision().isPresent(), "没选成事情就没了");
        Credits.add(player, 1000);
        double happiness = s.happiness();
        Settlements.testAct(player, f.entry(), SettlementPayloads.Action.DECIDE, -1, 0);
        helper.assertValueEqual(Credits.get(player), 200L, "打井以后剩的信用点");
        helper.assertTrue(Math.abs(s.happiness() - Math.min(100, happiness + 8)) < 1e-6, "打井没加幸福");

        s.forceDecision("archivist");
        Credits.add(player, 600);
        Settlements.testAct(player, f.entry(), SettlementPayloads.Action.DECIDE, -1, 0);
        helper.assertValueEqual(count(player, UniverseItems.NAVIGATION_DATA.get()), 2, "买到的星图（导航数据）");
        helper.assertTrue(s.recentDecisions().contains("archivist"), "最近的决定里没记上");
        cleanup(helper, f);
        helper.succeed();
    }

    // ---- 海盗

    /** 总督不在场：防御高的守住（威胁清零、赏金记在账上），没防御的没守住（两座建筑受损、储备被抢走三成）。 */
    static void raidResolvesByDefense(GameTestHelper helper) {
        Fixture strong = claimed(helper, Race.WARDEN, false, 3);
        for (int i = 0; i < 6; i++) {
            built(strong.s(), i, BuildingType.TURRET, 3);
        }
        built(strong.s(), 6, BuildingType.WATCHTOWER, 3);
        strong.s().debugSet(100, 0, 0);
        Fixture weak = claimed(helper, Race.TRADER, false, 4);
        for (int i = 0; i < 3; i++) {
            built(weak.s(), i, BuildingType.HABITATION, 0);
        }
        weak.s().debugSet(0, 100, 50);
        double reserve = weak.s().reserve();
        long now = helper.getLevel().getServer().overworld().getGameTime();
        strong.s().scheduleRaid(now);
        weak.s().scheduleRaid(now);
        helper.startSequence()
                .thenWaitUntil(() -> {
                    helper.assertTrue(strong.s().raidAt() < 0, "防御高的来袭还没算");
                    helper.assertTrue(weak.s().raidAt() < 0, "没防御的来袭还没算");
                })
                .thenExecute(() -> {
                    helper.assertValueEqual(strong.s().raidsWon(), 1, "防御高的守住次数");
                    helper.assertTrue(strong.s().threat() == 0, "守住了威胁没清零");
                    helper.assertTrue(strong.s().treasury() >= SettlementRules.raidReward(3), "总督不在场，赏金没记在账上");
                    helper.assertValueEqual(weak.s().raidsLost(), 1, "没防御的失守次数");
                    int damaged = 0;
                    for (int i = 0; i < weak.s().plotCount(); i++) {
                        damaged += weak.s().plot(i).damaged() ? 1 : 0;
                    }
                    helper.assertValueEqual(damaged, 2, "受损的建筑");
                    helper.assertTrue(Math.abs(weak.s().reserve() - reserve * 0.7) < 0.5, "储备没被抢走三成：" + reserve + " → " + weak.s().reserve());
                    cleanup(helper, strong);
                    cleanup(helper, weak);
                })
                .thenSucceed();
    }

    /**
     * 真打：两架战机从侧上方飞来扫射广场，一直飞在地面两格以上，开了炮；全部打下来以后算守住。
     * 聚落立在测试场地往北 3000 格的平地上：场地四周有屏障，战机拉开、掉头时会撞上去来回弹，半天对不准。
     */
    static void raidersStrafeAboveGround(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos far = helper.absolutePos(CENTER).offset(0, 0, -3000);
        level.getChunk(far.getX() >> 4, far.getZ() >> 4);
        BlockPos site = new BlockPos(far.getX(), level.getHeight(Heightmap.Types.MOTION_BLOCKING, far.getX(), far.getZ()) - 1, far.getZ());
        chunks(helper, site, true, AIRSPACE_RADIUS);
        Fixture f = claimed(helper, site, Race.WARDEN, false, 5);
        List<PirateFighter> fighters = SettlementRaids.testRaid(level, f.entry(), 2, Vec3.atCenterOf(f.center()).add(36, 24, 0));
        helper.assertValueEqual(fighters.size(), 2, "放出来的战机");
        double[] lowest = {Double.MAX_VALUE};
        helper.onEachTick(() -> {
            for (PirateFighter fighter : fighters) {
                if (!fighter.isRemoved()) {
                    double ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(fighter.getX()), Mth.floor(fighter.getZ()));
                    lowest[0] = Math.min(lowest[0], fighter.getY() - ground);
                }
            }
        });
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(fighters.stream().anyMatch(fighter -> fighter.shots() > 0), "战机没开炮"))
                .thenExecute(() -> {
                    helper.assertTrue(fighters.stream().allMatch(PirateFighter::strafing), "战机没在扫射");
                    for (PirateFighter fighter : fighters) {
                        for (int k = 0; k < 40 && !fighter.isRemoved(); k++) {
                            fighter.hurtServer(level, level.damageSources().generic(), 1000);
                        }
                    }
                })
                .thenWaitUntil(() -> helper.assertValueEqual(f.s().raidsWon(), 1, "全部打下来以后的守住次数"))
                .thenExecute(() -> {
                    helper.assertTrue(lowest[0] >= 1.9, "战机钻到离地不到两格：" + lowest[0]);
                    cleanup(helper, f, AIRSPACE_RADIUS);
                })
                .thenSucceed();
    }
}
