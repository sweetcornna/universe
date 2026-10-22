package io.github.sweetcornna.universe.core.darkfog;

/**
 * 巢穴派出去的一次飞行（ADR-0028 §八）：一个中继站或者一颗种子。按游戏时间走三段：
 *
 * <ul>
 *   <li>起飞：从巢穴往外飞，太空里有实体，打得掉。</li>
 *   <li>路上：只记时间。</li>
 *   <li>到达：中继站往星球上降落，种子进入目标恒星系；有人在附近就有实体，打得掉。</li>
 * </ul>
 *
 * 中继站到达时才挑落点（{@link #land}），挑不到就在轨道上等一阵再试（{@link #retryArrival}）。
 */
public final class Flight {
    public enum Kind {
        RELAY,
        SEED
    }

    public enum Phase {
        DEPART,
        CRUISE,
        ARRIVE,
        DONE
    }

    private final int id;
    private final Kind kind;
    /** 从哪个恒星系出发（巢穴所在的）。 */
    private final int system;
    /** 种子去的恒星系；中继站和出发的一样。 */
    private int target;
    /** 中继站去的星球维度（{@code universe:s3/p2} 这样的 ID）；种子是空的。 */
    private String dimension;
    private final int level;
    /** 出发点：太空维度里巢穴的位置。 */
    private final double fromX;
    private final double fromY;
    private final double fromZ;
    private final long launched;
    private long departEnd;
    private long arriveStart;
    private long end;
    /** 挑落点失败了几次。 */
    private int attempts;
    private boolean siteChosen;
    private int siteX;
    private int siteZ;
    /** 落点是补中继站的那处基地；新落地是 -1。 */
    private int baseId = -1;
    /** 实体的血量（满血的比例）：人走开、实体收回，再放出来时照这个。 */
    private float health = 1;

    public Flight(int id, Kind kind, int system, int target, String dimension, int level, double fromX, double fromY, double fromZ, long launched,
            long departEnd, long arriveStart, long end) {
        this.id = id;
        this.kind = kind;
        this.system = system;
        this.target = target;
        this.dimension = dimension;
        this.level = DarkFogLevels.clamp(level);
        this.fromX = fromX;
        this.fromY = fromY;
        this.fromZ = fromZ;
        this.launched = launched;
        this.departEnd = departEnd;
        this.arriveStart = arriveStart;
        this.end = end;
    }

    /** 读存档用。 */
    public Flight(int id, Kind kind, int system, int target, String dimension, int level, double[] from, long[] times, int attempts,
            int[] site, float health) {
        this(id, kind, system, target, dimension, level, from[0], from[1], from[2], times[0], times[1], times[2], times[3]);
        this.attempts = attempts;
        if (site.length >= 3) {
            this.siteChosen = true;
            this.siteX = site[0];
            this.siteZ = site[1];
            this.baseId = site[2];
        }
        this.health = Math.max(0.01F, Math.min(1, health));
    }

    public Phase phase(long now) {
        if (now < this.departEnd) {
            return Phase.DEPART;
        }
        if (now < this.arriveStart) {
            return Phase.CRUISE;
        }
        return now < this.end ? Phase.ARRIVE : Phase.DONE;
    }

    /** 走了多少，0–1（星图上画种子走到哪了）。 */
    public double progress(long now) {
        long total = this.end - this.launched;
        return total <= 0 ? 1 : Math.max(0, Math.min(1, (now - this.launched) / (double) total));
    }

    /** 这一段走了多少，0–1。 */
    public double phaseProgress(long now) {
        long start;
        long stop;
        switch (this.phase(now)) {
            case DEPART -> {
                start = this.launched;
                stop = this.departEnd;
            }
            case CRUISE -> {
                start = this.departEnd;
                stop = this.arriveStart;
            }
            case ARRIVE -> {
                start = this.arriveStart;
                stop = this.end;
            }
            default -> {
                return 1;
            }
        }
        return stop <= start ? 1 : Math.max(0, Math.min(1, (now - start) / (double) (stop - start)));
    }

    /** 落点挑不到：在轨道上等 {@code wait} 刻再从头降落一次。 */
    public void retryArrival(long now, long wait) {
        long duration = this.end - this.arriveStart;
        this.arriveStart = now + wait;
        this.end = this.arriveStart + duration;
        this.attempts++;
        this.siteChosen = false;
        this.baseId = -1;
    }

    /** 种子的目的地有了巢穴：改去 {@code target}，从现在起再飞 {@code cruise} 刻。 */
    public void retarget(int target, long now, long cruise) {
        long duration = this.end - this.arriveStart;
        this.target = target;
        this.arriveStart = now + cruise;
        this.end = this.arriveStart + duration;
        this.attempts++;
    }

    /** 调试命令用：直接跳到这一段的开头（{@link Phase#DONE} 是马上到）；已经过了这一段就不动。 */
    public void skipTo(Phase phase, long now) {
        long arrive = this.end - this.arriveStart;
        switch (phase) {
            case DEPART -> {
            }
            case CRUISE -> {
                if (now < this.departEnd) {
                    long cruise = this.arriveStart - this.departEnd;
                    this.departEnd = now;
                    this.arriveStart = now + cruise;
                    this.end = this.arriveStart + arrive;
                }
            }
            case ARRIVE -> {
                if (now < this.arriveStart) {
                    this.departEnd = Math.min(this.departEnd, now);
                    this.arriveStart = now;
                    this.end = now + arrive;
                }
            }
            case DONE -> {
                this.departEnd = Math.min(this.departEnd, now);
                this.arriveStart = Math.min(this.arriveStart, now);
                this.end = Math.min(this.end, now);
            }
        }
    }

    /** 挑好了落点：({@code x}, {@code z})，补中继站时 {@code baseId} 是那处基地。 */
    public void land(String dimension, int x, int z, int baseId) {
        this.dimension = dimension;
        this.siteChosen = true;
        this.siteX = x;
        this.siteZ = z;
        this.baseId = baseId;
    }

    public int id() {
        return this.id;
    }

    public Kind kind() {
        return this.kind;
    }

    public int system() {
        return this.system;
    }

    public int target() {
        return this.target;
    }

    public String dimension() {
        return this.dimension;
    }

    public int level() {
        return this.level;
    }

    public double fromX() {
        return this.fromX;
    }

    public double fromY() {
        return this.fromY;
    }

    public double fromZ() {
        return this.fromZ;
    }

    public long launched() {
        return this.launched;
    }

    public long departEnd() {
        return this.departEnd;
    }

    public long arriveStart() {
        return this.arriveStart;
    }

    public long end() {
        return this.end;
    }

    public int attempts() {
        return this.attempts;
    }

    public boolean siteChosen() {
        return this.siteChosen;
    }

    public int siteX() {
        return this.siteX;
    }

    public int siteZ() {
        return this.siteZ;
    }

    public int baseId() {
        return this.baseId;
    }

    public float health() {
        return this.health;
    }

    public void setHealth(float health) {
        this.health = Math.max(0.01F, Math.min(1, health));
    }
}
