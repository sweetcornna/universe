package io.github.sweetcornna.universe.core.terraform;

import java.util.Map;

import io.github.sweetcornna.universe.core.chem.AtmosphereMix;
import io.github.sweetcornna.universe.core.chem.Gas;
import io.github.sweetcornna.universe.core.galaxy.Hazard;
import io.github.sweetcornna.universe.core.galaxy.Planet;

/**
 * 一颗星球现用的环境（ADR-0036 §六）：本来的加上改造。
 *
 * @param planet         换过大气、温度、危害、天气的星球，给原来读 {@link Planet} 的地方用；没改造时就是原来那颗
 * @param capacity       一个标准大气压合多少单位气体
 * @param amounts        每种气体现在有多少单位
 * @param mix            成分
 * @param pressure       总气压（标准大气压）
 * @param temperature    平均温度（K）
 * @param radiationScale 辐射危害的强度倍数：1 照旧，致密恒星旁边大气够厚时 0.5；没有辐射危害时为 0
 * @param naturalWater   星球上本来就有水（海、冰、水冰矿、下雨下雪）
 * @param biomass        生物量
 */
public record EffectiveEnvironment(Planet planet, double capacity, Map<Gas, Double> amounts, AtmosphereMix mix, double pressure,
        double temperature, double radiationScale, boolean naturalWater, double biomass) {
    /** 一个标准大气压是多少千帕。 */
    public static final double KPA_PER_ATM = 101.325;

    public EffectiveEnvironment {
        amounts = Map.copyOf(amounts);
    }

    /** 这种气体的分压（标准大气压）。 */
    public double partial(Gas gas) {
        return this.mix.partial(gas, this.pressure);
    }

    /** 氧分压（千帕）。 */
    public double oxygenKpa() {
        return this.partial(Gas.OXYGEN) * KPA_PER_ATM;
    }

    public double toxicFraction() {
        return this.mix.toxicFraction();
    }

    /** 有毒气体的接触指数，见 {@link Terraform#TOXIC_LIMIT}。 */
    public double toxicIndex() {
        return this.mix.exposureIndex(this.pressure);
    }

    /** 头顶的大气柱质量（地球海平面的倍数），见 {@link Terraform#SHIELD_COLUMN}。 */
    public double shieldingColumn() {
        return Terraform.column(this.planet, this.pressure);
    }

    public boolean hasHazard(Hazard hazard) {
        return this.planet.hasHazard(hazard);
    }

    public boolean breathable() {
        return this.planet.atmosphere().breathable();
    }

    /** 这种气体还剩多少单位（大气提取器最多能取走这么多）。 */
    public double available(Gas gas) {
        return this.amounts.getOrDefault(gas, 0.0);
    }
}
