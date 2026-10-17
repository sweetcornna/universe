package io.github.sweetcornna.universe.core.power;

/**
 * 接入电网的设备：发电机、用电设备、蓄电器都实现它，一台设备也可以兼几样。能量以焦耳计，每刻结算一次
 * （技术架构 §3.7）。
 */
public interface PowerDevice {
    /** 这一刻要用多少能量。 */
    default long demand() {
        return 0;
    }

    /** 这一刻最多能发多少能量。 */
    default long generation() {
        return 0;
    }

    /** 蓄电器这一刻最多还能充进多少。 */
    default long chargeLimit() {
        return 0;
    }

    /** 蓄电器这一刻最多能放出多少。 */
    default long dischargeLimit() {
        return 0;
    }

    /**
     * 这一刻的结算结果。
     *
     * @param satisfaction 用电满足率 0–1，供电不足时设备按它降速；不用电的设备收到 1
     * @param load         发电机的负荷 0–1：实际发出的占最多能发的多少，按它烧燃料
     * @param stored       蓄电器充进（正）或放出（负）的能量
     */
    void settle(double satisfaction, double load, long stored);
}
