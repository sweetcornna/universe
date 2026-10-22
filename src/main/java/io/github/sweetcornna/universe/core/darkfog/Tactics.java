package io.github.sweetcornna.universe.core.darkfog;

/**
 * 黑雾单位和建筑的打法（ADR-0028 §二、§三）：先打信号塔，再打附近的人，进攻时打设施；守家的不离家太远。
 * 还有等离子哨塔吊射的弹道、溅射判定和预警里的方位。距离都是格。
 */
public final class Tactics {
    /** 信号塔嘲讽的范围。 */
    public static final double TAUNT_RANGE = 48;
    /** 守家时离家超过这么远就回去。 */
    public static final double DEFEND_LEASH = 48;
    /** 守家时家附近这么远内有人就打。 */
    public static final double DEFEND_ENGAGE = 32;
    /** 进攻途中这么近的人顺手打。 */
    public static final double ATTACK_ENGAGE = 16;
    /** 被人打了，这么近内追着打回去。 */
    public static final double REVENGE_RANGE = 32;
    /** 离进攻目标这么近就开始找设施打。 */
    public static final double ARRIVE = 24;
    /** 进攻途中经过的设施，这么近的顺手打。 */
    public static final double PASSING_FACILITY = 12;
    /** 打设施的伤害是打人的几倍。 */
    public static final double FACILITY_DAMAGE = 2;
    /** 减速最多减到这么多，免得冷却无限长。 */
    private static final double MIN_JAM = 0.2;

    private Tactics() {
    }

    /** 这一刻该干什么。 */
    public enum Order {
        /** 去打信号塔。 */
        TAUNT,
        /** 去打人。 */
        PLAYER,
        /** 去打设施。 */
        FACILITY,
        /** 往进攻目标走。 */
        ADVANCE,
        /** 回家。 */
        RETURN,
        /** 在家附近转。 */
        PATROL
    }

    /**
     * 单位看到的情况。找不到的东西距离填 {@link Double#POSITIVE_INFINITY}。
     *
     * @param attacking          进攻模式
     * @param homeDistance       离家多远
     * @param tauntDistance      最近的信号塔
     * @param playerDistance     最近的人离自己多远
     * @param playerHomeDistance 那个人离家多远
     * @param revengeDistance    最近打过自己的人离自己多远
     * @param facilityDistance   最近的设施离自己多远（只在进攻模式下找）
     * @param targetDistance     离进攻目标多远（没有目标是无穷远）
     */
    public record Situation(boolean attacking, double homeDistance, double tauntDistance, double playerDistance, double playerHomeDistance,
            double revengeDistance, double facilityDistance, double targetDistance) {
    }

    public static Order decide(Situation s) {
        if (s.tauntDistance() <= TAUNT_RANGE) {
            return Order.TAUNT;
        }
        if (s.revengeDistance() <= REVENGE_RANGE && (s.attacking() || s.homeDistance() <= DEFEND_LEASH + 16)) {
            return Order.PLAYER;
        }
        if (!s.attacking()) {
            if (s.homeDistance() > DEFEND_LEASH) {
                return Order.RETURN;
            }
            return s.playerHomeDistance() <= DEFEND_ENGAGE ? Order.PLAYER : Order.PATROL;
        }
        if (s.playerDistance() <= ATTACK_ENGAGE) {
            return Order.PLAYER;
        }
        boolean arrived = s.targetDistance() <= ARRIVE;
        if (s.facilityDistance() <= PASSING_FACILITY || arrived && Double.isFinite(s.facilityDistance())) {
            return Order.FACILITY;
        }
        if (Double.isFinite(s.targetDistance()) && !arrived) {
            return Order.ADVANCE;
        }
        return Order.RETURN;
    }

    /** 被干扰时的冷却：减速到 {@code jam} 倍，冷却就长到 1/jam 倍。 */
    public static int cooldown(int ticks, double jam) {
        return (int) Math.ceil(ticks / Math.max(MIN_JAM, Math.min(1, jam)));
    }

    /** 离地多高飞：突袭者贴地悬浮，游骑兵、守卫者飞得高。 */
    public static double hover(UnitKind kind) {
        return switch (kind) {
            case RAIDER -> 1.5;
            case RANGER -> 6;
            case GUARDIAN -> 10;
        };
    }

    /** 打人、打设施的距离（从机身中心算）。 */
    public static double reach(UnitKind kind) {
        return switch (kind) {
            case RAIDER -> 3.5;
            case RANGER -> 22;
            case GUARDIAN -> 18;
        };
    }

    /** 两次攻击之间隔多少刻。 */
    public static int attackInterval(UnitKind kind) {
        return switch (kind) {
            case RAIDER -> 20;
            case RANGER -> 24;
            case GUARDIAN -> 30;
        };
    }

    /**
     * 吊射：{@code ticks} 刻后正好落到 (dx, dy, dz)，每刻先减重力再走一步（和原版弹射物一样，没有空气阻力）。
     *
     * @return 初速度 {vx, vy, vz}
     */
    public static double[] ballistic(double dx, double dy, double dz, double gravity, int ticks) {
        int t = Math.max(1, ticks);
        double vy = (dy + gravity * t * (t + 1) / 2.0) / t;
        return new double[] {dx / t, vy, dz / t};
    }

    /** 吊射的飞行时间：远的飞得久，20–40 刻。 */
    public static int flightTicks(double distance) {
        return (int) Math.max(20, Math.min(40, Math.round(distance * 0.9)));
    }

    /**
     * 等离子哨塔的炮弹落地溅射：水平半径内、脚离落点不太高的才挨打，所以跳起来能躲。
     *
     * @param horizontal 离落点的水平距离
     * @param feetAbove  脚比落点高多少
     */
    public static boolean splash(double horizontal, double feetAbove, double radius) {
        return horizontal <= radius && feetAbove >= -1.5 && feetAbove <= 1.0;
    }

    /** 溅射伤害随距离减，中心满伤，边缘一半。 */
    public static double splashFactor(double horizontal, double radius) {
        if (radius <= 0) {
            return 1;
        }
        return 1 - 0.5 * Math.max(0, Math.min(1, horizontal / radius));
    }

    /**
     * 从 A 看 B 在哪个方向，8 个方位：0 北、1 东北、2 东、3 东南、4 南、5 西南、6 西、7 西北。
     * 和原版一样，北是 -Z，东是 +X。
     */
    public static int compass(double dx, double dz) {
        double degrees = Math.toDegrees(Math.atan2(dx, -dz));
        return Math.floorMod((int) Math.round(degrees / 45), 8);
    }
}
