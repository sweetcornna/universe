package io.github.sweetcornna.universe.gametest;

import java.util.function.Consumer;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.core.research.ResearchState;
import io.github.sweetcornna.universe.core.research.Tech;
import io.github.sweetcornna.universe.core.research.TechTree;
import io.github.sweetcornna.universe.factory.RecipeMachineBlockEntity;
import io.github.sweetcornna.universe.factory.TallMachineBlock;
import io.github.sweetcornna.universe.factory.belt.BeltBlock;
import io.github.sweetcornna.universe.factory.belt.BeltNetwork;
import io.github.sweetcornna.universe.factory.belt.StorageDepotBlockEntity;
import io.github.sweetcornna.universe.factory.power.FuelGeneratorBlockEntity;
import io.github.sweetcornna.universe.factory.research.MatrixLabBlockEntity;
import io.github.sweetcornna.universe.factory.research.Research;
import io.github.sweetcornna.universe.factory.research.ResearchData;
import io.github.sweetcornna.universe.registry.UniverseBlocks;
import io.github.sweetcornna.universe.registry.UniverseItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.gametest.GameTestHooks;
import net.neoforged.neoforge.registries.DeferredRegister;

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
