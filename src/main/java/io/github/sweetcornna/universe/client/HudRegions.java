package io.github.sweetcornna.universe.client;

import java.util.HashMap;
import java.util.Map;

/**
 * HUD 左边的面板（外骨骼、载具、飞船）占的地方：航点、空间站这些标记的字挪到面板右边，不压在面板上。
 * 面板每帧画的时候报一次；标记比面板先画，读到的是上一帧报的，面板不动，差一帧看不出来。只在渲染线程用。
 */
public final class HudRegions {
    /** 多久没报的面板算已经收起来了（纳秒）。 */
    private static final long STALE = 250_000_000L;
    private static final Map<String, long[]> REGIONS = new HashMap<>();

    private HudRegions() {
    }

    public static void claim(String key, int x0, int y0, int x1, int y1) {
        REGIONS.put(key, new long[] {x0, y0, x1, y1, System.nanoTime()});
    }

    /** 宽 {@code w}、高 {@code h} 的字放在 ({@code x}, {@code y}) 时压着哪块面板，就挪到那块面板右边；返回挪过的 x。 */
    public static int avoid(int x, int y, int w, int h) {
        long now = System.nanoTime();
        for (int pass = 0; pass <= REGIONS.size(); pass++) {
            boolean moved = false;
            for (long[] r : REGIONS.values()) {
                if (now - r[4] <= STALE && x < r[2] && x + w > r[0] && y < r[3] && y + h > r[1]) {
                    x = (int) r[2] + 3;
                    moved = true;
                }
            }
            if (!moved) {
                break;
            }
        }
        return x;
    }
}
