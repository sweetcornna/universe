package io.github.sweetcornna.universe.core.chem.reaction;

/**
 * 反应釜在某个工况下跑出来的数（ADR-0036 §四），界面上那一行就是它。
 *
 * @param equilibrium      平衡转化率，0–1
 * @param singlePass       单程转化率，0–1：没反应的原料留在釜里接着反应，所以它决定的是速度
 * @param batchesPerMinute 每分钟做几批
 * @param productPerMinute 每分钟出几个主产物
 * @param power            用电（J/刻）
 * @param catalyzed        放了对的催化剂，或者这条反应不用催化剂
 */
public record ReactorResult(double equilibrium, double singlePass, double batchesPerMinute, double productPerMinute,
        double power, boolean catalyzed) {
}
