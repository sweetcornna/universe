package io.github.sweetcornna.universe.core.darkfog;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.BiPredicate;
import java.util.function.IntPredicate;

import io.github.sweetcornna.universe.core.galaxy.StarSystem;
import io.github.sweetcornna.universe.core.procgen.Rng;

/**
 * 中继站和种子怎么飞（ADR-0028 §八）：每一段多久、种子去哪个恒星系、中继站落在哪。时间都是刻。
 *
 * <ul>
 *   <li>中继站：起飞 45 秒，路上 3–5 分钟，降落 30 秒。落点离玩家的设施 220–480 格，离别的基地够远，不在护盾里。</li>
 *   <li>种子：起飞、到达各 1 分钟，路上按距离（8 分钟 + 每光年 3 分钟，12–60 分钟）。只去 15 光年内最近的、还没有巢穴的恒星系。</li>
 * </ul>
 */
public final class FlightPlan {
    public static final int RELAY_DEPART_TICKS = 45 * 20;
    public static final int RELAY_CRUISE_MIN_TICKS = 3 * 1200;
    public static final int RELAY_CRUISE_MAX_TICKS = 5 * 1200;
    public static final int RELAY_DESCENT_TICKS = 30 * 20;
    public static final int SEED_DEPART_TICKS = 60 * 20;
    public static final int SEED_ARRIVE_TICKS = 60 * 20;
    /** 种子只去这么远（光年）以内的恒星系。 */
    public static final double SEED_RANGE = 15;
    /** 落点挑不到时在轨道上等多久再试。 */
    public static final int LANDING_RETRY_TICKS = 2 * 1200;
    /** 挑几次落点都不行就放弃这个中继站。 */
    public static final int MAX_LANDING_ATTEMPTS = 5;
    /** 落点离玩家的设施多远。 */
    public static final int LANDING_MIN = 220;
    public static final int LANDING_MAX = 480;
    /** 一次挑落点试几个方向。 */
    static final int SITE_TRIES = 16;
    /** 降落从多高开始（离地面）。 */
    public static final double DESCENT_HEIGHT = 140;

    private FlightPlan() {
    }

    /** 种子在路上走多久：8 分钟加每光年 3 分钟，12–60 分钟。 */
    public static long seedCruiseTicks(double lightYears) {
        double minutes = Math.max(12, Math.min(60, 8 + 3 * Math.max(0, lightYears)));
        return Math.round(minutes * 1200);
    }

    public static long relayCruiseTicks(Rng rng) {
        return rng.range(RELAY_CRUISE_MIN_TICKS, RELAY_CRUISE_MAX_TICKS);
    }

    /** 新的中继站飞行：去星球维度 {@code dimension}。 */
    public static Flight relay(int id, int system, String dimension, int level, double x, double y, double z, long now, Rng rng) {
        long depart = now + RELAY_DEPART_TICKS;
        long arrive = depart + relayCruiseTicks(rng);
        return new Flight(id, Flight.Kind.RELAY, system, system, dimension, level, x, y, z, now, depart, arrive, arrive + RELAY_DESCENT_TICKS);
    }

    /** 新的种子飞行：从 {@code from} 去 {@code to}，两边隔着 {@code lightYears} 光年。 */
    public static Flight seed(int id, int from, int to, double lightYears, int level, double x, double y, double z, long now) {
        long depart = now + SEED_DEPART_TICKS;
        long arrive = depart + seedCruiseTicks(lightYears);
        return new Flight(id, Flight.Kind.SEED, from, to, "", level, x, y, z, now, depart, arrive, arrive + SEED_ARRIVE_TICKS);
    }

    /**
     * 种子去哪：离 {@code from} 最近的、{@link #SEED_RANGE} 光年以内、{@code taken} 不认的恒星系。
     *
     * @param taken 有巢穴了、或者已经有种子往那边飞的恒星系
     */
    public static OptionalInt seedTarget(List<StarSystem> systems, int from, IntPredicate taken) {
        if (from < 0 || from >= systems.size()) {
            return OptionalInt.empty();
        }
        StarSystem origin = systems.get(from);
        int best = -1;
        double bestDistance = SEED_RANGE;
        for (StarSystem system : systems) {
            if (system.index() == from || taken.test(system.index())) {
                continue;
            }
            double distance = origin.distanceTo(system);
            if (distance <= bestDistance) {
                best = system.index();
                bestDistance = distance;
            }
        }
        return best < 0 ? OptionalInt.empty() : OptionalInt.of(best);
    }

    /**
     * 中继站的落点：以玩家的设施 ({@code anchorX}, {@code anchorZ}) 为中心，{@link #LANDING_MIN}–{@link #LANDING_MAX} 格外随便挑一个方向，
     * 离 {@code bases} 里每处基地都至少 {@code spacing} 格，不在护盾里。
     *
     * @param bases    别的基地的中心，每个是 {x, z}
     * @param shielded 这一点有没有护盾盖着
     * @return {x, z}；试了 {@link #SITE_TRIES} 次都不行是空
     */
    public static Optional<int[]> landingSite(double anchorX, double anchorZ, Rng rng, List<int[]> bases, int spacing,
            BiPredicate<Double, Double> shielded) {
        for (int i = 0; i < SITE_TRIES; i++) {
            double angle = rng.nextDouble() * Math.PI * 2;
            double distance = rng.range((double) LANDING_MIN, (double) LANDING_MAX);
            int x = (int) Math.floor(anchorX + Math.cos(angle) * distance);
            int z = (int) Math.floor(anchorZ + Math.sin(angle) * distance);
            if (clear(x, z, bases, spacing) && !shielded.test(x + 0.5, z + 0.5)) {
                return Optional.of(new int[] {x, z});
            }
        }
        return Optional.empty();
    }

    static boolean clear(int x, int z, List<int[]> bases, int spacing) {
        for (int[] base : bases) {
            if (Math.hypot(base[0] - x, base[1] - z) < spacing) {
                return false;
            }
        }
        return true;
    }

    /** 降落时离地多高：{@code t} 从 0 到 1，开始降得快，快到地面时慢下来。 */
    public static double descentHeight(double t) {
        double left = 1 - Math.max(0, Math.min(1, t));
        return DESCENT_HEIGHT * left * left;
    }
}
