package io.github.sweetcornna.universe.core.darkfog;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.github.sweetcornna.universe.core.procgen.Rng;
import io.github.sweetcornna.universe.core.procgen.Seeds;

/**
 * 黑雾地面基地放在哪（ADR-0028 §二），按派生键 {@code dark_fog/base} 算：每颗能登陆的星球（母星除外）0–3 处，
 * 离着陆点（出生点）600–2000 格；"初始占领"越高越多、越近；100% 以上时母星系保证至少一处。
 */
public final class BaseSites {
    public static final String KEY = "dark_fog/base";
    public static final int MAX_PER_BODY = 3;
    public static final double MIN_DISTANCE = 600;
    public static final double MAX_DISTANCE = 2000;
    /** 同一颗星球上两处基地至少隔多远。 */
    public static final double SPACING = 400;
    /** 第 1、2、3 处基地有的概率（占领 100% 时）；前一处没有，后面的也没有。 */
    private static final double[] CHANCE = {0.65, 0.3, 0.1};

    /** 一个能登陆的天体。 */
    public record Body(String path, long seed, int worldSize) {
    }

    /** 一处基地的中心（钻芯），相对出生点。 */
    public record Site(int index, int x, int z) {
    }

    private BaseSites() {
    }

    /** 这颗星球上有几处基地（不算母星系的保底）。 */
    public static int count(long bodySeed, double occupation) {
        Rng rng = Rng.of(Seeds.derive(bodySeed, KEY));
        int count = 0;
        for (double chance : CHANCE) {
            if (rng.nextDouble() < chance * occupation) {
                count++;
            } else {
                break;
            }
        }
        return count;
    }

    /**
     * 一个恒星系里每个天体有几处基地。母星系一处都没抽到、占领又到了 100% 时，挑一个天体放一处。
     *
     * @param bodies 能登陆的天体，母星不在里面
     */
    public static Map<String, Integer> counts(List<Body> bodies, boolean homeSystem, long systemSeed, double occupation) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        int total = 0;
        for (Body body : bodies) {
            int count = count(body.seed(), occupation);
            counts.put(body.path(), count);
            total += count;
        }
        if (homeSystem && total == 0 && occupation >= 1 && !bodies.isEmpty()) {
            Body chosen = Rng.of(Seeds.derive(systemSeed, KEY)).pick(bodies);
            counts.put(chosen.path(), 1);
        }
        return counts;
    }

    /** 这颗星球上的基地放在哪：在世界边界里，离出生点 600–2000 格（小星球按边界缩），彼此隔开。 */
    public static List<Site> place(Body body, int count, double occupation) {
        List<Site> sites = new ArrayList<>();
        if (count <= 0) {
            return sites;
        }
        double half = body.worldSize() / 2.0;
        double maxR = Math.min(MAX_DISTANCE / Math.sqrt(Math.max(1, occupation)), half - BaseLayout.RADIUS - 64);
        maxR = Math.max(maxR, Math.min(1200, half - BaseLayout.RADIUS - 64));
        double minR = Math.min(MIN_DISTANCE, maxR * 0.6);
        double spacing = Math.min(SPACING, maxR / 2);
        Rng rng = Rng.of(Seeds.derive(Seeds.derive(body.seed(), KEY), "sites"));
        for (int i = 0; i < count; i++) {
            for (int attempt = 0; attempt < 24; attempt++) {
                double angle = rng.range(0, Math.PI * 2);
                double r = rng.range(minR, Math.max(minR + 1, maxR));
                int x = (int) Math.round(Math.cos(angle) * r);
                int z = (int) Math.round(Math.sin(angle) * r);
                boolean apart = sites.stream().allMatch(other -> Math.hypot(other.x() - x, other.z() - z) >= spacing);
                if (apart) {
                    sites.add(new Site(sites.size(), x, z));
                    break;
                }
            }
        }
        return sites;
    }
}
