package io.github.sweetcornna.universe.core.audio;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * 同一片地方一小段时间里只放头几声（ADR-0029 §五）。
 *
 * <p>一次清掉整座黑雾基地时，十几座建筑在同一两秒里倒塌，每座都放爆炸和崩塌声（传得很远），叠在一起只是一片噪音。
 * 头几声照常放，后面的跳过；隔开一段时间、或者离得够远的照常放。
 */
public final class SoundBudget {
    private record Play(long time, double x, double y, double z) {
    }

    private final int max;
    private final long window;
    private final double radius;
    private final Deque<Play> recent = new ArrayDeque<>();

    /**
     * @param max    窗口里同一片地方最多放几声
     * @param window 窗口多长（刻）
     * @param radius 多近算同一片地方（格）
     */
    public SoundBudget(int max, long window, double radius) {
        this.max = max;
        this.window = window;
        this.radius = radius;
    }

    /** 这一声放不放；放的话记下来。时间要单调不减。 */
    public boolean allow(long time, double x, double y, double z) {
        while (!this.recent.isEmpty() && time - this.recent.peekFirst().time() >= this.window) {
            this.recent.pollFirst();
        }
        int near = 0;
        double r2 = this.radius * this.radius;
        for (Play play : this.recent) {
            double dx = play.x() - x;
            double dy = play.y() - y;
            double dz = play.z() - z;
            if (dx * dx + dy * dy + dz * dz <= r2) {
                near++;
            }
        }
        if (near >= this.max) {
            return false;
        }
        this.recent.addLast(new Play(time, x, y, z));
        return true;
    }
}
