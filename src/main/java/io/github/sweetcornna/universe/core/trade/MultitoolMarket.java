package io.github.sweetcornna.universe.core.trade;

import java.util.ArrayList;
import java.util.List;

import io.github.sweetcornna.universe.core.procgen.Rng;
import io.github.sweetcornna.universe.core.procgen.Seeds;
import io.github.sweetcornna.universe.core.survival.MultitoolClass;
import io.github.sweetcornna.universe.core.survival.MultitoolSpec;
import io.github.sweetcornna.universe.core.tech.Grade;
import io.github.sweetcornna.universe.core.tech.ModuleHost;
import io.github.sweetcornna.universe.core.tech.ModuleType;
import io.github.sweetcornna.universe.core.tech.TechModule;

/**
 * 空间站的多功能工具柜（ADR-0026 §六）：每天挂 {@value #TOOLS_PER_DAY} 把，按恒星系种子和日期算，同一天去看到的一样
 * （新的派生键 {@code multitool_cabinet}）。档次按空间站的经济，富裕的站好货多；类型按 {@link MultitoolClass#weight()}，
 * 异星型少见，守卫机型不卖。
 *
 * <p>价钱按档次和类型定，自带的技术模块另算。买的时候手上的旧工具折价收走：卖的类型给标价的一半（不算模块，模块会转到新工具上），
 * 守卫机型给一个步行机主脑的钱；没有类型的旧工具按做它的材料算，由适配层给。
 */
public final class MultitoolMarket {
    public static final int TOOLS_PER_DAY = 3;
    /** 档次的底价：C、B、A、S。 */
    static final long[] BASE_PRICE = {4000, 10000, 24000, 60000};
    /** 档次的概率（%）：贫穷、一般、富裕。 */
    static final double[][] GRADE_ODDS = {{50, 35, 13, 2}, {35, 38, 22, 5}, {20, 35, 35, 10}};
    /** 卖的工具自带几个技术模块：C、B、A、S。 */
    static final int[] INSTALLED = {0, 1, 1, 2};
    /** 旧工具折价：标价的一半。 */
    public static final double TRADE_IN = 0.5;
    /** 守卫机型的折价：一个步行机主脑的钱。 */
    public static final long SENTINEL_TRADE_IN = 1500;

    private MultitoolMarket() {
    }

    /** 挂着卖的一把工具。 */
    public record Offer(int slot, long seed, MultitoolClass type, Grade grade) {
        public MultitoolSpec spec() {
            return new MultitoolSpec(this.type, this.grade, 0);
        }

        /** 自带的技术模块，档次和工具一样；按工具的种子抽（新的派生键 {@code modules}），不重样，专长那一项更容易抽到。 */
        public List<TechModule> modules() {
            Rng rng = Rng.of(Seeds.derive(this.seed, "modules"));
            List<ModuleType> pool = new ArrayList<>(ModuleType.of(ModuleHost.MULTITOOL));
            ModuleType favored = favored(this.type);
            List<TechModule> modules = new ArrayList<>();
            for (int i = 0; i < INSTALLED[this.grade.ordinal()] && !pool.isEmpty(); i++) {
                ModuleType type = rng.weighted(pool, t -> t == favored ? 3 : 1);
                pool.remove(type);
                modules.add(TechModule.roll(type, this.grade, rng));
            }
            return modules;
        }
    }

    /** 和类型专长配套的模块。 */
    static ModuleType favored(MultitoolClass type) {
        return switch (type) {
            case PISTOL -> ModuleType.BEAM_FOCUS;
            case EXPERIMENTAL -> ModuleType.SCANNER_ARRAY;
            case RIFLE, ALIEN, SENTINEL -> ModuleType.WEAPON_CALIBRATOR;
        };
    }

    /** 这一天这座站的柜子里挂的工具。 */
    public static List<Offer> offers(long systemSeed, Economy.Wealth wealth, long day) {
        Rng rng = Rng.of(Seeds.derive(Seeds.derive(Seeds.derive(systemSeed, "multitool_cabinet"), day), "tools"));
        double[] odds = GRADE_ODDS[Math.min(wealth.ordinal(), GRADE_ODDS.length - 1)];
        List<MultitoolClass> types = new ArrayList<>();
        for (MultitoolClass type : MultitoolClass.values()) {
            if (type.weight() > 0) {
                types.add(type);
            }
        }
        List<Offer> offers = new ArrayList<>();
        for (int i = 0; i < TOOLS_PER_DAY; i++) {
            MultitoolClass type = rng.weighted(types, MultitoolClass::weight);
            Grade grade = rng.weighted(List.of(Grade.values()), g -> odds[g.ordinal()]);
            offers.add(new Offer(i, rng.nextLong(), type, grade));
        }
        return offers;
    }

    /** 类型的价钱系数：步枪、实验型贵一点，异星型贵一半。 */
    static double factor(MultitoolClass type) {
        return switch (type) {
            case PISTOL, SENTINEL -> 1.0;
            case RIFLE -> 1.15;
            case EXPERIMENTAL -> 1.1;
            case ALIEN -> 1.5;
        };
    }

    /** 工具本身的标价（不含自带的模块）。 */
    public static long price(MultitoolSpec spec) {
        return Math.round(BASE_PRICE[spec.grade().ordinal()] * factor(spec.type()));
    }

    /** 有类型的旧工具折多少钱。 */
    public static long tradeIn(MultitoolSpec spec) {
        if (spec.type() == MultitoolClass.SENTINEL) {
            return SENTINEL_TRADE_IN;
        }
        return Math.round(price(spec) * TRADE_IN);
    }

    /** 没有类型的旧工具（做出来的）折多少钱：做它的材料值多少就给一半。 */
    public static long legacyTradeIn(double craftValue) {
        return Math.round(Math.max(0, craftValue) * TRADE_IN);
    }

    /** 实际要付多少：标价减折价；折价比标价高时是负的，差额退给玩家。 */
    public static long due(long price, long tradeIn) {
        return price - tradeIn;
    }
}
