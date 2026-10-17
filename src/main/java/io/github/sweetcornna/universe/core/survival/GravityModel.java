package io.github.sweetcornna.universe.core.survival;

/**
 * 星球重力对生物的影响（玩法设计 §3.3）。原版的重力加速度、起跳速度、安全摔落高度、摔落伤害倍数都是属性，
 * 这里算出每颗星球要乘上的系数。
 *
 * <ul>
 *   <li>重力加速度按星球重力等比缩放，低重力跳得高、落得慢。</li>
 *   <li>高重力下起跳速度按 √g 加强，跳的高度只降一成，仍然跳得上一格（外骨骼的腿部助力）。</li>
 *   <li>摔落：着地速度的平方和 g·h 成正比，所以安全高度除以 g、伤害乘以 g。</li>
 * </ul>
 */
public final class GravityModel {
    /** 原版的安全摔落高度。 */
    static final double SAFE_FALL = 3;

    private GravityModel() {
    }

    /** 重力加速度的倍数。 */
    public static double gravity(double g) {
        return g;
    }

    /** 起跳速度的倍数：低重力不变，高重力按 √g 再打九五折。 */
    public static double jump(double g) {
        return g <= 1 ? 1 : Math.sqrt(g) * 0.95;
    }

    /** 安全摔落高度（格）。 */
    public static double safeFall(double g) {
        return SAFE_FALL / g;
    }

    /** 摔落伤害的倍数。 */
    public static double fallDamage(double g) {
        return g;
    }

    /** 起跳高度（格），给测试和界面用：照原版每刻先移动、再减重力、再乘 0.98 的空气阻力，原版大约 1.25 格。 */
    public static double jumpHeight(double g) {
        double v = 0.42 * jump(g);
        double a = 0.08 * gravity(g);
        double y = 0;
        double top = 0;
        for (int tick = 0; tick < 400 && v > 0; tick++) {
            y += v;
            top = Math.max(top, y);
            v = (v - a) * 0.98;
        }
        return top;
    }
}
