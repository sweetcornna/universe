package io.github.sweetcornna.universe.core.ship;

import java.util.Map;

import io.github.sweetcornna.universe.core.tech.Grade;

/**
 * 飞船的评级（ADR-0024）：类型看装了什么，档次看用料。
 *
 * <p>类型按三项分数取最高的一项，至少 {@link #SPECIALTY} 分才算，否则是穿梭机。档次的品质分是高级部件（Mk.II 部件、
 * 光子炮、护盾发生器）占功能部件的比例 × 70，加合金船体板占全船方块的比例 × 30。
 *
 * @param type    类型
 * @param grade   档次
 * @param quality 品质分（0–100）
 */
public record ShipRating(ShipType type, Grade grade, double quality) {
    /** 一项分数至少这么多，才算这个类型。 */
    public static final double SPECIALTY = 6;
    /** 每多少格货舱算 1 分运输分。 */
    public static final int CARGO_SLOTS_PER_POINT = 9;
    /** 档次的分界：B、A、S 各自的最低品质分。 */
    static final double[] GRADE_FLOOR = {0, 25, 50, 75};
    /** 档次给类型专长的加成：C、B、A、S。 */
    static final double[] SPECIALTY_BONUS = {0, 0.10, 0.20, 0.35};
    /** 技术槽：C、B、A、S。 */
    static final int[] SLOTS = {3, 4, 5, 6};

    public static final ShipRating DEFAULT = new ShipRating(ShipType.SHUTTLE, Grade.C, 0);

    /**
     * @param counts     各种部件的个数
     * @param cargoSlots 船上容器一共多少格
     */
    public static ShipRating of(Map<ShipPart, Integer> counts, int cargoSlots) {
        double combat = combatScore(counts);
        double cargo = cargoScore(cargoSlots);
        double explore = exploreScore(counts);
        ShipType type = ShipType.SHUTTLE;
        double best = SPECIALTY - 1e-9;
        if (combat > best) {
            type = ShipType.FIGHTER;
            best = combat;
        }
        if (cargo > best) {
            type = ShipType.HAULER;
            best = cargo;
        }
        if (explore > best) {
            type = ShipType.EXPLORER;
        }
        double quality = quality(counts);
        Grade grade = Grade.C;
        for (Grade g : Grade.values()) {
            if (quality >= GRADE_FLOOR[g.ordinal()]) {
                grade = g;
            }
        }
        return new ShipRating(type, grade, quality);
    }

    /** 战斗分：光子炮 3 分，采矿光束 1 分，护盾发生器 2 分。 */
    public static double combatScore(Map<ShipPart, Integer> counts) {
        return 3 * count(counts, ShipPart.PHOTON_CANNON) + count(counts, ShipPart.MINING_BEAM)
                + 2 * count(counts, ShipPart.SHIELD_GENERATOR);
    }

    /** 运输分：每 {@link #CARGO_SLOTS_PER_POINT} 格货舱 1 分。 */
    public static double cargoScore(int cargoSlots) {
        return (double) cargoSlots / CARGO_SLOTS_PER_POINT;
    }

    /** 探索分：跃迁引擎 3 分（Mk.II 4 分），燃料舱 0.5 分（Mk.II 1 分）。 */
    public static double exploreScore(Map<ShipPart, Integer> counts) {
        return 3 * count(counts, ShipPart.WARP_DRIVE) + 4 * count(counts, ShipPart.WARP_DRIVE_MK2)
                + 0.5 * count(counts, ShipPart.FUEL_TANK) + count(counts, ShipPart.FUEL_TANK_MK2);
    }

    /** 品质分（0–100）。 */
    public static double quality(Map<ShipPart, Integer> counts) {
        int functional = 0;
        int advanced = 0;
        int blocks = 0;
        for (Map.Entry<ShipPart, Integer> e : counts.entrySet()) {
            int n = e.getValue();
            blocks += n;
            switch (e.getKey()) {
                case THRUSTER, FUEL_TANK, IMPULSE_ENGINE, WARP_DRIVE, MINING_BEAM -> functional += n;
                case THRUSTER_MK2, FUEL_TANK_MK2, IMPULSE_ENGINE_MK2, WARP_DRIVE_MK2, PHOTON_CANNON, SHIELD_GENERATOR -> {
                    functional += n;
                    advanced += n;
                }
                default -> {
                }
            }
        }
        double parts = functional == 0 ? 0 : 70.0 * advanced / functional;
        double plating = blocks == 0 ? 0 : 30.0 * count(counts, ShipPart.ALLOY_PLATING) / blocks;
        return Math.min(100, parts + plating);
    }

    /** 类型专长的加成（0.35 表示 +35%）。 */
    public double bonus() {
        return SPECIALTY_BONUS[this.grade.ordinal()];
    }

    /** 技术槽的格数。 */
    public int slots() {
        return SLOTS[this.grade.ordinal()];
    }

    /** 某个档次的技术槽格数。 */
    public static int slots(Grade grade) {
        return SLOTS[grade.ordinal()];
    }

    private static int count(Map<ShipPart, Integer> counts, ShipPart part) {
        return counts.getOrDefault(part, 0);
    }
}
