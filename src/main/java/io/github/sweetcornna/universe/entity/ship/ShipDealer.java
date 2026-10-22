package io.github.sweetcornna.universe.entity.ship;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.block.ship.HelmBlockEntity;
import io.github.sweetcornna.universe.block.ship.ShipHelmBlock;
import io.github.sweetcornna.universe.core.fleet.Fleet;
import io.github.sweetcornna.universe.core.fleet.FreighterRules;
import io.github.sweetcornna.universe.core.fleet.Frigate;
import io.github.sweetcornna.universe.core.galaxy.StarSystem;
import io.github.sweetcornna.universe.core.ship.ShipDesign;
import io.github.sweetcornna.universe.core.ship.ShipMarket;
import io.github.sweetcornna.universe.core.ship.ShipRating;
import io.github.sweetcornna.universe.core.space.StationLayout;
import io.github.sweetcornna.universe.core.space.StationLayout.Site;
import io.github.sweetcornna.universe.core.tech.TechModule;
import io.github.sweetcornna.universe.core.trade.Economy;
import io.github.sweetcornna.universe.fleet.FleetData;
import io.github.sweetcornna.universe.fleet.Freighters;
import io.github.sweetcornna.universe.network.DealerPayloads;
import io.github.sweetcornna.universe.network.FleetPayloads;
import io.github.sweetcornna.universe.registry.UniverseBlocks;
import io.github.sweetcornna.universe.registry.UniverseDataComponents;
import io.github.sweetcornna.universe.registry.UniverseItems;
import io.github.sweetcornna.universe.station.Stations;
import io.github.sweetcornna.universe.trade.Credits;
import io.github.sweetcornna.universe.trade.ItemValues;
import io.github.sweetcornna.universe.world.planet.Planets;
import io.github.sweetcornna.universe.world.space.SpaceDimensions;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Prediction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 空间站的飞船交易终端（ADR-0024）：每天挂 {@link ShipMarket#SHIPS_PER_DAY} 艘船，买下的船在起降台上组装好、加满燃料，
 * 驾驶台里装着自带的技术模块，归买的人，成了他的主力船。停在机库里的自己的船能卖给终端，给方块价值的六成。
 */
public final class ShipDealer {
    /** 离终端多远还能操作。 */
    private static final double REACH = 8;
    /** 离起降台中心这么近（水平方向）的船算停在机库里。 */
    private static final double HANGAR = StationLayout.PAD_RADIUS + 10;
    /** 一个游戏日多少刻。 */
    private static final long DAY = 24000;
    /** 卖掉的记录里，船、护卫舰、货船各是一类。 */
    static final String SHIPS = "ship";
    static final String FRIGATES = "frigate";
    static final String FREIGHTER = "freighter";

    private record Session(ResourceKey<Level> dimension, BlockPos pos, int system) {
    }

    private static final Map<UUID, Session> SESSIONS = new HashMap<>();

    private ShipDealer() {
    }

    public static void registerEvents(IEventBus gameBus) {
        gameBus.addListener(ServerStoppedEvent.class, event -> SESSIONS.clear());
        gameBus.addListener(PlayerEvent.PlayerLoggedOutEvent.class, event -> SESSIONS.remove(event.getEntity().getUUID()));
    }

    /** 今天是第几个游戏日（按主世界的游戏时间，不受 /time set 影响）。 */
    public static long day(MinecraftServer server) {
        return server.overworld().getGameTime() / DAY;
    }

    /** 右键终端：记下会话，发界面内容。 */
    public static void open(ServerPlayer player, ServerLevel level, BlockPos pos) {
        OptionalInt system = SpaceDimensions.system(level.dimension());
        if (system.isEmpty() || Stations.site(level.getServer(), level.dimension()).isEmpty()
                || Planets.galaxy(level.getServer()).system(system.getAsInt()).isEmpty()) {
            player.sendOverlayMessage(Component.translatable("message.universe.trade.no_signal"));
            return;
        }
        Session session = new Session(level.dimension(), pos.immutable(), system.getAsInt());
        SESSIONS.put(player.getUUID(), session);
        send(player, session, true, null);
    }

    public static void handleBuy(DealerPayloads.Buy payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            Session session = session(player);
            if (session != null) {
                send(player, session, false, buy(player, session, payload.slot()));
            }
        }
    }

    public static void handleHire(DealerPayloads.Hire payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            Session session = session(player);
            if (session != null) {
                send(player, session, false, hire(player, session, payload.slot()));
            }
        }
    }

    public static void handleBuyFreighter(DealerPayloads.BuyFreighter payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            Session session = session(player);
            if (session != null) {
                send(player, session, false, buyFreighter(player, session, payload.seed()));
            }
        }
    }

    public static void handleSell(DealerPayloads.Sell payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            Session session = session(player);
            if (session != null) {
                send(player, session, false, sell(player, session, payload.entity()));
            }
        }
    }

    private static @Nullable Session session(ServerPlayer player) {
        Session session = SESSIONS.get(player.getUUID());
        if (session == null || !player.isAlive() || player.level().dimension() != session.dimension()
                || player.distanceToSqr(Vec3.atCenterOf(session.pos())) > REACH * REACH
                || !player.level().getBlockState(session.pos()).is(UniverseBlocks.SHIP_DEALER.get())) {
            SESSIONS.remove(player.getUUID());
            return null;
        }
        return session;
    }

    // ---- 货单

    private static StarSystem star(MinecraftServer server, int system) {
        return Planets.galaxy(server).system(system).orElseThrow();
    }

    static List<ShipMarket.Offer> offers(MinecraftServer server, int system) {
        StarSystem star = star(server, system);
        return ShipMarket.ships(star.seed(), Economy.of(star).wealth(), day(server));
    }

    /** 一艘船的价钱：方块加自带模块的价值，乘档次系数。 */
    static long price(MinecraftServer server, ShipMarket.Offer offer) {
        double value = ShipBlueprints.value(server, offer.design());
        for (TechModule module : offer.modules()) {
            value += ItemValues.unitValue(server, moduleStack(module));
        }
        return ShipMarket.price(value, offer.grade());
    }

    public static ItemStack moduleStack(TechModule module) {
        ItemStack stack = new ItemStack(UniverseItems.MODULES.get(module.type()).get());
        stack.set(UniverseDataComponents.TECH_MODULE.get(), module);
        return stack;
    }

    private static void send(ServerPlayer player, Session session, boolean open, @Nullable Component notice) {
        MinecraftServer server = player.level().getServer();
        ServerLevel level = player.level();
        Site site = Stations.site(server, session.dimension()).orElseThrow();
        long day = day(server);
        ShipDealerData data = ShipDealerData.get(server);
        List<DealerPayloads.Offer> ships = new ArrayList<>();
        for (ShipMarket.Offer offer : offers(server, session.system())) {
            ships.add(new DealerPayloads.Offer(offer.slot(), offer.seed(), offer.type().ordinal(), offer.grade().ordinal(),
                    price(server, offer), data.sold(session.system(), day, SHIPS, offer.slot())));
        }
        List<DealerPayloads.Owned> owned = new ArrayList<>();
        for (ShipEntity ship : hangarShips(level, site, player.getUUID())) {
            ShipStructure structure = ship.structure();
            ShipRating rating = structure.rating();
            owned.add(new DealerPayloads.Owned(ship.getId(), rating.type().ordinal(), rating.grade().ordinal(), structure.blocks().size(),
                    resale(server, structure), hasCargo(structure)));
        }
        StarSystem star = star(server, session.system());
        List<DealerPayloads.FrigateOffer> frigates = new ArrayList<>();
        for (Frigate frigate : ShipMarket.frigates(star.seed(), day)) {
            frigates.add(new DealerPayloads.FrigateOffer(frigate.id(), FleetPayloads.FrigateInfo.of(frigate, false), ShipMarket.frigatePrice(frigate),
                    data.sold(session.system(), day, FRIGATES, frigate.id())));
        }
        Optional<DealerPayloads.FreighterOffer> freighter = ShipMarket.freighter(star.seed(), Economy.of(star).wealth(), day)
                .map(offer -> new DealerPayloads.FreighterOffer(offer.seed(), offer.grade().ordinal(), ShipMarket.freighterPrice(offer.grade()),
                        FreighterRules.range(offer.grade()), data.sold(session.system(), day, FREIGHTER, 0)));
        Fleet fleet = FleetData.get(server).entry(player.getUUID()).fleet();
        int fleetSize = fleet.freighter().isPresent() ? fleet.frigates().size() : -1;
        PacketDistributor.sendToPlayer(player, new DealerPayloads.State(open, Credits.get(player), padFree(level, site), ships, owned, frigates,
                freighter, fleetSize, Optional.ofNullable(notice)));
    }

    // ---- 买

    private static Component buy(ServerPlayer player, Session session, int slot) {
        MinecraftServer server = player.level().getServer();
        ServerLevel level = player.level();
        Site site = Stations.site(server, session.dimension()).orElseThrow();
        long day = day(server);
        ShipDealerData data = ShipDealerData.get(server);
        ShipMarket.Offer offer = offers(server, session.system()).stream().filter(o -> o.slot() == slot).findFirst().orElse(null);
        if (offer == null || data.sold(session.system(), day, SHIPS, slot)) {
            return Component.translatable("message.universe.dealer.sold_out");
        }
        long price = price(server, offer);
        if (Credits.get(player) < price) {
            return Component.translatable("message.universe.dealer.poor", Credits.format(price - Credits.get(player)));
        }
        ShipDesign design = offer.design();
        double[] pad = site.pad();
        BlockPos center = BlockPos.containing(pad[0], pad[1], pad[2]);
        Map<BlockPos, BlockState> layout = ShipBlueprints.layout(design, center, Stations.direction(site.hangar()));
        if (!padFree(level, site) || layout.keySet().stream().anyMatch(pos -> !level.getBlockState(pos).isAir())) {
            return Component.translatable("message.universe.dealer.pad_busy");
        }
        BlockPos helm = null;
        for (Map.Entry<BlockPos, BlockState> entry : layout.entrySet()) {
            level.setBlock(entry.getKey(), entry.getValue(), 3);
            if (entry.getValue().getBlock() instanceof ShipHelmBlock) {
                helm = entry.getKey();
            }
        }
        if (helm != null && level.getBlockEntity(helm) instanceof HelmBlockEntity tech) {
            List<TechModule> modules = offer.modules();
            for (int i = 0; i < modules.size() && i < HelmBlockEntity.SLOTS; i++) {
                tech.items().set(i, moduleStack(modules.get(i)));
            }
            tech.setChanged();
        }
        ShipEntity ship = helm == null ? null : ShipAssembly.assemble(level, helm, player.getUUID(), message -> {
        });
        if (ship == null) {
            for (BlockPos pos : layout.keySet()) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            }
            return Component.translatable("message.universe.dealer.failed");
        }
        ship.refuel(ship.fuelCapacity());
        Credits.spend(player, price);
        data.markSold(session.system(), day, SHIPS, slot);
        ShipOwners.get(server).piloted(player.getUUID(), ship);
        level.playSound(null, center, SoundEvents.PLAYER_LEVELUP, SoundSource.BLOCKS, 0.6F, 1.4F);
        return Component.translatable("message.universe.dealer.bought", typeName(offer.type().key()), offer.grade().letter(), Credits.format(price));
    }

    // ---- 护卫舰和货船

    private static Component hire(ServerPlayer player, Session session, int slot) {
        MinecraftServer server = player.level().getServer();
        long day = day(server);
        ShipDealerData data = ShipDealerData.get(server);
        Frigate offer = ShipMarket.frigates(star(server, session.system()).seed(), day).stream().filter(f -> f.id() == slot).findFirst().orElse(null);
        if (offer == null || data.sold(session.system(), day, FRIGATES, slot)) {
            return Component.translatable("message.universe.dealer.sold_out");
        }
        FleetData fleets = FleetData.get(server);
        Fleet fleet = fleets.entry(player.getUUID()).fleet();
        Fleet.Problem problem = fleet.canHire();
        if (problem != null) {
            return Component.translatable("message.universe.fleet.problem." + problem.name().toLowerCase(java.util.Locale.ROOT));
        }
        long price = ShipMarket.frigatePrice(offer);
        if (!Credits.spend(player, price)) {
            return Component.translatable("message.universe.dealer.poor", Credits.format(price - Credits.get(player)));
        }
        Frigate hired = fleet.hire(offer);
        fleets.changed();
        data.markSold(session.system(), day, FRIGATES, slot);
        Freighters.refreshFrigates(server, player.getUUID());
        player.level().playSound(null, session.pos(), SoundEvents.PLAYER_LEVELUP, SoundSource.BLOCKS, 0.6F, 1.4F);
        return Component.translatable("message.universe.dealer.hired", hired.designation(),
                Component.translatable("gui.universe.fleet.type." + hired.type().key()), Credits.format(price));
    }

    private static Component buyFreighter(ServerPlayer player, Session session, long seed) {
        MinecraftServer server = player.level().getServer();
        long day = day(server);
        ShipDealerData data = ShipDealerData.get(server);
        StarSystem star = star(server, session.system());
        Optional<ShipMarket.FreighterOffer> offer = ShipMarket.freighter(star.seed(), Economy.of(star).wealth(), day);
        if (offer.isEmpty() || offer.get().seed() != seed || data.sold(session.system(), day, FREIGHTER, 0)) {
            return Component.translatable("message.universe.dealer.sold_out");
        }
        FleetData fleets = FleetData.get(server);
        if (fleets.entry(player.getUUID()).fleet().freighter().isPresent()) {
            return Component.translatable("message.universe.dealer.have_freighter");
        }
        long price = ShipMarket.freighterPrice(offer.get().grade());
        if (!Credits.spend(player, price)) {
            return Component.translatable("message.universe.dealer.poor", Credits.format(price - Credits.get(player)));
        }
        Freighters.grant(server, player.getUUID(), offer.get().seed(), offer.get().grade(), session.system(), null);
        data.markSold(session.system(), day, FREIGHTER, 0);
        player.level().playSound(null, session.pos(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.BLOCKS, 0.8F, 1.0F);
        return Component.translatable("message.universe.dealer.bought_freighter", offer.get().grade().letter(), Credits.format(price));
    }

    private static Component typeName(String key) {
        return Component.translatable("ship.universe.type." + key);
    }

    /** 起降台上方没有别的船。 */
    private static boolean padFree(ServerLevel level, Site site) {
        double[] pad = site.pad();
        double r = StationLayout.PAD_RADIUS + 1;
        AABB box = new AABB(pad[0] - r, pad[1], pad[2] - r, pad[0] + r, pad[1] + StationLayout.CEILING - 1, pad[2] + r);
        return level.getEntitiesOfClass(ShipEntity.class, box).isEmpty();
    }

    // ---- 卖

    /** 机库里、没人坐着的自己的船。 */
    private static List<ShipEntity> hangarShips(ServerLevel level, Site site, UUID owner) {
        double[] pad = site.pad();
        AABB box = new AABB(pad[0] - HANGAR, pad[1] - 2, pad[2] - HANGAR, pad[0] + HANGAR, pad[1] + StationLayout.CEILING, pad[2] + HANGAR);
        return level.getEntitiesOfClass(ShipEntity.class, box,
                ship -> ship.structure() != null && owner.equals(ship.owner()) && ship.getPassengers().isEmpty());
    }

    private static long resale(MinecraftServer server, ShipStructure structure) {
        return ShipMarket.resale(ShipBlueprints.value(server, structure.blocks().stream().map(ShipStructure.ShipBlock::state).toList()));
    }

    /** 船上除了驾驶台以外的容器里还有东西。 */
    private static boolean hasCargo(ShipStructure structure) {
        for (ShipStructure.ShipBlock block : structure.blocks()) {
            if (block.blockEntity() != null && !(block.state().getBlock() instanceof ShipHelmBlock)
                    && !block.blockEntity().getListOrEmpty("Items").isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private static Component sell(ServerPlayer player, Session session, int entityId) {
        MinecraftServer server = player.level().getServer();
        ServerLevel level = player.level();
        Site site = Stations.site(server, session.dimension()).orElseThrow();
        Entity entity = level.getEntity(entityId);
        if (!(entity instanceof ShipEntity ship) || !hangarShips(level, site, player.getUUID()).contains(ship)) {
            return Component.translatable("message.universe.dealer.not_here");
        }
        ShipStructure structure = ship.structure();
        if (hasCargo(structure)) {
            return Component.translatable("message.universe.dealer.cargo");
        }
        long price = resale(server, structure);
        // 驾驶台里的技术模块和驱动还给卖船的人
        if (structure.helm().state().getBlock() instanceof ShipHelmBlock) {
            for (ItemStack stack : ShipPanel.helm(structure, level.registryAccess()).items()) {
                if (!stack.isEmpty()) {
                    player.getInventory().placeItemBackInInventory(stack.copy(), Prediction.SERVER_ONLY);
                }
            }
        }
        ShipOwners.get(server).forget(ship.getUUID());
        ship.discard();
        Credits.add(player, price);
        level.playSound(null, ship.blockPosition(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.BLOCKS, 0.6F, 0.8F);
        ShipRating rating = structure.rating();
        return Component.translatable("message.universe.dealer.sold", typeName(rating.type().key()), rating.grade().letter(),
                Credits.format(price));
    }

}
