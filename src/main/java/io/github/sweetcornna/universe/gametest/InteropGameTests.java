package io.github.sweetcornna.universe.gametest;

import io.github.sweetcornna.universe.core.power.GridInterface;
import io.github.sweetcornna.universe.factory.TallMachineBlock;
import io.github.sweetcornna.universe.factory.belt.StorageTankBlockEntity;
import io.github.sweetcornna.universe.factory.power.AccumulatorBlockEntity;
import io.github.sweetcornna.universe.factory.power.FuelGeneratorBlockEntity;
import io.github.sweetcornna.universe.factory.power.GridInterfaceBlockEntity;
import io.github.sweetcornna.universe.factory.resource.WaterPumpBlockEntity;
import io.github.sweetcornna.universe.registry.UniverseBlocks;
import io.github.sweetcornna.universe.registry.UniverseItems;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/** 和别的模组互通（ADR-0030 §三）的游戏内测试：电网接口的能量能力、储液罐和抽水站的流体能力。由 {@link UniverseGameTests} 登记。 */
final class InteropGameTests {
    private InteropGameTests() {
    }

    /**
     * 输出模式：电网接口从人造恒星那里取电，推给旁边的另一个电网接口（输入模式，NeoForge 的能量处理器）；
     * 外面按能量能力抽，一刻最多 2250。
     */
    static void outputFeedsNeighbor(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        tall(helper, new BlockPos(1, 1, 1), UniverseBlocks.TESLA_TOWER.get().defaultBlockState());
        BlockPos star = new BlockPos(5, 1, 1);
        tall(helper, star, UniverseBlocks.ARTIFICIAL_STAR.get().defaultBlockState());
        helper.getBlockEntity(star, FuelGeneratorBlockEntity.class)
                .setItem(FuelGeneratorBlockEntity.FUEL_SLOT, new ItemStack(UniverseItems.ANTIMATTER_FUEL_ROD.get(), 4));
        BlockPos outputPos = new BlockPos(3, 1, 4);
        BlockPos neighborPos = new BlockPos(3, 1, 5);
        helper.setBlock(outputPos, UniverseBlocks.GRID_INTERFACE.get());
        helper.setBlock(neighborPos, UniverseBlocks.GRID_INTERFACE.get());
        GridInterfaceBlockEntity output = helper.getBlockEntity(outputPos, GridInterfaceBlockEntity.class);
        GridInterfaceBlockEntity neighbor = helper.getBlockEntity(neighborPos, GridInterfaceBlockEntity.class);
        output.setOutput(true);
        helper.startSequence().thenWaitUntil(() -> {
            helper.assertTrue(output.connected(), "电网接口没接上电网");
            helper.assertTrue(neighbor.buffer() > 0, "旁边的接口没收到电：输出接口缓冲 " + output.buffer());
        }).thenExecute(() -> {
            EnergyHandler handler = level.getCapability(Capabilities.Energy.BLOCK, helper.absolutePos(outputPos), null);
            helper.assertTrue(handler != null, "电网接口没有能量能力");
            helper.assertTrue(handler.getCapacityAsLong() == GridInterface.BUFFER, "容量不对：" + handler.getCapacityAsLong());
            // 换成不推的模式之前先停住推送：把旁边的接口拆了，这一刻只剩测试在抽
            helper.setBlock(neighborPos, net.minecraft.world.level.block.Blocks.AIR);
        }).thenWaitUntil(() -> helper.assertTrue(output.buffer() >= GridInterface.RATE, "缓冲没攒满一刻的量：" + output.buffer()))
                .thenExecute(() -> {
                    EnergyHandler handler = level.getCapability(Capabilities.Energy.BLOCK, helper.absolutePos(outputPos), null);
                    int first;
                    int second;
                    try (Transaction transaction = Transaction.openRoot()) {
                        first = handler.extract(100_000, transaction);
                        second = handler.extract(100_000, transaction);
                        transaction.commit();
                    }
                    helper.assertTrue(first > 0 && first + second <= GridInterface.RATE, "一刻抽了 " + first + " + " + second);
                    try (Transaction transaction = Transaction.openRoot()) {
                        helper.assertTrue(handler.insert(1000, transaction) == 0, "输出模式收了电");
                    }
                }).thenSucceed();
    }

    /** 输入模式：从能量能力灌进来的电送进电网，充进蓄电器；一刻最多收 2250，事务回滚不留下电。 */
    static void inputFeedsGrid(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        tall(helper, new BlockPos(1, 1, 1), UniverseBlocks.TESLA_TOWER.get().defaultBlockState());
        BlockPos interfacePos = new BlockPos(3, 1, 4);
        BlockPos accumulatorPos = new BlockPos(5, 1, 4);
        helper.setBlock(interfacePos, UniverseBlocks.GRID_INTERFACE.get());
        helper.setBlock(accumulatorPos, UniverseBlocks.ACCUMULATOR.get());
        GridInterfaceBlockEntity device = helper.getBlockEntity(interfacePos, GridInterfaceBlockEntity.class);
        AccumulatorBlockEntity accumulator = helper.getBlockEntity(accumulatorPos, AccumulatorBlockEntity.class);
        helper.startSequence().thenExecuteAfter(2, () -> {
            EnergyHandler handler = level.getCapability(Capabilities.Energy.BLOCK, helper.absolutePos(interfacePos), null);
            helper.assertTrue(handler != null, "电网接口没有能量能力");
            try (Transaction transaction = Transaction.openRoot()) {
                helper.assertTrue(handler.insert(500, transaction) == 500, "没收进 500");
                // 不提交：回滚以后缓冲还是空的
            }
            helper.assertTrue(device.buffer() == 0, "回滚以后缓冲里还有 " + device.buffer());
            int first;
            int second;
            try (Transaction transaction = Transaction.openRoot()) {
                first = handler.insert(100_000, transaction);
                second = handler.insert(100_000, transaction);
                transaction.commit();
            }
            helper.assertTrue(first == GridInterface.RATE && second == 0, "一刻收了 " + first + " + " + second);
            try (Transaction transaction = Transaction.openRoot()) {
                helper.assertTrue(handler.extract(1000, transaction) == 0, "输入模式让抽");
            }
        }).thenWaitUntil(() -> {
            helper.assertTrue(accumulator.energy() > 0, "灌进来的电没送进电网：接口缓冲 " + device.buffer());
        }).thenSucceed();
    }

    /**
     * 储液罐的流体能力：灌 2500 mB 水变成两个水和 500 mB 零头，物品能力照样拿得出整的；抽完就空；
     * 别的流体（岩浆）灌不进。抽水站的产物格只出不进，不满一个先记着，够一个才拿走。
     */
    static void tankAndPumpFluids(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos tankPos = new BlockPos(2, 1, 2);
        helper.setBlock(tankPos, UniverseBlocks.STORAGE_TANK.get());
        StorageTankBlockEntity tank = helper.getBlockEntity(tankPos, StorageTankBlockEntity.class);
        BlockPos pumpPos = new BlockPos(4, 1, 4);
        helper.setBlock(pumpPos, UniverseBlocks.WATER_PUMP.get());
        WaterPumpBlockEntity pump = helper.getBlockEntity(pumpPos, WaterPumpBlockEntity.class);
        helper.startSequence().thenExecuteAfter(1, () -> {
            ResourceHandler<FluidResource> fluids = level.getCapability(Capabilities.Fluid.BLOCK, helper.absolutePos(tankPos), null);
            helper.assertTrue(fluids != null, "储液罐没有流体能力");
            FluidResource water = FluidResource.of(Fluids.WATER);
            try (Transaction transaction = Transaction.openRoot()) {
                helper.assertTrue(fluids.insert(water, 2500, transaction) == 2500, "2500 mB 水没灌进去");
                helper.assertTrue(fluids.insert(FluidResource.of(Fluids.LAVA), 1000, transaction) == 0, "岩浆灌进去了");
                transaction.commit();
            }
            helper.assertTrue(tank.fluid() == UniverseItems.WATER.get() && tank.amount() == 2 && tank.extra() == 500,
                    "罐里是 " + tank.fluid() + " ×" + tank.amount() + " + " + tank.extra() + " mB");
            helper.assertTrue(fluids.getResource(0).equals(water) && fluids.getAmountAsLong(0) == 2500, "流体能力读出来不对");
            ResourceHandler<ItemResource> items = level.getCapability(Capabilities.Item.BLOCK, helper.absolutePos(tankPos), null);
            try (Transaction transaction = Transaction.openRoot()) {
                helper.assertTrue(items.extract(ItemResource.of(UniverseItems.WATER.get()), 1, transaction) == 1, "物品能力拿不出一个水");
                transaction.commit();
            }
            try (Transaction transaction = Transaction.openRoot()) {
                helper.assertTrue(fluids.extract(water, 5000, transaction) == 1500, "剩下的不是 1500 mB");
                transaction.commit();
            }
            helper.assertTrue(tank.fluid() == null && tank.amount() == 0 && tank.extra() == 0, "抽完没空");

            pump.setItem(WaterPumpBlockEntity.OUTPUT, new ItemStack(UniverseItems.WATER.get(), 3));
            ResourceHandler<FluidResource> pumped = level.getCapability(Capabilities.Fluid.BLOCK, helper.absolutePos(pumpPos), null);
            helper.assertTrue(pumped != null, "抽水站没有流体能力");
            helper.assertTrue(pumped.getAmountAsLong(0) == 3000, "抽水站读出 " + pumped.getAmountAsLong(0) + " mB");
            try (Transaction transaction = Transaction.openRoot()) {
                helper.assertTrue(pumped.insert(water, 1000, transaction) == 0, "抽水站收了水");
                helper.assertTrue(pumped.extract(water, 250, transaction) == 250, "抽不出 250 mB");
                transaction.commit();
            }
            helper.assertTrue(pump.getItem(WaterPumpBlockEntity.OUTPUT).getCount() == 3, "不满一个就拿走了物品");
            try (Transaction transaction = Transaction.openRoot()) {
                helper.assertTrue(pumped.extract(water, 800, transaction) == 800, "抽不出 800 mB");
                transaction.commit();
            }
            helper.assertTrue(pump.getItem(WaterPumpBlockEntity.OUTPUT).getCount() == 2, "攒够一个没拿走：还剩 "
                    + pump.getItem(WaterPumpBlockEntity.OUTPUT).getCount());
            helper.assertTrue(pumped.getAmountAsLong(0) == 1950, "抽水站剩 " + pumped.getAmountAsLong(0) + " mB");
        }).thenSucceed();
    }

    private static void tall(GameTestHelper helper, BlockPos pos, BlockState state) {
        helper.setBlock(pos, state.setValue(TallMachineBlock.HALF, DoubleBlockHalf.LOWER));
        helper.setBlock(pos.above(), state.setValue(TallMachineBlock.HALF, DoubleBlockHalf.UPPER));
    }
}
