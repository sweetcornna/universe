package io.github.sweetcornna.universe.core.weather;

import java.util.function.DoubleSupplier;

import io.github.sweetcornna.universe.core.galaxy.WeatherProfile;

/**
 * 一颗星球的天气轮换：晴天和有天气（下雨、下雪、沙尘、火山灰……）交替，每段持续多久随机，
 * 有天气的时间占比等于 {@link WeatherProfile#frequency()}。每段天气开始时按
 * {@link WeatherProfile#stormChance()} 决定是不是风暴。
 *
 * <p>以游戏刻计时。可变对象，由世界层的存档数据持有，只在服务端线程上用。
 */
public final class WeatherCycle {
    /** 有天气的一段平均持续多少刻（频率越高越长）。 */
    static final int ACTIVE_BASE = 6000;
    /** 天气强度每刻最多变化多少（和原版下雨一样，约 5 秒从无到满）。 */
    public static final float RAMP = 0.01F;
    /** 低于这个频率就当作从不变天。 */
    static final double NEVER = 0.005;

    private boolean active;
    private boolean storm;
    private int remaining;

    public WeatherCycle(boolean active, boolean storm, int remaining) {
        this.active = active;
        this.storm = storm;
        this.remaining = remaining;
    }

    /** 晴天开局，第一段晴天的长短也随机。 */
    public static WeatherCycle start(WeatherProfile profile, DoubleSupplier random) {
        WeatherCycle cycle = new WeatherCycle(false, false, 0);
        cycle.remaining = cycle.duration(profile, random);
        return cycle;
    }

    /**
     * 推进一刻。
     *
     * @return 这一刻是否换了天气（晴天和有天气之间切换，或者风暴开始、结束）
     */
    public boolean tick(WeatherProfile profile, DoubleSupplier random) {
        if (!hasWeather(profile)) {
            boolean changed = this.active;
            this.active = false;
            this.storm = false;
            this.remaining = Integer.MAX_VALUE;
            return changed;
        }
        if (this.remaining == Integer.MAX_VALUE) {
            // 原来从不变天（例如参数改过），重新排一段晴天
            this.remaining = this.duration(profile, random);
        }
        if (--this.remaining > 0) {
            return false;
        }
        this.active = !this.active;
        this.storm = this.active && random.getAsDouble() < profile.stormChance();
        this.remaining = this.duration(profile, random);
        return true;
    }

    /** 命令用：直接设成晴天、普通天气或风暴，持续 {@code ticks} 刻后回到正常轮换。 */
    public void force(boolean active, boolean storm, int ticks) {
        this.active = active;
        this.storm = active && storm;
        this.remaining = Math.max(1, ticks);
    }

    private int duration(WeatherProfile profile, DoubleSupplier random) {
        double f = profile.frequency();
        double activeMean = ACTIVE_BASE * (1 + f);
        double mean = this.active ? activeMean : activeMean * (1 - f) / f;
        return (int) Math.max(200, Math.round(mean * (0.5 + random.getAsDouble())));
    }

    public static boolean hasWeather(WeatherProfile profile) {
        return profile.precipitation() != WeatherProfile.Precipitation.NONE && profile.frequency() >= NEVER;
    }

    /** 强度的目标值：晴天 0，普通天气 0.55，风暴 1；落下来的雨雪不分强弱，有就是 1。 */
    public float targetIntensity(WeatherProfile profile) {
        if (!this.active) {
            return 0;
        }
        if (profile.precipitation().falls()) {
            return 1;
        }
        return this.storm ? 1 : 0.55F;
    }

    /** 是否打雷：风暴，而且这颗星球的风暴带闪电。 */
    public boolean thundering(WeatherProfile profile) {
        return this.active && this.storm && profile.lightning();
    }

    /** 向目标值靠近一步。 */
    public static float approach(float current, float target) {
        if (current < target) {
            return Math.min(target, current + RAMP);
        }
        return Math.max(target, current - RAMP);
    }

    public boolean active() {
        return this.active;
    }

    public boolean storm() {
        return this.storm;
    }

    public int remaining() {
        return this.remaining;
    }
}
