package io.github.sweetcornna.universe.core.trade;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

import io.github.sweetcornna.universe.core.galaxy.StarSystem;
import io.github.sweetcornna.universe.core.procgen.Rng;
import io.github.sweetcornna.universe.core.procgen.Seeds;

/**
 * 恒星系的经济：类型决定空间站缺什么（收购价高）、产什么（卖得便宜），财富决定差价和货量（ADR-0017）。
 *
 * <p>由恒星系的种子派生，新加的派生键不影响已有的宇宙。
 */
public record Economy(Type type, Wealth wealth) {
    /** 和无人深空的七种经济对应，名字自拟。 */
    public enum Type {
        /** 采矿：产原料，缺零件和能源。 */
        MINING(EnumSet.of(TradeCategory.RAW), EnumSet.of(TradeCategory.COMPONENT, TradeCategory.ENERGY)),
        /** 制造：产零件，缺原料和冶炼品。 */
        MANUFACTURING(EnumSet.of(TradeCategory.COMPONENT), EnumSet.of(TradeCategory.RAW, TradeCategory.REFINED)),
        /** 科技：产飞船部件和模块，缺高级品和零件。 */
        TECHNOLOGY(EnumSet.of(TradeCategory.TECH), EnumSet.of(TradeCategory.ADVANCED, TradeCategory.COMPONENT)),
        /** 贸易：什么都收一点，差价最小。 */
        TRADING(EnumSet.noneOf(TradeCategory.class), EnumSet.noneOf(TradeCategory.class)),
        /** 科研：产矩阵和能源，缺动植物产物和高级品。 */
        SCIENTIFIC(EnumSet.of(TradeCategory.ENERGY), EnumSet.of(TradeCategory.BIO, TradeCategory.ADVANCED)),
        /** 能源：产燃料，缺冶炼品和零件。 */
        POWER(EnumSet.of(TradeCategory.ENERGY), EnumSet.of(TradeCategory.REFINED, TradeCategory.COMPONENT)),
        /** 先进材料：产冶炼品和高级品，缺原料和能源。 */
        MATERIALS(EnumSet.of(TradeCategory.REFINED, TradeCategory.ADVANCED), EnumSet.of(TradeCategory.RAW, TradeCategory.ENERGY));

        private final Set<TradeCategory> supplies;
        private final Set<TradeCategory> demands;

        Type(Set<TradeCategory> supplies, Set<TradeCategory> demands) {
            this.supplies = supplies;
            this.demands = demands;
        }

        public boolean supplies(TradeCategory category) {
            return this.supplies.contains(category);
        }

        public boolean demands(TradeCategory category) {
            return this.demands.contains(category);
        }

        public Set<TradeCategory> supplied() {
            return this.supplies.isEmpty() ? Set.of() : EnumSet.copyOf(this.supplies);
        }

        public String key() {
            return this.name().toLowerCase(Locale.ROOT);
        }
    }

    /** 财富：越富，收购价越接近基准价，卖价的加成越小，货也越多。 */
    public enum Wealth {
        POOR(0.80, 1.35, 0.6),
        AVERAGE(0.88, 1.25, 1.0),
        WEALTHY(0.95, 1.15, 1.5);

        private final double sellFactor;
        private final double buyMarkup;
        private final double stockFactor;

        Wealth(double sellFactor, double buyMarkup, double stockFactor) {
            this.sellFactor = sellFactor;
            this.buyMarkup = buyMarkup;
            this.stockFactor = stockFactor;
        }

        /** 玩家卖给空间站时拿到基准价的几成。 */
        public double sellFactor() {
            return this.sellFactor;
        }

        /** 玩家从空间站买时比基准价贵多少。 */
        public double buyMarkup() {
            return this.buyMarkup;
        }

        /** 货量的倍数。 */
        public double stockFactor() {
            return this.stockFactor;
        }

        public int tier() {
            return this.ordinal() + 1;
        }

        public String key() {
            return this.name().toLowerCase(Locale.ROOT);
        }
    }

    /** 母星系固定是中等财富的制造经济，前期卖零件、买原料都顺手。 */
    public static final Economy HOME = new Economy(Type.MANUFACTURING, Wealth.AVERAGE);
    /** 收购价：恒星系缺的大类贵三成，产的大类便宜两成五。 */
    static final double DEMAND_BONUS = 1.30;
    static final double SUPPLY_DISCOUNT = 0.75;
    /** 卖价：恒星系自己产的大类便宜两成。 */
    static final double LOCAL_DISCOUNT = 0.80;

    public static Economy of(StarSystem system) {
        if (system.index() == 0) {
            return HOME;
        }
        Rng rng = Rng.of(Seeds.derive(system.seed(), "economy"));
        Type type = rng.pick(Type.values());
        // 稀有恒星周围更富：蓝巨星、中子星、黑洞
        double rich = switch (system.star().starClass()) {
            case B, O, NEUTRON_STAR, BLACK_HOLE -> 0.45;
            case M -> 0.12;
            default -> 0.25;
        };
        double roll = rng.nextDouble();
        Wealth wealth = roll < rich ? Wealth.WEALTHY : roll < rich + 0.4 ? Wealth.AVERAGE : Wealth.POOR;
        return new Economy(type, wealth);
    }

    /** 玩家卖一件基准价 value、大类 category 的东西拿到多少（还没算供求）。 */
    public double sellPrice(double value, TradeCategory category) {
        double factor = this.wealth.sellFactor();
        if (this.type.demands(category)) {
            factor *= DEMAND_BONUS;
        } else if (this.type.supplies(category)) {
            factor *= SUPPLY_DISCOUNT;
        }
        return value * factor;
    }

    /** 玩家买一件要付多少（还没算存货）。 */
    public double buyPrice(double value, TradeCategory category) {
        double factor = this.wealth.buyMarkup();
        if (this.type.supplies(category)) {
            factor *= LOCAL_DISCOUNT;
        }
        return value * factor;
    }
}
