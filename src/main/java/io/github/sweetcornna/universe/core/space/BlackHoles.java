package io.github.sweetcornna.universe.core.space;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import io.github.sweetcornna.universe.core.galaxy.Galaxy;
import io.github.sweetcornna.universe.core.galaxy.StarClass;
import io.github.sweetcornna.universe.core.galaxy.StarSystem;
import io.github.sweetcornna.universe.core.procgen.Colors;
import io.github.sweetcornna.universe.core.procgen.Rng;
import io.github.sweetcornna.universe.core.procgen.Seeds;
import io.github.sweetcornna.universe.core.sky.StarLook;

/**
 * 太空里的黑洞（ADR-0024）。恒星类型的权重属于存档格式，黑洞恒星又极少，所以像无人深空那样另外在恒星系里放：
 * 每个系有 {@link #CHANCE} 的机会有一个，用新的派生键 {@code black_hole}，不碰已有的派生。本身是黑洞的恒星也算一个，
 * 在恒星的位置上。
 *
 * <p>开船飞进视界就被甩到星团里离这里最远的那一片恒星系中的一个，不用翘曲器、不看驱动档次。
 */
public final class BlackHoles {
    public static final double CHANCE = 1.0 / 6;
    /** 离恒星多远（格）。 */
    public static final double MIN_DISTANCE = 30000;
    public static final double MAX_DISTANCE = 45000;
    /** 离黄道面最多多高：太空维度只有 512 格高。 */
    public static final double MAX_HEIGHT = 100;
    /** 视界：飞进这么近就被甩走。 */
    public static final double HORIZON = 150;
    /** 和星球的球面、空间站至少隔多远。 */
    static final double CLEARANCE = 6000;
    /** 候选是离得最远的这么一部分恒星系。 */
    static final double FAR_SHARE = 0.25;
    /** 船体掉多少（按上限的比例）。 */
    public static final double HULL_LOSS = 0.3;
    private static final int TRIES = 24;

    private BlackHoles() {
    }

    public record BlackHole(double x, double y, double z) {
        public double distanceTo(double px, double py, double pz) {
            double dx = px - this.x;
            double dy = py - this.y;
            double dz = pz - this.z;
            return Math.sqrt(dx * dx + dy * dy + dz * dz);
        }
    }

    /** {@link #of} 的简便写法：天体和空间站按这个系照常算出来。 */
    public static Optional<BlackHole> in(StarSystem system) {
        double[] station = StationLayout.site(system).map(s -> new double[] {s.x(), s.y(), s.z()}).orElse(new double[] {0, 0, 0});
        return of(system, SpaceLayout.of(system), station);
    }

    /** 视界的半径（格）：恒星本身是黑洞时就是画出来的阴影，太空里另外放的是 {@link #HORIZON}。 */
    public static double horizon(StarSystem system) {
        return system.star().starClass() == StarClass.BLACK_HOLE ? SpaceLayout.starRadius(system) : HORIZON;
    }

    /**
     * 画出来的样子（ADR-0025）：阴影的半径等于视界，吸积盘偏暖的橙白色。恒星本身是黑洞时就是那颗恒星的样子。
     * 只管画面，用新的派生键 {@code black_hole/look}。
     */
    public static StarLook look(StarSystem system) {
        if (system.star().starClass() == StarClass.BLACK_HOLE) {
            return StarLook.of(system);
        }
        Rng rng = Rng.of(Seeds.derive(system.seed(), "black_hole/look"));
        double seed = rng.nextDouble();
        int color = Colors.lerp(0xFF9A48, 0xFFD49A, rng.nextDouble());
        return StarLook.blackHole(rng, HORIZON, color, seed);
    }

    /**
     * 这个系里的黑洞。
     *
     * @param bodies  这个系的天体（{@link SpaceLayout#of}）
     * @param station 空间站的位置 {x, y, z}
     */
    public static Optional<BlackHole> of(StarSystem system, List<SpaceLayout.Body> bodies, double[] station) {
        if (system.star().starClass() == StarClass.BLACK_HOLE) {
            return Optional.of(new BlackHole(0, 0, 0));
        }
        Rng rng = Rng.of(Seeds.derive(system.seed(), "black_hole"));
        if (!rng.chance(CHANCE)) {
            return Optional.empty();
        }
        for (int i = 0; i < TRIES; i++) {
            double angle = rng.range(0, Math.PI * 2);
            double distance = rng.range(MIN_DISTANCE, MAX_DISTANCE);
            double x = Math.cos(angle) * distance;
            double z = Math.sin(angle) * distance;
            double y = rng.range(-MAX_HEIGHT, MAX_HEIGHT);
            if (clear(x, y, z, bodies, station)) {
                return Optional.of(new BlackHole(x, y, z));
            }
        }
        return Optional.empty();
    }

    private static boolean clear(double x, double y, double z, List<SpaceLayout.Body> bodies, double[] station) {
        for (SpaceLayout.Body body : bodies) {
            double dx = x - body.x();
            double dy = y - body.y();
            double dz = z - body.z();
            if (Math.sqrt(dx * dx + dy * dy + dz * dz) < body.radius() + CLEARANCE) {
                return false;
            }
        }
        double dx = x - station[0];
        double dz = z - station[2];
        return Math.sqrt(dx * dx + dz * dz) > CLEARANCE;
    }

    /**
     * 被甩到哪个恒星系：离出发的系最远的那 {@link #FAR_SHARE} 里挑一个。同一个人每次跳都不一样（{@code jumps} 是他跳过几次）。
     */
    public static int destination(Galaxy galaxy, int from, long player, int jumps) {
        StarSystem origin = galaxy.system(from).orElseThrow();
        List<StarSystem> others = new ArrayList<>();
        for (StarSystem system : galaxy.systems()) {
            if (system.index() != from) {
                others.add(system);
            }
        }
        others.sort(Comparator.comparingDouble(origin::distanceTo).reversed());
        int candidates = Math.max(1, (int) Math.ceil(others.size() * FAR_SHARE));
        Rng rng = Rng.of(Seeds.derive(Seeds.derive(Seeds.derive(galaxy.seed(), "black_hole_jump"), player), jumps * 64L + from));
        return others.get(rng.nextInt(candidates)).index();
    }
}
