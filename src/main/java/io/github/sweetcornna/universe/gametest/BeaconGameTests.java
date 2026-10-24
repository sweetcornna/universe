package io.github.sweetcornna.universe.gametest;

import java.util.List;
import java.util.Map;

import io.github.sweetcornna.universe.beacon.HoloBeaconBlockEntity;
import io.github.sweetcornna.universe.beacon.HoloBeacons;
import io.github.sweetcornna.universe.blueprint.BlueprintData;
import io.github.sweetcornna.universe.blueprint.Blueprints;
import io.github.sweetcornna.universe.core.beacon.BeaconVisibility;
import io.github.sweetcornna.universe.core.beacon.Memo;
import io.github.sweetcornna.universe.core.blueprint.BlueprintGeometry;
import io.github.sweetcornna.universe.core.dyson.DysonSphere;
import io.github.sweetcornna.universe.core.dyson.RayReceiver;
import io.github.sweetcornna.universe.core.dyson.SwarmOrbit;
import io.github.sweetcornna.universe.core.factory.VeinWarnings;
import io.github.sweetcornna.universe.core.galaxy.GalaxyGenerator;
import io.github.sweetcornna.universe.dyson.Dyson;
import io.github.sweetcornna.universe.dyson.RayReceiverBlockEntity;
import io.github.sweetcornna.universe.factory.TallMachineBlock;
import io.github.sweetcornna.universe.factory.power.FuelGeneratorBlockEntity;
import io.github.sweetcornna.universe.factory.resource.MiningMachineBlockEntity;
import io.github.sweetcornna.universe.factory.resource.VeinAlerts;
import io.github.sweetcornna.universe.factory.resource.VeinNodes;
import io.github.sweetcornna.universe.registry.UniverseBlocks;
import io.github.sweetcornna.universe.registry.UniverseItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.clock.WorldClock;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.storage.TagValueInput;

/**
 * 全息信标、黑雾透镜、矿脉枯竭提醒和蓝图（ADR-0033 §六–§九）的游戏内测试。由 {@link UniverseGameTests} 登记。
 */
final class BeaconGameTests {
    private BeaconGameTests() {
    }

    /**
     * 黑雾透镜：半夜看不见恒星，放一枚黑雾透镜进去照样接收；透镜被吃掉，接收站记的是黑雾透镜，
     * 功率上限是不装透镜的两倍，损耗比引力透镜少一半。
     */
    static void darkFogLensPowerAndLoss(GameTestHelper helper) {
        BlockPos towerPos = new BlockPos(1, 1, 1);
        BlockPos starPos = new BlockPos(5, 1, 1);
        BlockPos receiverPos = new BlockPos(3, 1, 4);
        tall(helper, towerPos, UniverseBlocks.TESLA_TOWER.get().defaultBlockState());
        tall(helper, starPos, UniverseBlocks.ARTIFICIAL_STAR.get().defaultBlockState());
        helper.getBlockEntity(starPos, FuelGeneratorBlockEntity.class)
                .setItem(FuelGeneratorBlockEntity.FUEL_SLOT, new ItemStack(UniverseItems.ANTIMATTER_FUEL_ROD.get(), 4));
        helper.setBlock(receiverPos, UniverseBlocks.RAY_RECEIVER.get());
        DysonSphere sphere = Dyson.sphere(helper.getLevel().getServer(), GalaxyGenerator.HOME_SYSTEM);
        long now = helper.getLevel().getGameTime();
        for (SwarmOrbit orbit : sphere.orbits()) {
            sphere.addSails(orbit.id(), 2000, now, 10_000_000);
            break;
        }
        setMidnight(helper);
        RayReceiverBlockEntity receiver = helper.getBlockEntity(receiverPos, RayReceiverBlockEntity.class);
        ItemStack lens = new ItemStack(UniverseItems.DARK_FOG_LENS.get(), 1);
        helper.assertTrue(receiver.canPlaceItem(RayReceiverBlockEntity.LENS_SLOT, lens), "接收站不收黑雾透镜");
        helper.assertValueEqual(RayReceiverBlockEntity.lensOf(lens), RayReceiver.Lens.DARK_FOG, "认出来的透镜");
        receiver.setItem(RayReceiverBlockEntity.LENS_SLOT, lens);
        helper.startSequence().thenWaitUntil(() -> helper.assertTrue(status(receiver) == RayReceiverBlockEntity.Status.RECEIVING,
                "装了黑雾透镜还不接收：" + status(receiver)))
                .thenExecuteAfter(5, () -> {
                    helper.assertTrue(receiver.getItem(RayReceiverBlockEntity.LENS_SLOT).isEmpty(), "透镜没被吃掉");
                    helper.assertValueEqual(receiver.lens(), RayReceiver.Lens.DARK_FOG, "正在用的透镜");
                    helper.assertValueEqual(receiver.data().get(RayReceiverBlockEntity.LENS_KIND), RayReceiver.Lens.DARK_FOG.ordinal(),
                            "界面上的透镜种类");
                    helper.assertTrue(receiver.data().get(RayReceiverBlockEntity.LENS) > 0, "透镜的剩余时间没在走");
                    double c = receiver.data().get(RayReceiverBlockEntity.CONTINUITY) / 1000.0;
                    helper.assertTrue(Math.abs(RayReceiver.maxOutput(c, RayReceiver.Lens.DARK_FOG) - 2 * RayReceiver.maxOutput(c, RayReceiver.Lens.NONE)) < 1,
                            "黑雾透镜的功率上限不是两倍");
                    double fog = 1 - RayReceiver.efficiency(c, RayReceiver.Lens.DARK_FOG, 1);
                    double graviton = 1 - RayReceiver.efficiency(c, RayReceiver.Lens.GRAVITON, 1);
                    helper.assertTrue(Math.abs(fog - graviton / 2) < 1e-9, "黑雾透镜的损耗不是引力透镜的一半：" + fog + " / " + graviton);
                }).thenSucceed();
    }

    /**
     * 矿脉枯竭：节点采完变回石头；提醒按采矿机的设置发，同一台 5 分钟内只提醒一次；不提醒的不发，
     * 默认的"全部采完"只在一个矿点都不剩时发。收到提醒的是同一维度的玩家。
     */
    static void veinAlertOnceThenQuiet(GameTestHelper helper) {
        BlockPos orePos = new BlockPos(6, 1, 6);
        BlockPos nodeMachinePos = new BlockPos(1, 1, 1);
        BlockPos quietMachinePos = new BlockPos(3, 1, 1);
        BlockPos allMachinePos = new BlockPos(5, 1, 1);
        helper.setBlock(orePos, Blocks.IRON_ORE);
        helper.setBlock(nodeMachinePos, UniverseBlocks.MINING_MACHINE.get());
        helper.setBlock(quietMachinePos, UniverseBlocks.MINING_MACHINE.get());
        helper.setBlock(allMachinePos, UniverseBlocks.MINING_MACHINE.get());
        ServerLevel level = helper.getLevel();
        BlockPos ore = helper.absolutePos(orePos);
        VeinNodes.Node node = VeinNodes.node(level.getBlockState(ore));
        helper.assertTrue(node != null, "铁矿石不是矿脉节点");
        VeinNodes.drain(level, ore, node, 1);
        helper.assertTrue(VeinNodes.extract(level, ore, node), "只剩一个的节点采一次没采完");
        helper.assertTrue(level.getBlockState(ore).is(node.depleted().getBlock()), "采完的节点没变回石头");

        MiningMachineBlockEntity nodeMachine = helper.getBlockEntity(nodeMachinePos, MiningMachineBlockEntity.class);
        helper.assertValueEqual(nodeMachine.warning(), VeinWarnings.DEFAULT, "新放的采矿机的提醒设置");
        nodeMachine.setWarning(VeinWarnings.Mode.NODE);
        helper.assertTrue(nodeMachine.depleted(level, node.item(), false), "一个矿点采完时没提醒");
        helper.assertTrue(!nodeMachine.depleted(level, node.item(), false), "5 分钟内又提醒了一次");
        helper.assertTrue(!nodeMachine.depleted(level, node.item(), true), "5 分钟内全部采完也不该再提醒");

        MiningMachineBlockEntity quiet = helper.getBlockEntity(quietMachinePos, MiningMachineBlockEntity.class);
        quiet.setWarning(VeinWarnings.Mode.NONE);
        helper.assertTrue(!quiet.depleted(level, node.item(), true), "设了不提醒还在提醒");

        MiningMachineBlockEntity all = helper.getBlockEntity(allMachinePos, MiningMachineBlockEntity.class);
        helper.assertTrue(!all.depleted(level, node.item(), false), "还有别的矿点时就提醒了全部采完");
        helper.assertTrue(all.depleted(level, node.item(), true), "全部采完时没提醒");

        helper.assertValueEqual(VeinAlerts.recipients(level), List.copyOf(level.players()), "收提醒的人");
        helper.assertTrue(VeinAlerts.message(ore, UniverseBlocks.MINING_MACHINE.get(), node.item(), false)
                .getContents() instanceof TranslatableContents contents && contents.getKey().equals("message.universe.vein_alert.node"),
                "提醒的文字不对");
        helper.succeed();
    }

    /** 只贴设备：一层石头地板上摆着分流器和传送带，只贴设备时地板跳过，设备照放。 */
    static void devicesOnlySkipsFloor(GameTestHelper helper) {
        BlockState stone = Blocks.STONE.defaultBlockState();
        BlockState splitter = UniverseBlocks.SPLITTER.get().defaultBlockState();
        BlockState belt = UniverseBlocks.CONVEYOR_BELT.get().defaultBlockState();
        helper.assertTrue(!Blueprints.device(stone), "石头算成了设备");
        helper.assertTrue(Blueprints.device(splitter), "分流器不算设备");
        helper.assertTrue(Blueprints.device(belt), "传送带不算设备（标签 universe:blueprint_devices）");
        int[] positions = {
                BlueprintGeometry.pack(0, 0, 0), BlueprintGeometry.pack(1, 0, 0), BlueprintGeometry.pack(2, 0, 0),
                BlueprintGeometry.pack(0, 1, 0), BlueprintGeometry.pack(1, 1, 0)};
        int[] states = {0, 0, 0, 1, 2};
        BlueprintData.Stored stored = new BlueprintData.Stored("floor", 3, 2, 1, List.of(stone, splitter, belt), positions, states, Map.of(),
                BlueprintData.Meta.NONE);
        BlockPos anchor = helper.absolutePos(new BlockPos(2, 1, 2));
        List<Blueprints.Placement> everything = Blueprints.placements(stored, anchor, 0, false);
        List<Blueprints.Placement> devices = Blueprints.placements(stored, anchor, 0, true);
        helper.assertValueEqual(everything.size(), 5, "整张蓝图的方块数");
        helper.assertValueEqual(devices.size(), 2, "只贴设备时的方块数");
        helper.assertTrue(devices.stream().noneMatch(p -> p.state().is(Blocks.STONE)), "只贴设备时还放了地板");
        helper.assertTrue(devices.stream().anyMatch(p -> p.state().is(UniverseBlocks.SPLITTER.get())), "只贴设备时漏了分流器");
        helper.succeed();
    }

    /** 同名的蓝图再存一次版本加 1，别的名字从第 1 版开始；作者和说明存得住。 */
    static void sameNameBumpsVersion(GameTestHelper helper) {
        BlueprintData data = BlueprintData.get(helper.getLevel().getServer());
        String name = "gametest-" + helper.getLevel().getGameTime() + "-" + helper.absolutePos(BlockPos.ZERO).asLong();
        BlueprintData.Stored stored = new BlueprintData.Stored(name, 1, 1, 1, List.of(Blocks.STONE.defaultBlockState()),
                new int[] {BlueprintGeometry.pack(0, 0, 0)}, new int[] {0}, Map.of(), BlueprintData.Meta.NONE);
        helper.assertValueEqual(data.nextVersion(name), 1, "没存过的名字");
        int first = data.add(stored.withMeta(new BlueprintData.Meta("Dev", data.nextVersion(name), "一号产线")));
        helper.assertValueEqual(data.nextVersion(name), 2, "存过一次以后");
        int second = data.add(stored.withMeta(new BlueprintData.Meta("Dev", data.nextVersion(name), "")));
        helper.assertValueEqual(data.get(second).meta().version(), 2, "第二次存的版本");
        helper.assertValueEqual(data.nextVersion(name), 3, "存过两次以后");
        helper.assertValueEqual(data.nextVersion(name + "-other"), 1, "别的名字");
        helper.assertValueEqual(data.get(first).meta().author(), "Dev", "作者");
        helper.assertValueEqual(data.get(first).meta().description(), "一号产线", "说明");
        helper.succeed();
    }

    /** 信标的设置：存档再读回来都在；登记表里有它，拆掉以后登记表里也没了。 */
    static void beaconSettingsPersist(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 1, 2);
        helper.setBlock(pos, UniverseBlocks.HOLO_BEACON.get());
        ServerLevel level = helper.getLevel();
        BlockPos absolute = helper.absolutePos(pos);
        HoloBeaconBlockEntity beacon = helper.getBlockEntity(pos, HoloBeaconBlockEntity.class);
        Memo memo = Memo.clean(List.of(new Memo.Line("铺好传送带", true), new Memo.Line("等硅矿", false)));
        beacon.apply("前哨", "minecraft:diamond", 3, BeaconVisibility.CLUSTER, memo);

        CompoundTag saved = beacon.saveCustomOnly(level.registryAccess());
        HoloBeaconBlockEntity copy = new HoloBeaconBlockEntity(absolute, level.getBlockState(absolute));
        copy.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), saved));
        helper.assertValueEqual(copy.label(), "前哨", "名字");
        helper.assertValueEqual(copy.icon(), "minecraft:diamond", "图标");
        helper.assertValueEqual(copy.iconItem(), Items.DIAMOND, "图标物品");
        helper.assertValueEqual(copy.color(), 3, "颜色");
        helper.assertValueEqual(copy.visibility(), BeaconVisibility.CLUSTER, "范围");
        helper.assertValueEqual(copy.memo(), memo, "备忘");

        HoloBeacons beacons = HoloBeacons.get(level.getServer());
        HoloBeacons.Beacon entry = beacons.at(level.dimension(), absolute).orElse(null);
        helper.assertTrue(entry != null, "登记表里没有这座信标");
        helper.assertValueEqual(entry.label(), "前哨", "登记表里的名字");
        helper.assertValueEqual(entry.visibility(), BeaconVisibility.CLUSTER, "登记表里的范围");
        helper.setBlock(pos, Blocks.AIR);
        helper.assertTrue(beacons.at(level.dimension(), absolute).isEmpty(), "拆掉以后登记表里还有");
        helper.succeed();
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

    private static void tall(GameTestHelper helper, BlockPos pos, BlockState state) {
        helper.setBlock(pos, state.setValue(TallMachineBlock.HALF, DoubleBlockHalf.LOWER));
        helper.setBlock(pos.above(), state.setValue(TallMachineBlock.HALF, DoubleBlockHalf.UPPER));
    }
}
