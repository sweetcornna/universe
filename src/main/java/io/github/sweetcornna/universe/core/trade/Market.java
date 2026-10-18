package io.github.sweetcornna.universe.core.trade;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 一座空间站的行情：玩家卖得越多，这样东西的收购价越低，过一阵慢慢回升；空间站卖的东西有存货，买空了要等补货（ADR-0017）。
 *
 * <p>按游戏刻惰性结算：每次查询或交易时按经过的时间衰减和补货，不需要每刻更新。
 */
public final class Market {
    /** 卖出的压价每两天（48000 刻）减半。 */
    public static final long HALF_LIFE = 48_000;
    /** 存货从空到满要两天。 */
    public static final long RESTOCK = 48_000;
    /** 同一座空间站的卖价至少比它自己的收购价高这么多，买了马上卖回去不赚钱。 */
    static final double SPREAD = 1.15;

    private final Economy economy;
    private final Map<String, Pressure> pressure = new HashMap<>();
    private final List<Listing> listings;

    /** 卖出的压价：累计了多少件、记到哪一刻。 */
    private record Pressure(double amount, long tick) {
    }

    /** 空间站卖的一样东西：基准价、大类、最多存多少、现在存多少。 */
    public static final class Listing {
        private final String item;
        private final double value;
        private final TradeCategory category;
        private final int capacity;
        private double stock;
        private long tick;

        public Listing(String item, double value, TradeCategory category, int capacity, double stock, long tick) {
            this.item = item;
            this.value = value;
            this.category = category;
            this.capacity = Math.max(1, capacity);
            this.stock = Math.max(0, Math.min(this.capacity, stock));
            this.tick = tick;
        }

        public String item() {
            return this.item;
        }

        public double value() {
            return this.value;
        }

        public TradeCategory category() {
            return this.category;
        }

        public int capacity() {
            return this.capacity;
        }

        /** now 时有几件（向下取整）。 */
        public int stock(long now) {
            this.restock(now);
            return (int) Math.floor(this.stock + 1e-9);
        }

        /** 存档用：补货到 now 以后的存货（带小数）。 */
        public double rawStock(long now) {
            this.restock(now);
            return this.stock;
        }

        private void restock(long now) {
            if (now > this.tick) {
                this.stock = Math.min(this.capacity, this.stock + this.capacity * (double) (now - this.tick) / RESTOCK);
                this.tick = now;
            }
        }
    }

    public Market(Economy economy, List<Listing> listings) {
        this.economy = economy;
        this.listings = new ArrayList<>(listings);
    }

    public Economy economy() {
        return this.economy;
    }

    public List<Listing> listings() {
        return List.copyOf(this.listings);
    }

    /** 压价的参考量：便宜的东西要卖很多才压得动，贵的几件就压下去。 */
    static double reference(double value) {
        return Math.max(6, Math.min(400, 192 * Math.pow(4 / Math.max(value, 0.01), 0.3)));
    }

    /** item 现在累计的压价（件）。 */
    public double pressure(String item, long now) {
        Pressure p = this.pressure.get(item);
        if (p == null) {
            return 0;
        }
        return p.amount() * Math.pow(0.5, Math.max(0, now - p.tick()) / (double) HALF_LIFE);
    }

    /**
     * 卖 count 件拿多少信用点：每件都比前一件便宜一点，按连续压价积分，
     * 单价 × 参考量 × ln((参考量 + 已压 + count) / (参考量 + 已压))。
     */
    public double sellQuote(String item, double value, TradeCategory category, int count, long now) {
        if (count <= 0 || value <= 0) {
            return 0;
        }
        double unit = this.economy.sellPrice(value, category);
        double k = reference(value);
        double s = this.pressure(item, now);
        return unit * k * Math.log((k + s + count) / (k + s));
    }

    /** 现在卖一件的单价（界面上显示的那个）。 */
    public double sellUnitPrice(String item, double value, TradeCategory category, long now) {
        double k = reference(value);
        return this.economy.sellPrice(value, category) * k / (k + this.pressure(item, now));
    }

    /** 卖出：记下压价，返回拿到的信用点。 */
    public double sell(String item, double value, TradeCategory category, int count, long now) {
        double credits = this.sellQuote(item, value, category, count, now);
        if (credits > 0) {
            this.pressure.put(item, new Pressure(this.pressure(item, now) + count, now));
        }
        return credits;
    }

    /** 从这一项买一件现在要多少：存货越少越贵（空的时候贵五成），而且不低于这里的收购价。 */
    public double buyUnitPrice(Listing listing, long now) {
        double fill = listing.rawStock(now) / listing.capacity();
        double price = this.economy.buyPrice(listing.value(), listing.category()) * (1 + 0.5 * (1 - fill));
        double floor = this.economy.sellPrice(listing.value(), listing.category()) * SPREAD;
        return Math.max(price, floor);
    }

    /** 买 count 件一共多少；存货不够返回负数。 */
    public double buyQuote(Listing listing, int count, long now) {
        if (count <= 0 || listing.stock(now) < count) {
            return -1;
        }
        double fill = listing.rawStock(now) / listing.capacity();
        double after = (listing.rawStock(now) - count) / listing.capacity();
        // 单价随存货线性变化，取首尾的平均
        double base = this.economy.buyPrice(listing.value(), listing.category());
        double average = base * (1 + 0.5 * (1 - (fill + after) / 2));
        double floor = this.economy.sellPrice(listing.value(), listing.category()) * SPREAD;
        return Math.max(average, floor) * count;
    }

    /** 买入：扣存货，返回花了多少；买不了返回负数，什么都不变。 */
    public double buy(Listing listing, int count, long now) {
        double cost = this.buyQuote(listing, count, now);
        if (cost >= 0) {
            listing.stock = listing.rawStock(now) - count;
        }
        return cost;
    }

    // ---- 存档

    /** 存档用：现在还有压价的物品和它的量。 */
    public Map<String, Double> pressureSnapshot(long now) {
        Map<String, Double> out = new HashMap<>();
        for (String item : this.pressure.keySet()) {
            double amount = this.pressure(item, now);
            if (amount > 0.01) {
                out.put(item, amount);
            }
        }
        return out;
    }

    /** 读档：恢复压价（记在 now 这一刻）。 */
    public void restorePressure(Map<String, Double> amounts, long now) {
        this.pressure.clear();
        amounts.forEach((item, amount) -> this.pressure.put(item, new Pressure(amount, now)));
    }
}
