package io.github.sweetcornna.universe.trade;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.advancement.MilestoneTrigger;
import io.github.sweetcornna.universe.civ.Civ;
import io.github.sweetcornna.universe.core.civ.Race;
import io.github.sweetcornna.universe.core.civ.Standing;
import io.github.sweetcornna.universe.core.galaxy.StarSystem;
import io.github.sweetcornna.universe.core.trade.Economy;
import io.github.sweetcornna.universe.core.trade.Goods;
import io.github.sweetcornna.universe.core.trade.Market;
import io.github.sweetcornna.universe.core.trade.OutpostStock;
import io.github.sweetcornna.universe.core.trade.TradeCategory;
import io.github.sweetcornna.universe.dyson.Dyson;
import io.github.sweetcornna.universe.poi.Pois;
import io.github.sweetcornna.universe.network.TradeActionPayload;
import io.github.sweetcornna.universe.network.TradeStatePayload;
import io.github.sweetcornna.universe.registry.UniverseBlocks;
import io.github.sweetcornna.universe.registry.UniverseItems;
import io.github.sweetcornna.universe.world.planet.Planets;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Prediction;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 交易终端的服务端（ADR-0017）：打开界面、按服务端的价钱卖出和购买、记下行情。每个恒星系一座空间站一份行情，
 * 终端放在哪个恒星系（太空、行星、母星）就是哪座空间站的行情。
 */
public final class Trading {
    /** 离终端多远还能交易。 */
    private static final double REACH = 8;

    /** 每座空间站都卖的东西，和要多富的恒星系才卖。 */
    private record Staple(Supplier<? extends Item> item, Economy.Wealth minWealth) {
    }

    private static final List<Staple> STAPLES = List.of(
            new Staple(UniverseItems.SPACE_WARPER, Economy.Wealth.POOR),
            new Staple(UniverseItems.LAUNCH_FUEL, Economy.Wealth.POOR),
            new Staple(UniverseItems.OXYGEN_CAPSULE, Economy.Wealth.POOR),
            new Staple(UniverseItems.HAZARD_CELL, Economy.Wealth.POOR),
            new Staple(UniverseItems.SHIP_THRUSTER, Economy.Wealth.POOR),
            new Staple(UniverseItems.FUEL_TANK, Economy.Wealth.POOR),
            new Staple(UniverseItems.IMPULSE_ENGINE, Economy.Wealth.AVERAGE),
            new Staple(UniverseItems.WARP_DRIVE, Economy.Wealth.AVERAGE),
            // 武器、护盾、采矿光束哪里都卖；Mk.II 推进器和燃料舱要中等财富，Mk.II 引擎和合金船体板只有富裕的恒星系卖
            new Staple(UniverseItems.PHOTON_CANNON, Economy.Wealth.POOR),
            new Staple(UniverseItems.MINING_BEAM, Economy.Wealth.POOR),
            new Staple(UniverseItems.SHIELD_GENERATOR, Economy.Wealth.POOR),
            new Staple(UniverseItems.SHIP_THRUSTER_MK2, Economy.Wealth.AVERAGE),
            new Staple(UniverseItems.FUEL_TANK_MK2, Economy.Wealth.AVERAGE),
            new Staple(UniverseItems.IMPULSE_ENGINE_MK2, Economy.Wealth.WEALTHY),
            new Staple(UniverseItems.WARP_DRIVE_MK2, Economy.Wealth.WEALTHY),
            new Staple(UniverseItems.ALLOY_HULL_PLATING, Economy.Wealth.WEALTHY),
            // 镍做镍催化剂（合成燃料那条线），不走生物浸出的话中等财富的空间站有卖（ADR-0036 §四）
            new Staple(UniverseItems.NICKEL_INGOT, Economy.Wealth.AVERAGE));

    /** 交易站总有的东西（ADR-0026）：燃料、氧气、防护电池、空间翘曲器。 */
    private static final List<Supplier<? extends Item>> ESSENTIALS = List.of(UniverseItems.LAUNCH_FUEL, UniverseItems.OXYGEN_CAPSULE,
            UniverseItems.HAZARD_CELL, UniverseItems.SPACE_WARPER);

    /** 玩家开着的终端：交易时检查还在不在旁边。{@code outpost} 是星球上交易站的种子，空间站的终端为 0。 */
    private record Session(ResourceKey<Level> dimension, BlockPos pos, int system, long outpost) {
    }

    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    private static @Nullable List<Goods> catalog;

    private Trading() {
    }

    public static void registerEvents(IEventBus gameBus) {
        gameBus.addListener(OnDatapackSyncEvent.class, event -> {
            if (event.getPlayer() == null) {
                reload(event.getPlayerList().getServer());
            }
        });
        gameBus.addListener(ServerStoppedEvent.class, event -> {
            ItemValues.invalidate();
            catalog = null;
            SESSIONS.clear();
        });
        gameBus.addListener(PlayerEvent.PlayerLoggedOutEvent.class, event -> SESSIONS.remove(event.getEntity().getUUID()));
    }

    /** 重新加载了数据包：价钱、货单都重算。 */
    private static void reload(MinecraftServer server) {
        ItemValues.invalidate();
        synchronized (Trading.class) {
            catalog = null;
        }
        TradeData.get(server).refresh();
    }

    static long now(MinecraftServer server) {
        return server.overworld().getGameTime();
    }

    /** 能在空间站买卖的东西：常备品，加上打了大类标签、算得出价钱的东西（科技类只有常备品和模块）。 */
    public static synchronized List<Goods> catalog(MinecraftServer server) {
        if (catalog == null) {
            Map<String, Goods> goods = new LinkedHashMap<>();
            for (Staple staple : STAPLES) {
                Item item = staple.item().get();
                ItemValues.value(server, item).ifPresent(value -> goods.put(id(item),
                        new Goods(id(item), value, ItemValues.category(item, value), true, staple.minWealth())));
            }
            for (TradeCategory category : TradeCategory.values()) {
                if (category == TradeCategory.TECH) {
                    continue;
                }
                for (Holder<Item> holder : BuiltInRegistries.ITEM.getTagOrEmpty(ItemValues.tag(category))) {
                    Item item = holder.value();
                    if (!goods.containsKey(id(item))) {
                        ItemValues.value(server, item).ifPresent(value -> goods.put(id(item), Goods.of(id(item), value, ItemValues.category(item, value))));
                    }
                }
            }
            catalog = List.copyOf(goods.values());
        }
        return catalog;
    }

    /** 恒星系空间站的行情（任务、维修这些以后也用）。 */
    static Optional<StationMarket> station(MinecraftServer server, int system) {
        return Planets.galaxy(server).system(system).map(star -> TradeData.get(server).market(server, star, now(server)));
    }

    /**
     * 技术商（ADR-0027 §六）：一个技术模块在这个恒星系的买价，和终端卖的模块一样算（存货满时一件的价钱），不算声望折扣。
     * 没有这个恒星系时为空。
     */
    public static OptionalDouble modulePrice(MinecraftServer server, int system, ItemStack module) {
        Optional<StationMarket> found = station(server, system);
        return found.isEmpty() ? OptionalDouble.empty() : OptionalDouble.of(found.get().modulePrice(server, module, now(server)));
    }

    // ---- 终端

    /** 右键终端：记下会话，发界面内容。 */
    public static void open(ServerPlayer player, ServerLevel level, BlockPos pos) {
        open(player, level, pos, 0);
    }

    /** 右键星球上交易站的终端（ADR-0026）：行情和本恒星系的空间站一样，货少一些（{@link OutpostStock}）。 */
    public static void openOutpost(ServerPlayer player, ServerLevel level, BlockPos pos) {
        long seed = Pois.claimKey(level, pos);
        open(player, level, pos, seed == 0 ? 1 : seed);
    }

    private static void open(ServerPlayer player, ServerLevel level, BlockPos pos, long outpost) {
        OptionalInt system = Dyson.systemOf(level.dimension());
        if (system.isEmpty() || Planets.galaxy(level.getServer()).system(system.getAsInt()).isEmpty()) {
            player.sendOverlayMessage(Component.translatable("message.universe.trade.no_signal"));
            return;
        }
        SESSIONS.put(player.getUUID(), new Session(level.dimension(), pos.immutable(), system.getAsInt(), outpost));
        send(player, system.getAsInt(), outpost, true, null);
    }

    /** 交易站摆不摆这一行货。 */
    private static boolean carries(long outpost, Market.Listing listing) {
        if (outpost == 0) {
            return true;
        }
        boolean essential = ESSENTIALS.stream().anyMatch(item -> id(item.get()).equals(listing.item()));
        return OutpostStock.carries(outpost, listing.item(), essential);
    }

    public static void handleAction(TradeActionPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        Session session = SESSIONS.get(player.getUUID());
        if (session == null || !valid(player, session)) {
            SESSIONS.remove(player.getUUID());
            return;
        }
        MinecraftServer server = player.level().getServer();
        Optional<StationMarket> station = station(server, session.system());
        if (station.isEmpty()) {
            return;
        }
        long now = now(server);
        Component notice = payload.buy()
                ? buy(player, station.get(), payload.key(), payload.mode(), now, session.outpost(), priceFactor(player, session.system()))
                : sell(player, station.get(), payload.key(), payload.mode(), now);
        send(player, session.system(), session.outpost(), false, notice);
    }

    private static boolean valid(ServerPlayer player, Session session) {
        return player.isAlive() && player.level().dimension() == session.dimension()
                && player.distanceToSqr(Vec3.atCenterOf(session.pos())) <= REACH * REACH
                && player.level().getBlockState(session.pos()).is(session.outpost() == 0 ? UniverseBlocks.TRADE_TERMINAL.get()
                        : UniverseBlocks.OUTPOST_TRADE_TERMINAL.get());
    }

    // ---- 卖出

    /** 背包里（不含盔甲和副手）键对得上、能卖的那些格子。 */
    private static List<ItemStack> matching(MinecraftServer server, ServerPlayer player, String key) {
        List<ItemStack> out = new ArrayList<>();
        for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
            if (ItemValues.sellable(server, stack) && ItemValues.key(stack).equals(key)) {
                out.add(stack);
            }
        }
        return out;
    }

    private static TradeCategory category(MinecraftServer server, ItemStack stack) {
        return ItemValues.category(stack.getItem(), ItemValues.value(server, stack.getItem()).orElse(0));
    }

    private static Component sell(ServerPlayer player, StationMarket station, String key, int mode, long now) {
        MinecraftServer server = player.level().getServer();
        List<ItemStack> stacks = matching(server, player, key);
        int available = stacks.stream().mapToInt(ItemStack::getCount).sum();
        if (available == 0) {
            return fail("message.universe.trade.nothing_to_sell");
        }
        ItemStack first = stacks.getFirst();
        int count = switch (mode) {
            case TradeActionPayload.STACK -> Math.min(available, first.getMaxStackSize());
            case TradeActionPayload.ALL -> available;
            default -> 1;
        };
        TradeCategory category = category(server, first);
        Market market = station.market();
        if (Math.floor(market.sellQuote(key, ItemValues.unitValue(server, first), category, count, now)) < 1) {
            return fail("message.universe.trade.too_cheap");
        }
        Component name = first.getHoverName();
        double total = 0;
        int remaining = count;
        for (ItemStack stack : stacks) {
            if (remaining <= 0) {
                break;
            }
            int n = Math.min(remaining, stack.getCount());
            total += market.sell(key, ItemValues.unitValue(server, stack), category, n, now);
            stack.shrink(n);
            remaining -= n;
        }
        long credits = (long) Math.floor(total);
        Credits.add(player, credits);
        MilestoneTrigger.reach(player, MilestoneTrigger.TRADE);
        player.getInventory().setChanged();
        TradeData.get(server).setDirty();
        return ok("message.universe.trade.sold", count, name, credits);
    }

    // ---- 购买

    /** 声望打折（ADR-0027 §四）：买东西的价钱按这个恒星系主导种族的声望档乘上去，卖出不变。 */
    private static double priceFactor(ServerPlayer player, int system) {
        return Planets.galaxy(player.level().getServer()).system(system).map(Race::dominant)
                .map(race -> Standing.priceFactor(Civ.state(player).tier(race))).orElse(1.0);
    }

    private static long cost(Market market, Market.Listing listing, int count, long now, double factor) {
        double quote = market.buyQuote(listing, count, now);
        return quote < 0 ? -1 : (long) Math.ceil(quote * factor - 1e-9);
    }

    private static Component buy(ServerPlayer player, StationMarket station, String key, int mode, long now, long outpost, double factor) {
        MinecraftServer server = player.level().getServer();
        if (key.startsWith("m")) {
            return buyModule(player, station, key, now, moduleLimit(outpost), factor);
        }
        int index;
        try {
            index = Integer.parseInt(key);
        } catch (NumberFormatException e) {
            return fail("message.universe.trade.out_of_stock");
        }
        Market market = station.market();
        List<Market.Listing> listings = market.listings();
        if (index < 0 || index >= listings.size()) {
            return fail("message.universe.trade.out_of_stock");
        }
        Market.Listing listing = listings.get(index);
        Optional<Item> item = BuiltInRegistries.ITEM.getOptional(Identifier.parse(listing.item()));
        int stock = listing.stock(now);
        if (item.isEmpty() || stock <= 0 || !carries(outpost, listing)) {
            return fail("message.universe.trade.out_of_stock");
        }
        int count = mode == TradeActionPayload.STACK ? Math.min(stock, item.get().getDefaultMaxStackSize()) : 1;
        long credits = Credits.get(player);
        long cost = cost(market, listing, count, now, factor);
        // 钱不够一组时买得起几件买几件
        while (count > 1 && cost > credits) {
            count--;
            cost = cost(market, listing, count, now, factor);
        }
        if (cost < 0 || cost > credits) {
            return fail("message.universe.trade.not_enough", cost(market, listing, 1, now, factor));
        }
        market.buy(listing, count, now);
        Credits.spend(player, cost);
        MilestoneTrigger.reach(player, MilestoneTrigger.TRADE);
        ItemStack bought = new ItemStack(item.get(), count);
        Component name = bought.getHoverName();
        player.getInventory().placeItemBackInInventory(bought, Prediction.SERVER_ONLY);
        TradeData.get(server).setDirty();
        return ok("message.universe.trade.bought", count, name, cost);
    }

    /** 摆几个技术模块：空间站全摆，交易站少摆。 */
    private static int moduleLimit(long outpost) {
        return outpost == 0 ? Integer.MAX_VALUE : OutpostStock.MODULES;
    }

    private static Component buyModule(ServerPlayer player, StationMarket station, String key, long now, int limit, double factor) {
        int index;
        try {
            index = Integer.parseInt(key.substring(1));
        } catch (NumberFormatException e) {
            return fail("message.universe.trade.out_of_stock");
        }
        List<StationMarket.ModuleOffer> offers = station.offers();
        if (index < 0 || index >= Math.min(offers.size(), limit) || station.sold(index)) {
            return fail("message.universe.trade.out_of_stock");
        }
        StationMarket.ModuleOffer offer = offers.get(index);
        long cost = (long) Math.ceil(station.offerPrice(offer, now) * factor - 1e-9);
        if (!Credits.spend(player, cost)) {
            return fail("message.universe.trade.not_enough", cost);
        }
        station.markSold(index);
        ItemStack bought = offer.stack().copy();
        Component name = bought.getHoverName();
        player.getInventory().placeItemBackInInventory(bought, Prediction.SERVER_ONLY);
        TradeData.get(player.level().getServer()).setDirty();
        return ok("message.universe.trade.bought", 1, name, cost);
    }

    // ---- 界面内容

    private static void send(ServerPlayer player, int system, long outpost, boolean open, @Nullable Component notice) {
        MinecraftServer server = player.level().getServer();
        Optional<StationMarket> found = station(server, system);
        if (found.isEmpty()) {
            return;
        }
        StationMarket station = found.get();
        long now = now(server);
        Market market = station.market();
        double factor = priceFactor(player, system);
        // 卖出：背包里能卖的东西按键归在一起；按一件的基准价排（不随压价变），连着点同一行卖的总是同一样东西
        Map<String, List<ItemStack>> groups = new LinkedHashMap<>();
        for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
            if (ItemValues.sellable(server, stack)) {
                groups.computeIfAbsent(ItemValues.key(stack), key -> new ArrayList<>()).add(stack);
            }
        }
        List<Map.Entry<String, List<ItemStack>>> sorted = new ArrayList<>(groups.entrySet());
        sorted.sort(Comparator.comparingDouble((Map.Entry<String, List<ItemStack>> entry) -> -ItemValues.unitValue(server, entry.getValue().getFirst()))
                .thenComparing(Map.Entry::getKey));
        List<TradeStatePayload.Row> sell = new ArrayList<>();
        sorted.forEach(entry -> {
            String key = entry.getKey();
            List<ItemStack> stacks = entry.getValue();
            ItemStack first = stacks.getFirst();
            int count = stacks.stream().mapToInt(ItemStack::getCount).sum();
            double unit = ItemValues.unitValue(server, first);
            TradeCategory category = category(server, first);
            sell.add(new TradeStatePayload.Row(first.copyWithCount(1), key, count, 0,
                    market.sellUnitPrice(key, unit, category, now),
                    Math.floor(market.sellQuote(key, unit, category, 1, now)),
                    Math.floor(market.sellQuote(key, unit, category, Math.min(count, first.getMaxStackSize()), now)),
                    Math.floor(market.sellQuote(key, unit, category, count, now)),
                    category.ordinal()));
        });
        // 购买：货单，最后是这一批的模块
        List<TradeStatePayload.Row> buy = new ArrayList<>();
        List<Market.Listing> listings = market.listings();
        for (int i = 0; i < listings.size(); i++) {
            Market.Listing listing = listings.get(i);
            Optional<Item> item = BuiltInRegistries.ITEM.getOptional(Identifier.parse(listing.item()));
            if (item.isEmpty() || !carries(outpost, listing)) {
                continue;
            }
            int stock = listing.stock(now);
            buy.add(new TradeStatePayload.Row(new ItemStack(item.get()), String.valueOf(i), stock, listing.capacity(),
                    market.buyUnitPrice(listing, now) * factor, cost(market, listing, 1, now, factor),
                    cost(market, listing, Math.max(1, Math.min(stock, item.get().getDefaultMaxStackSize())), now, factor), -1,
                    listing.category().ordinal()));
        }
        List<StationMarket.ModuleOffer> offers = station.offers();
        for (int i = 0; i < Math.min(offers.size(), moduleLimit(outpost)); i++) {
            StationMarket.ModuleOffer offer = offers.get(i);
            double price = station.offerPrice(offer, now) * factor;
            boolean sold = station.sold(i);
            long cost = (long) Math.ceil(price - 1e-9);
            buy.add(new TradeStatePayload.Row(offer.stack().copy(), "m" + i, sold ? 0 : 1, 1, price, sold ? -1 : cost, sold ? -1 : cost, -1,
                    TradeCategory.TECH.ordinal()));
        }
        Economy economy = station.economy();
        PacketDistributor.sendToPlayer(player, new TradeStatePayload(open, system, outpost != 0, economy.type().ordinal(), economy.wealth().ordinal(),
                Credits.get(player), sell, buy, Optional.ofNullable(notice)));
    }

    private static Component ok(String key, Object... args) {
        return Component.translatable(key, args).withStyle(ChatFormatting.GREEN);
    }

    private static Component fail(String key, Object... args) {
        return Component.translatable(key, args).withStyle(ChatFormatting.RED);
    }

    private static String id(Item item) {
        return BuiltInRegistries.ITEM.getKey(item).toString();
    }
}
