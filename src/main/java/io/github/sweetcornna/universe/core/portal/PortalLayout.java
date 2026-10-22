package io.github.sweetcornna.universe.core.portal;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 先驱者传送门的方块布局（ADR-0024）：一块石台，上面立着一座下半截埋在台子里的石环，环的上半圈嵌着六块符文石（门的地址），
 * 门开时环里是一层光；门前侧边一个拨号台。
 *
 * <p>本地坐标：原点在石台中心的台面上，x 朝右，y 朝上，z 朝门前（拨号台、到达点的那一侧）。石环在 z = 0 这一层。
 */
public final class PortalLayout {
    /** 石环圆心的高度（格，台面是 0）：圆心压低，下面三分之一埋在台子里，走着就能进门。 */
    static final double CENTER_Y = 3.0;
    /** 环里（门开时那层光）的半径。 */
    static final double INNER = 3.2;
    /** 石环的外半径。 */
    static final double OUTER = 4.4;
    /** 石台的半宽（x）和半深（z）。 */
    public static final int PLATFORM_HALF_WIDTH = 5;
    public static final int PLATFORM_HALF_DEPTH = 4;
    /** 台面以上清空多高。 */
    public static final int CLEAR_HEIGHT = 9;
    /** 台子下面最多往下垫几格，垫到实心的地面为止。 */
    public static final int SUPPORT_DEPTH = 12;

    private PortalLayout() {
    }

    public enum Part {
        /** 先驱者石：石台和石环。 */
        STONE,
        /** 刻着地址里一个符文的石块。 */
        GLYPH,
        /** 门开时的那层光（门关着时是空气）。 */
        FIELD,
        DIAL
    }

    /**
     * 一格。
     *
     * @param glyph 是地址里的第几个符文（{@link Part#GLYPH} 才有，其余是 -1）
     */
    public record Cell(int x, int y, int z, Part part, int glyph) {
    }

    /** 所有的方块：石台、石环、符文石、拨号台、环里的那层光。 */
    public static List<Cell> cells() {
        List<Cell> cells = new ArrayList<>();
        for (int x = -PLATFORM_HALF_WIDTH; x <= PLATFORM_HALF_WIDTH; x++) {
            for (int z = -PLATFORM_HALF_DEPTH; z <= PLATFORM_HALF_DEPTH; z++) {
                cells.add(new Cell(x, 0, z, Part.STONE, -1));
            }
        }
        List<int[]> ring = ring();
        List<int[]> glyphs = glyphCells(ring);
        for (int[] c : ring) {
            int glyph = -1;
            for (int i = 0; i < glyphs.size(); i++) {
                if (glyphs.get(i)[0] == c[0] && glyphs.get(i)[1] == c[1]) {
                    glyph = i;
                }
            }
            cells.add(new Cell(c[0], c[1], 0, glyph >= 0 ? Part.GLYPH : Part.STONE, glyph));
        }
        for (int[] c : field()) {
            cells.add(new Cell(c[0], c[1], c[2], Part.FIELD, -1));
        }
        int[] dial = dial();
        cells.add(new Cell(dial[0], dial[1], dial[2], Part.DIAL, -1));
        return cells;
    }

    /** 环里的格子 {x, y, z}：门开时填上那层光。 */
    public static List<int[]> field() {
        List<int[]> field = new ArrayList<>();
        for (int y = 1; y <= CLEAR_HEIGHT; y++) {
            for (int x = -PLATFORM_HALF_WIDTH; x <= PLATFORM_HALF_WIDTH; x++) {
                if (distance(x, y) < INNER) {
                    field.add(new int[] {x, y, 0});
                }
            }
        }
        return field;
    }

    /** 拨号台 {x, y, z}：门前偏右，不挡着进门的路。 */
    public static int[] dial() {
        return new int[] {3, 1, 3};
    }

    /** 从别的门过来的人站在哪 {x, y, z}（脚下那一格），面朝门外。 */
    public static int[] arrival() {
        return new int[] {0, 1, 2};
    }

    /** 石环的格子 {x, y}（台面以上）。 */
    static List<int[]> ring() {
        List<int[]> ring = new ArrayList<>();
        for (int y = 1; y <= CLEAR_HEIGHT; y++) {
            for (int x = -PLATFORM_HALF_WIDTH; x <= PLATFORM_HALF_WIDTH; x++) {
                double d = distance(x, y);
                if (d >= INNER && d < OUTER) {
                    ring.add(new int[] {x, y});
                }
            }
        }
        return ring;
    }

    /**
     * 上半圈挑 {@link PortalAddress#LENGTH} 格嵌符文石，间隔大致相等，按站在门前看从左到右排。站在门前面朝门时，
     * 本地的 +x 在左手边。
     */
    static List<int[]> glyphCells(List<int[]> ring) {
        List<int[]> upper = new ArrayList<>();
        for (int[] c : ring) {
            if (c[1] > CENTER_Y) {
                upper.add(c);
            }
        }
        // 从 +x 那头（角度小）排到 -x 那头
        upper.sort(Comparator.comparingDouble((int[] c) -> Math.atan2(c[1] - CENTER_Y, c[0])));
        List<int[]> picked = new ArrayList<>();
        int n = PortalAddress.LENGTH;
        for (int i = 0; i < n; i++) {
            int index = (int) Math.round((i + 0.5) * upper.size() / n - 0.5);
            picked.add(upper.get(Math.min(upper.size() - 1, Math.max(0, index))));
        }
        return picked;
    }

    private static double distance(int x, int y) {
        double dy = y - CENTER_Y;
        return Math.sqrt(x * x + dy * dy);
    }

    /**
     * 本地坐标转世界坐标的偏移 {dx, dz}：{@code frontX, frontZ} 是门前的方向（东西南北之一的单位向量）。
     * 右手边 = 门前顺时针转 90 度（从上往下看）。
     */
    public static int[] rotate(int x, int z, int frontX, int frontZ) {
        // 门前 (fx, fz) 顺时针转 90 度是 (-fz, fx)：朝南 (0, 1) 的右手边是西 (-1, 0)
        int rightX = -frontZ;
        int rightZ = frontX;
        return new int[] {rightX * x + frontX * z, rightZ * x + frontZ * z};
    }
}
