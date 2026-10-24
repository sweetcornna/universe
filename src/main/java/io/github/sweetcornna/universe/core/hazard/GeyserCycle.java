package io.github.sweetcornna.universe.core.hazard;

import io.github.sweetcornna.universe.core.procgen.Rng;
import io.github.sweetcornna.universe.core.procgen.Seeds;

/**
 * 一个间歇泉喷口的节奏（ADR-0034 §六）：安静 40–90 秒，响 3 秒（冒泡、地面轻震），喷 5 秒。喷的时候一根
 * {@value #HEIGHT} 格高的水汽柱，前半秒升起来，最后一秒落下去。
 *
 * <p>节奏只看喷口的种子和世界时间，服务端和客户端各算各的，不用同步；同一个泉眼的几个喷口种子不同，错开着喷。
 *
 * @param quiet  安静多少刻
 * @param offset 节奏的起点错开多少刻
 */
public record GeyserCycle(int quiet, int offset) {
    public static final int QUIET_MIN = 40 * 20;
    public static final int QUIET_MAX = 90 * 20;
    public static final int RUMBLE = 3 * 20;
    public static final int ERUPT = 5 * 20;
    /** 水汽柱的高度（格）。 */
    public static final double HEIGHT = 15;
    /** 水汽柱的半径（格，从喷口中心量）。 */
    public static final double RADIUS = 1.1;
    /** 柱子升起来要几刻。 */
    static final int RISE = 10;
    /** 柱子落下去要几刻。 */
    static final int FALL = 20;
    /** 柱底往上推的速度（格/刻）：没有阻力时能上到七八格，一路推着能上到柱顶。 */
    static final double LIFT_BASE = 1.05;
    /** 柱顶附近还剩的推力，保证能被推到顶。 */
    static final double LIFT_MIN = 0.16;

    public GeyserCycle {
        if (quiet <= 0 || offset < 0) {
            throw new IllegalArgumentException(quiet + ", " + offset);
        }
    }

    public enum Phase {
        QUIET, RUMBLE, ERUPT
    }

    /** 按喷口的种子定下节奏（喷口的种子是方块位置派生的）。 */
    public static GeyserCycle of(long seed) {
        Rng rng = Rng.of(Seeds.derive(seed, "geyser"));
        int quiet = rng.range(QUIET_MIN, QUIET_MAX);
        return new GeyserCycle(quiet, rng.nextInt(quiet + RUMBLE + ERUPT));
    }

    public int length() {
        return this.quiet + RUMBLE + ERUPT;
    }

    /** 这一刻在一圈节奏里的第几刻。 */
    public int tick(long time) {
        return (int) Math.floorMod(time + this.offset, (long) this.length());
    }

    public Phase phase(long time) {
        int tick = this.tick(time);
        return tick < this.quiet ? Phase.QUIET : tick < this.quiet + RUMBLE ? Phase.RUMBLE : Phase.ERUPT;
    }

    /** 进了这个阶段第几刻（从 0 起）。 */
    public int intoPhase(long time) {
        int tick = this.tick(time);
        return tick < this.quiet ? tick : tick < this.quiet + RUMBLE ? tick - this.quiet : tick - this.quiet - RUMBLE;
    }

    /** 这一刻刚进入这个阶段。 */
    public boolean starts(long time, Phase phase) {
        return this.phase(time) == phase && this.intoPhase(time) == 0;
    }

    /** 离下一次喷还有几刻；正在喷时为 0。 */
    public int untilEruption(long time) {
        int tick = this.tick(time);
        int start = this.quiet + RUMBLE;
        return tick >= start ? 0 : start - tick;
    }

    /** 这一刻水汽柱有多高（格）；不在喷时为 0。 */
    public double column(long time) {
        return columnAt(this.phase(time), this.intoPhase(time));
    }

    /** 喷了 {@code into} 刻时柱子有多高。 */
    public static double columnAt(Phase phase, int into) {
        if (phase != Phase.ERUPT) {
            return 0;
        }
        if (into < RISE) {
            return HEIGHT * (into + 1) / RISE;
        }
        if (into >= ERUPT - FALL) {
            return HEIGHT * (ERUPT - into) / (double) FALL;
        }
        return HEIGHT;
    }

    /**
     * 柱子里离喷口 {@code above} 格高的地方往上推的速度（格/刻）；不在柱子里为 0。越往上越弱，但到柱顶前一直推得动。
     *
     * @param column 柱子此刻的高度（{@link #column}）
     */
    public static double lift(double above, double column) {
        if (column <= 0 || above < -0.5 || above > column) {
            return 0;
        }
        return Math.max(LIFT_MIN, LIFT_BASE * (1 - Math.max(0, above) / HEIGHT));
    }

    /** 离柱子中心水平 {@code distance} 格在不在柱子里。 */
    public static boolean inColumn(double distance) {
        return distance <= RADIUS;
    }
}
