package io.github.sweetcornna.universe.gametest;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import com.mojang.authlib.GameProfile;
import com.mojang.serialization.Codec;

import io.github.sweetcornna.universe.blueprint.BlueprintData;
import io.github.sweetcornna.universe.blueprint.Blueprints;
import io.github.sweetcornna.universe.blueprint.BuildSite;
import io.github.sweetcornna.universe.blueprint.BuildSites;
import io.github.sweetcornna.universe.blueprint.Construction;
import io.github.sweetcornna.universe.core.blueprint.BlueprintGeometry;
import io.github.sweetcornna.universe.core.logistics.ItemRoute;
import io.github.sweetcornna.universe.core.logistics.Logic;
import io.github.sweetcornna.universe.core.logistics.Station;
import io.github.sweetcornna.universe.core.logistics.StationKind;
import io.github.sweetcornna.universe.core.mecha.Distribution;
import io.github.sweetcornna.universe.darkfog.Facilities;
import io.github.sweetcornna.universe.darkfog.facility.FacilityGhostBlockEntity;
import io.github.sweetcornna.universe.darkfog.facility.FacilityRepairs;
import io.github.sweetcornna.universe.factory.MachineMenu;
import io.github.sweetcornna.universe.factory.TallMachineBlock;
import io.github.sweetcornna.universe.logistics.ControlPanel;
import io.github.sweetcornna.universe.logistics.DistributorBlockEntity;
import io.github.sweetcornna.universe.logistics.DistributorRegistry;
import io.github.sweetcornna.universe.logistics.Logistics;
import io.github.sweetcornna.universe.logistics.LogisticsData;
import io.github.sweetcornna.universe.logistics.StationBlockEntity;
import io.github.sweetcornna.universe.logistics.StationContents;
import io.github.sweetcornna.universe.network.ControlPanelPayloads;
import io.github.sweetcornna.universe.network.ControlPanelPayloads.StationEdit;
import io.github.sweetcornna.universe.registry.UniverseBlocks;
import io.github.sweetcornna.universe.registry.UniverseItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ConfigurationTask;
import net.minecraft.server.players.NameAndId;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.TypedEntityData;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.neoforged.neoforge.common.extensions.ICommonPacketListener;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 物流控制面板远程改设置的权限和快照（ADR-0033 §三，1.0 复查）：有主人的站、配送器、路线只给主人和队友远程改，0.14 留下的没主人的
 * 谁都能改；改了什么才发快照，同一刻里只发一份。由 {@link UniverseGameTests} 登记。
 *
 * <p>假玩家没有连接，这里直接调服务端的方法，不给他们发包；快照只看排没排上，到了这一刻末尾假玩家不在玩家列表里，发不出去。
 */
final class ControlPanelGameTests {
    private ControlPanelGameTests() {
    }

    private static void tall(GameTestHelper helper, BlockPos pos, BlockState state) {
        helper.setBlock(pos, state.setValue(TallMachineBlock.HALF, DoubleBlockHalf.LOWER));
        helper.setBlock(pos.above(), state.setValue(TallMachineBlock.HALF, DoubleBlockHalf.UPPER));
    }

    /** 像玩家亲手放下的一样：走方块的 {@code setPlacedBy}。 */
    private static void placedBy(GameTestHelper helper, BlockPos pos, Player player) {
        BlockPos absolute = helper.absolutePos(pos);
        BlockState state = helper.getLevel().getBlockState(absolute);
        state.getBlock().setPlacedBy(helper.getLevel(), absolute, state, player, ItemStack.EMPTY);
    }

    /**
     * 下一刻做 {@code first}，再下一刻做 {@code second}，然后算过。{@code first} 没做完（失败了）就不做 {@code second}：一步失败以后，
     * 下一刻 GameTest 还会把到点的下一步跑一遍，{@code GameTestInfo#fail} 只留最后一个错，{@code second} 再失败就把 {@code first}
     * 真正的错盖掉了，报出来的是一个看不出原因的错。
     */
    private static void twoTicks(GameTestHelper helper, Runnable first, Runnable second) {
        boolean[] finished = new boolean[1];
        helper.startSequence().thenExecuteAfter(1, () -> {
            first.run();
            finished[0] = true;
        }).thenExecuteAfter(1, () -> {
            if (finished[0]) {
                second.run();
            }
        }).thenSucceed();
    }

    private static Station station(GameTestHelper helper, BlockPos pos) {
        Station station = helper.getBlockEntity(pos, StationBlockEntity.class).station();
        helper.assertTrue(station != null, "账本里没有 " + pos + " 的站");
        return station;
    }

    private static boolean edit(ServerPlayer player, UUID station, int action, int value, String text, UUID other) {
        return ControlPanel.editStation(player, new StationEdit(station, action, 0, value, text, other));
    }

    private static ControlPanelPayloads.StationRow row(GameTestHelper helper, ServerPlayer viewer, UUID id) {
        ControlPanelPayloads.StationRow row = ControlPanel.snapshot(helper.getLevel().getServer(), viewer).stations().stream()
                .filter(r -> r.id().equals(id)).findFirst().orElse(null);
        helper.assertTrue(row != null, "快照里没有这座站");
        return row;
    }

    private static ControlPanelPayloads.RouteRow routeRow(GameTestHelper helper, ServerPlayer viewer, UUID id) {
        ControlPanelPayloads.RouteRow row = ControlPanel.snapshot(helper.getLevel().getServer(), viewer).routes().stream()
                .filter(r -> r.id().equals(id)).findFirst().orElse(null);
        helper.assertTrue(row != null, "快照里没有这条路线");
        return row;
    }

    private static ItemRoute route(GameTestHelper helper, String item) {
        ItemRoute route = Logistics.network(helper.getLevel().getServer()).routes().stream().filter(r -> r.item().equals(item)).findFirst()
                .orElse(null);
        helper.assertTrue(route != null, "路线没记下来");
        return route;
    }

    /** 存档的账本走一遍编码、解码。 */
    private static LogisticsData roundTrip(MinecraftServer server) {
        Codec<LogisticsData> codec = LogisticsData.TYPE.codecFactory().create(null);
        Tag tag = codec.encodeStart(NbtOps.INSTANCE, LogisticsData.get(server)).getOrThrow();
        return codec.parse(NbtOps.INSTANCE, tag).getOrThrow();
    }

    /** 包的上下文：只认玩家，别的用不到。 */
    private static IPayloadContext context(ServerPlayer player) {
        return new IPayloadContext() {
            @Override
            public ICommonPacketListener listener() {
                throw new UnsupportedOperationException();
            }

            @Override
            public Player player() {
                return player;
            }

            @Override
            public CompletableFuture<Void> enqueueWork(Runnable task) {
                task.run();
                return CompletableFuture.completedFuture(null);
            }

            @Override
            public <T> CompletableFuture<T> enqueueWork(Supplier<T> task) {
                return CompletableFuture.completedFuture(task.get());
            }

            @Override
            public PacketFlow flow() {
                return PacketFlow.SERVERBOUND;
            }

            @Override
            public void handle(CustomPacketPayload payload) {
            }

            @Override
            public void finishCurrentTask(ConfigurationTask.Type type) {
            }
        };
    }

    /**
     * 玩家放下的站记着主人：别的玩家远程改名、分组、换格子、拿它当伙伴都不行，主人和主人的队友（主人不在线也认）可以；快照里对
     * 不同的人标着能不能改。配送器和路线一样；别的玩家建的路线走了主人的站，主人也能删。改名走包的入口：改了才排快照，同一刻改两次
     * 只排一份，下一刻发掉；改成原样的不排。
     */
    static void ownedStationRefusesOthers(GameTestHelper helper) {
        BlockPos ownedPos = new BlockPos(1, 1, 1);
        BlockPos otherPos = new BlockPos(5, 1, 5);
        BlockPos distributorPos = new BlockPos(3, 2, 1);
        BlockState interstellar = UniverseBlocks.INTERSTELLAR_LOGISTICS_STATION.get().defaultBlockState();
        tall(helper, ownedPos, interstellar);
        tall(helper, otherPos, interstellar);
        helper.setBlock(distributorPos.below(), Blocks.CHEST);
        helper.setBlock(distributorPos, UniverseBlocks.LOGISTICS_DISTRIBUTOR.get());
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer owner = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
        ServerPlayer stranger = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
        // 账本里还没有这座站（下一刻 onLoad 才建）：主人先记在方块实体上
        placedBy(helper, ownedPos, owner);
        placedBy(helper, distributorPos, owner);
        Station[] stations = new Station[2];
        twoTicks(helper, () -> {
            Station owned = station(helper, ownedPos);
            Station other = station(helper, otherPos);
            stations[0] = owned;
            stations[1] = other;
            helper.assertValueEqual(owned.owner(), Optional.of(owner.getUUID()), "放下的玩家没记成主人");
            // 账本里已经有了再放：直接改到账本上
            placedBy(helper, otherPos, stranger);
            helper.assertValueEqual(other.owner(), Optional.of(stranger.getUUID()), "后记的主人没改到账本上");
            // 别的玩家
            helper.assertFalse(edit(stranger, owned.id(), StationEdit.RENAME, 0, "抢来的", StationEdit.NONE), "别的玩家改了名");
            helper.assertFalse(edit(stranger, owned.id(), StationEdit.GROUP, 4, "", StationEdit.NONE), "别的玩家改了分组");
            helper.assertFalse(ControlPanel.editStation(stranger, new StationEdit(owned.id(), StationEdit.SLOT_ITEM, 0, 0, "minecraft:stone",
                    StationEdit.NONE)), "别的玩家换了格子的物品");
            helper.assertFalse(edit(stranger, other.id(), StationEdit.PARTNER_ADD, 0, "", owned.id()), "别的玩家拿主人的站当了伙伴");
            helper.assertTrue(owned.name().isEmpty() && owned.group() == 0 && owned.slot(0).isEmpty() && !owned.partneredWith(other),
                    "别的玩家的改动落到了账本上");
            helper.assertFalse(row(helper, stranger, owned.id()).editable(), "快照里别的玩家能改");
            helper.assertTrue(row(helper, stranger, other.id()).editable(), "快照里自己的站不能改");
            // 主人
            helper.assertTrue(edit(owner, owned.id(), StationEdit.RENAME, 0, "主人的站", StationEdit.NONE), "主人改不了名");
            helper.assertValueEqual(owned.name(), "主人的站", "名字");
            helper.assertFalse(edit(owner, owned.id(), StationEdit.PARTNER_ADD, 0, "", other.id()), "主人拿别人的站当了伙伴");
            helper.assertTrue(row(helper, owner, owned.id()).editable(), "快照里主人不能改");
            // 队友：主人不在线，按服务器记得的名字查队伍
            ServerPlayer mate = new ServerPlayer(server, helper.getLevel(), new GameProfile(UUID.randomUUID(), "cp-mate"),
                    ClientInformation.createDefault());
            server.services().nameToIdCache().add(new NameAndId(owner.getUUID(), "cp-owner"));
            Scoreboard scoreboard = server.getScoreboard();
            PlayerTeam team = scoreboard.addPlayerTeam("universe_cp_test");
            try {
                helper.assertFalse(edit(mate, owned.id(), StationEdit.GROUP, 2, "", StationEdit.NONE), "不同队伍的改了分组");
                scoreboard.addPlayerToTeam("cp-mate", team);
                scoreboard.addPlayerToTeam("cp-owner", team);
                helper.assertTrue(edit(mate, owned.id(), StationEdit.GROUP, 2, "", StationEdit.NONE), "队友改不了分组");
            } finally {
                scoreboard.removePlayerTeam(team);
            }
            // 配送器
            String dimension = helper.getLevel().dimension().identifier().toString();
            BlockPos absolute = helper.absolutePos(distributorPos);
            DistributorBlockEntity distributor = helper.getBlockEntity(distributorPos, DistributorBlockEntity.class);
            ControlPanelPayloads.DistributorEdit peer = new ControlPanelPayloads.DistributorEdit(dimension, absolute,
                    DistributorBlockEntity.CYCLE_PEER);
            helper.assertFalse(ControlPanel.editDistributor(stranger, peer), "别的玩家改了配送器");
            helper.assertValueEqual(distributor.peer(), Distribution.Peer.NONE, "配送器的模式");
            helper.assertTrue(ControlPanel.editDistributor(owner, peer), "主人改不了配送器");
            DistributorRegistry.Entry entry = DistributorRegistry.get(server).get(new DistributorRegistry.Key(dimension, absolute));
            helper.assertTrue(entry != null && entry.owner().equals(Optional.of(owner.getUUID())), "登记表里没记配送器的主人");
            List<ControlPanelPayloads.DistributorRow> rows = ControlPanel.snapshot(server, stranger).distributors();
            helper.assertTrue(rows.stream().anyMatch(r -> r.pos().equals(absolute) && !r.editable()), "快照里别的玩家能改配送器");
            // 路线：建的人是主人
            helper.assertTrue(ControlPanel.editRoute(owner, new ControlPanelPayloads.RouteEdit(true, StationEdit.NONE, "minecraft:phantom_membrane",
                    true, "0", "1")), "建不了路线");
            ItemRoute route = route(helper, "minecraft:phantom_membrane");
            helper.assertTrue(owner.getUUID().equals(route.creator()), "路线没记建的人");
            ControlPanelPayloads.RouteEdit delete = new ControlPanelPayloads.RouteEdit(false, route.id(), "", false, "", "");
            helper.assertFalse(ControlPanel.editRoute(stranger, delete), "别的玩家删了路线");
            // 存档：主人、建的人存得下来
            LogisticsData copy = roundTrip(server);
            Station savedStation = copy.network().get(owned.id());
            helper.assertTrue(savedStation != null && savedStation.owner().equals(Optional.of(owner.getUUID())), "存档里站没记主人");
            ItemRoute saved = copy.network().route(route.id());
            helper.assertTrue(saved != null && owner.getUUID().equals(saved.creator()), "存档里路线没记建的人");
            helper.assertTrue(ControlPanel.editRoute(owner, delete), "主人删不了路线");
            // 别的玩家建的路线拉走主人站上的货：主人删得掉，和这座站不相干的人删不掉
            owned.assign(1, "minecraft:armadillo_scute");
            owned.slot(1).setRemote(Logic.SUPPLY);
            int home = owned.location().system();
            helper.assertTrue(ControlPanel.editRoute(stranger, new ControlPanelPayloads.RouteEdit(true, StationEdit.NONE,
                    "minecraft:armadillo_scute", true, Integer.toString(home), home == 0 ? "1" : "0")), "别的玩家建不了路线");
            ItemRoute pulling = route(helper, "minecraft:armadillo_scute");
            ControlPanelPayloads.RouteEdit deletePulling = new ControlPanelPayloads.RouteEdit(false, pulling.id(), "", false, "", "");
            helper.assertTrue(routeRow(helper, owner, pulling.id()).editable(), "快照里主人删不了走他的站的路线");
            helper.assertFalse(routeRow(helper, mate, pulling.id()).editable(), "快照里不相干的人能删路线");
            helper.assertFalse(ControlPanel.editRoute(mate, deletePulling), "不相干的人删了路线");
            helper.assertTrue(ControlPanel.editRoute(owner, deletePulling), "主人删不了拉走他的货的路线");
            // 拆下来的物品带着主人；什么都没设的站只有主人，不往物品里存
            StationContents contents = StationContents.CODEC.parse(NbtOps.INSTANCE,
                    StationContents.CODEC.encodeStart(NbtOps.INSTANCE, StationContents.of(owned)).getOrThrow()).getOrThrow();
            helper.assertValueEqual(contents.owner(), Optional.of(owner.getUUID()), "物品里的主人");
            helper.assertTrue(StationContents.of(other).isEmpty(), "只有主人的站也存进了物品");
            // 包的入口：改了才排快照，同一刻只排一份。主人这时还一份都没排过，改成原样的先在这里试，不用看上一刻末尾发没发
            ControlPanel.handleStation(new StationEdit(owned.id(), StationEdit.RENAME, 0, 0, "抢来的", StationEdit.NONE), context(stranger));
            helper.assertFalse(ControlPanel.pending(stranger), "改不了的也排了快照");
            ControlPanel.handleStation(new StationEdit(owned.id(), StationEdit.RENAME, 0, 0, owned.name(), StationEdit.NONE), context(owner));
            ControlPanel.handleStation(new StationEdit(owned.id(), StationEdit.RANGE, 0, owned.droneRange(), "", StationEdit.NONE), context(owner));
            helper.assertFalse(ControlPanel.pending(owner), "改成原样也排了快照");
            ControlPanel.handleStation(new StationEdit(owned.id(), StationEdit.RENAME, 0, 0, "一号站", StationEdit.NONE), context(owner));
            ControlPanel.handleStation(new StationEdit(owned.id(), StationEdit.RANGE, 0, owned.droneRange() + 250, "", StationEdit.NONE),
                    context(owner));
            helper.assertTrue(ControlPanel.pending(owner), "改了没排快照");
            helper.assertValueEqual(owned.name(), "一号站", "包的入口改的名字");
        }, () -> {
            helper.assertFalse(ControlPanel.pending(owner), "这一刻末尾没发掉");
            // 发掉以后再改成原样（照账本里现在的名字）：还是不排
            Station owned = station(helper, ownedPos);
            helper.assertTrue(owned == stations[0], "账本里的站到了下一刻换成了另一条");
            ControlPanel.handleStation(new StationEdit(owned.id(), StationEdit.RENAME, 0, 0, owned.name(), StationEdit.NONE), context(owner));
            helper.assertFalse(ControlPanel.pending(owner), "发掉以后改成原样也排了快照");
            Logistics.network(server).remove(stations[0].id());
            Logistics.network(server).remove(stations[1].id());
        });
    }

    /** 0.14 留下的站、配送器、路线没记主人，谁都能远程改，存一遍档也还是没主人。 */
    static void legacyStationAcceptsEdits(GameTestHelper helper) {
        BlockPos stationPos = new BlockPos(1, 1, 1);
        BlockPos distributorPos = new BlockPos(3, 2, 1);
        tall(helper, stationPos, UniverseBlocks.PLANETARY_LOGISTICS_STATION.get().defaultBlockState());
        helper.setBlock(distributorPos.below(), Blocks.CHEST);
        helper.setBlock(distributorPos, UniverseBlocks.LOGISTICS_DISTRIBUTOR.get());
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer anyone = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
        helper.startSequence().thenExecuteAfter(1, () -> {
            Station station = station(helper, stationPos);
            helper.assertTrue(station.owner().isEmpty(), "没人放的站有了主人");
            helper.assertTrue(row(helper, anyone, station.id()).editable(), "快照里没主人的站不能改");
            helper.assertTrue(edit(anyone, station.id(), StationEdit.RENAME, 0, "公用站", StationEdit.NONE), "没主人的站改不了名");
            helper.assertValueEqual(station.name(), "公用站", "名字");
            String dimension = helper.getLevel().dimension().identifier().toString();
            BlockPos absolute = helper.absolutePos(distributorPos);
            helper.assertTrue(ControlPanel.editDistributor(anyone, new ControlPanelPayloads.DistributorEdit(dimension, absolute,
                    DistributorBlockEntity.CYCLE_ICARUS)), "没主人的配送器改不了");
            // 旧档里的路线：没记建的人
            helper.assertTrue(ControlPanel.editRoute(server, new ControlPanelPayloads.RouteEdit(true, StationEdit.NONE, "minecraft:rabbit_hide",
                    true, "0", "1")), "建不了路线");
            ItemRoute route = route(helper, "minecraft:rabbit_hide");
            helper.assertTrue(route.creator() == null, "旧路线有了建的人");
            LogisticsData copy = roundTrip(server);
            Station savedStation = copy.network().get(station.id());
            ItemRoute savedRoute = copy.network().route(route.id());
            helper.assertTrue(savedStation != null && savedStation.owner().isEmpty(), "存一遍档站有了主人");
            helper.assertTrue(savedRoute != null && savedRoute.creator() == null, "存一遍档路线有了建的人");
            helper.assertTrue(ControlPanel.editRoute(anyone, new ControlPanelPayloads.RouteEdit(false, route.id(), "", false, "", "")),
                    "没记建的人的路线删不了");
            Logistics.network(server).remove(station.id());
        }).thenSucceed();
    }

    /**
     * 复制出来的站（创造模式选取、带方块实体数据的物品、伪造的数据）带着原来那座站的编号和主人。下一刻换新编号之前它也不是原来那座站：
     * 别的玩家放下它，原来那座站的主人不变；打开它的界面、漏斗从它拿货都碰不到原来那座站；同一刻里拆掉它，原来那座站还在账本里，
     * 货和载具都在。下一刻留下的复制的站换了新编号，主人是放它的人。
     *
     * <p>两种复制：放下带 {@code BLOCK_ENTITY_DATA} 的物品（读数据时方块已经在世界里，编号当场丢掉）；读档读进来的（读数据时还没放进
     * 世界，编号带着，只能靠 {@link StationBlockEntity#station} 认位置）。
     */
    static void copiedStationKeepsOriginalOwner(GameTestHelper helper) {
        BlockPos originalPos = new BlockPos(1, 1, 1);
        BlockPos copyPos = new BlockPos(5, 1, 5);
        BlockPos loadedPos = new BlockPos(5, 1, 1);
        BlockState interstellar = UniverseBlocks.INTERSTELLAR_LOGISTICS_STATION.get().defaultBlockState();
        tall(helper, originalPos, interstellar);
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        ServerPlayer owner = (ServerPlayer) helper.makeMockServerPlayer(GameType.CREATIVE);
        ServerPlayer copier = (ServerPlayer) helper.makeMockServerPlayer(GameType.CREATIVE);
        placedBy(helper, originalPos, owner);
        Station[] stations = new Station[2];
        StationContents[] before = new StationContents[1];
        twoTicks(helper, () -> {
            Station original = station(helper, originalPos);
            stations[0] = original;
            helper.assertValueEqual(original.owner(), Optional.of(owner.getUUID()), "原来那座站的主人");
            original.setName("原来的站");
            helper.assertTrue(original.assign(0, "minecraft:iron_ingot"), "设不了格子");
            original.insert("minecraft:iron_ingot", 200);
            original.addDrones(10);
            original.addVessels(5);
            original.setWarpers(3);
            before[0] = StationContents.of(original);
            StationBlockEntity source = helper.getBlockEntity(originalPos, StationBlockEntity.class);
            // 带 BLOCK_ENTITY_DATA 的物品：先放方块，再读进方块实体数据，最后 setPlacedBy
            ItemStack stack = new ItemStack(UniverseItems.INTERSTELLAR_LOGISTICS_STATION.get());
            stack.set(DataComponents.BLOCK_ENTITY_DATA, TypedEntityData.<BlockEntityType<?>>of(source.getType(),
                    source.saveCustomOnly(level.registryAccess())));
            tall(helper, copyPos, interstellar);
            helper.assertTrue(BlockItem.updateCustomBlockEntityTag(level, copier, helper.absolutePos(copyPos), stack), "方块实体数据没读进去");
            StationBlockEntity copy = helper.getBlockEntity(copyPos, StationBlockEntity.class);
            helper.assertFalse(copy.saveCustomOnly(level.registryAccess()).contains("Station"), "放下的复制的站留着原来那座站的编号");
            placedBy(helper, copyPos, copier);
            helper.assertValueEqual(original.owner(), Optional.of(owner.getUUID()), "放下复制的站改了原来那座站的主人");
            // 读档读进来的：方块实体先读数据再放进世界
            tall(helper, loadedPos, interstellar);
            BlockPos loadedAt = helper.absolutePos(loadedPos);
            BlockEntity read = BlockEntity.loadStatic(loadedAt, level.getBlockState(loadedAt), source.saveWithFullMetadata(level.registryAccess()),
                    level.registryAccess());
            helper.assertTrue(read instanceof StationBlockEntity, "读不出方块实体");
            level.setBlockEntity(read);
            StationBlockEntity loaded = helper.getBlockEntity(loadedPos, StationBlockEntity.class);
            helper.assertTrue(loaded == read && loaded.saveCustomOnly(level.registryAccess()).contains("Station"), "读进来的站没带着编号");
            // 漏斗从第一格的出货口拿（投放口三格，进货口每格一个，后面是出货口）
            int output = StationBlockEntity.WARPER_SLOT + 1 + StationKind.INTERSTELLAR.slots();
            helper.assertTrue(loaded.removeItem(output, 64).isEmpty(), "漏斗从复制的站拿走了原来那座站的货");
            // 界面：改范围、改供需、清掉格子、取载具、取货都落不到原来那座站上，界面上也不显示它的东西。
            // 给东西的按钮放在后面：假玩家没有连接，真给了东西会在 placeItemBackInInventory 里崩掉，前面的先报错
            MachineMenu menu = (MachineMenu) loaded.createMenu(1, copier.getInventory(), copier);
            helper.assertTrue(menu != null, "打不开复制的站的界面");
            helper.assertValueEqual(menu.data(StationBlockEntity.VESSELS), 0, "复制的站的界面显示原来那座站的运输船");
            for (int button : new int[] {StationBlockEntity.RANGE_PLUS, StationBlockEntity.CYCLE_REMOTE, StationBlockEntity.CLEAR,
                    StationBlockEntity.TAKE_DRONES, StationBlockEntity.TAKE_VESSELS, StationBlockEntity.TAKE_WARPERS, StationBlockEntity.WITHDRAW}) {
                helper.assertFalse(menu.clickMenuButton(copier, button), "复制的站的界面按钮 " + button + " 有反应");
            }
            helper.assertTrue(copier.getInventory().isEmpty(), "复制的站的界面给了东西");
            helper.assertTrue(copy.station() == null && loaded.station() == null, "复制的站换编号之前碰得到原来那座站");
            // 同一刻里拆掉（创造模式一下就拆掉）
            copier.gameMode.destroyBlock(loadedAt);
            helper.assertBlockNotPresent(UniverseBlocks.INTERSTELLAR_LOGISTICS_STATION.get(), loadedPos);
            helper.assertTrue(Logistics.network(server).get(original.id()) == original, "拆掉复制的站把原来那座站从账本里去掉了");
            helper.assertValueEqual(StationContents.of(original), before[0], "原来那座站的东西");
        }, () -> {
            helper.assertTrue(Logistics.network(server).get(stations[0].id()) == stations[0]
                    && helper.getBlockEntity(originalPos, StationBlockEntity.class).station() == stations[0], "原来那座站在账本里换成了另一条");
            helper.assertValueEqual(StationContents.of(stations[0]), before[0], "原来那座站的东西");
            stations[1] = station(helper, copyPos);
            helper.assertFalse(stations[1] == stations[0], "复制的站没换编号");
            helper.assertValueEqual(stations[1].owner(), Optional.of(copier.getUUID()), "复制的站的主人");
            helper.assertValueEqual(stations[0].owner(), Optional.of(owner.getUUID()), "原来那座站的主人");
            Logistics.network(server).remove(stations[0].id());
            Logistics.network(server).remove(stations[1].id());
        });
    }

    /**
     * 贴蓝图建的物流站、配送器记成放蓝图的人的；设置照样带过来。创造模式直接贴走 {@link Blueprints#pasteNow}，和每刻的队列一样一格一格放；
     * 建造无人机走 {@link Construction#buildNow}，和每刻派出去的无人机一样：工地是别人的、帮忙建的是自己时，主人是工地的主人。
     * 两格高的站只有下半截有方块实体。
     */
    static void blueprintStationHasOwner(GameTestHelper helper) {
        BlockState station = UniverseBlocks.INTERSTELLAR_LOGISTICS_STATION.get().defaultBlockState();
        CompoundTag settings = (CompoundTag) StationContents.CODEC.encodeStart(NbtOps.INSTANCE, new StationContents(List.of(), 0, 0, 0, 0,
                Station.DEFAULT_RANGE, new StationContents.Settings("", 3, false, 0, List.of()), Optional.empty())).getOrThrow();
        // 一排三格宽、两格高：西头是站，东头是箱子上面的配送器
        int[] positions = {BlueprintGeometry.pack(0, 0, 0), BlueprintGeometry.pack(0, 1, 0), BlueprintGeometry.pack(2, 0, 0),
                BlueprintGeometry.pack(2, 1, 0)};
        BlueprintData.Stored stored = new BlueprintData.Stored("owned", 3, 2, 1, List.of(station.setValue(TallMachineBlock.HALF,
                DoubleBlockHalf.LOWER), station.setValue(TallMachineBlock.HALF, DoubleBlockHalf.UPPER), Blocks.CHEST.defaultBlockState(),
                UniverseBlocks.LOGISTICS_DISTRIBUTOR.get().defaultBlockState()), positions, new int[] {0, 1, 2, 3}, Map.of(0, settings),
                BlueprintData.Meta.NONE);
        ServerLevel level = helper.getLevel();
        ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(GameType.CREATIVE);
        BlockPos stationPos = null;
        BlockPos distributorPos = null;
        List<Blueprints.Placement> placements = Blueprints.placements(stored, helper.absolutePos(new BlockPos(3, 1, 3)), 0, false);
        Blueprints.pasteNow(player, placements);
        for (Blueprints.Placement placement : placements) {
            if (placement.state().is(UniverseBlocks.INTERSTELLAR_LOGISTICS_STATION.get())
                    && placement.state().getValue(TallMachineBlock.HALF) == DoubleBlockHalf.LOWER) {
                stationPos = helper.relativePos(placement.pos());
            } else if (placement.state().is(UniverseBlocks.LOGISTICS_DISTRIBUTOR.get())) {
                distributorPos = helper.relativePos(placement.pos());
            }
        }
        helper.assertTrue(stationPos != null && distributorPos != null, "蓝图里没有站或配送器");
        // 建造无人机：工地是 siteOwner 放的，来建的是 builder 的无人机，材料从 builder 身上扣
        ServerPlayer siteOwner = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
        ServerPlayer builder = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
        builder.getInventory().add(new ItemStack(UniverseItems.INTERSTELLAR_LOGISTICS_STATION.get()));
        BlockPos dronePos = new BlockPos(1, 1, 5);
        List<BlockState> halves = List.of(station.setValue(TallMachineBlock.HALF, DoubleBlockHalf.LOWER),
                station.setValue(TallMachineBlock.HALF, DoubleBlockHalf.UPPER));
        BuildSite site = BuildSites.get(level.getServer()).add(siteOwner.getUUID(), level.dimension(), helper.absolutePos(dronePos), halves,
                new int[] {BlueprintGeometry.pack(0, 0, 0), BlueprintGeometry.pack(0, 1, 0)}, new int[] {0, 1}, Map.of(0, settings));
        helper.assertTrue(Construction.buildNow(builder, site, 0) && Construction.buildNow(builder, site, 1), "无人机建不了站");
        helper.assertTrue(builder.getInventory().isEmpty(), "无人机没从帮忙建的人身上扣材料");
        BlockPos stationAt = stationPos;
        BlockPos distributorAt = distributorPos;
        helper.startSequence().thenExecuteAfter(1, () -> {
            Station built = station(helper, stationAt);
            helper.assertValueEqual(built.owner(), Optional.of(player.getUUID()), "贴蓝图建的站没记主人");
            helper.assertValueEqual(built.group(), 3, "蓝图里的分组");
            helper.assertValueEqual(helper.getBlockEntity(distributorAt, DistributorBlockEntity.class).owner(), Optional.of(player.getUUID()),
                    "贴蓝图建的配送器没记主人");
            Station byDrone = station(helper, dronePos);
            helper.assertValueEqual(byDrone.owner(), Optional.of(siteOwner.getUUID()), "无人机建的站的主人不是工地的主人");
            helper.assertValueEqual(byDrone.group(), 3, "工地里的分组");
            Logistics.network(level.getServer()).remove(built.id());
            Logistics.network(level.getServer()).remove(byDrone.id());
        }).thenSucceed();
    }

    /**
     * 黑雾打掉有主人的站和配送器：残影记着主人。别的玩家（他的无人机）重建站，战场分析基地（没有玩家）重建配送器，主人都还是原来的；
     * 站什么都没设，物品里不带主人，只靠残影记着。
     */
    static void rebuiltStationKeepsOwner(GameTestHelper helper) {
        BlockPos stationPos = new BlockPos(1, 1, 1);
        BlockPos distributorPos = new BlockPos(3, 2, 1);
        tall(helper, stationPos, UniverseBlocks.INTERSTELLAR_LOGISTICS_STATION.get().defaultBlockState());
        helper.setBlock(distributorPos.below(), Blocks.CHEST);
        helper.setBlock(distributorPos, UniverseBlocks.LOGISTICS_DISTRIBUTOR.get());
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        ServerPlayer owner = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
        ServerPlayer neighbour = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
        placedBy(helper, stationPos, owner);
        placedBy(helper, distributorPos, owner);
        twoTicks(helper, () -> {
            Station before = station(helper, stationPos);
            helper.assertValueEqual(before.owner(), Optional.of(owner.getUUID()), "放下的玩家没记成主人");
            helper.assertTrue(Facilities.damage(level, helper.absolutePos(stationPos), 1_000_000, null), "站没打掉");
            helper.assertTrue(Facilities.damage(level, helper.absolutePos(distributorPos), 1_000_000, null), "配送器没打掉");
            helper.assertValueEqual(helper.getBlockEntity(stationPos, FacilityGhostBlockEntity.class).owner(), owner.getUUID(), "站的残影没记主人");
            helper.assertValueEqual(helper.getBlockEntity(distributorPos, FacilityGhostBlockEntity.class).owner(), owner.getUUID(),
                    "配送器的残影没记主人");
            helper.assertTrue(Logistics.network(server).get(before.id()) == null, "打掉的站还在账本里");
            helper.assertTrue(FacilityRepairs.rebuild(level, helper.absolutePos(stationPos), neighbour), "别的玩家重建不了站");
            helper.assertTrue(FacilityRepairs.rebuild(level, helper.absolutePos(distributorPos), null), "战场分析基地重建不了配送器");
            helper.assertValueEqual(helper.getBlockEntity(distributorPos, DistributorBlockEntity.class).owner(), Optional.of(owner.getUUID()),
                    "重建的配送器换了主人");
        }, () -> {
            Station rebuilt = station(helper, stationPos);
            helper.assertValueEqual(rebuilt.owner(), Optional.of(owner.getUUID()), "别的玩家重建的站换了主人");
            Logistics.network(server).remove(rebuilt.id());
        });
    }
}
