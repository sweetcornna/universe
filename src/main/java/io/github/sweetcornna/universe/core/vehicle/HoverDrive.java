package io.github.sweetcornna.universe.core.vehicle;

/**
 * 悬浮车离地多高（ADR-0026）：车底的推力垫托着车，悬在地面或水面上 {@link #HEIGHT} 格。像一根带阻尼的弹簧：
 * 低了往上托，高了往下拉，几刻就稳住，不管星球的重力多大都停在同一个高度。离地超过 {@link #SENSE} 格，推力垫够不着地面，
 * 只剩重力往下掉（冲下悬崖的时候）。
 * <p>
 * "地面"由实体往下探：方块和水面都算，所以开得过湖和海。前后左右的坡交给实体的碰撞和爬台阶。
 */
public final class HoverDrive {
    /** 车底离地多高（格）。 */
    public static final double HEIGHT = 1.0;
    /** 推力垫够得着多远（格）：再高就往下掉。 */
    public static final double SENSE = 4.0;
    /** 弹簧：差一格每刻给多少上下的速度。 */
    static final double STIFFNESS = 0.25;
    /** 阻尼：每刻上下的速度剩多少。 */
    static final double DAMPING = 0.6;
    /** 推力垫托着的时候，上下的速度最快多少格/刻。 */
    static final double MAX_LIFT = 0.5;
    /** 每刻的重力（乘上星球的表面重力），只在够不着地面时有用。 */
    public static final double GRAVITY = 0.08;

    private HoverDrive() {
    }

    /** 推力垫够得着地面：油门和方向盘有抓地力。 */
    public static boolean hovering(double clearance) {
        return clearance <= SENSE;
    }

    /**
     * 下一刻的上下速度。
     *
     * @param clearance 车底离下面的地面或水面多高（格）；探不到地面时传一个大于 {@link #SENSE} 的数
     * @param vy        现在的上下速度（格/刻）
     * @param gravity   星球的表面重力倍数（地球 1）
     */
    public static double lift(double clearance, double vy, double gravity) {
        if (!hovering(clearance)) {
            return (vy - GRAVITY * gravity) * 0.98;
        }
        double next = vy * DAMPING + (HEIGHT - clearance) * STIFFNESS;
        return Math.max(-MAX_LIFT, Math.min(MAX_LIFT, next));
    }
}
