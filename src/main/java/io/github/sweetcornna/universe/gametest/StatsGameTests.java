package io.github.sweetcornna.universe.gametest;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import com.mojang.serialization.JsonOps;

import io.github.sweetcornna.universe.core.galaxy.Galaxy;
import io.github.sweetcornna.universe.core.galaxy.Planet;
import io.github.sweetcornna.universe.core.galaxy.StarSystem;
import io.github.sweetcornna.universe.core.logistics.Location;
import io.github.sweetcornna.universe.core.logistics.Logic;
import io.github.sweetcornna.universe.core.logistics.LogisticsNetwork;
import io.github.sweetcornna.universe.core.logistics.Station;
import io.github.sweetcornna.universe.core.logistics.StationKind;
import io.github.sweetcornna.universe.core.logistics.StationSlot;
import io.github.sweetcornna.universe.core.logistics.Trip;
import io.github.sweetcornna.universe.core.logistics.Vehicle;
import io.github.sweetcornna.universe.core.research.ResearchState;
import io.github.sweetcornna.universe.core.research.Tech;
import io.github.sweetcornna.universe.core.research.TechTree;
import io.github.sweetcornna.universe.core.stats.Dashboard;
import io.github.sweetcornna.universe.core.stats.ProductionStats;
import io.github.sweetcornna.universe.core.stats.ReferenceRates;
import io.github.sweetcornna.universe.factory.RecipeMachineBlockEntity;
import io.github.sweetcornna.universe.factory.TallMachineBlock;
import io.github.sweetcornna.universe.factory.power.FuelGeneratorBlockEntity;
import io.github.sweetcornna.universe.factory.recipe.MachineRecipe;
import io.github.sweetcornna.universe.factory.research.Research;
import io.github.sweetcornna.universe.factory.research.ResearchData;
import io.github.sweetcornna.universe.logistics.Logistics;
import io.github.sweetcornna.universe.network.DashboardEditPayload;
import io.github.sweetcornna.universe.registry.UniverseAttachments;
import io.github.sweetcornna.universe.registry.UniverseBlocks;
import io.github.sweetcornna.universe.registry.UniverseItems;
import io.github.sweetcornna.universe.stats.DashboardPins;
import io.github.sweetcornna.universe.stats.Dashboards;
import io.github.sweetcornna.universe.stats.ProductionData;
import io.github.sweetcornna.universe.stats.ProductionLog;
import io.github.sweetcornna.universe.world.planet.Planets;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/**
 * 统计和研究队列（ADR-0033 §四、§五）的游戏内测试：研究队列在当前研究完成后自动接下一项、前置没好的跳过；参考产量按正在
 * 运转的设备算，停了就不算；星际运输船卸货记进进出口；仪表盘钉图、去重、存档来回。由 {@link UniverseGameTests} 登记。
 */
final class StatsGameTests {
    private static final String GEAR_RECIPE = "universe:assembling/gear";

    private StatsGameTests() {
    }

    // ---- 研究队列

    /**
     * 正在研究 C，队里排着 [B, A]，B 要 A 当前置。C 研究完时 B 的前置还没好，跳过它先研究 A，B 留在队里；停下来时 B 还是研究不了，
     * 不开始；再研究 A，A 研究完接着研究 B。研究进度全服共用，这个测试在一刻里做完，做完把进度恢复原样。
     */
    static void researchQueueAdvances(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ResearchState state = ResearchData.get(server).state();
        ResearchState saved = state.copy();
        try {
            state.clear();
            TechTree tree = Research.TREE;
            // B 和它的一个前置 A 都要研究站，挑点数最少的一对
            Tech b = null;
            Tech a = null;
            for (Tech tech : tree.techs()) {
                if (tech.isHandIn()) {
                    continue;
                }
                for (String required : tech.requires()) {
                    Tech parent = tree.get(required).orElse(null);
                    if (parent != null && !parent.isHandIn() && (b == null || tech.points() + parent.points() < b.points() + a.points())) {
                        b = tech;
                        a = parent;
                    }
                }
            }
            helper.assertTrue(b != null && a != null, "科技树里找不到前置也要研究站的科技");
            completeRequirements(state, a.id());
            for (String required : b.requires()) {
                if (!required.equals(a.id()) && !state.isDone(required)) {
                    completeRequirements(state, required);
                    state.complete(required);
                }
            }
            String first = a.id();
            String second = b.id();
            // C：现在就能研究的、点数最少的另一个
            Tech c = tree.techs().stream()
                    .filter(tech -> !tech.isHandIn() && !tech.id().equals(first) && !tech.id().equals(second) && state.available(tree, tech.id()))
                    .min(Comparator.comparingInt(Tech::points)).orElse(null);
            helper.assertTrue(c != null, "没有别的能研究的科技");
            helper.assertTrue(state.start(tree, c.id()), "开始不了 " + c.id());
            int limit = Research.queueLimit(state);
            helper.assertTrue(!state.enqueue(tree, second, limit), "前置没完成也没排队，就排进去了");
            helper.assertTrue(state.enqueue(tree, first, limit), "排不进 " + first);
            helper.assertTrue(state.enqueue(tree, second, limit), "前置排在队里，还是排不进 " + second);
            helper.assertTrue(!state.enqueue(tree, first, limit), "同一个科技排了两次");
            helper.assertTrue(state.move(1, -1), "挪不动");
            helper.assertValueEqual(state.queue(), List.of(second, first), "队列");

            finish(server, state, c.id());
            helper.assertTrue(first.equals(state.current()), "C 完成后在研究的是 " + state.current());
            helper.assertValueEqual(state.queue(), List.of(second), "前置没好的那项没留在队里");

            // 停下来：队里只剩前置没好的，不开始
            state.stop();
            state.advance(tree);
            helper.assertTrue(state.current() == null, "停下以后开始了前置没好的 " + state.current());
            helper.assertValueEqual(state.queue(), List.of(second), "停下以后的队列");

            helper.assertTrue(state.start(tree, first), "开始不了 " + first);
            finish(server, state, first);
            helper.assertTrue(second.equals(state.current()), "A 完成后在研究的是 " + state.current());
            helper.assertTrue(state.queue().isEmpty(), "队列没清空");
        } finally {
            state.copyFrom(saved);
            Research.changed(server);
        }
        helper.succeed();
    }

    /** 研究站一点一点攒，直到这个科技完成（走服务端的入口，和研究站一样）。 */
    private static void finish(MinecraftServer server, ResearchState state, String id) {
        for (int i = 0; i < 100_000 && !state.isDone(id); i++) {
            Research.addPoint(server);
        }
    }

    private static void completeRequirements(ResearchState state, String id) {
        Tech tech = Research.TREE.get(id).orElseThrow();
        for (String required : tech.requires()) {
            if (!state.isDone(required)) {
                completeRequirements(state, required);
                state.complete(required);
            }
        }
    }

    // ---- 参考产量

    /** 接上电、在做齿轮的制造台报参考产量（满速每分钟做几次）；拿走原料停下来，过一会儿就不算了。 */
    static void referenceCountsRunningMachines(GameTestHelper helper) {
        tall(helper, new BlockPos(1, 1, 1), UniverseBlocks.TESLA_TOWER.get().defaultBlockState());
        BlockPos star = new BlockPos(5, 1, 1);
        tall(helper, star, UniverseBlocks.ARTIFICIAL_STAR.get().defaultBlockState());
        helper.getBlockEntity(star, FuelGeneratorBlockEntity.class)
                .setItem(FuelGeneratorBlockEntity.FUEL_SLOT, new ItemStack(UniverseItems.ANTIMATTER_FUEL_ROD.get(), 4));
        BlockPos pos = new BlockPos(3, 1, 3);
        helper.setBlock(pos, UniverseBlocks.ASSEMBLER.get());
        RecipeMachineBlockEntity assembler = helper.getBlockEntity(pos, RecipeMachineBlockEntity.class);
        CompoundTag settings = new CompoundTag();
        settings.putString("Recipe", GEAR_RECIPE);
        assembler.applyBlueprintSettings(settings);
        assembler.setItem(0, new ItemStack(Items.IRON_INGOT, 64));
        BlockPos absolute = helper.absolutePos(pos);
        MinecraftServer server = helper.getLevel().getServer();
        String dimension = helper.getLevel().dimension().identifier().toString();
        helper.startSequence()
                // assertValueEqual 拿实际值调 equals，还没报的时候是 null 会直接崩掉测试服务端，所以反过来比
                .thenWaitUntil(() -> helper.assertTrue(GEAR_RECIPE.equals(ProductionLog.referenceRecipe(helper.getLevel(), absolute)),
                        "报的配方"))
                .thenExecute(() -> {
                    RecipeHolder<MachineRecipe> recipe = assembler.recipe();
                    helper.assertTrue(recipe != null, "制造台没有配方");
                    // 一型制造台速度 0.75，没喷增产剂
                    double expected = ProductionStats.LONG_TICKS * 0.75 / recipe.value().time();
                    ReferenceRates.Rate rate = ProductionLog.referenceRate(server, List.of(dimension), "universe:gear");
                    helper.assertTrue(rate.producers() >= 1 && rate.produced() >= expected - 1e-6, "参考产量 " + rate.produced() + "，至少该有 " + expected);
                    ReferenceRates.Rate iron = ProductionLog.referenceRate(server, List.of(dimension), "minecraft:iron_ingot");
                    helper.assertTrue(iron.consumers() >= 1 && iron.consumed() >= expected - 1e-6, "铁锭的参考消耗 " + iron.consumed());
                    assembler.setItem(0, ItemStack.EMPTY);
                })
                .thenWaitUntil(() -> helper.assertTrue(ProductionLog.referenceRecipe(helper.getLevel(), absolute) == null, "停了还在报参考产量"))
                .thenSucceed();
    }

    // ---- 进出口

    /** 星际运输船把货卸进另一个维度的需求站：运到的那边记进口，运出的那边记出口，同一个星系合起来看是内部往来。 */
    static void shipmentCountsAsTrade(GameTestHelper helper) {
        String item = "minecraft:disc_fragment_5";
        MinecraftServer server = helper.getLevel().getServer();
        Galaxy galaxy = Planets.galaxy(server);
        StarSystem system = galaxy.systems().getFirst();
        helper.assertTrue(!system.bodies().isEmpty(), "第一个恒星系里没有星球");
        Planet body = system.bodies().getFirst();
        Location location = new Location(system.index(), body.orbit().radius(), system.x(), system.y(), system.z(), body.id().path());
        String from = "universe:gametest_export";
        String to = "universe:gametest_import";
        Station supply = new Station(UUID.randomUUID(), StationKind.INTERSTELLAR, from, 0, 64, 0, location);
        Station demand = new Station(UUID.randomUUID(), StationKind.INTERSTELLAR, to, 0, 64, 0, location);
        supply.addVessels(1);
        StationSlot slot = demand.slot(0);
        slot.setItem(item);
        slot.setMax(1000);
        slot.setLocal(Logic.STORAGE);
        slot.setRemote(Logic.DEMAND);
        LogisticsNetwork network = Logistics.network(server);
        network.add(supply);
        network.add(demand);
        long now = server.overworld().getGameTime();
        // 一趟快到了的运输船，载着 40 个
        network.restoreTrip(new Trip(Vehicle.VESSEL, supply.id(), demand.id(), item, 0, 0, 40, 40, true, now - 10, now + 2, now + 4, false,
                false, 0));
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertValueEqual(demand.slot(0).count(), 40, "需求站收到的"))
                .thenExecute(() -> {
                    ProductionStats stats = ProductionData.get(server).stats();
                    long time = server.overworld().getGameTime();
                    ProductionStats.Row in = stats.row(List.of(to), item, ProductionStats.Range.MINUTE, time);
                    ProductionStats.Row out = stats.row(List.of(from), item, ProductionStats.Range.MINUTE, time);
                    helper.assertTrue(in.imported() > 0 && in.produced() == 0, "运到的那边没记进口");
                    helper.assertTrue(out.exported() > 0 && out.consumed() == 0, "运出的那边没记出口");
                    List<ProductionStats.Trade> trades = stats.trade(List.of(to), item, ProductionStats.Range.MINUTE, time);
                    helper.assertTrue(trades.size() == 1 && trades.getFirst().partner().equals(from), "进口没记对面是谁");
                    ProductionStats.Row both = stats.row(List.of(from, to), item, ProductionStats.Range.MINUTE, time);
                    helper.assertTrue(both.imported() == 0 && both.exported() == 0, "两边合起来看还算进出口");
                    network.remove(supply.id());
                    network.remove(demand.id());
                })
                .thenSucceed();
    }

    // ---- 仪表盘

    /** 钉图：同一张不钉两次，最多 6 张，能拖、能收起、能关掉；存档来回一趟不丢；每张图都算得出数。 */
    static void dashboardPinsAndReads(GameTestHelper helper) {
        ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
        MinecraftServer server = helper.getLevel().getServer();
        Dashboard.Pin gear = Dashboard.Pin.of(Dashboard.Kind.ITEM, "universe:gear", "", 0);
        Dashboards.edit(player, DashboardEditPayload.Action.ADD, -1, gear);
        Dashboards.edit(player, DashboardEditPayload.Action.ADD, -1, gear);
        helper.assertValueEqual(Dashboards.pins(player).size(), 1, "同一张钉了两次");
        Dashboard.Kind[] kinds = Dashboard.Kind.values();
        for (Dashboard.Kind kind : kinds) {
            Dashboards.edit(player, DashboardEditPayload.Action.ADD, -1, Dashboard.Pin.of(kind, kind == Dashboard.Kind.DYSON ? "0"
                    : kind == Dashboard.Kind.STATION ? UUID.randomUUID().toString() : "universe:circuit_board", "", 1));
        }
        Dashboards.edit(player, DashboardEditPayload.Action.ADD, -1, Dashboard.Pin.of(Dashboard.Kind.ITEM, "universe:magnet", "", 0));
        helper.assertValueEqual(Dashboards.pins(player).size(), Dashboard.MAX_PINS, "钉的张数");
        Dashboards.edit(player, DashboardEditPayload.Action.PLACE, 0, new Dashboard.Pin(Dashboard.Kind.ITEM, "", "", 0, 0.25F, 0.5F, false));
        Dashboards.edit(player, DashboardEditPayload.Action.TOGGLE, 1, gear);
        List<Dashboard.Pin> pins = player.getData(UniverseAttachments.DASHBOARD);
        helper.assertTrue(pins.getFirst().placed() && pins.getFirst().x() == 0.25F, "拖动没记下");
        helper.assertTrue(pins.get(1).collapsed(), "没收起");
        for (Dashboard.Pin pin : pins) {
            helper.assertTrue(Dashboards.reading(server, pin) != null, "算不出 " + pin.kind() + " 的数");
        }
        List<Dashboard.Pin> saved = DashboardPins.CODEC.codec()
                .parse(JsonOps.INSTANCE, DashboardPins.CODEC.codec().encodeStart(JsonOps.INSTANCE, pins).getOrThrow()).getOrThrow();
        helper.assertValueEqual(saved, pins, "存档来回一趟");
        Dashboards.edit(player, DashboardEditPayload.Action.REMOVE, 0, gear);
        Dashboards.edit(player, DashboardEditPayload.Action.RESET, -1, gear);
        helper.assertValueEqual(Dashboards.pins(player).size(), Dashboard.MAX_PINS - 1, "关掉一张后的张数");
        helper.assertTrue(Dashboards.pins(player).stream().noneMatch(Dashboard.Pin::placed), "全部靠右以后还有拖过的");
        helper.succeed();
    }

    private static void tall(GameTestHelper helper, BlockPos pos, BlockState state) {
        helper.setBlock(pos, state.setValue(TallMachineBlock.HALF, DoubleBlockHalf.LOWER));
        helper.setBlock(pos.above(), state.setValue(TallMachineBlock.HALF, DoubleBlockHalf.UPPER));
    }
}
