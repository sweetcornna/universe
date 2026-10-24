package io.github.sweetcornna.universe.core.deepspace;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.core.procgen.Rng;
import io.github.sweetcornna.universe.core.procgen.Seeds;
import io.github.sweetcornna.universe.core.space.SpaceLayout;

/**
 * 每个星系太空里的深空兴趣点（ADR-0034 §四）：碎片带、废弃前哨站、太空残骸。位置按星系的种子定下，同一个存档里不变。
 *
 * <p>都放在行星和卫星的大气球外面几百格、黄道面附近（太空维度只有 y ∈ [−256, 255] 能放方块），起飞以后抬头就能在
 * HUD 上看到；一片碎片带放在空间站外面。每个星系至少一艘残骸。
 */
public final class DeepSpaceSites {
    /** HUD 上标出来的距离（格）。 */
    public static final double HUD_RANGE = 2500;
    /** 放的高度范围：离方块的上下限留出残骸的高度。 */
    static final double MIN_Y = -150;
    static final double MAX_Y = 150;
    /** 离恒星表面至少这么远。 */
    static final double STAR_CLEARANCE = 2500;
    /** 离大气球外沿至少这么远（再加上兴趣点本身的大小）。 */
    static final double BODY_CLEARANCE = 250;
    /** 离空间站至少这么远。 */
    static final double STATION_CLEARANCE = 700;
    /** 两个兴趣点之间至少隔这么远。 */
    static final double SPACING = 600;
    private static final int ATTEMPTS = 16;

    private DeepSpaceSites() {
    }

    public enum Kind {
        DEBRIS(DebrisField.RADIUS),
        OUTPOST(30),
        HULK(70);

        private final double extent;

        Kind(double extent) {
            this.extent = extent;
        }

        /** 包住整个兴趣点的半径（格）。 */
        public double extent() {
            return this.extent;
        }

        /** 语言键、命令里用的名字。 */
        public String key() {
            return this.name().toLowerCase(Locale.ROOT);
        }
    }

    /**
     * 一个兴趣点。
     *
     * @param index 在这个星系里的序号（存档里按它记状态）
     * @param seed  搭的样子由它定
     */
    public record Site(Kind kind, int index, double x, double y, double z, long seed) {
        public double distanceTo(double px, double py, double pz) {
            double dx = px - this.x;
            double dy = py - this.y;
            double dz = pz - this.z;
            return Math.sqrt(dx * dx + dy * dy + dz * dz);
        }
    }

    /**
     * 一个星系的所有深空兴趣点。
     *
     * @param station 空间站的中心；没有空间站时为空
     */
    public static List<Site> of(long systemSeed, List<SpaceLayout.Body> bodies, double starRadius, double @Nullable [] station) {
        return of(systemSeed, bodies, starRadius, station, List.of());
    }

    /**
     * 同上，另外躲开 {@code avoid} 里的点（黑暗迷雾的巢穴、黑洞）至少 {@link #SPACING} 格。
     */
    public static List<Site> of(long systemSeed, List<SpaceLayout.Body> bodies, double starRadius, double @Nullable [] station,
            List<double[]> avoid) {
        Rng rng = Rng.of(Seeds.derive(systemSeed, "deep_space"));
        List<Kind> wanted = new ArrayList<>();
        if (station != null) {
            wanted.add(Kind.DEBRIS);
        }
        wanted.add(Kind.HULK);
        if (rng.chance(0.7)) {
            wanted.add(Kind.OUTPOST);
        }
        if (rng.chance(0.35)) {
            wanted.add(Kind.HULK);
        }
        if (rng.chance(0.5)) {
            wanted.add(Kind.DEBRIS);
        }
        List<Site> sites = new ArrayList<>();
        for (int k = 0; k < wanted.size(); k++) {
            Kind kind = wanted.get(k);
            boolean nearStation = k == 0 && station != null;
            // 残骸多试几次：每个星系都要有一艘能拆的
            int attempts = kind == Kind.HULK ? ATTEMPTS * 3 : ATTEMPTS;
            for (int attempt = 0; attempt < attempts; attempt++) {
                double ax;
                double ay;
                double az;
                double distance;
                if (nearStation) {
                    ax = station[0];
                    ay = station[1];
                    az = station[2];
                    distance = rng.range(900.0, 1500.0);
                } else if (!bodies.isEmpty()) {
                    SpaceLayout.Body body = rng.pick(bodies);
                    ax = body.x();
                    ay = body.y();
                    az = body.z();
                    distance = body.radius() + switch (kind) {
                        case HULK -> rng.range(500.0, 1100.0);
                        case OUTPOST -> rng.range(400.0, 900.0);
                        case DEBRIS -> rng.range(450.0, 1000.0);
                    };
                } else {
                    break;
                }
                double angle = rng.nextDouble() * Math.PI * 2;
                double x = ax + Math.cos(angle) * distance;
                double z = az + Math.sin(angle) * distance;
                double y = Math.clamp(ay + rng.range(-80.0, 80.0), MIN_Y, MAX_Y);
                if (clear(x, y, z, kind, bodies, starRadius, station, sites, nearStation) && away(x, y, z, kind, avoid)) {
                    sites.add(new Site(kind, sites.size(), x, y, z, rng.nextLong()));
                    break;
                }
            }
        }
        return sites;
    }

    private static boolean away(double x, double y, double z, Kind kind, List<double[]> avoid) {
        for (double[] point : avoid) {
            double dx = x - point[0];
            double dy = y - point[1];
            double dz = z - point[2];
            if (Math.sqrt(dx * dx + dy * dy + dz * dz) < SPACING + kind.extent()) {
                return false;
            }
        }
        return true;
    }

    static boolean clear(double x, double y, double z, Kind kind, List<SpaceLayout.Body> bodies, double starRadius, double @Nullable [] station,
            List<Site> taken, boolean nearStation) {
        if (Math.sqrt(x * x + y * y + z * z) < starRadius + STAR_CLEARANCE) {
            return false;
        }
        for (SpaceLayout.Body body : bodies) {
            if (body.distanceTo(x, y, z) < body.radius() + BODY_CLEARANCE + kind.extent()) {
                return false;
            }
        }
        if (station != null && !nearStation) {
            double dx = x - station[0];
            double dy = y - station[1];
            double dz = z - station[2];
            if (Math.sqrt(dx * dx + dy * dy + dz * dz) < STATION_CLEARANCE) {
                return false;
            }
        }
        for (Site other : taken) {
            if (other.distanceTo(x, y, z) < SPACING) {
                return false;
            }
        }
        return true;
    }
}
