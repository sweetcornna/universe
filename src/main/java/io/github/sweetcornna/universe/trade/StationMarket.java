package io.github.sweetcornna.universe.trade;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.core.galaxy.StarSystem;
import io.github.sweetcornna.universe.core.procgen.Rng;
import io.github.sweetcornna.universe.core.procgen.Seeds;
import io.github.sweetcornna.universe.core.tech.ModuleLoot;
import io.github.sweetcornna.universe.core.tech.ModuleType;
import io.github.sweetcornna.universe.core.tech.TechModule;
import io.github.sweetcornna.universe.core.trade.Economy;
import io.github.sweetcornna.universe.core.trade.Goods;
import io.github.sweetcornna.universe.core.trade.Market;
import io.github.sweetcornna.universe.core.trade.TradeCategory;
import io.github.sweetcornna.universe.registry.UniverseDataComponents;
import io.github.sweetcornna.universe.registry.UniverseItems;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;

/**
 * 一座空间站的行情（ADR-0017）：核心层的 {@link Market}（压价、存货）加上这一批卖的技术模块。只在内存里，
 * 存档时转成 {@link TradeData.Station}。
 */
final class StationMarket {
    /** 技术模块的货每两天换一批。 */
    static final long MODULE_PERIOD = Market.RESTOCK;
    /** 没有配方的模块按这个基准价算（C 级）。 */
    private static final double MODULE_FALLBACK = 400;

    /** 这一批卖的一个模块：数值已经定好。 */
    record ModuleOffer(ItemStack stack, double value) {
    }

    private final StarSystem system;
    private final Economy economy;
    private final Market market;
    /** 最后一次结算到哪一刻；存档按这一刻记。 */
    private long tick;
    private long period;
    private final Set<Integer> sold = new HashSet<>();
    private List<ModuleOffer> offers = List.of();

    private StationMarket(StarSystem system, Economy economy, Market market, long tick, long period, List<Integer> sold) {
        this.system = system;
        this.economy = economy;
        this.market = market;
        this.tick = tick;
        this.period = period;
        this.sold.addAll(sold);
    }

    static StationMarket create(MinecraftServer server, StarSystem system, TradeData.@Nullable Station saved, long now) {
        Economy economy = Economy.of(system);
        long tick = saved == null ? now : Math.min(saved.tick(), now);
        List<Market.Listing> listings = Goods.stock(Seeds.derive(system.seed(), "trade_goods"), economy, Trading.catalog(server), tick);
        if (saved != null) {
            // 清单里还在的东西按存档的存货，新加的满货
            List<Market.Listing> restored = new ArrayList<>();
            for (Market.Listing listing : listings) {
                Double stock = saved.stock().get(listing.item());
                restored.add(stock == null ? listing
                        : new Market.Listing(listing.item(), listing.value(), listing.category(), listing.capacity(), stock, tick));
            }
            listings = restored;
        }
        Market market = new Market(economy, listings);
        if (saved != null) {
            market.restorePressure(saved.pressure(), tick);
        }
        StationMarket station = new StationMarket(system, economy, market, tick,
                saved == null ? -1 : saved.period(), saved == null ? List.of() : saved.sold());
        station.rollModules(server, now);
        return station;
    }

    StarSystem system() {
        return this.system;
    }

    Economy economy() {
        return this.economy;
    }

    Market market() {
        return this.market;
    }

    /** 结算到 now：记下时间，换一批模块。 */
    void touch(MinecraftServer server, long now) {
        this.tick = Math.max(this.tick, now);
        this.rollModules(server, now);
    }

    private void rollModules(MinecraftServer server, long now) {
        long current = Math.floorDiv(now, MODULE_PERIOD);
        if (current != this.period) {
            this.period = current;
            this.sold.clear();
            this.offers = List.of();
        }
        if (this.offers.isEmpty()) {
            this.offers = offers(server, current);
        }
    }

    /** 这一批的模块：恒星越稀有、恒星系越富，品级越好；富裕的多卖一个。 */
    private List<ModuleOffer> offers(MinecraftServer server, long period) {
        Rng rng = Rng.of(Seeds.derive(Seeds.derive(this.system.seed(), "trade_modules"), period));
        boolean wealthy = this.economy.wealth() == Economy.Wealth.WEALTHY;
        int rarity = ModuleLoot.rarity(this.system.star().starClass());
        List<ModuleOffer> out = new ArrayList<>();
        for (int i = 0; i < (wealthy ? 3 : 2); i++) {
            TechModule module = ModuleLoot.roll(rng, ModuleType.personal(), rarity, wealthy ? 1 : 0);
            ItemStack stack = new ItemStack(UniverseItems.MODULES.get(module.type()).get());
            stack.set(UniverseDataComponents.TECH_MODULE.get(), module);
            double base = ItemValues.value(server, stack.getItem()).orElse(MODULE_FALLBACK);
            out.add(new ModuleOffer(stack, base * ItemValues.gradeFactor(module.grade())));
        }
        return List.copyOf(out);
    }

    List<ModuleOffer> offers() {
        return this.offers;
    }

    boolean sold(int offer) {
        return this.sold.contains(offer);
    }

    void markSold(int offer) {
        this.sold.add(offer);
    }

    /** 模块的卖价：存货满的价钱（只有一件）。 */
    double offerPrice(ModuleOffer offer, long now) {
        Market.Listing listing = new Market.Listing("module", offer.value(), TradeCategory.TECH, 1, 1, now);
        return this.market.buyUnitPrice(listing, now);
    }

    TradeData.Station snapshot() {
        Map<String, Double> stock = new HashMap<>();
        for (Market.Listing listing : this.market.listings()) {
            stock.put(listing.item(), listing.rawStock(this.tick));
        }
        return new TradeData.Station(this.tick, this.market.pressureSnapshot(this.tick), stock, this.period, List.copyOf(this.sold));
    }
}
