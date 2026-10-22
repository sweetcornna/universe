package io.github.sweetcornna.universe.core.survival;

/**
 * 多功能工具战斗模式的等离子弹（ADR-0014）：按住使用键连发，基础伤害 5、每秒 4 发，和采矿激光共用热量。
 *
 * <p>射速用"弹药充能"算：每刻充 {@code 射速 / 20} 发，满 1 发就打出去；不开火时也照样充，但最多存 1 发，
 * 所以按下扳机的第一发立即打出，连点也快不过射速。武器校准模块的伤害、射速加成按 {@code 基础值 × (1 + 加成)} 算。
 */
public final class PlasmaWeapon {
    public static final double BASE_DAMAGE = 5;
    /** 每秒几发。 */
    public static final double BASE_RATE = 4;
    /** 按住扳机时的升温是采矿激光的几倍：连发 5 秒过热。 */
    public static final float HEAT_FACTOR = 0.8F;
    /** 弹体每刻飞几格。 */
    public static final double SPEED = 3.0;
    /** 弹体飞多少刻后消失：射程约 60 格。 */
    public static final int LIFETIME = 20;
    private static final double EPSILON = 1e-9;

    private final double rate;
    private double charge = 1;

    /** 爆能枪：每秒 {@link #BASE_RATE} 发。 */
    public PlasmaWeapon() {
        this(BASE_RATE);
    }

    /** 装在工具上的别的武器按自己的射速连发（ADR-0026 §六），充能的算法一样。 */
    public PlasmaWeapon(double rate) {
        this.rate = rate;
    }

    /**
     * 推进一刻。
     *
     * @param firing    这一刻按着扳机而且没有过热
     * @param rateBoost 射速的加成倍数（{@code 1 + 加成}）
     * @return 这一刻打出几发
     */
    public int tick(boolean firing, double rateBoost) {
        double perTick = this.rate * rateBoost / 20;
        if (!firing) {
            this.charge = Math.min(1, this.charge + perTick);
            return 0;
        }
        int shots = (int) Math.floor(this.charge + EPSILON);
        this.charge = Math.max(0, this.charge - shots) + perTick;
        return shots;
    }

    /** 一发的伤害。 */
    public static float damage(double damageBoost) {
        return (float) (BASE_DAMAGE * damageBoost);
    }
}
