package io.github.sweetcornna.universe.core.ship;

import java.util.EnumMap;
import java.util.Map;

import io.github.sweetcornna.universe.core.space.DriveTier;
import io.github.sweetcornna.universe.core.space.Warp;
import io.github.sweetcornna.universe.core.tech.Stat;
import io.github.sweetcornna.universe.core.tech.TechBonuses;

/**
 * 由部件统计出的飞船性能。
 *
 * @param blocks             方块总数
 * @param mass               总质量（吨，不含燃料）
 * @param thrusters          推进器数（含 Mk.II）
 * @param fuelTanks          燃料舱数（含 Mk.II）
 * @param impulse            脉冲引擎数（含 Mk.II）
 * @param thrust             推进器的总推力，用"在 1 g 下能托起多少吨"表示
 * @param fuelCapacity       燃料容量
 * @param warpDrives         跃迁引擎数（含 Mk.II）
 * @param advancedWarpDrives 其中 Mk.II 跃迁引擎的台数
 * @param impulseSpeed       开脉冲引擎能到的最高速度（格/刻）；没有脉冲引擎时为 0
 * @param hull               船体（生命值）上限
 * @param shield             护盾上限
 * @param cannons            光子炮门数
 * @param beams              采矿光束台数
 * @param fuelUse            燃料消耗的倍数（技术和穿梭机的专长，ADR-0024）
 * @param damage             武器伤害的倍数
 * @param fireRate           光子炮射速的倍数
 * @param warpBonus          技术加的跃迁航程（光年）
 * @param warpScale          跃迁航程的倍数（探索船的专长）
 * @param drive              装着的跃迁驱动档次
 */
public record ShipStats(int blocks, double mass, int thrusters, int fuelTanks, int impulse, double thrust, double fuelCapacity,
        int warpDrives, int advancedWarpDrives, double impulseSpeed, double hull, double shield, int cannons, int beams,
        double fuelUse, double damage, double fireRate, double warpBonus, double warpScale, DriveTier drive) {
    /** 一个推进器在 1 g 下托起的吨数。 */
    public static final double THRUST_PER_THRUSTER = 12;
    /** 一个 Mk.II 推进器在 1 g 下托起的吨数（ADR-0017）。 */
    public static final double THRUST_PER_THRUSTER_MK2 = 20;
    /** 一个燃料舱的容量。 */
    public static final double FUEL_PER_TANK = 2000;
    /** 一个 Mk.II 燃料舱的容量。 */
    public static final double FUEL_PER_TANK_MK2 = 5000;
    /** 一台脉冲引擎能推满多少吨的船；船更重时开不到最高速度。 */
    public static final double IMPULSE_LIFT = 200;
    public static final double IMPULSE_LIFT_MK2 = 300;
    /** 脉冲引擎的最高速度（格/刻）。 */
    public static final double IMPULSE_SPEED = 40;
    public static final double IMPULSE_SPEED_MK2 = 60;
    /** 推满之前的起步速度：再重的船开脉冲引擎也有这么快。 */
    private static final double IMPULSE_FLOOR = 6;
    /** 船体：基础 40 点，每个方块 1 点（最多算 200 个方块），每块合金船体板另加 4 点。 */
    public static final double HULL_BASE = 40;
    public static final double HULL_BLOCK_CAP = 200;
    public static final double HULL_PER_ALLOY = 4;
    /** 一台护盾发生器的护盾。 */
    public static final double SHIELD_PER_GENERATOR = 40;

    public static ShipStats of(Map<ShipPart, Integer> counts) {
        int blocks = 0;
        double mass = 0;
        for (Map.Entry<ShipPart, Integer> e : counts.entrySet()) {
            blocks += e.getValue();
            mass += e.getKey().mass() * e.getValue();
        }
        int thrusters = count(counts, ShipPart.THRUSTER);
        int thrustersMk2 = count(counts, ShipPart.THRUSTER_MK2);
        int tanks = count(counts, ShipPart.FUEL_TANK);
        int tanksMk2 = count(counts, ShipPart.FUEL_TANK_MK2);
        int impulse = count(counts, ShipPart.IMPULSE_ENGINE);
        int impulseMk2 = count(counts, ShipPart.IMPULSE_ENGINE_MK2);
        int warp = count(counts, ShipPart.WARP_DRIVE);
        int warpMk2 = count(counts, ShipPart.WARP_DRIVE_MK2);
        double impulseSpeed = 0;
        if (impulse + impulseMk2 > 0) {
            // 推得动的吨数和最高速度按台数加权；船比引擎推得动的重，速度按比例降下来
            double share = Math.min(1, (impulse * IMPULSE_LIFT + impulseMk2 * IMPULSE_LIFT_MK2) / Math.max(1, mass));
            double cap = (impulse * IMPULSE_SPEED + impulseMk2 * IMPULSE_SPEED_MK2) / (impulse + impulseMk2);
            impulseSpeed = IMPULSE_FLOOR + (cap - IMPULSE_FLOOR) * share;
        }
        double hull = HULL_BASE + Math.min(blocks, HULL_BLOCK_CAP) + count(counts, ShipPart.ALLOY_PLATING) * HULL_PER_ALLOY;
        return new ShipStats(blocks, mass, thrusters + thrustersMk2, tanks + tanksMk2, impulse + impulseMk2,
                thrusters * THRUST_PER_THRUSTER + thrustersMk2 * THRUST_PER_THRUSTER_MK2,
                tanks * FUEL_PER_TANK + tanksMk2 * FUEL_PER_TANK_MK2,
                warp + warpMk2, warpMk2, impulseSpeed, hull, count(counts, ShipPart.SHIELD_GENERATOR) * SHIELD_PER_GENERATOR,
                count(counts, ShipPart.PHOTON_CANNON), count(counts, ShipPart.MINING_BEAM), 1, 1, 1, 0, 1, DriveTier.BASIC);
    }

    /**
     * 加上评级的专长和装着的技术（ADR-0024）。专长只加在类型对应的那一项上：战斗机加伤害，运输船加船体和护盾，
     * 探索船加跃迁航程，穿梭机省燃料。
     */
    public ShipStats tuned(ShipRating rating, TechBonuses tech, DriveTier drive) {
        double specialty = 1 + rating.bonus();
        ShipType type = rating.type();
        double fuelUse = tech.reduction(Stat.FUEL_USE) / (type == ShipType.SHUTTLE ? specialty : 1);
        double damage = tech.boost(Stat.DAMAGE) * (type == ShipType.FIGHTER ? specialty : 1);
        double hauler = type == ShipType.HAULER ? specialty : 1;
        return new ShipStats(this.blocks, this.mass, this.thrusters, this.fuelTanks, this.impulse, this.thrust, this.fuelCapacity,
                this.warpDrives, this.advancedWarpDrives, this.impulseSpeed * tech.boost(Stat.PULSE_SPEED),
                this.hull * tech.boost(Stat.HULL) * hauler, this.shield * tech.boost(Stat.SHIP_SHIELD) * hauler, this.cannons, this.beams,
                fuelUse, damage, tech.boost(Stat.FIRE_RATE), tech.get(Stat.WARP_RANGE),
                type == ShipType.EXPLORER ? specialty : 1, drive);
    }

    private static int count(Map<ShipPart, Integer> counts, ShipPart part) {
        return counts.getOrDefault(part, 0);
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

    /** 跃迁射程（光年）；没有跃迁引擎时为 0。技术加的光年和探索船的倍数也算上。 */
    public double warpRange() {
        if (this.warpDrives <= 0) {
            return 0;
        }
        return (Warp.range(this.warpDrives - this.advancedWarpDrives, this.advancedWarpDrives) + this.warpBonus) * this.warpScale;
    }

    /** 有没有武器（光子炮或采矿光束）。 */
    public boolean armed() {
        return this.cannons > 0 || this.beams > 0;
    }
}
