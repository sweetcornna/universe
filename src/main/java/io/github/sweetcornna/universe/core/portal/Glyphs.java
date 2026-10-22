package io.github.sweetcornna.universe.core.portal;

import io.github.sweetcornna.universe.core.procgen.Rng;
import io.github.sweetcornna.universe.core.procgen.Seeds;

/**
 * 玩家学会的符文（ADR-0024），16 种各占一位。巨石、第一次见到的传送门、探索远征各教一个没学过的。
 */
public final class Glyphs {
    public static final int ALL = (1 << PortalAddress.GLYPHS) - 1;
    /** 传送门开着多久（刻）。 */
    public static final int OPEN_TICKS = 30 * 20;
    /** 玩家走到多近时放传送门。 */
    public static final double PLACE_RANGE = 96;
    /** 传送门离着陆点多远。 */
    public static final int MIN_DISTANCE = 220;
    public static final int MAX_DISTANCE = 520;

    private Glyphs() {
    }

    public static boolean knows(int known, int glyph) {
        return (known >>> glyph & 1) != 0;
    }

    public static int count(int known) {
        return Integer.bitCount(known & ALL);
    }

    /** 这个地址的符文是不是都学会了。 */
    public static boolean canDial(int known, int[] glyphs) {
        for (int glyph : glyphs) {
            if (!knows(known, glyph)) {
                return false;
            }
        }
        return true;
    }

    /** 再学一个：从没学过的里面随便挑一个；全学会了返回 -1。 */
    public static int next(int known, Rng rng) {
        int missing = PortalAddress.GLYPHS - count(known);
        if (missing <= 0) {
            return -1;
        }
        int pick = rng.nextInt(missing);
        for (int glyph = 0; glyph < PortalAddress.GLYPHS; glyph++) {
            if (!knows(known, glyph) && pick-- == 0) {
                return glyph;
            }
        }
        return -1;
    }

    /** 这颗星球的传送门相对着陆点的偏移 {dx, dz}（新的派生键 {@code portal}）。 */
    public static int[] offset(long planetSeed) {
        Rng rng = Rng.of(Seeds.derive(planetSeed, "portal"));
        double angle = rng.range(0, Math.PI * 2);
        double distance = rng.range(MIN_DISTANCE, MAX_DISTANCE);
        return new int[] {(int) Math.round(Math.cos(angle) * distance), (int) Math.round(Math.sin(angle) * distance)};
    }
}
