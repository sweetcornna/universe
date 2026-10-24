package io.github.sweetcornna.universe.core.hazard;

/**
 * 局部危害多久来一次（ADR-0034 §六）。每个玩家每 {@value #CHECK_INTERVAL} 刻掷一次，按种类的基础概率乘上
 * 世界设置里的频率（游戏规则，100 是默认，0 关掉）。
 *
 * <p>默认频率下，一直待在合适的地方：火山喷发大约八分钟一次；孢子云七分钟左右，风暴里快三倍；尘卷风按风力四到
 * 十三分钟一次。间歇泉是世界生成的，不掷。
 */
public final class HazardOdds {
    /** 每隔多少刻掷一次。 */
    public static final int CHECK_INTERVAL = 20 * 20;
    /** 同一个玩家两次危害之间至少隔多久（刻），免得接连来。 */
    public static final int QUIET_AFTER = 90 * 20;
    static final double ERUPTION = 0.04;
    static final double SPORES = 0.05;
    static final double DUST_DEVIL = 0.05;
    static final double MAX = 0.9;

    private HazardOdds() {
    }

    /**
     * 这一次掷中的概率。
     *
     * @param frequency 世界设置里的频率（百分比）
     * @param storm     天气：0 晴、1 雨雪、2 风暴
     * @param wind      星球的风力 0–1
     */
    public static double chance(HazardKind kind, int frequency, int storm, double wind) {
        if (frequency <= 0) {
            return 0;
        }
        double base = switch (kind) {
            case GEYSER -> 0;
            case ERUPTION -> ERUPTION;
            case SPORE_CLOUD -> SPORES * (storm >= 2 ? 3 : storm == 1 ? 1.6 : 1);
            case DUST_DEVIL -> DUST_DEVIL * (0.5 + Math.clamp(wind, 0, 1));
        };
        return Math.clamp(base * frequency / 100.0, 0, MAX);
    }

    /** 平均多少刻来一次（单元测试和手册用）。 */
    public static double meanTicks(HazardKind kind, int frequency, int storm, double wind) {
        double chance = chance(kind, frequency, storm, wind);
        return chance <= 0 ? Double.POSITIVE_INFINITY : CHECK_INTERVAL / chance;
    }
}
