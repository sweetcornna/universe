package io.github.sweetcornna.universe.core.ship;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.github.sweetcornna.universe.core.fleet.Frigate;
import io.github.sweetcornna.universe.core.procgen.Rng;
import io.github.sweetcornna.universe.core.procgen.Seeds;
import io.github.sweetcornna.universe.core.tech.Grade;
import io.github.sweetcornna.universe.core.tech.ModuleHost;
import io.github.sweetcornna.universe.core.tech.ModuleType;
import io.github.sweetcornna.universe.core.tech.TechModule;
import io.github.sweetcornna.universe.core.trade.Economy;

/**
 * 空间站的飞船交易终端（ADR-0024）：每天挂几艘船、一两艘护卫舰，有时还有一艘货船。都按恒星系种子和日期算，
 * 同一天去看到的一样（新的派生键 {@code ship_dealer}）。
 */
public final class ShipMarket {
    public static final int SHIPS_PER_DAY = 4;
    public static final int FRIGATES_PER_DAY = 2;
    /** 每座站有货船卖的日子。 */
    public static final double FREIGHTER_CHANCE = 0.25;
    /** 档次的价钱系数：C、B、A、S。 */
    static final double[] GRADE_PRICE = {1.0, 1.3, 1.7, 2.4};
    /** 档次的概率（%）：贫穷、一般、富裕。 */
    static final double[][] GRADE_ODDS = {{60, 30, 10, 0}, {49, 35, 15, 1}, {30, 40, 28, 2}};
    /** 卖船给的比例。 */
    public static final double RESALE = 0.6;
    /** 货船的底价（信用点）。 */
    public static final double FREIGHTER_PRICE = 120000;
    /** 护卫舰的价钱：底价加属性合计 × 每点。 */
    static final double FRIGATE_BASE = 8000;
    static final double FRIGATE_PER_POINT = 700;
    public static final double FRIGATE_MAX_PRICE = 40000;
    /** 卖的船自带几个技术模块：C、B、A、S。 */
    static final int[] INSTALLED = {0, 1, 2, 3};

    private ShipMarket() {
    }

    /** 挂着卖的一艘船：按 {@code seed} 用 {@link ShipDesigner} 生成。 */
    public record Offer(int slot, long seed, ShipType type, Grade grade) {
        public ShipDesign design() {
            return ShipDesigner.design(this.seed, this.type, this.grade);
        }

        /** 船上装好的技术模块，档次和船一样。 */
        public List<TechModule> modules() {
            return installed(this.seed, this.design());
        }
    }

    /**
     * 卖的船自带的技术模块：按船的种子抽（新的派生键 {@code modules}），不重样，只抽船上用得上的
     * （没有光子炮不给火控，没有护盾发生器不给偏导护盾），船的专长那一项更容易抽到。
     */
    public static List<TechModule> installed(long seed, ShipDesign design) {
        Rng rng = Rng.of(Seeds.derive(seed, "modules"));
        Map<ShipPart, Integer> parts = design.parts();
        List<ModuleType> pool = new ArrayList<>();
        for (ModuleType type : ModuleType.of(ModuleHost.SHIP)) {
            if (useful(type, parts)) {
                pool.add(type);
            }
        }
        List<TechModule> modules = new ArrayList<>();
        for (int i = 0; i < INSTALLED[design.grade().ordinal()] && !pool.isEmpty(); i++) {
            ModuleType type = rng.weighted(pool, t -> favored(design.type(), t) ? 3 : 1);
            pool.remove(type);
            modules.add(TechModule.roll(type, design.grade(), rng));
        }
        return modules;
    }

    private static boolean useful(ModuleType type, Map<ShipPart, Integer> parts) {
        return switch (type) {
            case PULSE_TUNING -> has(parts, ShipPart.IMPULSE_ENGINE, ShipPart.IMPULSE_ENGINE_MK2);
            case HYPERDRIVE_BOOSTER -> has(parts, ShipPart.WARP_DRIVE, ShipPart.WARP_DRIVE_MK2);
            case DEFLECTOR -> has(parts, ShipPart.SHIELD_GENERATOR);
            case FIRE_CONTROL -> has(parts, ShipPart.PHOTON_CANNON);
            default -> true;
        };
    }

    private static boolean has(Map<ShipPart, Integer> parts, ShipPart... any) {
        for (ShipPart part : any) {
            if (parts.getOrDefault(part, 0) > 0) {
                return true;
            }
        }
        return false;
    }

    private static boolean favored(ShipType ship, ModuleType module) {
        return switch (ship) {
            case FIGHTER -> module == ModuleType.FIRE_CONTROL;
            case HAULER -> module == ModuleType.DEFLECTOR || module == ModuleType.HULL_REINFORCEMENT;
            case EXPLORER -> module == ModuleType.HYPERDRIVE_BOOSTER;
            case SHUTTLE -> module == ModuleType.THRUST_EFFICIENCY;
        };
    }

    /** 挂着卖的货船。 */
    public record FreighterOffer(long seed, Grade grade) {
    }

    private static Rng rng(long systemSeed, long day, String what) {
        return Rng.of(Seeds.derive(Seeds.derive(Seeds.derive(systemSeed, "ship_dealer"), day), what));
    }

    public static Grade grade(Rng rng, Economy.Wealth wealth) {
        double[] odds = GRADE_ODDS[Math.min(wealth.ordinal(), GRADE_ODDS.length - 1)];
        return rng.weighted(List.of(Grade.values()), g -> odds[g.ordinal()]);
    }

    public static List<Offer> ships(long systemSeed, Economy.Wealth wealth, long day) {
        Rng rng = rng(systemSeed, day, "ships");
        List<Offer> offers = new ArrayList<>();
        List<ShipType> types = List.of(ShipType.values());
        for (int i = 0; i < SHIPS_PER_DAY; i++) {
            ShipType type = rng.weighted(types, t -> t == ShipType.SHUTTLE ? 35 : t == ShipType.FIGHTER ? 25 : 20);
            offers.add(new Offer(i, rng.nextLong(), type, grade(rng, wealth)));
        }
        return offers;
    }

    public static List<Frigate> frigates(long systemSeed, long day) {
        Rng rng = rng(systemSeed, day, "frigates");
        List<Frigate> offers = new ArrayList<>();
        for (int i = 0; i < FRIGATES_PER_DAY; i++) {
            offers.add(Frigate.roll(i, rng.fork("frigate")));
        }
        return offers;
    }

    public static java.util.Optional<FreighterOffer> freighter(long systemSeed, Economy.Wealth wealth, long day) {
        Rng rng = rng(systemSeed, day, "freighter");
        if (!rng.chance(FREIGHTER_CHANCE)) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(new FreighterOffer(rng.nextLong(), grade(rng, wealth)));
    }

    /** 船的价钱：全船方块的价值乘档次系数。 */
    public static long price(double blockValue, Grade grade) {
        return Math.round(blockValue * GRADE_PRICE[grade.ordinal()]);
    }

    /** 卖船给的钱。 */
    public static long resale(double blockValue) {
        return Math.round(blockValue * RESALE);
    }

    public static long freighterPrice(Grade grade) {
        return Math.round(FREIGHTER_PRICE * GRADE_PRICE[grade.ordinal()]);
    }

    public static long frigatePrice(Frigate frigate) {
        return Math.round(Math.min(FRIGATE_MAX_PRICE, FRIGATE_BASE + FRIGATE_PER_POINT * frigate.total()));
    }
}
