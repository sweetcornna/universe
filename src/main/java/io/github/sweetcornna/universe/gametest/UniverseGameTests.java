package io.github.sweetcornna.universe.gametest;

import java.util.function.Consumer;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.core.factory.Cargo;
import io.github.sweetcornna.universe.core.factory.Proliferation;
import io.github.sweetcornna.universe.core.logistics.Station;
import io.github.sweetcornna.universe.core.logistics.StationSlot;
import io.github.sweetcornna.universe.core.research.ResearchState;
import io.github.sweetcornna.universe.core.research.Tech;
import io.github.sweetcornna.universe.core.research.TechTree;
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
import io.github.sweetcornna.universe.logistics.StationBlockEntity;
import io.github.sweetcornna.universe.logistics.StationHandler;
import io.github.sweetcornna.universe.registry.UniverseBlocks;
import io.github.sweetcornna.universe.registry.UniverseDataComponents;
import io.github.sweetcornna.universe.registry.UniverseItems;
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
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
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
    }

    private static void test(RegisterGameTestsEvent event, Holder<TestEnvironmentDefinition<?>> environment, ResourceKey<Consumer<GameTestHelper>> function,
            int maxTicks) {
        event.registerTest(function.identifier(), new FunctionGameTestInstance(function, new TestData<>(environment, PLATFORM, maxTicks, 0, true)));
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

    // ---- 搭场地

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
