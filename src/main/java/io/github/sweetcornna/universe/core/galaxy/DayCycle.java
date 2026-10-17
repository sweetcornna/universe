package io.github.sweetcornna.universe.core.galaxy;

import java.util.Locale;

/**
 * 昼夜周期。
 *
 * <p>原版的昼夜由维度类型上的时间线驱动，时间线属于登录时同步的注册表，运行时不能新增（技术架构 §3.2），
 * 所以自转周期取有限的几档，每档对应一套环境模板。潮汐锁定的星球太阳停在天上不动，按停的位置分三档。
 */
public enum DayCycle {
    /** 半天：10 分钟一个昼夜。 */
    SHORT(12000, false, 0),
    /** 和母星相同：20 分钟。 */
    STANDARD(24000, false, 0),
    /** 40 分钟。 */
    LONG(48000, false, 0),
    /** 80 分钟。 */
    VERY_LONG(96000, false, 0),
    /** 潮汐锁定，永远是正午。 */
    LOCKED_DAY(0, true, 6000),
    /** 潮汐锁定，太阳停在地平线附近，永远是黄昏。 */
    LOCKED_TWILIGHT(0, true, 12300),
    /** 潮汐锁定，永夜。 */
    LOCKED_NIGHT(0, true, 18000);

    private final int lengthTicks;
    private final boolean locked;
    private final int lockedTime;

    DayCycle(int lengthTicks, boolean locked, int lockedTime) {
        this.lengthTicks = lengthTicks;
        this.locked = locked;
        this.lockedTime = lockedTime;
    }

    /** 一个昼夜的 tick 数；潮汐锁定时为 0。 */
    public int lengthTicks() {
        return this.lengthTicks;
    }

    public boolean locked() {
        return this.locked;
    }

    /** 潮汐锁定时太阳停留的位置，用原版 24000 tick 一天的时刻表示（6000 是正午）。 */
    public int lockedTime() {
        return this.lockedTime;
    }

    public String serializedName() {
        return this.name().toLowerCase(Locale.ROOT);
    }
}
