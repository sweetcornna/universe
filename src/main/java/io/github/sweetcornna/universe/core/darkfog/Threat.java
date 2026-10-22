package io.github.sweetcornna.universe.core.darkfog;

/**
 * 黑雾基地的威胁和进攻（ADR-0028 §三）。威胁 0–1，满了派一波。
 *
 * <ul>
 *   <li>用电：普通攻击性下，这颗星球用电 25 kW 时约 40 分钟涨满，400 kW 时约 10 分钟，最快 6 分钟
 *       （涨满的时间和用电的平方根成反比）。</li>
 *   <li>战斗：基地挨打按伤害涨，打钻芯、营地涨得最多；打进攻部队不涨（打完威胁就清零了）。</li>
 *   <li>攻击性、用电威胁系数、战斗威胁系数乘上去。</li>
 * </ul>
 */
public final class Threat {
    /** 每档攻击性涨威胁的倍率：被动、迟钝、普通、敏锐、好斗、狂暴。被动的基地不进攻。 */
    private static final double[] AGGRESSION = {0, 0.5, 1, 1.5, 2, 3};
    /** 每档攻击性派出营地里多大一部分单位。 */
    private static final double[] WAVE_SHARE = {0, 0.3, 0.5, 0.65, 0.8, 1};
    public static final double REFERENCE_WATTS = 25_000;
    public static final double REFERENCE_MINUTES = 40;
    public static final double FASTEST_MINUTES = 6;
    /** 用电不到这么多不涨威胁。 */
    public static final double MIN_WATTS = 200;
    /** 挨多少伤害涨满一次威胁（打钻芯、营地按两倍算，打单位按一半）。 */
    public static final double DAMAGE_TO_FILL = 1500;
    /** 满了以后过多久进攻部队出现（这段时间里给人预警）。 */
    public static final int WARNING_TICKS = 600;
    /** 打完一波以后多久不涨威胁。 */
    public static final int COOLDOWN_TICKS = 3600;
    /** 一波最长打多久，到时候没死的撤走。 */
    public static final int WAVE_TIMEOUT_TICKS = 7200;
    /** 进攻部队在目标朝基地方向多远处出现。 */
    public static final double SPAWN_DISTANCE = 80;
    /** 一波里一种单位最多多少个。 */
    public static final int MAX_PER_KIND = 16;

    /** 一波进攻部队。 */
    public record Wave(int raiders, int rangers) {
        public static final Wave NONE = new Wave(0, 0);

        public int total() {
            return this.raiders + this.rangers;
        }
    }

    private Threat() {
    }

    public static double aggression(int aggressiveness) {
        return AGGRESSION[Math.max(0, Math.min(AGGRESSION.length - 1, aggressiveness))];
    }

    /** 普通攻击性、系数 100% 时，用电这么多瓦要多少分钟涨满；用电太少时是无穷大。 */
    public static double minutesToFill(double watts) {
        if (watts < MIN_WATTS) {
            return Double.POSITIVE_INFINITY;
        }
        return Math.max(FASTEST_MINUTES, REFERENCE_MINUTES * Math.sqrt(REFERENCE_WATTS / watts));
    }

    /** 用电每刻涨多少威胁。 */
    public static double powerPerTick(double watts, DarkFogSettings settings) {
        double minutes = minutesToFill(watts);
        if (Double.isInfinite(minutes)) {
            return 0;
        }
        return aggression(settings.aggressiveness()) * settings.powerThreat() / (minutes * 1200);
    }

    /** 建筑挨一点伤害涨多少威胁。 */
    public static double buildingPerDamage(BuildingKind kind, DarkFogSettings settings) {
        double weight = kind == BuildingKind.CORE || kind.isCamp() ? 2 : 1;
        return weight / DAMAGE_TO_FILL * settings.combatThreat() * aggression(settings.aggressiveness());
    }

    /** 守家的单位挨一点伤害涨多少威胁。 */
    public static double unitPerDamage(DarkFogSettings settings) {
        return 0.5 / DAMAGE_TO_FILL * settings.combatThreat() * aggression(settings.aggressiveness());
    }

    /**
     * 这一波派多少：营地里现有的突袭者、游骑兵按攻击性取一部分，等级越高取得越多（每级多 1%），至少一个。
     */
    public static Wave wave(int raiders, int rangers, int level, int aggressiveness) {
        if (aggressiveness <= DarkFogSettings.PASSIVE) {
            return Wave.NONE;
        }
        double share = Math.min(1, WAVE_SHARE[Math.min(WAVE_SHARE.length - 1, aggressiveness)] + 0.01 * DarkFogLevels.clamp(level));
        return new Wave(take(raiders, share), take(rangers, share));
    }

    private static int take(int available, double share) {
        if (available <= 0) {
            return 0;
        }
        return Math.min(MAX_PER_KIND, Math.min(available, Math.max(1, (int) Math.ceil(available * share))));
    }
}
