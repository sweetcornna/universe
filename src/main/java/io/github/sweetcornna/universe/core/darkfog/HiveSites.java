package io.github.sweetcornna.universe.core.darkfog;

import java.util.List;

import io.github.sweetcornna.universe.core.procgen.Rng;
import io.github.sweetcornna.universe.core.procgen.Seeds;
import io.github.sweetcornna.universe.core.space.SpaceLayout;

/**
 * 太空巢穴放在哪（ADR-0028 §八），按派生键 {@code dark_fog/hive} 算：有地面基地的恒星系一座，在绕恒星的轨道上，
 * 离恒星两万四到六万格，离黄道面不超过 80 格，和星球的大气球、空间站、黑洞都隔开五千格以上。开脉冲引擎半分钟到一分钟就到。
 */
public final class HiveSites {
    public static final String KEY = "dark_fog/hive";
    public static final double MIN_DISTANCE = 24000;
    public static final double MAX_DISTANCE = 60000;
    /** 离黄道面最多多高：太空维度只有 512 格高。 */
    public static final double MAX_HEIGHT = 80;
    /** 和星球的大气球、空间站、黑洞至少隔多远。 */
    public static final double CLEARANCE = 5000;
    /** 和恒星表面至少隔多远。 */
    public static final double STAR_CLEARANCE = 8000;
    private static final int TRIES = 32;

    public record Site(double x, double y, double z) {
        public double distanceTo(double px, double py, double pz) {
            double dx = px - this.x;
            double dy = py - this.y;
            double dz = pz - this.z;
            return Math.sqrt(dx * dx + dy * dy + dz * dz);
        }
    }

    private HiveSites() {
    }

    /** 这个恒星系开局有没有巢穴：和地面基地一样按 {@link BaseSites#counts} 算，有一处基地就有。 */
    public static boolean seeded(List<BaseSites.Body> bodies, boolean homeSystem, long systemSeed, double occupation) {
        return BaseSites.counts(bodies, homeSystem, systemSeed, occupation).values().stream().anyMatch(count -> count > 0);
    }

    /**
     * 巢穴的中心。
     *
     * @param starRadius 恒星在太空里的半径（{@link SpaceLayout#starRadius}）
     * @param bodies     这个系的天体（{@link SpaceLayout#of}）
     * @param avoid      还要躲开的点 {x, y, z}：空间站、黑洞
     */
    public static Site site(long systemSeed, double starRadius, List<SpaceLayout.Body> bodies, List<double[]> avoid) {
        Rng rng = Rng.of(Seeds.derive(systemSeed, KEY));
        double min = Math.max(MIN_DISTANCE, starRadius + STAR_CLEARANCE);
        double max = Math.max(MAX_DISTANCE, min + 8000);
        double firstAngle = 0;
        for (int i = 0; i < TRIES; i++) {
            double angle = rng.range(0, Math.PI * 2);
            double distance = rng.range(min, max);
            double y = rng.range(-MAX_HEIGHT, MAX_HEIGHT);
            if (i == 0) {
                firstAngle = angle;
            }
            Site site = new Site(Math.cos(angle) * distance, y, Math.sin(angle) * distance);
            if (clear(site, bodies, avoid)) {
                return site;
            }
        }
        // 挤满了：顺着第一次的方向往外推，推到空处为止
        for (double distance = max; ; distance += 4000) {
            Site site = new Site(Math.cos(firstAngle) * distance, 0, Math.sin(firstAngle) * distance);
            if (clear(site, bodies, avoid) || distance > max * 8) {
                return site;
            }
        }
    }

    static boolean clear(Site site, List<SpaceLayout.Body> bodies, List<double[]> avoid) {
        for (SpaceLayout.Body body : bodies) {
            if (body.distanceTo(site.x(), site.y(), site.z()) < body.radius() + CLEARANCE) {
                return false;
            }
        }
        for (double[] point : avoid) {
            if (site.distanceTo(point[0], point[1], point[2]) < CLEARANCE) {
                return false;
            }
        }
        return true;
    }
}
