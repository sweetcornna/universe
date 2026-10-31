package io.github.sweetcornna.universe.gametest;

import java.util.ArrayList;
import java.util.List;

import io.netty.channel.ChannelFutureListener;
import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.beacon.HoloBeaconBlockEntity;
import io.github.sweetcornna.universe.beacon.HoloBeacons;
import io.github.sweetcornna.universe.core.beacon.BeaconVisibility;
import io.github.sweetcornna.universe.core.beacon.Memo;
import io.github.sweetcornna.universe.core.hazard.GeyserKind;
import io.github.sweetcornna.universe.core.mecha.fleet.Squadrons;
import io.github.sweetcornna.universe.core.stats.ProductionStats;
import io.github.sweetcornna.universe.core.stats.ProductionStats.Range;
import io.github.sweetcornna.universe.core.survival.ToolMode;
import io.github.sweetcornna.universe.core.survival.ToolWeapon;
import io.github.sweetcornna.universe.deepspace.Structures;
import io.github.sweetcornna.universe.gravity.GravityCoils;
import io.github.sweetcornna.universe.gravity.GravityPayloads;
import io.github.sweetcornna.universe.hazard.GeyserVentBlockEntity;
import io.github.sweetcornna.universe.mecha.fleet.FleetData;
import io.github.sweetcornna.universe.mecha.fleet.FleetUnit;
import io.github.sweetcornna.universe.mecha.fleet.MechaFleet;
import io.github.sweetcornna.universe.registry.UniverseAttachments;
import io.github.sweetcornna.universe.registry.UniverseBlocks;
import io.github.sweetcornna.universe.registry.UniverseDataComponents;
import io.github.sweetcornna.universe.registry.UniverseGameRules;
import io.github.sweetcornna.universe.registry.UniverseItems;
import io.github.sweetcornna.universe.stats.ProductionData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/** 1.0.0 发布前审查修掉的问题（危害、引力抓取器、深空、全息信标、生产统计）的游戏内测试，由 {@link UniverseGameTests} 登记。 */
final class ReleaseFixGameTests {
    private ReleaseFixGameTests() {
    }

    /** 世界设置关掉局部危害时喷口这一轮不喷：存档和发给客户端的数据里都记着，读回来的水汽柱是 0；规则改回来再查一次就又喷了。 */
    static void geyserDormantSynced(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        BlockPos pos = new BlockPos(3, 1, 3);
        helper.setBlock(pos, UniverseBlocks.GEYSER_VENTS.get(GeyserKind.HOT).get());
        GeyserVentBlockEntity vent = helper.getBlockEntity(pos, GeyserVentBlockEntity.class);
        long now = level.getGameTime();
        // 规则对整个服务端生效：在这一刻里改、查、改回去，别的测试看不到
        int frequency = server.getGameRules().get(UniverseGameRules.LOCAL_HAZARDS.get());
        try {
            server.getGameRules().set(UniverseGameRules.LOCAL_HAZARDS.get(), 0, server);
            vent.settle(level, now);
        } finally {
            server.getGameRules().set(UniverseGameRules.LOCAL_HAZARDS.get(), frequency, server);
        }
        helper.assertTrue(vent.dormant(), "规则关了喷口还喷");
        CompoundTag update = vent.getUpdateTag(level.registryAccess());
        helper.assertTrue(update.getBooleanOr("Dormant", false), "发给客户端的数据里没有不喷：" + update);
        GeyserVentBlockEntity copy = new GeyserVentBlockEntity(helper.absolutePos(pos), vent.getBlockState());
        copy.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), update));
        helper.assertTrue(copy.dormant(), "客户端读回来不知道这一轮不喷");
        for (long t = now; t < now + vent.cycle().length(); t += 5) {
            if (copy.column(t) > 0) {
                helper.fail("不喷的喷口在 " + (t - now) + " 刻后有 " + copy.column(t) + " 格高的水汽柱");
            }
        }
        vent.settle(level, now);
        helper.assertFalse(vent.dormant(), "规则改回来以后还不喷");
        helper.assertFalse(vent.getUpdateTag(level.registryAccess()).contains("Dormant"), "又喷了，发给客户端的数据里还记着不喷");
        helper.succeed();
    }

    /** 生产统计的进出口、各种设备的发电和用电存档再读回来还在。 */
    static void productionSeriesRoundTrip(GameTestHelper helper) {
        long now = helper.getLevel().getGameTime();
        ProductionData data = ProductionData.CODEC.parse(NbtOps.INSTANCE, emptyProduction()).getOrThrow();
        ProductionStats stats = data.stats();
        stats.shipped("test:a", "test:b", "minecraft:iron_ingot", 12, now);
        stats.device("test:a", "universe:fuel_generator", 900, 0, now);
        stats.device("test:a", "universe:assembler", 0, 400, now);
        Tag saved = ProductionData.CODEC.encodeStart(NbtOps.INSTANCE, data).getOrThrow();
        ProductionStats loaded = ProductionData.CODEC.parse(NbtOps.INSTANCE, saved).getOrThrow().stats();
        ProductionStats.Ledger a = loaded.ledgers().get("test:a");
        ProductionStats.Ledger b = loaded.ledgers().get("test:b");
        helper.assertTrue(a != null && b != null, "读回来少了维度：" + loaded.dimensions());
        helper.assertValueEqual(sum(b.imports().get(ProductionStats.tradeKey("minecraft:iron_ingot", "test:a")), now), 12L, "进口");
        helper.assertValueEqual(sum(a.exports().get(ProductionStats.tradeKey("minecraft:iron_ingot", "test:b")), now), 12L, "出口");
        helper.assertValueEqual(sum(a.generators().get("universe:fuel_generator"), now), 900L, "发电设备");
        helper.assertValueEqual(sum(a.consumers().get("universe:assembler"), now), 400L, "用电设备");
        helper.succeed();
    }

    private static CompoundTag emptyProduction() {
        CompoundTag tag = new CompoundTag();
        tag.put("dimensions", new CompoundTag());
        return tag;
    }

    private static long sum(ProductionStats.@Nullable Series series, long now) {
        return series == null ? -1 : series.sum(Range.HOUR, now);
    }

    /** 一刻里连着改好几回信标，登记表在这一刻末尾才发一次，发完就没有待发的了。 */
    static void holoBeaconCoalescesBroadcasts(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 1, 2);
        helper.setBlock(pos, UniverseBlocks.HOLO_BEACON.get());
        ServerLevel level = helper.getLevel();
        HoloBeacons beacons = HoloBeacons.get(level.getServer());
        HoloBeaconBlockEntity beacon = helper.getBlockEntity(pos, HoloBeaconBlockEntity.class);
        for (int i = 0; i < 10; i++) {
            beacon.apply(i % 2 == 0 ? "甲" : "乙", "", 0, BeaconVisibility.PLANET, Memo.EMPTY);
        }
        helper.assertTrue(beacons.unsent(), "改了设置，没记下要发");
        helper.assertValueEqual(beacons.at(level.dimension(), helper.absolutePos(pos)).map(HoloBeacons.Beacon::label).orElse(""), "乙", "登记表里的名字");
        helper.runAfterDelay(1, () -> {
            helper.assertFalse(beacons.unsent(), "这一刻末尾没把改动发出去");
            helper.setBlock(pos, Blocks.AIR);
            helper.succeed();
        });
    }

    /** 别人的舰队单位抓不起来，自己的抓得起来。 */
    static void gravityRefusesOthersFleetUnit(GameTestHelper helper) {
        ServerPlayer owner = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
        ServerPlayer thief = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
        Vec3 at = helper.absoluteVec(new Vec3(3.5, 1, 3.5));
        owner.snapTo(at.x, at.y, at.z, 0, 0);
        thief.snapTo(at.x + 2, at.y, at.z, 0, 0);
        FleetData data = owner.getData(UniverseAttachments.MECHA_FLEET);
        for (int slot = 0; slot < Squadrons.slots(0); slot++) {
            data.setSlot(0, slot, new ItemStack(UniverseItems.PROTOTYPE.get()));
        }
        MechaFleet.launch(owner, 0);
        List<FleetUnit> units = MechaFleet.units(owner, 0);
        try {
            helper.assertFalse(units.isEmpty(), "编队没放出去");
            for (FleetUnit unit : units) {
                helper.assertFalse(GravityCoils.grabbable(thief, unit), "别人的舰队单位也抓得起来");
                helper.assertTrue(GravityCoils.grabbable(owner, unit), "自己的舰队单位抓不起来");
            }
        } finally {
            MechaFleet.recallAll(owner, false);
        }
        helper.succeed();
    }

    /** 抓着东西时才看见抓的人（或者被抓的东西）的玩家，也补收到引力束。 */
    static void gravityBeamReachesLateTracker(GameTestHelper helper) {
        ServerPlayer holder = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
        Vec3 at = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(0, 1, 3)));
        holder.snapTo(at.x, at.y, at.z, -90, 0);
        ItemStack tool = new ItemStack(UniverseItems.MULTITOOL.get());
        tool.set(UniverseDataComponents.TOOL_MODULES.get(),
                ItemContainerContents.fromItems(List.of(new ItemStack(UniverseItems.WEAPONS.get(ToolWeapon.GRAVITY_COIL).get()))));
        tool.set(UniverseDataComponents.TOOL_MODE.get(), ToolMode.GRAVITY);
        holder.setItemInHand(InteractionHand.MAIN_HAND, tool);
        ItemEntity iron = helper.spawnItem(Items.IRON_INGOT, new Vec3(3.5, 1 + holder.getEyeHeight() - 0.125, 3.5));
        iron.setNoGravity(true);
        iron.setDeltaMovement(Vec3.ZERO);
        boolean[] holding = {true};
        helper.onEachTick(() -> GravityCoils.hold(holder, holding[0]));
        helper.runAfterDelay(10, () -> {
            helper.assertTrue(GravityCoils.holding(holder) == iron, "没抓起铁锭");
            ServerPlayer tracker = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
            List<Packet<?>> sent = capture(tracker);
            NeoForge.EVENT_BUS.post(new PlayerEvent.StartTracking(tracker, holder));
            NeoForge.EVENT_BUS.post(new PlayerEvent.StartTracking(tracker, iron));
            long beams = sent.stream()
                    .filter(packet -> packet instanceof ClientboundCustomPayloadPacket custom && custom.payload() instanceof GravityPayloads.Beam beam
                            && beam.holder() == holder.getId() && beam.target() == iron.getId())
                    .count();
            helper.assertValueEqual(beams, 2L, "补发的引力束");
            holding[0] = false;
            GravityCoils.release(holder, true);
            helper.succeed();
        });
    }

    /** 给假玩家接上一条连接：发给它的包都记下来，不真的发。 */
    private static List<Packet<?>> capture(ServerPlayer player) {
        List<Packet<?>> sent = new ArrayList<>();
        new ServerGamePacketListenerImpl(player.level().getServer(), new Connection(PacketFlow.SERVERBOUND), player,
                CommonListenerCookie.createInitial(player.getGameProfile(), false)) {
            @Override
            public void send(Packet<?> packet) {
                sent.add(packet);
            }

            @Override
            public void send(Packet<?> packet, @Nullable ChannelFutureListener listener) {
                sent.add(packet);
            }
        };
        return sent;
    }

    /**
     * 远处没加载的区块：问一下不会当场生成，挂上加载票以后在后台加载好。
     *
     * <p>区块在后台线程上生成，花的是真实时间；测试服务端两刻之间不等（{@code GameTestServer.waitUntilNextTick} 只跑完排着的任务），
     * 别的测试跑完以后一秒能跑几千刻，按刻数限时，机器一忙就不够（12000 刻不到两秒）。所以按真实时间限时（{@link RealTime}），登记的最长刻数不设限。
     */
    static void farChunksLoadInBackground(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos far = helper.absolutePos(BlockPos.ZERO).offset(6000, 0, 0);
        int cx = SectionPos.blockToSectionCoord(far.getX());
        int cz = SectionPos.blockToSectionCoord(far.getZ());
        // 跨两个区块
        BoundingBox box = new BoundingBox(cx * 16 + 8, far.getY(), cz * 16 + 2, cx * 16 + 24, far.getY() + 8, cz * 16 + 12);
        helper.assertTrue(level.getChunkSource().getChunkNow(cx, cz) == null, "远处的区块本来就加载着，测不了");
        helper.assertFalse(Structures.ready(level, box), "没加载的区块当成加载好了");
        helper.assertTrue(level.getChunkSource().getChunkNow(cx, cz) == null, "问了一下就当场加载了区块");
        RealTime.await(helper, () -> Structures.ready(level, box), "挂了加载票，区块还没加载好")
                .thenSucceed();
    }
}
