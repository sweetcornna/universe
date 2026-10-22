package io.github.sweetcornna.universe.gametest;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import io.github.sweetcornna.universe.factory.MachineBlock;
import io.github.sweetcornna.universe.block.HydroponicTrayBlockEntity;
import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.block.portal.PortalGlyphBlock;
import io.github.sweetcornna.universe.block.ship.HelmBlockEntity;
import io.github.sweetcornna.universe.block.ship.LandingPadBlockEntity;
import io.github.sweetcornna.universe.core.factory.Cargo;
import io.github.sweetcornna.universe.core.factory.Proliferation;
import io.github.sweetcornna.universe.core.fleet.Expedition;
import io.github.sweetcornna.universe.core.fleet.Fleet;
import io.github.sweetcornna.universe.core.fleet.Frigate;
import io.github.sweetcornna.universe.core.fleet.FrigateType;
import io.github.sweetcornna.universe.core.galaxy.BodyId;
import io.github.sweetcornna.universe.core.galaxy.Planet;
import io.github.sweetcornna.universe.core.galaxy.StarSystem;
import io.github.sweetcornna.universe.core.logistics.Station;
import io.github.sweetcornna.universe.core.logistics.StationSlot;
import io.github.sweetcornna.universe.core.mecha.Distribution;
import io.github.sweetcornna.universe.core.portal.PortalAddress;
import io.github.sweetcornna.universe.core.portal.PortalLayout;
import io.github.sweetcornna.universe.core.procgen.Rng;
import io.github.sweetcornna.universe.core.research.ResearchState;
import io.github.sweetcornna.universe.core.research.Tech;
import io.github.sweetcornna.universe.core.research.TechTree;
import io.github.sweetcornna.universe.core.ship.CrashedShip;
import io.github.sweetcornna.universe.core.ship.ShipDesign;
import io.github.sweetcornna.universe.core.ship.ShipDesigner;
import io.github.sweetcornna.universe.core.ship.ShipMarket;
import io.github.sweetcornna.universe.core.ship.ShipType;
import io.github.sweetcornna.universe.core.space.DriveTier;
import io.github.sweetcornna.universe.core.survival.Dish;
import io.github.sweetcornna.universe.core.tech.Grade;
import io.github.sweetcornna.universe.core.tech.ModuleType;
import io.github.sweetcornna.universe.core.tech.TechModule;
import io.github.sweetcornna.universe.core.trade.Economy;
import io.github.sweetcornna.universe.entity.ship.ShipAssembly;
import io.github.sweetcornna.universe.entity.ship.ShipBlueprints;
import io.github.sweetcornna.universe.entity.ship.ShipEntity;
import io.github.sweetcornna.universe.entity.ship.ShipOwners;
import io.github.sweetcornna.universe.factory.FuelRecipeMachineBlockEntity;
import io.github.sweetcornna.universe.factory.RecipeMachineBlockEntity;
import io.github.sweetcornna.universe.factory.TallMachineBlock;
import io.github.sweetcornna.universe.factory.belt.BeltBlock;
import io.github.sweetcornna.universe.factory.belt.BeltNetwork;
import io.github.sweetcornna.universe.factory.belt.BeltPassBlock;
import io.github.sweetcornna.universe.factory.belt.SprayCoaterBlockEntity;
import io.github.sweetcornna.universe.factory.belt.StorageDepotBlockEntity;
import io.github.sweetcornna.universe.factory.power.FuelGeneratorBlockEntity;
import io.github.sweetcornna.universe.factory.power.GeothermalBlockEntity;
import io.github.sweetcornna.universe.factory.research.MatrixLabBlockEntity;
import io.github.sweetcornna.universe.factory.research.Research;
import io.github.sweetcornna.universe.factory.research.ResearchData;
import io.github.sweetcornna.universe.fleet.FleetData;
import io.github.sweetcornna.universe.fleet.FleetTerminal;
import io.github.sweetcornna.universe.logistics.DistributorBlockEntity;
import io.github.sweetcornna.universe.logistics.StationBlockEntity;
import io.github.sweetcornna.universe.logistics.StationHandler;
import io.github.sweetcornna.universe.poi.PoiInteractions;
import io.github.sweetcornna.universe.portal.Portals;
import io.github.sweetcornna.universe.registry.UniverseBlocks;
import io.github.sweetcornna.universe.registry.UniverseDataComponents;
import io.github.sweetcornna.universe.registry.UniverseEntities;
import io.github.sweetcornna.universe.registry.UniverseItems;
import io.github.sweetcornna.universe.world.planet.Planets;
import io.github.sweetcornna.universe.block.ClamBlock;
import io.github.sweetcornna.universe.entity.AquaticCreature;
import io.github.sweetcornna.universe.exocraft.Submarine;
import io.github.sweetcornna.universe.exocraft.Torpedo;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.gametest.GameTestHooks;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * 游戏内测试（ADR-0019）：在真的世界里搭几台设备，看电网、配方、传送带、研究站连起来能不能转。单元测试只管 core，
 * 这里管适配层。{@code ./gradlew runGameTestServer} 跑完全部测试后退出，有失败时退出码非零；开发客户端里也能用
 * {@code /test} 命令单个跑。
 *
 * <p>只在 NeoForge 打开游戏内测试时（开发环境）注册，正式版里没有这些条目，所以不放在 registry 包里。测试场地是
 * 一块 7×4×7、底下一层石台的空结构（{@code data/universe/structure/gametest/platform.nbt}，由 {@code tools/gametest_platform.py} 生成）。
 */
public final class UniverseGameTests {
    private static final DeferredRegister<Consumer<GameTestHelper>> FUNCTIONS = DeferredRegister.create(Registries.TEST_FUNCTION, Universe.MOD_ID);
    private static final Identifier PLATFORM = Universe.id("gametest/platform");
    /** 17×8×17、石头地面的大场地，放整艘设计出来的船（ADR-0024）。 */
    private static final Identifier HANGAR = Universe.id("gametest/hangar");
    /** 13×12×11 的石台，放得下一整座先驱者传送门。 */
    private static final Identifier PORTAL = Universe.id("gametest/portal");
    private static final String GEAR_RECIPE = "universe:assembling/gear";
    private static final String LAB_TECH = "fluid_storage";
    // 字段按顺序初始化：这几个要排在 FUNCTIONS 后面
    private static final ResourceKey<Consumer<GameTestHelper>> ASSEMBLER_MAKES_GEARS = function("assembler_makes_gears",
            UniverseGameTests::assemblerMakesGears);
    private static final ResourceKey<Consumer<GameTestHelper>> ASSEMBLER_WAITS_FOR_POWER = function("assembler_waits_for_power",
            UniverseGameTests::assemblerWaitsForPower);
    private static final ResourceKey<Consumer<GameTestHelper>> BELT_DELIVERS_TO_DEPOT = function("belt_delivers_to_depot",
            UniverseGameTests::beltDeliversToDepot);
    private static final ResourceKey<Consumer<GameTestHelper>> LAB_ADDS_POINTS = function("lab_adds_points", UniverseGameTests::labAddsPoints);
    private static final ResourceKey<Consumer<GameTestHelper>> COATER_SPRAYS_CARGO = function("coater_sprays_cargo",
            UniverseGameTests::coaterSpraysCargo);
    private static final ResourceKey<Consumer<GameTestHelper>> PILER_STACKS_CARGO = function("piler_stacks_cargo",
            UniverseGameTests::pilerStacksCargo);
    private static final ResourceKey<Consumer<GameTestHelper>> SPRAYED_INPUT_MAKES_MORE = function("sprayed_input_makes_more",
            UniverseGameTests::sprayedInputMakesMore);
    private static final ResourceKey<Consumer<GameTestHelper>> STACKED_LABS_SHARE = function("stacked_labs_share",
            UniverseGameTests::stackedLabsShare);
    private static final ResourceKey<Consumer<GameTestHelper>> GEOTHERMAL_NEEDS_LAVA = function("geothermal_needs_lava",
            UniverseGameTests::geothermalNeedsLava);
    private static final ResourceKey<Consumer<GameTestHelper>> STATION_KEEPS_POINTS = function("station_keeps_points",
            UniverseGameTests::stationKeepsPoints);
    private static final ResourceKey<Consumer<GameTestHelper>> DISTRIBUTORS_SHARE_ITEMS = function("distributors_share_items",
            UniverseGameTests::distributorsShareItems);
    private static final ResourceKey<Consumer<GameTestHelper>> HELM_TECH_TUNES_SHIP = function("helm_tech_tunes_ship",
            UniverseGameTests::helmTechTunesShip);
    private static final ResourceKey<Consumer<GameTestHelper>> LANDING_PAD_REFUELS_SHIP = function("landing_pad_refuels_ship",
            UniverseGameTests::landingPadRefuelsShip);
    private static final ResourceKey<Consumer<GameTestHelper>> HYDROPONIC_TRAY_GROWS = function("hydroponic_tray_grows",
            UniverseGameTests::hydroponicTrayGrows);
    private static final ResourceKey<Consumer<GameTestHelper>> FUEL_MACHINES_COOK = function("fuel_machines_cook",
            UniverseGameTests::fuelMachinesCook);
    private static final ResourceKey<Consumer<GameTestHelper>> FLEET_SAVE_ROUND_TRIP = function("fleet_save_round_trip",
            UniverseGameTests::fleetSaveRoundTrip);
    private static final ResourceKey<Consumer<GameTestHelper>> FLEET_VOYAGE_CLAIM = function("fleet_voyage_claim",
            UniverseGameTests::fleetVoyageClaim);
    private static final ResourceKey<Consumer<GameTestHelper>> DEALER_SHIPS_ASSEMBLE = function("dealer_ships_assemble",
            UniverseGameTests::dealerShipsAssemble);
    private static final ResourceKey<Consumer<GameTestHelper>> CRASHED_SHIP_REPAIRS = function("crashed_ship_repairs",
            UniverseGameTests::crashedShipRepairs);
    private static final ResourceKey<Consumer<GameTestHelper>> PORTAL_DIALS_AND_CLOSES = function("portal_dials_and_closes",
            UniverseGameTests::portalDialsAndCloses);
    private static final ResourceKey<Consumer<GameTestHelper>> CLAM_GIVES_PEARL = function("clam_gives_pearl",
            UniverseGameTests::clamGivesPearl);
    private static final ResourceKey<Consumer<GameTestHelper>> SUBMARINE_HOVERS = function("submarine_hovers",
            UniverseGameTests::submarineHovers);
    private static final ResourceKey<Consumer<GameTestHelper>> TORPEDO_HITS_CREATURE = function("torpedo_hits_creature",
            UniverseGameTests::torpedoHitsCreature);

    private UniverseGameTests() {
    }

    public static void register(IEventBus modBus) {
        if (!GameTestHooks.isGametestEnabled()) {
            return;
        }
        FUNCTIONS.register(modBus);
        modBus.addListener(RegisterGameTestsEvent.class, UniverseGameTests::registerTests);
    }

    private static ResourceKey<Consumer<GameTestHelper>> function(String name, Consumer<GameTestHelper> test) {
        FUNCTIONS.register(name, () -> test);
        return ResourceKey.create(Registries.TEST_FUNCTION, Universe.id(name));
    }

    private static void registerTests(RegisterGameTestsEvent event) {
        Holder<TestEnvironmentDefinition<?>> environment = event.registerEnvironment(Universe.id("default"));
        test(event, environment, ASSEMBLER_MAKES_GEARS, 200);
        test(event, environment, ASSEMBLER_WAITS_FOR_POWER, 100);
        test(event, environment, BELT_DELIVERS_TO_DEPOT, 200);
        test(event, environment, LAB_ADDS_POINTS, 400);
        test(event, environment, COATER_SPRAYS_CARGO, 200);
        test(event, environment, PILER_STACKS_CARGO, 200);
        test(event, environment, SPRAYED_INPUT_MAKES_MORE, 600);
        test(event, environment, STACKED_LABS_SHARE, 100);
        test(event, environment, GEOTHERMAL_NEEDS_LAVA, 60);
        test(event, environment, STATION_KEEPS_POINTS, 40);
        test(event, environment, DISTRIBUTORS_SHARE_ITEMS, 200);
        test(event, environment, HELM_TECH_TUNES_SHIP, 40);
        test(event, environment, LANDING_PAD_REFUELS_SHIP, 300);
        test(event, environment, HYDROPONIC_TRAY_GROWS, 200);
        test(event, environment, FUEL_MACHINES_COOK, 200);
        test(event, environment, DEALER_SHIPS_ASSEMBLE, HANGAR, 40);
        test(event, environment, CRASHED_SHIP_REPAIRS, HANGAR, 40);
        test(event, environment, PORTAL_DIALS_AND_CLOSES, PORTAL, 20);
        test(event, environment, FLEET_SAVE_ROUND_TRIP, 20);
        test(event, environment, FLEET_VOYAGE_CLAIM, 20);
        test(event, environment, CLAM_GIVES_PEARL, 40);
        test(event, environment, SUBMARINE_HOVERS, 80);
        test(event, environment, TORPEDO_HITS_CREATURE, 60);
    }

    private static void test(RegisterGameTestsEvent event, Holder<TestEnvironmentDefinition<?>> environment, ResourceKey<Consumer<GameTestHelper>> function,
            int maxTicks) {
        test(event, environment, function, PLATFORM, maxTicks);
    }

    private static void test(RegisterGameTestsEvent event, Holder<TestEnvironmentDefinition<?>> environment, ResourceKey<Consumer<GameTestHelper>> function,
            Identifier structure, int maxTicks) {
        event.registerTest(function.identifier(), new FunctionGameTestInstance(function, new TestData<>(environment, structure, maxTicks, 0, true)));
    }

    // ---- 测试

    /** 感应塔把人造恒星的电送到一型制造台，制造台用铁锭做出齿轮。 */
    private static void assemblerMakesGears(GameTestHelper helper) {
        powerPlant(helper);
        BlockPos machine = assembler(helper, new BlockPos(3, 1, 3));
        helper.succeedWhen(() -> {
            RecipeMachineBlockEntity assembler = helper.getBlockEntity(machine, RecipeMachineBlockEntity.class);
            helper.assertTrue(assembler.countItem(UniverseItems.GEAR.get()) > 0, "制造台没有做出齿轮");
        });
    }

    /** 没接电的制造台停在“缺电”，不出东西，原料不动。 */
    private static void assemblerWaitsForPower(GameTestHelper helper) {
        BlockPos machine = assembler(helper, new BlockPos(3, 1, 3));
        helper.runAfterDelay(60, () -> {
            RecipeMachineBlockEntity assembler = helper.getBlockEntity(machine, RecipeMachineBlockEntity.class);
            helper.assertValueEqual(assembler.data().get(RecipeMachineBlockEntity.STATUS), RecipeMachineBlockEntity.NO_POWER, "制造台状态");
            helper.assertValueEqual(assembler.countItem(UniverseItems.GEAR.get()), 0, "齿轮数量");
            helper.assertValueEqual(assembler.countItem(Items.IRON_INGOT), 64, "铁锭数量");
            helper.succeed();
        });
    }

    /** 一排朝东的传送带把头上放的铁锭送进尽头的储物仓。 */
    private static void beltDeliversToDepot(GameTestHelper helper) {
        BlockState belt = UniverseBlocks.CONVEYOR_BELT.get().defaultBlockState().setValue(BeltBlock.FACING, Direction.EAST);
        for (int x = 0; x < 6; x++) {
            helper.setBlock(new BlockPos(x, 1, 3), belt);
        }
        BlockPos depot = new BlockPos(6, 1, 3);
        helper.setBlock(depot, UniverseBlocks.STORAGE_DEPOT.get());
        // 传送带网络在下一刻才把新放的几格连成线
        helper.runAfterDelay(2, () -> helper.assertTrue(BeltNetwork.of(helper.getLevel())
                .insertAt(helper.absolutePos(new BlockPos(0, 1, 3)), 0, BeltNetwork.id(Items.IRON_INGOT)), "传送带上放不下铁锭"));
        helper.succeedWhen(() -> helper.assertTrue(helper.getBlockEntity(depot, StorageDepotBlockEntity.class).countItem(Items.IRON_INGOT) == 1,
                "铁锭没有进储物仓"));
    }

    /** 接上电、放进电磁矩阵的研究站给当前研究攒点数。 */
    private static void labAddsPoints(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ResearchState state = ResearchData.get(server).state();
        if (state.isDone(LAB_TECH)) {
            state.revoke(LAB_TECH);
        }
        completeRequirements(state, LAB_TECH);
        helper.assertTrue(state.start(Research.TREE, LAB_TECH), "研究开不了头");
        Research.changed(server);
        int before = state.progress(LAB_TECH);

        powerPlant(helper);
        BlockPos pos = new BlockPos(3, 1, 3);
        helper.setBlock(pos, UniverseBlocks.MATRIX_LAB.get());
        MatrixLabBlockEntity lab = helper.getBlockEntity(pos, MatrixLabBlockEntity.class);
        String matrix = Research.TREE.get(LAB_TECH).orElseThrow().matrices().getFirst();
        lab.setItem(TechTree.MATRICES.indexOf(matrix), new ItemStack(UniverseItems.ELECTROMAGNETIC_MATRIX.get(), MatrixLabBlockEntity.AUTOMATION_LIMIT));
        helper.succeedWhen(() -> helper.assertTrue(state.progress(LAB_TECH) > before || state.isDone(LAB_TECH), "研究点数没有增加"));
    }

    /** 传送带中间串一台装了 Mk.I 增产剂的喷涂机，经过的铁锭进储物仓时每个带 1 点（ADR-0022）。 */
    private static void coaterSpraysCargo(GameTestHelper helper) {
        powerPlant(helper);
        BlockState belt = UniverseBlocks.CONVEYOR_BELT.get().defaultBlockState().setValue(BeltBlock.FACING, Direction.EAST);
        for (int x = 0; x < 6; x++) {
            helper.setBlock(new BlockPos(x, 1, 3), belt);
        }
        BlockPos coater = new BlockPos(3, 1, 3);
        helper.setBlock(coater, UniverseBlocks.SPRAY_COATER.get().defaultBlockState().setValue(BeltPassBlock.FACING, Direction.EAST));
        helper.assertValueEqual(helper.getBlockEntity(coater, SprayCoaterBlockEntity.class)
                .insert(new ItemStack(UniverseItems.PROLIFERATOR_MK1.get(), 1)), 1, "装进去的增产剂");
        BlockPos depot = new BlockPos(6, 1, 3);
        helper.setBlock(depot, UniverseBlocks.STORAGE_DEPOT.get());
        // 等电网通上电再放货：没电时货物照样流过去，只是不喷
        helper.runAfterDelay(40, () -> helper.assertTrue(BeltNetwork.of(helper.getLevel())
                .insertAt(helper.absolutePos(new BlockPos(0, 1, 3)), 0, BeltNetwork.id(Items.IRON_INGOT)), "传送带上放不下铁锭"));
        helper.succeedWhen(() -> {
            ItemStack stack = helper.getBlockEntity(depot, StorageDepotBlockEntity.class).getItem(0);
            helper.assertTrue(stack.is(Items.IRON_INGOT), "铁锭没有进储物仓");
            helper.assertValueEqual(BeltNetwork.level(stack), 1, "铁锭的增产点数");
        });
    }

    /** 自动集装机把先后来的两个铁锭叠成一格（货物集装 0 级叠 2 个），后面的传送带上是一格 2 个。 */
    private static void pilerStacksCargo(GameTestHelper helper) {
        powerPlant(helper);
        BlockState belt = UniverseBlocks.CONVEYOR_BELT.get().defaultBlockState().setValue(BeltBlock.FACING, Direction.EAST);
        for (int x = 0; x < 7; x++) {
            helper.setBlock(new BlockPos(x, 1, 3), belt);
        }
        helper.setBlock(new BlockPos(3, 1, 3), UniverseBlocks.AUTOMATIC_PILER.get().defaultBlockState().setValue(BeltPassBlock.FACING, Direction.EAST));
        int iron = BeltNetwork.id(Items.IRON_INGOT);
        // 同一刻放在相邻两格：Mk.I 传送带走一格约 17 刻，集装机最多等 20 刻
        helper.runAfterDelay(40, () -> {
            BeltNetwork network = BeltNetwork.of(helper.getLevel());
            helper.assertTrue(network.insertAt(helper.absolutePos(new BlockPos(1, 1, 3)), 0, iron)
                    && network.insertAt(helper.absolutePos(new BlockPos(0, 1, 3)), 0, iron), "传送带上放不下铁锭");
        });
        helper.succeedWhen(() -> {
            int piled = 0;
            for (int x = 4; x < 7; x++) {
                BeltNetwork.BlockItems items = BeltNetwork.of(helper.getLevel()).itemsAt(helper.absolutePos(new BlockPos(x, 1, 3)));
                if (items != null) {
                    for (int cargo : items.items()) {
                        piled = Math.max(piled, Cargo.count(cargo));
                    }
                }
            }
            helper.assertValueEqual(piled, 2, "集装机后面一格货物的个数");
        });
    }

    /** 两台做齿轮的制造台，一台的铁锭喷过 Mk.III：同样的时间里它多出齿轮（默认额外产出）。 */
    private static void sprayedInputMakesMore(GameTestHelper helper) {
        powerPlant(helper);
        BlockPos plain = assembler(helper, new BlockPos(2, 1, 4));
        BlockPos sprayed = assembler(helper, new BlockPos(4, 1, 4));
        helper.getBlockEntity(sprayed, RecipeMachineBlockEntity.class).setItem(0, BeltNetwork.stack(Items.IRON_INGOT, 64, Proliferation.MAX_LEVEL));
        helper.succeedWhen(() -> {
            int a = helper.getBlockEntity(plain, RecipeMachineBlockEntity.class).countItem(UniverseItems.GEAR.get());
            int b = helper.getBlockEntity(sprayed, RecipeMachineBlockEntity.class).countItem(UniverseItems.GEAR.get());
            helper.assertTrue(a >= 4 && b > a, "喷过的铁锭没有多出齿轮：" + a + " / " + b);
        });
    }

    /** 自演化研究站叠在矩阵研究站上，每秒从下面那台拿一半矩阵。 */
    private static void stackedLabsShare(GameTestHelper helper) {
        BlockPos bottom = new BlockPos(3, 1, 3);
        helper.setBlock(bottom, UniverseBlocks.MATRIX_LAB.get());
        helper.setBlock(bottom.above(), UniverseBlocks.SELF_EVOLUTION_LAB.get());
        Item matrix = UniverseItems.ELECTROMAGNETIC_MATRIX.get();
        int slot = TechTree.MATRICES.indexOf(BuiltInRegistries.ITEM.getKey(matrix).toString());
        helper.getBlockEntity(bottom, MatrixLabBlockEntity.class).setItem(slot, BeltNetwork.stack(matrix, 20, 2));
        helper.succeedWhen(() -> {
            helper.assertValueEqual(helper.getBlockEntity(bottom.above(), MatrixLabBlockEntity.class).countItem(matrix), 10, "上面那台的矩阵");
            helper.assertValueEqual(helper.getBlockEntity(bottom, MatrixLabBlockEntity.class).countItem(matrix), 10, "下面那台的矩阵");
        });
    }

    /** 地热发电站下面是熔岩才有地热；放在石头上的是 0。 */
    private static void geothermalNeedsLava(GameTestHelper helper) {
        BlockPos lava = new BlockPos(3, 1, 3);
        for (Direction side : Direction.Plane.HORIZONTAL) {
            helper.setBlock(lava.relative(side), Blocks.STONE);
        }
        helper.setBlock(lava, Blocks.LAVA);
        BlockPos hot = lava.above();
        helper.setBlock(hot, UniverseBlocks.GEOTHERMAL_POWER_STATION.get());
        BlockPos dry = new BlockPos(5, 1, 5);
        helper.setBlock(dry, UniverseBlocks.GEOTHERMAL_POWER_STATION.get());
        helper.runAfterDelay(25, () -> {
            helper.assertValueEqual(helper.getBlockEntity(hot, GeothermalBlockEntity.class).data().get(GeothermalBlockEntity.HEAT), 100, "熔岩上的地热");
            helper.assertValueEqual(helper.getBlockEntity(dry, GeothermalBlockEntity.class).data().get(GeothermalBlockEntity.HEAT), 0, "石头上的地热");
            helper.succeed();
        });
    }

    /** 物流站收喷过的铁锭，点数记进格子；回滚的事务不留点数；拿货时只给带同样点数的物品。 */
    private static void stationKeepsPoints(GameTestHelper helper) {
        BlockPos pos = new BlockPos(3, 1, 3);
        tall(helper, pos, UniverseBlocks.PLANETARY_LOGISTICS_STATION.get().defaultBlockState());
        helper.runAfterDelay(1, () -> {
            StationBlockEntity entity = helper.getBlockEntity(pos, StationBlockEntity.class);
            Station station = entity.station();
            helper.assertTrue(station != null, "账本里没有这座站");
            StationSlot slot = station.slot(0);
            helper.assertTrue(slot.setItem("minecraft:iron_ingot"), "设不了物品");
            slot.setMax(1000);
            StationHandler handler = entity.handler();
            ItemResource sprayed = ItemResource.of(Items.IRON_INGOT).with(UniverseDataComponents.PROLIFERATION.get(), 2);
            try (Transaction transaction = Transaction.openRoot()) {
                helper.assertValueEqual(handler.insert(0, sprayed, 10, transaction), 10, "放进去的铁锭");
            }
            helper.assertValueEqual(slot.count(), 0, "回滚后的数量");
            helper.assertValueEqual(slot.points(), 0L, "回滚后的点数");
            try (Transaction transaction = Transaction.openRoot()) {
                handler.insert(0, sprayed, 10, transaction);
                transaction.commit();
            }
            helper.assertValueEqual(slot.points(), 20L, "点数");
            helper.assertValueEqual(handler.getResource(0), sprayed, "格子里的物品");
            try (Transaction transaction = Transaction.openRoot()) {
                helper.assertValueEqual(handler.extract(0, ItemResource.of(Items.IRON_INGOT), 5, transaction), 0, "没喷过的铁锭");
                helper.assertValueEqual(handler.extract(0, sprayed, 5, transaction), 5, "喷过的铁锭");
                transaction.commit();
            }
            helper.assertValueEqual(slot.points(), 10L, "剩下的点数");
            helper.succeed();
        });
    }

    /** 两台配送器各自顶在储物仓上：需求的那台派机器人去供应的那台取铁锭，放进自己下面（ADR-0023）。 */
    private static void distributorsShareItems(GameTestHelper helper) {
        powerPlant(helper);
        BlockPos supply = new BlockPos(2, 1, 4);
        BlockPos demand = new BlockPos(4, 1, 4);
        helper.setBlock(supply, UniverseBlocks.STORAGE_DEPOT.get());
        helper.setBlock(demand, UniverseBlocks.STORAGE_DEPOT.get());
        helper.getBlockEntity(supply, StorageDepotBlockEntity.class).setItem(0, new ItemStack(Items.IRON_INGOT, 32));
        distributor(helper, supply.above(), Distribution.Icarus.NONE, Distribution.Peer.SUPPLY);
        distributor(helper, demand.above(), Distribution.Icarus.NONE, Distribution.Peer.DEMAND);
        helper.succeedWhen(() -> helper.assertTrue(helper.getBlockEntity(demand, StorageDepotBlockEntity.class).countItem(Items.IRON_INGOT) > 0,
                "需求的配送器没有取到铁锭"));
    }

    // ---- 搭场地

    /** 一台管铁锭、装了两个配送机器人的物流配送器。 */
    private static void distributor(GameTestHelper helper, BlockPos pos, Distribution.Icarus icarus, Distribution.Peer peer) {
        helper.setBlock(pos, UniverseBlocks.LOGISTICS_DISTRIBUTOR.get());
        DistributorBlockEntity distributor = helper.getBlockEntity(pos, DistributorBlockEntity.class);
        CompoundTag settings = new CompoundTag();
        settings.putString("Filter", "minecraft:iron_ingot");
        settings.putString("Icarus", icarus.name());
        settings.putString("Peer", peer.name());
        distributor.applyBlueprintSettings(settings);
        distributor.setItem(DistributorBlockEntity.BOT_SLOT, new ItemStack(UniverseItems.LOGISTICS_BOT.get(), 2));
    }

    /**
     * 驾驶台里的船体加固模块和镉驱动（ADR-0024）跟着船走：组装出来的船记了主人，船体比只按部件算的高，驱动是镉驱动；
     * 停船以后主力船记成"停了"，模块和驱动还在驾驶台里。
     */
    private static void helmTechTunesShip(GameTestHelper helper) {
        BlockPos helm = smallShip(helper, 1);
        HelmBlockEntity tech = helper.getBlockEntity(helm, HelmBlockEntity.class);
        ItemStack module = new ItemStack(UniverseItems.MODULES.get(ModuleType.HULL_REINFORCEMENT).get());
        module.set(UniverseDataComponents.TECH_MODULE.get(), TechModule.typical(ModuleType.HULL_REINFORCEMENT, Grade.A));
        tech.items().set(0, module);
        tech.items().set(1, new ItemStack(UniverseItems.DRIVES.get(DriveTier.CADMIUM).get()));
        tech.setChanged();
        UUID owner = UUID.randomUUID();
        ShipEntity ship = ShipAssembly.assemble(helper.getLevel(), helper.absolutePos(helm), owner, message -> {
        });
        helper.assertTrue(ship != null && ship.structure() != null, "没有组装出飞船");
        helper.assertValueEqual(ship.structure().rating().type(), ShipType.SHUTTLE, "类型");
        helper.assertValueEqual(ship.structure().tech().modules().size(), 1, "生效的模块数");
        helper.assertValueEqual(ship.structure().stats().drive(), DriveTier.CADMIUM, "驱动");
        helper.assertTrue(ship.structure().stats().hull() > ship.structure().baseStats().hull() + 1, "船体没有加上去");
        helper.assertValueEqual(ship.owner(), owner, "主人");
        ShipOwners owners = ShipOwners.get(helper.getLevel().getServer());
        owners.piloted(owner, ship);
        helper.assertTrue(ShipAssembly.disassemble(ship), "停不了船");
        helper.assertTrue(owners.main(owner).orElseThrow().parked(), "停船没记下来");
        HelmBlockEntity back = helper.getBlockEntity(helm, HelmBlockEntity.class);
        helper.assertTrue(back.items().get(0).is(UniverseItems.MODULES.get(ModuleType.HULL_REINFORCEMENT).get()), "模块没放回驾驶台");
        helper.assertTrue(back.items().get(1).is(UniverseItems.DRIVES.get(DriveTier.CADMIUM).get()), "驱动没放回驾驶台");
        helper.succeed();
    }

    /** 着陆台接上电，给停在上面、燃料舱空着的船加燃料（ADR-0024）。 */
    private static void landingPadRefuelsShip(GameTestHelper helper) {
        powerPlant(helper);
        BlockPos pad = new BlockPos(3, 1, 3);
        helper.setBlock(pad, UniverseBlocks.LANDING_PAD.get());
        BlockPos helm = smallShip(helper, 2);
        ShipEntity ship = ShipAssembly.assemble(helper.getLevel(), helper.absolutePos(helm), null, message -> {
        });
        helper.assertTrue(ship != null, "没有组装出飞船");
        helper.assertValueEqual(ship.fuelServer(), 0.0, "燃料舱一开始是空的");
        helper.succeedWhen(() -> {
            helper.assertTrue(helper.getBlockEntity(pad, LandingPadBlockEntity.class).shipAbove() == ship, "着陆台上没找到船");
            helper.assertTrue(ship.fuelServer() > 20, "燃料没加上去：" + ship.fuelServer());
            ship.discard();
        });
    }

    /** 水培槽（ADR-0026）：洞穴髓球种不进去；种下氧囊草、接上电就开始长，补光灯亮。 */
    private static void hydroponicTrayGrows(GameTestHelper helper) {
        powerPlant(helper);
        BlockPos pos = new BlockPos(3, 1, 3);
        helper.setBlock(pos, UniverseBlocks.HYDROPONIC_TRAY.get());
        HydroponicTrayBlockEntity tray = helper.getBlockEntity(pos, HydroponicTrayBlockEntity.class);
        helper.assertFalse(tray.sow(new ItemStack(UniverseItems.RESOURCES.get("marrow_bulb").get())), "洞穴髓球不该能种进水培槽");
        helper.assertTrue(tray.sow(new ItemStack(UniverseItems.RESOURCES.get("oxygen").get())), "氧气种不下去");
        helper.succeedWhen(() -> {
            helper.assertTrue(tray.growth() > 0.01, "没有长：" + tray.growth());
            helper.assertBlockProperty(pos, MachineBlock.WORKING, true);
        });
    }

    /** 甲壳蚌（ADR-0026）：右键撬开掉一颗活珍珠，撬开的再右键没有了。 */
    private static void clamGivesPearl(GameTestHelper helper) {
        BlockPos pos = new BlockPos(3, 1, 3);
        flood(helper, 2, 1, 2, 4, 2, 4);
        helper.setBlock(pos, UniverseBlocks.CLAM.get());
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        helper.useBlock(pos, player);
        helper.succeedWhen(() -> {
            helper.assertBlockProperty(pos, ClamBlock.OPEN, true);
            helper.assertItemEntityPresent(UniverseItems.LIVING_PEARL.get(), pos, 3);
        });
    }

    /** 潜艇（ADR-0026）：没人开的时候在水里悬停，不沉也不浮；在空气里往下掉。 */
    private static void submarineHovers(GameTestHelper helper) {
        flood(helper, 0, 1, 0, 3, 3, 6);
        Submarine wet = helper.spawn(UniverseEntities.SUBMARINE.get(), new Vec3(1.7, 1.5, 3.5));
        Submarine dry = helper.spawn(UniverseEntities.SUBMARINE.get(), new Vec3(5.4, 2.4, 3.5));
        double start = wet.getY();
        helper.runAfterDelay(60, () -> {
            helper.assertTrue(wet.isInWater() && wet.submerged(), "潜艇不在水里");
            helper.assertTrue(Math.abs(wet.getY() - start) < 0.15, "水里的潜艇漂走了：" + start + " → " + wet.getY());
            helper.assertTrue(dry.getY() < helper.absoluteVec(new Vec3(0, 1.1, 0)).y, "空气里的潜艇没掉下去：" + dry.getY());
            helper.succeed();
        });
    }

    /** 鱼雷（ADR-0026）：在水里直线跑，打中水生动物炸开；打不中发射它的潜艇。 */
    private static void torpedoHitsCreature(GameTestHelper helper) {
        flood(helper, 0, 1, 0, 6, 3, 6);
        Submarine submarine = helper.spawn(UniverseEntities.SUBMARINE.get(), new Vec3(1.2, 1.2, 3.5));
        AquaticCreature target = helper.spawn(UniverseEntities.AQUATIC_CREATURE.get(), new Vec3(5.5, 1.8, 3.5));
        float health = target.getHealth();
        Vec3 from = helper.absoluteVec(new Vec3(2.6, 2.0, 3.5));
        helper.getLevel().addFreshEntity(Torpedo.launch(helper.getLevel(), submarine, from, new Vec3(1, -0.05, 0)));
        helper.succeedWhen(() -> {
            helper.assertTrue(!target.isAlive() || target.getHealth() < health, "鱼雷没打中：" + target.getHealth());
            helper.assertTrue(submarine.isAlive(), "鱼雷炸了自己的潜艇");
        });
    }

    /** 测试场地里这一块（含两端，相对坐标）灌满水。 */
    private static void flood(GameTestHelper helper, int x0, int y0, int z0, int x1, int y1, int z1) {
        for (BlockPos pos : BlockPos.betweenClosed(x0, y0, z0, x1, y1, z1)) {
            helper.setBlock(pos, Blocks.WATER);
        }
    }

    /**
     * 烧燃料的配方设备（ADR-0026）不接电也干活：营养处理器烧一块煤把牛奶做成黄油，空桶留在原料格里；
     * 便携精炼机烧异星原木把粗铁炼成铁锭。
     */
    private static void fuelMachinesCook(GameTestHelper helper) {
        BlockPos processorPos = new BlockPos(2, 1, 3);
        helper.setBlock(processorPos, UniverseBlocks.NUTRIENT_PROCESSOR.get());
        FuelRecipeMachineBlockEntity processor = helper.getBlockEntity(processorPos, FuelRecipeMachineBlockEntity.class);
        selectRecipe(processor, "universe:nutrient/butter");
        processor.setItem(0, new ItemStack(Items.MILK_BUCKET));
        processor.setItem(processor.fuelSlot(), new ItemStack(Items.COAL));
        BlockPos refinerPos = new BlockPos(5, 1, 3);
        helper.setBlock(refinerPos, UniverseBlocks.PORTABLE_REFINER.get());
        FuelRecipeMachineBlockEntity refiner = helper.getBlockEntity(refinerPos, FuelRecipeMachineBlockEntity.class);
        selectRecipe(refiner, "universe:portable_refining/iron_ingot");
        refiner.setItem(0, new ItemStack(Items.RAW_IRON, 2));
        refiner.setItem(refiner.fuelSlot(), new ItemStack(UniverseItems.PLANET_LOG.get()));
        helper.succeedWhen(() -> {
            helper.assertTrue(processor.getItem(3).is(UniverseItems.DISHES.get(Dish.BUTTER).get()), "营养处理器没有做出黄油");
            helper.assertTrue(processor.getItem(0).is(Items.BUCKET), "空桶没有留在原料格里：" + processor.getItem(0));
            helper.assertTrue(processor.getItem(processor.fuelSlot()).isEmpty(), "煤没有烧掉");
            helper.assertTrue(refiner.getItem(1).is(Items.IRON_INGOT), "便携精炼机没有炼出铁锭");
        });
    }

    private static void selectRecipe(RecipeMachineBlockEntity machine, String recipe) {
        CompoundTag settings = new CompoundTag();
        settings.putString("Recipe", recipe);
        machine.applyBlueprintSettings(settings);
    }

    /**
     * 飞船交易终端卖的四种船（ADR-0024）放成方块、装上自带的模块、组装起来：方块一个不多一个不少（石头地面没带上），
     * 类型和档次跟设计时一样，自带模块都生效，1 g 下推重比够。
     */
    private static void dealerShipsAssemble(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Set<ShipType> seen = EnumSet.noneOf(ShipType.class);
        for (long day = 0; day < 40 && seen.size() < ShipType.values().length; day++) {
            for (ShipMarket.Offer offer : ShipMarket.ships(11, Economy.Wealth.WEALTHY, day)) {
                if (!seen.add(offer.type())) {
                    continue;
                }
                ShipDesign design = offer.design();
                Map<BlockPos, BlockState> layout = ShipBlueprints.layout(design, helper.absolutePos(new BlockPos(8, 1, 8)), Direction.EAST);
                BlockPos helm = null;
                for (Map.Entry<BlockPos, BlockState> entry : layout.entrySet()) {
                    level.setBlock(entry.getKey(), entry.getValue(), 3);
                    if (entry.getValue().is(UniverseBlocks.SHIP_HELM.get())) {
                        helm = entry.getKey();
                    }
                }
                helper.assertTrue(helm != null, offer.type() + " 没有驾驶台");
                HelmBlockEntity tech = (HelmBlockEntity) level.getBlockEntity(helm);
                List<TechModule> modules = offer.modules();
                for (int i = 0; i < modules.size(); i++) {
                    ItemStack stack = new ItemStack(UniverseItems.MODULES.get(modules.get(i).type()).get());
                    stack.set(UniverseDataComponents.TECH_MODULE.get(), modules.get(i));
                    tech.items().set(i, stack);
                }
                tech.setChanged();
                ShipEntity ship = ShipAssembly.assemble(level, helm, null, message -> {
                });
                helper.assertTrue(ship != null && ship.structure() != null, offer.type() + " 组装不起来");
                helper.assertValueEqual(ship.structure().blocks().size(), design.cells().size(), offer.type() + " 的方块数");
                helper.assertValueEqual(ship.structure().rating().type(), design.rating().type(), offer.type() + " 的类型");
                helper.assertValueEqual(ship.structure().rating().grade(), design.rating().grade(), offer.type() + " 的档次");
                helper.assertValueEqual(ship.structure().tech().modules().size(), modules.size(), offer.type() + " 生效的模块");
                helper.assertTrue(ship.structure().stats().thrustToWeight(1) >= ShipDesigner.MIN_TWR, offer.type() + " 推重比不够");
                ship.discard();
            }
        }
        helper.assertValueEqual(seen.size(), ShipType.values().length, "四种船没凑齐");
        helper.succeed();
    }

    /** 舰队的存档（ADR-0024）：货船、护卫舰（受损的也记着）、在外面的远征、接过的任务，存了再读回来一样。 */
    private static void fleetSaveRoundTrip(GameTestHelper helper) {
        FleetData data = FleetData.CODEC.parse(NbtOps.INSTANCE, new CompoundTag()).getOrThrow();
        UUID owner = UUID.randomUUID();
        Fleet fleet = data.entry(owner).fleet();
        fleet.setFreighter(new Fleet.Freighter(data.takeFreighterId(), 99, Grade.A));
        Rng rng = Rng.of(1);
        for (int i = 0; i < 3; i++) {
            fleet.hire(Frigate.roll(i, rng));
        }
        fleet.frigate(1).setDamaged(true);
        Fleet.Voyage voyage = fleet.launch(5, 1, new Expedition.Offer(Expedition.Kind.TRADE, 2, 16), List.of(0, 2), 1234, Rng.of(2));
        Tag tag = FleetData.CODEC.encodeStart(NbtOps.INSTANCE, data).getOrThrow();
        FleetData back = FleetData.CODEC.parse(NbtOps.INSTANCE, tag).getOrThrow();
        Fleet read = back.find(owner).orElseThrow().fleet();
        helper.assertValueEqual(read.freighter(), fleet.freighter(), "货船");
        helper.assertValueEqual(read.frigates().size(), 3, "护卫舰数");
        helper.assertTrue(read.frigate(1).damaged(), "受损没记住");
        for (Frigate frigate : fleet.frigates()) {
            Frigate other = read.frigate(frigate.id());
            helper.assertTrue(other != null && other.total() == frigate.total() && other.traits().equals(frigate.traits())
                    && other.designation().equals(frigate.designation()), frigate.designation() + " 读回来不一样");
        }
        helper.assertValueEqual(read.voyages(), List.of(voyage), "远征");
        helper.assertTrue(read.taken(5, 1), "接过的任务");
        helper.assertValueEqual(read.nextFrigate(), 3, "下一个编号");
        helper.succeed();
    }

    /**
     * 远征回来（ADR-0024）：五艘强的打一星任务全胜，领了以后远征没了、护卫舰都出勤一次；各种任务、各个难度带回来的
     * 物品 ID 都认得出来（一样不丢），技术模块是飞船模块。
     */
    private static void fleetVoyageClaim(GameTestHelper helper) {
        Fleet fleet = new Fleet();
        fleet.setFreighter(new Fleet.Freighter(0, 7, Grade.C));
        List<Integer> team = new ArrayList<>();
        for (int i = 0; i < Expedition.MAX_TEAM; i++) {
            team.add(fleet.hire(new Frigate(0, "GT-10" + i, FrigateType.SUPPORT, new int[] {30, 30, 30, 30}, List.of(), 2, 0, false)).id());
        }
        Fleet.Voyage voyage = fleet.launch(0, 0, new Expedition.Offer(Expedition.Kind.INDUSTRIAL, 1, 10), team, 0, Rng.of(3));
        Fleet.Claim claim = fleet.claim(voyage);
        helper.assertTrue(fleet.voyages().isEmpty(), "远征还在");
        helper.assertTrue(voyage.events().stream().allMatch(Expedition.Event::success), "五艘强的没全胜");
        for (Frigate frigate : fleet.frigates()) {
            helper.assertValueEqual(frigate.expeditions(), 1, frigate.designation() + " 的出勤次数");
        }
        FleetTerminal.Haul haul = FleetTerminal.haul(voyage, claim.rewards());
        helper.assertTrue(haul.credits() >= 500, "全胜的信用点没给");
        // 各种任务、各个难度：每一样奖励都换得成东西
        for (Expedition.Kind kind : Expedition.Kind.values()) {
            for (int difficulty = 1; difficulty <= 5; difficulty++) {
                for (long seed = 0; seed < 8; seed++) {
                    Expedition.Offer offer = new Expedition.Offer(kind, difficulty, 10);
                    Rng rng = Rng.of(seed);
                    List<Expedition.Event> events = new ArrayList<>();
                    for (int i = 0; i < 2 + difficulty; i++) {
                        events.add(new Expedition.Event(rng.pick(io.github.sweetcornna.universe.core.fleet.Area.values()), true, -1,
                                Expedition.Outcome.NONE));
                    }
                    Fleet.Voyage trip = new Fleet.Voyage(0, 0, offer, team, 0, events, seed);
                    List<Expedition.Reward> rewards = Expedition.rewards(offer, events, rng);
                    int expected = rewards.stream().filter(r -> !r.key().equals(Expedition.CREDITS) && !r.key().equals(Expedition.GLYPH))
                            .mapToInt(Expedition.Reward::count).sum();
                    FleetTerminal.Haul got = FleetTerminal.haul(trip, rewards);
                    helper.assertValueEqual(got.count(), expected, kind + " " + difficulty + "★ 的奖励丢了东西");
                    for (ItemStack stack : got.items()) {
                        TechModule module = stack.get(UniverseDataComponents.TECH_MODULE.get());
                        helper.assertTrue(module == null || module.type().host() == io.github.sweetcornna.universe.core.tech.ModuleHost.SHIP,
                                "远征给了不是飞船的模块");
                    }
                }
            }
        }
        helper.succeed();
    }

    /** 坠毁的飞船（ADR-0024）：损坏的驾驶台组装不了；修好以后整艘船组装起来，1 g 下飞得起来。 */
    private static void crashedShipRepairs(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        long seed = 77;
        ShipDesign design = CrashedShip.design(seed);
        List<ShipDesign.Cell> wreck = CrashedShip.wreck(design, seed);
        ShipDesign broken = new ShipDesign(design.type(), design.grade(), design.primary(), design.accent(), wreck);
        BlockPos helm = null;
        for (Map.Entry<BlockPos, BlockState> entry : ShipBlueprints.layout(broken, helper.absolutePos(new BlockPos(8, 1, 8)), Direction.NORTH)
                .entrySet()) {
            BlockState state = entry.getValue();
            if (state.is(UniverseBlocks.SHIP_HELM.get())) {
                helm = entry.getKey();
                state = UniverseBlocks.DAMAGED_HELM.get().defaultBlockState()
                        .setValue(HorizontalDirectionalBlock.FACING, state.getValue(HorizontalDirectionalBlock.FACING));
            }
            level.setBlock(entry.getKey(), state, 3);
        }
        helper.assertTrue(helm != null, "没有驾驶台");
        helper.assertTrue(level.getBlockState(helm).is(UniverseBlocks.DAMAGED_HELM.get()), "驾驶台没坏");
        PoiInteractions.fixHelm(level, helm, level.getBlockState(helm));
        helper.assertTrue(level.getBlockState(helm).is(UniverseBlocks.SHIP_HELM.get()), "没修好");
        ShipEntity ship = ShipAssembly.assemble(level, helm, null, message -> {
        });
        helper.assertTrue(ship != null && ship.structure() != null, "修好的船组装不起来");
        helper.assertValueEqual(ship.structure().blocks().size(), wreck.size(), "方块数");
        helper.assertTrue(ship.structure().stats().canLift(1), "飞不起来");
        ship.discard();
        helper.succeed();
    }

    /** 先驱者传送门（ADR-0024）：石环上刻着这颗星球的地址；拨自己、拨错的地址都不开；拨别的星球开门，环里填满光，关门撤掉。 */
    private static void portalDialsAndCloses(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        BodyId home = Planets.bodyId(level.dimension()).orElseThrow();
        int[] own = Portals.address(server, home);
        Portals.Site site = new Portals.Site(helper.absolutePos(new BlockPos(6, 1, 5)), Direction.SOUTH);
        Portals.build(level, site, own);
        for (PortalLayout.Cell cell : PortalLayout.cells()) {
            BlockState state = level.getBlockState(site.at(cell.x(), cell.y(), cell.z()));
            switch (cell.part()) {
                case STONE -> helper.assertTrue(state.is(UniverseBlocks.PORTAL_STONE.get()), "石环和石台");
                case GLYPH -> helper.assertValueEqual(state.getValue(PortalGlyphBlock.GLYPH), own[cell.glyph()], "第 " + (cell.glyph() + 1) + " 个符文");
                case DIAL -> helper.assertTrue(state.is(UniverseBlocks.PORTAL_DIAL.get()), "拨号台");
                case FIELD -> helper.assertTrue(state.isAir(), "门关着，环里该是空的");
            }
        }
        helper.assertTrue(Portals.dial(level, site, own) != null, "拨自己不该开门");
        // 改一个符文，挑一个校验对不上的
        int[] wrong = own.clone();
        do {
            wrong[PortalAddress.LENGTH - 1] = (wrong[PortalAddress.LENGTH - 1] + 1) % PortalAddress.GLYPHS;
        } while (PortalAddress.decode(Planets.galaxy(server).seed(), wrong).isPresent());
        helper.assertTrue(Portals.dial(level, site, wrong) != null, "拨错的地址不该开门");
        BodyId target = null;
        for (StarSystem system : Planets.galaxy(server).systems()) {
            for (Planet planet : system.planets()) {
                if (target == null && planet.landable() && !planet.id().equals(home)) {
                    target = planet.id();
                }
            }
        }
        helper.assertTrue(target != null, "星团里没有别的能着陆的星球");
        helper.assertTrue(Portals.dial(level, site, Portals.address(server, target)) == null, "拨别的星球该开门");
        for (int[] c : PortalLayout.field()) {
            helper.assertTrue(level.getBlockState(site.at(c[0], c[1], c[2])).is(UniverseBlocks.PORTAL_FIELD.get()), "门开了，环里该有光");
        }
        Portals.close(level, site);
        for (int[] c : PortalLayout.field()) {
            helper.assertTrue(level.getBlockState(site.at(c[0], c[1], c[2])).isAir(), "门关了，光该撤掉");
        }
        helper.succeed();
    }

    /** 一艘最小的船：驾驶台在 (3, y, 3) 朝北，东西两边各一个推进器，后面一个燃料舱。返回驾驶台的位置。 */
    private static BlockPos smallShip(GameTestHelper helper, int y) {
        BlockPos helm = new BlockPos(3, y, 3);
        helper.setBlock(helm, UniverseBlocks.SHIP_HELM.get());
        helper.setBlock(new BlockPos(2, y, 3), UniverseBlocks.SHIP_THRUSTER.get());
        helper.setBlock(new BlockPos(4, y, 3), UniverseBlocks.SHIP_THRUSTER.get());
        helper.setBlock(new BlockPos(3, y, 4), UniverseBlocks.FUEL_TANK.get());
        return helm;
    }

    /** 场地西北角一座感应塔，东北角一台装了燃料棒的人造恒星，中间的设备都在塔的覆盖范围里。 */
    private static void powerPlant(GameTestHelper helper) {
        tall(helper, new BlockPos(1, 1, 1), UniverseBlocks.TESLA_TOWER.get().defaultBlockState());
        BlockPos star = new BlockPos(5, 1, 1);
        tall(helper, star, UniverseBlocks.ARTIFICIAL_STAR.get().defaultBlockState());
        helper.getBlockEntity(star, FuelGeneratorBlockEntity.class)
                .setItem(FuelGeneratorBlockEntity.FUEL_SLOT, new ItemStack(UniverseItems.ANTIMATTER_FUEL_ROD.get(), 4));
    }

    /** 一台选好齿轮配方、放了一组铁锭的一型制造台。 */
    private static BlockPos assembler(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, UniverseBlocks.ASSEMBLER.get());
        RecipeMachineBlockEntity assembler = helper.getBlockEntity(pos, RecipeMachineBlockEntity.class);
        CompoundTag settings = new CompoundTag();
        settings.putString("Recipe", GEAR_RECIPE);
        assembler.applyBlueprintSettings(settings);
        assembler.setItem(0, new ItemStack(Items.IRON_INGOT, 64));
        return pos;
    }

    private static void tall(GameTestHelper helper, BlockPos pos, BlockState state) {
        helper.setBlock(pos, state.setValue(TallMachineBlock.HALF, DoubleBlockHalf.LOWER));
        helper.setBlock(pos.above(), state.setValue(TallMachineBlock.HALF, DoubleBlockHalf.UPPER));
    }

    /** 把一个科技的前置（连同前置的前置）都记成已完成。 */
    private static void completeRequirements(ResearchState state, String id) {
        Tech tech = Research.TREE.get(id).orElseThrow();
        for (String required : tech.requires()) {
            if (!state.isDone(required)) {
                completeRequirements(state, required);
                state.complete(required);
            }
        }
    }
}
