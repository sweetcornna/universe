package io.github.sweetcornna.universe.core.audio;

/**
 * 声音在不同介质里传过来剩多少（ADR-0029 §四）。
 *
 * <p>真空不传声，但完全听不见敌人和设备就没法玩：宇航服的外部拾音把外面的声音还原出来，压得很小、很闷。
 * 稀薄的大气照火星的实测往这边靠（毅力号：同样的声源小约 20 分贝，高频衰减得厉害），为了能玩只取一部分。
 * 身边的、自己坐着的载具上的声音靠接触传过来，照常。
 */
public final class Acoustics {
    /** 这个距离（格）以内算贴着，照常听见。 */
    public static final double CONTACT = 2.0;
    /** 大气压（母星的标准大气压）到这里就和母星一样。 */
    public static final double FULL_PRESSURE = 0.5;
    /** 低于这个就当真空。 */
    public static final double VACUUM_PRESSURE = 0.001;
    /** 真空里宇航服还原出来的音量（-10 dB）和高频。 */
    public static final double VACUUM_GAIN = 0.316;
    public static final double VACUUM_HIGHS = 0.1;
    /** 稀薄大气里高频随距离衰减：每这么多格剩 1/e（大气越稀越快）。 */
    public static final double HIGHS_DISTANCE = 24.0;
    /** 水下听到的高频。 */
    public static final double UNDERWATER_HIGHS = 0.25;

    /** 直达声的滤波：gain 是整体的音量，highs 是高频（约 5 kHz 以上）相对低频剩下的比例。 */
    public record Filter(double gain, double highs) {
        public static final Filter NONE = new Filter(1, 1);

        public boolean none() {
            return this.gain >= 0.999 && this.highs >= 0.999;
        }
    }

    private Acoustics() {
    }

    /**
     * @param pressure   听者所在的大气压，真空为 0
     * @param distance   声源到听者的距离（格）
     * @param contact    声源在自己坐着的载具上（不管多远都算贴着）
     * @param underwater 听者的头在水里
     */
    public static Filter filter(double pressure, double distance, boolean contact, boolean underwater) {
        double gain = 1;
        double highs = 1;
        if (!contact && distance > CONTACT && pressure < FULL_PRESSURE) {
            // 0.001 到 0.5 个大气压之间按对数插值：火星（0.006）大约在 0.29；音量按分贝插，火星小 7 分贝左右
            double medium = medium(pressure);
            gain = Math.pow(VACUUM_GAIN, 1 - medium);
            highs = (VACUUM_HIGHS + (1 - VACUUM_HIGHS) * medium) * Math.exp(-(distance - CONTACT) * (1 - medium) / HIGHS_DISTANCE);
            highs = Math.max(highs, VACUUM_HIGHS);
        }
        if (underwater) {
            highs *= UNDERWATER_HIGHS;
        }
        return gain >= 0.999 && highs >= 0.999 ? Filter.NONE : new Filter(gain, highs);
    }

    /** 介质有多"满"：真空 0，半个大气压以上 1，中间按气压的对数。 */
    public static double medium(double pressure) {
        if (pressure <= VACUUM_PRESSURE) {
            return 0;
        }
        if (pressure >= FULL_PRESSURE) {
            return 1;
        }
        return Math.log(pressure / VACUUM_PRESSURE) / Math.log(FULL_PRESSURE / VACUUM_PRESSURE);
    }
}
