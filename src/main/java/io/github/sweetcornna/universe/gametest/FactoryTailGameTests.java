package io.github.sweetcornna.universe.gametest;

import io.github.sweetcornna.universe.blueprint.BlueprintSettings;
import io.github.sweetcornna.universe.core.dyson.DysonSphere;
import io.github.sweetcornna.universe.core.dyson.SwarmOrbit;
import io.github.sweetcornna.universe.core.factory.Proliferation;
import io.github.sweetcornna.universe.core.galaxy.GalaxyGenerator;
import io.github.sweetcornna.universe.core.logistics.Logic;
import io.github.sweetcornna.universe.core.logistics.Station;
import io.github.sweetcornna.universe.core.logistics.StationSlot;
import io.github.sweetcornna.universe.dyson.Dyson;
import io.github.sweetcornna.universe.dyson.RayReceiverBlockEntity;
import io.github.sweetcornna.universe.factory.TallMachineBlock;
import io.github.sweetcornna.universe.factory.belt.SplitterBlockEntity;
import io.github.sweetcornna.universe.factory.belt.TrafficMonitorBlockEntity;
import io.github.sweetcornna.universe.factory.power.FuelGeneratorBlockEntity;
import io.github.sweetcornna.universe.factory.research.MatrixLabBlockEntity;
import io.github.sweetcornna.universe.logistics.StationBlockEntity;
import io.github.sweetcornna.universe.registry.UniverseBlocks;
import io.github.sweetcornna.universe.registry.UniverseItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.clock.WorldClock;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/**
 * 工厂和物流的尾巴（ADR-0030 §五）的游戏内测试：漏斗从物流站抽货、比较器读物流站、装了透镜的射线接收站在看不见恒星时
 * 照样接收，还有蓝图把分流器、流速监测器、研究站的设置带回来。由 {@link UniverseGameTests} 登记。
 */
final class FactoryTailGameTests {
    private FactoryTailGameTests() {
    }

    /** 物流站下面的漏斗把站里的铁锭抽进箱子，账本里的存量跟着少。 */
    static void hopperPullsFromStation(GameTestHelper helper) {
        BlockPos stationPos = new BlockPos(3, 2, 3);
        BlockPos hopperPos = stationPos.below();
        BlockPos chestPos = hopperPos.below();
        helper.setBlock(chestPos, Blocks.CHEST);
        helper.setBlock(hopperPos, Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.DOWN));
        tall(helper, stationPos, UniverseBlocks.PLANETARY_LOGISTICS_STATION.get().defaultBlockState());
        helper.startSequence().thenExecuteAfter(1, () -> {
            StationBlockEntity entity = helper.getBlockEntity(stationPos, StationBlockEntity.class);
            Station station = entity.station();
            helper.assertTrue(station != null, "账本里没有这座站");
            StationSlot slot = station.slot(0);
            helper.assertTrue(slot.setItem("minecraft:iron_ingot"), "设不了物品");
            slot.setMax(1000);
            slot.setLocal(Logic.SUPPLY);
            station.insert("minecraft:iron_ingot", 12, 0);
            helper.assertValueEqual(slot.count(), 12, "放进站里的铁锭");
        }).thenWaitUntil(() -> {
            // 漏斗抽上来的、已经送进箱子的都算
            int pulled = countIron(helper.getBlockEntity(hopperPos, HopperBlockEntity.class))
                    + countIron(helper.getBlockEntity(chestPos, ChestBlockEntity.class));
            helper.assertTrue(pulled > 0, "漏斗没从物流站抽到铁锭");
        }).thenExecute(() -> {
            StationSlot slot = helper.getBlockEntity(stationPos, StationBlockEntity.class).station().slot(0);
            helper.assertTrue(slot.count() < 12, "站里的铁锭没少：还有 " + slot.count());
        }).thenSucceed();
    }

    private static int countIron(net.minecraft.world.Container container) {
        int total = 0;
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.getItem(i);
            if (stack.is(Items.IRON_INGOT)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    /** 比较器按账本里的存量出信号：空站 0，装一半居中，装满满格。 */
    static void comparatorReadsStation(GameTestHelper helper) {
        BlockPos stationPos = new BlockPos(3, 1, 3);
        tall(helper, stationPos, UniverseBlocks.PLANETARY_LOGISTICS_STATION.get().defaultBlockState());
        helper.startSequence().thenExecuteAfter(1, () -> {
            StationBlockEntity entity = helper.getBlockEntity(stationPos, StationBlockEntity.class);
            Station station = entity.station();
            helper.assertTrue(station != null, "账本里没有这座站");
            helper.assertValueEqual(entity.analogSignal(), 0, "空站的比较器信号");
            StationSlot slot = station.slot(0);
            helper.assertTrue(slot.setItem("minecraft:iron_ingot"), "设不了物品");
            slot.setMax(100);
            station.insert("minecraft:iron_ingot", 50, 0);
            int half = entity.analogSignal();
            helper.assertTrue(half > 0 && half < 15, "装一半的信号是 " + half);
            station.insert("minecraft:iron_ingot", 50, 0);
            helper.assertValueEqual(entity.analogSignal(), 15, "装满的比较器信号");
        }).thenSucceed();
    }

    /**
     * 射线接收站：主世界入夜、看不见恒星时停摆；放一枚引力透镜进去，有大气就照样接收（ADR-0016），透镜被吃掉、
     * 开始计时。
     */
    static void lensReceivesWithoutSun(GameTestHelper helper) {
        BlockPos towerPos = new BlockPos(1, 1, 1);
        BlockPos starPos = new BlockPos(5, 1, 1);
        BlockPos receiverPos = new BlockPos(3, 1, 4);
        tall(helper, towerPos, UniverseBlocks.TESLA_TOWER.get().defaultBlockState());
        tall(helper, starPos, UniverseBlocks.ARTIFICIAL_STAR.get().defaultBlockState());
        helper.getBlockEntity(starPos, FuelGeneratorBlockEntity.class)
                .setItem(FuelGeneratorBlockEntity.FUEL_SLOT, new ItemStack(UniverseItems.ANTIMATTER_FUEL_ROD.get(), 4));
        helper.setBlock(receiverPos, UniverseBlocks.RAY_RECEIVER.get());
        // 戴森云里放足够的帆，接收站有电可拿
        DysonSphere sphere = Dyson.sphere(helper.getLevel().getServer(), GalaxyGenerator.HOME_SYSTEM);
        long now = helper.getLevel().getGameTime();
        for (SwarmOrbit orbit : sphere.orbits()) {
            sphere.addSails(orbit.id(), 2000, now, 10_000_000);
            break;
        }
        // 半夜：天上看得见，但恒星在地平线下
        setMidnight(helper);
        RayReceiverBlockEntity receiver = helper.getBlockEntity(receiverPos, RayReceiverBlockEntity.class);
        helper.startSequence().thenExecuteAfter(25, () -> {
            helper.assertTrue(sphere.generation() > 0, "戴森球没有功率");
            helper.assertTrue(status(receiver) != RayReceiverBlockEntity.Status.RECEIVING,
                    "没有透镜、看不见恒星时还在接收：" + status(receiver));
            receiver.setItem(RayReceiverBlockEntity.LENS_SLOT, new ItemStack(UniverseItems.GRAVITON_LENS.get(), 1));
        }).thenWaitUntil(() -> helper.assertTrue(status(receiver) == RayReceiverBlockEntity.Status.RECEIVING,
                "装了透镜还不接收：" + status(receiver)))
                .thenExecuteAfter(5, () -> helper.assertTrue(receiver.getItem(RayReceiverBlockEntity.LENS_SLOT).isEmpty(), "透镜没被吃掉"))
                .thenSucceed();
    }

    /** 把这个维度的时钟拨到半夜（26.3 的时间在 {@code ServerClockManager} 里，见 TimeCommand）。 */
    private static void setMidnight(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Holder<WorldClock> clock = level.dimensionTypeRegistration().value().defaultClock().orElse(null);
        helper.assertTrue(clock != null, "这个维度没有默认时钟");
        level.clockManager().setTotalTicks(clock, 18000);
    }

    private static RayReceiverBlockEntity.Status status(RayReceiverBlockEntity receiver) {
        return RayReceiverBlockEntity.Status.values()[receiver.data().get(RayReceiverBlockEntity.STATUS)];
    }

    /**
     * 蓝图带回设置（ADR-0030 §五）：分流器的优先出口和过滤、流速监测器的下限和过滤、研究站的增产模式，存出来再用到
     * 新放下的方块上都还在；优先出口跟着蓝图转 90°。
     */
    static void blueprintKeepsSettings(GameTestHelper helper) {
        BlockPos splitterPos = new BlockPos(2, 1, 2);
        BlockPos copiedSplitterPos = new BlockPos(2, 1, 4);
        BlockPos monitorPos = new BlockPos(4, 1, 2);
        BlockPos copiedMonitorPos = new BlockPos(4, 1, 4);
        BlockPos labPos = new BlockPos(6, 1, 2);
        BlockPos copiedLabPos = new BlockPos(6, 1, 4);
        helper.setBlock(splitterPos, UniverseBlocks.SPLITTER.get());
        helper.setBlock(copiedSplitterPos, UniverseBlocks.SPLITTER.get());
        helper.setBlock(monitorPos, UniverseBlocks.TRAFFIC_MONITOR.get());
        helper.setBlock(copiedMonitorPos, UniverseBlocks.TRAFFIC_MONITOR.get());
        helper.setBlock(labPos, UniverseBlocks.MATRIX_LAB.get());
        helper.setBlock(copiedLabPos, UniverseBlocks.MATRIX_LAB.get());
        helper.startSequence().thenExecuteAfter(1, () -> {
            SplitterBlockEntity splitter = helper.getBlockEntity(splitterPos, SplitterBlockEntity.class);
            splitter.togglePriority(Direction.NORTH);
            splitter.setFilter(Items.IRON_INGOT);
            CompoundTag settings = splitter.blueprintSettings();
            helper.assertTrue(settings != null, "分流器没存出设置");
            // 蓝图转 90°：北变成东
            SplitterBlockEntity copiedSplitter = helper.getBlockEntity(copiedSplitterPos, SplitterBlockEntity.class);
            copiedSplitter.applyBlueprintSettings(BlueprintSettings.rotated(settings, 1));
            helper.assertValueEqual(copiedSplitter.priority(), Direction.EAST, "转过以后的优先出口");
            helper.assertValueEqual(copiedSplitter.filter(), Items.IRON_INGOT, "带回来的过滤物品");

            TrafficMonitorBlockEntity monitor = helper.getBlockEntity(monitorPos, TrafficMonitorBlockEntity.class);
            CompoundTag seed = new CompoundTag();
            seed.putInt("Threshold", 120);
            seed.putString("Filter", "minecraft:copper_ingot");
            monitor.applyBlueprintSettings(seed);
            CompoundTag monitorSettings = monitor.blueprintSettings();
            helper.assertTrue(monitorSettings != null, "流速监测器没存出设置");
            TrafficMonitorBlockEntity copiedMonitor = helper.getBlockEntity(copiedMonitorPos, TrafficMonitorBlockEntity.class);
            copiedMonitor.applyBlueprintSettings(monitorSettings);
            helper.assertValueEqual(copiedMonitor.threshold(), 120, "带回来的下限");
            helper.assertValueEqual(copiedMonitor.filter(), Items.COPPER_INGOT, "带回来的只数的物品");

            MatrixLabBlockEntity lab = helper.getBlockEntity(labPos, MatrixLabBlockEntity.class);
            CompoundTag speedup = new CompoundTag();
            speedup.putBoolean("Speedup", true);
            lab.applyBlueprintSettings(speedup);
            helper.assertValueEqual(lab.mode(), Proliferation.Mode.SPEEDUP, "研究站的增产模式");
            MatrixLabBlockEntity copiedLab = helper.getBlockEntity(copiedLabPos, MatrixLabBlockEntity.class);
            copiedLab.applyBlueprintSettings(lab.blueprintSettings());
            helper.assertValueEqual(copiedLab.mode(), Proliferation.Mode.SPEEDUP, "带回来的增产模式");
        }).thenSucceed();
    }

    private static void tall(GameTestHelper helper, BlockPos pos, BlockState state) {
        helper.setBlock(pos, state.setValue(TallMachineBlock.HALF, DoubleBlockHalf.LOWER));
        helper.setBlock(pos.above(), state.setValue(TallMachineBlock.HALF, DoubleBlockHalf.UPPER));
    }
}
