package io.github.sweetcornna.universe.core.ecology;

/**
 * 卵的孵化（ADR-0026 §九）：放在地上 5 分钟孵出幼崽，蛋壳先后裂两道。
 */
public final class Incubation {
    /** 孵化要几刻：5 分钟。 */
    public static final int HATCH_TICKS = 6000;
    /** 蛋壳的样子：完好、裂一道、裂两道。 */
    public static final int STAGES = 3;

    private Incubation() {
    }

    /** 孵了 {@code ticks} 刻的卵是哪个样子，0 到 {@link #STAGES} - 1。 */
    public static int stage(int ticks) {
        return (int) Math.clamp((long) ticks * STAGES / HATCH_TICKS, 0, STAGES - 1);
    }

    public static boolean hatched(int ticks) {
        return ticks >= HATCH_TICKS;
    }

    /** 孵了几成，0–1。 */
    public static double progress(int ticks) {
        return Math.clamp((double) ticks / HATCH_TICKS, 0, 1);
    }
}
