package io.github.sweetcornna.universe.core.sentinel;

/**
 * 地面守卫机的背后弱点（ADR-0026）：四足机、步行机背上是散热口，从后面打伤害翻倍。
 *
 * <p>角度用 Minecraft 的偏航角：0° 朝南（+z），90° 朝西（-x）；机身朝向是 (-sin, cos)。
 */
public final class Flank {
    /** 背后的扇形：正后方左右各 60°，一共 120°。 */
    public static final double REAR_HALF_ANGLE = 60;
    public static final float BACK_MULTIPLIER = 2;
    private static final double REAR_COS = Math.cos(Math.toRadians(REAR_HALF_ANGLE));

    private Flank() {
    }

    /**
     * 打过来的位置在不在机身背后。
     *
     * @param bodyYaw 机身的偏航角（度）
     * @param dx      打过来的位置减去机身中心，x
     * @param dz      同上，z
     */
    public static boolean fromBehind(float bodyYaw, double dx, double dz) {
        double length = Math.sqrt(dx * dx + dz * dz);
        if (length < 1e-4) {
            return false;
        }
        double yaw = Math.toRadians(bodyYaw);
        double facing = (-Math.sin(yaw) * dx + Math.cos(yaw) * dz) / length;
        return facing <= -REAR_COS;
    }

    /** 从背后打的伤害翻倍，别的方向不变。 */
    public static float damage(float damage, float bodyYaw, double dx, double dz) {
        return fromBehind(bodyYaw, dx, dz) ? damage * BACK_MULTIPLIER : damage;
    }
}
