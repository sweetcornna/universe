package io.github.sweetcornna.universe.core.sentinel;

import java.util.Arrays;

/**
 * 通缉时一波守卫机的编成（ADR-0026 §四）。打光一波升一级，再来下一波；警觉的星球每波多一架无人机。
 *
 * <pre>
 * 等级  无人机  重型  维修  召唤  四足机  步行机
 *  1      2
 *  2      3            1
 *  3      2      1           1      1
 *  4      2      1     1     1      2
 *  5      2      1     2     1      1       1
 * </pre>
 */
public final class Wave {
    private static final int[][] TABLE = {
            // DRONE, HEAVY, REPAIR, SUMMONER, QUADRUPED, WALKER
            {2, 0, 0, 0, 0, 0},
            {3, 0, 1, 0, 0, 0},
            {2, 1, 0, 1, 1, 0},
            {2, 1, 1, 1, 2, 0},
            {2, 1, 2, 1, 1, 1},
    };
    public static final Wave NONE = new Wave(0, new int[SentinelKind.values().length]);

    private final int level;
    private final int[] counts;

    private Wave(int level, int[] counts) {
        this.level = level;
        this.counts = counts;
    }

    /** 这一级通缉的一波；没被通缉、星球没有守卫机时是空的。 */
    public static Wave of(int level, SentinelLevel planet) {
        if (level <= 0 || !planet.present()) {
            return NONE;
        }
        int clamped = Math.min(level, Wanted.MAX_LEVEL);
        int[] counts = Arrays.copyOf(TABLE[clamped - 1], SentinelKind.values().length);
        if (planet == SentinelLevel.AGGRESSIVE) {
            counts[SentinelKind.DRONE.ordinal()]++;
        }
        return new Wave(clamped, counts);
    }

    public int level() {
        return this.level;
    }

    public int count(SentinelKind kind) {
        return this.counts[kind.ordinal()];
    }

    /** 一共几架。 */
    public int total() {
        return Arrays.stream(this.counts).sum();
    }

    public boolean empty() {
        return this.total() == 0;
    }
}
