package io.github.sweetcornna.universe.core.mission;

import java.util.Optional;

/**
 * 地标任务要去的地方（ADR-0027 §六）：哪颗星球（天体路径，比如 {@code s3/p2}）、哪种地标、中心的 x、z，离着陆点多远。
 * 存在 {@link Mission#item()} 里，写成 {@code s3/p2|observatory|120|-340|420}。
 *
 * @param body     天体路径
 * @param type     地标种类的 id
 * @param distance 离着陆点的水平距离，算报酬用
 */
public record LandmarkTarget(String body, String type, int x, int z, int distance) {
    /** 走到中心多近算到了。 */
    public static final int ARRIVE = 16;

    public String encode() {
        return this.body + "|" + this.type + "|" + this.x + "|" + this.z + "|" + this.distance;
    }

    public static Optional<LandmarkTarget> parse(String text) {
        String[] parts = text.split("\\|");
        if (parts.length != 5 || parts[0].isEmpty() || parts[1].isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.of(new LandmarkTarget(parts[0], parts[1], Integer.parseInt(parts[2]), Integer.parseInt(parts[3]),
                    Integer.parseInt(parts[4])));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    /** 这个位置算不算到了（只看水平距离）。 */
    public boolean reached(double x, double z) {
        double dx = x - (this.x + 0.5);
        double dz = z - (this.z + 0.5);
        return dx * dx + dz * dz <= ARRIVE * ARRIVE;
    }
}
