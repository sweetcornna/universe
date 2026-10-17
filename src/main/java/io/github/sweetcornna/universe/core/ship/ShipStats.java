package io.github.sweetcornna.universe.core.ship;

import java.util.EnumMap;
import java.util.Map;

/**
 * 由部件统计出的飞船性能。
 *
 * @param blocks        方块总数
 * @param mass          总质量（吨，不含燃料）
 * @param thrusters     推进器数
 * @param fuelTanks     燃料舱数
 * @param impulse       脉冲引擎数
 * @param thrust        推进器的总推力，用"在 1 g 下能托起多少吨"表示
 * @param fuelCapacity  燃料容量
 */
public record ShipStats(int blocks, double mass, int thrusters, int fuelTanks, int impulse, double thrust, double fuelCapacity) {
    /** 一个推进器在 1 g 下托起的吨数。 */
    public static final double THRUST_PER_THRUSTER = 12;
    /** 一个燃料舱的容量。 */
    public static final double FUEL_PER_TANK = 2000;

    public static ShipStats of(Map<ShipPart, Integer> counts) {
        int blocks = 0;
        double mass = 0;
        for (Map.Entry<ShipPart, Integer> e : counts.entrySet()) {
            blocks += e.getValue();
            mass += e.getKey().mass() * e.getValue();
        }
        int thrusters = counts.getOrDefault(ShipPart.THRUSTER, 0);
        int tanks = counts.getOrDefault(ShipPart.FUEL_TANK, 0);
        int impulse = counts.getOrDefault(ShipPart.IMPULSE_ENGINE, 0);
        return new ShipStats(blocks, mass, thrusters, tanks, impulse, thrusters * THRUST_PER_THRUSTER, tanks * FUEL_PER_TANK);
    }

    /** 方便测试和命令：按部件逐个列出数量。 */
    public static ShipStats of(Object... partsAndCounts) {
        Map<ShipPart, Integer> counts = new EnumMap<>(ShipPart.class);
        for (int i = 0; i < partsAndCounts.length; i += 2) {
            counts.merge((ShipPart) partsAndCounts[i], (Integer) partsAndCounts[i + 1], Integer::sum);
        }
        return of(counts);
    }

    /** 推重比：推力和重量之比，大于 1 才飞得起来。 */
    public double thrustToWeight(double gravity) {
        if (gravity <= 0) {
            return this.thrust > 0 ? Double.POSITIVE_INFINITY : 0;
        }
        return this.thrust / (Math.max(1, this.mass) * gravity);
    }

    /** 能不能在这个重力下起飞。 */
    public boolean canLift(double gravity) {
        return this.thrustToWeight(gravity) > 1.0;
    }
}
